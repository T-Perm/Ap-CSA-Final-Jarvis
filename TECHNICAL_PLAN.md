# Technical implementation plan

This is the master technical doc. It maps out:

- Every library we use and why
- Every class we build and what it does
- Every method's signature and behavior
- How the classes talk to each other
- The integration order so we don't build the wrong piece at the wrong time

Read this end to end at least once. Then refer back to it constantly while
building. Each person also has a role-specific doc:

- `docs/role_PERSON_A.md` - input + detection
- `docs/role_PERSON_B.md` - control + system
- `docs/role_PERSON_C.md` - UI + HUD

---

## Part 1: Libraries

### Why each library exists and what it gives us

| Library | Version | Why we need it | What we'd lose without it |
|---------|---------|----------------|---------------------------|
| `org.openpnp:opencv` | 4.9.0-0 | Camera capture + image processing | Cannot read webcam in Java; would need raw OS-level capture which is huge undertaking |
| `com.github.kwhat:jnativehook` | 2.2.2 | OS-level keyboard hook | Could not detect Ctrl+Shift+G when our window doesn't have focus |
| `java.awt.Robot` | built-in | Move mouse + click | Could not control the cursor (no replacement exists in standard Java) |
| `javax.swing` | built-in | The HUD window | Could not show any window at all |
| `java.awt.Graphics2D` | built-in | Drawing the HUD | Could not draw any custom UI |
| `java.awt.SystemTray` | built-in | Tray icon | App would always need a visible window |

### Why specifically `org.openpnp:opencv` not regular OpenCV

The official OpenCV release requires manually installing native DLLs on each
machine and setting `-Djava.library.path`. The `org.openpnp` wrapper bundles
the native DLLs (Windows, Mac, Linux) inside the .jar so it Just Works on
any machine that has Java. Critical because we have 3 different laptops.

### Why JNativeHook specifically

It is the only well-maintained pure-Java OS-keyboard-hook library. The
alternative is JNI/JNA wrappers which are way more complicated. JNativeHook
"just works" via `GlobalScreen.registerNativeHook()`.

### Maven coordinates (already in pom.xml)

```xml
<dependency>
    <groupId>org.openpnp</groupId>
    <artifactId>opencv</artifactId>
    <version>4.9.0-0</version>
</dependency>
<dependency>
    <groupId>com.github.kwhat</groupId>
    <artifactId>jnativehook</artifactId>
    <version>2.2.2</version>
</dependency>
```

---

## Part 2: Package structure

```
com.starkmouse
|
|-- input           (Person A)
|   |-- CameraInput.java
|
|-- detection       (Person A)
|   |-- Gesture.java
|   |-- GestureDetector.java     (interface)
|   |-- ColorBlobDetector.java
|   |-- HandContourDetector.java
|
|-- control         (Person B)
|   |-- MouseController.java
|   |-- GestureMapper.java
|
|-- ui              (Person C)
|   |-- FrameConverter.java
|   |-- HudOverlay.java
|
|-- app             (shared, mainly Person C)
    |-- MainApp.java
```

The dependency direction is strict:

- `app` knows everyone
- `ui`, `control`, `detection` know `input` only (and `detection` knows itself)
- `input` knows nobody

Never reverse this. `CameraInput` should never import anything from `detection`,
and `Gesture` should never import anything from `control` or `ui`.

---

## Part 3: Class-by-class specification

This section is the contract between team members. If Person A changes
`Gesture`'s method names, Person B and C's code breaks. So we lock the
API here first, then everyone implements against it.

### 3.1 `Gesture.java` (Person A)

A simple, immutable data class. All fields are `final`. No setters.

**Fields**:
- `Type type` (the enum below)
- `int x`, `int y` (camera pixel coordinates)
- `double confidence` (0.0 to 1.0)

**Nested enum `Type`**:
- `NONE` - nothing detected
- `POINT` - move cursor
- `LEFT_CLICK` - left click
- `RIGHT_CLICK` - right click
- `PAUSE` - disable gesture-to-mouse mapping
- `RESUME` - re-enable mapping

