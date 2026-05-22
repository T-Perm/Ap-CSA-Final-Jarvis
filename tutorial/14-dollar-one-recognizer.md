# Chapter 14 - $1 Unistroke Recognizer

**Audience**: Person A. **Time**: 4-6 hours.
**You will write**: `Recognizer.java`, `RecognitionResult.java`,
`Template.java`, `DollarOneRecognizer.java`.

The recognition algorithm, from the 2007 Wobbrock/Wilson/Li paper. Five
steps: resample, rotate, scale, translate, compare. No machine learning.

This is the longest build in the project. Implement each step as its
own method and test it before moving on.

## The big picture

Two strokes of the same letter look different (size, rotation, position,
speed). The four normalization steps transform any stroke into a
canonical 64-point form so that same-letter strokes become nearly
identical. Then comparison is just averaging point-to-point distances.

---

## The supporting types (build these first - they're easy)

### Recognizer.java (interface)

`src/main/java/com/starkmouse/scratchpad/Recognizer.java`. One method:
`RecognitionResult recognize(Stroke stroke)`. Same interface-for-
swappability idea as GestureDetector.

### RecognitionResult.java

`src/main/java/com/starkmouse/scratchpad/RecognitionResult.java`.

A nested static class `Alternate` holding a `char letter` and `double
confidence` (public final fields are fine here).

Fields: `char predictedLetter`, `double confidence`, `List<Alternate>
alternates`.

A static factory `unknown()` returning a result with letter `'?'`,
confidence 0, empty alternates list. Getters for the three fields.

### Template.java

`src/main/java/com/starkmouse/scratchpad/Template.java`. Holds a `char
letter` and a `List<Point> normalizedPoints` (the 64-point canonical
form of one training example). Constructor + getters.

---

## Concept: each normalization step

Study these. You'll implement each as a `static List<Point>` method
that takes points and returns transformed points (pure functions - don't
mutate the input).

### Step 1: resample to N points (N = 64)

Your raw stroke has uneven spacing. Walk along the path; every time
you've traveled `pathLength / (N-1)` units, drop a new point
(interpolating between the two original points you're between). End
with exactly N points.

The interpolation when you cross the interval between points `prev` and
`cur`: fraction `t = (interval - accumulated) / distance(prev, cur)`,
new point at `prev + t * (cur - prev)` on each axis.

Tiny illustration of interpolation (not the full method):

```java
double t = 0.4;
int nx = (int)Math.round(prev.x + t * (cur.x - prev.x));
int ny = (int)Math.round(prev.y + t * (cur.y - prev.y));
```

Watch out: floating-point rounding can leave you one point short; after
the loop, if you have < N points, append copies of the last point.

### Step 2: rotate to a canonical angle

Find the "indicative angle" = angle from the centroid to the first
point: `Math.atan2(first.y - centroid.y, first.x - centroid.x)` (or the
reverse sign - be consistent). Rotate ALL points around the centroid by
the negative of that angle, so the first point lands on the +x axis.

Rotation of a point around center `c` by angle `a`:

```java
double dx = p.x - c.x, dy = p.y - c.y;
int nx = (int)Math.round(dx*Math.cos(a) - dy*Math.sin(a) + c.x);
int ny = (int)Math.round(dx*Math.sin(a) + dy*Math.cos(a) + c.y);
```

### Step 3: scale to a square

Find the bounding box (min/max x and y). Scale x by `SIZE/boxWidth` and
y by `SIZE/boxHeight` independently (SIZE = 250) so the stroke fills a
250x250 box regardless of original size. Guard against zero width/height
(use `Math.max(1, ...)`).

### Step 4: translate to origin

Compute the centroid, subtract it from every point so the centroid sits
at (0,0).

### Step 5: compare (path distance)

Given two normalized 64-point lists, sum the Euclidean distance between
corresponding points (a[0]-b[0], a[1]-b[1], ...) and divide by N. Lower
= more similar.

---

## Helpers you'll need

Write these small `static` helpers (used by the steps above):

- `double pathLength(List<Point>)` - sum of distances between
  consecutive points
- `Point centroid(List<Point>)` - average x, average y
- `double distance(Point, Point)` - Euclidean
- (a `boundingBox` is just inline min/max in step 3)

---

## Build DollarOneRecognizer.java

`src/main/java/com/starkmouse/scratchpad/DollarOneRecognizer.java`
implementing `Recognizer`.

### Constants

`N = 64`, `SQUARE_SIZE = 250.0`, `MIN_CONFIDENCE = 0.5`, and a
`HALF_DIAGONAL = 0.5 * Math.sqrt(SIZE*SIZE*2)` used to turn distance
into a 0..1 confidence.

### Field

A `TemplateLibrary library` (next chapter) passed in the constructor.
For now you can stub the library or build chapter 15 first - your call.

### A public static normalize(List<Point> raw)

Runs steps 1-4 in order and returns the canonical points. Make it
`public static` so the calibration tool (ch15) can normalize training
examples with the same code.

### recognize(Stroke stroke)

1. If `stroke.size() < 5`, return `RecognitionResult.unknown()`.
2. `normalize` the stroke's points.
3. For each template in the library: compute `pathDistance` to the
   normalized input, convert to a score `1.0 - distance/HALF_DIAGONAL`.
   Collect (letter, score) pairs.
4. Sort by score descending.
5. If empty or best score < MIN_CONFIDENCE, return `unknown()`.
6. Build a `RecognitionResult` with the best letter+score and the next
   2-3 as alternates.

### Imports

`java.awt.Point`, `java.util.ArrayList`, `java.util.List`,
`java.util.Comparator` (for sorting).

---

## Test each step in isolation FIRST

Before testing recognition, test normalize on a known shape:

```java
List<Point> raw = new ArrayList<>();
raw.add(new Point(0,0));
raw.add(new Point(100,0));
raw.add(new Point(100,100));
List<Point> n = DollarOneRecognizer.normalize(raw);
System.out.println(n.size());   // must be exactly 64
```

If it's not 64, your resample top-up is wrong. Fix that before anything
else - every later step assumes 64 points.

Sanity-check rotate/scale/translate by printing the centroid after
translate (should be ~0,0) and the bounding box after scale (should be
~250x250).

Full recognition test comes after chapter 15 gives you templates.

## Checklist

- [ ] normalize always returns exactly 64 points
- [ ] Each step is its own pure static method
- [ ] After translate, centroid ~ (0,0)
- [ ] Confidence is 0..1
- [ ] Returns unknown() below threshold
- [ ] Javadoc everywhere

This is the rubric kill move - "implemented from the paper, no ML."
Make sure you can explain all five steps verbally.

Commit: `add $1 Recognizer`. Move to chapter 15.
