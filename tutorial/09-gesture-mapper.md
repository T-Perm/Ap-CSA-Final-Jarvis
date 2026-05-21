# Chapter 9 - GestureMapper

**Audience**: Person B. **Time**: 30 min (more for the AppMode part).

Translate Gestures into MouseController calls. The mapper also owns
the AppMode state machine that decides whether gestures move the
cursor or drive the scratchpad.

## AppMode.java

`src/main/java/com/starkmouse/control/AppMode.java`:

```java
package com.starkmouse.control;

/**
 * Top-level app state. Each mode reinterprets the same gestures.
 */
public enum AppMode {
    /** Default - gestures move cursor and click. */
    MOUSE,
    /** Scratchpad open - gestures draw and recognize. */
    SCRATCHPAD
}
```

## GestureMapper.java

`src/main/java/com/starkmouse/control/GestureMapper.java`:

```java
package com.starkmouse.control;

import com.starkmouse.detection.Gesture;
import com.starkmouse.scratchpad.ScratchpadController;

/**
 * Routes detected gestures to actions, dispatching based on AppMode.
 */
public class GestureMapper {

    private final MouseController mouse;
    private final ScratchpadController scratchpad;
    private AppMode mode = AppMode.MOUSE;
    private boolean enabled = true;

    /**
     * @param mouse the OS mouse controller
     * @param scratchpad the scratchpad controller (may be null until
     *                   chapter 16 wires it up - guard accordingly)
     */
    public GestureMapper(MouseController mouse, ScratchpadController scratchpad) {
        this.mouse = mouse;
        this.scratchpad = scratchpad;
    }

    public void apply(Gesture g) {
        if (g.getType() == Gesture.Type.NONE) return;

        // SCRATCHPAD_TOGGLE switches modes regardless of enabled state
        if (g.getType() == Gesture.Type.SCRATCHPAD_TOGGLE) {
            toggleMode();
            return;
        }

        if (!enabled) return;

        switch (mode) {
            case MOUSE -> applyMouseMode(g);
            case SCRATCHPAD -> applyScratchpadMode(g);
        }
    }

    private void toggleMode() {
        if (mode == AppMode.MOUSE) {
            mode = AppMode.SCRATCHPAD;
            if (scratchpad != null) scratchpad.show();
        } else {
            mode = AppMode.MOUSE;
            if (scratchpad != null) scratchpad.hide();
        }
    }

    private void applyMouseMode(Gesture g) {
        switch (g.getType()) {
            case POINT       -> mouse.moveCursor(g.getX(), g.getY());
            case LEFT_CLICK  -> mouse.leftClick();
            case RIGHT_CLICK -> mouse.rightClick();
            case PEN_DOWN    -> enabled = false;   // fist held = pause
            case PEN_UP      -> enabled = true;    // palm held = resume
            default          -> { }
        }
    }

    private void applyScratchpadMode(Gesture g) {
        if (scratchpad == null) return;
        switch (g.getType()) {
            case POINT    -> scratchpad.trackCursor(g.getX(), g.getY());
            case PEN_DOWN -> scratchpad.penDown();
            case PEN_UP   -> scratchpad.penUp();
            default       -> { }
        }
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public AppMode getMode() { return mode; }
}
```

## Imports

| Import | What |
|--------|------|
| `com.starkmouse.detection.Gesture` | Person A's data class |
| `com.starkmouse.scratchpad.ScratchpadController` | not yet written; we'll create the class in chapter 16. For now, you can comment out the import and the field, then add it back in chapter 16. |

If your IDE complains about the import not resolving, that's fine -
Person B is going to write `ScratchpadController` in chapter 16. You
can:

1. Stub it now: create an empty `ScratchpadController.java` with just
   `show()`, `hide()`, `trackCursor(int,int)`, `penDown()`, `penUp()`
   methods that do nothing
2. Or comment out the scratchpad parts and fill them in chapter 16

I recommend stubbing - then you can integrate end-to-end early.

## Stub for ScratchpadController

`src/main/java/com/starkmouse/scratchpad/ScratchpadController.java`:

```java
package com.starkmouse.scratchpad;

/** Stub. Filled in chapter 16. */
public class ScratchpadController {
    public void show() { System.out.println("[stub] scratchpad show"); }
    public void hide() { System.out.println("[stub] scratchpad hide"); }
    public void trackCursor(int x, int y) { /* noop */ }
    public void penDown() { System.out.println("[stub] pen down"); }
    public void penUp() { System.out.println("[stub] pen up"); }
}
```

Lets you wire and test the mapper today.

## Java 17 switch expressions

The `switch (...) -> { ... }` syntax is Java 14+ (we're on 17). Lets
us write one expression per case instead of `case X: doStuff(); break;`.
Cleaner and the compiler enforces exhaustiveness (won't compile if you
forget a case, when used as an expression).

We used it as a statement here (return type `void`) so `default -> { }`
is needed to keep the compiler happy.

## Test

```java
public static void main(String[] args) throws Exception {
    MouseController mouse = new MouseController();
    mouse.setFrameSize(640, 480);
    ScratchpadController stub = new ScratchpadController();
    GestureMapper mapper = new GestureMapper(mouse, stub);

    mapper.apply(new Gesture(Gesture.Type.POINT, 320, 240, 1.0));
    Thread.sleep(200);
    mapper.apply(new Gesture(Gesture.Type.LEFT_CLICK, 0, 0, 1.0));
    Thread.sleep(200);
    mapper.apply(new Gesture(Gesture.Type.SCRATCHPAD_TOGGLE, 0, 0, 1.0));
    // mode is now SCRATCHPAD, stub should print "[stub] scratchpad show"
    mapper.apply(new Gesture(Gesture.Type.PEN_DOWN, 0, 0, 1.0));
    // stub should print "[stub] pen down"
}
```

Run and verify.

Commit: `add AppMode, GestureMapper, ScratchpadController stub`. Move to
chapter 10.
