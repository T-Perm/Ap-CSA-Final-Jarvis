# Chapter 5 - CameraInput

**Audience**: Person A. **Time**: 20 min.

Wrap `VideoCapture` in a clean API so the main loop doesn't import
OpenCV directly.

## CameraInput.java

`src/main/java/com/starkmouse/input/CameraInput.java`:

```java
package com.starkmouse.input;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

/**
 * Wraps an OpenCV VideoCapture in a simple API.
 */
public class CameraInput {

    /** Camera index (0 = default webcam). */
    private final int cameraIndex;

    /** Underlying capture; null until start(). */
    private VideoCapture capture;

    /** Reused frame buffer to avoid per-frame allocation. */
    private final Mat buffer = new Mat();

    /**
     * @param cameraIndex 0-based index
     */
    public CameraInput(int cameraIndex) {
        this.cameraIndex = cameraIndex;
    }

    /**
     * Open the camera.
     * @throws RuntimeException if camera cannot be opened
     */
    public void start() {
        capture = new VideoCapture(cameraIndex);
        if (!capture.isOpened()) {
            throw new RuntimeException("Could not open camera " + cameraIndex);
        }
        // warm up - first few frames are usually black
        for (int i = 0; i < 5; i++) {
            capture.read(buffer);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Grab the next frame.
     * @return the latest frame, or null on read failure
     */
    public Mat grabFrame() {
        if (capture == null || !capture.isOpened()) return null;
        boolean ok = capture.read(buffer);
        return ok && !buffer.empty() ? buffer : null;
    }

    /** Release the camera so other apps can use it. */
    public void stop() {
        if (capture != null) {
            capture.release();
            capture = null;
        }
    }

    /** @return true if camera is open */
    public boolean isRunning() {
        return capture != null && capture.isOpened();
    }
}
```

## Imports

| Import | What |
|--------|------|
| `org.opencv.core.Mat` | frame buffer |
| `org.opencv.videoio.VideoCapture` | webcam handle |

## Design notes

- `buffer` is reused across calls. `cap.read(buffer)` writes into the
  same Mat each time instead of allocating a new one. OpenCV does this
  pattern a lot - reuse Mats, don't create them per-frame.
- Returning a shared Mat means the caller must not hold onto it past
  the next `grabFrame()` call. For us that's fine - the loop processes
  each frame before grabbing the next.
- The warm-up loop in `start()` handles the "first 5 frames are black"
  issue once instead of every caller dealing with it.

## Test it

Throwaway main in `MainApp.java` (you'll replace it later):

```java
public static void main(String[] args) {
    nu.pattern.OpenCV.loadLocally();
    CameraInput cam = new CameraInput(0);
    cam.start();
    for (int i = 0; i < 50; i++) {
        Mat f = cam.grabFrame();
        System.out.println(f == null ? "null" : f.size().toString());
        try { Thread.sleep(33); } catch (InterruptedException ignored) {}
    }
    cam.stop();
}
```

`mvn package`, `java -jar target/...`, see 50 `640x480` (or similar)
lines. If you see `null` repeatedly the camera isn't delivering.

Commit: `add CameraInput`. Move to chapter 6.
