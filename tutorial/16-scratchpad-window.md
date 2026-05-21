# Chapter 16 - Scratchpad window

**Audience**: Person B. **Time**: 2-3 hours.

The Swing window that appears when the scratchpad gesture fires.
Replace the chapter 9 stub. The window contains a drawing surface
(left) and a recognition panel (right).

## ScratchpadController.java (replace the stub)

`src/main/java/com/starkmouse/scratchpad/ScratchpadController.java`:

```java
package com.starkmouse.scratchpad;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Public API for the scratchpad. Owns the window. Everything called
 * from GestureMapper goes through here.
 */
public class ScratchpadController {

    private JFrame frame;
    private DrawingSurface surface;
    private RecognitionPanel results;
    private final StrokeRecorder recorder = new StrokeRecorder();
    private final Recognizer recognizer;

    // camera dimensions for coord mapping - set externally
    private int cameraWidth = 640;
    private int cameraHeight = 480;

    public ScratchpadController(Recognizer recognizer) {
        this.recognizer = recognizer;
        SwingUtilities.invokeLater(this::build);
    }

    public void setCameraSize(int w, int h) {
        this.cameraWidth = w;
        this.cameraHeight = h;
    }

    private void build() {
        frame = new JFrame("Stark Mouse Scratchpad");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setSize(700, 450);

        surface = new DrawingSurface(recorder.getCurrent());
        surface.setPreferredSize(new Dimension(500, 410));
        results = new RecognitionPanel();
        results.setPreferredSize(new Dimension(200, 410));

        frame.setLayout(new BorderLayout());
        frame.add(surface, BorderLayout.CENTER);
        frame.add(results, BorderLayout.EAST);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setVisible(true);
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setVisible(false);
        });
    }

    public boolean isShowing() {
        return frame != null && frame.isVisible();
    }

    /** Called every frame with camera-space coords. */
    public void trackCursor(int cameraX, int cameraY) {
        if (surface == null) return;
        int[] surface_xy = mapToSurface(cameraX, cameraY);
        recorder.onPoint(surface_xy[0], surface_xy[1]);
        surface.setCursor(surface_xy[0], surface_xy[1], recorder.isPenDown());
        surface.repaint();
    }

    public void penDown() {
        recorder.penDown();
    }

    public void penUp() {
        recorder.penUp();
        Stroke s = recorder.getCurrent();
        if (s.size() >= 5) {
            RecognitionResult r = recognizer.recognize(s);
            if (results != null) results.showResult(r);
        }
    }

    /** Mirror + scale camera coords to surface coords. */
    private int[] mapToSurface(int cameraX, int cameraY) {
        if (surface == null) return new int[]{0, 0};
        double mirrored = cameraWidth - cameraX;
        int sx = (int)(mirrored / cameraWidth * surface.getWidth());
        int sy = (int)((double) cameraY / cameraHeight * surface.getHeight());
        return new int[]{sx, sy};
    }
}
```

## DrawingSurface.java

`src/main/java/com/starkmouse/scratchpad/DrawingSurface.java`:

```java
package com.starkmouse.scratchpad;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.util.List;

/**
 * Paints the current stroke and live cursor on a dotted grid.
 */
public class DrawingSurface extends JPanel {

    private static final Color BG = new Color(245, 248, 250);
    private static final Color GRID = new Color(180, 200, 220);
    private static final Color INK = new Color(20, 60, 100);
    private static final Color CURSOR_DOWN = new Color(255, 110, 40);
    private static final Color CURSOR_UP = new Color(120, 160, 200);

    private final Stroke stroke;
    private int cursorX = 0;
    private int cursorY = 0;
    private boolean penDown = false;

    public DrawingSurface(Stroke stroke) {
        this.stroke = stroke;
        setBackground(BG);
    }

    public void setCursor(int x, int y, boolean penDown) {
        this.cursorX = x;
        this.cursorY = y;
        this.penDown = penDown;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g2);
        drawStroke(g2);
        drawCursor(g2);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID);
        for (int x = 20; x < getWidth(); x += 30) {
            for (int y = 20; y < getHeight(); y += 30) {
                g2.fillOval(x - 1, y - 1, 2, 2);
            }
        }
    }

    private void drawStroke(Graphics2D g2) {
        List<Point> pts = stroke.getPoints();
        if (pts.size() < 2) return;
        GeneralPath path = new GeneralPath();
        path.moveTo(pts.get(0).x, pts.get(0).y);
        for (int i = 1; i < pts.size() - 1; i++) {
            Point p = pts.get(i);
            Point next = pts.get(i + 1);
            double mx = (p.x + next.x) / 2.0;
            double my = (p.y + next.y) / 2.0;
            path.quadTo(p.x, p.y, mx, my);
        }
        Point last = pts.get(pts.size() - 1);
        path.lineTo(last.x, last.y);

        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(INK);
        g2.draw(path);
    }

    private void drawCursor(Graphics2D g2) {
        g2.setColor(penDown ? CURSOR_DOWN : CURSOR_UP);
        g2.fillOval(cursorX - 6, cursorY - 6, 12, 12);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(cursorX - 14, cursorY - 14, 28, 28);
    }
}
```

