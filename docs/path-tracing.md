# Progressive path tracing

A focused path-traced room demonstrates rough diffuse surfaces, polished metal, transmissive glass and colored area lights. A second view uses the engine’s 2D wrapper. This launcher opens the path-tracing sample directly, including with no options.

**Requires:** OpenGL 4.3; unavailable on macOS OpenGL. See [platform support](platforms.md).

## Run without Gradle

Extract the [Windows desktop download](https://github.com/tehnewb/Valthorne-examples/releases/latest)
and double-click `path-tracing.bat`. Java and dependencies are included. To run its smoke
check, open PowerShell in the extracted folder and use `.\path-tracing.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other platforms and launch options.

## Build and run from source

```sh
./gradlew runPathTracingExample
./gradlew runPathTracingExample --args="--help"
./gradlew runPathTracingExample --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Tab**: switch 2D/3D views. **Left/right arrows**: orbit the 3D camera.
- **1/2/3**: select the implemented quality presets. **D**: toggle denoising.
- **V**: toggle VSync. **Escape**: exit.

## Read the implementation

Start at [PathTracingExample.java](../src/main/java/valthorne/examples/lightingstudio/PathTracingExample.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `model` and `paint` build readable material variants in a small authored room.
2. `init` configures progressive tracers, sky, emissive surfaces and the overlay.
3. `active` selects which tracer supplies quality and accumulation diagnostics.
4. `update` changes camera/settings; such changes invalidate accumulated samples.
5. `render` waits for the requested accumulation in snapshot mode, then captures before swap and exits.

## Modes and outputs

`--2d` starts in the flat view. `--quality=INTERACTIVE|HIGH|ULTRA` selects a quality enum. `--snapshot=path.png` waits for 512 accumulated samples. `--smoke` supplies `build/captures/path-tracing.png` using the same converged capture path. `--benchmark` warms up 30 frames and measures 120 completed frames; it is not a convergence benchmark.

Default smoke output: `build/captures/path-tracing.png`. Snapshot runs can take longer than the other smoke checks because they wait for convergence.

## Extend it

Change one roughness or transmission value and compare converged snapshots with the camera held still. Add another area emitter, then compare raw and denoised results. Never compare moving, repeatedly invalidated accumulation with a settled reference as if they were equivalent workloads.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
