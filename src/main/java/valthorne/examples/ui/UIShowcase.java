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
import valthorne.ui.nodes.Button;
import valthorne.ui.nodes.Checkbox;
import valthorne.ui.nodes.CollapsibleSection;
import valthorne.ui.nodes.DataTable;
import valthorne.ui.nodes.Grid;
import valthorne.ui.nodes.Image;
import valthorne.ui.nodes.Label;
import valthorne.ui.nodes.Modal;
import valthorne.ui.nodes.Panel;
import valthorne.ui.nodes.ProgressBar;
import valthorne.ui.nodes.ScrollPanel;
import valthorne.ui.nodes.Slider;
import valthorne.ui.nodes.SlugLabel;
import valthorne.ui.nodes.SplitPane;
import valthorne.ui.nodes.TabbedPane;
import valthorne.ui.nodes.TableColumn;
import valthorne.ui.nodes.TextField;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.nodes.VirtualList;
import valthorne.ui.nodes.nano.NanoButton;
import valthorne.ui.nodes.nano.NanoCheckbox;
import valthorne.ui.nodes.nano.NanoCollapsibleSection;
import valthorne.ui.nodes.nano.NanoComboBox;
import valthorne.ui.nodes.nano.NanoContainer;
import valthorne.ui.nodes.nano.NanoDataTable;
import valthorne.ui.nodes.nano.NanoGrid;
import valthorne.ui.nodes.nano.NanoHyperlink;
import valthorne.ui.nodes.nano.NanoImage;
import valthorne.ui.nodes.nano.NanoLabel;
import valthorne.ui.nodes.nano.NanoModal;
import valthorne.ui.nodes.nano.NanoPanel;
import valthorne.ui.nodes.nano.NanoProgressBar;
import valthorne.ui.nodes.nano.NanoScrollPanel;
import valthorne.ui.nodes.nano.NanoSlider;
import valthorne.ui.nodes.nano.NanoSplitPane;
import valthorne.ui.nodes.nano.NanoTabbedPane;
import valthorne.ui.nodes.nano.NanoTextField;
import valthorne.ui.nodes.nano.NanoTooltip;
import valthorne.ui.nodes.nano.NanoVirtualList;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.ui.theme.ThemeData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Complete interactive catalog for every concrete control in Valthorne's current UI library. Pages
 * group related elements so the source doubles as a set of focused construction examples.
 *
 * <h2>Ownership</h2>
 *
 * <p>The scene owns its theme, preview image, texture, and Slug font. UI nodes borrow those
 * resources and are destroyed before the resources are released. The hidden smoke mode visits and
 * captures every page through the same code path used by interactive navigation.
 */
public final class UIShowcase extends Scene {
    /** Top-level catalog destinations and their visible descriptions. */
    private enum Page {
        FOUNDATIONS("Foundations", "Containers, labels, images and grids"),
        INPUTS("Inputs", "Buttons, fields, checkboxes, sliders and progress"),
        NAVIGATION("Navigation", "Scrolling, disclosure, tabs and split panes"),
        DATA("Data", "Virtual lists and sortable tables"),
        OVERLAYS("Overlays", "Tooltips, links and modal focus");

        private final String title;
        private final String subtitle;

