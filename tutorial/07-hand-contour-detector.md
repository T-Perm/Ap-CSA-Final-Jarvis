# Chapter 7 - HandContourDetector

**Audience**: Person A. **Time**: 3-5 hours including tuning.
**You will write**: `HandContourDetector.java`.

The hardest detection file. Detect a bare hand, count extended fingers,
map count to gesture type, and detect timed "hold" gestures for the
scratchpad.

If you haven't read `api-reference-1-opencv.md`, read its convexHull /
convexityDefects / morphologyEx entries first. This chapter assumes
those methods are familiar.

## Goal

`detect(Mat frame)` returns a `Gesture` whose type reflects the number
of fingers (and timed holds), positioned at the hand's centroid.

## The pipeline

```
BGR -> YCrCb -> skin mask (inRange) -> morphology cleanup
    -> largest contour -> convex hull -> convexity defects
    -> count finger gaps -> finger count -> gesture type
    -> apply hold-timing logic -> Gesture
```

---

## Method recap (see api-reference-1 for full detail)

- `Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2YCrCb)` - skin-friendly
  color space
- `Core.inRange(ycrcb, lo, hi, mask)` - skin threshold; good skin
  bounds are roughly `Scalar(0,133,77)` to `Scalar(255,173,127)`
- `Imgproc.morphologyEx(mask, mask, op, kernel)` with
  `MORPH_OPEN` then `MORPH_CLOSE`; kernel from
  `Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5,5))`
- `Imgproc.findContours(...)` + `Imgproc.contourArea(...)` - same as ch6
- `Imgproc.convexHull(contour, hullIndices, false)` - hullIndices is a
  `MatOfInt`
- `Imgproc.convexityDefects(contour, hullIndices, defects)` - defects is
  a `MatOfInt4`; `.toArray()` gives ints in groups of 4:
  `[startIdx, endIdx, farIdx, depth]`, depth in 1/256 px

---

## Concept: counting fingers from defects

5 spread fingers create 4 deep "valleys" (defects) between them. So:
`fingers = validGaps + 1`. A valid gap is one that's deep enough
(depth > ~20px after dividing by 256) AND has a sharp angle at the
valley (< ~90 degrees - finger gaps are acute).

The angle at the valley point uses the law of cosines on the triangle
formed by the two fingertip points and the valley point.

