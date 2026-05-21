# Chapter 7 - HandContourDetector

**Audience**: Person A. **Time**: 3-5 hours including tuning.

Track a bare hand. Count extended fingers. Map finger count to gesture
type. Also detect "hold gestures" (fist held 1s, palm held 2s) for the
scratchpad triggers.

## Algorithm

1. Convert BGR -> YCrCb (better for skin than HSV).
2. Threshold the Cr,Cb channels to get a skin mask.
3. Morphological cleanup (open + close).
4. Find largest contour -> the hand.
5. Compute convex hull. The hull touches the fingertips.
6. Compute convexity defects: the gaps between hull and contour, i.e.
   the valleys between fingers.
7. Filter defects by depth + angle. Each valid defect = one finger gap.
   N gaps means N+1 fingers.
8. Map finger count to gesture type.
9. Track how long the same gesture has been held - emit "hold" variants
   when threshold passed.

## Why YCrCb not HSV for skin

Skin tones across all ethnicities cluster in a tight Cr/Cb range
(~133..173 Cr, 77..127 Cb) regardless of how bright Y is. HSV's hue
also clusters but with more outliers. YCrCb is the standard choice
for skin detection.

## HandContourDetector.java

`src/main/java/com/starkmouse/detection/HandContourDetector.java`:

```java
package com.starkmouse.detection;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

/**
 * Bare-hand detector. Finds hand via skin segmentation, counts fingers
 * via convex hull defects, also detects timed hold gestures.
 */
public class HandContourDetector implements GestureDetector {

    private static final Scalar SKIN_LOWER = new Scalar(0, 133, 77);
    private static final Scalar SKIN_UPPER = new Scalar(255, 173, 127);
    private static final double MIN_HAND_AREA = 5000.0;
    private static final double MAX_FINGER_ANGLE_DEG = 90.0;
    private static final double MIN_DEFECT_DEPTH = 20.0;

    /** ms a gesture must be held to upgrade to PEN_DOWN/PEN_UP. */
    private static final long HOLD_MS_PEN = 1000;
    /** ms a palm must be held to fire SCRATCHPAD_TOGGLE. */
    private static final long HOLD_MS_TOGGLE = 2000;

    private final Mat ycrcb = new Mat();
    private final Mat mask = new Mat();
    private final Mat morphKernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));

    // hold-gesture tracking
    private Gesture.Type lastRawType = Gesture.Type.NONE;
    private long heldSince = 0;
    private boolean penFired = false;
    private boolean toggleFired = false;

    @Override
    public Gesture detect(Mat frame) {
        Imgproc.cvtColor(frame, ycrcb, Imgproc.COLOR_BGR2YCrCb);
        Core.inRange(ycrcb, SKIN_LOWER, SKIN_UPPER, mask);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, morphKernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, morphKernel);

        MatOfPoint hand = findLargestContour(mask);
        if (hand == null) return resetAndNone();
        double area = Imgproc.contourArea(hand);
        if (area < MIN_HAND_AREA) return resetAndNone();

        Moments m = Imgproc.moments(hand);
        int cx = (int) (m.m10 / m.m00);
        int cy = (int) (m.m01 / m.m00);
        int fingers = countFingers(hand);

        Gesture.Type raw = rawTypeFromFingers(fingers);
        Gesture.Type out = applyHoldLogic(raw);
        double confidence = Math.min(1.0, area / 20000.0);
        return new Gesture(out, cx, cy, confidence);
    }

    /** Reset hold timers when no hand visible. */
    private Gesture resetAndNone() {
        lastRawType = Gesture.Type.NONE;
        penFired = false;
        toggleFired = false;
        return Gesture.none();
    }

    private MatOfPoint findLargestContour(Mat bin) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(bin, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();
        MatOfPoint best = null;
        double bestArea = 0;
        for (MatOfPoint c : contours) {
            double a = Imgproc.contourArea(c);
            if (a > bestArea) { bestArea = a; best = c; }
        }
        return best;
    }

    private int countFingers(MatOfPoint hand) {
        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(hand, hull, false);
        if (hull.size().height < 3) return 0;

        MatOfInt4 defects = new MatOfInt4();
        try {
            Imgproc.convexityDefects(hand, hull, defects);
        } catch (Exception e) {
            return 0;
        }

        Point[] points = hand.toArray();
        int[] arr = defects.toArray();
        int gaps = 0;
        for (int i = 0; i < arr.length; i += 4) {
            int startIdx = arr[i];
            int endIdx = arr[i + 1];
            int farIdx = arr[i + 2];
            double depth = arr[i + 3] / 256.0;
            if (depth < MIN_DEFECT_DEPTH) continue;
            double angle = angleAtPoint(points[startIdx], points[endIdx], points[farIdx]);
            if (angle < MAX_FINGER_ANGLE_DEG) gaps++;
        }
        return Math.min(5, gaps + 1);
    }

    private double angleAtPoint(Point a, Point b, Point far) {
        double ab2 = sqdist(far, a);
        double cb2 = sqdist(far, b);
        double ac2 = sqdist(a, b);
        double cos = (ab2 + cb2 - ac2) / (2 * Math.sqrt(ab2) * Math.sqrt(cb2));
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    private double sqdist(Point p, Point q) {
        double dx = p.x - q.x, dy = p.y - q.y;
        return dx * dx + dy * dy;
    }

    /**
     * Raw gesture type before hold-logic. Maps finger count to base type.
     */
    private Gesture.Type rawTypeFromFingers(int fingers) {
        return switch (fingers) {
            case 0 -> Gesture.Type.NONE;       // fist - will upgrade to PEN_DOWN
            case 1 -> Gesture.Type.POINT;
            case 2 -> Gesture.Type.LEFT_CLICK;
            case 3 -> Gesture.Type.RIGHT_CLICK;
            default -> Gesture.Type.NONE;       // 4-5 fingers, palm - upgrades to PEN_UP / TOGGLE
        };
    }

    /**
     * Detects "held for N ms" gestures. Fist held 1s -> PEN_DOWN.
     * Palm held 1s -> PEN_UP. Palm held 2s -> SCRATCHPAD_TOGGLE.
     * Plain raw gestures pass through unchanged.
     */
    private Gesture.Type applyHoldLogic(Gesture.Type raw) {
        // We need to differentiate fist (0 fingers) from palm (4-5).
        // But rawTypeFromFingers returns NONE for both. Track the raw
        // finger count separately via a sentinel raw type.
        // For simplicity, do hold detection only for fist/palm here:
        // we set a side-channel using the *finger count* in a parallel
        // path. Simpler: re-derive whether raw was fist or palm by
        // checking the last computed fingers in detect(). For this
        // file, we keep raw shape - see step 3 below.
        return raw;
    }
}
```

