# Chapter 6 - ColorBlobDetector

**Audience**: Person A. **Time**: 1-2 hours including HSV tuning.
**You will write**: `ColorBlobDetector.java` implementing `GestureDetector`.

This chapter teaches you the OpenCV methods you need, shows tiny
examples, then tells you what to build. The example snippets are NOT
your detector - they're throwaway illustrations. You write the real
class.

## Goal

Track a brightly-colored marker (orange tape, highlighter). Return a
`Gesture(POINT, centerX, centerY, confidence)` pointing at the marker's
center, or `Gesture.none()` if no marker is visible.

## The pipeline you'll build

```
BGR frame -> convert to HSV -> threshold to mask -> find contours
          -> pick largest -> compute centroid -> return Gesture
```

Five OpenCV calls. Let's learn each.

---

## Method 1: Imgproc.cvtColor

Converts a Mat between color spaces.

`Imgproc.cvtColor(Mat src, Mat dst, int code)`

Example (throwaway):

```java
Mat hsv = new Mat();
Imgproc.cvtColor(someFrame, hsv, Imgproc.COLOR_BGR2HSV);
// hsv now holds the HSV version of someFrame
```

Why HSV: hue (color) is roughly independent of brightness, so "find
orange" works in bright or dim light. In raw BGR, orange-in-shadow and
orange-in-sun have totally different values.

OpenCV HSV ranges: H is 0-179 (not 360), S and V are 0-255.

---

## Method 2: Core.inRange

Produces a binary mask: white (255) where the pixel is within bounds,
black (0) elsewhere.

`Core.inRange(Mat src, Scalar lower, Scalar upper, Mat dst)`

Example (throwaway):

```java
Mat mask = new Mat();
Scalar lo = new Scalar(5, 100, 100);    // orange-ish
Scalar hi = new Scalar(20, 255, 255);
Core.inRange(hsv, lo, hi, mask);
// mask is 1-channel: 255 where pixel was orange
```

`Scalar` is just a tuple of numbers; for HSV it's (hue, sat, val).

---

## Method 3: Imgproc.findContours

Traces the outlines of white blobs in a binary mask.

```java
Imgproc.findContours(Mat image, List<MatOfPoint> contours,
                     Mat hierarchy, int mode, int method)
```

Example (throwaway):

```java
List<MatOfPoint> contours = new ArrayList<>();
Mat hierarchy = new Mat();
Imgproc.findContours(mask, contours, hierarchy,
        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
hierarchy.release();
// contours now holds one MatOfPoint per blob
```

- `RETR_EXTERNAL`: only outer outlines (ignore holes)
- `CHAIN_APPROX_SIMPLE`: fewer points, faster
- You provide an empty list; it gets filled
- `hierarchy` is required but we ignore its contents - release it after

`MatOfPoint` = one contour, basically a list of points.

---

## Method 4: Imgproc.contourArea

Returns a contour's enclosed area in pixels.

`double Imgproc.contourArea(MatOfPoint contour)`

Example (throwaway - find biggest blob):

```java
double biggest = 0;
for (MatOfPoint c : contours) {
    double a = Imgproc.contourArea(c);
    if (a > biggest) biggest = a;
}
```

Why: noise makes tiny blobs; your marker is the biggest. Also lets you
reject everything if even the biggest is too small (no marker present).

---

## Method 5: Imgproc.moments -> centroid

Image moments give you a blob's center of mass.

`Moments m = Imgproc.moments(MatOfPoint contour)`

Then: centroid x = `m.m10 / m.m00`, centroid y = `m.m01 / m.m00`.

Example (throwaway):

```java
Moments m = Imgproc.moments(someContour);
int cx = (int)(m.m10 / m.m00);
int cy = (int)(m.m01 / m.m00);
```

`m00` is the area (mass). `m10`, `m01` are the x and y moments. Divide
to get the average position = center. Don't compute this on an empty
contour (m00 == 0 -> divide by zero -> NaN).

---

## Now build it

