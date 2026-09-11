# Build, smoke checks and measurements

`./gradlew build` compiles against Maven Central, checks Java formatting, validates
documentation, verifies packaged assets and runs native gameplay/persistence checks.
It also generates Javadoc. It does not open a graphics window.

| Task | Coverage |
| --- | --- |
| `checkFormat` / `formatJava` | Pinned Google Java Format 1.36.0 in AOSP style; check or apply formatting |
| `verifyDocumentation` | Named type/method contracts, local Markdown links and resource checksums |
| `verifyGalleryAssets` | Six shared gallery assets: bounds, material groups and normals |
| `verifyCombatAssets` | Combat OBJ resources, materials, attachment metadata and normals |
| `verifyEnvironmentAssets` | Industrial kit dimensions, materials, centering and normals |
| `verifyLightRig` | 83 isolated editing/persistence checks; unique temporary files, no saved-rig mutation |
| `verifyFpsArena` | 1,437 native movement, combat, wave, effects and ownership checks |
| `javadoc` | Reference documentation, including private helper contracts |
| `examplesZip` | Optional standalone project download, excluding local output and tests |

The native validation counts describe this initial examples revision and may grow.
Read task output for the count on a later revision. There is no test folder required
for these checks and no silent dependency on ignored engine files.

## Graphical runs

Every demo accepts `--smoke` through its launcher task. The starter exits after three
updates. Other demos capture a deterministic scene or exercise a bounded sequence of
native input actions. Run the same interactive demo afterward when reviewing visual
changes; a capture check alone does not certify usability or every driver.

```sh
./gradlew run3DExample --args="--smoke"
./gradlew runUIShowcase --args="--smoke"
./gradlew runPhysicsStudio --args="--smoke --scenario=4"
./gradlew runFpsArena --args="--smoke"
```

Output goes to `build/captures/`, `build/ui-showcase/`, `build/audio-studio/`,
`build/lighting-studio/`, `build/physics-studio/` or `build/fps-arena/`, depending on
the demo. Frame capture reads the actual framebuffer size and releases native pixel
storage synchronously. Graphics failures remain failures; headless success does not
substitute for a working context.

CI builds on Windows, Linux and both Mac architectures. Linux uses Xvfb/Mesa to run
the portable starter, scene, physics, 2D lighting, UI and audio smoke checks. Hosted
Mac graphics are not claimed: see [platform limits](platforms.md). Filament and compute
smoke runs require appropriate local hardware.

## Benchmarks

Use a demo's documented `--benchmark` options. They warm up before recording a fixed
number of samples, report milliseconds and exit. Do not combine `--benchmark` and
`--smoke`, compare different asset workloads as equivalent, or describe CPU/frame
timings as GPU-only measurements. GPU/driver, renderer, quality, lights, particle mode,
resolution, revision and power state belong with every published result.

## Release download check

Build `examplesZip`, extract it outside this checkout, then run `build` and at least
the starter smoke on a supported host. The extracted project must resolve Valthorne
from Central and include all asset notices. Publish the ZIP with its SHA-256 checksum;
keep the corresponding source commit and engine version in the release notes.
