# Chapter 10 - System tray + global hotkey

**Audience**: Person B. **Time**: 1-1.5 hours.

This chapter gives the app its Whisper Flow style presence: a tray
icon that's the only persistent UI, and a global hotkey
(Ctrl+Shift+G) that works even when the app isn't focused.

## What you need to know

**SystemTray (java.awt)**: tray icons are JDK-built-in. Limitations:
not supported on every OS (always check `SystemTray.isSupported()`).
Windows: yes. macOS: yes. Linux: sometimes.

**TrayIcon image**: any AWT `Image`. Easiest path: draw your own
`BufferedImage` in code so you don't ship a PNG asset.

**PopupMenu**: shown on right-click. Java's old `java.awt.PopupMenu`
(not Swing's JPopupMenu) is what SystemTray accepts.

**JNativeHook**: third-party library that hooks into OS keyboard
events at a low level. `GlobalScreen.registerNativeHook()` starts it;
listeners receive every key press globally.

JNativeHook gotcha: it logs every keystroke to Java's logger at INFO
level by default. **Silence it** or your console floods.

## TrayController.java

`src/main/java/com/starkmouse/control/TrayController.java`:

```java
package com.starkmouse.control;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Owns the tray icon. Icon color reflects app state:
 *   cyan = idle, orange = gesture detected, gray = paused.
 */
public class TrayController {

    /** Possible icon states. */
    public enum IconState { IDLE, ACTIVE, PAUSED }

    private TrayIcon trayIcon;
    private Runnable onExit = () -> System.exit(0);

    /**
     * Try to install the tray icon. Returns true on success.
     */
    public boolean install() {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray not supported.");
            return false;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> onExit.run());
        menu.add(exitItem);

        trayIcon = new TrayIcon(buildIcon(IconState.IDLE),
                                "Stark Mouse", menu);
        trayIcon.setImageAutoSize(true);

        try {
            SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (AWTException e) {
            System.err.println("Could not install tray icon: " + e.getMessage());
            return false;
        }
    }

    /** Called by MainApp on shutdown. */
    public void onExit(Runnable r) { this.onExit = r; }

    /** Update icon color based on state. */
    public void setState(IconState state) {
        if (trayIcon == null) return;
        trayIcon.setImage(buildIcon(state));
    }

    /** Show a Windows balloon notification. */
    public void notify(String title, String message) {
        if (trayIcon == null) return;
        trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    /** Build a 16x16 colored circle icon. */
    private Image buildIcon(IconState state) {
        Color color = switch (state) {
            case IDLE -> new Color(95, 212, 255);   // cyan
            case ACTIVE -> new Color(255, 140, 50); // orange
            case PAUSED -> new Color(120, 120, 120); // gray
        };
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillOval(2, 2, 12, 12);
        g.dispose();
        return img;
    }

    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }
}
```

## Tray imports

| Import | What |
|--------|------|
| `java.awt.AWTException` | thrown by `SystemTray.add` |
| `java.awt.Color` | icon color |
| `java.awt.Graphics2D` | draw the icon |
| `java.awt.Image` | what TrayIcon takes |
| `java.awt.MenuItem` | "Exit" |
| `java.awt.PopupMenu` | right-click menu |
| `java.awt.SystemTray` | the singleton |
| `java.awt.TrayIcon` | our icon |
| `java.awt.image.BufferedImage` | image we draw onto |

## HotkeyController.java

`src/main/java/com/starkmouse/control/HotkeyController.java`:

```java
package com.starkmouse.control;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a global hotkey via JNativeHook. Currently:
 *   Ctrl+Shift+G - toggle gesture-to-mouse mapping.
 */
public class HotkeyController implements NativeKeyListener {

    /** Called when the hotkey fires. */
    private final Runnable onToggle;

    /**
     * @param onToggle action to run on Ctrl+Shift+G
     */
    public HotkeyController(Runnable onToggle) {
        this.onToggle = onToggle;
    }

    /** Register the hook and start listening. */
    public boolean install() {
        // silence JNativeHook's noisy default logging
        Logger l = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        l.setLevel(Level.OFF);
        l.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            return true;
        } catch (NativeHookException e) {
            System.err.println("Could not register global hotkey: " + e.getMessage());
            return false;
        }
    }

    public void remove() {
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ignored) { }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        boolean ctrl = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
        boolean shift = (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0;
        if (ctrl && shift && e.getKeyCode() == NativeKeyEvent.VC_G) {
            onToggle.run();
        }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) { }
    @Override public void nativeKeyTyped(NativeKeyEvent e) { }
}
```

## Hotkey imports

| Import | What |
|--------|------|
| `com.github.kwhat.jnativehook.GlobalScreen` | the JNativeHook entry point |
| `com.github.kwhat.jnativehook.NativeHookException` | thrown by register |
| `com.github.kwhat.jnativehook.keyboard.NativeKeyEvent` | event passed to listeners |
| `com.github.kwhat.jnativehook.keyboard.NativeKeyListener` | interface to implement |
| `java.util.logging.Level`, `Logger` | silence JNativeHook |

## JNativeHook key codes

`NativeKeyEvent.VC_*` constants - `VC_G`, `VC_F1`, `VC_A`, etc. Not
to be confused with `java.awt.event.KeyEvent.VK_*` - different naming.

Modifiers: `NativeKeyEvent.CTRL_MASK`, `SHIFT_MASK`, `ALT_MASK`,
`META_MASK` (Windows key / Cmd).

`nativeKeyTyped` is for typed characters (with key repeats). We usually
don't want that for hotkeys - use `nativeKeyPressed` for press events.

## Why "callback" via Runnable

We could have HotkeyController directly call `mapper.setEnabled(...)`,
but that couples controllers. Instead the constructor takes a
`Runnable` callback - `MainApp` will wire it later:

```java
HotkeyController hk = new HotkeyController(() -> {
    mapper.setEnabled(!mapper.isEnabled());
    tray.setState(mapper.isEnabled() ? IconState.ACTIVE : IconState.PAUSED);
    tray.notify("Stark Mouse", mapper.isEnabled() ? "Active" : "Paused");
});
hk.install();
```

This pattern (constructor takes a Runnable / lambda for the callback)
keeps each controller small and independent.

## Test

```java
public static void main(String[] args) throws Exception {
    TrayController tray = new TrayController();
    tray.install();
    tray.setState(IconState.ACTIVE);
    tray.notify("Hello", "Hello from Stark Mouse");

    HotkeyController hk = new HotkeyController(() -> {
        System.out.println("HOTKEY FIRED");
        tray.notify("Hotkey", "Ctrl+Shift+G pressed");
    });
    hk.install();

    Thread.sleep(30_000);   // keep alive 30s to test the hotkey

    hk.remove();
    tray.remove();
}
```

Run for 30s. Right-click the tray icon (cyan dot near the system clock).
You should see "Exit". Press Ctrl+Shift+G anywhere (even in another
app) - your console prints "HOTKEY FIRED" and a notification appears.

## Common errors

- **"NoClassDefFoundError: com/github/kwhat/jnativehook/..."**: ran
  the non-fat jar. Use `-jar target/...-jar-with-dependencies.jar`.
- **No tray icon appears on Windows**: Windows hides "new" tray icons
  by default. Click the up-arrow next to the tray, drag the cyan dot
  out so it stays visible. Or: Settings > Personalization > Taskbar >
  "Select which icons appear" > toggle Stark Mouse on.
- **Hotkey doesn't fire**: another app is grabbing Ctrl+Shift+G. Try
  a different combo (`VC_J` etc.).

Commit: `add TrayController, HotkeyController`. Move to chapter 11.
