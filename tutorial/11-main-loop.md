# Chapter 11 - Main loop and threading

**Audience**: both, mostly Person B. **Time**: 1 hour.

Wire everything together in `MainApp`. The capture-detect-act loop
runs on its own thread.

## Threading rules

Java GUI rule: anything that touches Swing components must run on the
Event Dispatch Thread (EDT). Use `SwingUtilities.invokeLater`.

The capture loop is heavy work (grabbing frames, running detection) so
it goes on its own background thread. It can call `setEnabled(...)`
and similar setters on UI controllers because those just store values.

## MainApp.java

`src/main/java/com/starkmouse/app/MainApp.java` (replace the skeleton):

```java
package com.starkmouse.app;

import com.starkmouse.control.GestureMapper;
import com.starkmouse.control.HotkeyController;
import com.starkmouse.control.MouseController;
import com.starkmouse.control.TrayController;
import com.starkmouse.control.TrayController.IconState;
import com.starkmouse.detection.ColorBlobDetector;
import com.starkmouse.detection.Gesture;
import com.starkmouse.detection.GestureDetector;
import com.starkmouse.detection.HandContourDetector;
import com.starkmouse.input.CameraInput;
import com.starkmouse.scratchpad.ScratchpadController;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;

import javax.swing.SwingUtilities;
import java.awt.AWTException;

/**
 * Entry point. Owns the background capture-detect-act loop, the tray,
 * and the global hotkey.
 */
public class MainApp {

    private final CameraInput camera = new CameraInput(0);
    private final GestureDetector[] detectors = {
        new ColorBlobDetector(),
        new HandContourDetector()
    };
    private int detectorIndex = 0;
    private MouseController mouse;
    private ScratchpadController scratchpad;
    private GestureMapper mapper;
    private final TrayController tray = new TrayController();
    private HotkeyController hotkey;

    private Thread loopThread;
    private volatile boolean running = false;

    public static void main(String[] args) {
        OpenCV.loadLocally();
        SwingUtilities.invokeLater(() -> new MainApp().launch());
    }

    private void launch() {
        try {
            mouse = new MouseController();
        } catch (AWTException e) {
            System.err.println("Robot unavailable, cannot continue.");
            return;
        }

        scratchpad = new ScratchpadController();   // stub for now
        mapper = new GestureMapper(mouse, scratchpad);

        if (!tray.install()) {
            System.err.println("Tray unavailable. App will exit silently on Ctrl+C.");
        }
        tray.onExit(this::shutdown);
        tray.setState(IconState.ACTIVE);

        hotkey = new HotkeyController(() -> {
            mapper.setEnabled(!mapper.isEnabled());
            tray.setState(mapper.isEnabled() ? IconState.ACTIVE : IconState.PAUSED);
            tray.notify("Stark Mouse", mapper.isEnabled() ? "Active" : "Paused");
        });
        hotkey.install();

        startLoop();
    }

    private GestureDetector detector() {
        return detectors[detectorIndex];
    }

    private void startLoop() {
        camera.start();
        // Tell mouse the camera frame size for coord mapping
        Mat first = camera.grabFrame();
        if (first != null) {
            mouse.setFrameSize(first.cols(), first.rows());
        }

        running = true;
        loopThread = new Thread(this::loop, "stark-mouse-loop");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    private void loop() {
        while (running) {
            Mat frame = camera.grabFrame();
            if (frame == null) {
                sleep(30);
                continue;
            }
            Gesture g = detector().detect(frame);
            mapper.apply(g);

            // tray feedback - subtle "is the camera seeing my hand?"
            if (g.getType() != Gesture.Type.NONE) {
                tray.setState(IconState.ACTIVE);
            }

            sleep(15);  // cap at ~60 FPS
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private void shutdown() {
        running = false;
        try { if (loopThread != null) loopThread.join(500); }
        catch (InterruptedException ignored) { }
        if (hotkey != null) hotkey.remove();
        camera.stop();
        tray.remove();
        System.exit(0);
    }
}
```

## Imports

| Import | What |
|--------|------|
| `com.starkmouse.*` | your own classes |
| `nu.pattern.OpenCV` | `loadLocally()` for native libs |
| `org.opencv.core.Mat` | frame type for setFrameSize |
| `javax.swing.SwingUtilities` | `invokeLater` to hop to EDT |
| `java.awt.AWTException` | from `new MouseController()` |

## Things to notice

**`OpenCV.loadLocally()` is called BEFORE `invokeLater`.** Native libs
load early. Subsequent code can use OpenCV classes freely.

**`SwingUtilities.invokeLater(() -> new MainApp().launch())`**: starts
the app on the EDT. Construction and tray/hotkey install happen there.

**The loop runs on its own daemon thread.** `setDaemon(true)` means
when the main thread dies, the loop dies too (no need to manually stop).

**`volatile boolean running`**: `volatile` ensures the loop thread sees
the change when shutdown sets `running = false`. Without it the JVM
might cache the value and never exit.

**`camera.grabFrame()` returning a shared Mat**: as noted in chapter 5,
the Mat is reused. We don't hold onto it past one loop iteration.
Detection processes it immediately, then we move on.

## Test

```
mvn package
java -jar target/stark-mouse-1.0.0-jar-with-dependencies.jar
```

- Tray icon appears (cyan dot)
- Hold up orange marker - cursor follows
- Press Ctrl+Shift+G - icon goes gray, cursor stops following
- Press again - icon goes orange, cursor follows again
- Right-click tray, Exit

This is your minimum viable demo. If this works, you have a project
to submit even if the scratchpad falls through.

## Common errors

- **`UnsatisfiedLinkError`** when running: VC++ redist not installed.
- **App doesn't quit when window is closed**: there's no window. Use
  tray Exit menu, or kill the process.
- **Cursor flies to one corner**: `setFrameSize` was wrong. Camera
  resolution might not be 640x480. Print `first.cols(), first.rows()`
  to confirm.

Commit: `wire main loop, end-to-end MVP`. Move to chapter 12.
