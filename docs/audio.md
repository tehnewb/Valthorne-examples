# Spatial audio studio

The studio synthesizes two quiet tones and starts paused. Edit sound-area coverage, move the listener independently of the viewing camera, and inspect attenuation. Retained wave indicators illustrate playback without reallocating geometry each frame.

**Requires:** OpenGL 3.3 and an audio device. See [platform support](platforms.md).

## Run without Gradle

[Download Audio studio for Windows x64](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-audio-windows-x64-2.0.1.zip).
Extract the whole ZIP and double-click **Start.bat**. Java, assets and dependencies
are included. To run a bounded smoke check, open PowerShell in the extracted
folder and use `.\Start.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other distribution options.

## Build and run from source

```sh
./gradlew runAudioStudio
./gradlew runAudioStudio --args="--help"
./gradlew runAudioStudio --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- Select **A** or **B** in the inspector, or click its source marker. Use **Play / pause**, stop, mute and looping controls.
- Drag empty world space to move the listener horizontally.
- With the pointer over the scene, **WASD** move the listener, **Q/E** change height, arrows orbit and **R/F** zoom.
- **Silent wave preview** shows wave motion without starting audio. **Escape** exits.

## Read the implementation

Start at [AudioStudio.java](../src/main/java/valthorne/examples/audio/AudioStudio.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `tone` synthesizes PCM, and each `Zone` owns its player and shape settings.
2. `Zone.rebuild` updates boundary geometry after a shape/extent change.
3. `Wave` retains visual geometry; normal animation updates transforms and appearance rather than rebuilding the source.
4. `inspect` binds UI controls to the selected zone, while `applyVolume` combines gain and mute.
5. Disposal releases players and rendering/UI resources and restores listener position.

## Modes and outputs

`--smoke` runs the native interaction audit and exits after checking slider dragging, source/listener edits, wave retention and playback/mute behavior. Automated silent hosts may set `ALSOFT_DRIVERS=null`; audible demonstrations must use a real device.

Default output: `build/audio-studio/studio.png`. No sound asset download is needed; shape and volume changes are temporary unless you add your own persistence.

## Extend it

Add a third synthesized source and compare sphere versus box falloff. Keep sound position stationary while moving only the listener. Treat wave rings as a slowed visual illustration: they do not simulate acoustic propagation, reflections or measured pressure.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
