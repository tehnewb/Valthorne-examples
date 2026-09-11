// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.ui;

import static org.lwjgl.opengl.GL11.*;

import valthorne.*;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.scene.GameScreen;
import valthorne.scene.Scene;
import valthorne.ui.UINode;
import valthorne.ui.nodes.*;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.ui.theme.ThemeData;

import java.nio.file.Path;

/**
 * An interactive catalog of controls, mixed texture/NanoVG content, virtual lists, advanced
 * workspaces and data tools within one input and layout hierarchy.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>The shared UI root controls capture, focus and page lifetime. Page transitions dispose the old
 * page; themes and preview textures belong to the application. The smoke path visits each page and
 * captures it through the same rendering path as interactive use.
 *
 * <p>Run through {@link valthorne.examples.launcher.ExampleLauncher} for help, validated options
 * and platform checks. Study the accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/ui.md">example walkthrough</a>
 * for controls, code navigation and extension exercises.
 */
public final class UIShowcase extends Scene {
    private final boolean smoke;
    private ProfessionalTheme dark, light;
    private ThemeData darkData, lightData;
    private boolean lightMode;
    private NanoPanel shell, content;
    private NanoLabel status, diagnostics, heading, fpsLabel;
    private VirtualList virtualList;
    private valthorne.graphics.texture.TextureData previewData;
    private valthorne.graphics.texture.Texture previewTexture;
    private int frame;
    private float diagnosticTimer;
    private double fpsElapsed;
    private int fpsFrames;

    /**
     * Records whether to run the gallery interactively or visit every page in a bounded smoke
     * sequence.
     */
    private UIShowcase(boolean smoke) {
        this.smoke = smoke;
    }

    /**
     * Starts this application on the process main thread. Prefer the documented Gradle launcher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        boolean smoke = java.util.Arrays.asList(args).contains("--smoke");
        JGL.init(
                new GameScreen(new UIShowcase(smoke)),
                JGLConfiguration.defaults()
                        .title("Valthorne | UI Showcase")
                        .size(1280, 820)
                        .samples(16)
                        .swapInterval(SwapInterval.OFF)
                        .visible(!smoke));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        camera = null;
        var pixels = org.lwjgl.BufferUtils.createByteBuffer(32 * 32 * 4);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
                pixels.put((byte) (70 + x * 4))
                        .put((byte) (90 + y * 4))
                        .put((byte) 230)
                        .put((byte) 255);
        pixels.flip();
        previewData = new valthorne.graphics.texture.TextureData(pixels, 32, 32);
        previewTexture = new valthorne.graphics.texture.Texture(previewData);
        dark = new ProfessionalTheme(false, 1);
        light = new ProfessionalTheme(true, 1);
        darkData = dark.create();
        lightData = light.create();
        ui.setTheme(darkData);
        shell = new NanoPanel();
        shell.getLayout()
                .absolute()
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100)
                .column()
                .padding(24)
                .gap(18);
        shell.setStyle(NanoPanel.BACKGROUND_COLOR_KEY, dark.surface);
        ui.add(shell);
        var top = new NanoContainer();
        top.getLayout().row().widthPercent(100).height(52).gap(16).itemsCenter();
        var title = label("VALTHORNE / UI LAB", 25);
        title.getLayout().grow();
        top.add(title);
        fpsLabel = label("FPS / warming up", 16);
        fpsLabel.getLayout().width(210).noShrink();
        fpsLabel.setTooltip(
                "Average FPS and frame time over the last half-second, including presentation"
                        + " waits.");
        top.add(fpsLabel);
        top.add(
                button(
                        "Switch theme",
                        () -> {
                            lightMode = !lightMode;
                            ui.setTheme(lightMode ? lightData : darkData);
                            shell.setStyle(
                                    NanoPanel.BACKGROUND_COLOR_KEY,
                                    lightMode ? light.surface : dark.surface);
                        }));
        top.add(
                button(
                        "Inspector",
                        () -> {
                            var inspector = ui.getInspector();
                            inspector.setEnabled(!inspector.isEnabled());
                            inspector.setOutlines(true);
                        }));
        shell.add(top);
        var body = new NanoContainer();
        body.getLayout().row().grow().height(0).widthPercent(100).gap(20).minHeight(0);
        shell.add(body);
        var sidebar = new NanoPanel();
        sidebar.getLayout().width(224).heightPercent(100).noShrink().column().padding(16).gap(12);
        sidebar.add(label("EXPLORE", 13));
        sidebar.add(button("01  Controls", this::controls));
        sidebar.add(button("02  Mixed nesting", this::mixed));
        sidebar.add(button("03  10,000 items", this::largeList));
        sidebar.add(button("04  Workspace", this::advanced));
        sidebar.add(button("05  Data tools", this::dataTools));
        sidebar.add(
                label(
                        "Tab / Shift+Tab: focus\n"
                                + "Enter / Space: activate\n"
                                + "Escape: close modal\n"
                                + "Ctrl+Z / Ctrl+Y: undo / redo",
                        14));
        diagnostics = label("", 13);
        diagnostics.getLayout().widthPercent(100).grow();
        sidebar.add(diagnostics);
        body.add(sidebar);
        var workspace = new NanoContainer();
        workspace.getLayout().width(0).grow().heightPercent(100).column().gap(14).minWidth(0);
        heading = label("", 24);
        workspace.add(heading);
        content = new NanoPanel();
        content.getLayout()
                .grow()
                .height(0)
                .widthPercent(100)
                .minHeight(0)
                .padding(20)
                .column()
                .gap(14);
        workspace.add(content);
        body.add(workspace);
        status = label("One tree. Two renderers. Shared behavior.", 15);
        shell.add(status);
        controls();
        ui.layout();
    }

    /** Disposes the previous page content and starts a new labeled gallery section. */
    private void reset(String title) {
        ui.cancelInput();
        content.clear();
        virtualList = null;
        heading.text(title);
    }

