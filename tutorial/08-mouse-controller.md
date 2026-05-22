# Chapter 8 - MouseController

**Audience**: Person B. **Time**: 1 hour.
**You will write**: `MouseController.java`.

Drive the OS cursor with `java.awt.Robot`. Handle screen mapping, the
mirror flip, smoothing, and click debounce.

Read `api-reference-2-robot.md` first if you haven't - this chapter
assumes Robot, mouseMove, screen size, smoothing, and debounce are
familiar.

## Goal

A class with a constructor (creates the Robot, queries screen size),
`setFrameSize(w,h)`, `moveCursor(camX, camY)`, `leftClick()`,
`rightClick()`.

---

## Methods recap

- `new Robot()` throws `AWTException`
- `robot.mouseMove(int screenX, int screenY)`
- `robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)` / `mouseRelease(...)`
  (BUTTON3 for right)
- `Toolkit.getDefaultToolkit().getScreenSize()` -> `Dimension` with
  `.width`, `.height`

---

## The three formulas you'll implement

**Mirror + map** (camera coords -> screen coords):

```
mirroredX = frameWidth - camX
screenX   = mirroredX / frameWidth  * screenWidth
screenY   = camY      / frameHeight * screenHeight
```

(Cast to double in the division or you'll get integer-division zeros.)

**Smoothing** (kill jitter):

```
smoothedX = smoothedX * 0.6 + screenX * 0.4
```

**Debounce** (kill click storms):

```
if (now - lastClick < 400ms) skip; else click and set lastClick = now
```

---

## Now build it

Create `src/main/java/com/starkmouse/control/MouseController.java`.

### Fields

- `private final Robot robot`
- `private int frameWidth = 640, frameHeight = 480` (defaults; updated
  via setFrameSize)
- `private final int screenWidth, screenHeight` (set in constructor)
- `private static final double SMOOTHING = 0.6`
- `private double smoothedX, smoothedY` (init to screen center)
- `private static final long CLICK_DEBOUNCE_MS = 400`
- `private long lastClickTime = 0`

### Constructor: `MouseController() throws AWTException`

1. `robot = new Robot();` (let the exception propagate - callers handle it)
2. Get screen size from Toolkit, store width/height.
3. Init smoothedX/Y to screen center (screenWidth/2.0, screenHeight/2.0).

### setFrameSize(int w, int h)

Store w and h into frameWidth/frameHeight.

### moveCursor(int camX, int camY)

1. Compute mirroredX, targetX, targetY using the formulas above (mind
   the double casts).
2. Update smoothedX/Y with the smoothing formula.
3. `robot.mouseMove((int) smoothedX, (int) smoothedY)`.

### leftClick() / rightClick()

Each: if `!debounceOk()` return; else press+release the right button
mask.

### private boolean debounceOk()

Implement the debounce formula. Returns true and updates lastClickTime
if enough time passed, else false.

### Imports

| Import | For |
|--------|-----|
| `java.awt.AWTException` | constructor throws |
| `java.awt.Dimension` | screen size |
| `java.awt.Robot` | the API |
| `java.awt.Toolkit` | getScreenSize |
| `java.awt.event.InputEvent` | button masks |

---

## Test

```java
MouseController mc = new MouseController();
mc.setFrameSize(1920, 1080);   // pretend camera == screen for a 1:1 test
long start = System.currentTimeMillis();
while (System.currentTimeMillis() - start < 5000) {
    double t = (System.currentTimeMillis() - start) / 1000.0;
    int x = (int)(960 + 200 * Math.cos(t * 2));
    int y = (int)(540 + 200 * Math.sin(t * 2));
    mc.moveCursor(1920 - x, y);   // feed inverted x since moveCursor mirrors
    Thread.sleep(16);
}
```

Cursor traces a smooth circle for 5 seconds.

## Checklist

- [ ] Constructor declares `throws AWTException`
- [ ] moveCursor mirrors, maps, smooths
- [ ] Double casts in the division (no integer-division bug)
- [ ] Clicks debounce
- [ ] Javadoc everywhere

Commit: `add MouseController`. Move to chapter 9.
