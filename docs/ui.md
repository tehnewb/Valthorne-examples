# UI component gallery

Explore every concrete element in the Valthorne 2.2 UI library through a dedicated feature page. Texture-backed and NanoVG counterparts are paired so their shared behavior and renderer-specific styling can be compared directly; Slug typography and Nano-only controls receive focused coverage too.

**Requires:** OpenGL 3.3. See [platform support](platforms.md).

## Run without Gradle

[Download UI gallery for Windows x64](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-ui-windows-x64-2.0.1.zip).
Extract the whole ZIP and double-click **Start.bat**. Java, assets and dependencies
are included. To run a bounded smoke check, open PowerShell in the extracted
folder and use `.\Start.bat --smoke`.
See [runtime instructions](../README-RUNTIME.md) for other distribution options.

## Build and run from source

```sh
./gradlew runUIShowcase
./gradlew runUIShowcase --args="--help"
./gradlew runUIShowcase --args="--smoke"
```

Windows PowerShell uses `./gradlew.bat`. Use the repository root as the working
directory. The common launcher supplies a consistent entry point; Gradle supplies
native access and macOS first-thread flags where applicable.

## Controls

### Wheel and file chooser showcase

The focused [WidgetShowcase](../src/main/java/valthorne/examples/ui/WidgetShowcase.java)
demonstrates the wheel, Open/Save chooser, menus, dropdowns, and numeric controls.
Use the sibling engine checkout containing these widgets:

```powershell
.\gradlew.bat '-PvalthorneDir=../Valthorne' runWidgetShowcase
```

Drag the wheel to change hue/saturation and use Value and Alpha independently.
The View menu switches between wheel and RGBA sliders. The chooser's operation
dropdown selects Open files, Save file, or Choose folder. Double-click folders,
type a location or filename, change the file filter, and use Ctrl/Shift selection
in Open mode. Save requests Replace confirmation for existing files. Approval only
reports the selected path in this demo; it never writes a file. The chooser now has
a breadcrumb/editable path at the top, an expandable folder tree at the left, and
sortable Name/Date modified rows at the right. Search filters the current directory.
Ctrl+L edits the path, Alt+Left/Right traverses history, and F5 refreshes. **New folder**
creates an actual directory after a name is entered. The modal chooser
button demonstrates Cancel/Escape and focus restoration. Toggle light/dark to compare
both palettes. This showcase starts in the light palette to match a conventional desktop file dialog.

The floating **Window widget** demo opens on launch. Drag its title, resize an edge
or corner, and independently toggle **Dragging** and **Resizing** inside the window.
Edit the title field and press Enter to rename it. The top-right **X** or **Close window** preserves its
state; **Window demo** or View → Show window demo brings it back. Keyboard focus on
the title or a resize border supports arrow keys, with Shift for larger steps.

### NanoVG widget showcase

[NanoWidgetShowcase](../src/main/java/valthorne/examples/ui/NanoWidgetShowcase.java)
provides the same file chooser, menus, color editor, and floating window using
NanoVG controls throughout. It supports the same interactions and light/dark switch.

```sh
./gradlew runNanoWidgetShowcase -PvalthorneDir=../Valthorne
```

In PowerShell, use `./gradlew.bat runNanoWidgetShowcase '-PvalthorneDir=../Valthorne'`.

### Main gallery controls

- Use the scrolling left navigation to choose an element family such as Button/NanoButton, Grid/NanoGrid or DataTable/NanoDataTable.
- **Inspect layout**: toggle live bounds visualization for the retained UI tree.
- **Tab / Shift+Tab**: move keyboard focus. **Enter / Space**: activate the focused control.
- **Arrow keys**: adjust focused sliders and split dividers. **Escape**: dismiss a modal.
- Every page contains live controls for the element's available states, orientations, callbacks, styling and data modes.

## Read the implementation

Start at [UIShowcase.java](../src/main/java/valthorne/examples/ui/UIShowcase.java).
Its Javadoc describes the lifecycle and helper contracts. Generate the full reference
with `./gradlew javadoc`.

1. `init` creates the professional theme, two preview images, Slug font and permanent application shell.
2. `buildNavigation` creates one left-rail destination for each element family.
3. `show` destroys the old feature page and dispatches to the selected element builder.
4. Each `*Page` method explicitly constructs all important modes for that element: states, callbacks, geometry, styling, orientation, selection, filtering or virtualization as applicable.
5. Small helpers at the end contain only repeated presentation conventions such as cards, sizing and sample content.

The source intentionally keeps component configuration visible rather than hiding it behind factories, making each page useful as copyable API documentation.

## Modes and outputs

`--smoke` visits all 18 element pages, captures each rendered state and exits. No benchmark flag is provided.

Smoke captures are written under `build/ui-showcase/` using the page names, including `buttons.png`, `grids.png`, `virtual_lists.png` and `modals.png`. See the checked-in illustration below.

![UI data tools](images/ui.png)

## Extend it

When an element gains a feature, add a visible state or interaction to its page and document the behavior beside it. When adding an entirely new element, add a navigation entry, a page builder and a smoke capture before updating the checked-in screenshot.

Use [architecture and ownership](architecture.md) when extracting code into your
game. Run the affected smoke/validation checks after edits; [verification](verification.md)
explains which checks need a display and which are safe on a headless host.
