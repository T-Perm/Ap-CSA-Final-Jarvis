# StarkMouse - High-Performance Hand Tracking System
AP Computer Science A Final Project

StarkMouse is an advanced gesture-controlled cursor controller that allows users to operate their computer mouse hands-free. Using MediaPipe and OpenCV on a native C++ backend combined with a Java Swing overlay HUD, the application tracks hand movements, applies coordinate smoothing, and detects finger-pinch gestures to trigger mouse actions.

---

## 1. How to Use the Program

### Prerequisites
- **Java Runtime Environment (JRE) / JDK 17 or higher**
- A working **Webcam**
- Windows OS (64-bit) for native dynamic library compatibility (`stark_mouse_jni.dll` is included in the root)
either run `run.bat` or run the command java -jar StarkMouse.jar in the `starkmouse_release` folder



### Controls and Gestures
- **Mouse Movement**: Hold your hand in front of the webcam. Your cursor will follow the position of the **base of your middle finger**  This anchor was specifically chosen to prevent cursor jumping when fingers pinch.
- **Left Click / Click-and-Drag**: Pinch your **Thumb and Index Finger** together. Releasing the pinch releases the mouse button.
- **Right Click**: Pinch your **Thumb and Middle Finger** together.
- **Mouse Scrolling (Scroll Mode)**: Pinch your **Thumb and Pinky Finger** together. While holding the pinch, move your hand vertically:
  - Moving your hand **up** scrolls up.
  - Moving your hand **down** scrolls down.
  - While scrolling, mouse cursor movement is locked to prevent cursor drift.
- **Toggle HUD Overlay (Ctrl+Shift+G)**: Press this global hotkey to show or hide the Swing HUD window displaying the camera feed and landmarks.
- **Repositioning HUD**: Click and drag anywhere on the HUD panel to move the floating window across your desktop screen.

---

## 2. Problems Encountered During Development

- **The "java being anti cv" bottleneck**: Initially we planned on using a method called convex hulling to detect fingers as before libraries such as mediapipe that was a common finger detection method. We then realised that convexHull was terrible and we became primary sources as to why libraries such as MediaPipe have been developed. We then tried to bend the rules of the project by calling a python script running mediapipe detection. Our idea was quickly shut down. Our final option was taking the c++ library that was wrapped for python and creating a Java Native Interface from it using a tool called bezelisk. This process took a total of 5 hours, 10 gigabites of Shaurya Mathur's hotspot and our mental stanities. Once we finally got bezel instealled and working with our library we learned that c++ itself is annoying. Initially, running the Mediapipe tracker and the JNI JVM invocation on the same thread blocked the Swing event thread, resulting in a frozen UI. We resolved this by spawning a separate native execution thread (`std::thread`) in the DLL, calling `AttachCurrentThread` to register it with the Java VM, and passing frames asynchronously.


- **Accidental Button Presses / Click Jitter**: Frame drops and small hand twitches caused rapid click press-and-release loops, flooding the OS with mouse events. We introduced isolated click state tracking, double-click cooldowns (`COOLDOWN_MS`), and distance release hysteresis (`PINCH_RELEASE_THRESHOLD`) to ensure clicks remain stable and smooth.

---

## 3. Features Not Able to Implement and Why

- **Double-Click Gesture**: We attempted to map a quick double-tap pinch to trigger a double-click event. However, due to camera frame-rate limitations (typically 30 FPS), rapid finger movements are often blurred and missed by MediaPipe. Relying on hand detection speed was too inconsistent, so we opted to let the OS handle double-clicks naturally when the user quickly performs two standard pinch gestures.
- **Fully Custom Calibration Screen**: We planned to implement a GUI wizard to calibrate individual user hand sizes. However, doing so dynamically required extensive inter-process coordination between the JNI thread and Swing frames. Because of project time constraints and AP CSA submission timelines, we integrated a real-time **telemetry telemetry display** directly on the HUD (showing raw pinch distances) to let users visually calibrate their distance thresholds instead.
- **Scratchpad**: Durring Ideation we planned to add a scratchpad feature that let users open a scratchpad and write using an image detection model. We then subsequently learned of two problems. Machine learning in java is literally just c++ libraries wrapped for java and many vision libraries are unsupported by java. Due to the limitation of us being unable to implement pure java vision in time and knowing that compiling another JNI would take over an hour per each run, we decided to "scratch" the scratchpad.
---

## 4. Reflection and What We Learned

Creating this project was a highly educational experience that bridged the gap between high-level Java programming and low-level native compilation:
- **JNI Architecture**: We learned how Java interacts with native C++ dynamic libraries (`.dll`) via Java Native Interface, including thread attachment, JVM global references, and mapping complex types (such as passing raw JPEG buffers and coordinate arrays).
- **Hysteresis & Signal Filtering**: We learned how raw sensor inputs require math filters (like exponential moving average smoothing) and hysteresis threshold bounds to turn noisy hardware signals into smooth, reliable UI controls.
- **Build Systems**: Managing compilation dependencies across Bazel (C++) and Maven (Java) provided valuable hands-on experience in working with modern enterprise build pipelines.