// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.ui;

import org.lwjgl.BufferUtils;

import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.SwapInterval;
import valthorne.Window;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.scene.GameScreen;
import valthorne.scene.Scene;
import valthorne.ui.UINode;
import valthorne.ui.enums.Alignment;
import valthorne.ui.nodes.*;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Element-by-element reference for Valthorne UI. The left rail selects one component family; the
 * workspace then presents every meaningful visual state, interaction mode, orientation, data mode,
 * and styling option exposed by that family in Valthorne 2.3.0.
 *
 * <p>Texture and NanoVG counterparts appear together because they share behavior while using
 * different renderers. Elements without a counterpart receive their own section. The application
 * owns its theme, preview images, texture, and Slug font and releases them after destroying the UI.
 */
public final class UIShowcase extends Scene {
    /** Selectable reference pages shown in the left navigation rail. */
    private enum Page {
        BUTTONS("Button / NanoButton"),
        CHECKBOXES("Checkbox / NanoCheckbox"),
        TEXT_FIELDS("TextField / NanoTextField"),
        SLIDERS("Slider / NanoSlider"),
        PROGRESS("ProgressBar / NanoProgressBar"),
        LABELS("Label / NanoLabel / SlugLabel"),
        IMAGES("Image / NanoImage"),
        PANELS("Panel / NanoPanel / Container"),
        GRIDS("Grid / NanoGrid"),
        SCROLLING("ScrollPanel / NanoScrollPanel"),
        COLLAPSIBLE("CollapsibleSection"),
        TABS("TabbedPane / NanoTabbedPane"),
        SPLITS("SplitPane / NanoSplitPane"),
        VIRTUAL_LISTS("VirtualList / NanoVirtualList"),
        TABLES("DataTable / NanoDataTable"),
        MODALS("Modal / NanoModal"),
        TOOLTIPS("Tooltip / NanoTooltip / Hyperlink"),
        COMBO_BOX("NanoComboBox");

        private final String title;

        /** Stores the human-readable navigation label. */
        Page(String title) {
            this.title = title;
        }
    }

    private static final Color BACKGROUND = new Color(0xFF0C111A);
    private static final Color SURFACE = new Color(0xFF141E2C);
    private static final Color CARD = new Color(0xFF1A2637);
    private static final Color ACCENT = new Color(0xFF75A7FF);
    private static final Color CYAN = new Color(0xFF54D6C3);
    private static final Color MUTED = new Color(0xFF9BAAC0);
    private static final Color DANGER = new Color(0xFFF37C91);

    private final boolean smoke;
    private ProfessionalTheme theme;
    private TextureData previewData;
    private TextureData alternateData;
    private Texture previewTexture;
    private Texture alternateTexture;
    private SlugFont slugFont;
    private NanoPanel rootPanel;
    private NanoPanel workspace;
    private NanoLabel heading;
    private NanoLabel description;
    private NanoLabel status;
    private Page currentPage = Page.BUTTONS;
    private int smokeFrame;

    /** Selects interactive or bounded smoke operation. */
    private UIShowcase(boolean smoke) {
        this.smoke = smoke;
    }

    /** Starts the component reference on the process main thread. */
    public static void main(String[] args) {
        boolean smoke = Arrays.asList(args).contains("--smoke");
        JGL.init(
                new GameScreen(new UIShowcase(smoke)),
                JGLConfiguration.defaults()
                        .title("Valthorne 2.2 | UI Element Reference")
                        .size(1500, 920)
                        .samples(8)
                        .swapInterval(SwapInterval.VSYNC)
                        .visible(!smoke));
    }

    /** Creates resources and builds the persistent application shell. */
    @Override
    public void init() {
        camera = null;
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        previewData = imageData(0);
        alternateData = imageData(1);
        previewTexture = new Texture(previewData);
        alternateTexture = new Texture(alternateData);
        slugFont = loadSlugFont();
        buildShell();
        show(Page.BUTTONS);
        ui.layout();
    }

