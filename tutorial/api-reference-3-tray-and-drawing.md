# API reference 3 - SystemTray and Swing drawing

Textbook reference for the tray icon and for custom 2D drawing. Both
built into the JDK.

---

# Part A: SystemTray

The tray icon is your app's only persistent UI (Whisper Flow style).

## SystemTray.isSupported

**What it does**: returns whether the OS has a system tray. Always check
before using.

**Signature**: `static boolean SystemTray.isSupported()`

**Example**:

```java
import java.awt.SystemTray;

if (!SystemTray.isSupported()) {
    System.err.println("No tray on this OS");
    return;
}
```

**Try it**: Print whether your machine supports the tray. (On Windows
it will; this is just to see the API.)

---

## Building a TrayIcon

**What it is**: the icon that sits near the system clock.

**Construction**:
```java
TrayIcon(Image image, String tooltip, PopupMenu menu)
```

You need an `Image`, a tooltip string, and a right-click `PopupMenu`.

**Adding to the tray**:
```java
SystemTray.getSystemTray().add(trayIcon);   // throws AWTException
```

**Example**:

```java
import java.awt.*;

PopupMenu menu = new PopupMenu();
MenuItem exit = new MenuItem("Exit");
exit.addActionListener(e -> System.exit(0));
menu.add(exit);

TrayIcon icon = new TrayIcon(myImage, "My App", menu);
icon.setImageAutoSize(true);
SystemTray.getSystemTray().add(icon);
```

**Note**: `PopupMenu`, `MenuItem` are the *old* AWT classes (not Swing's
`JPopupMenu`). SystemTray requires the AWT ones.

**addActionListener with a lambda**: `e -> System.exit(0)` is a lambda
implementing `ActionListener`. Fires when the menu item is clicked.

**Try it**: Show a tray icon (use a placeholder image - see drawing
section below to make one) with a menu containing "Hello" and "Exit".
Wire "Hello" to print to console, "Exit" to quit. Right-click the icon
to test.

---

## TrayIcon.displayMessage (balloon notification)

**What it does**: shows a Windows toast/balloon notification.

**Signature**:
```java
void displayMessage(String caption, String text, TrayIcon.MessageType type)
```

`MessageType` is `INFO`, `WARNING`, `ERROR`, or `NONE`.

**Example**:

```java
icon.displayMessage("Stark Mouse", "Paused", TrayIcon.MessageType.INFO);
```

**Try it**: Show a notification when your "Hello" menu item is clicked
instead of printing to console. This is how you'll give the user
feedback ("Paused"/"Active") without a window.

---

## TrayIcon.setImage (changing the icon)

**What it does**: swaps the icon's image at runtime. Use it to reflect
state via color.

**Signature**: `void setImage(Image img)`

**Example**:

```java
icon.setImage(makeIcon(Color.ORANGE));   // see drawing section for makeIcon
```

**Try it**: Make three icon images (cyan, orange, gray) and a menu with
items that switch between them. Click each, watch the tray icon change
color. This is exactly how the app shows idle/active/paused state.

---

# Part B: Swing drawing (Java2D)

You'll draw the scratchpad surface and the tray icon yourself with
Java2D. The core idea: override `paintComponent` on a `JPanel`, get a
`Graphics2D`, call drawing methods.

## The paintComponent pattern

**What it is**: the method Swing calls to paint a component. You
override it; you never call it directly (you call `repaint()` which
schedules it).

**Signature**: `protected void paintComponent(Graphics g)`

**The boilerplate**:

```java
import javax.swing.JPanel;
import java.awt.*;

class MyPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);                       // clear background
        Graphics2D g2 = (Graphics2D) g.create();       // working copy
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        // ... draw here ...
        g2.dispose();                                  // clean up the copy
    }
}
```

**Why `g.create()` then `dispose()`**: you get a private copy of the
graphics context so any state you set (color, stroke, font) doesn't
leak back to Swing. Dispose frees it.

**Why antialiasing**: without it, lines and circles look jagged. With
it, smooth.

**Try it**: Make a JPanel that fills itself with a dark background and
draws "HELLO" in cyan in the center (see drawString below). Put it in
a JFrame, show it.

---

## Graphics2D drawing methods