    /**
     * Builds the basic control page, including stateful inputs and examples of callback-driven
     * updates.
     */
    private void controls() {
        reset("Controls / consistent behavior, different skins");
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().widthPercent(100).grow().height(0).minHeight(0);
        var columns = new Panel();
        columns.getLayout().widthPercent(100).row().gap(24).noShrink();
        var regular = new Panel();
        regular.getLayout().widthPercent(47).column().gap(14).noShrink();
        var vector = new NanoContainer();
        vector.getLayout().widthPercent(47).column().gap(14).noShrink();
        columns.add(regular);
        columns.add(vector);
        scroll.setContent(columns);
        content.add(scroll);
        regular.add(label("TEXTURE CONTROLS", 15));
        vector.add(label("NANOVG CONTROLS", 15));
        var first =
                new Button("Texture button").action(b -> status.text("Texture button activated"));
        size(first, 38);
        regular.add(first);
        vector.add(button("Nano button", () -> status.text("Nano button activated")));
        var field = new TextField("Type here, then undo");
        size(field, 40);
        field.getLayout().widthPercent(100);
        regular.add(field);
        var nanoField = new NanoTextField("Unicode, selection, undo");
        size(nanoField, 40);
        nanoField.getLayout().widthPercent(100);
        vector.add(nanoField);
        field.getEditor().validator(value -> !value.isBlank());
        nanoField.getEditor().validator(value -> !value.isBlank());
        var password = new TextField("Protected clipboard").masking(true);
        size(password, 40);
        password.getLayout().widthPercent(100);
        regular.add(password);
        var nanoPassword = new NanoTextField("Protected clipboard").masking(true);
        size(nanoPassword, 40);
        nanoPassword.getLayout().widthPercent(100);
        vector.add(nanoPassword);
        var progress = new ProgressBar(0, 100).progress(50).displayPercentage(true);
        size(progress, 26);
        var nanoProgress = new NanoProgressBar(0, 100).progress(50).displayPercentage(true);
        size(nanoProgress, 26);
        var slider =
                new Slider(0, 100, 50)
                        .stepSize(1)
                        .action(
                                s -> {
                                    progress.progress(s.getValue());
                                    status.text("Texture slider: " + (int) s.getValue());
                                });
        var nanoSlider =
                new NanoSlider(0, 100, 50)
                        .stepSize(1)
                        .action(
                                s -> {
                                    nanoProgress.progress(s.getValue());
                                    status.text("Nano slider: " + (int) s.getValue());
                                });
        size(slider, 32);
        size(nanoSlider, 32);
        slider.getLayout().widthPercent(100);
        nanoSlider.getLayout().widthPercent(100);
        regular.add(slider);
        vector.add(nanoSlider);
        progress.update(1f);
        nanoProgress.update(1f);
        regular.add(progress);
        vector.add(nanoProgress);
        var check = new Checkbox().action(c -> status.text("Texture checkbox: " + c.isChecked()));
        check.getLayout().width(24).height(24);
        var nanoCheck =
                new NanoCheckbox().action(c -> status.text("Nano checkbox: " + c.isChecked()));
        nanoCheck.getLayout().width(24).height(24);
        regular.add(check);
        vector.add(nanoCheck);
        var disabled = new Button("Disabled");
        size(disabled, 38);
        disabled.setEnabled(false);
        regular.add(disabled);
        var nanoDisabled = button("Disabled", () -> {});
        nanoDisabled.setEnabled(false);
        vector.add(nanoDisabled);
        regular.add(button("Texture modal + Nano content", () -> showModal(false)));
        vector.add(button("Nano modal + Texture content", () -> showModal(true)));
        first.setTooltip("A regular button inside a mixed UI tree");
        nanoSlider.setTooltip("Click, drag, scroll, or use arrow keys");
        var choices =
                new NanoComboBox<String>()
                        .items(java.util.List.of("Balanced", "Performance", "Quality"))
                        .onChange(value -> status.text("Selected preset: " + value));
        choices.getLayout().widthPercent(100);
        vector.add(choices);
        regular.add(label("Text, clipboard and range logic\nare independent of rendering.", 14));
        vector.add(label("Use the inspector to see bounds,\nfocus, capture and frame costs.", 14));
    }

