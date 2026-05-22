# API reference 1 - OpenCV

Textbook-style reference for the non-CSA APIs this project uses. Each
entry: what the method does, an example, and an exercise to implement.

This file: OpenCV. See the other api-reference-*.md files for Robot,
SystemTray, Swing drawing, JNativeHook, threading, and file I/O.

OpenCV is a C++ library; the Java version wraps it. Everything centers
on one type: `Mat` (a matrix of pixels). Most operations take a source
Mat and write into a destination Mat you supply.

---

## Mat

**What it is**: a matrix. An image is a Mat where each element is a
pixel. A color image has 3 channels (Blue, Green, Red - OpenCV is BGR).
A binary mask has 1 channel where pixels are 0 or 255.

**Methods you'll use**:

- `new Mat()` - empty matrix, gets sized automatically when written to
- `mat.empty()` -> boolean, true if no data
- `mat.size()` -> a `Size` (has `.width`, `.height`)
- `mat.cols()` -> int width
- `mat.rows()` -> int height
- `mat.release()` - free the native memory (do this when done)

**Example**:

```java
Mat frame = new Mat();
camera.read(frame);            // frame now holds an image
if (!frame.empty()) {
    System.out.println(frame.cols() + "x" + frame.rows());  // 640x480
}
frame.release();
```

**Why release matters**: a Mat holds native (C++) memory that Java's
garbage collector doesn't track well. If you create thousands of Mats
in a loop without releasing, you leak memory fast. The pattern: create
a few Mats once (as fields), reuse them every frame.

**Try it**: Write a method `describe(Mat m)` that returns a String like
`"image 640x480, 3 channels"` if the Mat is non-empty, or `"empty"` if
it is. (Hint: `m.channels()` returns the channel count.)

---

## VideoCapture

**What it is**: webcam (or video file) reader.

**Methods**:

- `new VideoCapture(int index)` - open camera by index (0 = default)
- `cap.isOpened()` -> boolean, false if open failed
- `cap.read(Mat dst)` -> boolean, reads next frame into dst, false on fail
- `cap.set(int propId, double value)` - set a property (resolution, etc.)
- `cap.release()` - free the camera

**Example**:

```java
VideoCapture cap = new VideoCapture(0);
if (!cap.isOpened()) { System.err.println("no camera"); return; }

Mat frame = new Mat();
cap.read(frame);               // grab one frame
// ... use frame ...
cap.release();
```

**Setting resolution** (lower = faster):

```java
import org.opencv.videoio.Videoio;
cap.set(Videoio.CAP_PROP_FRAME_WIDTH, 320);
cap.set(Videoio.CAP_PROP_FRAME_HEIGHT, 240);
```

**Try it**: Write a loop that reads 30 frames and counts how many came
back non-empty (the first few are often empty during warmup). Print the
count. This tells you how reliable your camera's startup is.

---

## Imgproc.cvtColor

**What it does**: converts a Mat from one color space to another.

**Signature**: `Imgproc.cvtColor(Mat src, Mat dst, int code)`

The `code` is a constant like `Imgproc.COLOR_BGR2HSV` or
`Imgproc.COLOR_BGR2YCrCb` or `Imgproc.COLOR_BGR2GRAY`.

**Example**:

```java
Mat bgr = ...;              // from camera
Mat hsv = new Mat();
Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);
// hsv now holds the HSV version
```

**Why color spaces matter**:
- BGR: raw camera. Color and brightness mixed in all channels. Bad for
  filtering.
- HSV: Hue (color), Saturation (vividness), Value (brightness). Good
  for "find all orange pixels" because hue is roughly lighting-independent.
- YCrCb: Luma (Y) and two chroma channels. Skin tones cluster tightly
  in Cr/Cb. Good for skin detection.

**Try it**: Convert a camera frame to grayscale
(`Imgproc.COLOR_BGR2GRAY`) and save it with `Imgcodecs.imwrite`. Open
the file. Confirm it's gray. (Grayscale is 1 channel - useful to see
how cvtColor changes channel count.)

---

## Core.inRange

**What it does**: thresholds a Mat into a binary mask. Output pixel is
255 if the input pixel falls between the lower and upper bounds (per
channel), else 0.

**Signature**: `Core.inRange(Mat src, Scalar lower, Scalar upper, Mat dst)`

`Scalar` is just an n-number tuple. For HSV, `new Scalar(h, s, v)`.

**Example**:

```java
Mat hsv = ...;                              // HSV image
Mat mask = new Mat();
Scalar lower = new Scalar(5, 100, 100);     // orange-ish, vivid, bright
Scalar upper = new Scalar(20, 255, 255);
Core.inRange(hsv, lower, upper, mask);
// mask is 1-channel: 255 where the pixel was orange, else 0
```

