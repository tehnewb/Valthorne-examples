# Physics and lighting studio

A complete Jolt simulation workbench with fourteen focused labs, live contact telemetry,
raycast interaction, an editable light rig and a dark gradient interface. Shared gallery
assets and lighting are retained while scenario resets replace the physics world.

**Requires:** Windows x64, OpenGL 4.3, Filament and Jolt. See [platform support](platforms.md).

## Run without Gradle

[Download Physics studio for Windows x64](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-physics-studio-windows-x64-2.0.1.zip).
Extract the whole ZIP and double-click **Start.bat**. Java, assets and dependencies
are included. To run a bounded smoke check, open PowerShell in the extracted
folder and use `.\Start.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other distribution options.

## Build and run from source

```sh
./gradlew runPhysicsStudio
./gradlew runPhysicsStudio --args="--help"
./gradlew runPhysicsStudio --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- **Right drag / middle drag / wheel**: orbit, pan and zoom over the scene.
- **Click** a dynamic body to push it; **Space** launches a sphere; **R** resets.
- Select a bulb and drag horizontally, or **Shift+drag** for height. Escape cancels placement or exits.
- The panels provide pause, single-step, scenario selection, body settings, imported-model drops and light editing.
- **Lighting: enabled/disabled** suppresses the environment and every authored light without losing the rig configuration.

## Read the implementation

Start at [PhysicsStudio.java](../src/main/java/valthorne/examples/physicsstudio/PhysicsStudio.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` loads retained assets/rendering resources, attaches the shared rig and builds controls.
2. `reset` replaces the world and calls the selected scenario builder.
3. `body` binds visuals and collision; gallery exhibits use static mesh collision while dropped models use convex hulls.
4. `PhysicsStudioParticles` owns a bounded emitter and borrows the current world/scene.
5. Gesture helpers preserve UI ownership; disposal detaches listeners and closes emitters before the world.

## Scenario catalog

The scenario selector is deliberately organized by API concept:

0. Ball pit: dense sphere contacts and a bounded container.
1. Shape gallery: box, sphere, cylinder, capsule, convex hull and static mesh.
2. Material lab: friction and restitution comparisons.
3. Force lab: initial velocity, center/point impulses, angular impulse, force and torque.
4. Body behavior: damping, gravity factors, rotation locking and sleeping policy.
5. Kinematics: translating and rotating platforms carrying dynamic passengers.
6. Sensors: non-solid trigger volumes with added, persisted and removed contact counts.
7. Layers: custom filtering where layers one and two ignore each other.
8. Joints: a distance-constrained chain attached to a static anchor.
9. CCD and raycasting: fast discrete/continuous bodies, a thin wall and pointer raycasts.
10. Dominoes: a rolling ramp feeding a curved chain.
11. Stress: 288 mixed dynamic bodies.
12. Mesh and convex hull gallery: static triangle meshes and droppable convex assets.
13. Particle fountain: bounded physical or visual-only particles.

## Modes and outputs

`--scenario=0..13` selects the catalog above; the ball pit is the default.
`--lights=3..16` sets the initial rig size. `--visual-particles` disables
particle-body collision; `--particle-rate=0..120` sets emissions per second.
`--smoke` exercises scene/editor input. `--benchmark` warms up 60 frames and
measures 240; `--smoke --scenario=13` specifically checks particle controls.

Saves: `build/physics-studio/rig.properties`. Captures: `build/physics-studio/scenario-N.png` and gallery inspection views.

![Physics studio](images/physics-studio.png)

## Extend it

Add a scenario as a distinct builder and preserve the existing reset/dispose boundaries. Use one borrowed mesh for many instances. Add a body property through the spawn settings rather than mutating a render transform independently of physics. Validate pause, single-step and reset with particles active.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