        /** Stores the display copy used by the navigation and workspace header. */
        Page(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final Color BACKGROUND = new Color(0xFF0B1018);
    private static final Color SIDEBAR = new Color(0xFF111A27);
    private static final Color CARD = new Color(0xFF182334);
    private static final Color ACCENT = new Color(0xFF73A7FF);
    private static final Color MUTED = new Color(0xFF9AAAC0);

    private final boolean smoke;
    private ProfessionalTheme theme;
    private ThemeData themeData;
    private TextureData previewData;
    private Texture previewTexture;
    private SlugFont slugFont;
    private NanoPanel stage;
    private NanoPanel pageHost;
    private NanoLabel pageTitle;
    private NanoLabel pageSubtitle;
    private NanoLabel status;
    private Page currentPage = Page.FOUNDATIONS;
    private int smokeFrame;

    /** Creates an interactive or bounded smoke-test version of the catalog. */
    private UIShowcase(boolean smoke) {
        this.smoke = smoke;
    }

    /** Starts the showcase using the common scene and UI event pipeline. */
    public static void main(String[] args) {
        boolean smoke = Arrays.asList(args).contains("--smoke");
        JGL.init(
                new GameScreen(new UIShowcase(smoke)),
                JGLConfiguration.defaults()
                        .title("Valthorne | Complete UI Showcase")
                        .size(1440, 900)
                        .samples(8)
                        .swapInterval(SwapInterval.VSYNC)
                        .visible(!smoke));
    }

    /**
     * Creates owned resources, applies the professional theme, and builds the application shell.
     */
    @Override
    public void init() {
        camera = null;
        theme = new ProfessionalTheme(false, 1);
        themeData = theme.create();
        ui.setTheme(themeData);
        createPreviewImage();
        loadSlugFont();
        buildShell();
        show(Page.FOUNDATIONS);
        ui.layout();
    }

    /** Creates a small reusable gradient image for both image-rendering backends. */
    private void createPreviewImage() {
        int size = 96;
        var pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float glow = 1f - Math.min(1f, (float) Math.hypot(x - 48, y - 42) / 68f);
                pixels.put((byte) (35 + 70 * glow));
                pixels.put((byte) (75 + 85 * glow));
                pixels.put((byte) (145 + 100 * glow));
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        previewData = new TextureData(pixels, size, size);
        previewTexture = new Texture(previewData);
    }

    /** Loads the bundled theme typeface into the current Slug renderer. */
    private void loadSlugFont() {
        try (var stream =
                ProfessionalTheme.class.getResourceAsStream(
                        "/ui/AtkinsonHyperlegible-Regular.ttf")) {
            if (stream == null) throw new IllegalStateException("Bundled UI font is missing");
            slugFont = SlugFont.load(stream.readAllBytes(), 32, 95);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the bundled Slug font", exception);
        }
    }

    /** Builds the permanent header, navigation rail, page host, and status bar. */
    private void buildShell() {
        stage = panel(BACKGROUND);
        stage.getLayout()
                .absolute()
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100)
                .column()
                .padding(24)
                .gap(18);
        ui.add(stage);

        var header = new NanoContainer();
        header.getLayout().row().widthPercent(100).height(64).itemsCenter().gap(16).noShrink();
        var brand = nanoLabel("VALTHORNE", 25, ACCENT);
        brand.getLayout().width(210).noShrink();
        header.add(brand);
        var identity = new NanoContainer();
        identity.getLayout().column().grow().gap(2);
        identity.add(nanoLabel("UI COMPONENT GALLERY", 17, theme.text));
        identity.add(
                nanoLabel("Every current element · two rendering backends · one tree", 13, MUTED));
        header.add(identity);
        header.add(nanoButton("Inspect layout", this::toggleInspector));
        stage.add(header);

        var body = new NanoContainer();
        body.getLayout().row().grow().height(0).widthPercent(100).gap(18).minHeight(0);
        stage.add(body);
        body.add(buildSidebar());

        var workspace = new NanoContainer();
        workspace.getLayout().column().grow().width(0).heightPercent(100).gap(12).minWidth(0);
        pageTitle = nanoLabel("", 27, theme.text);
        pageSubtitle = nanoLabel("", 14, MUTED);
        workspace.add(pageTitle);
        workspace.add(pageSubtitle);
        pageHost = panel(CARD);
        pageHost.getLayout()
                .column()
                .grow()
                .height(0)
                .widthPercent(100)
                .minHeight(0)
                .padding(18)
                .gap(14);
        workspace.add(pageHost);
        body.add(workspace);

        status = nanoLabel("Ready · Tab moves focus · Enter or Space activates", 13, MUTED);
        stage.add(status);
    }

