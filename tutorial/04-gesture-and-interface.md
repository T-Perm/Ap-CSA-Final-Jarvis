# Chapter 4 - Gesture class + GestureDetector interface

**Audience**: Person A. **Time**: 30 min.
**You will write**: `Gesture.java` and `GestureDetector.java`.

These two files are the contract the whole project depends on. Small,
but lock them down - Person B builds against them.

## What you're building

1. `Gesture` - an immutable data object carrying one detection result
   (type, x, y, confidence), plus a nested enum of gesture types.
2. `GestureDetector` - an interface every detector implements.

No new APIs here - this is plain Java. The point is getting the shape
right so nothing downstream breaks.

---

## Concept: why immutable

"Immutable" = once constructed, fields never change. You do this by
making every field `final` and providing no setters. Benefits: safe to
share across threads (your capture loop is on a background thread),
can't be accidentally mutated, easier to reason about.

Example of the pattern (throwaway, not your class):

```java
public class Point2D {
    private final int x;
    private final int y;
    public Point2D(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
}
```

Note: fields `final`, set once in constructor, only getters.

---

## Concept: enum vs string constants

You could represent gesture types as strings ("POINT", "CLICK"). Don't.
A typo'd string is a runtime bug; a typo'd enum constant won't compile.
Enums also work in `switch`.

Example (throwaway):

```java
public enum Direction { NORTH, SOUTH, EAST, WEST }
Direction d = Direction.NORTH;
```

You can nest an enum inside a class - then it's referenced as
`Gesture.Type.POINT`.

---

## Build Gesture.java

Create `src/main/java/com/starkmouse/detection/Gesture.java`.

### The nested enum

Inside the class, declare `public enum Type` with these constants
(give each a one-line Javadoc comment - rubric):

- `NONE` - nothing detected
- `POINT` - move cursor / track fingertip
- `LEFT_CLICK`
- `RIGHT_CLICK`
- `PEN_DOWN` - fist held 1s (pause in mouse mode, pen-down in scratchpad)
- `PEN_UP` - palm held 1s (resume in mouse mode, pen-up in scratchpad)
- `SCRATCHPAD_TOGGLE` - palm held 2s, opens/closes scratchpad

### The fields

Four `private final` fields: a `Type`, an `int x`, an `int y`, a
`double confidence`. Give each a brief Javadoc.

### The constructor

Takes all four, assigns them with `this.x = x;` etc.

### A static factory for "nothing"

Write `public static Gesture none()` that returns a `Gesture` with type
`NONE`, coordinates 0,0, confidence 0.0. This lets detectors return
`Gesture.none()` instead of `null`, which avoids null checks everywhere.

### Getters

One getter per field: `getType()`, `getX()`, `getY()`, `getConfidence()`.

### toString (helps debugging)

Override `toString()` to return something like `POINT @ (320,240) c=0.85`.
Use `String.format("%s @ (%d,%d) c=%.2f", ...)`.

### Imports

None needed - everything is `java.lang`.

---

## Build GestureDetector.java

Create `src/main/java/com/starkmouse/detection/GestureDetector.java`.

It's an `interface`. Declare three things:

1. `Gesture detect(Mat frame)` - analyze a frame, return a Gesture
2. `String getName()` - short label for the detector
3. `default void calibrate() { }` - optional setup hook, empty default

### Concept: default method

Interfaces can provide a default implementation for a method using the
`default` keyword. Classes that implement the interface don't have to
override it. We use it so detectors that need no calibration can skip it.

Example (throwaway):

```java
interface Greeter {
    String greet();
    default void wave() { System.out.println("waves"); }
}
```

### Imports

Just `org.opencv.core.Mat` (the parameter type).

---

## Why this is the polymorphism showcase

On demo day: "`GestureDetector` is an interface. We have two
implementations - color blob and hand contour. The main loop holds a
`GestureDetector` reference and calls `detect()` without knowing which
class it actually is. F1/F2 swaps the implementation at runtime."

That's textbook polymorphism with a real payoff.

---

## Checklist

- [ ] `Gesture` fields all `final`, no setters
- [ ] `Type` enum has all 7 constants, each with Javadoc
- [ ] `none()` factory works
- [ ] `toString` prints readably
- [ ] `GestureDetector` is an `interface` with the 3 members
- [ ] Every method/field has Javadoc

## Lock the contract

Once committed, tell Person B "Gesture and GestureDetector are final."
Adding enum values later is safe; renaming methods is not.

Compare to `/reference/detection/` only after writing your own.

Commit: `add Gesture and GestureDetector`. Move to chapter 5.