**Methods**:
- Constructor: `Gesture(Type type, int x, int y, double confidence)`
- `static Gesture none()` - factory for the "nothing detected" sentinel
- Getters: `getType()`, `getX()`, `getY()`, `getConfidence()`
- `toString()` for debugging: `"POINT @ (320, 240) conf=0.85"`

**Why this design**:
- Immutable means safe to pass between threads
- Enum (not String constants) means the compiler catches typos
- `none()` factory is cleaner than returning `null` everywhere

### 3.2 `GestureDetector.java` (Person A)

The interface that allows hot-swapping detection algorithms.

```
interface GestureDetector {
    Gesture detect(Mat frame);
    String getName();
    default void calibrate() { }   // optional, default empty
}
```

**Why an interface**:
- We have 2 concrete implementations (color blob, hand contour)
- The main loop should not know or care which one is active
- This is the project's primary showcase of polymorphism for the rubric

### 3.3 `ColorBlobDetector.java` (Person A) - Tier 1

Tracks a single brightly-colored marker.

**Algorithm**:
1. Convert BGR frame to HSV color space (`Imgproc.cvtColor`)
2. Threshold to a binary mask of "in-range" pixels (`Core.inRange`)
3. Find all contours (`Imgproc.findContours`)
4. Pick the contour with the largest area
5. If too small (< 400 px²), return `Gesture.none()`
6. Compute centroid from image moments
7. Return `Gesture(POINT, cx, cy, confidence)` where confidence scales with area

**Constants to tune**:
- `LOWER_BOUND = new Scalar(5, 100, 100)` - HSV lower (orange)
- `UPPER_BOUND = new Scalar(20, 255, 255)` - HSV upper
- `MIN_BLOB_AREA = 400.0`

**Fields**:
- 2 reusable `Mat` buffers (`hsv` and `mask`) so we don't allocate per frame

**Methods**:
- `detect(Mat frame)` - returns Gesture
- `getName()` - returns "Color Blob"

### 3.4 `HandContourDetector.java` (Person A) - Tier 2 stretch

Tracks a bare hand and counts extended fingers.

**Algorithm**:
1. Convert BGR to YCrCb (better for skin detection than HSV)
2. inRange to threshold skin-tone pixels
3. Morphological open + close to clean up noise (`Imgproc.morphologyEx`)
4. Find the largest contour - this is the hand
5. Compute the convex hull (`Imgproc.convexHull`)
6. Compute convexity defects (`Imgproc.convexityDefects`) - the valleys
   between fingers
7. For each defect: filter by depth (> 20 px) and angle (< 90 deg)
8. Valid defects + 1 = finger count (because N fingers create N-1 valleys)
9. Map finger count to gesture type:
   - 0 (fist) -> PAUSE
   - 1 -> POINT
   - 2 -> LEFT_CLICK
   - 3 -> RIGHT_CLICK
   - 4-5 (open palm) -> RESUME
10. Compute centroid via moments, same as ColorBlobDetector

**Constants**:
- `LOWER_BOUND = new Scalar(0, 133, 77)` - YCrCb skin lower
- `UPPER_BOUND = new Scalar(255, 173, 127)` - YCrCb skin upper
- `MIN_HAND_AREA = 5000.0`
- `MAX_FINGER_ANGLE_DEG = 90.0`
- `MIN_DEFECT_DEPTH = 20.0`

**Helper methods** (keep `detect()` short by extracting):
- `findLargestContour(Mat mask) -> MatOfPoint`
- `countFingers(MatOfPoint hand) -> int`
- `angleAtPoint(Point a, Point b, Point far) -> double` (law of cosines)
- `distanceSquared(Point p1, Point p2) -> double`
- `mapFingerCountToType(int fingers) -> Gesture.Type`

### 3.5 `CameraInput.java` (Person A)

Thin wrapper around OpenCV's VideoCapture.

**Fields**:
- `int cameraIndex` (final, set in constructor; 0 = default webcam)
- `VideoCapture capture` (null until start)
- `Mat buffer` (reused across calls, no per-frame allocation)

