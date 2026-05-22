# API reference 2 - java.awt.Robot and screen

Textbook reference for controlling the OS mouse and querying the screen.
All built into the JDK (no dependency).

`Robot` synthesizes input events at the OS level - the OS believes a
real mouse moved or clicked.

---

## new Robot()

**What it does**: creates a Robot. Throws `AWTException` if the
platform doesn't support it (essentially never on a normal desktop).

**Signature**: `new Robot() throws AWTException`

**Example**:

```java
import java.awt.Robot;
import java.awt.AWTException;

try {
    Robot robot = new Robot();
} catch (AWTException e) {
    System.err.println("Robot not supported here");
}
```

**Why the throws**: `AWTException` is a *checked* exception - Java
forces you to either `catch` it or declare `throws` on your method.
You'll either wrap it in a try/catch (in a constructor) or add
`throws AWTException` to your method signature.

**Try it**: Write a method `makeRobot()` that returns a Robot or null,
catching the exception internally so callers don't have to. (This is a
common pattern: hide a checked exception behind a null return when the
failure is unrecoverable anyway.)

---

## robot.mouseMove

**What it does**: moves the cursor to absolute screen coordinates.

**Signature**: `void mouseMove(int x, int y)`

Coordinates are screen pixels, origin top-left. (0,0) is the top-left
corner of the primary monitor.

**Example**:

```java
robot.mouseMove(500, 300);   // cursor jumps to (500, 300)
```

**Try it**: Write a loop that moves the cursor smoothly from (0,0) to
(800,600) over one second (e.g. 60 steps with `Thread.sleep(16)`
between). Watch the cursor glide. This teaches you that animation =
many small moves over time.

---

## robot.mousePress / mouseRelease

**What they do**: press or release a mouse button. A click is press
then release.

**Signatures**:
```java
void mousePress(int buttons)
void mouseRelease(int buttons)
```

`buttons` is a bitmask from `java.awt.event.InputEvent`:
- `InputEvent.BUTTON1_DOWN_MASK` - left
- `InputEvent.BUTTON2_DOWN_MASK` - middle
- `InputEvent.BUTTON3_DOWN_MASK` - right

**Example - a left click**:

```java
import java.awt.event.InputEvent;

robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
```

**Drag** = press, move, release:

```java
robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
robot.mouseMove(600, 400);
robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
```

**Try it**: Write `leftClick()` and `rightClick()` helper methods.
Then write `doubleClick()` (two left clicks with a tiny sleep between).
Test by clicking on a desktop icon to select it.

---

## Toolkit.getDefaultToolkit().getScreenSize()

**What it does**: returns the primary screen's dimensions.

**Signature**: `Dimension Toolkit.getDefaultToolkit().getScreenSize()`

`Dimension` has public `.width` and `.height` int fields.

**Example**:

```java
import java.awt.Dimension;
import java.awt.Toolkit;

Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
int w = screen.width;    // e.g. 1920
int h = screen.height;   // e.g. 1080
```

**Why you need it**: your camera is 640x480 but the screen is 1920x1080.
To map a hand position in the camera to a cursor position on screen,
you scale: `screenX = cameraX / 640.0 * 1920`.

**Try it**: Write a method `mapToScreen(int camX, int camY, int camW,
int camH)` that returns the corresponding screen `Point`. Test by
feeding it the center of the camera (320, 240) and checking it returns
roughly screen center.

---

## Coordinate mapping with mirror (worked example)

The webcam shows a mirror image - move your hand right, the blob moves
left in the image. To make the cursor intuitive, flip x.

**Full mapping**:

```java
Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
int sw = screen.width, sh = screen.height;
int camW = 640, camH = 480;

void moveTo(int camX, int camY) {
    double mirroredX = camW - camX;             // flip horizontally
    int screenX = (int)(mirroredX / camW * sw);
    int screenY = (int)((double) camY / camH * sh);
    robot.mouseMove(screenX, screenY);
}
```

**Try it**: Implement `moveTo` above. Feed it the four corners of the
camera (0,0), (640,0), (0,480), (640,480) and verify the cursor goes
to the *mirrored* screen corners (top-left camera -> top-right screen,
because of the flip).

---

## Smoothing (the jitter fix)

The detected position wiggles frame-to-frame. Raw mapping = twitchy
cursor. Exponential smoothing blends old and new:

```
smoothed = smoothed * factor + target * (1 - factor)
```

factor near 1 = very smooth but laggy; near 0 = responsive but jittery.

**Worked example**:

```java
double smoothedX = sw / 2.0, smoothedY = sh / 2.0;
double FACTOR = 0.6;

void moveSmoothed(int targetScreenX, int targetScreenY) {
    smoothedX = smoothedX * FACTOR + targetScreenX * (1 - FACTOR);
    smoothedY = smoothedY * FACTOR + targetScreenY * (1 - FACTOR);
    robot.mouseMove((int) smoothedX, (int) smoothedY);
}
```

**Why it works**: each frame the cursor moves only 40% of the way to
the new target. Sudden 1-pixel jitter gets averaged out across frames.
Real motion still gets there in ~5 frames.

**Try it**: Make a test where the "target" jumps randomly by +/-3 px
every frame (simulating jitter) around a fixed point. Compare cursor
behavior with FACTOR=0 (raw, jittery) vs FACTOR=0.6 (smooth). Watch the
difference.

---

## Debouncing (the click-storm fix)

If a gesture fires every frame and you click on every frame, you get
30 clicks/sec. Debounce: ignore clicks that come too soon after the
last one.

**Worked example**:

```java
long lastClick = 0;
long DEBOUNCE_MS = 400;

boolean canClick() {
    long now = System.currentTimeMillis();
    if (now - lastClick < DEBOUNCE_MS) return false;
    lastClick = now;
    return true;
}

void clickIfAllowed() {
    if (!canClick()) return;
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
}
```

**Try it**: Implement `canClick()`. Then write a loop that calls
`clickIfAllowed()` 100 times in a tight loop (no sleep) and count how
many actual clicks happen (add a counter inside the press). With 400ms
debounce and a fast loop, you should get very few - because most calls
are within 400ms of the last.

---

## Capstone exercise

Build a tiny "controller" object with:
- `setScreenAndCamera(...)` to learn the dimensions
- `move(camX, camY)` that mirrors, maps, smooths, and moves
- `click()` that debounces

Then write a fake test main that feeds it a circle of camera
coordinates (use sin/cos) and confirm the cursor traces a smooth circle
on screen. This is MouseController (chapter 8) - compare afterward.
