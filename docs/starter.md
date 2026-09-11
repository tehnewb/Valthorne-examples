# Application starter

Start with the smallest complete Valthorne application. It opens a blue window, clears the framebuffer every frame and handles a clean exit. No downloaded example asset is needed.

**Requires:** OpenGL 3.3. See [platform support](platforms.md).

## Run

```sh
./gradlew runMinimalExample
./gradlew runMinimalExample --args="--help"
./gradlew runMinimalExample --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Escape** or the window close button: exit.
- `--smoke`: hide the window and exit after three update callbacks.

## Read the implementation

Start at [MinimalExample.java](../src/main/java/valthorne/examples/starter/MinimalExample.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `main` constructs the application and chooses window size, title and visibility. Native initialization belongs to `JGL`.
2. `init` is intentionally empty. Add texture, batch or renderer creation here after a context exists.
3. `update(delta)` receives seconds and decides when to request closure. Keep movement proportional to elapsed time.
4. `render` reuses one background `Color`; avoid allocating colors in the frame loop.
5. `dispose` is the matching place to release any resources you add.

## Modes and outputs

There are no additional options. The common launcher handles `--help` before creating a window.

The smoke check produces no screenshot. Its purpose is lifecycle/context startup and bounded exit.

## Extend it

Add a texture batch and one sprite, then move it using `delta`. Keep the texture and batch in application fields and release them once in `dispose`. Next add a viewport and verify the sprite remains correctly positioned after resizing.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
