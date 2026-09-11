# Platforms and runtime requirements

The examples use Java 25 and the native dependencies supplied by Valthorne 2.0.0.
The build resolves the same published engine on Windows, Linux and macOS; a native
compiler is not required. Rendering depends on the selected demo and driver.

| Target | Raster demos | Compute demos | Filament studios / arena |
| --- | --- | --- | --- |
| Windows x64 | OpenGL 3.3 | OpenGL 4.3 | Supported sharing backend; OpenGL 4.3 |
| Linux x64 | OpenGL 3.3 and display server | OpenGL 4.3 | Unavailable |
| macOS Intel / Apple Silicon | OpenGL 3.3 core; first-thread launcher | Unavailable | Unavailable |
| Other CPUs | Engine native artifacts may exist; requires separate target validation | Driver-dependent | Unavailable |

The common launcher rejects the known unsupported compute and Filament combinations
before opening a window. It does not certify a graphics driver. macOS OpenGL stops
at 4.1; use the starter, scene, physics, 2D lighting, UI or audio demos there.

The engine's hosted macOS runners could not create an accelerated NSGL pixel format.
Mac rendering remains unverified on those hosts. A successful compile or native
physics check is not rendering validation. Run a smoke check on the actual Mac before
relying on it for distribution. The [engine platform matrix](https://github.com/tehnewb/Valthorne/blob/v2.0.0/docs/platforms.md)
records the broader native-artifact and renderer limits.

On Linux, use a desktop session and installed graphics drivers. CI uses Xvfb with
Mesa software rendering for portable examples. That checks behavior, not GPU speed.
Silent automated runs may set `ALSOFT_DRIVERS=null`; do not set it in a normal game
launcher if audible output is wanted. Audio Studio starts paused and synthesizes
its own tones.

| Problem | Action |
| --- | --- |
| Java version or class-file error | Select JDK 25 in `JAVA_HOME` and the IDE's Gradle settings. |
| Missing native library | Use the complete Gradle runtime classpath and a matching JVM architecture. |
| GLFW / NSGL / display error | Check the desktop session, driver, requested OpenGL version and macOS first-thread flag. |
| Shader compilation error | Record the renderer/driver and demo; compute shaders require OpenGL 4.3. |
| Missing model or texture | Keep `src/main/resources` and dependency JAR resources intact. Run `verifyAssets`. |
| No sound | Press Play, check mute/gain and select an audio device; remove the null driver override. |
| Capture or save fails | Use a writable output directory; defaults are under `build/`. |

Record the demo, command, OS/CPU, Java version, GPU/driver and full failure message
when reporting a problem. Do not hide a graphics failure by describing a headless
build as a successful visual run.

## Gradle fails before project evaluation on Windows

`java.io.IOException: Unable to establish loopback connection` does not establish
that the FPS renderer failed. If Gradle has not evaluated the project, the demo has
not started. In OpenJDK's Windows [pipe implementation](https://github.com/openjdk/jdk25u/blob/master/src/java.base/windows/classes/sun/nio/ch/PipeImpl.java),
that message wraps an underlying local socket failure. Depending on the call path,
the socket can be TCP or Unix-domain. Read the deepest `Caused by` entries before
changing network settings; an IPv4 preference cannot fix every path.

To run the demo immediately, use the prebuilt runtime ZIP and
[direct launcher](../README-RUNTIME.md). This avoids Gradle startup, but cannot
guarantee graphics or other Java functionality on the affected computer.

For diagnosis, run these from the source checkout with JDK 25:

```powershell
java -version
java tools/JavaLoopbackCheck.java
.\gradlew.bat help --stacktrace
```

Compare the diagnostic in the affected terminal and in an ordinary PowerShell
window opened from Start. Detached child processes may still inherit the parent's
environment or restrictions. A failed Java-only probe reproduces the issue outside
the engine. TCP passing while selector creation fails narrows it to the selector
path. Inspect the reported Java home, Java temporary directory, exception and the
machine's sandbox/security policy with that evidence; do not disable protection
or reset networking based only on this generic message.

`--no-daemon` is not a reliable socket bypass: Gradle may still start a
[single-use daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:disabling_the_daemon)
when JVM arguments differ. Share the full cause chain, Java version and launch
environment when reporting the result. A successful local probe does not establish
that the affected computer's problem is fixed.

If the cause ends in `UnixDomainSockets.connect0` with `Invalid argument: connect`,
the failing connection is Unix-domain. A TCP firewall rule or IPv4 preference does
not target that path. Check the socket directory and launch environment first.
Java's [Windows socket-directory selection](https://docs.oracle.com/en/java/javase/25/core/java-networking.html)
considers `jdk.net.unixdomain.tmpdir`, the JDK's `conf/net.properties`, `TEMP`, then
`java.io.tmpdir`. A bad inherited `TEMP` or restricted socket operation is a possible
cause, not a diagnosis established by the message alone.

As a controlled diagnostic, create a short, writable directory on a local disk and
pass `-Djdk.net.unixdomain.tmpdir=<that-directory>` to the Java-only probe. If that
changes the outcome, investigate the original directory configuration. A failure
despite a valid directory needs the runtime/launch-policy evidence above. Do not
add a machine-specific path to the shared Gradle configuration.
