# Interactive lighting studio

An editable material room with sphere/panel emitters, camera navigation, light selection, presets, undo/redo and persistence. Windows x64 defaults to Filament; other supported compute hosts use the path tracer. macOS OpenGL cannot run this demo.

**Requires:** OpenGL 4.3; Windows x64 for the optional Filament backend. See [platform support](platforms.md).

## Run without Gradle

[Download Lighting studio for Windows x64](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-lighting-studio-windows-x64-2.0.1.zip).
Extract the whole ZIP and double-click **Start.bat**. Java, assets and dependencies
are included. To run a bounded smoke check, open PowerShell in the extracted
folder and use `.\Start.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other distribution options.

## Build and run from source

```sh
./gradlew runLightingStudio
./gradlew runLightingStudio --args="--help"
./gradlew runLightingStudio --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Right drag**: orbit. **Middle drag**: pan. **Wheel**: zoom. **3D / Top view** switches projection.
- Click a light to select; drag horizontally, or **Shift+drag** to change height.
- Use placement, duplicate, enable/delete, palette and numeric controls to edit the rig. **Escape** cancels placement.
- Use undo/redo and Save/Load rig; saved light state is separate from camera/render settings.

## Read the implementation

Start at [LightingStudio.java](../src/main/java/valthorne/examples/lightingstudio/LightingStudio.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `buildRoom` constructs shared material-reference geometry; `addLight` and `apply` retain light visuals.
2. `buildUi` creates the toolbar, property panels and viewport boundaries.
3. `capture`, `checkpoint` and `restore` copy light state for bounded undo/redo. Here `capture` means a state snapshot; `captureImage` writes pixels.
4. `pointRay` and `pickOrPlace` map pointer input into the active camera view.
5. `render` selects the renderer and handles asynchronous timer queries; disposal detaches input and closes context-owned resources.

## Modes and outputs

`--pathtracer` selects the compute renderer. `--progressive` selects stationary accumulation. `--filament-quality=INTERACTIVE|HIGH|ULTRA` chooses Filament quality. `--benchmark` or `--benchmark-motion` records a bounded workload. `--visual-validation=folder` runs the separate fixed-scene visual audit. `--smoke` performs native camera/light/UI interactions and captures both views.

Saves: `build/lighting-studio/rig.properties`. Smoke captures: `build/lighting-studio/studio.png` and `top-view.png`. A separate visual-audit folder may be chosen explicitly.

## Extend it

Add an editable material property and connect its change to the appropriate renderer invalidation. Preserve the difference between retained live objects and copied undo state. Keep UI pointer capture separate from camera gestures, especially when dragging across a panel boundary.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
