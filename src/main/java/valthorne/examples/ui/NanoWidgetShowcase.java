// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.ui.theme.ThemeData;

/**
 * Interactive showcase for file choosers, menus, and wheel/slider color editing.
 * Open/Save selections report their paths without modifying files; commands report their
 * results in a status label. Theme resources outlive the root that borrows them.
 * Run the examples project's runNanoWidgetShowcase task against the sibling engine.
 * @author Albert Beaupre
 */
public final class NanoWidgetShowcase implements Application {
    private UIRoot root; // Owned UI root and shared rendering contexts.
    private ProfessionalTheme dark, light; // Owned palettes, fonts and control skins.
    private ThemeData darkData, lightData; // Reusable resolved theme definitions.
    private NanoLabel status; // Owned callback feedback label.
    private NanoColorPicker picker; // Owned live RGBA editor.
    private NanoFileChooser chooser; // Embedded Open/Save chooser with real filesystem validation.
    private valthorne.ui.nodes.nano.NanoWindow demoWindow; // Floating window for testing movement, resizing, and title editing.
    private boolean lightMode = true; // Starts with the reference chooser's light desktop palette.

    /**
     * Opens a visible desktop window and enters the normal engine lifecycle.
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        JGL.init(new NanoWidgetShowcase(), JGLConfiguration.defaults()
                .title("Valthorne | Nano Widgets").size(1100, 720));
    }

    /**
     * Builds interactive controls after the graphics context becomes available.
     * Filesystem initialization errors are displayed without terminating the showcase.
     */
    @Override public void init() {
        root = new UIRoot();
        dark = new ProfessionalTheme(false, 1);
        light = new ProfessionalTheme(true, 1);
        darkData = dark.create(); lightData = light.create(); root.setTheme(lightData);
        NanoPanel background = new NanoPanel(); background.setStyleName("surface");
        background.getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
        root.add(background);
        status = place(new NanoLabel("Browse with the tree or path bar. Open/Save reports paths; New folder creates a directory."), 24, 680, 1030, 28);
        NanoMenuBar menus = place(new NanoMenuBar(), 24, 16, 1050, 36);
        menus.addMenu("File", List.of(
                new NanoPopupMenu.Item("Example command", () -> status.text("Menu command activated"), true),
                new NanoPopupMenu.Item("Disabled command", () -> {}, false),
                new NanoPopupMenu.Item("Close showcase", Window::requestClose, true)));
        menus.addMenu("View", List.of(new NanoPopupMenu.Item("Toggle light / dark", this::toggleTheme, true),
                new NanoPopupMenu.Item("Show window demo", this::showWindowDemo, true),
                new NanoPopupMenu.Item("Color wheel", () -> picker.mode(NanoColorPicker.Mode.WHEEL), true),
                new NanoPopupMenu.Item("RGBA sliders", () -> picker.mode(NanoColorPicker.Mode.SLIDERS), true)));
        root.add(menus, place(new NanoLabel("COLOR PICKER"), 24, 78, 350, 24));
        picker = place(new NanoColorPicker().mode(NanoColorPicker.Mode.WHEEL).color(new Color(0xAA6688CC))
                .onChange(color -> status.text("Color changed to " + color.toHex())), 24, 110, 360, 360);
        NanoComboBox<String> combo = place(new NanoComboBox<String>().items(List.of("Low", "Medium", "High", "Ultra"))
                .selectedIndex(2).onChange(value -> status.text("Quality: " + value)), 24, 492, 172, 36);
        NanoNumberSpinner spinner = place(new NanoNumberSpinner(0, 100, .5, 12.5)
                .onChange(value -> status.text("Number: " + value)), 204, 492, 180, 36);
        NanoRadioGroup radio = place(new NanoRadioGroup(List.of("Windowed", "Borderless", "Fullscreen"))
                .onChange(index -> status.text("Example mode selection: " + index)), 24, 564, 360, 96);
        root.add(picker, place(new NanoLabel("QUALITY"), 24, 470, 170, 22), combo,
                place(new NanoLabel("NUMBER"), 204, 470, 180, 22), spinner,
                place(new NanoLabel("RADIO GROUP"), 24, 538, 350, 24), radio);
        root.add(place(new NanoLabel("FILE CHOOSER / double-click folders to navigate"), 424, 64, 640, 24));
        try {
            chooser = place(new NanoFileChooser(Path.of(".")).multipleSelection(true)
                    .onApprove(paths -> status.text("Approved " + paths.size() + " item(s): " + paths.getFirst().getFileName()))
                    .onCancel(() -> status.text("File selection cancelled")), 424, 132, 650, 528);
            chooser.filters(List.of(new NanoFileChooser.Filter("All files", List.of()),
                    new NanoFileChooser.Filter("Java source (*.java)", List.of("java")),
                    new NanoFileChooser.Filter("JSON (*.json)", List.of("json"))));
            root.add(chooser);
            NanoComboBox<String> operation = place(new NanoComboBox<String>().items(List.of("Open files", "Save file", "Choose folder"))
                    .selectedIndex(0).onChange(value -> {
                        chooser.selectionMode(value.equals("Choose folder") ? NanoFileChooser.SelectionMode.DIRECTORIES : NanoFileChooser.SelectionMode.FILES);
                        chooser.mode(value.equals("Save file") ? NanoFileChooser.Mode.SAVE : NanoFileChooser.Mode.OPEN);
                        status.text(value + ": approval reports the selection without writing files");
                    }), 424, 92, 220, 36);
            NanoButton dialog = place(new NanoButton("Open modal chooser").action(button -> openDialog(button)), 660, 92, 240, 36);
            root.add(operation, dialog);
        } catch (IOException failure) { status.text("Cannot open browser: " + failure.getMessage()); }
        NanoButton theme = place(new NanoButton("Toggle light / dark").action(button -> toggleTheme()), 824, 16, 250, 36);
        NanoButton windows = place(new NanoButton("Window demo").action(button -> showWindowDemo()), 916, 92, 158, 36);
        root.add(theme, windows, status); root.layout(); showWindowDemo();
    }