    /** Creates the persistent page navigation and concise keyboard help. */
    private NanoPanel buildSidebar() {
        var sidebar = panel(SIDEBAR);
        sidebar.getLayout().width(230).heightPercent(100).column().padding(14).gap(10).noShrink();
        sidebar.add(nanoLabel("CATALOG", 12, MUTED));
        int number = 1;
        for (Page page : Page.values()) {
            String label = "%02d   %s".formatted(number++, page.title);
            var button = nanoButton(label, () -> show(page));
            button.getLayout().widthPercent(100);
            sidebar.add(button);
        }
        var spacer = new NanoContainer();
        spacer.getLayout().grow();
        sidebar.add(spacer);
        sidebar.add(nanoLabel("KEYBOARD", 12, MUTED));
        sidebar.add(
                nanoLabel(
                        "Tab / Shift+Tab   Focus\n"
                                + "Enter / Space     Activate\n"
                                + "Arrow keys        Adjust\n"
                                + "Escape            Dismiss",
                        13,
                        MUTED));
        return sidebar;
    }

    /** Enables or disables the root's live bounds inspector. */
    private void toggleInspector() {
        var inspector = ui.getInspector();
        inspector.setEnabled(!inspector.isEnabled());
        inspector.setOutlines(true);
        status.text("Layout inspector " + (inspector.isEnabled() ? "enabled" : "disabled"));
    }

    /** Replaces the active page with a freshly constructed component family. */
    private void show(Page page) {
        ui.cancelInput();
        currentPage = page;
        pageHost.clear();
        pageTitle.text(page.title);
        pageSubtitle.text(page.subtitle);
        switch (page) {
            case FOUNDATIONS -> buildFoundationsPage();
            case INPUTS -> buildInputsPage();
            case NAVIGATION -> buildNavigationPage();
            case DATA -> buildDataPage();
            case OVERLAYS -> buildOverlaysPage();
        }
        status.text(page.title + " · all controls are live and keyboard accessible");
    }

    /** Shows containers, labels, both image paths, both grids, and Slug text. */
    private void buildFoundationsPage() {
        var scroll = pageScroll();
        var gallery = galleryGrid();
        scroll.setContent(gallery);
        pageHost.add(scroll);

        var textureCard = card("TEXTURE FOUNDATIONS", "Panel · Label · Image · Grid");
        textureCard.add(regularLabel("Crisp atlas text from a themed Label"));
        var imageRow = new Panel();
        imageRow.getLayout().row().gap(12).height(88).noShrink();
        var image = new Image(previewTexture);
        image.getLayout().width(88).height(88);
        imageRow.add(image);
        var grid = new Grid().columns(3).cellSize(52, 32).gap(7);
        for (int i = 1; i <= 6; i++) grid.add(new Button(Integer.toString(i)));
        imageRow.add(grid);
        textureCard.add(imageRow);
        gallery.add(textureCard);

        var nanoCard = card("VECTOR FOUNDATIONS", "NanoPanel · NanoLabel · NanoImage · NanoGrid");
        nanoCard.add(
                nanoLabel("NanoVG text stays sharp at any scale", 16, theme.text).selectable(true));
        var nanoRow = new NanoContainer();
        nanoRow.getLayout().row().gap(12).height(88).noShrink();
        var nanoImage = new NanoImage(previewData);
        nanoImage.getLayout().width(88).height(88);
        nanoRow.add(nanoImage);
        var nanoGrid = new NanoGrid().columns(3).cellSize(52, 32).gap(7);
        for (int i = 1; i <= 6; i++) nanoGrid.add(new NanoButton(Integer.toString(i)));
        nanoRow.add(nanoGrid);
        nanoCard.add(nanoRow);
        gallery.add(nanoCard);

        var typography = card("SLUG TEXT", "SlugLabel · cached atlas and live curves");
        typography.add(
                new SlugLabel(slugFont, "Resolution-independent display type", 25).color(ACCENT));
        typography.add(
                new SlugLabel(slugFont, "Live curve rendering at showcase scale", 34)
                        .color(theme.text)
                        .liveCurves(true));
        gallery.add(typography);

        var composition = card("MIXED COMPOSITION", "NanoContainer · nested renderer switching");
        composition.add(nanoLabel("Both backends share layout, clipping and input.", 15, MUTED));
        var mixed = new NanoContainer();
        mixed.getLayout().row().gap(10).height(42).noShrink();
        mixed.add(new Button("Texture child"));
        mixed.add(new NanoButton("Vector child"));
        composition.add(mixed);
        gallery.add(composition);
    }