**Methods**:
- Constructor: `CameraInput(int cameraIndex)`
- `start()` - opens VideoCapture, throws RuntimeException if fails
- `grabFrame()` - returns latest Mat or null if read failed
- `stop()` - releases capture
- `isRunning()` - true if open and producing frames

**Why a wrapper class**:
- Hides OpenCV's resource lifecycle behind clean Java methods
- Makes testing easier (could write a `FakeCameraInput` for unit tests)

### 3.6 `MouseController.java` (Person B)

Drives the actual OS mouse using `java.awt.Robot`. Handles screen mapping,
mirroring, smoothing, click debouncing.

**Fields**:
- `Robot robot` - generates synthetic mouse events
- `int frameWidth, frameHeight` - camera dimensions (set via setFrameSize)
- `int screenWidth, screenHeight` - queried from Toolkit on construction
- `double smoothedX, smoothedY` - current smoothed cursor position
- `long lastClickTime` - for debouncing
- Constants: `SMOOTHING = 0.6`, `CLICK_DEBOUNCE_MS = 400`

**Methods**:
- Constructor: `MouseController() throws AWTException`
- `setFrameSize(int w, int h)` - call once after camera starts
- `moveCursor(int cameraX, int cameraY)`:
  - Mirror x: `mirroredX = frameWidth - cameraX`
  - Map to screen: `target = mirrored / frameWidth * screenWidth`
  - Smooth: `smoothed = smoothed * 0.6 + target * 0.4`
  - `robot.mouseMove((int) smoothedX, (int) smoothedY)`
- `leftClick()` - press + release `BUTTON1_DOWN_MASK`, debounced
- `rightClick()` - press + release `BUTTON3_DOWN_MASK`, debounced
- `private debounceOk()` - returns true if 400ms passed since last click

**Why mirror x**: webcam shows a mirror image. Moving hand right moves the
detected blob left in the image. Mirroring makes the cursor feel intuitive.

**Why smooth**: detected centroid wiggles by 1-2 px even when marker is still.
Direct mapping makes cursor twitchy. Exponential smoothing trades a tiny bit
of lag for steadiness.

### 3.7 `GestureMapper.java` (Person B)

Translates `Gesture` objects into `MouseController` calls. Owns the
"enabled" flag toggled by the global hotkey.

**Fields**:
- `MouseController mouse` (final, set in constructor)
- `boolean enabled` (default true)

**Methods**:
- Constructor: `GestureMapper(MouseController mouse)`
- `apply(Gesture g)`:
  - If `!enabled` or `g.getType() == NONE`, return
  - Switch on type, call appropriate MouseController method
- `setEnabled(boolean)`, `isEnabled()`

**Switch implementation** (using Java 17 switch expressions):
```
switch (g.getType()) {
    case POINT       -> mouse.moveCursor(g.getX(), g.getY());
    case LEFT_CLICK  -> mouse.leftClick();
    case RIGHT_CLICK -> mouse.rightClick();
    case PAUSE       -> setEnabled(false);
    case RESUME      -> setEnabled(true);
    default          -> { }
}
```

**Why this class exists separately**: Decouples detection from action. Want
to add user-customizable gestures later? Change only the mapper, not the
detector or controller.

### 3.8 `FrameConverter.java` (Person C)

Static utility class. Converts OpenCV Mat to Swing BufferedImage.

**Methods**:
- `static BufferedImage toBufferedImage(Mat mat)`:
  - Encode to PNG bytes via `Imgcodecs.imencode`
  - Decode bytes to BufferedImage via `ImageIO.read`
  - Return null on failure

**Why a separate class**: Keeps OpenCV out of HudOverlay. The HUD only knows
about BufferedImage. Easier to swap conversion strategy later if we need more
speed (direct pixel copy is faster but more complex).

### 3.9 `HudOverlay.java` (Person C)

Swing JPanel that renders camera feed + Stark HUD overlay. This is the
visual showcase of the project.

**Extends**: `javax.swing.JPanel`

