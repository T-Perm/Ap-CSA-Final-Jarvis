# Chapter 12 - first end-to-end test + detector switching

**Audience**: both. **Time**: 30 min testing.

Chapter 11 gives you the mouse. This chapter is about testing it, fixing
what's flaky, and adding F1/F2 detector switching (the polymorphism
showcase for the rubric).

## Detector switching

In `HotkeyController.nativeKeyPressed`, also handle F1/F2:

```java
@Override
public void nativeKeyPressed(NativeKeyEvent e) {
    boolean ctrl = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
    boolean shift = (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0;
    if (ctrl && shift && e.getKeyCode() == NativeKeyEvent.VC_G) {
        onToggle.run();
    } else if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
        if (onDetector1 != null) onDetector1.run();
    } else if (e.getKeyCode() == NativeKeyEvent.VC_F2) {
        if (onDetector2 != null) onDetector2.run();
    }
}

private Runnable onDetector1;
private Runnable onDetector2;
public void setOnDetector1(Runnable r) { this.onDetector1 = r; }
public void setOnDetector2(Runnable r) { this.onDetector2 = r; }
```

In `MainApp.launch()`, after hotkey.install():

```java
hotkey.setOnDetector1(() -> {
    detectorIndex = 0;
    tray.notify("Stark Mouse", "Detector: " + detector().getName());
});
hotkey.setOnDetector2(() -> {
    detectorIndex = 1;
    tray.notify("Stark Mouse", "Detector: " + detector().getName());
});
```

Now F1 switches to ColorBlob, F2 switches to HandContour. The fact that
the `detector()` method returns a `GestureDetector` (interface) and
`MainApp.loop()` doesn't know which concrete class it gets is your
polymorphism story for the rubric.

## Things to test

| Test | Expected | If broken |
|------|----------|-----------|
| Tray icon shows up | cyan dot near clock | Check `SystemTray.isSupported()`, Windows hidden icons |
| Hold orange marker | cursor follows hand position | Check HSV bounds, lighting |
| Wave hand fast | cursor smoothly follows (slight lag is fine) | Smoothing too high, lower to 0.5 |
| Hold marker still | cursor doesn't twitch | Smoothing too low, raise to 0.7 |
| Cover marker | cursor stops where it was | Should just work |
| Press Ctrl+Shift+G | icon -> gray, no cursor control | Check hotkey registration |
| Press F2 | switch to hand contour, see notify | Add the F2 handler |
| Hold 1 finger | cursor follows (HandContour) | Check skin tone bounds |
| Hold 2 fingers | left click fires | Check finger counting / debounce |
| Right-click tray | menu with Exit | |
| Click Exit | app exits cleanly | Check shutdown sequence |

## Things you'll probably hit

**Cursor lag too much**: lower SMOOTHING to 0.4.
**Cursor still jittery**: raise SMOOTHING to 0.75.
**Hand detection sees the wall**: change background (drape a dark cloth)
or narrow YCrCb bounds.
**Clicks fire constantly**: check `debounceOk()` is being called.
**Multiple icons in tray**: app crashed earlier without `tray.remove()`.
Kill stale java processes via Task Manager.

## Performance check

Add FPS logging to the loop temporarily:

```java
long lastReport = System.currentTimeMillis();
int frames = 0;

private void loop() {
    while (running) {
        // ... existing code ...
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastReport >= 1000) {
            System.out.println("FPS: " + frames);
            frames = 0;
            lastReport = now;
        }
    }
}
```

Should see 25-60 FPS. If under 15, something's wrong:

- Camera resolution too high (set lower with `capture.set(CAP_PROP_FRAME_WIDTH, 320)`)
- Detection algorithm too slow (profile with a simpler detector first)
- Mat allocation in detection (verify you're reusing buffers)

## Lock in the MVP

At this point: commit, push, tag the commit:

```
git tag mvp-mouse
git push origin mvp-mouse
```

This is your insurance. If the scratchpad falls through, you can
revert to this tag and ship.

Move to chapter 13 for the scratchpad.
