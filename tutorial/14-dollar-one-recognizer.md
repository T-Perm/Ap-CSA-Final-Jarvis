# Chapter 14 - $1 Unistroke Recognizer

**Audience**: Person A. **Time**: 4-6 hours.

The recognition algorithm. Implements the 2007 paper by Wobbrock, Wilson,
Li. Five steps: resample, rotate, scale, translate, compare.

This is the longest chapter. Worth it - on demo day "we implemented
the $1 Recognizer from the paper, no ML libraries" is the strongest
single answer you can give.

## Recognizer.java (interface)

`src/main/java/com/starkmouse/scratchpad/Recognizer.java`:

```java
package com.starkmouse.scratchpad;

/**
 * Strategy for recognizing a stroke as a letter or symbol.
 */
public interface Recognizer {
    /**
     * @param stroke the input stroke
     * @return best match plus alternates, or RecognitionResult.unknown()
     *         if no good match
     */
    RecognitionResult recognize(Stroke stroke);
}
```

## RecognitionResult.java

`src/main/java/com/starkmouse/scratchpad/RecognitionResult.java`:

```java
package com.starkmouse.scratchpad;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of recognizing a stroke.
 */
public class RecognitionResult {

    public static class Alternate {
        public final char letter;
        public final double confidence;
        public Alternate(char letter, double confidence) {
            this.letter = letter;
            this.confidence = confidence;
        }
    }

    private final char predictedLetter;
    private final double confidence;
    private final List<Alternate> alternates;

    public RecognitionResult(char letter, double confidence, List<Alternate> alternates) {
        this.predictedLetter = letter;
        this.confidence = confidence;
        this.alternates = alternates == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(alternates);
    }

    public static RecognitionResult unknown() {
        return new RecognitionResult('?', 0.0, Collections.emptyList());
    }

    public char getPredictedLetter() { return predictedLetter; }
    public double getConfidence() { return confidence; }
    public List<Alternate> getAlternates() { return alternates; }
}
```

## Template.java

`src/main/java/com/starkmouse/scratchpad/Template.java`:

```java
package com.starkmouse.scratchpad;

import java.awt.Point;
import java.util.Collections;
import java.util.List;

/**
 * A learned letter template. Stores the normalized 64-point version
 * of one example stroke.
 */
public class Template {
    private final char letter;
    private final List<Point> normalizedPoints;

    public Template(char letter, List<Point> normalizedPoints) {
        this.letter = letter;
        this.normalizedPoints = Collections.unmodifiableList(normalizedPoints);
    }

    public char getLetter() { return letter; }
    public List<Point> getPoints() { return normalizedPoints; }
}
```

## DollarOneRecognizer.java

The big one. `src/main/java/com/starkmouse/scratchpad/DollarOneRecognizer.java`:

```java
package com.starkmouse.scratchpad;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The $1 Unistroke Recognizer (Wobbrock, Wilson, Li, 2007).
 *
 * Five steps:
 *   1. Resample to N evenly-spaced points
 *   2. Rotate so first-to-centroid vector points to 0 degrees
 *   3. Scale to a SQUARE_SIZE x SQUARE_SIZE bounding box
 *   4. Translate so centroid is at origin
 *   5. Compare to each template, return the closest match
 */
public class DollarOneRecognizer implements Recognizer {

    /** Number of points all strokes are resampled to. */
    private static final int N = 64;
    /** Side length of the bounding square after scaling. */
    private static final double SQUARE_SIZE = 250.0;
    /** Confidence below which we say "?". */
    private static final double MIN_CONFIDENCE = 0.5;

    /** Diagonal of the square - used in confidence calculation. */
    private static final double HALF_DIAGONAL = 0.5 * Math.sqrt(SQUARE_SIZE * SQUARE_SIZE * 2);

    private final TemplateLibrary library;

    public DollarOneRecognizer(TemplateLibrary library) {
        this.library = library;
    }

    @Override
    public RecognitionResult recognize(Stroke stroke) {
        if (stroke.size() < 5) return RecognitionResult.unknown();

        List<Point> normalized = normalize(stroke.getPoints());

        List<RecognitionResult.Alternate> scores = new ArrayList<>();
        for (Template t : library.getAll()) {
            double d = pathDistance(normalized, t.getPoints());
            double score = 1.0 - (d / HALF_DIAGONAL);
            scores.add(new RecognitionResult.Alternate(t.getLetter(), score));
        }
        scores.sort(Comparator.comparingDouble((RecognitionResult.Alternate a) -> a.confidence).reversed());

        if (scores.isEmpty() || scores.get(0).confidence < MIN_CONFIDENCE) {
            return RecognitionResult.unknown();
        }

        RecognitionResult.Alternate best = scores.get(0);
        List<RecognitionResult.Alternate> alts = new ArrayList<>();
        for (int i = 1; i < Math.min(4, scores.size()); i++) alts.add(scores.get(i));
        return new RecognitionResult(best.letter, best.confidence, alts);
    }

    /**
     * Run all 4 normalization steps. Pure function: doesn't mutate input.
     */
    public static List<Point> normalize(List<Point> raw) {
        List<Point> resampled = resample(raw, N);
        double angle = indicativeAngle(resampled);
        List<Point> rotated = rotateBy(resampled, -angle);
        List<Point> scaled = scaleToSquare(rotated, SQUARE_SIZE);
        return translateToOrigin(scaled);
    }

    /**
     * Step 1: resample to n evenly-spaced points along the path.
     */
    private static List<Point> resample(List<Point> points, int n) {
        double interval = pathLength(points) / (n - 1);
        double accumulated = 0;
        List<Point> result = new ArrayList<>();
        result.add(points.get(0));

        // Walk along the path, dropping a new point every `interval` units.
        // We may need to split between consecutive original points.
        List<Point> working = new ArrayList<>(points);
        for (int i = 1; i < working.size(); i++) {
            Point prev = working.get(i - 1);
            Point cur = working.get(i);
            double d = distance(prev, cur);
            if (accumulated + d >= interval) {
                double t = (interval - accumulated) / d;
                int newX = (int) Math.round(prev.x + t * (cur.x - prev.x));
                int newY = (int) Math.round(prev.y + t * (cur.y - prev.y));
                Point pt = new Point(newX, newY);
                result.add(pt);
                working.add(i, pt);   // insert and continue from the new point
                accumulated = 0;
            } else {
                accumulated += d;
            }
        }
        // Floating-point rounding may leave us 1 short - top up.
        while (result.size() < n) {
            result.add(working.get(working.size() - 1));
        }
        return result;
    }

    /**
     * Step 2 prep: angle from centroid to first point.
     */
    private static double indicativeAngle(List<Point> points) {
        Point c = centroid(points);
        Point first = points.get(0);
        return Math.atan2(c.y - first.y, c.x - first.x);
    }

    /**
     * Step 2: rotate all points around the centroid by `angle` radians.
     */
    private static List<Point> rotateBy(List<Point> points, double angle) {
        Point c = centroid(points);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        List<Point> out = new ArrayList<>(points.size());
        for (Point p : points) {
            double dx = p.x - c.x;
            double dy = p.y - c.y;
            int nx = (int) Math.round(dx * cos - dy * sin + c.x);
            int ny = (int) Math.round(dx * sin + dy * cos + c.y);
            out.add(new Point(nx, ny));
        }
        return out;
    }

    /**
     * Step 3: scale x and y independently so bounding box is size x size.
     */
    private static List<Point> scaleToSquare(List<Point> points, double size) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        double w = Math.max(1, maxX - minX);
        double h = Math.max(1, maxY - minY);
        List<Point> out = new ArrayList<>(points.size());
        for (Point p : points) {
            int nx = (int) Math.round((p.x - minX) * (size / w));
            int ny = (int) Math.round((p.y - minY) * (size / h));
            out.add(new Point(nx, ny));
        }
        return out;
    }

    /**
     * Step 4: translate so centroid is at origin.
     */
    private static List<Point> translateToOrigin(List<Point> points) {
        Point c = centroid(points);
        List<Point> out = new ArrayList<>(points.size());
        for (Point p : points) {
            out.add(new Point(p.x - c.x, p.y - c.y));
        }
        return out;
    }

    /**
     * Step 5: average pairwise distance between two normalized strokes.
     */
    public static double pathDistance(List<Point> a, List<Point> b) {
        int n = Math.min(a.size(), b.size());
        double sum = 0;
        for (int i = 0; i < n; i++) sum += distance(a.get(i), b.get(i));
        return sum / n;
    }

    // helpers

    private static double pathLength(List<Point> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
        }
        return total;
    }

    private static Point centroid(List<Point> points) {
        long sx = 0, sy = 0;
        for (Point p : points) { sx += p.x; sy += p.y; }
        return new Point((int)(sx / points.size()), (int)(sy / points.size()));
    }

    private static double distance(Point a, Point b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```

## Imports

| Import | What |
|--------|------|
| `java.awt.Point` | 2D point |
| `java.util.ArrayList`, `Comparator`, `List` | collections |

That's it. No machine learning. No external libraries. Pure geometry.

## What each step is doing

**Step 1 - Resample**: your stroke might have 50 points (slow draw) or
500 points (fast draw). After resample both have exactly 64 evenly-
spaced points. Now we can compare them pointwise.

**Step 2 - Rotate**: a slanted A and an upright A look different unless
we rotate both to a canonical orientation. We rotate so the
"first point -> centroid" vector points along the x-axis.

**Step 3 - Scale**: a tiny A and a huge A look different unless we
scale both to the same size. Scale x and y to fit a 250x250 box.

**Step 4 - Translate**: a top-left A and a bottom-right A look
different in absolute pixels. Translate so centroid is at (0,0).

After steps 1-4, **two strokes of the same letter look almost identical**
regardless of size, rotation, position, or speed of drawing. That's the
magic.

**Step 5 - Compare**: just sum up the per-point distance between input
and template, divide by 64. The template with the smallest average
distance is the prediction.

## Confidence

Distance ranges from 0 (perfect match) to roughly the half-diagonal of
the bounding box. We convert to a 0..1 score:

```
score = 1.0 - (distance / halfDiagonal)
```

If the best score is below 0.5, we say "?" instead of guessing.

## Test (without templates yet)

Hard to test in isolation since we need templates. Skip ahead to
chapter 15 to add templates, then come back here.

Quick sanity test on normalize:

```java
public static void main(String[] args) {
    List<Point> raw = new ArrayList<>();
    raw.add(new Point(0, 0));
    raw.add(new Point(100, 0));
    raw.add(new Point(100, 100));
    List<Point> n = DollarOneRecognizer.normalize(raw);
    System.out.println("normalized has " + n.size() + " points");
    // Should print 64
}
```

Commit: `add $1 Recognizer`. Move to chapter 15.

## Why this is the rubric kill move

On demo day, when asked "how does the recognition work?":

> "We implemented the $1 Unistroke Recognizer from the 2007 paper by
> Wobbrock et al. No machine learning. The algorithm has five steps:
> resample the stroke to 64 evenly spaced points, rotate to a canonical
> orientation based on the angle from the first point to the centroid,
> scale to a 250 by 250 bounding box, translate to origin, then compare
> to each template by averaging the pairwise point distances. The
> closest template wins."

That answer demonstrates:
- You read and implemented a real algorithm
- You understand math beyond AP CSA curriculum
- You can articulate it without reading from a paper

A+.