**Reading the result**: the mask is a black image with white blobs
where the target color was. You can save it with `imwrite` to debug
your bounds.

**Try it**: Make a mask for "bright green" (HSV hue ~40-80). Save the
mask. Hold a green object up to the camera and confirm it shows up
white in the saved mask. Tune the bounds until the blob is clean.

---

## Imgproc.findContours

**What it does**: finds the outlines of white blobs in a binary mask.
Each contour is a list of points tracing a blob's edge.

**Signature**:
```java
Imgproc.findContours(Mat image, List<MatOfPoint> contours,
                     Mat hierarchy, int mode, int method)
```

- `image`: the binary mask (gets modified, pass a copy if you need the
  original)
- `contours`: an empty `List<MatOfPoint>` you provide; gets filled
- `hierarchy`: a Mat for contour nesting info; we usually ignore it but
  must pass one
- `mode`: `Imgproc.RETR_EXTERNAL` = only outermost contours (ignore holes)
- `method`: `Imgproc.CHAIN_APPROX_SIMPLE` = compress straight runs to
  endpoints (fewer points, faster)

**Example**:

```java
List<MatOfPoint> contours = new ArrayList<>();
Mat hierarchy = new Mat();
Imgproc.findContours(mask, contours, hierarchy,
        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
hierarchy.release();
System.out.println("found " + contours.size() + " blobs");
```

**MatOfPoint**: a typed Mat holding `Point`s. Think of it as a
`List<Point>` in OpenCV clothing. Call `.toArray()` to get a `Point[]`.

**Try it**: After finding contours, loop through them and print each
one's point count (`contour.toArray().length`). Hold up an object and
watch the numbers. A clean blob has a contour with dozens of points.

---

## Imgproc.contourArea

**What it does**: returns the area (in pixels) enclosed by a contour.

**Signature**: `double Imgproc.contourArea(MatOfPoint contour)`

**Example - find the biggest blob**:

```java
MatOfPoint largest = null;
double largestArea = 0;
for (MatOfPoint c : contours) {
    double area = Imgproc.contourArea(c);
    if (area > largestArea) {
        largestArea = area;
        largest = c;
    }
}
// largest is now the biggest blob, or null if there were none
```

**Why**: noise produces tiny blobs. The thing you care about (your
marker, your hand) is usually the largest blob. Filtering by a minimum
area discards noise.

**Try it**: Write a method `largestContour(List<MatOfPoint> contours,
double minArea)` that returns the largest contour whose area exceeds
`minArea`, or `null` if none qualify. This is a building block you'll
reuse in both detectors.

---

## Imgproc.moments + centroid

**What it does**: image moments are weighted sums over a shape. We use
them to find a contour's centroid (center of mass).

**Signature**: `Moments Imgproc.moments(Mat contour)`

The `Moments` object has fields `m00` (area), `m10`, `m01` (first
moments). Centroid = (m10/m00, m01/m00).

**Example**:

```java
Moments m = Imgproc.moments(largest);
int cx = (int)(m.m10 / m.m00);
int cy = (int)(m.m01 / m.m00);
System.out.println("blob center: " + cx + "," + cy);
```

**The math**: m00 is the total "mass" (pixel count). m10 sums x*mass,
m01 sums y*mass. Dividing gives the average x and y - the center of
mass. Same formula as physics center of mass.

**Watch for**: if m00 is 0 (empty contour), you divide by zero and get
NaN. Only compute centroid on a contour you know is non-empty.

**Try it**: Combine the last few entries. Read a frame, threshold for
your marker color, find the largest contour above some min area,
compute its centroid, print it. Move your marker and watch the centroid
coordinates track it. This is essentially the whole ColorBlobDetector.

---

## Imgproc.convexHull and convexityDefects

**What they do**: the convex hull is the smallest convex polygon
wrapping a shape (imagine a rubber band around your splayed hand - it
touches the fingertips). Convexity defects are the "valleys" between
the hull and the actual contour (the gaps between fingers).

**convexHull signature**:
```java
Imgproc.convexHull(MatOfPoint contour, MatOfInt hull, boolean clockwise)
```
`hull` (output) is filled with the *indices* into the contour that form
the hull. Pass `false` for clockwise.

**convexityDefects signature**:
```java
Imgproc.convexityDefects(MatOfPoint contour, MatOfInt hull, MatOfInt4 defects)
```
`defects` (output) is filled with 4-int tuples. Each tuple:
`[startIdx, endIdx, farIdx, depth]` where the indices point into the
contour, and depth is in units of 1/256 pixel (divide by 256 for pixels).