## Wait, that hold-logic doesn't work yet

Look at `rawTypeFromFingers`: both fist (0) and palm (4-5) return
`NONE`. We lose the distinction needed for hold detection. Two fixes:

**Option 1**: add `FIST_RAW` and `PALM_RAW` enum values, never emit
them to the mapper, only use them internally.

**Option 2**: track finger count as a class field and use it in
`applyHoldLogic`.

Going with option 2 for simplicity. Replace `applyHoldLogic` and update
`detect`:

```java
private int lastFingers = -1;

@Override
public Gesture detect(Mat frame) {
    // ... existing code up through `int fingers = countFingers(hand);`

    Gesture.Type raw = rawTypeFromFingers(fingers);
    Gesture.Type out = applyHoldLogic(raw, fingers);

    double confidence = Math.min(1.0, area / 20000.0);
    return new Gesture(out, cx, cy, confidence);
}

private Gesture.Type applyHoldLogic(Gesture.Type raw, int fingers) {
    boolean isFist = (fingers == 0);
    boolean isPalm = (fingers >= 4);
    long now = System.currentTimeMillis();

    if (fingers != lastFingers) {
        // gesture changed - reset hold tracking
        lastFingers = fingers;
        heldSince = now;
        penFired = false;
        toggleFired = false;
        return raw;
    }

    long held = now - heldSince;

    if (isFist && held >= HOLD_MS_PEN && !penFired) {
        penFired = true;
        return Gesture.Type.PEN_DOWN;
    }
    if (isPalm) {
        if (held >= HOLD_MS_TOGGLE && !toggleFired) {
            toggleFired = true;
            return Gesture.Type.SCRATCHPAD_TOGGLE;
        }
        if (held >= HOLD_MS_PEN && !penFired) {
            penFired = true;
            return Gesture.Type.PEN_UP;
        }
    }
    return raw;
}

@Override
public String getName() {
    return "Hand Contour";
}
```

Drop the now-unused `lastRawType` field.

## Imports recap

| Import | What |
|--------|------|
| `org.opencv.core.Core` | `inRange` |
| `org.opencv.core.Mat` | image buffers |
| `org.opencv.core.MatOfInt` | hull indices |
| `org.opencv.core.MatOfInt4` | defects (each defect is 4 ints) |
| `org.opencv.core.MatOfPoint` | a contour |
| `org.opencv.core.Point` | a 2D point |
| `org.opencv.core.Scalar` | color bounds |
| `org.opencv.core.Size` | kernel size |
| `org.opencv.imgproc.Imgproc` | most of the algorithms |
| `org.opencv.imgproc.Moments` | centroid math |
| `java.util.ArrayList`, `java.util.List` | contours list |

## How convexity defects work

OpenCV's `convexityDefects` returns an array where every 4 ints describe
one defect:

- `[i+0]` start index into the contour (one fingertip)
- `[i+1]` end index (the next fingertip)
- `[i+2]` farthest point index (the valley between them)
- `[i+3]` depth from hull to far point, in units of 256 (so divide by
  256 to get pixels)

Real finger gaps:
- have meaningful depth (we filter < 20 px to remove noise)
- have a sharp angle at the valley (< 90 degrees - finger gaps are
  acute)

## Tuning

- Skin range too broad? Lower 173 to ~165 (Cr upper).
- Hand missed? Widen Cr/Cb range.
- Fingers undercounted? Lower `MIN_DEFECT_DEPTH`.
- Fingers overcounted? Raise `MIN_DEFECT_DEPTH` or lower
  `MAX_FINGER_ANGLE_DEG`.
- Background is also skin-toned (beige wall, wooden furniture)?
  Use a darker, non-skin background for the demo. Sometimes you can't
  fix it with code.

## Holding gestures

- "Fist held 1s" -> single PEN_DOWN emitted once. The next frame you
  hold a fist, `penFired` is already true, so you get plain NONE again
  until you change gesture and re-fist.
- This "edge-trigger" pattern prevents the gesture firing every frame.

## Test

Wire `HandContourDetector` into the temporary main like the color blob
test. Print gestures every frame. Hold up 1, 2, 3 fingers. You should
see the gesture type change.

Commit: `add HandContourDetector`. Person A is now done with their
non-scratchpad work. Move to chapter 11 (integration with Person B)
or chapter 13 if Person B's pieces aren't ready.
