// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lightingstudio;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

import org.joml.Vector3f;
import org.joml.primitives.Rayf;

import valthorne.*;
import valthorne.camera.Camera3D;
import valthorne.camera.OrbitCameraController;
import valthorne.camera.OrthographicCamera3D;
import valthorne.camera.PerspectiveCamera;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.viewport.ScreenViewport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.DoubleConsumer;

/**
 * An editable material and light workbench with camera navigation, selection, undo/redo,
 * persistence and rendering controls. Windows x64 can use Filament; other supported compute hosts
 * use the path tracer.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>This workbench requires OpenGL 4.3 and cannot run on macOS OpenGL. GPU timer queries and
 * renderer resources belong to the current context. Light edits update retained instances; history
 * snapshots copy state before mutation. Saves and captures remain under build output.
 *
 * <p>Run through {@link valthorne.examples.launcher.ExampleLauncher} for help, validated options
 * and platform checks. Study the accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/lighting-studio.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class LightingStudio implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera perspective = new PerspectiveCamera();
    private final OrthographicCamera3D orthographic = new OrthographicCamera3D();
    private final OrbitCameraController orbit = new OrbitCameraController();
    private final Rayf ray = new Rayf();
    private final ArrayList<Light> lights = new ArrayList<>();
    private final ArrayDeque<List<LightState>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final Color[] palette = {
        new Color(1, .78f, .48f, 1),
        new Color(.3f, .65f, 1, 1),
        new Color(1, .18f, .38f, 1),
        new Color(.55f, .35f, 1, 1),
        new Color(.25f, 1, .68f, 1),
        Color.WHITE
    };
    private Model3D bulb, panel;
    private valthorne.graphics.texture.Texture
            referenceTexture; // Owned checker texture for visible material/refraction inspection.
    private ModelInstance3D
            referenceBoard; // Toggleable reference surface behind the material samples.
    private final int[] gpuTimers =
            new int[4]; // Asynchronous frame-time queries owned by this GL context.
    private final boolean[] gpuPending =
            new boolean[4]; // Slots cannot be reused before their results become available.
    private float gpuTime; // Last completed GPU render duration, in milliseconds.
    private PathTracer3D tracer;
    private FilamentRenderer3D filament;
    private boolean useFilament = true;
    private FilamentRenderer3D.Quality filamentQuality = FilamentRenderer3D.Quality.INTERACTIVE;
    private UIRoot ui;
    private ProfessionalTheme theme;
    private NanoPanel toolbar, left, right, footer;
    private NanoLabel status, stats, selection, help;
    private NanoComboBox<String> lightList;
    private final LinkedHashMap<String, NanoSlider> sliders = new LinkedHashMap<>();
    private final LinkedHashMap<String, NanoLabel> sliderLabels = new LinkedHashMap<>();
    private NanoLabel marker;
    private final Vector3f projected = new Vector3f();
    private int markerX = Integer.MIN_VALUE, markerY = Integer.MIN_VALUE;
    private Light selected;
    private int nextId = 1, colorIndex, width, height, vx, vy, vw, vh;
    private boolean topView,
            placing,
            sync = true,
            updating,
            dragging,
            orbiting,
            panning,
            lastLeft,
            lastRight,
            lastMiddle,
            smoke,
            benchmark;
    private float lastX, lastY, flatX, flatY, flatHeight = 10, elapsed, frameTime;
    private int frameCounter, totalFrames;
    private long checkpointTime;
    private String checkpointKey = "";
    private float smokeCameraX, smokeLightX;
    private boolean motionBenchmark,
            progressive; // Automated camera motion and explicit stationary-render selection.
    private final double[] benchmarkTimes = new double[120];
    private final MouseScrollListener wheel =
            event -> {
                if (inViewport() && ui.getCaptured() == null) {
                    if (topView)
                        flatHeight =
                                Math.max(
                                        2,
                                        Math.min(
                                                50,
                                                flatHeight
                                                        * (float)
                                                                Math.exp(
                                                                        -event.preciseYOffset()
                                                                                * .12f)));
                    else orbit.zoom(event.preciseYOffset());
                }
            };

    /** Immutable light transform/appearance snapshot used by undo, redo and persistence. */
    private record LightState(
            int id,
            float x,
            float y,
            float z,
            float radius,
            float power,
            float r,
            float g,
            float b,
            boolean enabled,
            boolean panel) {}

    /**
     * Mutable editor state for a retained emissive light model; changes are applied through the
     * owning rig.
     */
    private static final class Light {
        int id;
        float radius = .3f, power = 16;
        boolean enabled = true, panel;
        final Color color = Color.WHITE.copy();
        final ModelInstance3D instance = new ModelInstance3D();
    }

    /**
     * Starts this application on the process main thread. Prefer the documented Gradle launcher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) throws Exception {
        for (String a : args)
            if (a.startsWith("--visual-validation=")) {
                LightingVisualValidation.run(a.substring(a.indexOf('=') + 1));
                return;
            }
        var app = new LightingStudio();
        for (String a : args) {
            if (a.equals("--smoke")) app.smoke = true;
            if (a.equals("--benchmark")) app.benchmark = true;
            if (a.equals("--benchmark-motion")) {
                app.benchmark = true;
                app.motionBenchmark = true;
            }
            if (a.equals("--progressive")) {
                app.progressive = true;
                app.useFilament = false;
            }
            if (a.equals("--pathtracer")) app.useFilament = false;
            if (a.startsWith("--filament-quality="))
                app.filamentQuality =
                        FilamentRenderer3D.Quality.valueOf(
                                a.substring(a.indexOf('=') + 1).toUpperCase(Locale.ROOT));
        }
        if (!supportsFilament()) app.useFilament = false;
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .contextVersion(4, 3)
                        .size(1440, 900)
                        .depthBits(24)
                        .visible(false)
                        .title("Valthorne | Lighting Studio"));
    }

    /** Returns whether this JVM supports the implemented native texture-sharing path. */
    private static boolean supportsFilament() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        glGenQueries(gpuTimers);
        glfwSetWindowSizeLimits(Window.getAddress(), 1200, 800, GLFW_DONT_CARE, GLFW_DONT_CARE);
        tracer =
                new PathTracer3D()
                        .setQuality(PathTracer3D.Quality.INTERACTIVE)
                        .setSky(.06f, .07f, .09f);
        tracer.setRenderMode(
                progressive
                        ? PathTracer3D.RenderMode.PROGRESSIVE
                        : PathTracer3D.RenderMode.REALTIME);
        if (supportsFilament()) {
            filament = new FilamentRenderer3D();
            filament.setQuality(filamentQuality);
        }
        bulb = ModelBuilder3D.sphere(1, 20, 12);
        panel = ModelBuilder3D.plane(2, 2);
        perspective.setClipPlanes(.05f, 150);
        perspective.setFieldOfViewDegrees(50);
        resetCamera();
        orthographic.setClipPlanes(.05f, 100);
        buildRoom();
        preset(0);
        ui = new UIRoot();
        ui.setClickable(false);
        ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        buildUi();
        resize();
        refresh();
        Mouse.addScrollListener(wheel);
        if (benchmark || useFilament) {
            glfwSwapInterval(0);
            sync = false;
        }
    }

    /**
     * Creates a new material descriptor with the requested appearance. Geometry may share the
     * descriptor while independent edits need a copy.
     */
    private Material3D material(float r, float g, float b, float rough, float metal) {
        return new Material3D()
                .setTint(new Color(r, g, b, 1))
                .setRoughness(rough)
                .setMetallic(metal);
    }

    /**
     * Adds a retained scene instance at the requested world position using the supplied mesh and
     * material.
     */
    private void geometry(Model3D model, float x, float y, float z, Material3D material) {
        scene.add(new ModelInstance3D().setModel(model).setPosition(x, y, z).setMaterial(material));
    }

    /**
     * Builds the material-reference room, including emissive surfaces and the texture board used to
     * judge refraction.
     */
    private void buildRoom() {
        geometry(ModelBuilder3D.box(16, 14, .2f), 0, 0, -.1f, material(.5f, .52f, .55f, .65f, 0));
        geometry(ModelBuilder3D.box(12, .18f, 5), 0, 4, 2.5f, material(.45f, .46f, .48f, .85f, 0));
        geometry(ModelBuilder3D.box(.18f, 8, 5), -5, 0, 2.5f, material(.45f, .43f, .4f, .85f, 0));
        geometry(ModelBuilder3D.box(.18f, 8, 5), 5, 0, 2.5f, material(.17f, .2f, .24f, .85f, 0));
        geometry(
                ModelBuilder3D.sphere(1, 48, 24),
                -2.7f,
                0,
                1.15f,
                material(.95f, .7f, .3f, .15f, 1));
        geometry(
                ModelBuilder3D.sphere(1, 48, 24),
                0,
                .3f,
                1.15f,
                material(1, 1, 1, 0, 0).setTransmission(1));
        geometry(
                ModelBuilder3D.sphere(1, 48, 24),
                2.7f,
                .6f,
                1.15f,
                material(.08f, .33f, .44f, .35f, 0));
        for (int i = 0; i < 3; i++)
            geometry(
                    ModelBuilder3D.cylinder(1.2f, .15f, 32),
                    -2.7f + i * 2.7f,
                    i * .3f,
                    .075f,
                    material(.15f, .17f, .2f, .35f, .5f));
        geometry(ModelBuilder3D.box(1, 1, 1), -3.8f, 2.3f, .5f, material(.64f, .15f, .08f, .5f, 0));
        for (int i = 0; i < 6; i++)
            geometry(
                    ModelBuilder3D.box(.65f, .65f, .5f + i * .15f),
                    -3.5f + i * 1.4f,
                    3.3f,
                    .25f + i * .075f,
                    material(.6f, .63f, .67f, i / 5f, 1));
        try {
            referenceTexture = LightingVisualValidation.checker();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create material reference pattern", e);
        }
        referenceBoard =
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(8, 2.5f))
                        .setPosition(0, 3.85f, 1.55f)
                        .setRotation((float) Math.PI / 2, 0, 0)
                        .setMaterial(new Material3D().setTexture(referenceTexture).setRoughness(1));
        scene.add(referenceBoard);
    }

    /** Restores a close material-inspection view without modifying the light rig. */
    private void resetCamera() {
        orbit.reset();
        orbit.zoom(1);
        orbit.orbit(0, 12);
        flatX = flatY = 0;
        flatHeight = 10;
    }

    /**
     * Creates an editable emissive sphere or panel and registers its initial transform, radius and
     * power.
     */
    private Light addLight(
            float x,
            float y,
            float z,
            Color color,
            float radius,
            float power,
            boolean rectangular) {
        Light light = new Light();
        light.id = nextId++;
        light.color.set(color);
        light.radius = radius;
        light.power = power;
        light.panel = rectangular;
        light.instance.setPosition(x, y, z).setMaterial(new Material3D());
        lights.add(light);
        scene.add(light.instance);
        apply(light);
        return light;
    }

    /**
     * Copies one editable light's settings into its retained model transform and emissive material.
     */
    private void apply(Light light) {
        light.instance
                .setModel(light.panel ? panel : bulb)
                .setScale(light.radius)
                .setVisible(light.enabled);
        light.instance.getMaterial().setEmissive(light.color).setEmissionStrength(light.power);
    }

    /**
     * Replaces the current light arrangement with the selected authored preset and resets
     * selection.
     */
    private void preset(int index) {
        for (Light l : lights) scene.remove(l.instance);
        lights.clear();
        if (index == 2) {
            for (int i = 0; i < 64; i++)
                addLight(
                        -4 + (i % 8) * 1.1f,
                        -3 + (i / 8) * .85f,
                        2.8f,
                        palette[i % 6],
                        .09f,
                        8,
                        false);
        } else if (index == 1) {
            addLight(-3, -1, 3, palette[2], .65f, 28, false);
            addLight(3, 1, 3, palette[3], .7f, 24, false);
            addLight(0, 2, 4, palette[4], .8f, 18, true);
        } else {
            addLight(-1, 0, 5, Color.WHITE, 1.1f, 12, true);
            addLight(3, -1, 3, new Color(.8f, .9f, 1, 1), .24f, 40, false);
            addLight(-3, 2, 3, palette[5], .25f, 24, false);
        }
        selected = lights.getFirst();
        if (ui != null) refresh();
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private static NanoLabel label(String value, float size) {
        NanoLabel l = new NanoLabel(value);
        l.setStyle(NanoLabel.FONT_SIZE_KEY, size);
        l.getLayout().noShrink();
        return l;
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private static NanoButton button(String value, Runnable action) {
        NanoButton b = new NanoButton(value).action(n -> action.run());
        b.getLayout().height(34).noShrink();
        return b;
    }

    /** Creates a locally styled UI container used to group editor controls. */
    private NanoPanel box() {
        NanoPanel p = new NanoPanel();
        p.getLayout().absolute().column().padding(16).gap(10);
        ui.add(p);
        return p;
    }

    /**
     * Builds the toolbar, scene panels, light property editors and status footer around the render
     * viewport.
     */
    private void buildUi() {
        toolbar = box();
        toolbar.getLayout().row().itemsCenter().gap(12);
        toolbar.add(label("VALTHORNE / LIGHTING STUDIO", 22));
        toolbar.add(
                button(
                        "3D / Top view",
                        () -> {
                            topView = !topView;
                            placing = false;
                            message(
                                    topView
                                            ? "Top view: middle-drag to pan"
                                            : "3D view: right-drag to orbit");
                        }));
        toolbar.add(button("Reset camera", this::resetCamera));
        toolbar.add(button("Undo", this::undo));
        toolbar.add(button("Redo", this::redo));
        toolbar.add(button("Save rig", this::save));
        toolbar.add(button("Load rig", this::load));
        left = box();
        left.add(label("LIGHT RIG", 14));
        var presets =
                new NanoComboBox<String>()
                        .items(List.of("Material studio", "Neon gallery", "Stress / 64 lights"))
                        .selectedIndex(0)
                        .onChange(
                                value -> {
                                    checkpoint("preset", false);
                                    preset(
                                            value.startsWith("Neon")
                                                    ? 1
                                                    : value.startsWith("Stress") ? 2 : 0);
                                });
        presets.getLayout().height(36).widthPercent(100);
        left.add(presets);
        lightList =
                new NanoComboBox<String>()
                        .onChange(
                                value -> {
                                    if (!updating) {
                                        int index = lightList.getSelectedIndex();
                                        if (index >= 0 && index < lights.size()) {
                                            selected = lights.get(index);
                                            refresh();
                                        }
                                    }
                                });
        lightList.getLayout().height(36).widthPercent(100);
        left.add(lightList);
        left.add(
                button(
                        "+ Place colored light",
                        () -> {
                            placing = true;
                            message("Click a surface to place a light. Escape cancels.");
                        }));
        left.add(
                button(
                        "Duplicate selected",
                        () -> {
                            if (selected == null) return;
                            checkpoint("duplicate", false);
                            var p = selected.instance.getPosition();
                            selected =
                                    addLight(
                                            p.x() + .6f,
                                            p.y(),
                                            p.z(),
                                            selected.color,
                                            selected.radius,
                                            selected.power,
                                            selected.panel);
                            refresh();
                        }));
        left.add(
                button(
                        "Delete selected",
                        () -> {
                            if (selected == null) return;
                            checkpoint("delete", false);
                            scene.remove(selected.instance);
                            lights.remove(selected);
                            selected = lights.isEmpty() ? null : lights.getLast();
                            refresh();
                        }));
        left.add(label("COLOR PALETTE", 13));
        String[] colors = {"Warm amber", "Ice blue", "Rose", "Violet", "Mint", "Neutral white"};
        for (int i = 0; i < colors.length; i++) {
            int index = i;
            NanoButton b =
                    button(
                            colors[i],
                            () -> {
                                colorIndex = index;
                                if (selected != null) {
                                    checkpoint("color", false);
                                    selected.color.set(palette[index]);
                                    apply(selected);
                                    refresh();
                                }
                                message("Placement color: " + colors[index]);
                            });
            b.setStyle(NanoButton.TEXT_COLOR_KEY, palette[i]);
            left.add(b);
        }
        left.add(label("NAVIGATION", 13));
        help =
                label(
                        "Right drag   Orbit\n"
                                + "Middle drag   Pan\n"
                                + "Wheel   Zoom\n"
                                + "Left drag   Move light\n"
                                + "Shift + drag   Height\n"
                                + "Click   Select light\n"
                                + "Esc   Cancel placement",
                        13);
        left.add(help);
        right = box();
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().widthPercent(100).height(0).grow().minHeight(0);
        right.add(scroll);
        var fields = new NanoContainer();
        fields.getLayout().widthPercent(100).column().gap(8).noShrink();
        scroll.setContent(fields);
        selection = label("LIGHT PROPERTIES", 16);
        fields.add(selection);
        fields.add(
                button(
                        "Enable / disable",
                        () -> {
                            if (selected != null) {
                                checkpoint("enabled", false);
                                selected.enabled = !selected.enabled;
                                apply(selected);
                                refresh();
                            }
                        }));
        fields.add(
                button(
                        "Sphere / area panel",
                        () -> {
                            if (selected != null) {
                                checkpoint("shape", false);
                                selected.panel = !selected.panel;
                                apply(selected);
                                refresh();
                            }
                        }));
        slider(
                fields,
                "Power",
                0,
                80,
                16,
                v -> {
                    selected.power = (float) v;
                });
        slider(
                fields,
                "Radius",
                .08f,
                1.8f,
                .3f,
                v -> {
                    selected.radius = (float) v;
                });
        slider(fields, "X", -8, 8, 0, v -> selected.instance.getPosition().x = (float) v);
        slider(fields, "Y", -6, 6, 0, v -> selected.instance.getPosition().y = (float) v);
        slider(fields, "Height", .15f, 7, 3, v -> selected.instance.getPosition().z = (float) v);
        slider(
                fields,
                "Red",
                0,
                1,
                1,
                v -> selected.color.set((float) v, selected.color.g(), selected.color.b(), 1));
        slider(
                fields,
                "Green",
                0,
                1,
                1,
                v -> selected.color.set(selected.color.r(), (float) v, selected.color.b(), 1));
        slider(
                fields,
                "Blue",
                0,
                1,
                1,
                v -> selected.color.set(selected.color.r(), selected.color.g(), (float) v, 1));
        fields.add(label("RENDERING", 14));
        var algorithms =
                filament != null
                        ? List.of(
                                "Filament / real-time PBR",
                                "Realtime / temporal GI",
                                "Progressive / bounced light")
                        : List.of("Realtime / temporal GI", "Progressive / bounced light");
        var algorithm =
                new NanoComboBox<String>()
                        .items(algorithms)
                        .selectedIndex(
                                useFilament
                                        ? 0
                                        : (filament != null ? 1 : 0) + (progressive ? 1 : 0))
                        .onChange(
                                value -> {
                                    useFilament = value.startsWith("Filament");
                                    tracer.setRenderMode(
                                            value.startsWith("Realtime")
                                                    ? PathTracer3D.RenderMode.REALTIME
                                                    : PathTracer3D.RenderMode.PROGRESSIVE);
                                    message(
                                            useFilament
                                                    ? "Filament: real-time materials and shadow"
                                                            + " maps"
                                                    : value.startsWith("Realtime")
                                                            ? "Realtime: full resolution with"
                                                                    + " lighting history"
                                                            : "Progressive: hold the camera still"
                                                                    + " to reduce noise");
                                });
        algorithm.getLayout().height(36).widthPercent(100);
        fields.add(algorithm);
        var quality =
                new NanoComboBox<String>()
                        .items(List.of("Interactive", "High", "Ultra"))
                        .selectedIndex(filamentQuality.ordinal())
                        .onChange(
                                value -> {
                                    tracer.setQuality(
                                            PathTracer3D.Quality.valueOf(
                                                    value.toUpperCase(Locale.ROOT)));
                                    if (filament != null)
                                        filament.setQuality(
                                                FilamentRenderer3D.Quality.valueOf(
                                                        value.toUpperCase(Locale.ROOT)));
                                    message("Quality: " + value);
                                });
        quality.getLayout().height(36).widthPercent(100);
        fields.add(quality);
        fields.add(
                button(
                        "Smoothing on / off",
                        () -> {
                            tracer.setDenoising(!tracer.isDenoising());
                            if (filament != null) filament.setAntiAliasing(tracer.isDenoising());
                        }));
        fields.add(
                button(
                        "Reference pattern on / off",
                        () -> referenceBoard.setVisible(!referenceBoard.isRenderableVisible())));
        var exposure =
                new NanoSlider(.1f, 3, 1)
                        .action(
                                s -> {
                                    tracer.setExposure(s.getValue());
                                    if (filament != null) filament.setExposure(s.getValue());
                                });
        exposure.getLayout().widthPercent(100).height(28);
        fields.add(label("Exposure", 13));
        fields.add(exposure);
        fields.add(
                button(
                        "VSync on / off",
                        () -> {
                            sync = !sync;
                            glfwSwapInterval(sync ? 1 : 0);
                        }));
        fields.add(
                button(
                        "Refresh lighting",
                        () -> {
                            tracer.invalidate();
                            if (filament != null) filament.invalidate();
                        }));
        footer = box();
        footer.getLayout().row().itemsCenter().gap(24).padding(10);
        stats = label("Preparing renderer...", 14);
        stats.getLayout().width(600);
        status = label("Ready / select a light or place a new one", 14);
        status.getLayout().width(0).grow();
        footer.add(stats);
        footer.add(status);
        marker = label("[ SELECTED ]", 13);
        marker.setStyle(NanoLabel.COLOR_KEY, theme.accent);
        marker.setClickable(false);
        marker.setFocusable(false);
        marker.getLayout().absolute().width(120).height(24);
        ui.add(marker);
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(
            NanoContainer parent,
            String name,
            float min,
            float max,
            float initial,
            DoubleConsumer edit) {
        NanoLabel title = label(name, 13);
        parent.add(title);
        NanoSlider slider =
                new NanoSlider(min, max, initial)
                        .action(
                                s -> {
                                    if (updating || selected == null) return;
                                    checkpoint(name, true);
                                    edit.accept(s.getValue());
                                    apply(selected);
                                    title.text(
                                            name
                                                    + "  "
                                                    + String.format(
                                                            Locale.ROOT, "%.2f", s.getValue()));
                                });
        slider.getLayout().height(26).widthPercent(100);
        parent.add(slider);
        sliders.put(name, slider);
        sliderLabels.put(name, title);
    }

    /**
     * Synchronizes retained controls to the selected light while suppressing recursive edit
     * callbacks.
     */
    private void refresh() {
        if (ui == null) return;
        updating = true;
        try {
            lightList
                    .items(
                            lights.stream()
                                    .map(l -> "Light " + l.id + (l.enabled ? "" : " / off"))
                                    .toList())
                    .selectedIndex(selected == null ? -1 : lights.indexOf(selected));
            selection.text(
                    selected == null
                            ? "NO LIGHT SELECTED"
                            : "LIGHT "
                                    + selected.id
                                    + " / "
                                    + (selected.panel ? "AREA PANEL" : "SPHERE")
                                    + (selected.enabled ? "" : " / OFF"));
            for (NanoSlider slider : sliders.values()) slider.setEnabled(selected != null);
            if (selected != null) {
                var p = selected.instance.getPosition();
                float[] values = {
                    selected.power,
                    selected.radius,
                    p.x(),
                    p.y(),
                    p.z(),
                    selected.color.r(),
                    selected.color.g(),
                    selected.color.b()
                };
                int i = 0;
                for (var entry : sliders.entrySet()) {
                    float value = values[i++];
                    entry.getValue().value(value);
                    sliderLabels
                            .get(entry.getKey())
                            .text(
                                    entry.getKey()
                                            + "  "
                                            + String.format(Locale.ROOT, "%.2f", value));
                }
            }
        } finally {
            updating = false;
        }
    }

    /** Updates the status label with user-visible feedback from the last editor operation. */
    private void message(String value) {
        if (status != null) status.text(value);
    }

    /**
     * Returns immutable light-state snapshots for undo/redo; this overload does not read
     * framebuffer pixels.
     */
    private List<LightState> capture() {
        ArrayList<LightState> state = new ArrayList<>(lights.size());
        for (Light l : lights) {
            var p = l.instance.getPosition();
            state.add(
                    new LightState(
                            l.id,
                            p.x(),
                            p.y(),
                            p.z(),
                            l.radius,
                            l.power,
                            l.color.r(),
                            l.color.g(),
                            l.color.b(),
                            l.enabled,
                            l.panel));
        }
        return state;
    }

    /**
     * Records a pre-edit light state, optionally coalescing repeated edits with the same key into
     * one undo operation.
     */
    private void checkpoint(String key, boolean coalesce) {
        long now = System.nanoTime();
        if (coalesce && key.equals(checkpointKey) && now - checkpointTime < 400_000_000L) {
            checkpointTime = now;
            return;
        }
        undo.addLast(capture());
        if (undo.size() > 64) undo.removeFirst();
        redo.clear();
        checkpointKey = key;
        checkpointTime = now;
    }

    /** Rebuilds editable lights from a saved state list and refreshes selection and controls. */
    private void restore(List<LightState> state) {
        for (Light l : lights) scene.remove(l.instance);
        lights.clear();
        for (LightState s : state) {
            Light l =
                    addLight(
                            s.x, s.y, s.z, new Color(s.r, s.g, s.b, 1), s.radius, s.power, s.panel);
            l.id = s.id;
            l.enabled = s.enabled;
            apply(l);
        }
        selected = lights.isEmpty() ? null : lights.getFirst();
        refresh();
    }

    /** Moves the current state to redo history and restores the latest available undo state. */
    private void undo() {
        if (undo.isEmpty()) return;
        redo.addLast(capture());
        restore(undo.removeLast());
        checkpointKey = "";
        message("Undo applied");
    }

    /** Restores the latest redo state while preserving the current state in undo history. */
    private void redo() {
        if (redo.isEmpty()) return;
        undo.addLast(capture());
        restore(redo.removeLast());
        checkpointKey = "";
        message("Redo applied");
    }

    /**
     * Writes the current light arrangement to the studio's documented properties file under build
     * output.
     */
    private void save() {
        try {
            Properties p = new Properties();
            p.setProperty("count", Integer.toString(lights.size()));
            int i = 0;
            for (LightState s : capture()) {
                p.setProperty(
                        "light." + i++,
                        s.x + "," + s.y + "," + s.z + "," + s.radius + "," + s.power + "," + s.r
                                + "," + s.g + "," + s.b + "," + s.enabled + "," + s.panel);
            }
            Path path = Path.of("build/lighting-studio/rig.properties");
            Files.createDirectories(path.getParent());
            try (var out = Files.newOutputStream(path)) {
                p.store(out, "Valthorne light rig");
            }
            message("Saved build/lighting-studio/rig.properties");
        } catch (Exception e) {
            message("Save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the saved light arrangement and reports file/format errors through the studio status
     * UI.
     */
    private void load() {
        try {
            Properties p = new Properties();
            try (var in = Files.newInputStream(Path.of("build/lighting-studio/rig.properties"))) {
                p.load(in);
            }
            int count = Integer.parseInt(p.getProperty("count"));
            if (count < 0 || count > 256)
                throw new IllegalArgumentException("Light count must be 0–256");
            ArrayList<LightState> state = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String[] s = p.getProperty("light." + i).split(",");
                if (s.length != 10) throw new IllegalArgumentException("Invalid light");
                float[] v = new float[8];
                for (int j = 0; j < 8; j++) {
                    v[j] = Float.parseFloat(s[j]);
                    if (!Float.isFinite(v[j]))
                        throw new IllegalArgumentException("Non-finite light");
                }
                if (v[3] < .08f || v[3] > 1.8f || v[4] < 0 || v[4] > 80)
                    throw new IllegalArgumentException("Invalid radius/power");
                state.add(
                        new LightState(
                                i + 1,
                                v[0],
                                v[1],
                                v[2],
                                v[3],
                                v[4],
                                v[5],
                                v[6],
                                v[7],
                                Boolean.parseBoolean(s[8]),
                                Boolean.parseBoolean(s[9])));
            }
            checkpoint("load", false);
            restore(state);
            message("Rig loaded");
        } catch (Exception e) {
            message("Load failed: " + e.getMessage());
        }
    }

    /**
     * Recomputes viewport and UI layout from the current window dimensions without recreating scene
     * content.
     */
    private void resize() {
        width = Window.getWidth();
        height = Window.getHeight();
        vx = 248;
        vy = 66;
        vw = Math.max(1, width - 568);
        vh = Math.max(1, height - 148);
        toolbar.getLayout().left(12).top(12).width(Math.max(1, width - 24)).height(58);
        left.getLayout().left(12).top(82).width(224).height(Math.max(1, height - 148));
        right.getLayout()
                .left(Math.max(260, width - 308))
                .top(82)
                .width(296)
                .height(Math.max(1, height - 148));
        footer.getLayout()
                .left(12)
                .top(Math.max(82, height - 54))
                .width(Math.max(1, width - 24))
                .height(42);
        ui.layout();
    }

    /**
     * Returns whether the pointer is inside the scene viewport rather than an adjacent editor
     * panel.
     */
    private boolean inViewport() {
        float x = Mouse.getX(), y = Mouse.getY();
        return x >= vx && x < vx + vw && y >= vy && y < vy + vh;
    }

    /** Returns the borrowed perspective or top-down camera selected by the current view mode. */
    private Camera3D activeCamera() {
        return topView ? orthographic : perspective;
    }

    /** Applies the orbit or top-down view state to the active camera and refreshes projection. */
    private void updateCamera() {
        orbit.apply(perspective);
        orthographic.setPosition(flatX, flatY, 25);
        orthographic.lookAt(flatX, flatY, 0, 0, 1, 0);
        orthographic.setWorldHeight(flatHeight);
        activeCamera().rebuild(vw, vh);
    }

    /**
     * Converts a pointer position into the reusable world-space picking ray for the active camera.
     */
    private void pointRay(float x, float y) {
        activeCamera().screenPointToRay(x, y, vx, vy, vw, vh, ray);
    }

    /**
     * Selects an existing light or places a new one using the current view and world-space picking
     * ray.
     */
    private void pickOrPlace(float x, float y) {
        pointRay(x, y);
        if (placing) {
            checkpoint("place", false);
            var hit = scene.pick(ray);
            float px, py, pz;
            if (hit != null) {
                px = hit.position().x();
                py = hit.position().y();
                pz = hit.position().z() + .8f;
            } else {
                float t = Math.abs(ray.dZ) > .0001f ? -ray.oZ / ray.dZ : -1;
                if (t < 0) {
                    message("Aim at a surface");
                    return;
                }
                px = ray.oX + ray.dX * t;
                py = ray.oY + ray.dY * t;
                pz = 2;
            }
            if (lights.size() >= 256) {
                message("Maximum 256 lights");
                return;
            }
            selected = addLight(px, py, Math.min(7, pz), palette[colorIndex], .3f, 20, false);
            placing = false;
            refresh();
            message("Placed light " + selected.id);
            return;
        }
        Light nearest = null;
        float distance = Float.POSITIVE_INFINITY;
        for (Light l : lights) {
            float t = l.instance.intersect(ray);
            if (t < distance) {
                distance = t;
                nearest = l;
            }
        }
        if (nearest != null) {
            selected = nearest;
            checkpoint("drag", false);
            dragging = true;
            refresh();
            message("Moving light " + selected.id);
        }
    }

    /**
     * Processes input and advances this demo using elapsed seconds; rendering and resource
     * destruction remain in their lifecycle callbacks.
     *
     * @param delta elapsed time in seconds
     */
    @Override
    public void update(float delta) {
        if (width != Window.getWidth() || height != Window.getHeight()) resize();
        updateCamera();
        ui.update(delta);
        if (motionBenchmark) orbit.orbit(.65f, 0);
        float x = Mouse.getX(), y = Mouse.getY();
        boolean l = Mouse.isButtonDown(Mouse.LEFT),
                r = Mouse.isButtonDown(Mouse.RIGHT),
                m = Mouse.isButtonDown(Mouse.MIDDLE);
        boolean free =
                inViewport()
                        && ui.getCaptured() == null
                        && (ui.getHovered() == null || ui.getHovered() == ui);
        if (r && !lastRight && free) orbiting = true;
        if (m && !lastMiddle && free) panning = true;
        if (l && !lastLeft && free) pickOrPlace(x, y);
        float dx = x - lastX, dy = y - lastY;
        if (r && orbiting && !topView) orbit.orbit(dx, -dy);
        if (m && panning) {
            if (topView) {
                flatX -= dx * flatHeight / vh;
                flatY -= dy * flatHeight / vh;
            } else orbit.pan(dx, -dy, vh);
        }
        if (l && dragging && selected != null && (dx != 0 || dy != 0)) {
            var p = selected.instance.getPosition();
            if (Keyboard.isKeyDown(GLFW_KEY_LEFT_SHIFT) || Keyboard.isKeyDown(GLFW_KEY_RIGHT_SHIFT))
                p.z = Math.max(.15f, Math.min(7, p.z() + dy * .02f));
            else {
                pointRay(x, y);
                if (Math.abs(ray.dZ) > .0001f) {
                    float t = (p.z() - ray.oZ) / ray.dZ;
                    if (t > 0)
                        p.set(
                                Math.max(-8, Math.min(8, ray.oX + ray.dX * t)),
                                Math.max(-6, Math.min(6, ray.oY + ray.dY * t)),
                                p.z());
                }
            }
            refresh();
        }
        if (!l) dragging = false;
        if (!r) orbiting = false;
        if (!m) panning = false;
        lastLeft = l;
        lastRight = r;
        lastMiddle = m;
        lastX = x;
        lastY = y;
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) {
            placing = false;
            dragging = orbiting = panning = false;
            message("Ready");
        }
        elapsed += delta;
        frameCounter++;
        if (elapsed >= .3f) {
            stats.text(
                    useFilament
                            ? String.format(
                                    Locale.ROOT,
                                    "%.0f FPS | Render %.1f ms | Filament PBR | %d lights",
                                    frameCounter / elapsed,
                                    frameTime,
                                    lights.size())
                            : String.format(
                                    Locale.ROOT,
                                    "%.0f FPS | GPU %.1f ms | %s | %d lights",
                                    frameCounter / elapsed,
                                    gpuTime,
                                    tracer.getAccumulatedSamples() >= tracer.getMaxSamples()
                                            ? "Cached image"
                                            : tracer.getRenderMode()
                                                            == PathTracer3D.RenderMode.REALTIME
                                                    ? "Temporal GI"
                                                    : tracer.getAccumulatedSamples() + " samples",
                                    lights.size()));
            elapsed = 0;
            frameCounter = 0;
        }
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        if (width < 1 || height < 1) return;
        long start = System.nanoTime();
        int timerSlot = totalFrames % gpuTimers.length;
        if (gpuPending[timerSlot]
                && glGetQueryObjecti(gpuTimers[timerSlot], GL_QUERY_RESULT_AVAILABLE) != 0) {
            gpuTime = glGetQueryObjectui64(gpuTimers[timerSlot], GL_QUERY_RESULT) * 1e-6f;
            gpuPending[timerSlot] = false;
        }
        // A query must not span a synchronous render in Filament's other GL
        // context. Some drivers serialize contexts while such a query is active.
        boolean timing = !useFilament && !gpuPending[timerSlot];
        if (timing) glBeginQuery(GL_TIME_ELAPSED, gpuTimers[timerSlot]);
        Window.clear(new Color(.035f, .048f, .07f, 1));
        updateCamera();
        glViewport(vx, vy, vw, vh);
        if (useFilament) filament.render(scene, activeCamera());
        else tracer.render(scene, activeCamera());
        glViewport(0, 0, width, height);
        if (selected != null) {
            activeCamera().project(selected.instance.getPosition(), vx, vy, vw, vh, projected);
            boolean visible =
                    selected.enabled
                            && projected.z() > 0
                            && projected.z() < 1
                            && projected.x() > vx + 60
                            && projected.x() < vx + vw - 60
                            && projected.y() > vy
                            && projected.y() < vy + vh - 24;
            marker.setVisible(visible);
            int mx = (int) projected.x() - 50, my = height - (int) projected.y() - 30;
            if (visible && (mx != markerX || my != markerY)) {
                marker.getLayout().left(mx).top(my);
                markerX = mx;
                markerY = my;
            }
        } else marker.setVisible(false);
        ui.draw();
        if (timing) {
            glEndQuery(GL_TIME_ELAPSED);
            gpuPending[timerSlot] = true;
        }
        if (benchmark) glFinish();
        frameTime = (System.nanoTime() - start) * 1e-6f;
        totalFrames++;
        if (totalFrames == 1 && !smoke && !benchmark) glfwShowWindow(Window.getAddress());
        if (benchmark && totalFrames > 30 && totalFrames <= 150)
            benchmarkTimes[totalFrames - 31] = frameTime;
        if (motionBenchmark && totalFrames == 60)
            captureImage(
                    useFilament
                            ? "motion-filament"
                            : progressive ? "motion-progressive" : "motion-realtime");
        if (benchmark && totalFrames == 150) {
            if (useFilament) captureImage("filament");
            Arrays.sort(benchmarkTimes);
            System.out.printf(
                    Locale.ROOT,
                    "STUDIO: mean %.3f ms, median %.3f ms, p95 %.3f ms%n",
                    Arrays.stream(benchmarkTimes).average().orElse(0),
                    benchmarkTimes[60],
                    benchmarkTimes[114]);
            Window.requestClose();
        }
        if (smoke) {
            smokeInput();
            if (totalFrames == 60) {
                captureImage("studio");
                checkpoint("smoke", false);
                selected = addLight(1, -1, 3, palette[2], .4f, 24, false);
                refresh();
            }
            if (totalFrames == 90) {
                selected.instance.setPosition(-1, -1, 2);
                refresh();
            }
            if (totalFrames == 110) {
                undo();
                redo();
                topView = true;
                orbit.zoom(2);
            }
            if (totalFrames == 150) {
                captureImage("top-view");
                if (lights.size() != 4) throw new AssertionError("Light history failed");
                if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
                System.out.println(
                        "Studio smoke passed: native orbit, wheel zoom, UI placement tool, surface"
                                + " placement, light dragging, undo/redo, 2D/3D, GL checks.");
                Window.requestClose();
            }
        }
    }

    /**
     * Injects native pointer callbacks for deterministic smoke interaction, using window
     * coordinates and GLFW button/action constants.
     */
    private void pointer(float x, float y, int button, int action) {
        long window = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(window, null);
        glfwSetCursorPosCallback(window, cursor);
        cursor.invoke(window, x, height - y);
        if (button >= 0) {
            var buttons = glfwSetMouseButtonCallback(window, null);
            glfwSetMouseButtonCallback(window, buttons);
            buttons.invoke(window, button, action, 0);
        }
    }

    /**
     * Advances the scripted native-input sequence and asserts that the corresponding camera,
     * selection and UI state changes occur.
     */
    private void smokeInput() {
        float x = vx + vw * .4f, y = vy + vh * .3f;
        if (totalFrames == 5) {
            smokeCameraX = perspective.getPosition().x();
            pointer(x, y, GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        }
        if (totalFrames == 6) pointer(x + 60, y + 20, -1, 0);
        if (totalFrames == 7) {
            pointer(x + 60, y + 20, GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            if (Math.abs(perspective.getPosition().x() - smokeCameraX) < .1f)
                throw new AssertionError(
                        "Native orbit failed; hovered="
                                + ui.getHovered()
                                + ", captured="
                                + ui.getCaptured()
                                + ", inViewport="
                                + inViewport()
                                + ", held="
                                + Mouse.isButtonDown(Mouse.RIGHT)
                                + ", orbiting="
                                + orbiting);
        }
        if (totalFrames == 9) {
            orbit.reset();
            pointer(100, height - 230, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (totalFrames == 10) pointer(100, height - 230, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        if (totalFrames == 11) {
            if (!placing) throw new AssertionError("Placement UI button did not activate");
            activeCamera().project(2, -2, 0, vx, vy, vw, vh, projected);
            pointRay(projected.x(), projected.y());
            if (totalFrames == 16 && !Float.isFinite(selected.instance.intersect(ray)))
                throw new AssertionError(
                        "Projected light cannot be picked: " + projected.x() + "," + projected.y());
            pointer(projected.x(), projected.y(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (totalFrames == 12)
            pointer(Mouse.getX(), Mouse.getY(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        if (totalFrames == 13) {
            if (lights.size() != 4) throw new AssertionError("Native surface placement failed");
            undo();
        }
        if (totalFrames == 16) {
            selected = lights.get(1);
            smokeLightX = selected.instance.getPosition().x();
            activeCamera().project(selected.instance.getPosition(), vx, vy, vw, vh, projected);
            pointRay(projected.x(), projected.y());
            if (totalFrames == 16 && !Float.isFinite(selected.instance.intersect(ray)))
                throw new AssertionError(
                        "Projected light cannot be picked: " + projected.x() + "," + projected.y());
            pointer(projected.x(), projected.y(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (totalFrames == 17) {
            if (!dragging)
                throw new AssertionError(
                        "Drag did not start; hover="
                                + ui.getHovered()
                                + ", capture="
                                + ui.getCaptured()
                                + ", held="
                                + Mouse.isButtonDown(Mouse.LEFT)
                                + ", last="
                                + lastLeft);
            pointer(Mouse.getX() + 35, Mouse.getY(), -1, 0);
        }
        if (totalFrames == 18) {
            pointer(Mouse.getX(), Mouse.getY(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            if (Math.abs(selected.instance.getPosition().x() - smokeLightX) < .05f)
                throw new AssertionError(
                        "Native light dragging failed; drag="
                                + dragging
                                + ", hover="
                                + ui.getHovered()
                                + ", capture="
                                + ui.getCaptured()
                                + ", xy="
                                + Mouse.getX()
                                + ","
                                + Mouse.getY()
                                + ", viewport="
                                + inViewport()
                                + ", position="
                                + selected.instance.getPosition());
        }
        if (totalFrames == 20) {
            pointer(x, y, -1, 0);
            float before = orbit.getDistance();
            long window = Window.getAddress();
            var scroll = glfwSetScrollCallback(window, null);
            glfwSetScrollCallback(window, scroll);
            scroll.invoke(window, 0, 2);
            if (orbit.getDistance() >= before) throw new AssertionError("Native wheel zoom failed");
        }
        if (totalFrames == 22) {
            orbit.reset();
            preset(0);
        }
    }

    /** Saves the composed window framebuffer as a PNG under this demo's ignored build directory. */
    private void captureImage(String name) {
        FrameCapture.save(Path.of("build/lighting-studio", name + ".png"));
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        Mouse.removeScrollListener(wheel);
        ui.dispose();
        theme.close();
        tracer.close();
        if (filament != null) filament.close();
        if (referenceTexture != null) referenceTexture.dispose();
        glDeleteQueries(gpuTimers);
    }
}
