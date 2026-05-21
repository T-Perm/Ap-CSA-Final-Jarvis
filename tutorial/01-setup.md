# Chapter 1 - toolchain setup

**Audience**: both. **Time**: 15-30 min one time.

You need: Java 17+, Maven 3.8+, Git, an IDE. Maven is non-negotiable;
the IDE is your call.

## Install

**Java 17**: https://adoptium.net -> Temurin 17 Windows x64 .msi.
During install check "Set JAVA_HOME" and "Add to PATH". Verify:
`java -version` in a fresh PowerShell.

**Maven**: https://maven.apache.org/download.cgi -> binary zip. Extract
to `C:\Program Files\Apache\maven`. Add `C:\Program Files\Apache\maven\bin`
to your PATH (Windows env vars settings). Verify: `mvn -version`.

**Git**: https://git-scm.com/download/win, defaults. Then:

    git config --global user.name "Your Name"
    git config --global user.email "you@example.com"

**IDE** (pick one): VS Code + "Extension Pack for Java" (Microsoft) /
IntelliJ Community / Eclipse for Java Developers. All read `pom.xml`
the same way.

## One Windows-specific gotcha

OpenCV's native DLLs need the Microsoft Visual C++ Redistributable
(2015-2022, x64). Download from Microsoft and install now if you've
never installed it. Saves you a confusing `UnsatisfiedLinkError` later.

## Verify

In any temp folder:

```java
// Hello.java
public class Hello {
    public static void main(String[] args) {
        System.out.println("ok " + System.getProperty("java.version"));
    }
}
```

`javac Hello.java && java Hello` prints `ok 17.x.x` -> you're done.

Post in team chat when done, move to chapter 2.
