# Assets, provenance and reuse

The example assets are optional content in this repository. They are never added
to the published Valthorne engine JAR. All runtime paths are classpath resources
under `src/main/resources/valthorne/`; no demo downloads a model at launch.

The starter, raster scene, basic physics, 2D lighting, UI and audio demonstrations
generate their example geometry, labels, textures or tones in code. They also use
resources already supplied by the engine where needed. The physics studio and FPS
arena share the gallery assets and add their own model collections.

| Location | Contents and contract |
| --- | --- |
| `physics-studio/models` | Three Kenney furniture OBJs and authored material colors, with original notice |
| `physics-studio/realistic` | Three textured Poly Haven props, original diffuse JPEGs and source/conversion hashes |
| `fps-arena/combat` | Rifle, spare magazine, sentry, grenade and posed arms, plus author and conversion records |
| `fps-arena/environment` | Industrial modules, barriers, crate and barrel, with conversion dimensions and checksums |

See [third-party notices](../THIRD_PARTY_NOTICES.md) for all licenses and source links.
The source manifests distinguish downloaded files from adapted packaged outputs.
Credits, source URLs and transformation notes describe the actual assets, rather
than screenshots or source-site preview images.

## Coordinates and materials

`PhysicsStudioModels` scales gallery geometry to its documented full height and
normalizes the loader's Y-up inputs into the engine's Z-up world. Gallery bases sit
at zero. Dynamic arena crates and barrels instead use body-centered geometry.
`FpsCombatModels` supplies attachment offsets for first-person placement.
`FpsEnvironmentModels` records category, bounds and centering for structural placement.

The OBJ/MTL path retains authored material groups and diffuse/albedo textures.
Original normal and roughness maps are not imported; the examples supply material
roughness/metallic values. Combat arms are posed meshes, not a skeletal-animation
tutorial. Stair collision uses a smooth proxy ramp; a visual mesh and its collision
shape need not share every triangle.

## Lifetime and verification

Load each asset library once and share entries across model instances. Entries are
borrowed: do not close them independently or close the library while a renderer
still references their resources. Constructors clean up partial loads on failure.
Run `verifyAssets` after changing a model, material path or loader.

`tools/resources.sha256` covers every packaged resource, including notices and source
manifests. `verifyDocumentation` detects changed, missing and unlisted resources.
When deliberately changing an asset, update its source/adaptation record and the
checksum inventory together. Keep original download hashes as provenance; do not
rewrite them to pretend an adapted file was the upstream original.

The environment's historical conversion-script reference describes upstream asset
preparation. This standalone repository includes the ready-to-run results and their
conversion records; Blender and conversion tools are not runtime requirements.