You have all five methods. Here's the spec for `ColorBlobDetector`.

### File and declaration

Create `src/main/java/com/starkmouse/detection/ColorBlobDetector.java`.
The class implements `GestureDetector` (from chapter 4), so it needs:

- `Gesture detect(Mat frame)`
- `String getName()` returning `"Color Blob"`

### Imports you'll need

Figure out which of these each line of your code requires (your IDE
will help, but try to predict):

| Import | Used for |
|--------|----------|
| `org.opencv.core.Core` | `inRange` |
| `org.opencv.core.Mat` | frame and mask buffers |
| `org.opencv.core.MatOfPoint` | contours |
| `org.opencv.core.Scalar` | the HSV bounds |
| `org.opencv.imgproc.Imgproc` | cvtColor, findContours, contourArea, moments |
| `org.opencv.imgproc.Moments` | the moments result type |
| `java.util.ArrayList` | the contours list |
| `java.util.List` | the contours list type |

### Fields to declare

- Two `Scalar` constants for the lower/upper HSV bounds (start with the
  orange values from the examples above). Make them `static final`.
- A `double` constant for the minimum blob area (try 400). `static final`.
- Two reusable `Mat` fields - one for the HSV image, one for the mask.
  **Declare them as fields, not locals inside `detect`** - you don't
  want to allocate a new Mat 30 times a second.

### The detect method - your algorithm

Write `detect(Mat frame)` to do, in order:

1. Convert `frame` to HSV (into your hsv field).
2. `inRange` the HSV with your bounds (into your mask field).
3. Find contours in the mask (new local list + a temp hierarchy Mat;
   release the hierarchy).
4. Loop the contours, track the one with the largest area.
5. If no contour found, OR the largest area is below your minimum,
   return `Gesture.none()`.
6. Compute the centroid of the largest contour via moments.
7. Compute a confidence: `Math.min(1.0, area / 5000.0)` is a fine
   starting formula (bigger blob = more confident, capped at 1.0).
8. Return `new Gesture(Gesture.Type.POINT, cx, cy, confidence)`.

### getName

Return the string `"Color Blob"`. One line.

---

## Test what you wrote

Add a temporary main (in `MainApp` or a scratch file):

```java
nu.pattern.OpenCV.loadLocally();
CameraInput cam = new CameraInput(0);
cam.start();
ColorBlobDetector det = new ColorBlobDetector();
for (int i = 0; i < 100; i++) {
    Mat f = cam.grabFrame();
    if (f != null) System.out.println(det.detect(f));
    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
}
cam.stop();
```

Hold your marker up. You should see `POINT @ (xxx,yyy) c=0.xx` lines,
with the coordinates tracking the marker as you move it. Hide the
marker, see `NONE @ (0,0) c=0.00`.

---

## Tuning + debugging

If detection is bad, save the mask and look at it:

```java
org.opencv.imgcodecs.Imgcodecs.imwrite("debug-mask.png", mask);
```

(Temporarily make `mask` accessible, or add this inside `detect`.)

Open the PNG:
- Marker shows as a clean white blob -> your bounds are good, the bug
  is in your contour logic
- Mask is noisy / mostly white -> raise your saturation lower bound
- Mask is empty / marker isn't white -> your hue range is wrong for
  your marker; adjust LOWER/UPPER

Approximate hue ranges: red 0-10 or 170-179, orange 5-20, yellow 20-35,
green 40-80, blue 100-130.

---

## Checklist before moving on

- [ ] Class implements `GestureDetector`, compiles
- [ ] Both methods have Javadoc (rubric)
- [ ] Mat fields are reused, not allocated per call
- [ ] Returns `Gesture.none()` when no marker
- [ ] Coordinates track your marker live
- [ ] You can explain HSV vs BGR and what moments compute

If stuck >30 min, peek at `/reference/detection/ColorBlobDetector.java`,
understand the difference, close it, fix yours. Don't copy.

Commit: `add ColorBlobDetector`. Move to chapter 7.