## RecognitionPanel.java

`src/main/java/com/starkmouse/scratchpad/RecognitionPanel.java`:

```java
package com.starkmouse.scratchpad;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Right panel of the scratchpad. Shows the predicted letter, confidence
 * bar, and top alternates.
 */
public class RecognitionPanel extends JPanel {

    private static final Color BG = new Color(248, 250, 252);
    private static final Color INK = new Color(20, 60, 100);
    private static final Color MUTED = new Color(140, 160, 180);
    private static final Color GREEN = new Color(40, 170, 90);

    private RecognitionResult result = RecognitionResult.unknown();

    public RecognitionPanel() {
        setBackground(BG);
    }

    public void showResult(RecognitionResult r) {
        this.result = r;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(MUTED);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("PREDICTION", 20, 30);

        g2.setColor(INK);
        g2.setFont(new Font("Monospaced", Font.BOLD, 80));
        String letter = String.valueOf(result.getPredictedLetter());
        g2.drawString(letter, 60, 130);

        g2.setColor(MUTED);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("CONFIDENCE", 20, 170);
        int pct = (int)(result.getConfidence() * 100);
        g2.setColor(pct >= 70 ? GREEN : new Color(220, 140, 40));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString(pct + "%", getWidth() - 50, 170);

        // confidence bar
        int barW = getWidth() - 40;
        g2.setColor(new Color(220, 230, 240));
        g2.fillRect(20, 180, barW, 6);
        g2.setColor(pct >= 70 ? GREEN : new Color(220, 140, 40));
        g2.fillRect(20, 180, (int)(barW * result.getConfidence()), 6);

        // alternates
        g2.setColor(MUTED);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("ALTERNATES", 20, 220);
        int y = 245;
        for (RecognitionResult.Alternate a : result.getAlternates()) {
            g2.setColor(INK);
            g2.drawString(String.valueOf(a.letter), 30, y);
            g2.setColor(MUTED);
            int apct = (int)(a.confidence * 100);
            g2.drawString(apct + "%", getWidth() - 50, y);
            y += 18;
        }
    }
}
```

## Imports recap

| Import | Where used |
|--------|-----------|
| `javax.swing.JFrame`, `JPanel`, `SwingUtilities` | windows + panels |
| `java.awt.BorderLayout`, `Dimension` | window layout |
| `java.awt.BasicStroke` | thick stroke for ink |
| `java.awt.Color`, `Font` | colors and fonts |
| `java.awt.Graphics`, `Graphics2D` | drawing context |
| `java.awt.Point` | from Stroke |
| `java.awt.RenderingHints` | antialiasing |
| `java.awt.geom.GeneralPath` | smooth path |

## Smooth stroke (quadratic curves trick)

For each captured point, draw a quadratic Bezier to the midpoint of the
next pair. This makes the line look smooth instead of like a chain of
short segments. Same trick Apple Watch Scribble uses.

## Wire it into MainApp

In `MainApp.launch()`, replace the stub instantiation:

```java
TemplateLibrary lib = TemplateLibrary.defaultLocation();
try { lib.load(); } catch (IOException e) {
    System.err.println("No templates yet, run CalibrationTool first.");
}
DollarOneRecognizer recognizer = new DollarOneRecognizer(lib);
scratchpad = new ScratchpadController(recognizer);
```

Update `MainApp` imports:

```java
import com.starkmouse.scratchpad.DollarOneRecognizer;
import com.starkmouse.scratchpad.TemplateLibrary;
import java.io.IOException;
```

In `startLoop`, after `mouse.setFrameSize(...)`, also set the scratchpad
camera size:

```java
scratchpad.setCameraSize(first.cols(), first.rows());
```

## Test

Run the app. Hold palm up for 2 seconds. Scratchpad window appears.
Make a fist (held 1s) - cursor turns orange (pen down). Move hand to
draw a shape. Open palm - cursor returns to blue, recognition runs,
right panel updates. Hold palm 2s again - scratchpad disappears.

Commit: `add scratchpad window and panels`. Move to chapter 17.
