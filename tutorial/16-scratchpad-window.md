# Chapter 16 - Scratchpad window

**Audience**: Person B. **Time**: 2-3 hours.
**You will write**: the real `ScratchpadController.java` (replacing the
stub), plus `DrawingSurface.java` and `RecognitionPanel.java`.

The window that appears on the scratchpad gesture. Drawing surface on
the left, recognition results on the right.

Read the Swing-drawing section of `api-reference-3` first
(paintComponent, Graphics2D, GeneralPath, repaint).

## Goal

- `ScratchpadController` - owns the JFrame, exposes show/hide/track/
  penDown/penUp (the methods the mapper already calls).
- `DrawingSurface` - JPanel that draws the stroke + live cursor.
- `RecognitionPanel` - JPanel showing the predicted letter + confidence.

---

## Concept: JFrame as a floating window

- `new JFrame(title)`
- `frame.setUndecorated(false)` (a title bar is fine here)
- `frame.setAlwaysOnTop(true)`
- `frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE)` so closing
  hides rather than kills the whole app
- `frame.setLayout(new BorderLayout())`, add panels with
  `BorderLayout.CENTER` and `BorderLayout.EAST`
- `frame.pack()`, `frame.setLocationRelativeTo(null)` to center
- `frame.setVisible(true/false)` to show/hide

Build the frame on the EDT (`SwingUtilities.invokeLater`).

---

## Concept: smooth stroke rendering

To draw the captured points as a smooth line (not jagged), use
`GeneralPath` with `quadTo` through midpoints (see api-reference-3
"Drawing a smooth path"). Wider rounded `BasicStroke` makes it look
like ink.

---

## Concept: coordinate mapping (again)

The mapper calls `trackCursor(camX, camY)` with CAMERA coords. You must
mirror + scale to the drawing surface's pixel size before recording/
drawing - same mirror trick as MouseController:

```
mirrored = camW - camX
surfaceX = mirrored / camW * surface.getWidth()
surfaceY = camY     / camH * surface.getHeight()
```

---

## Build DrawingSurface.java

`src/main/java/com/starkmouse/scratchpad/DrawingSurface.java` extending
`JPanel`.

Constructor takes the `Stroke` to draw (shared reference with the
recorder).

Fields for the live cursor: `int cursorX, cursorY`, `boolean penDown`.
A setter `setCursor(x, y, penDown)`.

Override `paintComponent(Graphics g)`:
1. `super.paintComponent(g)`, get a Graphics2D copy, enable
   antialiasing.
2. Draw a light dotted grid (nested loops of tiny `fillOval`s every
   ~30px).
3. Draw the stroke as a smooth GeneralPath (skip if < 2 points).
4. Draw the live cursor: a filled dot + an outer ring; color it orange
   when penDown, blue/gray when up.
5. `g2.dispose()`.

Imports: `javax.swing.JPanel`; `java.awt.*` (BasicStroke, Color,
Graphics, Graphics2D, Point, RenderingHints); `java.awt.geom.GeneralPath`;
`java.util.List`.

---

## Build RecognitionPanel.java

`src/main/java/com/starkmouse/scratchpad/RecognitionPanel.java` extending
`JPanel`.

Field: a `RecognitionResult result` (init to `unknown()`). A method
`showResult(RecognitionResult r)` that stores it and calls `repaint()`.

Override `paintComponent`:
- A small "PREDICTION" label (drawString, small monospace font).
- The predicted letter big and bold (e.g. 80pt) below it.
- A "CONFIDENCE" label + percentage, and a filled bar whose width is
  `confidence * panelWidth` (green if >= 70%, amber otherwise).
- An "ALTERNATES" label, then loop the alternates printing letter +
  percentage.

Imports: `javax.swing.JPanel`; `java.awt.Color/Font/Graphics/Graphics2D/
RenderingHints`.

---

## Build ScratchpadController.java (replace the stub)

`src/main/java/com/starkmouse/scratchpad/ScratchpadController.java`.

Fields:
- `JFrame frame`, `DrawingSurface surface`, `RecognitionPanel results`
- `private final StrokeRecorder recorder = new StrokeRecorder()`
- `private final Recognizer recognizer` (constructor param)
- `int cameraWidth = 640, cameraHeight = 480` with a `setCameraSize(w,h)`

Constructor: takes a `Recognizer`, stores it, and
`SwingUtilities.invokeLater(this::build)`.

`private build()`: create the frame, the surface (give it the
recorder's current Stroke), the results panel, lay them out
(CENTER/EAST), pack, center.

The methods the mapper calls (you stubbed these in ch9 - now make them
real):
- `show()` / `hide()`: toggle frame visibility on the EDT.
- `isShowing()`: frame != null && frame.isVisible().
- `trackCursor(int camX, int camY)`: map to surface coords, call
  `recorder.onPoint(...)`, update `surface.setCursor(...)`,
  `surface.repaint()`.
- `penDown()`: `recorder.penDown()`.
- `penUp()`: `recorder.penUp()`; if the stroke has >= 5 points, run
  `recognizer.recognize(...)` and `results.showResult(...)`.

A private `int[] mapToSurface(camX, camY)` doing the mirror+scale.

Imports: `javax.swing.JFrame/SwingUtilities`; `java.awt.BorderLayout/
Dimension`.

---

## Wire it into MainApp

Replace the stub instantiation in `launch()`:

1. `TemplateLibrary lib = TemplateLibrary.defaultLocation(); lib.load();`
   (wrap in try/catch IOException; warn if no templates yet).
2. `DollarOneRecognizer recognizer = new DollarOneRecognizer(lib);`
3. `scratchpad = new ScratchpadController(recognizer);`

In `startLoop`, after `mouse.setFrameSize(...)`, also
`scratchpad.setCameraSize(frame.cols(), frame.rows())`.

Add the needed imports to MainApp.

---

## Test

Run the app. Hold palm 2s -> window appears. Fist 1s -> cursor turns
orange (pen down). Move your hand to draw. Open palm -> cursor returns
to blue, recognition runs, right panel shows a letter. Palm 2s ->
window hides.

## Checklist

- [ ] Window built on the EDT
- [ ] HIDE_ON_CLOSE (closing doesn't kill the app)
- [ ] Stroke renders smoothly (GeneralPath, not drawLine chains)
- [ ] Coordinates mirrored (drawing an A isn't backwards)
- [ ] penUp triggers recognition only with enough points
- [ ] Javadoc everywhere

There's no reference for this class (it postdates the reference set) -
you're on your own, which is good practice. Ask Claude with
api-reference-3 + your code if stuck.

Commit: `add scratchpad window`. Move to chapter 17.
