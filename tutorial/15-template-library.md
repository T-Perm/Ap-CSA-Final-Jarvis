# Chapter 15 - TemplateLibrary (hand-rolled JSON)

**Audience**: Person A. **Time**: 1.5-2 hours.

Save and load letter templates to/from disk. No JSON library - we
hand-roll the parser because (a) our format is dead simple, (b) it
looks more impressive in code review, and (c) it's well within scope.

## File format

`~/.stark-mouse/templates.json`:

```json
{
  "templates": [
    {"letter":"A","points":[[0,5],[10,20],[25,40]]},
    {"letter":"B","points":[[3,2],[8,15]]}
  ]
}
```

Points are the **normalized** 64-point versions (output of
`DollarOneRecognizer.normalize`). Storing already-normalized templates
means recognition is fast - no need to re-normalize on every load.

## TemplateLibrary.java

`src/main/java/com/starkmouse/scratchpad/TemplateLibrary.java`:

```java
package com.starkmouse.scratchpad;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and saves Templates as JSON. Hand-rolled parser; the format is
 * fixed so we exploit it.
 */
public class TemplateLibrary {

    private final Path file;
    private final Map<Character, Template> templates = new HashMap<>();

    public TemplateLibrary(Path file) {
        this.file = file;
    }

    /** Default location: ~/.stark-mouse/templates.json */
    public static TemplateLibrary defaultLocation() {
        Path home = Paths.get(System.getProperty("user.home"));
        return new TemplateLibrary(home.resolve(".stark-mouse").resolve("templates.json"));
    }

    public void add(Template t) {
        templates.put(t.getLetter(), t);
    }

    public Optional<Template> get(char letter) {
        return Optional.ofNullable(templates.get(letter));
    }

    public List<Template> getAll() {
        return new ArrayList<>(templates.values());
    }

    public int size() { return templates.size(); }

    /** Persist to disk. Creates parent dir if needed. */
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("{\"templates\":[");
            boolean first = true;
            for (Template t : templates.values()) {
                if (!first) w.write(",");
                first = false;
                w.write("{\"letter\":\"" + t.getLetter() + "\",\"points\":[");
                boolean firstPt = true;
                for (Point p : t.getPoints()) {
                    if (!firstPt) w.write(",");
                    firstPt = false;
                    w.write("[" + p.x + "," + p.y + "]");
                }
                w.write("]}");
            }
            w.write("]}");
        }
    }

    /** Load from disk. Does nothing if file doesn't exist. */
    public void load() throws IOException {
        if (!Files.exists(file)) return;
        templates.clear();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        parse(sb.toString());
    }

    /**
     * Parses our specific JSON shape. Not a general JSON parser - 
     * assumes our exact format. If you edit the file by hand and break
     * it, this will throw.
     */
    private void parse(String json) {
        // Find each {"letter":"X","points":[...]} block
        int cursor = 0;
        while (true) {
            int letterIdx = json.indexOf("\"letter\":\"", cursor);
            if (letterIdx < 0) break;
            int letterStart = letterIdx + 10;
            char letter = json.charAt(letterStart);

            int pointsIdx = json.indexOf("\"points\":[", letterStart);
            int arrStart = pointsIdx + 10;
            int arrEnd = findMatchingClose(json, arrStart);
            String arr = json.substring(arrStart, arrEnd);

            List<Point> pts = parsePointArray(arr);
            add(new Template(letter, pts));
            cursor = arrEnd;
        }
    }

    /** Find the ] that closes the [ at position openBracketAfter-1. */
    private int findMatchingClose(String s, int afterOpen) {
        int depth = 1;
        for (int i = afterOpen; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new RuntimeException("Unmatched [ in templates.json");
    }

    /** Parse "[1,2],[3,4],[5,6]" into List<Point>. */
    private List<Point> parsePointArray(String s) {
        List<Point> pts = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf('[', i);
            if (open < 0) break;
            int close = s.indexOf(']', open);
            String inner = s.substring(open + 1, close);
            String[] parts = inner.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            pts.add(new Point(x, y));
            i = close + 1;
        }
        return pts;
    }
}
```

## Imports

| Import | What |
|--------|------|
| `java.awt.Point` | the point type |
| `java.io.BufferedReader`, `BufferedWriter`, `IOException` | file I/O |
| `java.nio.file.Files`, `Path`, `Paths` | modern file API |
| `java.util.*` | HashMap, Optional, ArrayList |

`java.nio.file` is the modern file API (preferred over `java.io.File`).
`Files.newBufferedWriter(path)` opens a write stream. `Files.createDirectories(path)` makes the dir tree.

## Why hand-roll the parser

Real JSON parsing handles escapes, nested objects, unicode, all that
junk. Our file is machine-generated by our own code with a fixed shape:

```
{"templates":[{"letter":"X","points":[[x,y],[x,y],...]},...]}
```

So `String.indexOf` + `substring` + `split` gets us all the way. ~30
lines instead of pulling in Jackson or Gson.

If a teammate hand-edits the file and breaks the shape, the parser
throws. That's fine - it's not a user-facing file.

## Calibration tool

You need to capture templates somehow. Quick standalone tool:

`src/main/java/com/starkmouse/scratchpad/CalibrationTool.java`:

```java
package com.starkmouse.scratchpad;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Throwaway: builds a sample template library from hardcoded strokes
 * so you can test recognition before doing the real "draw 26 letters"
 * calibration UI. Replace with the real calibrator in week 4 if time.
 */
public class CalibrationTool {

    public static void main(String[] args) throws Exception {
        TemplateLibrary lib = TemplateLibrary.defaultLocation();

        // Fake "A" - up-right-down-cross
        lib.add(new Template('A', normalize(new int[][]{
            {200,400}, {300,100}, {400,400}, {250,250}, {350,250}
        })));

        // Fake "O" - circle
        List<int[]> oPts = new ArrayList<>();
        for (int deg = 0; deg <= 360; deg += 10) {
            double r = Math.toRadians(deg);
            oPts.add(new int[]{(int)(300 + 100*Math.cos(r)), (int)(250 + 100*Math.sin(r))});
        }
        lib.add(new Template('O', normalize(oPts.toArray(new int[0][]))));

        lib.save();
        System.out.println("Wrote " + lib.size() + " templates to ~/.stark-mouse/templates.json");
    }

    private static List<Point> normalize(int[][] coords) {
        List<Point> pts = new ArrayList<>();
        for (int[] xy : coords) pts.add(new Point(xy[0], xy[1]));
        return DollarOneRecognizer.normalize(pts);
    }
}
```

Run it once: `java -cp target/...with-dependencies.jar com.starkmouse.scratchpad.CalibrationTool`.

Now you have 2 templates. Test the recognizer with a hardcoded stroke:

```java
TemplateLibrary lib = TemplateLibrary.defaultLocation();
lib.load();
DollarOneRecognizer r = new DollarOneRecognizer(lib);

// Pretend you drew an A
Stroke s = new Stroke();
s.addPoint(200, 400);
s.addPoint(300, 100);
s.addPoint(400, 400);
s.addPoint(250, 250);
s.addPoint(350, 250);

RecognitionResult result = r.recognize(s);
System.out.println(result.getPredictedLetter() + " conf=" + result.getConfidence());
```

Should print `A conf=0.9x`.

## Real calibration later

In week 4, replace `CalibrationTool` with a UI that walks the user
through drawing each of A-Z. For now, the fake templates let you test
the recognition pipeline end to end.

Commit: `add TemplateLibrary and CalibrationTool`. Move to chapter 16.