    /**
     * Opens or raises a floating window with independently switchable drag/resize policies.
     * The title editor commits on Enter; hiding retains state for the next reopening.
     */
    private void showWindowDemo() {
        if (demoWindow == null) {
            demoWindow = new valthorne.ui.nodes.nano.NanoWindow("Window widget")
                    .bounds(64, 140, 350, 320).minimumSize(280, 260).maximumSize(700, 550)
                    .onClose(() -> status.text("Window closed. Use Window demo to reopen it."))
                    .onChange(frame -> status.text(String.format("Window: %.0f, %.0f / %.0f x %.0f", frame.x(), frame.y(), frame.width(), frame.height())));
            NanoPanel content = demoWindow.getContentPane(); content.getLayout().gap(8);
            NanoLabel help = new NanoLabel("Drag the title. Resize an edge or corner.");
            NanoTextField title = new NanoTextField("Window title").text("Window widget").action(field -> demoWindow.title(field.getText()));
            title.getLayout().height(36).widthPercent(100);
            NanoButton dragging = new NanoButton("Dragging: on").action(button -> {
                demoWindow.draggable(!demoWindow.isDraggable()); button.text("Dragging: " + (demoWindow.isDraggable() ? "on" : "off"));
            });
            NanoButton resizing = new NanoButton("Resizing: on").action(button -> {
                demoWindow.resizable(!demoWindow.isResizable()); button.text("Resizing: " + (demoWindow.isResizable() ? "on" : "off"));
            });
            NanoButton hide = new NanoButton("Close window").action(button -> demoWindow.close());
            for (NanoButton button : List.of(dragging, resizing, hide)) button.getLayout().height(36).widthPercent(100);
            content.add(help, title, dragging, resizing, hide); root.add(demoWindow);
        }
        demoWindow.open();
    }

    /**
     * Opens a fresh nonblocking modal so focus restoration and Cancel can be tried.
     * @param owner attached button that receives focus when the chooser closes
     */
    private void openDialog(NanoButton owner) {
        try {
            new NanoFileChooser(chooser.getExplorer().getDirectory())
                    .onApprove(paths -> status.text("NanoModal selected: " + paths.getFirst().getFileName()))
                    .onCancel(() -> status.text("NanoModal cancelled"))
                    .showDialog(owner);
        } catch (IOException failure) { status.text("Cannot open chooser: " + failure.getMessage()); }
    }

    /**
     * Changes palettes without replacing controls or discarding their current values.
     */
    private void toggleTheme() {
        lightMode = !lightMode; root.setTheme(lightMode ? lightData : darkData);
        status.text(lightMode ? "Light theme" : "Dark theme");
    }

    /**
     * Configures absolute top-left placement for this demonstration's fixed layout.
     * @param node node to position
     * @param x left coordinate
     * @param y top coordinate
     * @param width requested width
     * @param height requested height
     * @param <T> concrete node type
     * @return supplied node
     */
    private static <T extends UINode> T place(T node, float x, float y, float width, float height) {
        node.getLayout().absolute().left(x).top(y).width(width).height(height).noGrow().noShrink();
        return node;
    }

    /**
     * Advances control updates using elapsed seconds while native input routes through the root.
     * @param delta elapsed seconds
     */
    @Override public void update(float delta) { root.update(delta); }

    /**
     * Clears and paints one complete mixed-renderer UI frame.
     */
    @Override public void render() { Window.clear(lightMode ? light.surface : dark.surface); root.draw(); }

    /**
     * Releases the hierarchy before the shared fonts and skins it borrows.
     */
    @Override public void dispose() {
        if (root != null) root.dispose();
        if (dark != null) dark.close();
        if (light != null) light.close();
    }
}