    /** Shows every input control and connects paired sliders to paired progress bars. */
    private void buildInputsPage() {
        var scroll = pageScroll();
        var gallery = galleryGrid();
        scroll.setContent(gallery);
        pageHost.add(scroll);

        var texture =
                card("TEXTURE CONTROLS", "Button · TextField · Checkbox · Slider · ProgressBar");
        var textureButton = new Button("Create texture action");
        textureButton.action(button -> status.text("Texture button activated"));
        rowHeight(textureButton, 40);
        texture.add(textureButton);
        var textureField = new TextField("Type a project name");
        textureField.action(field -> status.text("Submitted: " + field.getText()));
        rowHeight(textureField, 40);
        texture.add(textureField);
        var textureCheck = new Checkbox().checked(true);
        textureCheck.action(check -> status.text("Texture checkbox: " + check.isChecked()));
        textureCheck.getLayout().width(26).height(26);
        texture.add(labeledRow("Enable texture controls", textureCheck));
        var textureProgress = new ProgressBar(0, 100).progress(64).displayPercentage(true);
        rowHeight(textureProgress, 25);
        var textureSlider = new Slider(0, 100, 64).stepSize(1);
        textureSlider.action(slider -> textureProgress.progress(slider.getValue()));
        rowHeight(textureSlider, 32);
        texture.add(textureSlider);
        texture.add(textureProgress);
        gallery.add(texture);

        var vector =
                card("VECTOR CONTROLS", "NanoButton · NanoTextField · NanoCheckbox · NanoSlider");
        vector.add(nanoButton("Create vector action", () -> status.text("Nano button activated")));
        var nanoField = new NanoTextField("Search components");
        nanoField.action(field -> status.text("Submitted: " + field.getText()));
        rowHeight(nanoField, 40);
        vector.add(nanoField);
        var nanoCheck = new NanoCheckbox().checked(true);
        nanoCheck.action(check -> status.text("Nano checkbox: " + check.isChecked()));
        nanoCheck.getLayout().width(26).height(26);
        vector.add(labeledRow("Enable vector controls", nanoCheck));
        var nanoProgress = new NanoProgressBar(0, 100).progress(72).displayPercentage(true);
        rowHeight(nanoProgress, 25);
        var nanoSlider = new NanoSlider(0, 100, 72).stepSize(1);
        nanoSlider.action(slider -> nanoProgress.progress(slider.getValue()));
        rowHeight(nanoSlider, 32);
        vector.add(nanoSlider);
        vector.add(nanoProgress);
        gallery.add(vector);

        var states = card("STATES & CHOICES", "Disabled states · masking · NanoComboBox");
        var disabled = new Button("Unavailable action");
        disabled.setEnabled(false);
        rowHeight(disabled, 40);
        states.add(disabled);
        var password = new TextField("Password").masking(true);
        rowHeight(password, 40);
        states.add(password);
        var nanoPassword = new NanoTextField("Vector password").masking(true);
        rowHeight(nanoPassword, 40);
        states.add(nanoPassword);
        var combo =
                new NanoComboBox<String>()
                        .items(List.of("Balanced", "Performance", "Quality"))
                        .selectedIndex(0)
                        .onChange(value -> status.text("Preset: " + value));
        rowHeight(combo, 40);
        states.add(combo);
        gallery.add(states);

        var orientation = card("ORIENTATION", "Vertical Slider · vertical ProgressBar");
        var verticalRow = new NanoContainer();
        verticalRow.getLayout().row().height(150).gap(24).noShrink();
        var verticalSlider = new Slider(0, 100, 45).vertical(true);
        verticalSlider.getLayout().width(34).height(140);
        var verticalProgress = new NanoProgressBar(0, 100).progress(45).vertical(true);
        verticalProgress.getLayout().width(34).height(140);
        verticalSlider.action(slider -> verticalProgress.progress(slider.getValue()));
        verticalRow.add(verticalSlider);
        verticalRow.add(verticalProgress);
        orientation.add(verticalRow);
        gallery.add(orientation);
    }

