# Chapter 11 - MainApp + the capture loop

**Audience**: both, mostly Person B. **Time**: 1-1.5 hours.
**You will write**: the real `MainApp.java` (replacing the skeleton).

This wires every class together and runs the capture-detect-act loop
on a background thread.

Read the threading section of `api-reference-4` first (Thread, volatile,
Thread.sleep, invokeLater, the EDT rule).

## Goal

`main` loads OpenCV, then on the EDT builds the controllers and starts
a background loop that grabs frames, detects gestures, applies them,
and updates the tray.

---

## Concept: the EDT and where things run

- Swing/AWT objects (tray, later the scratchpad window) should be set
  up on the Event Dispatch Thread. Use
  `SwingUtilities.invokeLater(() -> ...)` from `main`.
- The capture loop is heavy and must NOT run on the EDT or it freezes
  the UI. It runs on its own `Thread`.
- The loop is allowed to call your controllers' methods (moveCursor,
  setState, etc.) because those don't block the EDT.

---

## Concept: starting OpenCV first

`OpenCV.loadLocally()` must run before any OpenCV class is used. Put it
as the very first line of `main`, before `invokeLater`.

---

## Concept: daemon thread + volatile flag

The loop runs in a `Thread` you start. Mark it daemon so it dies with
the app. Control it with a `volatile boolean running` so the loop sees
when you set it false during shutdown. (Both covered in api-reference-4.)

---

## Build MainApp.java

Replace `src/main/java/com/starkmouse/app/MainApp.java`.

### Fields

- `CameraInput camera = new CameraInput(0)`
- `GestureDetector[] detectors` containing `new ColorBlobDetector()` and
  `new HandContourDetector()`
- `int detectorIndex = 0`
- `MouseController mouse` (set in launch)
- `ScratchpadController scratchpad` (the stub for now)
- `GestureMapper mapper` (set in launch)
- `TrayController tray = new TrayController()`
- `HotkeyController hotkey` (set in launch)
- `Thread loopThread`
- `volatile boolean running = false`

Add a private helper `GestureDetector detector()` returning
`detectors[detectorIndex]` (so F1/F2 switching takes effect next frame).

### main(String[] args)

1. `OpenCV.loadLocally();`
2. `SwingUtilities.invokeLater(() -> new MainApp().launch());`

### launch()

1. Create the MouseController in a try/catch (AWTException). On failure,
   print an error and return.
2. Create the ScratchpadController stub.
3. Create the GestureMapper(mouse, scratchpad).
4. `tray.install()`; if it returns false, warn but continue.
5. `tray.onExit(this::shutdown)` so the Exit menu runs your shutdown.
6. `tray.setState(ACTIVE)`.
7. Create the HotkeyController with a callback that: flips
   `mapper.setEnabled(!mapper.isEnabled())`, updates the tray icon
   (ACTIVE/PAUSED), and shows a notification. Then `hotkey.install()`.
8. `startLoop()`.

### startLoop()

1. `camera.start()`.
2. Grab one frame; if non-null, `mouse.setFrameSize(frame.cols(),
   frame.rows())` so cursor mapping uses the real resolution.
3. `running = true`.
4. Create `loopThread = new Thread(this::loop, "stark-mouse-loop")`,
   `setDaemon(true)`, `start()`.

### loop()

While `running`:
1. `Mat frame = camera.grabFrame()`; if null, `sleep(30)` and continue.
2. `Gesture g = detector().detect(frame)`.
3. `mapper.apply(g)`.
4. If `g.getType() != NONE`, `tray.setState(ACTIVE)` (subtle "I see your
   hand" feedback).
5. `sleep(15)` to cap ~60 FPS.

### sleep(long ms)

A helper wrapping `Thread.sleep` in a try/catch (swallow the
InterruptedException) so the loop stays readable.

### shutdown()

1. `running = false`.
2. `loopThread.join(500)` in a try/catch (wait for the loop to stop).
3. `hotkey.remove()`, `camera.stop()`, `tray.remove()`.
4. `System.exit(0)`.

### Imports

Your own classes (`com.starkmouse.*`), plus:

| Import | For |
|--------|-----|
| `nu.pattern.OpenCV` | loadLocally |
| `org.opencv.core.Mat` | the frame |
| `javax.swing.SwingUtilities` | invokeLater |
| `java.awt.AWTException` | from MouseController constructor |

(`TrayController.IconState` - import the nested enum or qualify it.)

---

## Test - this is your MVP

```
mvn package
java -jar target/stark-mouse-1.0.0-jar-with-dependencies.jar
```

- Tray icon appears (cyan dot).
- Hold orange marker -> cursor follows.
- Ctrl+Shift+G -> icon gray, cursor frozen; again -> orange, resumes.
- Right-click tray -> Exit -> clean quit.

If this works you have a submittable project. Tag it:

```
git tag mvp-mouse && git push origin mvp-mouse
```

## Common errors

- Cursor flies to a corner -> wrong frame size; print
  `frame.cols()/rows()` and confirm setFrameSize got real values.
- `UnsatisfiedLinkError` -> VC++ redist missing (chapter 1).
- App won't quit -> hotkey not unregistered; check shutdown order.

## Checklist

- [ ] OpenCV.loadLocally is the first line of main
- [ ] UI setup on the EDT via invokeLater
- [ ] Loop on a daemon thread, controlled by volatile running
- [ ] setFrameSize uses real camera resolution
- [ ] Clean shutdown
- [ ] Javadoc everywhere

Commit: `wire main loop, end-to-end MVP`. Move to chapter 12.
