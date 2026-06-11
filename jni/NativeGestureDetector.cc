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
#include "mediapipe/framework/port/opencv_imgcodecs_inc.h"
#include "mediapipe/framework/port/opencv_imgproc_inc.h"
#include "mediapipe/framework/port/opencv_video_inc.h"

namespace mp_hl = mediapipe::tasks::vision::hand_landmarker;
namespace mp_containers = mediapipe::tasks::components::containers;


static std::atomic<bool> g_running{false};
static std::thread g_tracker_thread;
static JavaVM* g_jvm = nullptr;
static jobject g_detector_ref = nullptr;

static void notify_java(JNIEnv* env, jobject detector, const uchar* jpeg_data, int jpeg_size, const double* coords, int size) {
    jclass cls = env->GetObjectClass(detector);
    jmethodID mid = env->GetMethodID(cls, "onFrameAndLandmarksDetected", "([B[D)V");
    if (mid == nullptr) return;

    jbyteArray jframe = env->NewByteArray(jpeg_size);
    if (jpeg_data != nullptr && jpeg_size > 0) {
        env->SetByteArrayRegion(jframe, 0, jpeg_size, reinterpret_cast<const jbyte*>(jpeg_data));
    }

    jdoubleArray jarr = env->NewDoubleArray(size);
    if (coords != nullptr && size > 0) {
        env->SetDoubleArrayRegion(jarr, 0, size, coords);
    }

    env->CallVoidMethod(detector, mid, jframe, jarr);

    env->DeleteLocalRef(jframe);
    env->DeleteLocalRef(jarr);
    env->DeleteLocalRef(cls);
}

static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
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

        // Compress the BGR frame to JPEG for rendering in Swing HUD
        std::vector<uchar> jpeg_buf;
        cv::Mat resized_frame;
        cv::resize(bgr_frame, resized_frame, cv::Size(480, 360));
        std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, 70};
        cv::imencode(".jpg", resized_frame, jpeg_buf, params);

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
        if (!result_or.ok()) {
            // Even if landmark detection fails, notify Java with the camera frame
            notify_java(env, g_detector_ref, jpeg_buf.data(), jpeg_buf.size(), nullptr, 0);
            continue;
        }

        auto& result = result_or.value();

        double coords[63];
        int num_coords = 0;

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
                num_coords = 63;
                for (int i = 0; i < 21; ++i) {
                    coords[i * 3]     = lm[i].x;
                    coords[i * 3 + 1] = lm[i].y;
                    coords[i * 3 + 2] = lm[i].z;
                }
            }
        }

        notify_java(env, g_detector_ref, jpeg_buf.data(), jpeg_buf.size(), coords, num_coords);

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
