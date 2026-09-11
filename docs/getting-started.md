# Run your first example

Use a **64-bit JDK 25** for the supported desktop target. Set `JAVA_HOME` to that JDK
and select it as the IDE's Gradle JVM. The wrapper uses Gradle 9.3.1; installing
Gradle separately is unnecessary. The library version is pinned in `gradle.properties`.

## Get the project

```sh
git clone https://github.com/tehnewb/Valthorne-examples.git
cd Valthorne-examples
./gradlew runMinimalExample
```

Alternatively extract the optional examples ZIP into a writable directory. Open
that directory as a Gradle project. On Windows replace `./gradlew` with
`./gradlew.bat`. On Unix, if an extracted archive loses execute permissions, use
`bash ./gradlew` or restore the wrapper's executable bit.

The initial build needs internet access for Gradle and Maven Central. All example
models, textures and their licenses are already included. Later builds can use the
local dependency cache. There is no dependency on a sibling Valthorne source checkout.

## Explore

```sh
./gradlew listExamples
./gradlew run3DExample --args="--help"
./gradlew run3DExample --args="--smoke"
./gradlew run --args="scene --snapshot=build/captures/my-scene.png"
```

The starter opens a blue window and exits with Escape. The 3D example displays a
lit scene with a hierarchy, particles and overlays. The scene smoke run captures a
PNG and exits after 90 rendered frames. Other demos have their own bounded audit
sequences; see their walkthroughs before choosing benchmark modes.

Help works without a display. A smoke run still creates a real window/context and
needs a driver, even when the window is hidden. Unknown options are rejected before
native initialization. Do not combine smoke and benchmark modes.

## IDE and launcher setup

Import `build.gradle`, let dependency resolution finish, and use the Gradle launch
tasks. To create an IDE Java run configuration directly:

- Main class: `valthorne.examples.launcher.ExampleLauncher`.
- Program arguments: a demo ID, such as `physics --smoke`.
- Working directory: this repository root, so output paths resolve consistently.
- JVM: JDK 25 with `--enable-native-access=ALL-UNNAMED`.
- On macOS also add `-XstartOnFirstThread`.

Launching a demo's implementation class directly bypasses the common option and
platform checks. The central launcher is the supported entry point for learning,
automation and installed distributions.

## Distribution scripts

The release's `Valthorne-examples-runtime-2.0.0.zip` contains compiled examples and
dependencies. Extract it and follow [runtime instructions](../README-RUNTIME.md)
to run without Gradle, including on a machine where Gradle cannot start.
`./gradlew distZip` builds this runtime ZIP on a working development machine.
The similarly named `Valthorne-examples-2.0.0.zip` is the source project and requires
Gradle to compile. The two tasks write distinct files.

`./gradlew installDist` creates `build/install/Valthorne-examples/`. Its `bin/`
scripts include the required classpath and native-access flags, and select the macOS
first-thread flag at launch time. Run the script with
`--list`, then a demo ID. A compatible Java 25 runtime is still required; this is
not an OS installer or a bundled JRE.

`./gradlew examplesZip` creates a source project under `build/distributions/`.
It contains wrappers, code, documentation and assets, excluding local output and
test folders. Do not copy only the examples JAR: runtime dependencies and resources
are required.

## Make it your game

Begin with [the starter walkthrough](starter.md), then create an independent Gradle
or Maven project using the [engine integration guide](https://github.com/tehnewb/Valthorne/blob/v2.0.0/docs/getting-started.md).
Copy only the example code/assets you need and preserve their license notices.
Create GPU resources after context initialization, update using seconds, and release
owned resources in the documented order. See [architecture](architecture.md).