    /** Produces one of two deterministic gradient images used by image examples. */
    private static TextureData imageData(int variant) {
        int size = 112;
        var pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float distance = (float) Math.hypot(x - 56, y - 48) / 80f;
                float glow = 1 - Math.min(1, distance);
                int red = variant == 0 ? (int) (30 + glow * 75) : (int) (90 + glow * 120);
                int green = variant == 0 ? (int) (80 + glow * 95) : (int) (35 + glow * 70);
                int blue = variant == 0 ? (int) (145 + glow * 105) : (int) (105 + glow * 100);
                pixels.put((byte) red).put((byte) green).put((byte) blue).put((byte) 255);
            }
        }
        pixels.flip();
        return new TextureData(pixels, size, size);
    }

    /** Loads the bundled professional-theme font for cached and curve-rendered Slug labels. */
    private static SlugFont loadSlugFont() {
        try (var stream =
                ProfessionalTheme.class.getResourceAsStream(
                        "/ui/AtkinsonHyperlegible-Regular.ttf")) {
            if (stream == null) throw new IllegalStateException("Bundled UI font is missing");
            return SlugFont.load(stream.readAllBytes(), 32, 95);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the bundled Slug font", exception);
        }
    }

    /** Builds the header, scrolling navigation rail, workspace, and status bar. */
    private void buildShell() {
        rootPanel = panel(BACKGROUND);
        rootPanel
                .getLayout()
                .absolute()
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100)
                .column()
                .padding(22)
                .gap(16);
        ui.add(rootPanel);

        var header = new NanoContainer();
        header.getLayout().row().height(62).widthPercent(100).itemsCenter().gap(16).noShrink();
        var brand = label("VALTHORNE / UI", 24, ACCENT);
        brand.getLayout().width(245).noShrink();
        header.add(brand);
        var copy = new NanoContainer();
        copy.getLayout().column().grow().gap(2);
        copy.add(label("ELEMENT REFERENCE", 17, theme.text));
        copy.add(label("Choose one element. Explore its complete public feature set.", 13, MUTED));
        header.add(copy);
        var inspectorButton = button("Toggle inspector", this::toggleInspector);
        inspectorButton.getLayout().width(170).noShrink();
        header.add(inspectorButton);
        rootPanel.add(header);

        var body = new NanoContainer();
        body.getLayout().row().grow().height(0).widthPercent(100).minHeight(0).gap(18);
        rootPanel.add(body);
        body.add(buildNavigation());

        var main = new NanoContainer();
        main.getLayout().column().grow().width(0).heightPercent(100).minWidth(0).gap(8);
        heading = label("", 26, theme.text);
        description = label("", 14, MUTED);
        main.add(heading);
        main.add(description);
        workspace = panel(SURFACE);
        workspace.getLayout().column().grow().height(0).widthPercent(100).minHeight(0).padding(16);
        main.add(workspace);
        body.add(main);

        status = label("Ready", 13, MUTED);
        rootPanel.add(status);
    }

    /** Creates a scrollable button for every reference page. */
    private NanoPanel buildNavigation() {
        var rail = panel(SURFACE);
        rail.getLayout().width(310).heightPercent(100).column().padding(12).gap(10).noShrink();
        rail.add(label("UI ELEMENTS", 12, MUTED));
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        var items = new NanoContainer();
        items.getLayout().column().gap(6).widthPercent(100).noShrink();
        for (Page page : Page.values()) {
            var item = button(page.title, () -> show(page));
            item.getLayout().widthPercent(100);
            items.add(item);
        }
        scroll.setContent(items);
        rail.add(scroll);
        rail.add(
                label(
                        "Tab focuses · Enter activates\nArrow keys adjust · Esc dismisses",
                        12,
                        MUTED));
        return rail;
    }

    /** Toggles live layout outlines without changing normal input routing. */
    private void toggleInspector() {
        var inspector = ui.getInspector();
        inspector.setEnabled(!inspector.isEnabled());
        inspector.setOutlines(true);
        status.text("Inspector " + (inspector.isEnabled() ? "enabled" : "disabled"));
    }

    /** Clears the old example and constructs the selected element reference. */
    private void show(Page page) {
        ui.cancelInput();
        currentPage = page;
        workspace.clear();
        heading.text(page.title);
        description.text(pageDescription(page));
        switch (page) {
            case BUTTONS -> buttonsPage();
            case CHECKBOXES -> checkboxesPage();
            case TEXT_FIELDS -> textFieldsPage();
            case SLIDERS -> slidersPage();
            case PROGRESS -> progressPage();
            case LABELS -> labelsPage();
            case IMAGES -> imagesPage();
            case PANELS -> panelsPage();
            case GRIDS -> gridsPage();
            case SCROLLING -> scrollingPage();
            case COLLAPSIBLE -> collapsiblePage();
            case TABS -> tabsPage();
            case SPLITS -> splitsPage();
            case VIRTUAL_LISTS -> virtualListsPage();
            case TABLES -> tablesPage();
            case MODALS -> modalsPage();
            case TOOLTIPS -> tooltipsPage();
            case COMBO_BOX -> comboBoxPage();
        }
        status.text(page.title + " · every example is interactive");
    }

    /** Returns the concise purpose statement displayed under each page title. */
    private static String pageDescription(Page page) {
        return switch (page) {
            case BUTTONS -> "Activation, text changes, interaction states, sizing and Nano styling";
            case CHECKBOXES ->
                    "Checked state, toggling, callbacks, focus, disabled state and visual tuning";
            case TEXT_FIELDS ->
                    "Placeholder, value, submission, validation, masking, caret and edit history";
            case SLIDERS ->
                    "Ranges, values, percentages, steps, orientation, keyboard input and styling";
            case PROGRESS ->
                    "Horizontal and vertical progress, percentages, animation and completion";
            case LABELS ->
                    "Raster, NanoVG and Slug typography, alignment, selection and live curves";
            case IMAGES -> "Texture and NanoVG images, replacement, sizing and tint";
            case PANELS -> "Containers, nested layout, surfaces, borders, radii and state colors";
            case GRIDS -> "Column counts, fixed cells, independent gaps and mixed child renderers";
            case SCROLLING -> "Axes, bars, speed, programmatic position and pointer scrolling";
            case COLLAPSIBLE -> "Expanded state, retained content and change notification";
            case TABS -> "Lazy pages, selection, retained state, change events and removal";
            case SPLITS -> "Horizontal and vertical division, ratios, limits and keyboard dragging";
            case VIRTUAL_LISTS ->
                    "Large data, columns, selection, overscan, variable heights and jumping";
            case TABLES ->
                    "Columns, weighted cells, sorting, filtering, selection and virtualization";
            case MODALS -> "Open, close, toggle, outside click, Escape and mixed-renderer content";
            case TOOLTIPS ->
                    "Texture and Nano tooltips plus hyperlink destination and visited state";
            case COMBO_BOX ->
                    "Items, selection, formatting, callbacks, keyboard control and popup state";
        };
    }

    /** Demonstrates all button behavior and Nano appearance controls. */
    private void buttonsPage() {
        var grid = pageGrid();
        var behavior = card("BEHAVIOR", "Activation, text mutation and enabled state");
        var texture = new Button("Texture action");
        texture.action(
                value -> {
                    value.text("Activated");
                    status.text("Button callback received its Button instance");
                });
        control(texture);
        behavior.add(texture);
        behavior.add(button("Nano action", () -> status.text("NanoButton activated")));
        var disabledTexture = new Button("Disabled texture button");
        disabledTexture.setEnabled(false);
        control(disabledTexture);
        behavior.add(disabledTexture);
        var disabledNano = new NanoButton("Disabled Nano button");
        disabledNano.setEnabled(false);
        control(disabledNano);
        behavior.add(disabledNano);
        grid.add(behavior);

        var sizing = card("SIZING & TYPOGRAPHY", "Font, padding and explicit dimensions");
        sizing.add(new NanoButton("Compact").fontSize(12).paddingX(8).paddingY(4).cornerRadius(3));
        var large = new NanoButton("Large touch target").fontSize(20).paddingX(22).paddingY(14);
        large.getLayout().height(58).widthPercent(100);
        sizing.add(large);
        var narrow = new Button("Explicit 180 × 36");
        narrow.getLayout().width(180).height(36);
        sizing.add(narrow);
        grid.add(sizing);

        var styling = card("NANO APPEARANCE", "All state colors, border width and radius");
        var styled =
                new NanoButton("Custom state palette")
                        .backgroundColor(new Color(0xFF23395B))
                        .hoverBackgroundColor(new Color(0xFF31558A))
                        .focusedBackgroundColor(new Color(0xFF23395B))
                        .pressedBackgroundColor(new Color(0xFF18304E))
                        .disabledBackgroundColor(new Color(0xFF242A34))
                        .borderColor(ACCENT)
                        .hoverBorderColor(CYAN)
                        .focusedBorderColor(CYAN)
                        .pressedBorderColor(ACCENT)
                        .disabledBorderColor(MUTED)
                        .textColor(theme.text)
                        .hoverTextColor(Color.WHITE)
                        .focusedTextColor(Color.WHITE)
                        .pressedTextColor(CYAN)
                        .disabledTextColor(MUTED)
                        .borderWidth(2)
                        .cornerRadius(14);
        control(styled);
        styling.add(styled);
        styling.add(label("Hover, focus and press to inspect every configured state.", 14, MUTED));
        grid.add(styling);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates checkbox state, callbacks, input modes and Nano styling. */
    private void checkboxesPage() {
        var grid = pageGrid();
        var states = card("STATE", "Unchecked, checked, toggled and disabled");
        states.add(checkRow("Texture unchecked", new Checkbox()));
        states.add(checkRow("Texture checked", new Checkbox().checked(true)));
        states.add(checkRow("Nano unchecked", new NanoCheckbox()));
        states.add(checkRow("Nano checked", new NanoCheckbox().checked(true)));
        var disabled = new NanoCheckbox().checked(true);
        disabled.setEnabled(false);
        states.add(checkRow("Disabled", disabled));
        grid.add(states);

        var callbacks = card("ACTIONS", "Callback state and programmatic toggle");
        var texture =
                new Checkbox()
                        .action(value -> status.text("Texture checked: " + value.isChecked()));
        callbacks.add(checkRow("Reports every texture change", texture));
        var nano =
                new NanoCheckbox()
                        .action(value -> status.text("Nano checked: " + value.isChecked()));
        callbacks.add(checkRow("Reports every Nano change", nano));
        callbacks.add(
                button(
                        "Toggle both",
                        () -> {
                            texture.toggle();
                            nano.toggle();
                        }));
        grid.add(callbacks);

        var style = card("NANO APPEARANCE", "Box, checkmark, outline, scale and thickness");
        var styled =
                new NanoCheckbox()
                        .checked(true)
                        .backgroundColor(new Color(0xFF152238))
                        .hoverBackgroundColor(new Color(0xFF213A5E))
                        .focusedBackgroundColor(new Color(0xFF152238))
                        .borderColor(ACCENT)
                        .hoverBorderColor(CYAN)
                        .focusedBorderColor(CYAN)
                        .checkmarkColor(CYAN)
                        .cornerRadius(7)
                        .borderWidth(2)
                        .checkmarkScale(.72f)
                        .checkmarkThickness(2.5f);
        styled.getLayout().width(42).height(42);
        style.add(checkRow("Custom 42-pixel control", styled));
        grid.add(style);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates text entry, validation, submission, masking, and Nano visual properties. */
    private void textFieldsPage() {
        var grid = pageGrid();
        var editing = card("EDITING", "Placeholder, initial text, submission and validation");
        var empty = new TextField("Texture placeholder");
        control(empty);
        editing.add(empty);
        var initial = new NanoTextField("Placeholder").text("Editable Nano text").caretIndex(8);
        initial.action(field -> status.text("Submitted: " + field.getText()));
        control(initial);
        editing.add(initial);
        var validated = new TextField("Required value");
        validated.getEditor().validator(value -> !value.isBlank() && value.length() <= 24);
        control(validated);
        editing.add(validated);
        editing.add(label("Ctrl+Z / Ctrl+Y undo and redo. Drag selects text.", 13, MUTED));
        grid.add(editing);

        var security = card("MASKING", "Password fields and custom mask character");
        var texturePassword =
                new TextField("Texture password").text("secret").masking(true).maskChar('•');
        control(texturePassword);
        security.add(texturePassword);
        var nanoPassword =
                new NanoTextField("Nano password").text("private").masking(true).maskChar('◆');
        control(nanoPassword);
        security.add(nanoPassword);
        security.add(label("Masked fields protect clipboard extraction.", 13, MUTED));
        grid.add(security);

        var appearance = card("NANO APPEARANCE", "Font, padding, backgrounds, caret and selection");
        var styled =
                new NanoTextField("Custom visual states")
                        .fontSize(17)
                        .padding(13)
                        .cornerRadius(12)
                        .borderWidth(2)
                        .backgroundColor(new Color(0xFF111D2C))
                        .hoverBackgroundColor(new Color(0xFF182A40))
                        .focusedBackgroundColor(new Color(0xFF111D2C))
                        .borderColor(theme.border)
                        .hoverBorderColor(ACCENT)
                        .focusedBorderColor(CYAN)
                        .textColor(theme.text)
                        .placeholderColor(MUTED)
                        .caretColor(CYAN)
                        .selectionColor(new Color(0x8854D6C3));
        control(styled);
        appearance.add(styled);
        grid.add(appearance);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates slider ranges, step sizes, orientation, geometry, and Nano colors. */
    private void slidersPage() {
        var grid = pageGrid();
        var horizontal = card("HORIZONTAL", "Range, value, percent, steps, wheel and keys");
        var texture = new Slider(-50, 50, 0).stepSize(5);
        texture.action(value -> status.text("Texture value: " + value.getValue()));
        slider(texture);
        horizontal.add(texture);
        var nano = new NanoSlider(0, 1, .35f).stepSize(.05f);
        nano.action(
                value ->
                        status.text("Nano percent: " + Math.round(value.getPercent() * 100) + "%"));
        slider(nano);
        horizontal.add(nano);
        horizontal.add(
                button(
                        "Increment / decrement",
                        () -> {
                            texture.increment();
                            nano.decrement();
                        }));
        grid.add(horizontal);

        var vertical = card("VERTICAL", "Orientation and custom texture thumb geometry");
        var verticalRow = new NanoContainer();
        verticalRow.getLayout().row().height(190).gap(36).noShrink();
        var textureVertical =
                new Slider(0, 100, 68)
                        .vertical(true)
                        .trackHeight(10)
                        .thumbSize(28, 20)
                        .thumbOffsetY(2);
        textureVertical.getLayout().width(42).height(180);
        var nanoVertical = new NanoSlider(0, 100, 42).vertical(true);
        nanoVertical.getLayout().width(42).height(180);
        verticalRow.add(textureVertical);
        verticalRow.add(nanoVertical);
        vertical.add(verticalRow);
        grid.add(vertical);

        var style = card("NANO APPEARANCE", "Track, fill and thumb colors for every state");
        var styled =
                new NanoSlider(0, 100, 58)
                        .trackColor(new Color(0xFF0B1420))
                        .hoverTrackColor(new Color(0xFF14243A))
                        .focusedTrackColor(new Color(0xFF14243A))
                        .disabledTrackColor(new Color(0xFF202733))
                        .fillColor(ACCENT)
                        .hoverFillColor(CYAN)
                        .focusedFillColor(CYAN)
                        .disabledFillColor(MUTED)
                        .thumbColor(theme.text)
                        .hoverThumbColor(CYAN)
                        .focusedThumbColor(CYAN)
                        .pressedThumbColor(ACCENT)
                        .disabledThumbColor(MUTED);
        slider(styled);
        style.add(styled);
        grid.add(style);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates progress orientation, labels, animation, completion, and styling. */
    private void progressPage() {
        var grid = pageGrid();
        var modes = card("MODES", "Percentage labels, raw bars and orientation");
        var texture = new ProgressBar(0, 100).progress(64).displayPercentage(true);
        controlHeight(texture, 28);
        modes.add(texture);
        var noLabel = new ProgressBar(0, 1).progress(.4f).displayPercentage(false);
        controlHeight(noLabel, 18);
        modes.add(noLabel);
        var nano = new NanoProgressBar(0, 100).progress(78).displayPercentage(true);
        controlHeight(nano, 28);
        modes.add(nano);
        var vertical = new NanoProgressBar(0, 100).progress(55).vertical(true);
        vertical.getLayout().width(42).height(150);
        modes.add(vertical);
        grid.add(modes);

        var animation = card("ANIMATION", "Displayed value, duration, callback and completion");
        var animated =
                new NanoProgressBar(0, 100)
                        .progress(25)
                        .animationDuration(.65f)
                        .displayPercentage(true)
                        .onProgress(value -> status.text("Displayed progress changed"));
        controlHeight(animated, 30);
        animation.add(animated);
        animation.add(button("25%", () -> animated.progress(25)));
        animation.add(button("100%", () -> animated.progress(100)));
        animation.add(label("isFinished() becomes true at the maximum.", 13, MUTED));
        grid.add(animation);

        var style = card("NANO APPEARANCE", "Surface, fill, border, text and geometry");
        var styled =
                new NanoProgressBar(0, 100)
                        .progress(67)
                        .displayPercentage(true)
                        .backgroundColor(new Color(0xFF0C1622))
                        .foregroundColor(CYAN)
                        .borderColor(ACCENT)
                        .textColor(theme.text)
                        .fontSize(14)
                        .cornerRadius(12)
                        .borderWidth(2)
                        .textPaddingX(10);
        controlHeight(styled, 32);
        style.add(styled);
        grid.add(style);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates raster, NanoVG, selectable, multiline, aligned, cached, and live Slug text. */
    private void labelsPage() {
        var grid = pageGrid();
        var raster = card("LABEL", "Theme font, color, multiline text and alignment");
        raster.add(
                new Label("Start aligned")
                        .font(theme.getFont())
                        .color(theme.text)
                        .alignment(Alignment.START));
        raster.add(
                new Label("Centered label")
                        .font(theme.getFont())
                        .color(ACCENT)
                        .alignment(Alignment.CENTER));
        raster.add(
                new Label("End aligned\nwith two lines")
                        .font(theme.getFont())
                        .color(CYAN)
                        .alignment(Alignment.END));
        grid.add(raster);

        var vector = card("NANO LABEL", "Font, color, tabs, line spacing and selection");
        vector.add(new NanoLabel("Small muted label").fontSize(13).color(MUTED));
        vector.add(new NanoLabel("Large vector heading").fontSize(28).color(ACCENT));
        vector.add(
                new NanoLabel("Selectable text\nwith custom line spacing")
                        .fontSize(17)
                        .lineSpacing(1.35f)
                        .tabSize(4)
                        .color(theme.text)
                        .selectable(true));
        grid.add(vector);

        var slug = card("SLUG LABEL", "Size, color, cached atlas and live curve rendering");
        slug.add(new SlugLabel(slugFont, "Cached Slug text", 24).color(ACCENT));
        slug.add(new SlugLabel(slugFont, "Live curves", 42).color(theme.text).liveCurves(true));
        slug.add(label("Use live curves for unusually large or transformed text.", 13, MUTED));
        grid.add(slug);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates texture images, Nano images, tint, dimensions, and source replacement. */
    private void imagesPage() {
        var grid = pageGrid();
        var texture = card("IMAGE", "Texture source, dimensions, tint and replacement");
        var image = new Image(previewTexture);
        image.getLayout().width(160).height(160);
        texture.add(image);
        texture.add(
                button(
                        "Swap texture",
                        () ->
                                image.texture(
                                        image.getTexture() == previewTexture
                                                ? alternateTexture
                                                : previewTexture)));
        var tinted = new Image(previewTexture).color(new Color(0x99FFFFFF));
        tinted.getLayout().width(84).height(84);
        texture.add(tinted);
        grid.add(texture);

        var nano = card("NANO IMAGE", "TextureData source, dimensions and replacement");
        var nanoImage = new NanoImage(previewData);
        nanoImage.getLayout().width(160).height(160);
        nano.add(nanoImage);
        nano.add(
                button(
                        "Swap TextureData",
                        () ->
                                nanoImage.texture(
                                        nanoImage.getTexture() == previewData
                                                ? alternateData
                                                : previewData)));
        grid.add(nano);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates regular, NanoVG, and layout-only containers plus state-aware surfaces. */
    private void panelsPage() {
        var grid = pageGrid();
        var regular = card("PANEL", "Texture surface and nested row/column layout");
        var panel = new Panel();
        panel.setStyleName("surface");
        panel.getLayout().column().padding(16).gap(8).height(160).widthPercent(100);
        panel.add(new Button("Nested texture control"));
        panel.add(new NanoButton("Nested Nano control"));
        regular.add(panel);
        grid.add(regular);

        var containers =
                card("CONTAINERS", "UIContainer behavior through mixed renderer containers");
        var plain = new NanoContainer();
        plain.getLayout().row().padding(12).gap(8).height(80).widthPercent(100);
        plain.add(new Button("A"), new NanoButton("B"), new Button("C"));
        containers.add(plain);
        containers.add(
                label(
                        "NanoContainer draws no surface; it only groups and lays out children.",
                        13,
                        MUTED));
        grid.add(containers);

        var nano = card("NANO PANEL", "All state surfaces, borders, width and radius");
        var styled =
                new NanoPanel()
                        .backgroundColor(new Color(0xFF15243A))
                        .hoverBackgroundColor(new Color(0xFF203A5B))
                        .focusedBackgroundColor(new Color(0xFF15243A))
                        .pressedBackgroundColor(new Color(0xFF102033))
                        .disabledBackgroundColor(new Color(0xFF252B35))
                        .borderColor(ACCENT)
                        .hoverBorderColor(CYAN)
                        .focusedBorderColor(CYAN)
                        .pressedBorderColor(ACCENT)
                        .disabledBorderColor(MUTED)
                        .cornerRadius(16)
                        .borderWidth(2);
        styled.getLayout().height(150).widthPercent(100).padding(18);
        styled.add(label("A fully styled NanoPanel", 17, theme.text));
        nano.add(styled);
        grid.add(nano);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates column counts, cell sizing, gaps, mixed nodes, and Nano surface styling. */
    private void gridsPage() {
        var grid = pageGrid();
        var regular = card("GRID", "Columns, cell size, row gap and column gap");
        var textureGrid = new Grid().columns(4).cellSize(72, 42).rowGap(12).columnGap(6);
        for (int index = 1; index <= 12; index++)
            textureGrid.add(new Button(Integer.toString(index)));
        regular.add(textureGrid);
        grid.add(regular);

        var nano = card("NANO GRID", "Layout plus surface and border state styling");
        var nanoGrid =
                new NanoGrid()
                        .columns(3)
                        .cellSize(96, 44)
                        .gap(8)
                        .backgroundColor(new Color(0xFF111C2A))
                        .hoverBackgroundColor(new Color(0xFF172A40))
                        .focusedBackgroundColor(new Color(0xFF111C2A))
                        .pressedBackgroundColor(new Color(0xFF0C1724))
                        .disabledBackgroundColor(new Color(0xFF242B35))
                        .borderColor(theme.border)
                        .hoverBorderColor(ACCENT)
                        .focusedBorderColor(CYAN)
                        .pressedBorderColor(ACCENT)
                        .disabledBorderColor(MUTED)
                        .cornerRadius(12)
                        .borderWidth(2);
        for (int index = 1; index <= 9; index++)
            nanoGrid.add(index % 2 == 0 ? new Button("T" + index) : new NanoButton("N" + index));
        nano.add(nanoGrid);
        grid.add(nano);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates scroll axes, bars, speed, pointer dragging, and programmatic positions. */
    private void scrollingPage() {
        var row = columns();
        var texture = card("SCROLL PANEL", "Vertical axis, visible bar, speed and scroll methods");
        var textureScroll =
                new ScrollPanel().horizontal(false).horizontalBar(false).scrollSpeed(44);
        textureScroll.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        textureScroll.setContent(longList(false));
        texture.add(textureScroll);
        texture.add(button("Scroll +160", () -> textureScroll.scrollBy(0, 160)));
        row.add(texture);

        var nano = card("NANO SCROLL PANEL", "Both axes, bars, position and pointer dragging");
        var nanoScroll = new NanoScrollPanel().horizontal(true).vertical(true).scrollSpeed(44);
        nanoScroll.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        var wide = longList(true);
        wide.getLayout().width(760);
        nanoScroll.setContent(wide);
        nano.add(nanoScroll);
        nano.add(button("Scroll to 120, 160", () -> nanoScroll.scroll(120, 160)));
        row.add(nano);
        workspace.add(row);
    }

    /** Demonstrates initial state, toggling, retained content, and change listeners. */
    private void collapsiblePage() {
        var grid = pageGrid();
        var regular = card("COLLAPSIBLE SECTION", "Expanded and collapsed texture sections");
        var open = new CollapsibleSection("Expanded section", sectionContent(false)).expanded(true);
        open.onChange(() -> status.text("Texture expanded: " + open.isExpanded()));
        regular.add(open);
        regular.add(
                new CollapsibleSection("Initially collapsed", sectionContent(false))
                        .expanded(false));
        grid.add(regular);

        var nano = card("NANO COLLAPSIBLE SECTION", "Expanded and collapsed vector sections");
        var nanoOpen =
                new NanoCollapsibleSection("Expanded section", sectionContent(true)).expanded(true);
        nanoOpen.onChange(() -> status.text("Nano expanded: " + nanoOpen.isExpanded()));
        nano.add(nanoOpen);
        nano.add(
                new NanoCollapsibleSection("Initially collapsed", sectionContent(true))
                        .expanded(false));
        grid.add(nano);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates lazy construction, selection, retained state, callbacks, and removal. */
    private void tabsPage() {
        var row = columns();
        var regular = card("TABBED PANE", "Lazy texture pages, selection and removal");
        var tabs = new TabbedPane();
        tabs.addTab("Overview", () -> tabContent(false, "Texture overview"));
        tabs.addTab("Editor", () -> tabContent(false, "Retained editor"));
        tabs.addTab("Logs", () -> tabContent(false, "Lazy logs"));
        tabs.onChange(() -> status.text("Texture selected tab: " + tabs.getSelectedIndex()));
        tabs.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        regular.add(tabs);
        regular.add(button("Select Editor", () -> tabs.select(1)));
        regular.add(
                button(
                        "Remove final tab",
                        () -> {
                            if (tabs.getTabCount() > 2) tabs.removeTab(tabs.getTabCount() - 1);
                        }));
        row.add(regular);

        var nano = card("NANO TABBED PANE", "Lazy Nano pages, selection and removal");
        var nanoTabs = new NanoTabbedPane();
        nanoTabs.addTab("Overview", () -> tabContent(true, "Nano overview"));
        nanoTabs.addTab("Editor", () -> tabContent(true, "Retained editor"));
        nanoTabs.addTab("Logs", () -> tabContent(true, "Lazy logs"));
        nanoTabs.onChange(() -> status.text("Nano selected tab: " + nanoTabs.getSelectedIndex()));
        nanoTabs.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        nano.add(nanoTabs);
        nano.add(button("Select Editor", () -> nanoTabs.select(1)));
        nano.add(
                button(
                        "Remove final tab",
                        () -> {
                            if (nanoTabs.getTabCount() > 2)
                                nanoTabs.removeTab(nanoTabs.getTabCount() - 1);
                        }));
        row.add(nano);
        workspace.add(row);
    }

    /**
     * Demonstrates orientation, ratios, minimum sizes, divider sizes, callbacks, and keyboard use.
     */
    private void splitsPage() {
        var grid = pageGrid();
        var horizontal =
                card("HORIZONTAL SPLITS", "Ratio, effective ratio, limits and divider size");
        var texture =
                new SplitPane(sectionContent(false), sectionContent(false))
                        .ratio(.35f)
                        .minimumSizes(100, 140)
                        .dividerSize(12);
        texture.onChange(() -> status.text("Texture ratio: " + texture.getEffectiveRatio()));
        texture.getLayout().height(190).widthPercent(100);
        horizontal.add(texture);
        var nano =
                new NanoSplitPane(sectionContent(true), sectionContent(true))
                        .ratio(.65f)
                        .minimumSizes(100, 140)
                        .dividerSize(12);
        nano.onChange(() -> status.text("Nano ratio: " + nano.getEffectiveRatio()));
        nano.getLayout().height(190).widthPercent(100);
        horizontal.add(nano);
        grid.add(horizontal);

        var vertical = card("VERTICAL SPLITS", "Vertical orientation and focusable dividers");
        var textureVertical =
                new SplitPane(sectionContent(false), sectionContent(false))
                        .vertical(true)
                        .ratio(.45f)
                        .minimumSizes(70, 70);
        textureVertical.getLayout().height(250).widthPercent(100);
        vertical.add(textureVertical);
        var nanoVertical =
                new NanoSplitPane(sectionContent(true), sectionContent(true))
                        .vertical(true)
                        .ratio(.55f)
                        .minimumSizes(70, 70);
        nanoVertical.getLayout().height(250).widthPercent(100);
        vertical.add(nanoVertical);
        grid.add(vertical);
        workspace.add(pageScroll(grid));
    }

    /**
     * Demonstrates large datasets, columns, selection, variable heights, overscan, refresh and
     * jump.
     */
    private void virtualListsPage() {
        var row = columns();
        var regular = card("VIRTUAL LIST", "10,000 rows, columns, multiple selection and overscan");
        var list =
                new VirtualList(10000, index -> new Button("Item " + index))
                        .columns(2)
                        .rowHeight(42)
                        .gap(5)
                        .overscan(3)
                        .selectable(true);
        list.getSelection()
                .onChange(
                        () ->
                                status.text(
                                        "Texture selection: "
                                                + list.getSelection().selectedCount()));
        list.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        regular.add(list);
        regular.add(button("Jump to 9,999", () -> list.scrollToIndex(9999)));
        regular.add(button("Refresh visible items", list::refreshItems));
        row.add(regular);

        var nano =
                card("NANO VIRTUAL LIST", "Variable heights, item overrides and live-node count");
        var nanoList =
                new NanoVirtualList(10000, index -> new NanoButton("Record " + index))
                        .columns(1)
                        .rowHeight(40)
                        .gap(5)
                        .overscan(3)
                        .selectable(true)
                        .variableHeights();
        for (int index = 0; index < 10000; index += 7) nanoList.itemHeight(index, 58);
        nanoList.getSelection()
                .onChange(
                        () ->
                                status.text(
                                        "Nano selection: "
                                                + nanoList.getSelection().selectedCount()));
        nanoList.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        nano.add(nanoList);
        nano.add(button("Jump to 9,999", () -> nanoList.scrollToIndex(9999)));
        nano.add(
                button(
                        "Report live nodes",
                        () -> status.text("Live Nano rows: " + nanoList.getLiveItemCount())));
        row.add(nano);
        workspace.add(row);
    }

    /**
     * Demonstrates column factories, weights, sorting, filtering, selection, row height and
     * scrolling.
     */
    private void tablesPage() {
        var rows = tableRows();
        var columns =
                List.of(
                        new TableColumn<ComponentRow>(
                                "ID",
                                1,
                                row -> regularLabel(Integer.toString(row.id())),
                                java.util.Comparator.comparingInt(ComponentRow::id)),
                        TableColumn.text("Component", 3, ComponentRow::name),
                        TableColumn.text("Renderer", 2, ComponentRow::renderer));
        var stack = new NanoContainer();
        stack.getLayout().column().grow().height(0).widthPercent(100).minHeight(0).gap(10);
        var filter = new NanoTextField("Filter component or renderer");
        control(filter);
        stack.add(filter);
        var row = columns();
        row.getLayout().grow().height(0).minHeight(0);
        var regular = card("DATA TABLE", "Sortable texture headers and virtualized mixed cells");
        var table = new DataTable<ComponentRow>(columns).rows(rows).rowHeight(40);
        table.getSelection()
                .onChange(
                        () -> status.text("Texture rows: " + table.getSelection().selectedCount()));
        table.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        regular.add(table);
        regular.add(button("Sort Component", () -> table.toggleSort(1)));
        row.add(regular);
        var nano = card("NANO DATA TABLE", "Nano headers, filtering, selection and scrolling");
        var nanoTable = new NanoDataTable<ComponentRow>(columns).rows(rows).rowHeight(40);
        nanoTable
                .getSelection()
                .onChange(
                        () ->
                                status.text(
                                        "Nano rows: " + nanoTable.getSelection().selectedCount()));
        nanoTable.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        nano.add(nanoTable);
        nano.add(button("Scroll to final row", () -> nanoTable.scrollToRow(rows.size() - 1)));
        row.add(nano);
        filter.getEditor()
                .onChange(
                        () -> {
                            String query =
                                    filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
                            java.util.function.Predicate<ComponentRow> predicate =
                                    value ->
                                            value.name()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                            .contains(query)
                                                    || value.renderer()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                            .contains(query);
                            table.filter(predicate);
                            nanoTable.filter(predicate);
                        });
        stack.add(row);
        workspace.add(stack);
    }

    /** Demonstrates modal lifecycle, dismissal options, toggling, and mixed content. */
    private void modalsPage() {
        var grid = pageGrid();
        var texture = card("MODAL", "Texture overlay, mixed dialog, outside click and Escape");
        texture.add(button("Open closeable texture modal", () -> openTextureModal(true)));
        texture.add(button("Open strict texture modal", () -> openTextureModal(false)));
        grid.add(texture);
        var nano = card("NANO MODAL", "Vector overlay, texture dialog and toggle lifecycle");
        nano.add(button("Open closeable Nano modal", () -> openNanoModal(true)));
        nano.add(button("Open strict Nano modal", () -> openNanoModal(false)));
        grid.add(nano);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates both tooltip renderers and hyperlink destination/visited state. */
    private void tooltipsPage() {
        var grid = pageGrid();
        var tooltip = card("TOOLTIP", "Texture tooltip text and attachment");
        var textureTarget = new Button("Hover for Tooltip");
        var textureTip = new Tooltip("Texture tooltip: concise contextual help");
        textureTarget.setTooltip(textureTip);
        control(textureTarget);
        tooltip.add(textureTarget);
        tooltip.add(
                button(
                        "Change tooltip text",
                        () -> textureTip.text("Tooltip text changed at runtime")));
        grid.add(tooltip);

        var nano = card("NANO TOOLTIP", "Vector font, size and padding");
        var nanoTarget = new NanoButton("Hover for NanoTooltip");
        var nanoTip = new NanoTooltip("Nano tooltip with custom metrics").fontSize(15).padding(12);
        nanoTarget.setTooltip(nanoTip);
        control(nanoTarget);
        nano.add(nanoTarget);
        grid.add(nano);

        var link =
                card("NANO HYPERLINK", "Text, URL, visited state, pointer and keyboard activation");
        var hyperlink =
                new NanoHyperlink(
                        "Open Valthorne on GitHub", "https://github.com/tehnewb/Valthorne");
        link.add(hyperlink);
        link.add(
                button(
                        "Toggle visited appearance",
                        () -> hyperlink.visited(!hyperlink.isVisited())));
        link.add(
                button(
                        "Change label",
                        () -> hyperlink.text("Valthorne repository (updated label)")));
        grid.add(link);
        workspace.add(pageScroll(grid));
    }

    /** Demonstrates combo data, formatting, selection, change events, and popup lifecycle. */
    private void comboBoxPage() {
        var grid = pageGrid();
        var basics = card("ITEMS & SELECTION", "Items, selected index/value and change callback");
        var presets =
                new NanoComboBox<String>()
                        .items(List.of("Balanced", "Performance", "Quality", "Cinematic"))
                        .selectedIndex(0)
                        .onChange(value -> status.text("Preset: " + value));
        control(presets);
        basics.add(presets);
        basics.add(button("Select Quality", () -> presets.selectedIndex(2)));
        basics.add(
                button(
                        "Open / close popup",
                        () -> {
                            if (presets.isOpen()) presets.close();
                            else presets.open();
                        }));
        grid.add(basics);

        var formatting = card("GENERIC VALUES", "Custom formatter and object selection");
        var objects =
                new NanoComboBox<QualityPreset>()
                        .items(
                                List.of(
                                        new QualityPreset("Low", 60),
                                        new QualityPreset("High", 120)))
                        .formatter(value -> value.name() + " · " + value.samples() + " samples")
                        .selectedIndex(1)
                        .onChange(value -> status.text("Samples: " + value.samples()));
        control(objects);
        formatting.add(objects);
        formatting.add(label("Arrow keys navigate; Enter commits; Escape closes.", 13, MUTED));
        grid.add(formatting);
        workspace.add(pageScroll(grid));
    }

    /** Opens a regular modal with configurable outside-click and Escape dismissal. */
    private void openTextureModal(boolean dismissible) {
        var modal =
                new Modal(rootPanel).closeOnOutsideClick(dismissible).closeOnEscape(dismissible);
        var content = modalContent("Texture Modal", dismissible, modal::close);
        modal.content(content).open();
    }

    /** Opens a Nano modal with configurable outside-click and Escape dismissal. */
    private void openNanoModal(boolean dismissible) {
        var modal =
                new NanoModal(rootPanel)
                        .closeOnOutsideClick(dismissible)
                        .closeOnEscape(dismissible);
        var content = modalContent("Nano Modal", dismissible, modal::close);
        modal.content(content).open();
    }

    /** Creates shared mixed-renderer dialog content for both modal implementations. */
    private NanoPanel modalContent(String title, boolean dismissible, Runnable close) {
        var content = panel(CARD);
        content.getLayout().width(430).column().padding(24).gap(14);
        content.add(label(title, 24, theme.text));
        content.add(
                label(
                        dismissible
                                ? "Escape and outside click close this dialog."
                                : "Only the explicit button closes this dialog.",
                        14,
                        MUTED));
        var field = new TextField("Focus is trapped inside");
        control(field);
        content.add(field);
        content.add(button("Close dialog", close));
        return content;
    }

    /** Creates repeated content used to demonstrate scroll panels. */
    private NanoContainer longList(boolean nano) {
        var list = new NanoContainer();
        list.getLayout().column().gap(6).widthPercent(100).noShrink();
        for (int index = 1; index <= 30; index++) {
            UINode item =
                    nano ? new NanoButton("Nano row " + index) : new Button("Texture row " + index);
            control(item);
            list.add(item);
        }
        return list;
    }

    /** Creates content used by collapsible and split-pane examples. */
    private NanoPanel sectionContent(boolean nano) {
        var content = panel(new Color(0xFF111A27));
        content.getLayout().column().padding(12).gap(8).grow().minWidth(0).minHeight(0);
        content.add(label(nano ? "Nano content" : "Texture content", 14, MUTED));
        content.add(nano ? new NanoButton("Vector action") : new Button("Texture action"));
        return content;
    }

    /** Creates a retained editable page for tab demonstrations. */
    private NanoPanel tabContent(boolean nano, String title) {
        var page = panel(new Color(0xFF111A27));
        page.getLayout().column().padding(14).gap(10).grow();
        page.add(label(title, 16, theme.text));
        UINode field =
                nano
                        ? new NanoTextField("State persists across tabs")
                        : new TextField("State persists across tabs");
        control(field);
        page.add(field);
        return page;
    }

    /** Creates the data rows shared by both table implementations. */
    private static List<ComponentRow> tableRows() {
        var rows = new ArrayList<ComponentRow>();
        String[] names = {
            "Button", "Checkbox", "CollapsibleSection", "DataTable", "Grid", "Image", "Label",
            "Modal", "Panel", "ProgressBar", "ScrollPanel", "Slider", "SplitPane", "TabbedPane",
            "TextField", "Tooltip", "VirtualList"
        };
        int id = 1;
        for (String name : names) {
            rows.add(new ComponentRow(id++, name, "Texture"));
            rows.add(new ComponentRow(id++, "Nano" + name, "NanoVG"));
        }
        rows.add(new ComponentRow(id++, "NanoComboBox", "NanoVG"));
        rows.add(new ComponentRow(id++, "NanoContainer", "NanoVG"));
        rows.add(new ComponentRow(id++, "NanoHyperlink", "NanoVG"));
        rows.add(new ComponentRow(id, "SlugLabel", "Slug"));
        return rows;
    }

    /** Stable record displayed by the data-table examples. */
    private record ComponentRow(int id, String name, String renderer) {}

    /** Sample domain object used to demonstrate generic combo-box formatting. */
    private record QualityPreset(String name, int samples) {}

    /** Creates a two-column, vertically scrollable feature gallery. */
    private NanoGrid pageGrid() {
        var grid = new NanoGrid().columns(2).cellHeight(340).gap(14);
        grid.getLayout().widthPercent(100).noShrink();
        return grid;
    }

    /** Wraps a page in its constrained vertical scroll viewport. */
    private NanoScrollPanel pageScroll(UINode content) {
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        scroll.setContent(content);
        return scroll;
    }

    /** Creates equal columns for examples that need the full workspace height. */
    private NanoContainer columns() {
        var row = new NanoContainer();
        row.getLayout().row().grow().height(0).minHeight(0).widthPercent(100).gap(14);
        return row;
    }

    /** Creates a professional feature card. */
    private NanoPanel card(String title, String subtitle) {
        var card = panel(CARD);
        card.getLayout().column().padding(16).gap(11).grow().minWidth(0).minHeight(0);
        card.add(label(title, 13, ACCENT));
        card.add(label(subtitle, 13, MUTED));
        return card;
    }

    /** Creates a bordered Nano surface. */
    private NanoPanel panel(Color color) {
        return new NanoPanel()
                .backgroundColor(color)
                .borderColor(theme == null ? new Color(0xFF34465E) : theme.border)
                .cornerRadius(10)
                .borderWidth(1);
    }

    /** Creates a styled Nano label. */
    private static NanoLabel label(String text, float size, Color color) {
        return new NanoLabel(text).fontSize(size).color(color);
    }

    /** Creates a texture-backed label using the showcase theme font and text color. */
    private Label regularLabel(String text) {
        return new Label(text).font(theme.getFont()).color(theme.text);
    }

    /** Creates a consistently sized Nano button. */
    private static NanoButton button(String text, Runnable action) {
        var button = new NanoButton(text).action(ignored -> action.run());
        control(button);
        return button;
    }

    /** Creates a descriptive row with a compact checkbox on the right. */
    private static NanoContainer checkRow(String text, UINode checkbox) {
        var row = new NanoContainer();
        row.getLayout().row().height(34).itemsCenter().gap(12).noShrink().widthPercent(100);
        var label = label(text, 14, MUTED);
        label.getLayout().grow();
        checkbox.getLayout().width(26).height(26);
        row.add(label);
        row.add(checkbox);
        return row;
    }

    /** Assigns standard full-width control geometry. */
    private static void control(UINode node) {
        controlHeight(node, 40);
    }

    /** Assigns explicit control height while preventing flex shrink. */
    private static void controlHeight(UINode node, float height) {
        node.getLayout().height(height).widthPercent(100).noShrink();
    }

    /** Assigns standard slider geometry. */
    private static void slider(UINode node) {
        controlHeight(node, 34);
    }

    /** Clears the background before the retained UI root is rendered. */
    @Override
    public void draw(TextureBatch batch) {
        Window.clear(BACKGROUND);
    }

    /** Leaves this event-driven reference without a separate simulation. */
    @Override
    public void update(float delta) {}

    /** Visits and captures every component page during hidden smoke operation. */
    @Override
    protected void drawScene() {
        super.drawScene();
        if (!smoke) return;
        smokeFrame++;
        if (smokeFrame % 3 != 0) return;
        FrameCapture.save(
                Path.of(
                        "build/ui-showcase",
                        currentPage.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
        int next = currentPage.ordinal() + 1;
        if (next == Page.values().length) Window.requestClose();
        else {
            show(Page.values()[next]);
            ui.layout();
        }
    }

    /** Destroys borrowers before releasing the theme, images, textures, and Slug font. */
    @Override
    public void dispose() {
        disposeScene();
        if (slugFont != null) slugFont.dispose();
        if (previewTexture != null) previewTexture.dispose();
        if (alternateTexture != null) alternateTexture.dispose();
        if (previewData != null) previewData.dispose();
        if (alternateData != null) alternateData.dispose();
        if (theme != null) theme.close();
    }
}
