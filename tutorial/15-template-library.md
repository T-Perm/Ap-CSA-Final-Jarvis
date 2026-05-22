# Chapter 15 - TemplateLibrary (hand-rolled JSON)

**Audience**: Person A. **Time**: 1.5-2 hours.
**You will write**: `TemplateLibrary.java` and a small calibration tool.

Save/load letter templates to disk. No JSON library - our format is
fixed and machine-generated, so we parse it ourselves.

Read the `java.nio.file` section of `api-reference-4` first.

## Goal

A class that holds templates in memory, saves them to
`~/.stark-mouse/templates.json`, and loads them back.

## The file format

```json
{"templates":[{"letter":"A","points":[[0,5],[10,20]]},{"letter":"B","points":[[3,2]]}]}
```

Points are already-normalized 64-point lists. Storing them normalized
means recognition doesn't re-normalize on load.

---

## Methods recap (from api-reference-4)

- `Paths.get(System.getProperty("user.home"))` then `.resolve("...")`
  to build the path
- `Files.createDirectories(path.getParent())` to make the folder
- `Files.newBufferedWriter(path)` / `newBufferedReader(path)` in
  try-with-resources
- `Files.exists(path)` before loading

---

## Concept: exploit the fixed format

A general JSON parser handles escapes, nesting, unicode. You don't need
that. Your file always looks exactly like the format above, written by
your own `save()`. So `String.indexOf`, `substring`, and `split` are
enough. ~30 lines instead of a library.

---

## Build TemplateLibrary.java

`src/main/java/com/starkmouse/scratchpad/TemplateLibrary.java`.

### Fields

- `private final Path file`
- `private final Map<Character, Template> templates = new HashMap<>()`

### Constructor + factory

- `TemplateLibrary(Path file)` stores the path.
- `static TemplateLibrary defaultLocation()` builds the path
  `~/.stark-mouse/templates.json` and returns `new TemplateLibrary(that)`.

### Simple methods

- `add(Template t)` -> `templates.put(t.getLetter(), t)`
- `Optional<Template> get(char c)` -> `Optional.ofNullable(...)`
- `List<Template> getAll()` -> a new ArrayList of the values
- `int size()`

### save() throws IOException

1. `Files.createDirectories(file.getParent())`.
2. Open a BufferedWriter (try-with-resources).
3. Write `{"templates":[`.
4. For each template, write
   `{"letter":"X","points":[` then each point as `[x,y]` comma-
   separated, then `]}`. Comma-separate the templates too (track a
   "first" boolean so you don't put a leading comma).
5. Write `]}`.

### load() throws IOException

1. If `!Files.exists(file)` just return.
2. Clear the map.
3. Read the whole file into one String (read lines, append to a
   StringBuilder).
4. Parse it (next method).

### private parse(String json)

Walk the string finding each `"letter":"X"` with `indexOf`. For each:
- the char after the closing quote of `"letter":"` is the letter
- find the following `"points":[`, then find its matching `]`
- extract that substring and parse it into a `List<Point>` (next helper)
- `add(new Template(letter, points))`
- advance your cursor past what you consumed

You'll want a helper to find the matching `]` (count `[` depth) and a
helper to split `"[1,2],[3,4]"` into points (find each `[..]`, split the
inner on comma, parseInt).

This is fiddly string work - go slow, test with a tiny file.

### Imports

`java.awt.Point`; `java.io.BufferedReader/BufferedWriter/IOException`;
`java.nio.file.Files/Path/Paths`; `java.util.ArrayList/HashMap/List/Map/
Optional`.

---

## Build a calibration tool (to get templates)

You need templates to test recognition. Quickest path for now: a
throwaway `main` that creates a couple of templates from hardcoded
strokes and saves them.

`src/main/java/com/starkmouse/scratchpad/CalibrationTool.java` with a
`main` that:

1. `TemplateLibrary lib = TemplateLibrary.defaultLocation();`
2. Build a few `List<Point>` shapes (e.g. an "A" as up-right-down-cross,
   an "O" as a circle generated with sin/cos).
3. Normalize each with `DollarOneRecognizer.normalize(...)`.
4. `lib.add(new Template('A', normalizedA))`, etc.
5. `lib.save()`.

Run it once to write the file. Later (week 4) you can replace it with a
real "draw each letter in the air" wizard.

---

## Test the round trip

```java
TemplateLibrary lib = TemplateLibrary.defaultLocation();
lib.load();
System.out.println(lib.size());     // however many you saved
```

Then test recognition end-to-end:

```java
DollarOneRecognizer r = new DollarOneRecognizer(lib);
Stroke s = new Stroke();
// add points roughly matching your 'A' template
RecognitionResult res = r.recognize(s);
System.out.println(res.getPredictedLetter() + " " + res.getConfidence());
```

Drawing an A-ish shape should print `A` with decent confidence.

## Checklist

- [ ] save writes valid JSON in the exact format
- [ ] load round-trips (save then load gives the same templates)
- [ ] Files.createDirectories makes the folder if missing
- [ ] load is a no-op if the file is absent
- [ ] Javadoc everywhere

Commit: `add TemplateLibrary and CalibrationTool`. Move to chapter 16.
