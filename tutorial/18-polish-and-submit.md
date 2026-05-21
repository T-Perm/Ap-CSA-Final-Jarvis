# Chapter 18 - Polish, README, submit

**Audience**: both. **Time**: week 4 entire week.

You have a working app. This chapter is about getting it ready to grade.

## Calibration tool, real version

The placeholder `CalibrationTool` makes 2 fake templates. Replace with
a tool that walks the user through drawing all 26 letters.

`src/main/java/com/starkmouse/scratchpad/Calibrator.java`:

```java
package com.starkmouse.scratchpad;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;

/**
 * Walks the user through training all 26 letters. After each letter
 * is drawn and accepted, saves to the library.
 *
 * Use: run as a separate main once when first setting up the app.
 * The recognizer will load whatever templates exist.
 */
public class Calibrator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private int index = 0;
    private TemplateLibrary lib;
    private JFrame frame;
    private JLabel prompt;
    // ... rest of UI omitted - integrate with existing DrawingSurface
}
```

For our scope, the simplest path: instead of a separate calibrator,
add a "Calibrate" gesture or hotkey to the main app that captures the
current stroke as a template for a letter you select.

Easiest hack: add a "calibration mode" enum value to AppMode. When in
calibration mode, draw a letter, hit a hotkey to label and save. Walk
through A-Z.

Honestly though - for the AP CSA project, you can record templates
yourself in week 4 and commit `templates.json` to the repo. That's
fine. Demo: "We pre-trained the templates for A-Z, the app loads them
on startup."

## README

Update `README.md` with real content. Match the rubric requirements:

```markdown
# Stark Mouse

A gesture-controlled mouse for Windows with air-drawing letter
recognition. AP Computer Science A final project, Spring 2026.

Team: [Person A], [Person B] (Member C left week 1)

## How to use

1. `mvn package`
2. `java -jar target/stark-mouse-1.0.0-jar-with-dependencies.jar`
3. Tray icon appears (cyan dot near system clock). App is running.
4. Hold up your orange marker, cursor follows. 2 fingers = click.
5. Press F2 to switch to bare-hand mode. 1 finger = cursor, 2 = click.
6. Ctrl+Shift+G anywhere to pause/resume.
7. Hold open palm for 2 seconds: scratchpad appears.
8. In scratchpad: fist held 1s = pen down; palm held 1s = pen up + 
   recognize.
9. Right-click tray icon, Exit, to quit.

## Architecture

5 packages, polymorphism via `GestureDetector` interface (color blob
and hand contour implementations), $1 Recognizer for letters.

See `docs/ARCHITECTURE.md` for full design.

## Problems we encountered

- OpenCV native library wouldn't load - fixed by using org.openpnp
  fork that bundles DLLs.
- Cursor jittery - fixed with exponential smoothing.
- Mirror image confusing - fixed by mirroring x.
- Skin detection picked up wooden floor - had to demo in front of a
  dark background.
- HandContourDetector dropped fingers when hand wasn't perpendicular to
  camera - tuned defect depth threshold.
- Mode-switching was complicated - dispatch in mapper resolved it.
- [your team's actual problems]

## Features we did not implement

- Type-into-active-window (we cut this when Member C left)
- Full 26-letter recognition reliability - we have 10 well-tuned letters
- Visual feedback HUD - chose Whisper Flow style invisibility instead
- ASL static handshape recognition (cut after considering scope)

## What we learned

[Person A]: Spent a lot of time on computer vision. HSV thresholding
and convex hull defects make sense. The $1 Recognizer is elegant -
geometry beats machine learning for simple shapes.

[Person B]: Java's standard library has more than I knew - Robot,
SystemTray, Swing's drawing pipeline. The state-machine pattern for
mode switching was the trickiest design problem; the AppMode enum
made it manageable.

## Tech

Java 17, Maven, OpenCV 4.9 (org.openpnp), JNativeHook 2.2.2,
java.awt.Robot, SystemTray, Swing/Java2D.
```

## Javadoc audit

Walk every file. Every method needs:

```java
/**
 * What it does (one sentence).
 * @param name what each param is for
 * @return what comes back
 */
```

Every field needs at least:

```java
/** What this field holds. */
private final int x;
```

Time budget: 2-3 hours. Don't skip.

## Demo prep

**Pre-flight checklist** (do day-before):

- [ ] `mvn clean package` builds with zero warnings
- [ ] Tray icon appears within 5 sec of launch
- [ ] Color blob detector works under demo lighting
- [ ] Hand contour detector works against demo background
- [ ] Templates calibrated for the demo machine's lighting
- [ ] Ctrl+Shift+G toggles reliably
- [ ] F1/F2 switches detectors reliably
- [ ] Scratchpad opens on palm-2s reliably (this is the riskiest)
- [ ] Backup video recorded showing all features

**Demo script** (5 min):

1. (0:00) Launch app. "Notice the cyan dot in the tray. That's the
   only UI - this app is meant to run quietly in the background like
   Whisper Flow."
2. (0:30) Hold up orange highlighter. Cursor follows. "The default
   detector tracks a colored marker via HSV thresholding."
3. (1:00) F2. "Now switching to hand contour detection. This finds my
   bare hand via skin color in YCrCb space and counts fingers using
   convex hull defects."
4. (1:30) Show 1, 2, 3 fingers - cursor, click, right-click.
5. (2:00) Ctrl+Shift+G. "Disengage. Global hotkey via JNativeHook -
   works even though the app doesn't have a window. Now I can use my
   trackpad normally."
6. (2:30) Ctrl+Shift+G to re-engage. Hold palm for 2 seconds.
   Scratchpad opens.
7. (3:00) "This is our air-drawing scratchpad. Fist for pen down,
   draw a letter, open palm to lift and recognize."
8. (3:30) Draw A. Open palm. "A" appears with 94% confidence.
9. (4:00) "The recognizer is the $1 Unistroke Recognizer from a 2007
   HCI paper. No machine learning - it normalizes the stroke and
   compares to templates we trained."
10. (4:30) Close scratchpad. Open palm 2s.
11. (5:00) "And that's everything."

**Recovery moves** if things break:

- Cursor flying around -> Ctrl+Shift+G to pause, restart demo with
  better lighting
- Hand not detected -> physically move closer to camera, or switch
  detector back to color blob
- Scratchpad won't open -> there's a backup Ctrl+Shift+S you should
  add for emergency (replace the F1 handler with this in MainApp if
  the gesture is unreliable):
  
  ```java
  hotkey.setOnDetector1(() -> {
      if (mapper.getMode() == AppMode.MOUSE) {
          scratchpad.show();
      } else {
          scratchpad.hide();
      }
  });
  ```

## Record a backup video

Whatever happens at the demo, you have video. Use Windows Game Bar
(Win+G) or OBS Studio. Record the full 5-min demo at home in good
lighting. If the live demo flops, "I have a backup recording" saves
the day.

## Submit

Check the rubric one more time. The submission requirements:

- Zip the project with `mvn clean` first (so target/ isn't there)
- The README must include: how to use, problems hit, features cut,
  what learned. All four required by the rubric.
- Every method has Javadoc.
- Git history shows individual contribution.

```
mvn clean
cd ..
zip -r stark-mouse.zip stark-mouse/ -x "*/target/*" "*.git/*"
```

Upload to the LMS. Done.

## After submission

Don't touch the code. Don't second-guess. You shipped.

Good luck with the demo.
