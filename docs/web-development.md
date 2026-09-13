# Shared desktop and web FPS

Maintain runnable examples in this repository. The FPS uses Valthorne's
`Canvas2D` and `PlatformTools` APIs, with Jolt physics retained. The FPS entry
point requests OpenGL 4.1. This context request does not expand the supported
demo platforms: the shared launcher still validates the requirements in
[platform support](platforms.md), and the downloadable desktop FPS remains a
Windows x64 application.

The new engine APIs currently require the development engine checkout. With
`Valthorne` and `Valthorne-examples` as sibling directories:

```sh
./gradlew '-PvalthorneDir=../Valthorne' runFpsArena
./gradlew '-PvalthorneDir=../Valthorne' verifyFpsArena verifyAssets
node ../Valthorne/portable/fps.mjs build web
node ../Valthorne/portable/fps.mjs verify web
```

Use `./gradlew.bat` in Windows PowerShell, keeping the entire `-P` argument
quoted. The optional `valthorneDir` selects a local engine composite build;
without it the project continues to resolve its pinned Maven
dependency. That published dependency must include the new engine APIs before
these FPS changes can be released without the local checkout.

CI checks out the exact `VALTHORNE_ENGINE_REVISION` from
[its workflow](https://github.com/tehnewb/Valthorne-examples/blob/main/.github/workflows/ci.yml)
under `build/engine` and passes
`'-PvalthorneDir=build/engine'` to its build and graphics checks. To reproduce CI,
use that revision for the local engine checkout. The engine checkout stays outside
the `examplesZip` source archive; its contents are limited to this project's
sources, resources, documentation, launch tools and build configuration. A source
archive built from current main still needs the matching development engine.

Web output is in `Valthorne/portable/web/build/dist`. The exporter selects this
repository's FPS source files and resources directly; do not copy them back
into the engine repository. Engine compatibility fixtures stay with the engine.
