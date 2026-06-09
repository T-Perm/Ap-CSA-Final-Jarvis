#include <jni.h>
#include <iostream>
#include <thread>
#include <atomic>
#include <mutex>
#include <string>
#include <cmath>
#include <chrono>

#include "mediapipe/tasks/cc/vision/hand_landmarker/hand_landmarker.h"
#include "mediapipe/tasks/cc/vision/core/running_mode.h"
#include "mediapipe/framework/formats/image_frame.h"
#include "mediapipe/framework/formats/image_frame_opencv.h"
#include "mediapipe/framework/formats/image.h"
#include "mediapipe/framework/port/opencv_highgui_inc.h"
#include "mediapipe/framework/port/opencv_imgproc_inc.h"
#include "mediapipe/framework/port/opencv_video_inc.h"

namespace mp_hl = mediapipe::tasks::vision::hand_landmarker;
namespace mp_containers = mediapipe::tasks::components::containers;


struct GestureState {
    std::string type = "NONE";
    double x = 0.0;
    double y = 0.0;
    double confidence = 0.0;
};

static std::mutex g_mutex;
static GestureState g_state;
static std::atomic<bool> g_running{false};
static std::thread g_tracker_thread;
static JavaVM* g_jvm = nullptr;
static jobject g_detector_ref = nullptr;


static int g_held_fingers = -1;
static int64_t g_held_since = 0;
static bool g_pen_fired = false;
static bool g_toggle_fired = false;
static const int64_t HOLD_PEN = 1000;
static const int64_t HOLD_TOGGLE = 2000;

static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

static int count_fingers(const std::vector<mp_containers::NormalizedLandmark>& lm) {
    int count = 0;
    if (lm[8].y < lm[6].y) count++;
    if (lm[12].y < lm[10].y) count++;
    if (lm[16].y < lm[14].y) count++;
    if (lm[20].y < lm[18].y) count++;
    if (std::abs(lm[4].x - lm[5].x) > 0.08f) count++;
    return count;
}

static std::string get_base_gesture(int f) {
    if (f == 1) return "POINT";
    if (f == 2) return "RIGHT_CLICK";
    if (f == 3) return "LEFT_CLICK";
    return "NONE";
}

static std::string apply_hold(int fingers) {
    bool fist = (fingers == 0);
    bool palm = (fingers >= 4);
    int64_t now = now_ms();

    if (fingers != g_held_fingers) {
        g_held_fingers = fingers;
        g_held_since = now;
        g_pen_fired = false;
        g_toggle_fired = false;
        return get_base_gesture(fingers);
    }

    int64_t held_duration = now - g_held_since;

    if (fist && held_duration >= HOLD_PEN && !g_pen_fired) {
        g_pen_fired = true;
        return "PEN_DOWN";
    }

    if (palm) {
        if (held_duration >= HOLD_TOGGLE && !g_toggle_fired) {
            g_toggle_fired = true;
            return "SCRATCHPAD_TOGGLE";
        } else if (held_duration >= HOLD_PEN && !g_pen_fired) {
            g_pen_fired = true;
            return "PEN_UP";
        }
    }

    return get_base_gesture(fingers);
}


static void notify_java(JNIEnv* env, jobject detector,
                         const std::string& type, double x, double y, double conf) {
    jclass cls = env->GetObjectClass(detector);
    jmethodID mid = env->GetMethodID(cls, "onGestureDetected",
                                      "(Ljava/lang/String;DDD)V");
    if (mid == nullptr) return;
    jstring jtype = env->NewStringUTF(type.c_str());
    env->CallVoidMethod(detector, mid, jtype, x, y, conf);
    env->DeleteLocalRef(jtype);
    env->DeleteLocalRef(cls);
}