All take pixel coordinates, origin top-left, y increases downward.

- `g2.setColor(Color c)` - set the current color
- `g2.drawLine(x1, y1, x2, y2)` - a line
- `g2.drawRect(x, y, w, h)` / `fillRect(...)` - rectangle outline / filled
- `g2.drawOval(x, y, w, h)` / `fillOval(...)` - oval; w==h gives a circle
- `g2.drawString(String s, x, y)` - text; (x,y) is the *baseline* left
- `g2.setFont(Font f)` - set the font
- `g2.setStroke(Stroke s)` - set line thickness/caps

**Color**: `new Color(r, g, b)` with 0-255 each, or `new Color(r,g,b,a)`
with alpha (transparency).

**Font**: `new Font("Monospaced", Font.BOLD, 14)`.

**Stroke**: `new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)`
for a thick rounded line.

**Example - draw a circle with a label**:

```java
g2.setColor(new Color(255, 140, 50));
g2.fillOval(100 - 6, 100 - 6, 12, 12);          // dot centered at (100,100)
g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
g2.drawString("target", 110, 104);
```

**fillOval centering trick**: `fillOval(x, y, w, h)` draws from the
*top-left* corner. To center a dot of radius r at (cx, cy), call
`fillOval(cx - r, cy - r, 2*r, 2*r)`.

**Try it**: Draw a target reticle at (200, 150): a hollow circle radius
12, a filled dot radius 3 in the center, and four short crosshair lines
sticking out. This is the kind of thing you'd draw at a tracked point.

---

## Making an image in code (for the tray icon)

You don't need a PNG file - draw the icon programmatically.

**Pattern**:

```java
import java.awt.image.BufferedImage;
import java.awt.*;

Image makeIcon(Color color) {
    BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillOval(2, 2, 12, 12);
    g.dispose();
    return img;
}
```

`BufferedImage` is an in-memory image. `TYPE_INT_ARGB` means it has an
alpha channel (transparency) so the corners outside the circle are
clear. `createGraphics()` gives you a Graphics2D to draw on it.

**Try it**: Write `makeIcon(Color)` and use it to make the cyan,
orange, and gray tray icons from Part A's exercise.

---

## Drawing a smooth path (the scratchpad stroke)

To draw a hand-drawn stroke smoothly (not as jagged line segments), use
`GeneralPath` with quadratic curves through midpoints.

**Pattern**:

```java
import java.awt.geom.GeneralPath;

GeneralPath path = new GeneralPath();
path.moveTo(points.get(0).x, points.get(0).y);
for (int i = 1; i < points.size() - 1; i++) {
    Point p = points.get(i);
    Point next = points.get(i + 1);
    double mx = (p.x + next.x) / 2.0;
    double my = (p.y + next.y) / 2.0;
    path.quadTo(p.x, p.y, mx, my);    // curve through p to the midpoint
}
Point last = points.get(points.size() - 1);
path.lineTo(last.x, last.y);

g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
g2.setColor(new Color(20, 60, 100));
g2.draw(path);
```

**Why**: drawing straight lines between captured points makes corners
at every point - looks staircased. Curving through midpoints smooths
it. This is the Apple-Watch-Scribble look.

**Try it**: Capture mouse-drag points in a JPanel (add a
MouseMotionListener that adds `e.getPoint()` to a list and calls
`repaint()`), then draw them once with `drawLine` chains and once with
the GeneralPath approach. Compare smoothness.

---

## repaint vs paintComponent

**The rule**: never call `paintComponent` yourself. Call `repaint()`.
Swing schedules the actual paint on its own thread when ready.

**Example**:

```java
public void setStroke(List<Point> pts) {
    this.points = pts;
    repaint();              // ask Swing to repaint soon
}
```

**Try it**: nothing new to build - just internalize: "I changed data,
now I call repaint()." Every setter that affects what's drawn ends with
`repaint()`.

---

## Capstone exercise

Build a JPanel that:
1. Has a dark background drawn in paintComponent
2. Draws a dotted grid (nested loops of small filled ovals every 30px)
3. Tracks the mouse (MouseMotionListener) and draws a glowing dot at the
   cursor position
4. When you drag, records points and draws a smooth path through them

This is essentially DrawingSurface (chapter 16). Compare afterward.
