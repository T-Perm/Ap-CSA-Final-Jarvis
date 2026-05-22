# Chapter 5 - CameraInput

**Audience**: Person A. **Time**: 20 min.
**You will write**: `CameraInput.java`.

Wrap OpenCV's `VideoCapture` in a clean class so the rest of the project
never imports OpenCV's video API directly.

## Goal

A class with: a constructor taking a camera index, `start()`,
`grabFrame()` returning a `Mat` (or null on failure), `stop()`, and
`isRunning()`.

---

## Method: VideoCapture

The webcam reader.

- `new VideoCapture(int index)` - open camera by index (0 = default)
- `cap.isOpened()` -> boolean
- `cap.read(Mat dst)` -> boolean, writes the next frame into dst
- `cap.release()` - free the camera

Example (throwaway):

```java
VideoCapture cap = new VideoCapture(0);
Mat frame = new Mat();
cap.read(frame);
cap.release();
```

---

## Concept: reuse one Mat buffer

`cap.read(buffer)` writes into a Mat you provide. If you make a new Mat
every frame, you leak native memory fast. Instead, keep ONE Mat as a
field and read into it every time.

---

## Concept: webcam warmup

The first several frames from a freshly-opened camera are often black
(auto-exposure settling). Read ~5 throwaway frames in `start()` so
callers get good frames immediately.

---

## Build CameraInput.java

Create `src/main/java/com/starkmouse/input/CameraInput.java`.

### Fields

- `private final int cameraIndex` - set in constructor
- `private VideoCapture capture` - null until `start()`
- `private final Mat buffer = new Mat()` - the reused frame buffer

### Constructor

`CameraInput(int cameraIndex)` - store the index.

### start()

1. `capture = new VideoCapture(cameraIndex)`
2. If `!capture.isOpened()`, throw a `RuntimeException` with a clear
   message (so a missing camera fails loudly).
3. Warmup: loop ~5 times, `capture.read(buffer)` with a short
   `Thread.sleep(50)` between (wrap the sleep's `InterruptedException`).

### grabFrame()

1. If `capture` is null or not opened, return `null`.
2. `boolean ok = capture.read(buffer)`.
3. Return `buffer` if `ok && !buffer.empty()`, else `null`.

(You're returning the shared buffer - the caller must use it before the
next `grabFrame()`. Fine for our single-threaded loop.)

### stop()

If `capture` isn't null: `capture.release()` then set it to null.

### isRunning()

Return whether `capture` is non-null and opened.

### Imports

| Import | For |
|--------|-----|
| `org.opencv.core.Mat` | the buffer |
| `org.opencv.videoio.VideoCapture` | the camera |

---

## Test it

Throwaway main:

```java
nu.pattern.OpenCV.loadLocally();
CameraInput cam = new CameraInput(0);
cam.start();
for (int i = 0; i < 50; i++) {
    Mat f = cam.grabFrame();
    System.out.println(f == null ? "null" : f.size().toString());
    try { Thread.sleep(33); } catch (InterruptedException ignored) {}
}
cam.stop();
```

Expect 50 lines like `640x480`. Constant `null` means the camera isn't
delivering - try index 1, quit Zoom/Teams, check camera privacy.

## Checklist

- [ ] Mat buffer is a reused field
- [ ] `start()` throws if camera won't open
- [ ] `grabFrame()` returns null (not a crash) on failure
- [ ] Javadoc on every method
- [ ] 50 frame sizes print in the test

Commit: `add CameraInput`. Move to chapter 6.
