# Chapter 12 - first end-to-end test + detector switching

**Audience**: both. **Time**: 30 min.
**You will modify**: `HotkeyController` and `MainApp` to add F1/F2.

Chapter 11 gave you the working mouse. Now test it hard, fix the
flakiness, and add live detector switching (your polymorphism demo).

---

## Add F1/F2 detector switching

You want F1 to select the color blob detector and F2 the hand contour
detector, switched live.

### In HotkeyController

You already handle Ctrl+Shift+G. Extend `nativeKeyPressed` to also check
for `NativeKeyEvent.VC_F1` and `VC_F2`. Since the hotkey controller
shouldn't know about detectors, add two more `Runnable` callback fields
(`onDetector1`, `onDetector2`) with setters, and run the matching one
when F1/F2 is pressed. Same callback pattern you already used for
`onToggle`.

### In MainApp.launch

After installing the hotkey, set those two callbacks:
- F1 callback: set `detectorIndex = 0`, then `tray.notify(...)` with the
  new detector's `getName()`.
- F2 callback: set `detectorIndex = 1`, then notify.

Because `loop()` calls `detector()` (which reads `detectorIndex`) every
frame, the switch takes effect on the next frame. That "the loop holds a
`GestureDetector` and doesn't know which concrete class it is" is your
polymorphism talking point.

---

## Test matrix

Run the app and verify each:

| Test | Expected | If broken |
|------|----------|-----------|
| Tray icon appears | cyan dot near clock | Windows hides new icons - drag out of overflow |
| Hold orange marker | cursor follows | check HSV bounds, lighting |
| Wave fast | smooth follow, slight lag ok | smoothing too high -> lower to 0.5 |
| Hold still | no twitch | smoothing too low -> raise to 0.7 |
| Ctrl+Shift+G | icon gray, no control | check hotkey registration |
| F2 | switch to hand contour + notify | F2 handler missing |
| 1 / 2 / 3 fingers | cursor / left / right click | check finger counting + debounce |
| Right-click tray, Exit | clean quit | check shutdown order |

---

## Likely fixes you'll make

- Cursor too laggy -> lower SMOOTHING (0.4).
- Cursor jittery -> raise SMOOTHING (0.75).
- Hand detector sees the wall -> darker background, or narrow YCrCb.
- Clicks spam -> confirm debounce is actually called.
- Two tray icons -> a previous run crashed without `tray.remove()`; kill
  stale java processes in Task Manager.

---

## Quick FPS check (optional, temporary)

Add a frame counter to `loop()`: increment each iteration, and every
1000ms print the count and reset. Expect 25-60. Under 15 means trouble
(camera resolution too high - set it lower in CameraInput with
`capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 320)`; or a Mat is being
allocated per frame somewhere).

Remove the counter once you're satisfied.

---

## Lock the MVP

Commit, push, and tag - this is your insurance if the scratchpad later
goes sideways:

```
git tag mvp-mouse
git push origin mvp-mouse
```

Move to chapter 13 for the scratchpad.