    /**
     * Shows a texture or NanoVG modal through the shared root so focus and pointer routing remain
     * consistent.
     */
    private void showModal(boolean nano) {
        var form = new Panel();
        form.getLayout().width(380).column().padding(24).gap(16);
        form.add(label("Focus stays inside this dialog", 21));
        var field = new NanoTextField("Try Tab, Shift+Tab, then Escape");
        size(field, 40);
        form.add(field);
        if (nano) {
            var modal = new NanoModal(content).content(form).closeOnOutsideClick(true);
            form.add(new Button("Close dialog").action(b -> modal.close()));
            modal.open();
        } else {
            var modal = new Modal(content).content(form).closeOnOutsideClick(true);
            form.add(button("Close dialog", modal::close));
            modal.open();
        }
    }

    /** Builds a page mixing texture and NanoVG nodes in one layout and input hierarchy. */
    private void mixed() {
        reset("Mixed nesting / scrolling and clipping");
        content.add(label("Texture scroll panel / Nano panel / texture buttons / Nano labels", 16));
        var previews = new NanoContainer();
        previews.getLayout().row().gap(16).height(56).noShrink();
        var textureGrid = new Grid().columns(2).cellSize(48, 48);
        textureGrid.getLayout().gap(8);
        textureGrid.add(new Image(previewTexture), new NanoImage(previewData));
        var nanoGrid = new NanoGrid().columns(2).cellSize(48, 48);
        nanoGrid.getLayout().gap(8);
        nanoGrid.add(new Image(previewTexture), new NanoImage(previewData));
        previews.add(textureGrid);
        previews.add(nanoGrid);
        previews.add(label("Both grid and image families\nshare the same source pixels.", 14));
        content.add(previews);
        var outer = new ScrollPanel();
        outer.horizontal(false).horizontalBar(false);
        outer.getLayout().widthPercent(100).grow().height(0).minHeight(0);
        var stack = new NanoPanel();
        stack.getLayout().widthPercent(100).column().gap(12).padding(16).noShrink();
        for (int i = 0; i < 18; i++) {
            var row = new Button("Texture row " + (i + 1));
            row.getLayout().height(48).widthPercent(100).noShrink();
            int index = i;
            row.action(b -> status.text("Mixed row " + (index + 1) + " activated"));
            stack.add(row);
            if (i == 2) {
                var nested = new NanoScrollPanel().horizontal(false).horizontalBar(false);
                nested.getLayout().height(140).widthPercent(100).noShrink();
                var items = new Panel();
                items.getLayout().widthPercent(100).column().gap(6).noShrink();
                for (int j = 0; j < 12; j++) {
                    var cell = button("Inner scroll item " + j, () -> {});
                    cell.getLayout().noShrink();
                    items.add(cell);
                }
                nested.setContent(items);
                stack.add(nested);
            }
        }
        outer.setContent(stack);
        content.add(outer);
    }