**Example - count finger gaps**:

```java
MatOfInt hull = new MatOfInt();
Imgproc.convexHull(handContour, hull, false);

MatOfInt4 defects = new MatOfInt4();
Imgproc.convexityDefects(handContour, hull, defects);

Point[] pts = handContour.toArray();
int[] arr = defects.toArray();
int gaps = 0;
for (int i = 0; i < arr.length; i += 4) {
    int farIdx = arr[i + 2];
    double depth = arr[i + 3] / 256.0;
    if (depth > 20) {           // deep enough to be a real finger gap
        gaps++;
    }
}
System.out.println("finger gaps: " + gaps);   // ~4 for an open hand
```

**Gotcha**: `convexityDefects` throws if the hull has fewer than 3
points or if you pass the *point* hull instead of the *index* hull.
Always pass the `MatOfInt` index version, and guard with a try/catch
or a size check.

**Why it counts fingers**: 5 spread fingers create 4 valleys between
them. So gaps + 1 = fingers (roughly; you also filter by the angle at
the valley to reject noise).

**Try it**: Build a method `countFingers(MatOfPoint hand)` that returns
gaps + 1, filtering defects by depth > 20px. Test it by holding up
different numbers of fingers (in good lighting against a dark
background). Don't worry about angle filtering yet - get depth working
first, then add the angle check from chapter 7.

---

## Imgproc.morphologyEx

**What it does**: cleans up a binary mask. "Open" removes small specks.
"Close" fills small holes.

**Signature**:
```java
Imgproc.morphologyEx(Mat src, Mat dst, int op, Mat kernel)
```
- `op`: `Imgproc.MORPH_OPEN` or `Imgproc.MORPH_CLOSE`
- `kernel`: a structuring element, made with
  `Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5,5))`

**Example**:

```java
Mat kernel = Imgproc.getStructuringElement(
        Imgproc.MORPH_ELLIPSE, new Size(5, 5));
Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);   // despeckle
Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);  // fill holes
```

(Writing src and dst as the same Mat is allowed.)

**Why**: skin/color masks are noisy - scattered white pixels from
lighting, small black holes inside the blob. Open then close gives you
a clean solid blob, which makes contour detection reliable.

**Try it**: Take a noisy mask (threshold something in a busy scene),
save it, then apply open+close and save again. Compare the two PNGs.
The second should be much cleaner.

---

## Imgcodecs.imwrite / imread

**What they do**: save a Mat to an image file / load an image file
into a Mat. Format determined by file extension.

**Signatures**:
```java
boolean Imgcodecs.imwrite(String path, Mat img)
Mat Imgcodecs.imread(String path)
```

**Example**:

```java
Imgcodecs.imwrite("debug.png", mask);     // save for inspection
Mat loaded = Imgcodecs.imread("debug.png"); // load it back
```

**Use for debugging**: when a detector misbehaves, `imwrite` the
intermediate mask and open the PNG. You'll instantly see whether the
problem is your thresholding (mask looks wrong) or your contour logic
(mask looks right but no detection).

**Try it**: Add a debug toggle to your detector that, when on, saves
the mask every 30th frame to `debug-mask.png`. Run it, draw your
marker, open the PNG, confirm the blob is clean. This habit will save
you hours.

---

## OpenCV.loadLocally

**What it does**: loads the native OpenCV library. Must be called once
before any other OpenCV class is touched.

**Signature**: `nu.pattern.OpenCV.loadLocally()`

**Example**:

```java
import nu.pattern.OpenCV;

public static void main(String[] args) {
    OpenCV.loadLocally();        // FIRST LINE
    // now safe to use Mat, VideoCapture, etc.
}
```

**If you forget**: the first OpenCV call throws `UnsatisfiedLinkError`
or `NoClassDefFoundError`. The fix is always "call loadLocally() earlier."

**Try it**: nothing to implement - just remember it's line 1 of main.
Verify by deliberately commenting it out and watching the error, then
putting it back. Knowing what that error looks like saves panic later.

---

## Putting it together (capstone exercise)

Implement, from the building blocks above, a method:

```java
// returns the centroid of the largest orange blob, or null if none
Point findOrangeMarker(Mat bgrFrame)
```

Steps you'll need:
1. cvtColor BGR -> HSV
2. inRange for orange
3. findContours
4. pick largest above min area
5. moments -> centroid
6. return as a Point, or null

When this works, you've basically written ColorBlobDetector's core.
Compare to chapter 6 afterward.
