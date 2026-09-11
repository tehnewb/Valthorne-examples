# Rigid-body playground

A focused playground for static, dynamic and kinematic bodies, gravity, collision, sleeping, scene bindings and continuous collision detection. Rendering remains on the portable raster path.

**Requires:** OpenGL 3.3 and Jolt. See [platform support](platforms.md).

## Run

```sh
./gradlew runPhysics3DExample
./gradlew runPhysics3DExample --args="--help"
./gradlew runPhysics3DExample --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Left/right arrows**: orbit the camera.
- **Space**: drop another body. **Click**: push a body through the picking/query path.
- **R**: reset the playground. **V**: toggle VSync. **Escape**: exit.

## Read the implementation

Start at [Physics3DExample.java](../src/main/java/valthorne/examples/physics/Physics3DExample.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` creates rendering resources and calls `reset` to construct the initial physics world.
2. `add` pairs a scene instance with a collision shape and body settings, then binds its transform to physics.
3. `update` handles requested interactions and advances simulation. Body synchronization belongs to the world, not an independent animation loop.
4. `render` draws the simulated state with shadows and diagnostics.
5. `reset` and `dispose` close the owned world before discarding its body references.

## Modes and outputs

`--snapshot=path.png` captures after 150 frames. `--smoke` supplies `build/captures/physics.png`. `--all-lights` enables the full authored light set; `--stress-lights` selects the heavier lighting workload. `--benchmark` warms up 120 frames and measures 300 completed frames before reporting.

Default smoke output: `build/captures/physics.png`. Benchmark timings describe this scene on the recorded driver; they are not a cross-hardware engine speed guarantee.

## Extend it

Change mass, friction or restitution on newly created body settings and compare behavior. Add a kinematic obstacle with an explicit trajectory. Keep collision dimensions and rendered geometry aligned, and test that repeated reset does not grow the world body count.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