    /** Shows both implementations of scrolling, disclosure, tabs, and draggable splits. */
    private void buildNavigationPage() {
        var tabs = new NanoTabbedPane();
        tabs.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        tabs.addTab("Scrolling", this::scrollingDemo);
        tabs.addTab("Disclosure", this::disclosureDemo);
        tabs.addTab("Tabs", this::tabsDemo);
        tabs.addTab("Split panes", this::splitDemo);
        pageHost.add(tabs);
    }

    /** Builds side-by-side texture and NanoVG scrolling examples. */
    private UINode scrollingDemo() {
        var row = twoColumns();
        var texture = card("SCROLL PANEL", "ScrollPanel · texture content");
        var textureScroll = new ScrollPanel().horizontal(false).horizontalBar(false);
        textureScroll.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        var textureItems = new Panel();
        textureItems.getLayout().column().gap(7).widthPercent(100).noShrink();
        for (int i = 1; i <= 18; i++) textureItems.add(new Button("Texture item " + i));
        textureScroll.setContent(textureItems);
        texture.add(textureScroll);
        row.add(texture);

        var vector = card("NANO SCROLL PANEL", "NanoScrollPanel · vector content");
        var nanoScroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        nanoScroll.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        var nanoItems = new NanoContainer();
        nanoItems.getLayout().column().gap(7).widthPercent(100).noShrink();
        for (int i = 1; i <= 18; i++) nanoItems.add(new NanoButton("Vector item " + i));
        nanoScroll.setContent(nanoItems);
        vector.add(nanoScroll);
        row.add(vector);
        return row;
    }

    /** Builds the two collapsible-section implementations. */
    private UINode disclosureDemo() {
        var row = twoColumns();
        var texture = card("COLLAPSIBLE SECTION", "Texture header and retained content");
        var textureContent = new Panel();
        textureContent.getLayout().column().padding(12).gap(8);
        textureContent.add(regularLabel("Texture content remains attached while collapsed."));
        texture.add(new CollapsibleSection("Texture details", textureContent));
        row.add(texture);
        var vector = card("NANO COLLAPSIBLE SECTION", "Vector header and retained content");
        var nanoContent = new NanoContainer();
        nanoContent.getLayout().column().padding(12).gap(8);
        nanoContent.add(nanoLabel("Vector content uses the same disclosure behavior.", 15, MUTED));
        vector.add(new NanoCollapsibleSection("Vector details", nanoContent));
        row.add(vector);
        return row;
    }

    /** Builds nested regular and NanoVG tabbed panes. */
    private UINode tabsDemo() {
        var row = twoColumns();
        var texture = card("TABBED PANE", "Lazy texture-backed pages");
        var textureTabs = new TabbedPane();
        textureTabs.addTab("Overview", () -> regularTabPage("Texture tab content"));
        textureTabs.addTab("Settings", () -> regularTabPage("Created when first selected"));
        textureTabs.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        texture.add(textureTabs);
        row.add(texture);
        var vector = card("NANO TABBED PANE", "Lazy NanoVG pages");
        var nanoTabs = new NanoTabbedPane();
        nanoTabs.addTab("Overview", () -> nanoTabPage("Vector tab content"));
        nanoTabs.addTab("Settings", () -> nanoTabPage("State persists between selections"));
        nanoTabs.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        vector.add(nanoTabs);
        row.add(vector);
        return row;
    }