**Fields**:
- `BufferedImage currentFrame` (latest camera frame, null at start)
- `Gesture currentGesture` (latest detection, default `Gesture.none()`)
- `double fps` (current FPS reading)
- `String detectorName` (current detector's display name)
- `boolean active` (whether mapping is enabled)
- Color constants: ACCENT_CYAN, ACCENT_ORANGE, ACCENT_GREEN, BG

**Methods**:
- Constructor sets preferred size 420x360 and background color
- `setFrame(BufferedImage)` - stores and calls `repaint()`
- `setGesture(Gesture)` - stores (repaint happens on next setFrame)
- `setFps(double)`, `setDetectorName(String)`, `setActive(boolean)`
- `@Override paintComponent(Graphics g)` - the big one

**`paintComponent` breakdown**:
1. Call `super.paintComponent(g)`
2. Cast to `Graphics2D`, enable antialiasing
3. Compute layout: `feedHeight = panelHeight - 60` (60 = status bar height)
4. Draw current frame stretched to (0, 0, panelWidth, feedHeight)
5. Call helper `drawScanGrid()` - faint cyan grid lines every 30px
6. Call helper `drawCornerBrackets()` - L-shapes at all 4 corners
7. If gesture != NONE, call helper `drawReticle(x, y)` at scaled coords
8. Call helper `drawStatusBar()` for the bottom strip
9. `g2.dispose()`

**Helper methods** (each does one thing):
- `drawScanGrid(Graphics2D, int width, int feedHeight)`
- `drawCornerBrackets(Graphics2D, int width, int feedHeight)`
- `drawReticle(Graphics2D, int x, int y)`
- `drawStatusBar(Graphics2D, int width, int height, int feedHeight)`

**Coordinate scaling for reticle**: gesture coords are in camera space
(640x480 typically), but the panel might be 420x360. Scale before drawing:
```
int rx = (int)((double) gesture.getX() / currentFrame.getWidth() * panelWidth);
int ry = (int)((double) gesture.getY() / currentFrame.getHeight() * feedHeight);
```

See `docs/HUD_DESIGN.md` for the full visual spec (colors, sizes, layout).

### 3.10 `MainApp.java` (Person C primary, Person B for tray/hotkey)

The wiring that ties everything together. Owns the background capture loop,
the window, the tray icon, the hotkey listener.

**Implements**: `NativeKeyListener` (from JNativeHook)

**Fields**:
- `CameraInput camera = new CameraInput(0)`
- `GestureDetector[] detectors = { new ColorBlobDetector(), new HandContourDetector() }`
- `int detectorIndex = 0`
- `GestureMapper mapper` (initialized in launch)
- `HudOverlay hud = new HudOverlay()`
- `JFrame hudFrame`
- `Thread loopThread`
- `volatile boolean running = false`
- `long lastFrameTime`, `double smoothedFps` for FPS calculation

**Methods**:
- `static main(String[])`:
  - `OpenCV.loadLocally()`
  - `SwingUtilities.invokeLater(() -> new MainApp().launch())`
- `launch()`:
  - Create MouseController + GestureMapper (catch AWTException)
  - `buildHudWindow()`
  - `installTrayIcon()`
  - `installGlobalHotkey()`
  - `startLoop()`
- `buildHudWindow()` - create undecorated, always-on-top JFrame
- `makeDraggable(JFrame)` - mouse listeners for dragging
- `installTrayIcon()` - SystemTray with Show/Hide/Exit menu items
- `installGlobalHotkey()` - register JNativeHook
- `@Override nativeKeyPressed(NativeKeyEvent)` - Ctrl+Shift+G toggle, F1/F2 detector switch
- `startLoop()` - spawn the background thread
- `loop()` - infinite capture-detect-act loop
- `updateFps()` - smoothed FPS calculation
- `sleep(long)` - swallows InterruptedException
- `shutdown()` - clean exit
- `detector()` - returns `detectors[detectorIndex]`, called every frame so
  F1/F2 take effect immediately

**The loop body**:
```
while (running) {
    Mat frame = camera.grabFrame();
    if (frame == null) { sleep(30); continue; }

    Gesture g = detector().detect(frame);
    mapper.apply(g);

    BufferedImage img = FrameConverter.toBufferedImage(frame);
    hud.setFrame(img);
    hud.setGesture(g);
    hud.setFps(updateFps());

    sleep(15);   // cap ~60 FPS
}
```

---

## Part 4: How the classes connect (data flow)

```
+--------------+                                 +------------------+
| Webcam       | --(BGR Mat)--> CameraInput --> |                  |
+--------------+                                 |                  |
                                                 |   GestureDetector|--(Gesture)-+
                                                 |   (active impl)  |            |
                                                 +------------------+            |
                                                                                 v
+--------------+                                                       +----------------+
| HudOverlay   | <-(BufferedImage)-- FrameConverter <-(Mat)-- loop     | GestureMapper  |
| (Swing JPanel)|<-(Gesture)----------- loop ---------------------------|                |
+--------------+                                                       +-------+--------+
                                                                               |
                                                                               v
                                                                       +----------------+
                                                                       | MouseController|--> OS mouse moves
                                                                       +----------------+


Side channels:
  JNativeHook ---(Ctrl+Shift+G)---> MainApp.nativeKeyPressed ---> mapper.setEnabled(...)
  JNativeHook ---(F1/F2)----------> MainApp.nativeKeyPressed ---> detectorIndex = ...
  SystemTray --(Show/Hide/Exit)---> MainApp's tray listeners ---> hudFrame.setVisible / shutdown
```

The main loop runs on its own thread. Everything else runs on the Swing EDT.
Setters on HudOverlay are safe to call from the loop thread because they
just store values and call `repaint()` (which is thread-safe).

---

## Part 5: Integration order (which week, what we build)

**Week 1 (May 18-25): Foundation**

Day 1-2 (everyone): Kickoff meeting, install tooling, build the skeleton
project, confirm `mvn package` works for all 3.

Day 3-5:
- Person A: `Gesture.java` + `GestureDetector.java` (interface) + read OpenCV docs
- Person B: `MouseController.java` with a test main that moves cursor in a circle
- Person C: blank `HudOverlay.java` extending JPanel, shown in a JFrame with a static test image

Day 6-7:
- Person A: `CameraInput.java` + test that opens webcam to a plain JFrame
- Person B: `GestureMapper.java` + test with hardcoded Gesture inputs
- Person C: HUD draws corner brackets + grid (no camera feed yet)

**Week 1 deliverable**: each person has their first component working in isolation.

**Week 2 (May 26 - Jun 1): First integration**

Day 1-2:
- Person A: `ColorBlobDetector.java` - test by printing centroid coords
- Person C: HUD displays the camera feed BufferedImage
- Person B: hotkey toggle works in a simple test app

Day 3-4: **First end-to-end integration**
- Build `MainApp.java` with all three lanes wired up
- Hold up an orange marker, watch cursor move
- This is the "minimum viable demo" - if we ship today we have a project

Day 5-7:
- Person A: starts `HandContourDetector.java`
- Person B: adds tray icon
- Person C: polishes HUD - reticle, status bar, scaled coordinates

**Week 2 deliverable**: working color-marker mouse with HUD and tray.

**Week 3 (Jun 2-7): Polish and second detector**

- Person A: tunes `HandContourDetector` until it reliably counts fingers
- Person B: tunes smoothing and debounce for cursor feel
- Person C: animations, FPS counter, calibration overlay

Day 5-7: lock features. No new code, only bug fixes and README writing.

**Week 4 (Jun 8-11): Submission**

- Everyone fills in README sections (problems hit, features cut, what learned)
- Final test on all 3 laptops
- Record backup demo video
- Verify Javadoc on every method
- Zip, submit

---

## Part 6: What can go wrong (and what to do)

| Problem | Most likely cause | Fix |
|---------|------------------|-----|
| `mvn package` hangs forever | First-time Maven download | Wait 2 min, check internet |
| App opens but feed is black | Webcam in use by another app (Zoom, Teams) | Quit other apps |
| OpenCV crashes on startup | `OpenCV.loadLocally()` not called | Call it FIRST THING in main |
| Mouse moves but wrong direction | Forgot to mirror x | Mirror in `MouseController.moveCursor` |
| Cursor twitchy | No smoothing or smoothing too low | Smoothing factor 0.6 |
| Clicks fire 10x per second | No debounce | 400ms debounce |
| HUD freezes | Loop is running on EDT | Loop must be on its own thread |
| HUD shows nothing | `setFrame` called but `repaint` not | Make sure setFrame calls repaint() |
| Hotkey doesn't work | JNativeHook not registered or wrong key code | Check VC_G not VK_G |
| Hand detection finds the wall | Wall is skin-toned (beige) | Adjust YCrCb bounds, better background |
| Hand detection misses your hand | Wrong skin range for your tone | Widen Cr/Cb bounds |
| Frame rate < 10 FPS | Mat->BufferedImage conversion is slow | Lower camera resolution or use direct pixel copy |

---

## Part 7: Anti-checklist (things NOT to do)

- Do NOT add ASL until the rest works perfectly. It's a stretch goal.
- Do NOT refactor "to be cleaner" the week of submission.
- Do NOT add features after Jun 7. Lock the code.
- Do NOT skip the per-person experiments in LEARNING_PLAN.md. They build
  the intuition you need to debug.
- Do NOT write code in `MainApp.java` that should live elsewhere. If you
  add detection logic to MainApp, move it to the detector class.
- Do NOT use raw arrays where ArrayLists make sense.
- Do NOT use generic variable names like `temp`, `data`, `obj`.
- Do NOT commit `target/` or `.idea/` (the .gitignore handles this, but
  double-check).

---

## Part 8: What the rubric will reward

Rubric weights as we understand them:

- Working program (40%) - the demo must work
- Code quality (25%) - clean class structure, sensible methods
- README (10%) - the four required sections
- Individual contribution (10%) - git history showing each person's work
- Comments / Javadoc (5%) - every method, every field
- Demo presentation (10%) - we can explain everything we built

Things we're doing explicitly for the rubric:
- Interface + multiple implementations = polymorphism showcase
- Lots of small methods with single responsibilities = readability
- Javadoc on EVERY method, even private ones = comment points
- Branches and PRs in git = individual contribution evidence
- Detector switching (F1/F2) = visible demonstration of polymorphism in action
- Both detectors implemented = shows we understood the abstraction
- HUD's `paintComponent` broken into 4 helper methods = good code structure
- $1 Recognizer implemented from scratch = "no ML libraries, just geometry"
  is a memorable demo answer
- AppMode state machine = clean handling of mode-dependent gesture meaning

---

## Part 9: Scratchpad feature (week 3 stretch)

The scratchpad is documented in detail in two companion files:

- `docs/SCRATCHPAD_TECHNICAL.md` - implementation spec for the new
  `scratchpad/` package, gesture state machine, $1 Recognizer algorithm
- `docs/SCRATCHPAD_DESIGN.md` - visual spec for the scratchpad window,
  matches the existing HUD aesthetic
- `docs/role_SCRATCHPAD.md` - how the team splits the scratchpad work

Short summary:

- New package `com/starkmouse/scratchpad/` with ~9 classes, ~700 lines
- New `AppMode` enum (MOUSE vs SCRATCHPAD) in `control/`
- `GestureMapper` updated to dispatch based on mode
- `HandContourDetector` updated to detect "hold gestures" (fist held 1s
  becomes PEN_DOWN, palm held 1s becomes PEN_UP, palm held 2s becomes
  SCRATCHPAD_TOGGLE)
- New Gesture types: PEN_DOWN, PEN_UP, SCRATCHPAD_TOGGLE
- $1 Unistroke Recognizer implemented from the paper - no ML

**This feature is week 3 only**. It is a stretch goal. If the core mouse
isn't solid by end of week 2, this entire feature gets cut. The mouse
alone is a complete A-grade submission.

Read those three files before week 3 starts.
