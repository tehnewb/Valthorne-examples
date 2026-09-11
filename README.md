# Valthorne examples

Runnable examples for **Valthorne 2.0.0**, from an empty application window to a
playable 3D arena. Each demo includes its source, a walkthrough, and any required
redistributable assets. The project consumes the released engine from Maven Central.

**Java 25 · Gradle wrapper included · Apache-2.0 code · CC0 example assets**

[Build status](https://github.com/tehnewb/Valthorne-examples/actions/workflows/ci.yml)
· [Download the standalone project](https://github.com/tehnewb/Valthorne-examples/releases/latest)

![Valthorne physics studio with textured models and editable lighting](docs/images/physics-studio.png)

Captured from the included Windows x64 demo. Rendering features and host requirements
vary by example; see the catalog below.

## Start here

Install JDK 25, clone this repository, and run:

```sh
./gradlew runMinimalExample
./gradlew listExamples
./gradlew run3DExample
```

On Windows PowerShell use `./gradlew.bat`. The wrapper supplies native dependencies
and launcher flags. Graphical demos need a desktop session and a compatible driver.
First-time builds download Gradle and Maven dependencies; assets need no separate download.

Read [getting started](docs/getting-started.md) for IDE setup, command-line help,
downloads, and creating your own game. A good learning sequence is **starter → scene
→ physics → UI**, followed by the studios or arena.

## Choose a demo

| Demo | Launch task | Learn | Requirements |
| --- | --- | --- | --- |
| [Application starter](docs/starter.md) | `runMinimalExample` | Lifecycle and resource ownership | OpenGL 3.3 |
| [3D scene](docs/scene.md) | `run3DExample` | Scene hierarchy, picking, shadows and overlays | OpenGL 3.3 |
| [Physics playground](docs/physics.md) | `runPhysics3DExample` | Bodies, collision and fixed-step simulation | OpenGL 3.3 + Jolt |
| [2D lighting](docs/lighting2d.md) | `runLighting2DExample` | Lights, occluders and cache invalidation | OpenGL 3.3 |
| [UI gallery](docs/ui.md) | `runUIShowcase` | Shared texture/NanoVG UI, tables and virtual lists | OpenGL 3.3 |
| [Audio studio](docs/audio.md) | `runAudioStudio` | Sound areas, listener motion and gain | OpenGL 3.3 + audio device |
| [Lighting studio](docs/lighting-studio.md) | `runLightingStudio` | Editable lights, materials and render modes | OpenGL 4.3 |
| [Path tracing](docs/path-tracing.md) | `runPathTracingExample` | Progressive lighting, glass and area lights | OpenGL 4.3 |
| [Physics studio](docs/physics-studio.md) | `runPhysicsStudio` | Gallery, simulation editor and particles | Windows x64 + OpenGL 4.3 |
| [FPS arena](docs/fps.md) | `runFpsArena` | Gameplay, physics, UI, assets and effects together | Windows x64 + OpenGL 4.3 |

Every launch task accepts `--args="--help"` and `--args="--smoke"`. Smoke checks are
bounded graphical runs, not headless tests. [Platform support](docs/platforms.md)
explains macOS limitations and Filament requirements.

## Read and extend the code

Sources are organized by feature under [src/main/java/valthorne/examples](src/main/java/valthorne/examples).
[Architecture and ownership](docs/architecture.md) explains the boundaries between
applications, gameplay state, shared assets, input routing and capture utilities.
Every named type and declared method has a Javadoc contract; build checks enforce
that coverage. `./gradlew javadoc` generates the reference under `build/docs/javadoc/`.

```sh
./gradlew build          # Compile, format check, documentation, asset/native checks
./gradlew examplesZip    # Optional source-and-assets download
./gradlew installDist    # Launcher scripts with all runtime dependencies
```

See [verification](docs/verification.md), [contributing](CONTRIBUTING.md), and
[asset provenance](docs/assets.md). Generated captures, saves and reports live under
ignored `build/` directories. No test folders or private engine checkout are needed.

## Use Valthorne in your game

The examples are an optional learning project. Your game only needs the engine:

```groovy
repositories { mavenCentral() }
dependencies { implementation 'io.github.tehnewb:Valthorne:2.0.0' }
```

See the engine's [complete integration guide](https://github.com/tehnewb/Valthorne/blob/v2.0.0/docs/getting-started.md).
Nothing in this repository is added to the engine dependency or its published JARs.

Code is [Apache-2.0](LICENSE). Asset terms, credits and source manifests are preserved
in [third-party notices](THIRD_PARTY_NOTICES.md). Please report example-specific bugs
with the demo name, OS, JVM, graphics driver and reproduction steps.