    /**
     * Builds the virtualized list demonstration, keeping visible row nodes bounded as the dataset
     * grows.
     */
    private void largeList() {
        reset("10,000 items / bounded live widgets");
        content.add(
                label(
                        "Alternating texture and Nano cells. Scroll freely or jump to the final"
                                + " row.",
                        16));
        var commands = new NanoContainer();
        commands.getLayout().row().gap(12).height(38);
        commands.add(button("Jump to end", () -> virtualList.scrollToIndex(9999)));
        commands.add(button("Back to start", () -> virtualList.scrollToIndex(0)));
        content.add(commands);
        virtualList =
                new VirtualList(
                        10000,
                        i ->
                                i % 2 == 0
                                        ? new Button("Texture / " + i)
                                                .action(b -> status.text("Selected item " + i))
                                        : new NanoButton("Nano / " + i)
                                                .action(b -> status.text("Selected item " + i)));
        virtualList.columns(4).rowHeight(44).gap(8);
        virtualList.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        content.add(virtualList);
    }

    /** Builds the advanced workspace controls and editing demonstrations. */
    private void advanced() {
        reset("Workspace / tabs, split panes and measured rows");
        content.add(
                label(
                        "Drag the divider. Use tab-header arrows. Shift/Ctrl-click rows to select.",
                        15));
        var tabs = new TabbedPane();
        tabs.addTab(
                "Documents",
                () -> {
                    var column = new NanoContainer();
                    column.getLayout().column().gap(12).padding(12);
                    column.add(label("Retained document state", 21));
                    var field = new NanoTextField("Edit me, then switch tabs");
                    size(field, 40);
                    column.add(field);
                    column.add(
                            label(
                                    "Inactive tabs keep their contents\n"
                                            + "but do no update or drawing work.",
                                    15));
                    return column;
                });
        tabs.addTab(
                "Settings",
                () -> {
                    var column = new Panel();
                    column.getLayout().column().gap(12).padding(12);
                    column.add(label("Created only when opened", 20));
                    column.add(
                            new Button("Texture action")
                                    .action(b -> status.text("Lazy settings page activated")));
                    var slider = new NanoSlider(0, 100, 50);
                    size(slider, 32);
                    column.add(slider);
                    return column;
                });
        virtualList =
                new VirtualList(
                        10000,
                        i -> {
                            var row =
                                    new NanoButton(
                                            "Record "
                                                    + i
                                                    + (i % 4 == 0 ? " / expanded details" : ""));
                            row.action(
                                    b ->
                                            status.text(
                                                    "Selected "
                                                            + virtualList
                                                                    .getSelection()
                                                                    .selectedCount()
                                                            + " records"));
                            return row;
                        });
        virtualList.rowHeight(40).gap(4).selectable(true).variableHeights();
        for (int i = 0; i < 10000; i += 4) virtualList.itemHeight(i, 72);
        var split =
                new SplitPane(tabs, virtualList).ratio(.52f).minimumSizes(200, 180).dividerSize(10);
        split.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        content.add(split);
    }

