# Chapter 4 - Gesture data class and detector interface

**Audience**: Person A. **Time**: 30 min.

You're building two things: a data class to carry detection results,
and an interface that all detectors implement. These are the contracts
the rest of the project depends on.

## Gesture.java

`src/main/java/com/starkmouse/detection/Gesture.java`:

```java
package com.starkmouse.detection;

/**
 * Immutable record of one detected gesture.
 */
public class Gesture {

    /** All gesture types the system can produce. */
    public enum Type {
        /** Nothing detected. */
        NONE,
        /** Move cursor / track fingertip. */
        POINT,
        /** Left click. */
        LEFT_CLICK,
        /** Right click. */
        RIGHT_CLICK,
        /** Fist held 1s - pause in mouse mode, pen-down in scratchpad. */
        PEN_DOWN,
        /** Palm held 1s - resume in mouse mode, pen-up in scratchpad. */
        PEN_UP,
        /** Palm held 2s - toggle scratchpad window. */
        SCRATCHPAD_TOGGLE
    }

    private final Type type;
    private final int x;
    private final int y;
    private final double confidence;

    /**
     * @param type gesture type
     * @param x camera-space x coordinate
     * @param y camera-space y coordinate
     * @param confidence 0.0 to 1.0
     */
    public Gesture(Type type, int x, int y, double confidence) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }

    /** @return a NONE gesture */
    public static Gesture none() {
        return new Gesture(Type.NONE, 0, 0, 0.0);
    }

    /** @return gesture type */
    public Type getType() { return type; }
    /** @return x in camera pixels */
    public int getX() { return x; }
    /** @return y in camera pixels */
    public int getY() { return y; }
    /** @return confidence 0..1 */
    public double getConfidence() { return confidence; }

    @Override
    public String toString() {
        return String.format("%s @ (%d,%d) c=%.2f", type, x, y, confidence);
    }
}
```

**Why immutable** (all `final`, no setters): safe to pass between
threads; can't be mutated by accident; cleaner reasoning.

**Why an enum not String constants**: typo'd `"point"` vs `"POINT"`
is a runtime bug; typo'd `Type.point` is a compile error.

**`none()` factory**: cleaner than returning `null` everywhere. The
loop can do `if (g.getType() == Type.NONE) skip;` without null-checks.

No imports needed - everything used is in `java.lang`.

## GestureDetector.java

`src/main/java/com/starkmouse/detection/GestureDetector.java`:

```java
package com.starkmouse.detection;

import org.opencv.core.Mat;

/**
 * Strategy interface for detection algorithms. The main loop holds a
 * GestureDetector reference and doesn't know which concrete class is
 * running - that's how we hot-swap algorithms at runtime.
 */
public interface GestureDetector {

    /**
     * Analyze one frame.
     * @param frame current BGR frame
     * @return detected Gesture or Gesture.none()
     */
    Gesture detect(Mat frame);

    /**
     * @return short display name e.g. "Color Blob"
     */
    String getName();

    /** Optional setup hook. Default does nothing. */
    default void calibrate() { }
}
```

**Imports**: `org.opencv.core.Mat` because the parameter is a Mat.

**`default void calibrate()`**: Java 8+ interfaces allow default
methods. Implementations don't have to override it.

## Why this matters for the rubric

This is the polymorphism showcase. Tell the grader:

- `GestureDetector` is the interface
- We have two implementations: `ColorBlobDetector` and `HandContourDetector`
- The main loop has `GestureDetector active = ...` and calls
  `active.detect(frame)` without knowing which one it is
- F1/F2 swaps the implementation at runtime

That's textbook polymorphism with a real, demoable benefit.

## Lock the contract

Once you commit these two files, Person B's code starts depending on
them. Don't change the method names or the enum values without telling
them. If you need to add a new gesture type, that's fine - adding is
safe, renaming is not.

Commit: `add Gesture and GestureDetector interface`. Move to chapter 5.
