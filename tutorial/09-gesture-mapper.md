# Chapter 9 - GestureMapper + AppMode

**Audience**: Person B. **Time**: 45 min.
**You will write**: `AppMode.java`, `GestureMapper.java`, and a stub
`ScratchpadController.java`.

The mapper turns Gestures into actions, and decides whether a gesture
controls the mouse or the scratchpad based on the current mode.

## Goal

- `AppMode` enum: `MOUSE`, `SCRATCHPAD`.
- `GestureMapper` with `apply(Gesture)`, mode dispatch, an enabled flag.
- A do-nothing `ScratchpadController` stub so you can build/test now
  (you'll flesh it out in chapter 16).

---

## Concept: state machine

Same physical gesture means different things depending on app state. A
fist in MOUSE mode pauses; a fist in SCRATCHPAD mode starts drawing.
Rather than scattering `if (scratchpadOpen)` everywhere, keep one
`AppMode mode` field and dispatch on it in one place.

---

## Concept: Java 17 switch expression

Cleaner than the old `case X: ...; break;`. Arrow form:

```java
switch (value) {
    case A -> doA();
    case B -> doB();
    default -> { }
}
```

No fall-through, no break needed.

---

## Concept: callback via Runnable

To avoid coupling, the mapper won't reach into the tray or scratchpad
for everything. Where it needs to notify something external, it can
hold a `Runnable` set by MainApp later. (You'll use this more in ch11.)

---

## Build AppMode.java

`src/main/java/com/starkmouse/control/AppMode.java`. A plain enum with
two constants `MOUSE` and `SCRATCHPAD`, each with a one-line Javadoc.

---

## Build the ScratchpadController stub

`src/main/java/com/starkmouse/scratchpad/ScratchpadController.java`.

For now, just methods that print so you can verify wiring. Declare:
`show()`, `hide()`, `boolean isShowing()`, `trackCursor(int x, int y)`,
`penDown()`, `penUp()`. Make them print a line (e.g. "[stub] show") or
no-op. You'll replace this whole class in chapter 16.

(Stubbing lets you build and test the mapper today instead of waiting
for the real scratchpad.)

---

## Build GestureMapper.java

`src/main/java/com/starkmouse/control/GestureMapper.java`.

### Fields

- `private final MouseController mouse`
- `private final ScratchpadController scratchpad`
- `private AppMode mode = AppMode.MOUSE`
- `private boolean enabled = true`

### Constructor

Takes a `MouseController` and a `ScratchpadController`, stores both.

### apply(Gesture g) - the dispatcher

1. If `g.getType() == NONE`, return.
2. If `g.getType() == SCRATCHPAD_TOGGLE`, call a private `toggleMode()`
   and return (toggle works even when paused).
3. If `!enabled`, return.
4. `switch (mode)`: MOUSE -> `applyMouseMode(g)`, SCRATCHPAD ->
   `applyScratchpadMode(g)`.

### private toggleMode()

If currently MOUSE: set mode SCRATCHPAD, call `scratchpad.show()`.
Else: set mode MOUSE, call `scratchpad.hide()`. (Guard against
scratchpad being null if you like.)

### private applyMouseMode(Gesture g)

switch on type:
- POINT -> `mouse.moveCursor(g.getX(), g.getY())`
- LEFT_CLICK -> `mouse.leftClick()`
- RIGHT_CLICK -> `mouse.rightClick()`
- PEN_DOWN -> `enabled = false` (fist held = pause)
- PEN_UP -> `enabled = true` (palm held = resume)
- default -> nothing

### private applyScratchpadMode(Gesture g)

switch on type:
- POINT -> `scratchpad.trackCursor(g.getX(), g.getY())`
- PEN_DOWN -> `scratchpad.penDown()`
- PEN_UP -> `scratchpad.penUp()`
- default -> nothing

### Accessors

`setEnabled(boolean)`, `isEnabled()`, `getMode()`.

### Imports

| Import | For |
|--------|-----|
| `com.starkmouse.detection.Gesture` | the input type |
| `com.starkmouse.scratchpad.ScratchpadController` | the stub |

---

## Test

```java
MouseController mouse = new MouseController();
mouse.setFrameSize(640, 480);
ScratchpadController stub = new ScratchpadController();
GestureMapper mapper = new GestureMapper(mouse, stub);

mapper.apply(new Gesture(Gesture.Type.POINT, 320, 240, 1.0)); // cursor moves
mapper.apply(new Gesture(Gesture.Type.SCRATCHPAD_TOGGLE, 0,0,1.0)); // stub: show
mapper.apply(new Gesture(Gesture.Type.PEN_DOWN, 0,0,1.0));    // stub: pen down
```

Verify the cursor jumps and the stub prints show/pen-down.

## Checklist

- [ ] AppMode enum exists
- [ ] Mapper dispatches by mode in ONE place
- [ ] SCRATCHPAD_TOGGLE works even when disabled
- [ ] Stub lets the project compile and run
- [ ] Javadoc everywhere

Commit: `add AppMode, GestureMapper, ScratchpadController stub`.
Move to chapter 10.