Throwaway angle helper to study (you'll write your own version):

```java
// angle at vertex `far`, given two other points a and b
double ab2 = sqDist(far, a), cb2 = sqDist(far, b), ac2 = sqDist(a, b);
double cos = (ab2 + cb2 - ac2) / (2 * Math.sqrt(ab2) * Math.sqrt(cb2));
double angleDeg = Math.toDegrees(Math.acos(clamp(cos, -1, 1)));
```

(`sqDist` = squared distance; clamp keeps acos's input in [-1,1] to
avoid NaN from rounding.)

---

## Concept: hold-gesture timing (the tricky part)

The scratchpad needs "fist held 1 second" and "palm held 2 seconds" as
distinct events. A detector that fires every frame can't express
"held." You track timing yourself:

- Remember the current finger count and when it started
- Each frame, if the count is unchanged, check how long it's been held
- When a threshold is crossed, emit the special gesture ONCE (set a
  "fired" flag so it doesn't repeat every frame)
- When the count changes, reset the timer and flags

You'll need fields like: `lastFingers`, `heldSince` (a timestamp from
`System.currentTimeMillis()`), `penFired`, `toggleFired`.

This is an "edge-triggered" pattern - fire on the transition, not
continuously.

---

## Now build it

Create `src/main/java/com/starkmouse/detection/HandContourDetector.java`
implementing `GestureDetector`.

### Constants (static final)

- Skin lower/upper `Scalar`s (use the YCrCb values above)
- `MIN_HAND_AREA` ~ 5000
- `MAX_FINGER_ANGLE_DEG` ~ 90
- `MIN_DEFECT_DEPTH` ~ 20
- `HOLD_MS_PEN` = 1000, `HOLD_MS_TOGGLE` = 2000

### Reusable Mat fields

- `ycrcb`, `mask`, and the morphology `kernel` (build the kernel once
  as a field).

### Hold-tracking fields

- `int lastFingers` (init -1), `long heldSince`, `boolean penFired`,
  `boolean toggleFired`.

### detect(Mat frame)

1. cvtColor to YCrCb.
2. inRange for skin -> mask.
3. morphologyEx OPEN then CLOSE on the mask.
4. Find the largest contour (reuse your ch6 approach; consider
   extracting a private `findLargestContour` helper).
5. If none, or area < MIN_HAND_AREA, reset your hold state and return
   `Gesture.none()`. (Write a small private `resetAndNone()` that clears
   lastFingers/penFired/toggleFired and returns `Gesture.none()`.)
6. Centroid via moments (cx, cy).
7. `int fingers = countFingers(handContour)` (private helper, below).
8. `Gesture.Type type = applyHoldLogic(fingers)` (private helper, below).
9. confidence = `Math.min(1.0, area / 20000.0)`.
10. Return `new Gesture(type, cx, cy, confidence)`.

### private int countFingers(MatOfPoint hand)

1. convexHull -> a `MatOfInt` of indices. If it has < 3 entries, return 0.
2. convexityDefects -> a `MatOfInt4`. Wrap in try/catch; on exception
   return 0.
3. `Point[] pts = hand.toArray()`, `int[] arr = defects.toArray()`.
4. Loop `i` over `arr` in steps of 4. For each defect:
   - depth = `arr[i+3] / 256.0`; skip if < MIN_DEFECT_DEPTH
   - angle = angle at `pts[arr[i+2]]` between `pts[arr[i]]` and
     `pts[arr[i+1]]`; count it if angle < MAX_FINGER_ANGLE_DEG
5. Return `Math.min(5, gaps + 1)`.

Write private helpers `angleAtPoint(a, b, far)` and `sqDist(p, q)` using
the law-of-cosines snippet above.

### private Gesture.Type applyHoldLogic(int fingers)

1. `isFist = fingers == 0`, `isPalm = fingers >= 4`.
2. `now = System.currentTimeMillis()`.
3. If `fingers != lastFingers`: reset (`lastFingers = fingers`,
   `heldSince = now`, clear both fired flags) and return the *base*
   gesture for this count (see mapping below).
4. `held = now - heldSince`.
5. If `isFist && held >= HOLD_MS_PEN && !penFired`: set penFired, return
   `PEN_DOWN`.
6. If `isPalm`:
   - if `held >= HOLD_MS_TOGGLE && !toggleFired`: set toggleFired,
     return `SCRATCHPAD_TOGGLE`
   - else if `held >= HOLD_MS_PEN && !penFired`: set penFired, return
     `PEN_UP`
7. Otherwise return the base gesture for this count.

### Base gesture mapping (non-held)

- 1 finger -> `POINT`
- 2 -> `LEFT_CLICK`
- 3 -> `RIGHT_CLICK`
- 0 (fist) and 4-5 (palm) -> `NONE` as the base (they only become
  meaningful once held). You can write this as a tiny `switch` or
  if/else.

### getName

Return `"Hand Contour"`.

---

## Tuning (budget real time here)

- False detection on a beige wall / wood desk -> use a darker, non-skin
  background for the demo, and/or narrow the Cr upper bound (173 -> 165).
- Hand missed entirely -> widen the skin range.
- Too many fingers -> raise MIN_DEFECT_DEPTH.
- Too few fingers -> lower MIN_DEFECT_DEPTH or raise MAX_FINGER_ANGLE_DEG.
- Save the mask with `Imgcodecs.imwrite("debug-mask.png", mask)` to see
  whether the problem is the mask or the geometry.

## Test

Wire it into a temporary main like ch6's test. Hold up 1, 2, 3 fingers
and watch the gesture type change. Then hold a fist still for a second
- you should see one `PEN_DOWN`. Hold an open palm for 2 seconds - you
should see `PEN_UP` around 1s then `SCRATCHPAD_TOGGLE` around 2s.

## Checklist

- [ ] Implements GestureDetector, compiles
- [ ] Reused Mat fields + prebuilt kernel
- [ ] Finger counting works in good lighting
- [ ] Hold gestures fire once (not every frame)
- [ ] Hold state resets when the hand disappears
- [ ] Javadoc everywhere

Stuck >30 min? Compare to `/reference/detection/HandContourDetector.java`
(note: the reference's hold-logic is structured slightly differently -
understand it, don't copy).

Commit: `add HandContourDetector`. Person A's core detection is done.
