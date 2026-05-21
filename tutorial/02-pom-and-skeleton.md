# Chapter 2 - Maven setup and project skeleton

**Audience**: both. **Time**: 20 min.

Maven is a build tool. `pom.xml` is the config. `mvn package` compiles
your code + bundles dependencies into a fat jar.

## pom.xml

Create at project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.starkmouse</groupId>
    <artifactId>stark-mouse</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.openpnp</groupId>
            <artifactId>opencv</artifactId>
            <version>4.9.0-0</version>
        </dependency>
        <dependency>
            <groupId>com.github.kwhat</groupId>
            <artifactId>jnativehook</artifactId>
            <version>2.2.2</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.6.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.starkmouse.app.MainApp</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>
                <executions>
                    <execution>
                        <id>make-assembly</id>
                        <phase>package</phase>
                        <goals><goal>single</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Key points:**

- `org.openpnp:opencv` bundles OpenCV's native DLLs inside the jar so we
  don't have to install OpenCV separately. The package name `nu.pattern`
  for the loader class is a historical quirk - get used to seeing it.
- `jnativehook` is the OS-level keyboard hook (Java's `KeyListener` only
  fires when your window has focus).
- `maven-assembly-plugin` makes the fat jar with `jar-with-dependencies`.
- `<mainClass>` is what `java -jar` will run.

## Directory layout

Maven is strict about layout:

```
stark-mouse/
├── pom.xml
└── src/main/java/com/starkmouse/
    ├── app/
    ├── input/
    ├── detection/
    ├── control/
    └── scratchpad/
```

PowerShell:

```
mkdir -p src\main\java\com\starkmouse\app
mkdir -p src\main\java\com\starkmouse\input
mkdir -p src\main\java\com\starkmouse\detection
mkdir -p src\main\java\com\starkmouse\control
mkdir -p src\main\java\com\starkmouse\scratchpad
```

Java rule: `package com.starkmouse.X;` must match `src/main/java/com/starkmouse/X/`.
Wrong folder = compile error.

## .gitignore

```
target/
*.class
*.log
.idea/
*.iml
.vscode/
.metadata/
.classpath
.project
.settings/
bin/
.DS_Store
```

## MainApp skeleton

`src/main/java/com/starkmouse/app/MainApp.java`:

```java
package com.starkmouse.app;

/** Application entry point. Filled in over the next chapters. */
public class MainApp {
    /**
     * Entry point.
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("Stark Mouse - skeleton");
    }
}
```

## Build and run

```
mvn package
java -jar target/stark-mouse-1.0.0-jar-with-dependencies.jar
```

First `mvn package` downloads ~110 MB (OpenCV is chunky). Output:
`Stark Mouse - skeleton`. The fat jar will be ~110 MB. Don't be alarmed.

## Useful Maven commands

- `mvn compile` - just compile, no jar
- `mvn package` - compile + jar
- `mvn clean` - delete `target/`
- `mvn clean package` - fresh build

## Common errors

- **"Could not find or load main class"**: `<mainClass>` in pom doesn't
  match where `MainApp.java` actually lives.
- **"package com.starkmouse.app does not exist"**: file in wrong folder.
- **"Source option X is no longer supported"**: `mvn -version` is using
  an old Java. Fix PATH so Java 17 wins.

Commit `pom.xml`, `.gitignore`, `MainApp.java`, message: `add maven skeleton`.

Move to chapter 3.
