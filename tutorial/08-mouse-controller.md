# Chapter 8 - MouseController

**Audience**: Person B. **Time**: 1 hour.

Drive the OS mouse using `java.awt.Robot`. Handle screen mapping,
mirroring (the webcam is a mirror image), exponential smoothing (to
fight jitter), and click debouncing.

## What you need to know about Robot

`java.awt.Robot` synthesizes input events at the OS level. The OS
thinks a real mouse moved or clicked. It's built into the JDK.

Constructor throws `AWTException` if Robot isn't supported on the
platform (mostly never happens on Windows/Mac/Linux desktops).

Key methods:

```java
Robot r = new Robot();
r.mouseMove(int x, int y);          // absolute screen coords
r.mousePress(int buttons);          // bitmask of buttons to press
r.mouseRelease(int buttons);

// Button bitmasks (from java.awt.event.InputEvent):
InputEvent.BUTTON1_DOWN_MASK        // left
InputEvent.BUTTON2_DOWN_MASK        // middle
InputEvent.BUTTON3_DOWN_MASK        // right

// Screen size:
Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
int w = d.width, h = d.height;
```

## MouseController.java

`src/main/java/com/starkmouse/control/MouseController.java`:

```java
package com.starkmouse.control;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;

/**
 * Drives the OS mouse via java.awt.Robot. Maps camera coords to
 * screen coords, mirrors x for intuitive feel, applies exponential
 * smoothing, debounces clicks.
 */
public class MouseController {

    private final Robot robot;
    private int frameWidth = 640;
    private int frameHeight = 480;
    private final int screenWidth;
    private final int screenHeight;

    /** Smoothing factor 0..1 (higher = smoother but more lag). */
    private static final double SMOOTHING = 0.6;
    private double smoothedX;
    private double smoothedY;

    /** Min ms between clicks to avoid click storms. */
    private static final long CLICK_DEBOUNCE_MS = 400;
    private long lastClickTime = 0;

    /** @throws AWTException if Robot is unavailable */
    public MouseController() throws AWTException {
        this.robot = new Robot();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        this.screenWidth = screen.width;
        this.screenHeight = screen.height;
        this.smoothedX = screenWidth / 2.0;
        this.smoothedY = screenHeight / 2.0;
    }

    /**
     * Tell the controller the camera dimensions for coord mapping.
     * @param w camera width
     * @param h camera height
     */
    public void setFrameSize(int w, int h) {
        this.frameWidth = w;
        this.frameHeight = h;
    }

    /**
     * Move cursor based on camera coords. Mirrors x.
     */
    public void moveCursor(int cameraX, int cameraY) {
        double mirroredX = frameWidth - cameraX;
        double targetX = (mirroredX / frameWidth) * screenWidth;
        double targetY = ((double) cameraY / frameHeight) * screenHeight;

        smoothedX = smoothedX * SMOOTHING + targetX * (1.0 - SMOOTHING);
        smoothedY = smoothedY * SMOOTHING + targetY * (1.0 - SMOOTHING);

        robot.mouseMove((int) smoothedX, (int) smoothedY);
    }

    public void leftClick() {
        if (!debounceOk()) return;
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public void rightClick() {
        if (!debounceOk()) return;
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    private boolean debounceOk() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) return false;
        lastClickTime = now;
        return true;
    }
}
```

## Imports

| Import | What |
|--------|------|
| `java.awt.AWTException` | thrown by `new Robot()` |
| `java.awt.Dimension` | screen size return value |
| `java.awt.Robot` | the input-synthesis API |
| `java.awt.Toolkit` | gateway to `getScreenSize()` |
| `java.awt.event.InputEvent` | button bitmask constants |

All `java.awt` - it's the JDK's GUI toolkit, predates Swing.

## Why mirror

Webcam shows a mirror image. Moving your hand right -> the detected
blob moves left in the image. So we flip x:

```
mirroredX = frameWidth - cameraX;
```

After this, hand right -> cursor right.

## Why smoothing

The detected centroid wiggles by 1-2 px every frame even when your
hand is still. Direct mapping makes the cursor twitchy.

Exponential smoothing blends the new target with the previous smoothed
position:

```
smoothed = smoothed * 0.6 + target * 0.4
```

0.6 = "60% of where I was, 40% of where I want to be." After ~5 frames
the cursor catches up. Tradeoff: smaller smoothing = more responsive
but jittery; larger = smoother but laggy. 0.6 is the sweet spot.

## Why debounce

Without debounce, every frame a 2-finger gesture is detected fires a
click. At 30 FPS that's 30 clicks/sec. Catastrophic.

`debounceOk()` returns false if less than 400ms has passed since the
last click. So a held 2-finger gesture clicks once, not 30 times.

## Test

Quick standalone main:

```java
public static void main(String[] args) throws Exception {
    MouseController mc = new MouseController();
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 5000) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        int x = (int)(600 + 200 * Math.cos(t * 2));
        int y = (int)(400 + 200 * Math.sin(t * 2));
        // Bypass smoothing by passing screen-space coords through
        // setFrameSize(screenWidth, screenHeight) trick
        mc.setFrameSize(mc.getClass().getDeclaredField("screenWidth")
            .getInt(mc), mc.getClass().getDeclaredField("screenHeight")
            .getInt(mc));
        mc.moveCursor(x, y);
        Thread.sleep(16);
    }
}
```

That reflection is ugly - just do this simpler test:

```java
public static void main(String[] args) throws Exception {
    MouseController mc = new MouseController();
    // Pretend the camera is the same size as the screen so mapping is 1:1
    mc.setFrameSize(1920, 1080);
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 5000) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        int x = (int)(960 + 200 * Math.cos(t * 2));
        int y = (int)(540 + 200 * Math.sin(t * 2));
        // mirror happens inside, so feed inverted x
        mc.moveCursor(1920 - x, y);
        Thread.sleep(16);
    }
}
```

Cursor goes in a circle for 5 seconds. Try not to fight it.

Commit: `add MouseController`. Move to chapter 9.
