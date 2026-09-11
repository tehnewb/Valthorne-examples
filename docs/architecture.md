# Code structure and ownership

Each feature has its own package. The applications are deliberately readable entry
points, while components reused by multiple scenes have separate ownership boundaries.
The examples depend on the published engine; no engine classes are copied here.

| Package | Responsibility | Key entry points |
| --- | --- | --- |
| `launcher` | Catalog, option validation and platform checks | `ExampleLauncher` |
| `starter`, `scene`, `physics`, `lighting2d` | Small focused learning applications | Their corresponding `*Example` classes |
| `ui`, `audio` | Interactive subsystem galleries | `UIShowcase`, `AudioStudio` |
| `lightingstudio` | Material editor, path tracer and visual audit | `LightingStudio`, `PathTracingExample` |
| `physicsstudio` | Scenario editor and bounded fountain | `PhysicsStudio`, `PhysicsStudioParticles` |
| `fps` | Playable sample, gameplay world, effects and combat/environment assets | `FpsArena`, `FpsArenaWorld`, `FpsArenaEffects` |
| `assets` | Gallery models shared by the physics studio and arena | `PhysicsStudioModels` |
| `lightrig` | Editable lights and isolated persistence checks | `StudioLightRig` |
| `shared` | Small infrastructure with consistent lifetime rules | `FrameCapture` |

## Application lifecycle

1. The launcher selects a demo and validates options before native initialization.
2. The demo creates its application object and passes its window configuration to `JGL`.
3. `init` creates GPU resources and registers controls after the context exists.
4. `update(delta)` applies input and advances simulation using seconds.
5. `render` composes the scene and overlays on the context-owning thread.
6. `dispose` detaches callbacks and releases owned resources before context destruction.

Some engine scene classes provide lifecycle adapters, but the same ordering applies.
Launch one application per JVM. Calling two application entry points concurrently
would share process-wide window/input/audio state and is unsupported.

## Ownership rules

| Resource | Owner and release rule |
| --- | --- |
| Procedural `Model3D` | CPU-side geometry; ordinary Java lifetime. Scene instances borrow it. |
| `ObjModel3D` asset library | Library owns its loaded model resources/textures; close after dependent instances stop rendering. |
| Renderers, batches, textures, themes | Application creates and closes them on the graphics thread. |
| Jolt world and bodies | Gameplay/scenario world owns simulation lifetime; reset removes the old world first. |
| Particle emitters | Effect component owns emitters and their bodies/lights; close before the borrowed physics world. |
| UI pages | Shared root/page owner disposes replaced content and manages focus/capture. |
| Audio players | Audio zones own players; disposal releases them and restores listener state. |
| Capture pixel storage | `FrameCapture` allocates and frees native storage within one synchronous call. |

The arena separates presentation from gameplay: `FpsArena` manages render/UI/input,
`FpsArenaWorld` owns simulation and gameplay rules, and `FpsArenaEffects` owns transient
effects. Asset libraries outlive world resets so a restart does not reload every mesh.
Hit events copy mutable vector values before delivering them to effects.

## Coordinates and input

3D scenes follow the engine's Z-up convention. Physics/world positions are distinct
from UI/window coordinates and native framebuffer pixels. Asset loaders explicitly
normalize source axes and bounds; dynamic props use body-centered geometry where
documented by the asset entry. Never compensate for an axis mismatch in both loader
and instance transform.

UI gets first opportunity to claim pointer/keyboard input. Studio gestures begin
only over the scene viewport and retain their initiating button until release.
Native smoke sequences use installed callbacks, so they exercise the same routing
as user interaction. The common capture utility queries physical framebuffer size
instead of assuming it equals logical window size.

## Extending an example

Keep scene construction, UI construction, simulation and drawing in separate methods.
Reuse one mesh across instances; avoid per-frame allocations for colors, matrices,
vectors and UI labels. Keep state changes explicit so cached rendering can detect
invalidation. Add a helper package only when a component is reused or has a separate
lifetime/contract, rather than hiding the learning path behind abstractions.

Document time units, coordinate spaces, borrowed versus owned resources, state
transitions and failure behavior. Public getters returning bodies or model entries
do not transfer disposal responsibility. Run [verification](verification.md) after
changing lifecycle or ownership, and update the affected walkthrough alongside code.
