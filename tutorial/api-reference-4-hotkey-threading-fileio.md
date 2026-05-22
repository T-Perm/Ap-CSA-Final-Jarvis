# API reference 4 - JNativeHook, threading, file I/O

Textbook reference for the remaining non-CSA pieces: global hotkeys,
background threads, and the modern file API.

---

# Part A: JNativeHook (global hotkey)

Java's built-in `KeyListener` only fires when your window has focus. For
a hotkey that works app-wide (even when another program is focused) you
need JNativeHook, which hooks into the OS keyboard at a low level.

## GlobalScreen.registerNativeHook

**What it does**: starts the OS-level keyboard hook. Must be called
before adding listeners.

**Signature**: `static void registerNativeHook() throws NativeHookException`

**Example**:

```java
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;

try {
    GlobalScreen.registerNativeHook();
} catch (NativeHookException e) {
    System.err.println("hook failed: " + e.getMessage());
}
```

**Try it**: register the hook, print "hook on", sleep 5 seconds,
unregister (below), print "hook off". Confirms the library loads.

---

## Silencing JNativeHook's logger

**The problem**: JNativeHook logs every keystroke at INFO level by
default, flooding your console.

**The fix**:

```java
import java.util.logging.Level;
import java.util.logging.Logger;

Logger l = Logger.getLogger(GlobalScreen.class.getPackage().getName());
l.setLevel(Level.OFF);
l.setUseParentHandlers(false);
```

Do this *before* `registerNativeHook()`.

**Try it**: register the hook without silencing first, type a few keys,
watch the console flood. Then add the silencing, re-run, confirm
silence. Now you know why the two lines exist.

---

## NativeKeyListener

**What it is**: the interface you implement to receive key events. Three
methods: pressed, released, typed.

**Signatures**:
```java
void nativeKeyPressed(NativeKeyEvent e)
void nativeKeyReleased(NativeKeyEvent e)
void nativeKeyTyped(NativeKeyEvent e)
```

You usually only care about `nativeKeyPressed`. Implement the other two
as empty.

**Reading the event**:
- `e.getKeyCode()` -> a `NativeKeyEvent.VC_*` code (e.g. `VC_G`, `VC_F1`)
- `e.getModifiers()` -> a bitmask; test with `& NativeKeyEvent.CTRL_MASK`

**Example - detect Ctrl+Shift+G**:

```java
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

class MyHotkeys implements NativeKeyListener {
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        boolean ctrl = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
        boolean shift = (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0;
        if (ctrl && shift && e.getKeyCode() == NativeKeyEvent.VC_G) {
            System.out.println("Ctrl+Shift+G pressed!");
        }
    }
    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}
}
```

**Register it**:
```java
GlobalScreen.addNativeKeyListener(new MyHotkeys());
```

**Note on VC vs VK**: JNativeHook uses `VC_*` codes
(`NativeKeyEvent.VC_G`). Don't confuse with Swing's `KeyEvent.VK_*`.
Different constants.

**Try it**: implement a listener that prints the name of any function
key pressed (F1-F12). Test by pressing them. Then narrow it to only
print on Ctrl+Shift+G. Run it, switch to another app (a browser),
press the combo, confirm it still fires - that's the "global" part.

---

## Unregistering

**What it does**: removes the hook. Important - if you don't, the
background hook thread can keep your process alive after you think it
exited.

**Signature**: `static void unregisterNativeHook() throws NativeHookException`

**Example**:

```java
try {
    GlobalScreen.unregisterNativeHook();
} catch (NativeHookException ignored) { }
```

**Try it**: in your hotkey test, unregister at the end and confirm the
program actually exits (vs hanging). Comment out the unregister and see
if it hangs - now you know why it's needed.

---

# Part B: Threading

Your capture loop must run on a background thread so it doesn't freeze
the UI. CSA may not have covered threads, so here's what you need.

## Creating and starting a Thread

**What it does**: runs a piece of code concurrently with the rest of
your program.

**Pattern**:

```java
Thread t = new Thread(() -> {
    // code that runs in the background
    while (running) {
        doWork();
    }
}, "my-thread-name");
t.setDaemon(true);
t.start();
```

