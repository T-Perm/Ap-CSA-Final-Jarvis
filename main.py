import cv2
import time
import socket
import struct
import threading
import urllib.request
import os
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import sys

MODEL_URL = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
MODEL_FILE = "hand_landmarker.task"

if not os.path.exists(MODEL_FILE):
    print("Downloading model...")
    urllib.request.urlretrieve(MODEL_URL, MODEL_FILE)
    print("Done.")

C_THUMB = (0, 255, 255)
C_INDEX = (0, 255, 0)
C_MID = (255, 128, 0)
C_RING = (255, 255, 0)
C_PINKY = (255, 0, 255)
C_PALM = (200, 200, 200)
C_DOT = (0, 0, 255)
C_TIP = (0, 255, 0)

CONNS = [
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 4),
    (0, 5),
    (5, 6),
    (6, 7),
    (7, 8),
    (0, 9),
    (9, 10),
    (10, 11),
    (11, 12),
    (0, 13),
    (13, 14),
    (14, 15),
    (15, 16),
    (0, 17),
    (17, 18),
    (18, 19),
    (19, 20),
    (5, 9),
    (9, 13),
    (13, 17),
]


def get_conn_color(idx):
    if idx < 4:
        return C_THUMB
    if idx < 8:
        return C_INDEX
    if idx < 12:
        return C_MID
    if idx < 16:
        return C_RING
    if idx < 20:
        return C_PINKY
    return C_PALM


PORT = 5005
latest_data = "NONE,0.0,0.0,0.0"
data_lock = threading.Lock()


def socket_server_thread():
    global latest_data
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(("127.0.0.1", PORT))
        server.listen(1)
        print(f"[Socket] Server listening on 127.0.0.1:{PORT}")
    except Exception as e:
        print(f"[Socket] Bind failed: {e}")
        return

    while True:
        try:
            client, addr = server.accept()
            print(f"[Socket] Client connected from {addr}")
            while True:
                with data_lock:
                    msg = latest_data + "\n"
                try:
                    client.sendall(msg.encode("utf-8"))
                except (socket.error, BrokenPipeError):
                    print("[Socket] Client disconnected.")
                    break
                time.sleep(0.01)
        except Exception as e:
            print(f"[Socket] Server loop error: {e}")
            time.sleep(1.0)


server_thread = threading.Thread(target=socket_server_thread, daemon=True)
server_thread.start()

FRAME_PORT = 5006
latest_frame_bytes = None
frame_lock = threading.Lock()


def frame_server_thread():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(("127.0.0.1", FRAME_PORT))
        server.listen(1)
        print(f"[FrameServer] Listening on 127.0.0.1:{FRAME_PORT}")
    except Exception as e:
        print(f"[FrameServer] Bind failed: {e}")
        return

    while True:
        try:
            client, addr = server.accept()
            print(f"[FrameServer] Java connected from {addr}", flush=True)
            frame_count = 0
            while True:
                req = client.recv(1)
                if not req:
                    print("[FrameServer] Java disconnected.", flush=True)
                    break
                with frame_lock:
                    data = latest_frame_bytes

                frame_count += 1
                if data is not None:
                    header = struct.pack(">I", len(data))
                    client.sendall(header + data)
                else:
                    client.sendall(struct.pack(">I", 0))
        except Exception as e:
            print(f"[FrameServer] Error: {e}", flush=True)
        finally:
            try:
                client.close()
            except:
                pass


frame_thread = threading.Thread(target=frame_server_thread, daemon=True)
frame_thread.start()

HOLD_PEN = 1000
HOLD_TOGGLE = 2000

held_fingers = -1
held_since = 0
pen_fired = False
toggle_fired = False


def count_fingers(landmarks):
    count = 0
    if landmarks[8].y < landmarks[6].y:
        count += 1
    if landmarks[12].y < landmarks[10].y:
        count += 1
    if landmarks[16].y < landmarks[14].y:
        count += 1
    if landmarks[20].y < landmarks[18].y:
        count += 1

    if abs(landmarks[4].x - landmarks[5].x) > 0.08:
        count += 1

    return count


def get_base_gesture(f):
    if f == 1:
        return "POINT"
    elif f == 2:
        return "RIGHT_CLICK"
    elif f == 3:
        return "LEFT_CLICK"
    return "NONE"


