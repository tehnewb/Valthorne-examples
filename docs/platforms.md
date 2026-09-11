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
