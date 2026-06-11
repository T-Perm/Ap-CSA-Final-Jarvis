# Plan: Java Gesture Detection and Swing HUD Overlay

This plan describes the architectural changes to transition the StarkMouse project to a more maintainable, Java-centric application. We will move the gesture classification logic (pinch detection) into Java and introduce a Swing-based Graphical HUD that renders the live webcam stream and overlays the MediaPipe hand skeleton (landmarks and connections).

## User Review Required

> [!IMPORTANT]
> **JNI Callback Redesign**: We will change the JNI interface so that the C++ tracker only acts as a frame-capture and landmark-extraction engine, passing raw JPEG frame bytes and a `double[63]` coordinate array (21 landmarks × 3 dimensions) to Java. All business logic, click states, and visual drawing will occur in Java.
>
> **Bazel Build Setup**: We will need to recompile the JNI DLL using the local Bazel environment located at `C:\Users\email\mp_build\mediapipe`.

## Proposed Changes

---

### C++ JNI Component

#### [MODIFY] NativeGestureDetector.cc (both `jni/` workspace copy and `C:\Users\email\mp_build\mediapipe\mediapipe\examples\desktop\stark_mouse_jni\`)
- Update `notify_java` to send raw frame bytes (JPEG encoded) and landmark coordinate array:
  - Callback signature: `onFrameAndLandmarksDetected(byte[] jpegBytes, double[] landmarks)`
- In `tracker_loop`:
  - Extract all 21 landmarks (x, y, z) into a `double[63]` array.
  - Resize the captured frame to `480x360` and compress to JPEG using `cv::imencode` at 70% quality for fast JNI memory transfer.
  - Call the updated Java callback method with the JPEG frame and the landmarks.

---

### Java Detector Component

#### [MODIFY] src/main/java/com/starkmouse/detection/NativeGestureDetector.java
- Replace `onGestureDetected(...)` with `onFrameAndLandmarksDetected(byte[] jpegBytes, double[] landmarks)`.
- Define a `FrameListener` interface and pass frame events to the registered listener.

---

### Java Application & UI Component

#### [MODIFY] src/main/java/com/starkmouse/app/MainApp.java
- **Swing GUI Layout**: floating, undecorated, always-on-top `JFrame` HUD; `HudPanel` renders the decoded camera frame, faint tech grid lines, corner brackets, 21 landmarks (green tips), cyan connections, and pinch glow effects. Draggable via MouseListeners.
- **Pinch Gestures**: Euclidean distance thumb tip (4) ↔ index tip (8) = Left Click; thumb tip (4) ↔ middle tip (12) = Right Click. Hysteresis: start `< 0.04`, release `> 0.06` (normalized units), with debounce/cooldown.
- **Cursor Tracking**: map cursor from Landmark 9 (middle finger MCP) with exponential moving average smoothing.
- **Toggle Key**: JNativeHook global hotkey `Ctrl+Shift+G` shows/hides the HUD.

---

## Verification Plan

### Automated Tests
- Run `mvn compile` to ensure the Java codebase compiles without warnings.

### Manual Verification
1. Recompile the JNI DLL using Bazel:
   ```powershell
   $env:PYTHON_BIN_PATH='C:\Users\email\AppData\Local\Programs\Python\Python311\python.exe'; C:\Users\email\mp_build\bazel.exe build -c opt --define MEDIAPIPE_DISABLE_GPU=1 //mediapipe/examples/desktop/stark_mouse_jni:stark_mouse_jni.dll
   ```
2. Copy the newly compiled `stark_mouse_jni.dll` to the workspace root.
3. Launch the Java program and verify that:
   - A Swing HUD window opens and displays the camera feed.
   - The green landmarks and cyan pipes track the hand correctly.
   - Pinching thumb + index triggers a left-click drag/click action.
   - Pinching thumb + middle triggers a right-click action.
   - Pressing `Ctrl+Shift+G` hides/shows the HUD window.
