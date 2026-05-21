# Chapter 6 - ColorBlobDetector

**Audience**: Person A. **Time**: 1-2 hours including HSV tuning.

Track a brightly-colored marker (orange tape, highlighter, sticky note)
through the camera. The marker's center becomes the cursor position.

## Algorithm

1. Convert BGR frame to HSV color space.
2. Threshold to a binary mask: pixels matching the target hue range
   become white, everything else black.
3. Find contours in the mask. Pick the largest one.
4. Compute centroid via image moments.
5. Return `Gesture(POINT, cx, cy, confidence)`.

## Why HSV not BGR

HSV separates color (H) from brightness (V). In BGR, "orange in dim
light" and "orange in bright light" have different B/G/R values. In
HSV they have the same H, just different V. Thresholding on H is
robust to lighting.

OpenCV HSV ranges:
- H: 0-179 (not 0-360 - they cut it in half to fit in a byte)
- S: 0-255 (saturation: 0=gray, 255=pure color)
- V: 0-255 (brightness: 0=black, 255=full bright)

Orange is roughly H=5..20, S=100..255, V=100..255.

## ColorBlobDetector.java

`src/main/java/com/starkmouse/detection/ColorBlobDetector.java`:

```java
package com.starkmouse.detection;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks a brightly-colored marker via HSV thresholding.
 */
public class ColorBlobDetector implements GestureDetector {

    /** Lower HSV bound (orange default). */
    private static final Scalar LOWER_BOUND = new Scalar(5, 100, 100);
    /** Upper HSV bound. */
    private static final Scalar UPPER_BOUND = new Scalar(20, 255, 255);
    /** Min blob area in pixels to filter noise. */
    private static final double MIN_BLOB_AREA = 400.0;

    private final Mat hsv = new Mat();
    private final Mat mask = new Mat();

    @Override
    public Gesture detect(Mat frame) {
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
        Core.inRange(hsv, LOWER_BOUND, UPPER_BOUND, mask);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        MatOfPoint largest = null;
        double largestArea = 0;
        for (MatOfPoint c : contours) {
            double a = Imgproc.contourArea(c);
            if (a > largestArea) {
                largestArea = a;
                largest = c;
            }
        }

        if (largest == null || largestArea < MIN_BLOB_AREA) {
            return Gesture.none();
        }

        Moments m = Imgproc.moments(largest);
        int cx = (int) (m.m10 / m.m00);
        int cy = (int) (m.m01 / m.m00);
        double confidence = Math.min(1.0, largestArea / 5000.0);

        return new Gesture(Gesture.Type.POINT, cx, cy, confidence);
    }

    @Override
    public String getName() {
        return "Color Blob";
    }
}
```

## Imports

| Import | What |
|--------|------|
| `org.opencv.core.Core` | `Core.inRange` (thresholding) |
| `org.opencv.core.Mat` | frame, mask buffers |
| `org.opencv.core.MatOfPoint` | a contour (typed `Mat` for arrays of `Point`) |
| `org.opencv.core.Scalar` | n-element vector for color bounds |
| `org.opencv.imgproc.Imgproc` | `cvtColor`, `findContours`, `contourArea`, `moments` |
| `org.opencv.imgproc.Moments` | image moments result |
| `java.util.ArrayList`, `java.util.List` | contours list |

## Key OpenCV API

```java
Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2HSV);
// dst becomes HSV version of src

Core.inRange(src, lower, upper, dst);
// dst (1-channel) is 255 where lower <= src <= upper, else 0

Imgproc.findContours(binaryMask, contoursList, hierarchyMat,
                     Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
// RETR_EXTERNAL: only outermost contours, no holes
// CHAIN_APPROX_SIMPLE: compress straight segments to endpoints

Imgproc.contourArea(contour);                  // pixels^2
Moments m = Imgproc.moments(contour);          // image moments
int cx = (int)(m.m10 / m.m00);                 // centroid x
int cy = (int)(m.m01 / m.m00);                 // centroid y
```

`m00` is the area, `m10`/`m01` are first-order moments. Dividing gives
the center of mass. This is the textbook way to get a centroid in
image processing.

## Tuning HSV bounds

The defaults track orange. To tune for your specific marker:

1. Pick a vivid object (highlighter cap, brightly colored tape).
2. Use any HSV picker online to estimate H, S, V.
3. Or save a debug mask while running:
   ```java
   Imgcodecs.imwrite("debug-mask.png", mask);
   ```
   then look at the PNG. Your marker should be a clean white blob on
   black. If it's noisy, raise saturation min. If your marker is dim,
   lower value min.

Common HSV ranges (approximate):
- Red: 0-10 OR 170-179 (red wraps around)
- Orange: 5-20
- Yellow: 20-35
- Green: 40-80
- Blue: 100-130

## Performance note

`hsv` and `mask` are class fields, reused every frame. Don't write
`Mat hsv = new Mat();` inside `detect()` - you'd allocate a new Mat 30
times a second and leak native memory.

## Test

You can't fully test this until chapter 11 wires it into a loop. But
you can verify it compiles by adding a temporary main to `MainApp` that
reads one frame and prints the result:

```java
ColorBlobDetector det = new ColorBlobDetector();
Mat f = cam.grabFrame();
Gesture g = det.detect(f);
System.out.println(g);
```

Should print either `NONE @ (0,0) c=0.00` (no marker visible) or
`POINT @ (320,240) c=0.84` (marker visible, centroid printed).

Commit: `add ColorBlobDetector`. Move to chapter 7.
