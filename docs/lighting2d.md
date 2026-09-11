# Cached 2D lighting

This demo combines colored lights with occluding geometry and a cached light map. Its static and stress options expose the difference between drawing a frame and rebuilding light/shadow data.

**Requires:** OpenGL 3.3. See [platform support](platforms.md).

## Run

```sh
./gradlew runLighting2DExample
./gradlew runLighting2DExample --args="--help"
./gradlew runLighting2DExample --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- Use the on-screen scene to inspect light overlap and occluder shadows.
- **Space**: toggle animation. **V**: toggle VSync. **Escape**: exit.
- Restart with the options below to compare static and animated workloads.

## Read the implementation

Start at [Lighting2DExample.java](../src/main/java/valthorne/examples/lighting2d/Lighting2DExample.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` creates the light renderer, shared white texture, batch and authored light/occluder arrangement.
2. `update` changes animated scene state only when animation is enabled.
3. `rect` expresses the simple scene through a reused texture instead of allocating one texture per shape.
4. `render` draws the scene/light composition and records renderer counters during benchmark mode.
5. `dispose` releases the light renderer and shared graphics resources.

## Modes and outputs

`--static` disables animation; `--stress-lights` enables the heavier light set. `--snapshot=path.png` captures after 150 frames. `--smoke` selects `build/captures/lighting2d.png`. `--benchmark` records the bounded workload and prints light-map render/shadow-upload counts with timings.

Default smoke output: `build/captures/lighting2d.png`. Compare equivalent light counts, resolution and animation state when measuring cache improvements.

## Extend it

Add an occluder and move it only when input changes its position. Compare the renderer counters before and after the edit. Keep a second scene completely static to demonstrate cache reuse without changing the visual result.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
