# UI component gallery

Explore every concrete element in the current Valthorne UI library through five focused pages. The gallery covers texture-backed, NanoVG and Slug rendering while demonstrating that all three paths share layout, focus, clipping and pointer ownership.

**Requires:** OpenGL 3.3. See [platform support](platforms.md).

## Run

```sh
./gradlew runUIShowcase
./gradlew runUIShowcase --args="--help"
./gradlew runUIShowcase --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

- Use the left navigation to open Foundations, Inputs, Navigation, Data or Overlays.
- **Inspect layout**: toggle live bounds visualization for the retained UI tree.
- **Tab / Shift+Tab**: move keyboard focus. **Enter / Space**: activate the focused control.
- **Arrow keys**: adjust focused sliders and split dividers. **Escape**: dismiss a modal.
- Click table headers to sort; scroll the 5,000-row lists to observe bounded live-node counts.

## Read the implementation

Start at [UIShowcase.java](../src/main/java/valthorne/examples/ui/UIShowcase.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` creates the professional theme, shared preview image, Slug font and permanent application shell.
2. `show` clears the previous page and dispatches to one small page builder.
3. Foundations demonstrates containers, labels, images, grids, mixed rendering and cached/live Slug text.
4. Inputs pairs both renderer families so their buttons, fields, checkboxes, sliders and progress bars can be compared directly.
5. Navigation presents both implementations of scrolling, collapsible sections, tabs and split panes.
6. Data exercises both virtual-list and sortable-table implementations with realistic collections.
7. Overlays demonstrates both tooltip and modal paths plus `NanoHyperlink` activation.

The helper methods at the end of the class contain only repeated presentation conventions—card surfaces, typography and sizing—so each component example remains explicit and easy to copy.

## Modes and outputs

`--smoke` visits every page, captures the rendered state and exits. No benchmark flag is provided.

Smoke captures are written under `build/ui-showcase/` as `foundations.png`, `inputs.png`, `navigation.png`, `data.png` and `overlays.png`. See the checked-in illustration below.

![UI data tools](images/ui.png)

## Extend it

Add a component to the page matching its behavior, then include its name in `componentRows` so the inventory table remains auditable. Verify keyboard focus, scrolling, clipping and modal capture after changing nesting.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