- The lambda `() -> { ... }` is the code to run (it implements `Runnable`).
- `setDaemon(true)` means the thread dies when the main program exits
  (you don't have to manually stop it on shutdown).
- `start()` begins execution. (Do not call `run()` directly - that runs
  it on the current thread, defeating the purpose.)

**Try it**: start a daemon thread that prints "tick" every 500ms
forever. In main, sleep 3 seconds then let main end. Confirm "tick"
prints ~6 times then stops when main exits (because the thread is a
daemon).

---

## volatile

**What it does**: marks a field so changes made by one thread are
immediately visible to others.

**The problem it solves**: without `volatile`, the JVM may cache a
field's value in a thread, so a `while (running)` loop might never
notice when another thread sets `running = false`.

**Example**:

```java
private volatile boolean running = false;

void start() {
    running = true;
    new Thread(this::loop).start();
}

void loop() {
    while (running) {    // sees changes to `running` thanks to volatile
        doWork();
    }
}

void stop() {
    running = false;     // the loop thread notices and exits
}
```

**Try it**: write a daemon thread loop controlled by a `volatile
boolean`. From main, set it false after 2 seconds and confirm the loop
stops. Then remove `volatile` - on some JVMs/loads the loop may not
stop. (It's subtle; the point is to know the keyword exists and why.)

---

## Thread.sleep

**What it does**: pauses the current thread for N milliseconds.

**Signature**: `static void sleep(long ms) throws InterruptedException`

**The annoying part**: it throws a *checked* `InterruptedException`. You
must handle it. The idiom when you just want a delay:

```java
try { Thread.sleep(15); } catch (InterruptedException ignored) {}
```

**Why we sleep in the loop**: a tight `while (true)` with no sleep pegs
a CPU core at 100%. Sleeping ~15ms caps the loop at ~60 iterations/sec,
which is plenty for video.

**Try it**: write a `sleep(long ms)` helper that wraps the try/catch so
your loop code stays clean. Use it in your tick-printing thread.

---

## SwingUtilities.invokeLater

**What it does**: runs code on Swing's UI thread (the Event Dispatch
Thread / EDT). All Swing component creation and updates must happen
there.

**Signature**: `static void invokeLater(Runnable r)`

**Example**:

```java
import javax.swing.SwingUtilities;

public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        // build and show your window here, on the EDT
        new MyApp().launch();
    });
}
```

**The rule**: heavy work (camera, detection) on a background thread;
anything touching Swing components on the EDT via invokeLater.

**The safe exception**: calling `repaint()` is allowed from any thread.
So a background loop can update a panel's data and call `repaint()`
without invokeLater - that's why our setters are safe.

**Try it**: from a background thread, update a JLabel's text two ways:
(1) directly (wrong - may glitch), and (2) wrapped in invokeLater
(correct). For a label it often looks fine either way, but internalize
the rule: Swing updates go through invokeLater.

---

# Part C: java.nio.file (modern file I/O)

For saving/loading the template JSON. The modern API (`java.nio.file`)
is cleaner than the old `java.io.File`.

## Path and Paths

**What they are**: a `Path` represents a file location. `Paths.get(...)`
and `Path.resolve(...)` build them.

**Example**:

```java
import java.nio.file.Path;
import java.nio.file.Paths;

Path home = Paths.get(System.getProperty("user.home"));
Path file = home.resolve(".stark-mouse").resolve("templates.json");
// file is now ~/.stark-mouse/templates.json
```

`resolve` appends a path segment - cleaner than string concatenation
with separators.

**Try it**: build a Path to a file called `notes.txt` inside a folder
`myapp` in the user's home directory. Print it with `System.out.println`.

---

## Files.createDirectories

**What it does**: creates a directory and any missing parents. No error
if it already exists.

**Signature**: `static Path Files.createDirectories(Path dir) throws IOException`

**Example**:

```java
import java.nio.file.Files;

Files.createDirectories(file.getParent());   // makes ~/.stark-mouse if needed
```

`file.getParent()` returns the directory portion of a path.

**Try it**: create a folder `~/.stark-mouse-test/sub/dir` in one call.
Confirm it exists in your file explorer. Run it twice - confirm no error
the second time.

---

## Files.newBufferedWriter / newBufferedReader

**What they do**: open a text file for writing / reading.

**Signatures**:
```java
BufferedWriter Files.newBufferedWriter(Path p) throws IOException
BufferedReader Files.newBufferedReader(Path p) throws IOException
```

**Writing example (with try-with-resources)**:

```java
import java.io.BufferedWriter;
import java.nio.file.Files;

try (BufferedWriter w = Files.newBufferedWriter(file)) {
    w.write("hello");
    w.newLine();
    w.write("world");
}   // writer auto-closed here
```

The `try (...)` form is "try-with-resources" - it auto-closes the
writer even if an exception is thrown. Always use it for files.

**Reading example**:

```java
import java.io.BufferedReader;

StringBuilder sb = new StringBuilder();
try (BufferedReader r = Files.newBufferedReader(file)) {
    String line;
    while ((line = r.readLine()) != null) {
        sb.append(line);
    }
}
String contents = sb.toString();
```

`readLine()` returns one line, or null at end of file. The
`while ((line = r.readLine()) != null)` idiom reads until EOF.

**Files.exists**:
```java
if (Files.exists(file)) { /* load it */ }
```

**Try it**: write a method `saveText(Path p, String content)` and
`loadText(Path p)` using the patterns above. Save a string, load it
back, confirm they match. Then save a small JSON-shaped string like
`{"x":5}` and read it back - this is the foundation of TemplateLibrary.

---

## Capstone exercise

Build a tiny key-value store:
- `save(Map<String,Integer> data, Path file)` writes lines like `key=value`
- `load(Path file)` reads them back into a Map

Use `Files.createDirectories`, `newBufferedWriter`, `newBufferedReader`.
Test: save `{"a":1,"b":2}`, load, confirm equal. This exercises the
exact file APIs TemplateLibrary uses, minus the JSON formatting.