    /** Builds matching draggable split panes for both renderers. */
    private UINode splitDemo() {
        var row = twoColumns();
        var texture = card("SPLIT PANE", "Drag or focus the texture divider");
        var textureSplit =
                new SplitPane(regularTabPage("Left workspace"), regularTabPage("Right workspace"))
                        .ratio(.42f)
                        .minimumSizes(120, 120)
                        .dividerSize(10);
        textureSplit.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        texture.add(textureSplit);
        row.add(texture);
        var vector = card("NANO SPLIT PANE", "Drag or focus the vector divider");
        var nanoSplit =
                new NanoSplitPane(nanoTabPage("Left workspace"), nanoTabPage("Right workspace"))
                        .ratio(.58f)
                        .minimumSizes(120, 120)
                        .dividerSize(10);
        nanoSplit.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        vector.add(nanoSplit);
        row.add(vector);
        return row;
    }

    /** Shows both virtual-list and data-table implementations with realistic data volumes. */
    private void buildDataPage() {
        var tabs = new NanoTabbedPane();
        tabs.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        tabs.addTab("Virtual lists", this::virtualListsDemo);
        tabs.addTab("Data tables", this::dataTablesDemo);
        pageHost.add(tabs);
    }

    /** Builds two independently virtualized 5,000-item collections. */
    private UINode virtualListsDemo() {
        var row = twoColumns();
        var texture = card("VIRTUAL LIST", "5,000 texture rows · bounded live nodes");
        var list =
                new VirtualList(
                                5000,
                                index ->
                                        new Button("Asset %04d".formatted(index))
                                                .action(button -> status.text("Asset " + index)))
                        .rowHeight(40)
                        .gap(5)
                        .selectable(true);
        list.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        texture.add(list);
        row.add(texture);

        var vector = card("NANO VIRTUAL LIST", "5,000 vector rows · variable heights");
        var nanoList =
                new NanoVirtualList(
                                5000,
                                index ->
                                        new NanoButton("Record %04d".formatted(index))
                                                .action(button -> status.text("Record " + index)))
                        .rowHeight(40)
                        .gap(5)
                        .selectable(true)
                        .variableHeights();
        for (int index = 0; index < 5000; index += 9) nanoList.itemHeight(index, 56);
        nanoList.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        vector.add(nanoList);
        row.add(vector);
        return row;
    }

    /** Builds matching sortable, virtualized tables from shared row data. */
    private UINode dataTablesDemo() {
        List<ComponentRow> rows = componentRows();
        var columns =
                List.of(
                        TableColumn.text("Component", 3, ComponentRow::name),
                        TableColumn.text("Family", 2, ComponentRow::family),
                        TableColumn.text("State", 1, ComponentRow::state));
        var layout = twoColumns();
        var texture = card("DATA TABLE", "Texture headers and virtual rows");
        var table = new DataTable<ComponentRow>(columns).rows(rows).rowHeight(40);
        table.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        texture.add(table);
        layout.add(texture);
        var vector = card("NANO DATA TABLE", "Vector headers and virtual rows");
        var nanoTable = new NanoDataTable<ComponentRow>(columns).rows(rows).rowHeight(40);
        nanoTable.getLayout().grow().height(0).minHeight(0).widthPercent(100);
        vector.add(nanoTable);
        layout.add(vector);
        return layout;
    }

