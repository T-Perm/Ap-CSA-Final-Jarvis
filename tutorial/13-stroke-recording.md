# Chapter 13 - Stroke + StrokeRecorder

**Audience**: Person A + B together. **Time**: 30 min.
**You will write**: `Stroke.java` and `StrokeRecorder.java`.

Capture the fingertip path while pen is down. This is the recognizer's
input.

## Goal

- `Stroke` - a sequence of points with jitter filtering.
- `StrokeRecorder` - records points into a Stroke when pen is down.

---

## Concept: java.awt.Point

A simple class with public `.x` and `.y` int fields (not getters).
`new Point(3, 5)` then `p.x`, `p.y`. Don't be surprised by the direct
field access - it's intentional.

---

## Concept: jitter filtering

The detected fingertip wiggles. If you record every point, the stroke
is noisy. Filter: only add a new point if it's at least a few pixels
from the previous one. Use squared distance to avoid a sqrt:

```
dx = x - last.x;  dy = y - last.y;
if (dx*dx + dy*dy < MIN*MIN) skip;
```

---

## Concept: unmodifiable views

When you expose your internal list, return
`Collections.unmodifiableList(list)` so callers can read but not mutate
your internal state. Defensive, clean.

---

## Build Stroke.java

`src/main/java/com/starkmouse/scratchpad/Stroke.java`.

Fields: a `private final List<Point> points = new ArrayList<>()`, and a
`private static final int MIN_DELTA_PX` (try 3).

Methods:
- `addPoint(int x, int y)`: if the list is non-empty, compute squared
  distance to the last point; skip if below MIN_DELTA_PX squared.
  Otherwise add `new Point(x, y)`.
- `getPoints()`: return an unmodifiable view of the list.
- `size()`, `isEmpty()`, `clear()`.

Imports: `java.awt.Point`, `java.util.ArrayList`, `java.util.List`,
`java.util.Collections`.

---

## Build StrokeRecorder.java

`src/main/java/com/starkmouse/scratchpad/StrokeRecorder.java`.

Fields: a `private final Stroke current = new Stroke()`, a
`private boolean penDown = false`.

Methods:
- `penDown()`: clear the current stroke, set penDown true.
- `penUp()`: set penDown false.
- `onPoint(int x, int y)`: if penDown, `current.addPoint(x, y)`.
- `getCurrent()`: return the stroke.
- `isPenDown()`: return the flag.

No imports beyond its own package.

---

## Note on coordinates

`x, y` here are DRAWING-SURFACE pixels, not camera pixels. The
ScratchpadController (ch16) mirrors + scales camera coords before
calling `onPoint`. You don't handle that here.

## Test

```java
StrokeRecorder r = new StrokeRecorder();
r.penDown();
r.onPoint(10, 10);
r.onPoint(11, 11);   // filtered (too close)
r.onPoint(50, 50);
r.penUp();
System.out.println(r.getCurrent().size());  // expect 2
```

## Checklist

- [ ] addPoint filters near-duplicates
- [ ] getPoints returns an unmodifiable view
- [ ] penDown clears the previous stroke
- [ ] Javadoc everywhere

Commit: `add Stroke and StrokeRecorder`. Move to chapter 14.
