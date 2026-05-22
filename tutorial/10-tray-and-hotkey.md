# Chapter 10 - Tray icon + global hotkey

**Audience**: Person B. **Time**: 1-1.5 hours.
**You will write**: `TrayController.java` and `HotkeyController.java`.

The tray icon is the app's only persistent UI. The global hotkey lets
Ctrl+Shift+G work even when another app is focused.

Read `api-reference-3` (SystemTray section) and `api-reference-4`
(JNativeHook section) first.

---

## Part A: TrayController

### Methods recap

- `SystemTray.isSupported()` -> boolean (check first)
- `new TrayIcon(Image, String tooltip, PopupMenu)`
- `SystemTray.getSystemTray().add(trayIcon)` throws AWTException
- `trayIcon.setImage(Image)` - swap icon at runtime
- `trayIcon.displayMessage(caption, text, TrayIcon.MessageType.INFO)` -
  balloon notification
- Build the icon image yourself with a `BufferedImage` + `Graphics2D`
  (see api-reference-3 "Making an image in code")

### Concept: lambda action listeners

`menuItem.addActionListener(e -> doSomething())` runs the lambda when
the item is clicked. The `e -> ...` is an `ActionListener`.

### Concept: callback for Exit

The tray shouldn't hard-code what "Exit" does. Store a `Runnable onExit`
(default `() -> System.exit(0)`) that MainApp can override to run a
clean shutdown.

### Build TrayController.java

`src/main/java/com/starkmouse/control/TrayController.java`.

Declare a nested `enum IconState { IDLE, ACTIVE, PAUSED }`.

Fields: a `TrayIcon trayIcon`, a `Runnable onExit`.

Methods:

- `boolean install()`:
  1. If `!SystemTray.isSupported()` return false.
  2. Build a `PopupMenu` with one `MenuItem("Exit")` whose listener
     runs `onExit`.
  3. Create the TrayIcon with `buildIcon(IconState.IDLE)`, the tooltip,
     and the menu. `setImageAutoSize(true)`.
  4. `SystemTray.getSystemTray().add(...)` in a try/catch (AWTException).
     Return true on success.
- `void onExit(Runnable r)` - setter for the exit callback.
- `void setState(IconState s)` - if trayIcon non-null, `setImage(buildIcon(s))`.
- `void notify(String title, String msg)` - displayMessage with INFO type.
- `void remove()` - remove the icon from the tray.
- `private Image buildIcon(IconState s)`:
  - Pick a `Color` per state (cyan idle, orange active, gray paused).
  - Make a 16x16 `BufferedImage` (TYPE_INT_ARGB), draw a filled oval in
    that color, return it.

Imports: `java.awt.*` covers most (AWTException, Color, Graphics2D,
Image, MenuItem, PopupMenu, SystemTray, TrayIcon) plus
`java.awt.image.BufferedImage`.

### Test

Install the tray, setState(ACTIVE), notify("Hi","there"), sleep 20s so
you can right-click the icon and see Exit. (On Windows, new tray icons
hide under the up-arrow; drag it out to keep it visible.)

---

## Part B: HotkeyController

### Methods recap

- `GlobalScreen.registerNativeHook()` throws `NativeHookException`
- `GlobalScreen.addNativeKeyListener(listener)`
- `GlobalScreen.unregisterNativeHook()`
- Implement `NativeKeyListener` (three methods; only `nativeKeyPressed`
  matters)
- In the event: `e.getKeyCode()` vs `NativeKeyEvent.VC_*`,
  `e.getModifiers() & NativeKeyEvent.CTRL_MASK`
- Silence the logger before registering (see api-reference-4)

### Concept: callbacks again

The hotkey controller shouldn't know about the mapper. Pass it a
`Runnable onToggle` in the constructor; MainApp wires it to "flip the
mapper's enabled state and update the tray."

### Build HotkeyController.java

`src/main/java/com/starkmouse/control/HotkeyController.java`,
`implements NativeKeyListener`.

Fields: `private final Runnable onToggle`.

Constructor: takes and stores the Runnable.

Methods:

- `boolean install()`:
  1. Silence JNativeHook's logger (two lines from api-reference-4).
  2. `registerNativeHook()` + `addNativeKeyListener(this)` in try/catch
     (NativeHookException). Return true/false.
- `void remove()`: `unregisterNativeHook()` in a try/catch.
- `nativeKeyPressed(NativeKeyEvent e)`: compute ctrl and shift booleans
  from modifiers; if ctrl && shift && keyCode == VC_G, call
  `onToggle.run()`.
- `nativeKeyReleased` and `nativeKeyTyped`: empty.

Imports:

| Import | For |
|--------|-----|
| `com.github.kwhat.jnativehook.GlobalScreen` | register/listeners |
| `com.github.kwhat.jnativehook.NativeHookException` | thrown |
| `com.github.kwhat.jnativehook.keyboard.NativeKeyEvent` | event + VC_/MASK constants |
| `com.github.kwhat.jnativehook.keyboard.NativeKeyListener` | interface |
| `java.util.logging.Level`, `Logger` | silence logging |

### Test

```java
HotkeyController hk = new HotkeyController(() -> System.out.println("FIRED"));
hk.install();
Thread.sleep(30000);   // press Ctrl+Shift+G in any app during this time
hk.remove();
```

Switch to a browser, press the combo, confirm "FIRED" prints. That's
the global part working.

---

## Common errors

- `NoClassDefFoundError` -> ran the non-fat jar; use
  `...-jar-with-dependencies.jar`.
- No tray icon -> Windows hides new icons; drag it out of the overflow.
- Hotkey never fires -> another app owns Ctrl+Shift+G, or you used
  `VK_G` instead of `VC_G`.
- Console flooded with key logs -> you didn't silence the logger before
  registering.
- App won't exit -> you forgot `unregisterNativeHook()`.

## Checklist

- [ ] TrayController.install returns false gracefully if unsupported
- [ ] Icon color changes with setState
- [ ] Notifications appear
- [ ] Hotkey fires globally
- [ ] Both controllers take callbacks (no coupling to mapper)
- [ ] Javadoc everywhere

Commit: `add TrayController and HotkeyController`. Move to chapter 11.