def apply_hold(fingers):
    global held_fingers, held_since, pen_fired, toggle_fired

    fist = fingers == 0
    palm = fingers >= 4
    now = int(time.time() * 1000)

    if fingers != held_fingers:
        held_fingers = fingers
        held_since = now
        pen_fired = False
        toggle_fired = False
        return get_base_gesture(fingers)

    held_duration = now - held_since

    if fist and held_duration >= HOLD_PEN and not pen_fired:
        pen_fired = True
        return "PEN_DOWN"

    if palm:
        if held_duration >= HOLD_TOGGLE and not toggle_fired:
            toggle_fired = True
            return "SCRATCHPAD_TOGGLE"
        elif held_duration >= HOLD_PEN and not pen_fired:
            pen_fired = True
            return "PEN_UP"

    return get_base_gesture(fingers)


def main():
    base_options = python.BaseOptions(model_asset_path=MODEL_FILE)
    options = vision.HandLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_hands=2,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    detector = vision.HandLandmarker.create_from_options(options)

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Error: Could not open webcam.")
        return

    print("Tracker running.", flush=True)
    prev_time = time.time()
    last_timestamp_ms = -1

    global latest_data, held_fingers, pen_fired, toggle_fired, latest_frame_bytes

    while True:
        try:
            success, frame = cap.read()
            if not success:
                print("Failed to grab frame.")
                break

            frame = cv2.flip(frame, 1)
            h, w, c = frame.shape

            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

            start_inf = time.time()
            timestamp_ms = int(time.perf_counter() * 1000)
            if timestamp_ms <= last_timestamp_ms:
                timestamp_ms = last_timestamp_ms + 1
            last_timestamp_ms = timestamp_ms

            results = detector.detect_for_video(mp_image, timestamp_ms)
            latency = (time.time() - start_inf) * 1000

            gesture_type = "NONE"
            finger_count = 0
            cx, cy = 0.0, 0.0

            if results.hand_landmarks:
                target_idx = -1
                for idx, handedness_list in enumerate(results.handedness):
                    category = handedness_list[0]
                    if category.category_name == "Left":
                        target_idx = idx
                        break

                if target_idx != -1:
                    landmarks = results.hand_landmarks[target_idx]

                    pts = []
                    for lm in landmarks:
                        px = int(lm.x * w)
                        py = int(lm.y * h)
                        pts.append((px, py))

                    for i, conn in enumerate(CONNS):
                        cv2.line(
                            frame, pts[conn[0]], pts[conn[1]], get_conn_color(i), 3
                        )

                    for i, pt in enumerate(pts):
                        is_tip = i in [4, 8, 12, 16, 20]
                        col = C_TIP if is_tip else C_DOT
                        rad = 7 if is_tip else 4
                        cv2.circle(frame, pt, rad, col, -1)
                        cv2.circle(frame, pt, rad, (255, 255, 255), 1)

                    cx = landmarks[9].x
                    cy = landmarks[9].y

                    finger_count = count_fingers(landmarks)
                    gesture_type = apply_hold(finger_count)

                    cv2.putText(
                        frame,
                        f"Right Hand - {gesture_type}",
                        (pts[0][0] - 50, pts[0][1] - 30),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.8,
                        (0, 0, 255),
                        2,
                    )

                    with data_lock:
                        latest_data = f"{gesture_type},{cx:.4f},{cy:.4f},1.0"
                else:
                    held_fingers = -1
                    pen_fired = False
                    toggle_fired = False
                    with data_lock:
                        latest_data = "NONE,0.0,0.0,0.0"
            else:
                held_fingers = -1
                pen_fired = False
                toggle_fired = False
                with data_lock:
                    latest_data = "NONE,0.0,0.0,0.0"

            curr_time = time.time()
            fps = 1.0 / (curr_time - prev_time)
            prev_time = curr_time

            cv2.putText(
                frame,
                f"FPS: {fps:.1f}",
                (10, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 0, 0),
                4,
            )
            cv2.putText(
                frame,
                f"FPS: {fps:.1f}",
                (10, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 255, 0),
                2,
            )

            cv2.putText(
                frame,
                f"Inference: {latency:.1f} ms",
                (10, 80),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 0, 0),
                4,
            )
            cv2.putText(
                frame,
                f"Inference: {latency:.1f} ms",
                (10, 80),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 255, 0),
                2,
            )

            cv2.putText(
                frame,
                f"Fingers: {finger_count}",
                (10, 120),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 0, 0),
                4,
            )
            cv2.putText(
                frame,
                f"Fingers: {finger_count}",
                (10, 120),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 255, 0),
                2,
            )

            _, jpeg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
            with frame_lock:
                latest_frame_bytes = jpeg.tobytes()

            time.sleep(0.001)
        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"Error inside main loop: {e}", file=sys.stderr)
            time.sleep(0.1)

    cap.release()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except Exception as e:
        print(f"Python main crashed: {e}", file=sys.stderr)
