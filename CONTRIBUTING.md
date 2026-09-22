# Contributing

Use JDK 25 and the checked-in wrapper. Current main uses development engine APIs;
select a sibling engine checkout with `'-PvalthorneDir=../Valthorne'`. CI pins the
matching engine revision; see [web development](docs/web-development.md). Releases
must build against a published engine without a local checkout or `mavenLocal()`.

## Code and documentation

- Keep each demo in its feature package. Shared code should have a clear purpose and owner.
- Use descriptive names, four-space indentation and the pinned formatter: `./gradlew formatJava`.
- Document every named type and explicitly declared method, including private helpers.
  Explain contracts and intent rather than restating a method name. Note units,
  coordinate spaces, mutation, ownership, null/sentinel values and relevant failures.
- Comment fields where units, capacity, resource ownership or lifecycle are not evident.
  Keep comments next to the state they explain; update them when behavior changes.
- Put GPU work on the context thread. Close particle emitters before their borrowed
  world and close asset libraries after their dependent scene instances.
- Update the walkthrough, launcher options and controls when behavior changes.
  Example code should teach the current engine API and preserve a clear path from
  input through simulation to rendering.

## Required checks

```sh
./gradlew formatJava
./gradlew '-PvalthorneDir=../Valthorne' build
./gradlew '-PvalthorneDir=../Valthorne' run --args="<demo-id> --help"
./gradlew '-PvalthorneDir=../Valthorne' run --args="<demo-id> --smoke"
```

Choose a supported graphics host for the last command. Record the OS, GPU and driver
with visual changes. Run related native validators when changing gameplay, assets
or persistence. Keep benchmark runs isolated from other timed work and identify the
exact workload, revision, warm-up and measurement window.

Executable validation helpers live with the components they exercise, and repository
checks live under `tools/`. Do not add test folders, private assets, credentials,
generated captures, saves or build output. A normal build performs display-free
checks; graphics checks are explicit and never silently converted into a passing build.

## Assets and releases

Preserve author/source/license information for every imported asset. Add source and
conversion details, validate material/texture references, and update the resource
checksum manifest deliberately. Do not include source-site preview renders or logos
merely because the downloadable model has a permissive license.

Before publishing an examples download, run `build examplesZip`, extract the ZIP into
a clean directory, and build it there. The engine publication is independent: this
repository has no Maven publishing configuration. See [verification](docs/verification.md)
and [asset provenance](docs/assets.md).
