# 3D scene, picking and overlays

Study a complete raster composition: seeded procedural meshes, hierarchical transforms, textured surfaces, point/directional lighting, a shadow map, particles and 2D title/performance overlays. This is the portable rendering path.

**Requires:** OpenGL 3.3. See [platform support](platforms.md).

## Run without Gradle

[Download 3D scene for Windows x64](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-scene-windows-x64-2.0.1.zip).
Extract the whole ZIP and double-click **Start.bat**. Java, assets and dependencies
are included. To run a bounded smoke check, open PowerShell in the extracted
folder and use `.\Start.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other distribution options.

## Build and run from source

```sh
./gradlew run3DExample
./gradlew run3DExample --args="--help"
./gradlew run3DExample --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Left/right arrows**: orbit. **W/S**: change camera distance.
- **Left click**: select a mesh through the picking ray.
- **V**: toggle VSync. **Escape**: exit.

## Read the implementation

Start at [Scene3DExample.java](../src/main/java/valthorne/examples/scene/Scene3DExample.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` creates reusable textures, lighting, camera/viewport and render batches. It builds scene instances that share CPU-side geometry.
2. A scene-node hierarchy places a satellite relative to its parent, illustrating local versus world transforms.
3. `update` handles orbit/zoom, edge-triggered selection and particle animation.
4. `render` performs shadow and scene passes, then draws overlays through the viewport.
5. `dispose` closes the emitter and rendering resources while the context remains alive.

## Modes and outputs

`--snapshot=path.png` saves the scene after 90 rendered frames and exits. `--smoke` supplies `build/captures/scene.png` unless an explicit snapshot path is provided. These modes still render a real scene.

Default smoke output: `build/captures/scene.png`. World positions use the Z-up engine convention; overlay coordinates and framebuffer pixels are separate spaces.

## Extend it

Add another child node and animate its local transform. Compare its world position with a root-level instance. Add a pickable object that shares an existing mesh, then observe how selection, lighting and shadows follow its transform without reloading geometry.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