    /** Stable sample data row displayed by the gallery's sortable/filterable table. */
    private record AssetRow(int id, String name, String type) {}

    /**
     * Builds the sortable/filterable data tools page with stable row identifiers and inspector
     * feedback.
     */
    private void dataTools() {
        reset("Data tools / searchable table and collapsible sections");
        var table =
                new DataTable<AssetRow>(
                        java.util.List.of(
                                new TableColumn<>(
                                        "ID",
                                        1,
                                        row -> new Label(Integer.toString(row.id())),
                                        java.util.Comparator.comparingInt(AssetRow::id)),
                                TableColumn.text("Name", 3, AssetRow::name),
                                TableColumn.text("Type", 2, AssetRow::type)));
        var data = new java.util.ArrayList<AssetRow>(10000);
        for (int i = 0; i < 10000; i++)
            data.add(
                    new AssetRow(
                            i,
                            "Asset " + i,
                            i % 3 == 0 ? "Texture" : i % 3 == 1 ? "Audio" : "Scene"));
        table.rows(data);
        table.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        var search = new NanoTextField("Search name, type, or ID...");
        search.getLayout().height(38).widthPercent(100).noShrink();
        var count = label("10,000 records / click a header to sort", 14);
        String[] previousQuery = {""};
        search.getEditor()
                .onChange(
                        () -> {
                            String query =
                                    search.getText().strip().toLowerCase(java.util.Locale.ROOT);
                            if (query.equals(previousQuery[0])) return;
                            previousQuery[0] = query;
                            table.filter(
                                    row ->
                                            query.isEmpty()
                                                    || row.name()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                            .contains(query)
                                                    || row.type()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                            .contains(query));
                            count.text(
                                    table.getModel().size() + " records / click a header to sort");
                        });
        table.getSelection()
                .onChange(
                        () ->
                                status.text(
                                        "Selected rows: " + table.getSelection().selectedCount()));
        var help = new NanoContainer();
        help.getLayout().padding(10).column().gap(8);
        help.add(
                label(
                        "Headers cycle ascending / descending / original order.\n"
                                + "Shift-click selects a range; Ctrl-click toggles a row.\n"
                                + "Only visible rows are created. Filtering or sorting clears"
                                + " selection.",
                        14));
        content.add(new CollapsibleSection("Table help", help).expanded(false));
        content.add(search);
        content.add(count);
        content.add(table);
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private static NanoLabel label(String text, float size) {
        var label = new NanoLabel(text);
        label.setStyle(NanoLabel.FONT_SIZE_KEY, size);
        return label;
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private static NanoButton button(String text, Runnable action) {
        var button = new NanoButton(text).action(b -> action.run());
        size(button, 38);
        return button;
    }

    /** Assigns the row height and prevents the layout engine from shrinking the supplied node. */
    private static void size(UINode node, float height) {
        node.getLayout().height(height).noShrink();
    }

    /** Clears the background; the application renders the attached UI root afterward. */
    @Override
    public void draw(TextureBatch batch) {
        Window.clear(lightMode ? light.surface : dark.surface);
    }

    /**
     * Processes input and advances this demo using elapsed seconds; rendering and resource
     * destruction remain in their lifecycle callbacks.
     *
     * @param delta elapsed time in seconds
     */
    @Override
    public void update(float delta) {
        // Keep sampling every frame, independently of the slower diagnostic text refresh.
        // Only format/update the label twice a second during interactive use.
        if (Float.isFinite(delta) && delta > 0) {
            fpsElapsed += delta;
            fpsFrames++;
            if (fpsElapsed >= .5 || smoke) {
                long fps = Math.round(fpsFrames / fpsElapsed);
                double milliseconds = Math.round(fpsElapsed * 10_000 / fpsFrames) / 10.0;
                fpsLabel.text(fps + " FPS / " + milliseconds + " ms");
                fpsElapsed = 0;
                fpsFrames = 0;
            }
        }
        diagnosticTimer += delta;
        if (diagnosticTimer < .3f && !smoke) return;
        diagnosticTimer = 0;
        var stats = ui.getFrameStats();
        String focus =
                ui.getFocused() == null ? "none" : ui.getFocused().getClass().getSimpleName();
        String capture =
                ui.getCaptured() == null ? "none" : ui.getCaptured().getClass().getSimpleName();
        diagnostics.text(
                "FRAME DIAGNOSTICS\n\nNodes: "
                        + stats.nodesDrawn()
                        + "\nLayout passes: "
                        + stats.layoutPasses()
                        + "\nTexture calls: "
                        + stats.textureDrawCalls()
                        + "\nNano flushes: "
                        + stats.nanoFlushes()
                        + "\nBackend switches: "
                        + stats.backendSwitches()
                        + "\n\nFocus: "
                        + focus
                        + "\nCapture: "
                        + capture
                        + (virtualList == null
                                ? ""
                                : "\n\nLive cells: " + virtualList.getLiveItemCount() + " / 10000")
                        + "\n\nInspector: "
                        + (ui.getInspector().isEnabled() ? "on" : "off")
                        + inspectedNode());
    }

    /**
     * Formats bounds and layout diagnostics for the hovered or focused node. Returns an empty
     * string when the inspector is disabled or has no matching entry.
     */
    private String inspectedNode() {
        if (!ui.getInspector().isEnabled()) return "";
        UINode selected = ui.getHovered() != null ? ui.getHovered() : ui.getFocused();
        for (var entry : ui.getInspector().entries())
            if (entry.node() == selected) {
                var b = entry.bounds();
                StringBuilder info =
                        new StringBuilder("\n\n")
                                .append(entry.type())
                                .append("\nBounds: ")
                                .append((int) b.width())
                                .append(" x ")
                                .append((int) b.height())
                                .append("\nAt: ")
                                .append((int) b.x())
                                .append(", ")
                                .append((int) b.y())
                                .append("\nClip: ")
                                .append(
                                        entry.clip() == null
                                                ? "none"
                                                : (int) entry.clip().width()
                                                        + " x "
                                                        + (int) entry.clip().height());
                entry.style().entrySet().stream()
                        .filter(e -> e.getValue() instanceof Color)
                        .limit(3)
                        .forEach(
                                e -> {
                                    Color c = (Color) e.getValue();
                                    String key =
                                            e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
                                    info.append("\n")
                                            .append(key)
                                            .append(":\n  ")
                                            .append(
                                                    String.format(
                                                            "#%02X%02X%02X",
                                                            (int) (c.r() * 255),
                                                            (int) (c.g() * 255),
                                                            (int) (c.b() * 255)));
                                });
                return info.toString();
            }
        return "\nHover a control to inspect.";
    }

    /** Composes the current gallery page and inspection overlays using their shared root. */
    @Override
    protected void drawScene() {
        super.drawScene();
        if (!smoke) return;
        frame++;
        if (frame == 3) {
            capture("controls");
            mixed();
        }
        if (frame == 6) {
            capture("mixed");
            largeList();
        }
        if (frame == 9) {
            capture("virtual-grid");
            controls();
            lightMode = true;
            ui.setTheme(lightData);
            shell.setStyle(NanoPanel.BACKGROUND_COLOR_KEY, light.surface);
        }
        if (frame == 12) {
            capture("light");
            showModal(true);
        }
        if (frame == 15) {
            capture("modal");
            JGL.publish(new valthorne.event.events.KeyPressEvent(Keyboard.ESCAPE, 0));
            advanced();
        }
        if (frame == 18) {
            capture("workspace");
            dataTools();
        }
        if (frame == 21) {
            capture("data-tools");
            Window.requestClose();
        }
    }

    /**
     * Captures the current demo frame or state for its documented workflow; capture-specific
     * overloads choose the output name.
     */
    private void capture(String name) {
        FrameCapture.save(Path.of("build/ui-showcase", name + ".png"));
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        disposeScene();
        if (previewTexture != null) {
            previewTexture.dispose();
            previewTexture = null;
        }
        if (dark != null) dark.close();
        if (light != null) light.close();
    }
}