    /** Shows both tooltip implementations, the hyperlink, and both modal implementations. */
    private void buildOverlaysPage() {
        var scroll = pageScroll();
        var gallery = galleryGrid();
        scroll.setContent(gallery);
        pageHost.add(scroll);

        var tooltips = card("TOOLTIPS", "Tooltip · NanoTooltip");
        var textureButton = new Button("Hover for texture tooltip");
        textureButton.setTooltip(new Tooltip("Texture-backed tooltip with delayed presentation"));
        rowHeight(textureButton, 42);
        tooltips.add(textureButton);
        var nanoButton = new NanoButton("Hover for vector tooltip");
        nanoButton.setTooltip(new NanoTooltip("NanoVG tooltip with crisp vector text"));
        rowHeight(nanoButton, 42);
        tooltips.add(nanoButton);
        gallery.add(tooltips);

        var links = card("HYPERLINK", "NanoHyperlink · keyboard and pointer activation");
        links.add(nanoLabel("Links validate and launch their retained destination.", 15, MUTED));
        links.add(
                new NanoHyperlink(
                        "Open the Valthorne repository", "https://github.com/tehnewb/Valthorne"));
        gallery.add(links);

        var textureModal = card("MODAL", "Texture overlay · Nano content");
        textureModal.add(nanoButton("Open texture modal", () -> openTextureModal()));
        gallery.add(textureModal);

        var nanoModal = card("NANO MODAL", "Vector overlay · texture content");
        nanoModal.add(nanoButton("Open vector modal", () -> openNanoModal()));
        gallery.add(nanoModal);
    }

    /** Opens a texture-backed modal containing mixed renderer content. */
    private void openTextureModal() {
        var modal = new Modal(stage).closeOnOutsideClick(true);
        var dialog = new NanoContainer();
        dialog.getLayout().width(420).column().padding(24).gap(14);
        dialog.add(nanoLabel("Texture modal", 24, theme.text));
        dialog.add(nanoLabel("Focus is trapped here until the dialog closes.", 15, MUTED));
        dialog.add(new NanoTextField("Try Tab, then Escape"));
        dialog.add(nanoButton("Close dialog", modal::close));
        modal.content(dialog).open();
    }

    /** Opens a NanoVG modal containing texture-backed controls. */
    private void openNanoModal() {
        var modal = new NanoModal(stage).closeOnOutsideClick(true);
        var dialog = new Panel();
        dialog.getLayout().width(420).column().padding(24).gap(14);
        dialog.add(regularLabel("NanoVG modal with texture content"));
        var field = new TextField("Focus remains inside this dialog");
        rowHeight(field, 40);
        dialog.add(field);
        var close = new Button("Close dialog");
        close.action(button -> modal.close());
        rowHeight(close, 40);
        dialog.add(close);
        modal.content(dialog).open();
    }

    /** Creates the standard scroll viewport used by card-gallery pages. */
    private NanoScrollPanel pageScroll() {
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().grow().height(0).widthPercent(100).minHeight(0);
        return scroll;
    }

    /** Creates a responsive two-column grid for gallery cards. */
    private NanoGrid galleryGrid() {
        var grid = new NanoGrid().columns(2).cellHeight(270).gap(14);
        grid.getLayout().widthPercent(100).noShrink();
        return grid;
    }

    /** Creates an equal two-column workspace row. */
    private NanoContainer twoColumns() {
        var row = new NanoContainer();
        row.getLayout().row().grow().height(0).widthPercent(100).gap(14).minHeight(0);
        return row;
    }

    /** Creates a polished card with a title, caption, and flexible content area. */
    private NanoPanel card(String title, String caption) {
        var card = panel(CARD);
        card.getLayout().column().padding(16).gap(11).grow().minWidth(0).minHeight(0);
        card.add(nanoLabel(title, 13, ACCENT));
        card.add(nanoLabel(caption, 13, MUTED));
        return card;
    }

    /** Creates a Nano panel with an explicit showcase surface color. */
    private NanoPanel panel(Color color) {
        return new NanoPanel()
                .backgroundColor(color)
                .borderColor(theme.border)
                .cornerRadius(10)
                .borderWidth(1);
    }

    /** Creates a consistently sized Nano button and binds a simple action. */
    private NanoButton nanoButton(String text, Runnable action) {
        var button = new NanoButton(text).action(ignored -> action.run());
        rowHeight(button, 40);
        return button;
    }

    /** Creates a Nano label with explicit typography for the showcase hierarchy. */
    private NanoLabel nanoLabel(String text, float size, Color color) {
        return new NanoLabel(text).fontSize(size).color(color);
    }

