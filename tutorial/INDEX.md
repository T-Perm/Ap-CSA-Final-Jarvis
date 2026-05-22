# Stark Mouse - build tutorial

Walk through 18 chapters to build the full project. You write the code,
this tutorial shows what to write and explains the parts you wouldn't
know from CSA (java.awt APIs, OpenCV, JNativeHook, Maven, threading).

You already know: classes, methods, ArrayLists, inheritance, basic
exception handling, file I/O basics. What you'll learn here: which
package each class lives in, the APIs unique to this project, and how
to wire it all together.

## App scope

Whisper Flow vibe - no floating window, just a tray icon. Cursor moves
via webcam-tracked hand. Hold palm 2s -> scratchpad window for
drawing letters in the air. Recognized letters appear in the scratchpad.

## Roles

- **Person A**: input + detection + recognizer math
- **Person B**: control + system + scratchpad UI + integration

`MainApp.java` is shared but Person B drives it.

## Chapters

### Foundations (both, week 1)

- [01-setup.md](./01-setup.md) - Java, Maven, IDE
- [02-pom-and-skeleton.md](./02-pom-and-skeleton.md) - Project layout
- [03-opencv-hello.md](./03-opencv-hello.md) - Webcam opens in Java

### Detection (Person A, week 1-2)

- [04-gesture-and-interface.md](./04-gesture-and-interface.md) - Data class + interface
- [05-camera-input.md](./05-camera-input.md) - VideoCapture wrapper
- [06-color-blob-detector.md](./06-color-blob-detector.md) - HSV blob tracking
- [07-hand-contour-detector.md](./07-hand-contour-detector.md) - Skin + finger count + hold gestures

### Control + system (Person B, week 1-2)

- [08-mouse-controller.md](./08-mouse-controller.md) - java.awt.Robot
- [09-gesture-mapper.md](./09-gesture-mapper.md) - Gesture -> action, AppMode
- [10-tray-and-hotkey.md](./10-tray-and-hotkey.md) - SystemTray + JNativeHook

### Integration (both, week 2)

- [11-main-loop.md](./11-main-loop.md) - Wire it all together, threading
- [12-first-integration.md](./12-first-integration.md) - First MVP demo + detector switching

### Scratchpad (split, week 3)

- [13-stroke-recording.md](./13-stroke-recording.md) - Stroke + StrokeRecorder
- [14-dollar-one-recognizer.md](./14-dollar-one-recognizer.md) - $1 Recognizer
- [15-template-library.md](./15-template-library.md) - JSON persistence
- [16-scratchpad-window.md](./16-scratchpad-window.md) - The Swing window
- [17-mode-switching.md](./17-mode-switching.md) - Edge cases

### Polish (both, week 4)

- [18-polish-and-submit.md](./18-polish-and-submit.md) - README, Javadoc, demo prep

## Use the reference

`/reference/` has working copies of most classes. **Do NOT copy from
there.** It exists so you can compare AFTER writing your own version,
or unstick yourself when you've been blocked for 30+ minutes.

Note: the reference predates our HUD-removal decision, so it still
contains `ui/HudOverlay.java` etc. Ignore that. Also predates the
scratchpad - chapters 13-17 have no reference, you're writing fresh.

## Sanity check before starting

Both of you build the chapter 2 skeleton on your own machine before
splitting up. If one of you can't build, no scratchpad will help.

## When stuck

1. Re-read the chapter
2. Search the exact error
3. Check the reference for that file (then close it without copying)
4. Ask your teammate
5. Ask Claude with CLAUDE.md content + the code + the error pasted

30 minutes max, then ask for help.

## API references (textbook style)

If you want to learn the non-CSA APIs in isolation - method-by-method,
with an example and an exercise for each - read these before or
alongside the chapters. They explain the libraries; the chapters apply
them.

- [api-reference-1-opencv.md](./api-reference-1-opencv.md) - Mat,
  VideoCapture, cvtColor, inRange, findContours, contourArea, moments,
  convexHull, convexityDefects, morphologyEx, imwrite (Person A)
- [api-reference-2-robot.md](./api-reference-2-robot.md) - Robot,
  mouseMove, mousePress, screen size, coordinate mapping, smoothing,
  debouncing (Person B)
- [api-reference-3-tray-and-drawing.md](./api-reference-3-tray-and-drawing.md)
  - SystemTray, TrayIcon, notifications, Graphics2D, paintComponent,
  BufferedImage, GeneralPath (Person B)
- [api-reference-4-hotkey-threading-fileio.md](./api-reference-4-hotkey-threading-fileio.md)
  - JNativeHook, Thread, volatile, invokeLater, java.nio.file (both)

Recommended: Person A reads reference 1, Person B reads 2 and 3, both
read 4. Each entry ends with a "try it" exercise - do those in a
scratch file to build muscle memory before writing the real classes.