static void tracker_loop() {

    JNIEnv* env = nullptr;
    JavaVMAttachArgs attach_args;
    attach_args.version = JNI_VERSION_1_8;
    attach_args.name = (char*)"TrackerThread";
    attach_args.group = nullptr;
    g_jvm->AttachCurrentThread((void**)&env, &attach_args);


    cv::VideoCapture cap(0);
    if (!cap.isOpened()) {
        std::cerr << "Could not open webcam." << std::endl;
        g_jvm->DetachCurrentThread();
        return;
    }


    auto options = std::make_unique<mp_hl::HandLandmarkerOptions>();
    options->base_options.model_asset_path = "hand_landmarker.task";
    options->running_mode = mediapipe::tasks::vision::core::RunningMode::VIDEO;
    options->num_hands = 2;
    options->min_hand_detection_confidence = 0.5;
    options->min_hand_presence_confidence = 0.5;
    options->min_tracking_confidence = 0.5;

    auto landmarker_or = mp_hl::HandLandmarker::Create(std::move(options));
    if (!landmarker_or.ok()) {
        std::cerr << "Failed to create HandLandmarker: "
                  << landmarker_or.status().message() << std::endl;
        cap.release();
        g_jvm->DetachCurrentThread();
        return;
    }
    auto landmarker = std::move(landmarker_or.value());

    std::cout << "Tracker running." << std::endl;
    int64_t last_ts = -1;

    while (g_running.load()) {
        cv::Mat bgr_frame;
        if (!cap.read(bgr_frame) || bgr_frame.empty()) continue;

        cv::flip(bgr_frame, bgr_frame, 1);

        cv::Mat rgb_frame;
        cv::cvtColor(bgr_frame, rgb_frame, cv::COLOR_BGR2RGB);


        auto mp_frame = std::make_shared<mediapipe::ImageFrame>(
            mediapipe::ImageFormat::SRGB,
            rgb_frame.cols, rgb_frame.rows, rgb_frame.step,
            rgb_frame.data,
            [](uint8_t*) {}
        );
        mediapipe::Image mp_image(mp_frame);

        int64_t ts = now_ms();
        if (ts <= last_ts) ts = last_ts + 1;
        last_ts = ts;

        auto result_or = landmarker->DetectForVideo(mp_image, ts);
        if (!result_or.ok()) continue;

        auto& result = result_or.value();

        std::string gesture_type = "NONE";
        double cx = 0.0, cy = 0.0;

        if (!result.hand_landmarks.empty()) {

            int target_idx = -1;
            for (int i = 0; i < (int)result.handedness.size(); i++) {
                auto& cats = result.handedness[i].categories;
                if (!cats.empty() && cats[0].category_name.has_value() &&
                    cats[0].category_name.value() == "Left") {
                    target_idx = i;
                    break;
                }
            }

            if (target_idx != -1) {
                auto& lm = result.hand_landmarks[target_idx].landmarks;
                cx = lm[9].x;
                cy = lm[9].y;

                int fingers = count_fingers(lm);
                gesture_type = apply_hold(fingers);
            } else {
                g_held_fingers = -1;
                g_pen_fired = false;
                g_toggle_fired = false;
            }
        } else {
            g_held_fingers = -1;
            g_pen_fired = false;
            g_toggle_fired = false;
        }


        {
            std::lock_guard<std::mutex> lock(g_mutex);
            g_state.type = gesture_type;
            g_state.x = cx;
            g_state.y = cy;
            g_state.confidence = (gesture_type == "NONE") ? 0.0 : 1.0;
        }


        notify_java(env, g_detector_ref, gesture_type, cx, cy,
                    (gesture_type == "NONE") ? 0.0 : 1.0);


        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    landmarker->Close().IgnoreError();
    cap.release();
    g_jvm->DetachCurrentThread();
}

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_starkmouse_detection_NativeGestureDetector_startTracker(
        JNIEnv* env, jobject obj) {
    if (g_running.load()) return;

    env->GetJavaVM(&g_jvm);
    g_detector_ref = env->NewGlobalRef(obj);

    g_running.store(true);
    g_tracker_thread = std::thread(tracker_loop);
    std::cout << "NativeGestureDetector started." << std::endl;
}

JNIEXPORT void JNICALL Java_com_starkmouse_detection_NativeGestureDetector_stopTracker(
        JNIEnv* env, jobject obj) {
    if (!g_running.load()) return;

    g_running.store(false);
    if (g_tracker_thread.joinable()) {
        g_tracker_thread.join();
    }
    if (g_detector_ref != nullptr) {
        env->DeleteGlobalRef(g_detector_ref);
        g_detector_ref = nullptr;
    }
    std::cout << "NativeGestureDetector stopped." << std::endl;
}

#ifdef __cplusplus
}
#endif
