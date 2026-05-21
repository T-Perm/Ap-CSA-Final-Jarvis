# Chapter 17 - Mode switching edge cases

**Audience**: both. **Time**: 1 hour.

The mode system works in the simple case but breaks at edges. This
chapter is about fixing those.

## Bug 1: GestureMapper has no recognizer reference

`ScratchpadController` was created with `null` recognizer in chapter 9
stub. The constructor was changed in chapter 16 to take a Recognizer.
Make sure MainApp passes one in.

## Bug 2: Same gesture fires repeatedly

`HandContourDetector.applyHoldLogic` emits PEN_DOWN once when the
threshold is crossed. But what if the user makes a fist, holds for
1.2s (PEN_DOWN fires), keeps holding for another 5s (no new events
because `penFired = true`), then releases? That's the intended behavior
in scratchpad mode (one pen-down, then drag).

But in mouse mode, fist held = pause. Once pause fires, you should stay
paused. Also intended.

Edge case: user fists, PEN_DOWN fires, app is in mouse mode, mapper
calls `enabled = false`. User releases fist, makes another fist. The
detector resets `penFired = false` (because finger count changed
through the release). New PEN_DOWN fires. Mapper sets `enabled = false`
again. Still consistent. OK.

## Bug 3: Closing scratchpad while pen is down

User opens scratchpad, makes a fist (pen down), then somehow holds palm
2s (which fires SCRATCHPAD_TOGGLE). The pen-down state was true; now
scratchpad is hidden but recorder still thinks pen is down.

Fix in `GestureMapper.toggleMode()`:

```java
private void toggleMode() {
    if (mode == AppMode.MOUSE) {
        mode = AppMode.SCRATCHPAD;
        if (scratchpad != null) scratchpad.show();
    } else {
        mode = AppMode.MOUSE;
        if (scratchpad != null) {
            scratchpad.penUp();  // make sure pen is up
            scratchpad.hide();
        }
    }
}
```

## Bug 4: User does palm-2s while no scratchpad templates loaded

App tries to recognize, gets unknown, panel shows "?". Cosmetic issue
only, no crash. But surface annoying for the demo.

Fix in `ScratchpadController.penUp`:

```java
public void penUp() {
    recorder.penUp();
    Stroke s = recorder.getCurrent();
    if (s.size() >= 5 && hasTemplates()) {
        RecognitionResult r = recognizer.recognize(s);
        if (results != null) results.showResult(r);
    } else if (results != null) {
        // No templates - show a hint
        results.showResult(RecognitionResult.unknown());
    }
}

private boolean hasTemplates() {
    // Access the library through the recognizer... we don't have
    // direct access. Add a method to DollarOneRecognizer:
    return ((DollarOneRecognizer) recognizer).hasTemplates();
}
```

In `DollarOneRecognizer`:

```java
public boolean hasTemplates() {
    return !library.getAll().isEmpty();
}
```

## Bug 5: Scratchpad opens but window is hidden behind others

JFrame's `setAlwaysOnTop(true)` only works if the window can receive
focus. Since we don't `setFocusable(false)`, it should pop to front.
If it doesn't on your machine:

```java
public void show() {
    SwingUtilities.invokeLater(() -> {
        if (frame != null) {
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        }
    });
}
```

## Bug 6: Tray state isn't updated when in scratchpad mode

In `MainApp.launch`, after `tray.setState(IconState.ACTIVE)`, we should
also wire scratchpad open/close to tray. Modify the mapper to call
back, or hook in MainApp:

Simplest: have ScratchpadController take a Runnable for "on show" / "on
hide":

```java
// In ScratchpadController:
private Runnable onShow = () -> {};
private Runnable onHide = () -> {};
public void onShow(Runnable r) { this.onShow = r; }
public void onHide(Runnable r) { this.onHide = r; }

public void show() {
    SwingUtilities.invokeLater(() -> {
        if (frame != null) frame.setVisible(true);
    });
    onShow.run();
}

public void hide() {
    SwingUtilities.invokeLater(() -> {
        if (frame != null) frame.setVisible(false);
    });
    onHide.run();
}
```

In MainApp:

```java
scratchpad.onShow(() -> tray.notify("Stark Mouse", "Scratchpad open"));
scratchpad.onHide(() -> tray.notify("Stark Mouse", "Scratchpad closed"));
```

## Bug 7: Pen-down fires accidentally during mouse mode

The user is using the mouse normally and accidentally pauses (fist
held). They don't realize. Cursor stops moving. They're confused.

Solution: ALWAYS show a tray notification when state changes. Already
covered by the hotkey notification; do the same for PEN_DOWN/PEN_UP in
mouse mode.

In `GestureMapper.applyMouseMode`:

```java
private void applyMouseMode(Gesture g) {
    switch (g.getType()) {
        case POINT       -> mouse.moveCursor(g.getX(), g.getY());
        case LEFT_CLICK  -> mouse.leftClick();
        case RIGHT_CLICK -> mouse.rightClick();
        case PEN_DOWN    -> {
            enabled = false;
            if (onPause != null) onPause.run();
        }
        case PEN_UP      -> {
            enabled = true;
            if (onResume != null) onResume.run();
        }
        default          -> { }
    }
}

private Runnable onPause = () -> {};
private Runnable onResume = () -> {};
public void onPause(Runnable r) { this.onPause = r; }
public void onResume(Runnable r) { this.onResume = r; }
```

MainApp:

```java
mapper.onPause(() -> {
    tray.setState(IconState.PAUSED);
    tray.notify("Stark Mouse", "Paused (gesture)");
});
mapper.onResume(() -> {
    tray.setState(IconState.ACTIVE);
    tray.notify("Stark Mouse", "Resumed (gesture)");
});
```

## Tracking what fires when - cheat sheet

| User does | Detector emits | Mapper does (MOUSE mode) | Mapper does (SCRATCHPAD mode) |
|-----------|---------------|--------------------------|-------------------------------|
| 1 finger | POINT | move cursor | track cursor in scratchpad |
| 2 fingers | LEFT_CLICK | left click | (ignored) |
| 3 fingers | RIGHT_CLICK | right click | (ignored) |
| Fist held 1s | PEN_DOWN | pause | start recording stroke |
| Palm held 1s | PEN_UP | resume | finish stroke, recognize |
| Palm held 2s | SCRATCHPAD_TOGGLE | open scratchpad | close scratchpad |

Note the palm-1s and palm-2s overlap. The detector fires PEN_UP at 1s,
then if still held to 2s, also fires SCRATCHPAD_TOGGLE. In scratchpad
mode: pen lifts at 1s (recognition runs), scratchpad closes at 2s
(window hides). Acceptable behavior for the user.

In mouse mode: palm-1s emits PEN_UP which resumes, palm-2s emits
SCRATCHPAD_TOGGLE which opens scratchpad. Sensible.

## Test all transitions

| From | Action | Expected end state |
|------|--------|------------------|
| Idle mouse | 1 finger | cursor moves |
| Idle mouse | fist 1s | tray gray, mouse paused |
| Paused mouse | palm 1s | tray orange, mouse active |
| Mouse | palm 2s | scratchpad opens |
| Scratchpad | palm 2s | scratchpad closes, mouse |
| Scratchpad open | fist 1s | drawing starts |
| Scratchpad drawing | palm 1s | recognition fires, panel updates |
| Scratchpad drawing | palm 2s | recognition fires, then scratchpad closes |

Run through all 8. Commit fixes as you find them.

Commit: `mode-switch edge cases`. Move to chapter 18.