    /** Creates a themed texture-backed label. */
    private Label regularLabel(String text) {
        return new Label(text).font(theme.getFont()).color(theme.text);
    }

    /** Pairs descriptive text with a compact control. */
    private NanoContainer labeledRow(String text, UINode control) {
        var row = new NanoContainer();
        row.getLayout().row().height(34).itemsCenter().gap(12).noShrink();
        var label = nanoLabel(text, 14, MUTED);
        label.getLayout().grow();
        row.add(label);
        row.add(control);
        return row;
    }

    /** Creates a simple regular tab page. */
    private Panel regularTabPage(String text) {
        var page = new Panel();
        page.getLayout().column().padding(16).gap(10).grow();
        page.add(regularLabel(text));
        page.add(new Button("Texture action"));
        return page;
    }

    /** Creates a simple vector tab page. */
    private NanoPanel nanoTabPage(String text) {
        var page = panel(SIDEBAR);
        page.getLayout().column().padding(16).gap(10).grow();
        page.add(nanoLabel(text, 15, theme.text));
        page.add(new NanoButton("Vector action"));
        return page;
    }

    /** Sets a fixed row height and opts the node out of vertical shrinking. */
    private static void rowHeight(UINode node, float height) {
        node.getLayout().height(height).noShrink().widthPercent(100);
    }

    /** Creates representative table data, including every component family. */
    private static List<ComponentRow> componentRows() {
        var rows = new ArrayList<ComponentRow>();
        String[] texture = {
            "Button", "Checkbox", "CollapsibleSection", "DataTable", "Grid", "Image", "Label",
            "Modal", "Panel", "ProgressBar", "ScrollPanel", "Slider", "SplitPane", "TabbedPane",
            "TextField", "Tooltip", "VirtualList"
        };
        String[] vector = {
            "NanoButton",
            "NanoCheckbox",
            "NanoCollapsibleSection",
            "NanoComboBox",
            "NanoContainer",
            "NanoDataTable",
            "NanoGrid",
            "NanoHyperlink",
            "NanoImage",
            "NanoLabel",
            "NanoModal",
            "NanoPanel",
            "NanoProgressBar",
            "NanoScrollPanel",
            "NanoSlider",
            "NanoSplitPane",
            "NanoTabbedPane",
            "NanoTextField",
            "NanoTooltip",
            "NanoVirtualList"
        };
        for (String name : texture) rows.add(new ComponentRow(name, "Texture", "Interactive"));
        for (String name : vector) rows.add(new ComponentRow(name, "NanoVG", "Interactive"));
        rows.add(new ComponentRow("SlugLabel", "Slug", "Rendered"));
        return rows;
    }

    /** One stable row in the component inventory tables. */
    private record ComponentRow(String name, String family, String state) {}

    /** Clears behind the retained UI; the scene infrastructure draws the root afterward. */
    @Override
    public void draw(TextureBatch batch) {
        Window.clear(BACKGROUND);
    }

    /** Keeps the example event-driven; no separate simulation is required. */
    @Override
    public void update(float delta) {}

    /** Captures every page in smoke mode and exits after the final catalog view. */
    @Override
    protected void drawScene() {
        super.drawScene();
        if (!smoke) return;
        smokeFrame++;
        if (smokeFrame % 3 != 0) return;
        capture(currentPage);
        int next = currentPage.ordinal() + 1;
        if (next >= Page.values().length) {
            Window.requestClose();
        } else {
            show(Page.values()[next]);
            ui.layout();
        }
    }

    /** Writes one deterministic smoke image named after its catalog page. */
    private static void capture(Page page) {
        FrameCapture.save(
                Path.of(
                        "build/ui-showcase",
                        page.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
    }

    /** Destroys UI borrowers before releasing all graphics resources owned by this scene. */
    @Override
    public void dispose() {
        disposeScene();
        if (slugFont != null) slugFont.dispose();
        if (previewTexture != null) previewTexture.dispose();
        if (previewData != null) previewData.dispose();
        if (theme != null) theme.close();
    }
}
