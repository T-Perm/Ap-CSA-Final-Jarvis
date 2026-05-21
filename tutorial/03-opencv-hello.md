# Chapter 3 - OpenCV hello world

**Audience**: both. **Time**: 20 min.

Prove the webcam opens in Java before building anything real.

## What you need to know about OpenCV

- `Mat` = matrix of pixels. Every image is a Mat.
- BGR not RGB (catches everyone once).
- `OpenCV.loadLocally()` must be called once before any OpenCV class
  is touched. Loads native DLLs from the jar.
- OpenCV classes hold native memory - call `.release()` when done.

## The test program

`src/main/java/com/starkmouse/app/OpenCVTest.java`:

```java
package com.starkmouse.app;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

/** Throwaway: opens webcam, saves one frame. Delete after this chapter. */
public class OpenCVTest {
    public static void main(String[] args) throws InterruptedException {
        OpenCV.loadLocally();

        VideoCapture cap = new VideoCapture(0);
        if (!cap.isOpened()) {
            System.err.println("Could not open camera 0");
            return;
        }

        Mat frame = new Mat();
        for (int i = 0; i < 10; i++) {
            cap.read(frame);
            Thread.sleep(50);
        }

        if (frame.empty()) {
            System.err.println("Got empty frame");
        } else {
            System.out.println("Frame: " + frame.size());
            Imgcodecs.imwrite("test-frame.png", frame);
            System.out.println("Saved test-frame.png");
        }

        cap.release();
        frame.release();
    }
}
```

## Imports

| Import | What |
|--------|------|
| `nu.pattern.OpenCV` | `loadLocally()` for native libs |
| `org.opencv.core.Mat` | image / matrix |
| `org.opencv.imgcodecs.Imgcodecs` | `imread` / `imwrite` |
| `org.opencv.videoio.VideoCapture` | webcam |

OpenCV classes live in `org.opencv.<submodule>.X` - `core`, `imgproc`,
`videoio`, `imgcodecs`, `objdetect`, etc. Guess the submodule, the IDE
will autocomplete.

## Notes

- First 5-10 frames from a fresh webcam are often black. Always read a
  few before using one.
- `Thread.sleep` throws `InterruptedException` - we just declare
  `throws` on main and forget it.
- Different camera index if 0 fails - try 1, 2.

## Build and run

```
mvn package
java -cp target/stark-mouse-1.0.0-jar-with-dependencies.jar com.starkmouse.app.OpenCVTest
```

`-cp` not `-jar` because the jar's main class is MainApp, not OpenCVTest.

Open `test-frame.png` to confirm.

## Common errors

- **`UnsatisfiedLinkError: can't find dependent libraries`** -> install
  Microsoft Visual C++ Redistributable 2015-2022 x64.
- **"Could not open camera 0"** -> another app has it (Zoom/Teams),
  privacy switch off, or Windows camera privacy denied. Try index 1.
- **All-black PNG** -> webcam cover, or bump loop iterations to 30.

Delete `OpenCVTest.java` and `test-frame.png` when done (or keep them
around to fiddle).

Person A -> chapter 4. Person B -> chapter 8.
