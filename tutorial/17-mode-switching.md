# Chapter 17 - Mode-switching edge cases

**Audience**: both. **Time**: 1 hour.
**You will modify**: `GestureMapper`, `ScratchpadController`, `MainApp`,
`DollarOneRecognizer`.

The happy path works. Now harden the edges. Each item below is a bug to
find and fix yourself - the description tells you what's wrong and which
method to touch, you write the fix.

---

## Edge 1: closing the scratchpad mid-stroke

If the user has pen down (recording) and then fires SCRATCHPAD_TOGGLE to
close, the recorder is left thinking pen is still down. Next time the
scratchpad opens, it's in a weird state.

Fix in `GestureMapper.toggleMode()`: when switching away from SCRATCHPAD,
call the scratchpad's `penUp()` before `hide()`.

---

## Edge 2: recognizing with no templates loaded

If `templates.json` doesn't exist yet, the library is empty, recognition
returns unknown, panel shows "?". Not a crash, just confusing.

Add a `boolean hasTemplates()` to `DollarOneRecognizer` (returns whether
the library has any). In `ScratchpadController.penUp`, only run
recognition if there are templates and the stroke is long enough;
otherwise show `RecognitionResult.unknown()`. Decide the cleanest way to
let the controller ask - simplest is a method on the recognizer.

---

## Edge 3: scratchpad opens behind other windows

`setAlwaysOnTop(true)` usually wins, but on some setups the window opens
behind the active app. In `ScratchpadController.show()`, after
`setVisible(true)`, also call `frame.toFront()` and
`frame.requestFocus()`.

---

## Edge 4: accidental pause in mouse mode

A user moving the mouse might hold a fist by accident, triggering pause,
and not understand why the cursor froze. Make state changes visible:
whenever the mapper pauses/resumes (PEN_DOWN/PEN_UP in mouse mode), fire
a tray notification + icon color change.

The mapper shouldn't import the tray. Use the callback pattern again:
add `onPause` / `onResume` Runnable fields to `GestureMapper` with
setters; call them in `applyMouseMode`. Wire them in `MainApp.launch` to
update the tray.

---

## Edge 5: tray doesn't reflect scratchpad open/close

When the scratchpad opens/closes, the tray should arguably notify too.
Add `onShow`/`onHide` Runnable callbacks to `ScratchpadController`, run
them in `show()`/`hide()`, and wire them in MainApp to a tray
notification.

(Optional polish - skip if short on time.)

---

## The full gesture table (verify your behavior matches)

| User does | Detector emits | MOUSE mode result | SCRATCHPAD mode result |
|-----------|---------------|-------------------|------------------------|
| 1 finger | POINT | move cursor | track/draw |
| 2 fingers | LEFT_CLICK | left click | (ignored) |
| 3 fingers | RIGHT_CLICK | right click | (ignored) |
| Fist held 1s | PEN_DOWN | pause | start recording |
| Palm held 1s | PEN_UP | resume | finish + recognize |
| Palm held 2s | SCRATCHPAD_TOGGLE | open scratchpad | close scratchpad |

Note palm-1s and palm-2s overlap: in scratchpad mode the pen lifts at 1s
(recognition runs) and the window closes at 2s. Acceptable.

---

## Test all transitions

Run through every cell of this table and confirm the result. Eight or so
distinct transitions. Fix anything that misbehaves; commit each fix
separately so your git history shows the debugging work (good for the
individual-contribution rubric).

Commit: `mode-switch edge cases`. Move to chapter 18.
