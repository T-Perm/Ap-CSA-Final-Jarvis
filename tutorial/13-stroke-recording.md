# Chapter 13 - Stroke recording

**Audience**: Person A + Person B together. **Time**: 30 min.

Capture the path of the fingertip while pen is down. This is the
"input" to the recognizer.

## Stroke.java

`src/main/java/com/starkmouse/scratchpad/Stroke.java` (Person A):

```java
package com.starkmouse.scratchpad;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single stroke (one pen-down to pen-up). Stored as a sequence of
 * (x,y) points. Used by both the drawing surface and the recognizer.
 */
public class Stroke {

    private final List<Point> points = new ArrayList<>();

    /** Min distance from last point in pixels - filters noise. */
    private static final int MIN_DELTA_PX = 3;

    /**
     * Add a point. Filters out near-duplicates from finger jitter.
     */
    public void addPoint(int x, int y) {
        if (!points.isEmpty()) {
            Point last = points.get(points.size() - 1);
            int dx = x - last.x;
            int dy = y - last.y;
            if (dx * dx + dy * dy < MIN_DELTA_PX * MIN_DELTA_PX) return;
        }
        points.add(new Point(x, y));
    }

    /** @return unmodifiable view of the current points */
    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public int size() { return points.size(); }
    public boolean isEmpty() { return points.isEmpty(); }
    public void clear() { points.clear(); }
}
```

## Imports

| Import | What |
|--------|------|
| `java.awt.Point` | 2D point with int x,y fields |
| `java.util.ArrayList`, `List` | the underlying storage |
| `java.util.Collections` | `unmodifiableList` for safe sharing |

`java.awt.Point` is a public class with `.x` and `.y` (not getter
methods) - it's intentionally bare. Don't be alarmed by the direct
field access.

## StrokeRecorder.java

`src/main/java/com/starkmouse/scratchpad/StrokeRecorder.java`:

```java
package com.starkmouse.scratchpad;

/**
 * Listens to fingertip positions and records them into a Stroke when
 * pen is down. Pen state is toggled externally.
 */
public class StrokeRecorder {

    private final Stroke current = new Stroke();
    private boolean penDown = false;

    public void penDown() {
        current.clear();
        penDown = true;
    }

    public void penUp() {
        penDown = false;
    }

    /** Called every frame with the fingertip position. */
    public void onPoint(int x, int y) {
        if (penDown) current.addPoint(x, y);
    }

    public Stroke getCurrent() { return current; }
    public boolean isPenDown() { return penDown; }
}
```

No imports needed - only references its own package.

## Coordinate mapping caveat

`x, y` here are in **drawing-surface pixels**, not camera pixels. The
ScratchpadController (chapter 16) will mirror+scale camera coords to
surface coords before calling `onPoint`. Same mirror trick as
MouseController:

```java
double mirrored = cameraWidth - cameraX;
int surfaceX = (int)(mirrored / cameraWidth * surfaceWidth);
int surfaceY = (int)((double) cameraY / cameraHeight * surfaceHeight);
```

## Test

```java
public static void main(String[] args) {
    StrokeRecorder r = new StrokeRecorder();
    r.penDown();
    r.onPoint(10, 10);
    r.onPoint(11, 11);  // filtered (delta < 3)
    r.onPoint(50, 50);
    r.penUp();
    System.out.println(r.getCurrent().size());  // 2
}
```

Commit: `add Stroke and StrokeRecorder`. Move to chapter 14.
