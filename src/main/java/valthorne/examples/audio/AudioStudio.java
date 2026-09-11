// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.audio;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import valthorne.*;
import valthorne.audio.AudioFormat;
import valthorne.audio.sound.SoundArea;
import valthorne.audio.sound.SoundData;
import valthorne.audio.sound.SoundPlayer;
import valthorne.camera.PerspectiveCamera;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.viewport.PerspectiveViewport;
import valthorne.viewport.ScreenViewport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A spatial sound-area laboratory with synthesized tones, a movable listener, editable
 * spherical/box coverage, and retained wave indicators. Playback starts paused.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>Each zone owns its player. Wave rings illustrate playback and gain; they are not a physical
 * acoustic simulation. The listener is independent of the viewing camera. Input, scene changes and
 * player lifetime are coordinated by the application callbacks.
 *
 * <p>Run through {@link valthorne.examples.launcher.ExampleLauncher} for help, validated options
 * and platform checks. Study the accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/audio.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class AudioStudio implements Application {
    private final boolean smoke;
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final Vector3f listener = new Vector3f(-4, -1, 2);
    private final ArrayList<ModelInstance3D> grid = new ArrayList<>();
    private final Zone[] zones = new Zone[2];
    private final Vector3f projected = new Vector3f();
    private Model3D unitBox, unitSphere;
    private ModelBatch3D batch;
    private MeshRenderState3D state;
    private PerspectiveViewport viewport;
    private ModelInstance3D listenerMarker;
    private UIRoot ui;
    private ProfessionalTheme theme;
    private NanoContainer fields;
    private NanoLabel stats, meters;
    private int selected, frames, fpsFrames;
    private float azimuth = -1.1f, distance = 17, elapsed;
    private boolean down, dragging, syncing;
    private final ArrayList<NanoSlider> sliders = new ArrayList<>();
    private boolean previewWaves;
    private NanoButton previewButton;

    /**
     * Records whether the studio should execute its bounded interaction audit instead of waiting
     * for user input.
     */
    private AudioStudio(boolean smoke) {
        this.smoke = smoke;
    }

    /**
     * Starts this application on the process main thread. Use the shared ExampleLauncher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        var app = new AudioStudio(List.of(args).contains("--smoke"));
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .title("Valthorne | Audio Studio")
                        .samples(32)
                        .size(1320, 860)
                        .depthBits(24)
                        .swapInterval(SwapInterval.OFF)
                        .visible(!app.smoke));
    }

    /**
     * Retained audio region: owns its source/player and boundary indicators while the studio owns
     * the shared scene.
     */
    private final class Zone {
        final String name;
        final SoundPlayer player;
        final Material3D core, outer;
        final ArrayList<ModelInstance3D> guides = new ArrayList<>();
        final ModelInstance3D marker;
        float x, y, z = 2, a = 2, b = 2, c = 2, fade = 2, volume = .25f, pitch = 1;
        boolean box, playing, looping = true, muted;
        SoundArea area;
        final ArrayList<Wave> waves = new ArrayList<>();
        float wavePhase, waveSpeed = .4f;

        /**
         * Creates a named sound region, synthesized source and visualization at its initial
         * world-space position.
         */
        Zone(String name, float x, float frequency, boolean box, Color color) {
            this.name = name;
            this.x = x;
            this.box = box;
            core = material(color);
            outer =
                    material(
                            new Color(
                                    color.getRed() / 255f * .5f,
                                    color.getGreen() / 255f * .5f,
                                    color.getBlue() / 255f * .5f,
                                    1));
            player = Audio.create(tone(frequency));
            player.setLooping(true);
            player.setVolume(volume);
            marker = new ModelInstance3D().setModel(unitSphere).setScale(.28f).setMaterial(core);
            rebuild();
            for (int i = 0; i < 3; i++) waves.add(new Wave(this, i / 3f));
        }

        /**
         * Reconstructs the region boundary visuals after shape or extent edits without moving the
         * audio source.
         */
        void rebuild() {
            area = box ? SoundArea.box(x, y, z, a, b, c, fade) : SoundArea.sphere(x, y, z, a, fade);
            player.setArea(area);
            marker.setPosition(x, y, z);
            guides.clear();
            if (box) {
                for (int corner = 0; corner < 8; corner++)
                    for (int axis = 0; axis < 3; axis++) {
                        if ((corner & (1 << axis)) != 0) continue;
                        line(guides, corner(corner), corner(corner | (1 << axis)), .07f, core);
                    }
            }
            for (int plane = 0; plane < 3; plane++) {
                if (!box) contour(plane, 0, core);
                contour(plane, fade, outer);
            }
        }

        /**
         * Updates visibility of the retained wave indicators to match playback and zone enablement.
         */
        boolean showWaves() {
            return previewWaves || (playing && !muted && volume > 0);
        }

        /**
         * Advances the retained wave phase by elapsed seconds without reallocating contour
         * geometry.
         */
        void animateWaves(float dt) {
            if (!showWaves()) return;
            wavePhase = (wavePhase + dt * waveSpeed * pitch) % 1;
            for (Wave wave : waves) wave.update(this);
        }

        /** Returns the selected local box corner used to build the region outline. */
        Vector3f corner(int index) {
            return new Vector3f(
                    x + ((index & 1) == 0 ? -a : a),
                    y + ((index & 2) == 0 ? -b : b),
                    z + ((index & 4) == 0 ? -c : c));
        }

        // Orthogonal cross-sections, not a solid shell. Rounded box corners follow
        // the same Euclidean boundary distance used by SoundArea.
        /**
         * Builds a retained contour in the requested principal plane using the zone extent plus a
         * visual expansion offset.
         */
        void contour(int plane, float extra, Material3D material) {
            float u = a, v = plane == 0 ? b : c;
            if (plane == 2) u = b;
            Vector3f previous = null;
            for (int i = 0; i <= 64; i++) {
                double angle = (i % 64) * Math.PI * 2 / 64;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                float px = box ? Math.copySign(u, cos) + extra * cos : (a + extra) * cos;
                float py = box ? Math.copySign(v, sin) + extra * sin : (a + extra) * sin;
                Vector3f next =
                        switch (plane) {
                            case 0 -> new Vector3f(x + px, y + py, z);
                            case 1 -> new Vector3f(x + px, y, z + py);
                            default -> new Vector3f(x, y + px, z + py);
                        };
                if (previous != null)
                    line(guides, previous, next, extra == 0 ? .07f : .045f, material);
                previous = next;
            }
        }
    }

    /** Pooled, illustrative expanding wavefront; not an acoustic pressure simulation. */
    private final class Wave {
        final ArrayList<ModelInstance3D> rings = new ArrayList<>();
        final Matrix4f transform = new Matrix4f();
        final Material3D material;
        final float offset;
        float radius;

        /** Allocates one retained wave indicator with a phase offset for the owning sound zone. */
        Wave(Zone zone, float offset) {
            this.offset = offset;
            material =
                    material(new Color(1, 1, 1, 1))
                            .setRenderPass(RenderPass3D.ADDITIVE)
                            .setFogMix(0);
            for (int plane = 0; plane < 3; plane++) {
                Vector3f previous = null;
                for (int i = 0; i <= 32; i++) {
                    double angle = (i % 32) * Math.PI * 2 / 32;
                    float u = (float) Math.cos(angle), v = (float) Math.sin(angle);
                    Vector3f next =
                            switch (plane) {
                                case 0 -> new Vector3f(u, v, 0);
                                case 1 -> new Vector3f(u, 0, v);
                                default -> new Vector3f(0, u, v);
                            };
                    if (previous != null) line(rings, previous, next, .018f, material);
                    previous = next;
                }
            }
            update(zone);
        }

        /**
         * Updates this wave's transform and visibility from the zone's current shape and playback
         * phase.
         */
        void update(Zone zone) {
            float age = (zone.wavePhase + offset) % 1;
            float reach =
                    (zone.box ? Math.max(zone.a, Math.max(zone.b, zone.c)) : zone.a) + zone.fade;
            radius = .3f + age * Math.max(1, reach);
            transform.translation(zone.x, zone.y, zone.z).scale(radius);
            for (ModelInstance3D ring : rings) ring.setParentTransform(transform);
            float brightness = (1 - age) * (previewWaves ? .65f : Math.min(1, zone.volume * 2));
            Color color = zone.core.getTint();
            material.getTint()
                    .set(
                            color.getRed() / 255f,
                            color.getGreen() / 255f,
                            color.getBlue() / 255f,
                            brightness);
        }
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        unitBox = ModelBuilder3D.box(1, 1, 1);
        unitSphere = ModelBuilder3D.sphere(1, 16, 10);
        batch = new ModelBatch3D();
        state =
                new MeshRenderState3D()
                        .setCamera(camera)
                        .setAmbientLight(new Color(.7f, .7f, .7f, 1))
                        .setDirectionalLight(new Color(.5f, .5f, .5f, 1))
                        .setLightDirection(-2, -3, 6);
        camera.setClipPlanes(.1f, 120);
        viewport = new PerspectiveViewport(900, 680, camera);
        Material3D gridColor = material(new Color(.14f, .19f, .25f, 1));
        for (int i = -12; i <= 12; i++) {
            line(grid, new Vector3f(i, -12, 0), new Vector3f(i, 12, 0), .025f, gridColor);
            line(grid, new Vector3f(-12, i, 0), new Vector3f(12, i, 0), .025f, gridColor);
        }
        zones[0] = new Zone("A / warm tone", -4, 220, false, new Color(.2f, .85f, 1, 1));
        zones[1] = new Zone("B / bright tone", 4, 330, true, new Color(1, .55f, .2f, 1));
        listenerMarker =
                new ModelInstance3D()
                        .setModel(unitSphere)
                        .setScale(.35f)
                        .setMaterial(material(new Color(1, .95f, .55f, 1)));
        ui = new UIRoot();
        ui.setClickable(false);
        ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        buildUi();
    }

    /**
     * Creates a new material descriptor with the requested appearance. Geometry may share the
     * descriptor while independent edits need a copy.
     */
    private static Material3D material(Color color) {
        return new Material3D().setTint(color);
    }

    /**
     * Adds a retained thin-box segment between world-space endpoints to the supplied visual
     * collection.
     */
    private void line(
            List<ModelInstance3D> list,
            Vector3f from,
            Vector3f to,
            float thickness,
            Material3D material) {
        Vector3f direction = new Vector3f(to).sub(from);
        float length = direction.length();
        if (length < .0001f) return;
        list.add(
                new ModelInstance3D()
                        .setModel(unitBox)
                        .setMaterial(material)
                        .setPosition((from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2)
                        .setScale(thickness, thickness, length)
                        .setRotation(
                                new Quaternionf()
                                        .rotationTo(new Vector3f(0, 0, 1), direction.div(length))));
    }

    /**
     * Synthesizes a looping PCM tone at the requested frequency in hertz; no external sound file or
     * download is required.
     */
    private static SoundData tone(float frequency) {
        int rate = 22050;
        var pcm = BufferUtils.createByteBuffer(rate * 2);
        for (int i = 0; i < rate; i++) {
            double phase = 2 * Math.PI * frequency * i / rate;
            pcm.putShort((short) (4000 * (Math.sin(phase) + .2 * Math.sin(phase * 2))));
        }
        pcm.flip();
        return new SoundData(
                null, pcm, 0, pcm.remaining(), 1, 1, rate, 16, false, false, AudioFormat.WAV);
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private NanoLabel label(String text, float size) {
        var label = new NanoLabel(text);
        label.setStyle(NanoLabel.FONT_SIZE_KEY, size);
        label.getLayout().noShrink();
        return label;
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private NanoButton button(String text, Runnable action) {
        var button = new NanoButton(text).action(n -> action.run());
        button.getLayout().height(30).noShrink();
        return button;
    }

    /**
     * Builds transport controls, zone selection and the property inspector around the shared scene
     * viewport.
     */
    private void buildUi() {
        var header = new NanoPanel();
        header.getLayout()
                .absolute()
                .left(16)
                .right(16)
                .top(12)
                .height(62)
                .row()
                .padding(16)
                .gap(24)
                .itemsCenter();
        header.add(label("VALTHORNE / AUDIO STUDIO", 22));
        stats = label("FPS / warming up", 14);
        stats.getLayout().width(285);
        header.add(stats);
        previewButton = button("Silent wave preview: off", () -> {});
        previewButton.getLayout().width(220);
        previewButton.action(
                n -> {
                    previewWaves = !previewWaves;
                    previewButton.text(
                            previewWaves ? "Silent wave preview: on" : "Silent wave preview: off");
                });
        header.add(previewButton);
        ui.add(header);
        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().absolute().left(16).top(88).bottom(16).width(310);
        fields = new NanoContainer();
        fields.getLayout().column().widthPercent(100).padding(14).gap(6);
        scroll.add(fields);
        ui.add(scroll);
        var footer = new NanoPanel();
        footer.getLayout()
                .absolute()
                .left(346)
                .right(16)
                .bottom(16)
                .height(118)
                .column()
                .padding(14)
                .gap(6);
        meters = label("", 15);
        meters.getLayout().height(36);
        footer.add(meters);
        footer.add(
                label(
                        "Expanding rings show sound waves. Play a sound or enable silent wave"
                            + " preview.\n"
                            + "WASD move listener / Q E height / arrows orbit / R F zoom / Esc"
                            + " exit\n"
                            + "Static contours show coverage. Animated waves are illustrative, not"
                            + " an acoustic simulation.",
                        13));
        ui.add(footer);
        inspect();
    }

    /**
     * Rebuilds the selected zone's property controls while keeping its audio source and retained
     * visual objects alive.
     */
    private void inspect() {
        syncing = true;
        fields.clear();
        sliders.clear();
        Zone zone = zones[selected];
        fields.add(label("SOUND SOURCES", 13));
        for (int i = 0; i < zones.length; i++) {
            int index = i;
            fields.add(
                    button(
                            (i == selected ? "> " : "") + zones[i].name,
                            () -> {
                                selected = index;
                                inspect();
                            }));
        }
        fields.add(label("PLAYBACK / starts paused", 13));
        var playback = new NanoContainer();
        playback.getLayout().row().gap(6).height(30).widthPercent(100).noShrink();
        var play =
                button(
                        "Play / pause",
                        () -> {
                            zone.playing = !zone.player.isPlaying();
                            if (zone.playing) zone.player.play();
                            else zone.player.pause();
                        });
        var stop =
                button(
                        "Stop",
                        () -> {
                            zone.player.stop();
                            zone.playing = false;
                        });
        play.getLayout().width(0).grow();
        stop.getLayout().width(0).grow();
        playback.add(play);
        playback.add(stop);
        fields.add(playback);
        var options = new NanoContainer();
        options.getLayout().row().gap(6).height(30).widthPercent(100).noShrink();
        var mute =
                button(
                        "Mute / unmute",
                        () -> {
                            zone.muted = !zone.muted;
                            applyVolume(zone);
                        });
        var loop =
                button(
                        "Toggle loop",
                        () -> {
                            zone.looping = !zone.looping;
                            zone.player.setLooping(zone.looping);
                        });
        mute.getLayout().width(0).grow();
        loop.getLayout().width(0).grow();
        options.add(mute);
        options.add(loop);
        fields.add(options);
        slider(
                "Volume",
                0,
                1,
                zone.volume,
                v -> {
                    zone.volume = v;
                    applyVolume(zone);
                });
        slider(
                "Pitch / speed",
                .25f,
                2,
                zone.pitch,
                v -> {
                    zone.pitch = v;
                    zone.player.setPitch(v);
                });
        fields.add(label("ZONE / world units", 13));
        fields.add(
                new NanoComboBox<String>()
                        .items(List.of("Sphere", "Box"))
                        .selectedIndex(zone.box ? 1 : 0)
                        .onChange(
                                value -> {
                                    zone.box = value.equals("Box");
                                    zone.rebuild();
                                }));
        slider(
                "Center X",
                -9,
                9,
                zone.x,
                v -> {
                    zone.x = v;
                    zone.rebuild();
                });
        slider(
                "Center Y",
                -9,
                9,
                zone.y,
                v -> {
                    zone.y = v;
                    zone.rebuild();
                });
        slider(
                "Center Z",
                0,
                8,
                zone.z,
                v -> {
                    zone.z = v;
                    zone.rebuild();
                });
        slider(
                "Radius / half width",
                .25f,
                5,
                zone.a,
                v -> {
                    zone.a = v;
                    zone.rebuild();
                });
        slider(
                "Box half depth",
                .25f,
                5,
                zone.b,
                v -> {
                    zone.b = v;
                    zone.rebuild();
                });
        slider(
                "Box half height",
                .25f,
                5,
                zone.c,
                v -> {
                    zone.c = v;
                    zone.rebuild();
                });
        slider(
                "Fade distance",
                0,
                5,
                zone.fade,
                v -> {
                    zone.fade = v;
                    zone.rebuild();
                });
        fields.add(
                button(
                        "Listener to selected center",
                        () -> listener.set(zone.marker.getPosition())));
        fields.add(
                button(
                        "Reset listener + camera",
                        () -> {
                            listener.set(-4, -1, 2);
                            azimuth = -1.1f;
                            distance = 17;
                        }));
        fields.add(label("WAVE VISUALIZATION", 12));
        slider("Wave speed (cycles/s)", .05f, 1, zone.waveSpeed, v -> zone.waveSpeed = v);
        syncing = false;
    }

    /** Combines the zone's gain and mute state before updating the audio player. */
    private void applyVolume(Zone zone) {
        zone.player.setVolume(zone.muted ? 0 : zone.volume);
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(String name, float min, float max, float value, Consumer<Float> action) {
        var caption = label(String.format(Locale.ROOT, "%s  %.2f", name, value), 13);
        var slider =
                new NanoSlider(min, max, value)
                        .action(
                                s -> {
                                    if (syncing) return;
                                    caption.text(
                                            String.format(
                                                    Locale.ROOT, "%s  %.2f", name, s.getValue()));
                                    action.accept(s.getValue());
                                });
        slider.getLayout().height(22).widthPercent(100).noShrink();
        fields.add(caption);
        fields.add(slider);
        sliders.add(slider);
    }

    /**
     * Processes input and advances this demo using elapsed seconds; rendering and resource
     * destruction remain in their lifecycle callbacks.
     *
     * @param delta elapsed time in seconds
     */
    @Override
    public void update(float delta) {
        if (smoke) smokeInput();
        ui.update(delta);
        float dt = Math.min(delta, .05f);
        for (Zone zone : zones) zone.animateWaves(dt);
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE))
            glfwSetWindowShouldClose(Window.getAddress(), true);
        boolean free =
                viewport.containsScreenPoint(Mouse.getX(), Mouse.getY())
                        && ui.getCaptured() == null
                        && (ui.getHovered() == null || ui.getHovered() == ui);
        if (free) {
            listener.x += (key(GLFW_KEY_D) - key(GLFW_KEY_A)) * dt * 4;
            listener.y += (key(GLFW_KEY_W) - key(GLFW_KEY_S)) * dt * 4;
            listener.z =
                    Math.max(
                            0,
                            Math.min(
                                    10, listener.z + (key(GLFW_KEY_E) - key(GLFW_KEY_Q)) * dt * 4));
            azimuth += (key(GLFW_KEY_RIGHT) - key(GLFW_KEY_LEFT)) * dt;
            distance =
                    Math.max(
                            10,
                            Math.min(45, distance + (key(GLFW_KEY_F) - key(GLFW_KEY_R)) * dt * 8));
        }
        camera.setPosition(
                (float) Math.cos(azimuth) * distance,
                (float) Math.sin(azimuth) * distance,
                distance * .65f);
        camera.lookAt(0, 0, 1.5f, 0, 0, 1);
        if (Window.getWidth() > 350 && Window.getHeight() > 220)
            viewport.setBounds(340, 140, Window.getWidth() - 356, Window.getHeight() - 224);
        camera.rebuild(viewport.getWidth(), viewport.getHeight());
        boolean pressed = Mouse.isButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (pressed && !down && free) {
            int hit = -1;
            float nearest = 28 * 28;
            for (int i = 0; i < zones.length; i++) {
                camera.project(
                        zones[i].marker.getPosition(),
                        viewport.getX(),
                        viewport.getY(),
                        viewport.getWidth(),
                        viewport.getHeight(),
                        projected);
                float dx = projected.x - Mouse.getX(), dy = projected.y - Mouse.getY();
                if (projected.z > 0 && projected.z < 1 && dx * dx + dy * dy < nearest) {
                    hit = i;
                    nearest = dx * dx + dy * dy;
                }
            }
            if (hit >= 0) {
                selected = hit;
                inspect();
            } else dragging = true;
        }
        if (dragging && pressed && free) {
            var ray = viewport.screenToRay(Mouse.getX(), Mouse.getY());
            if (ray != null && Math.abs(ray.dZ) > .0001f) {
                float t = (listener.z - ray.oZ) / ray.dZ;
                if (t > 0)
                    listener.set(
                            Math.max(-12, Math.min(12, ray.oX + ray.dX * t)),
                            Math.max(-12, Math.min(12, ray.oY + ray.dY * t)),
                            listener.z);
            }
        }
        if (!pressed) dragging = false;
        down = pressed;
        Audio.setListenerPosition(listener.x, listener.y, listener.z);
        listenerMarker.setPosition(listener);
        for (int i = 0; i < zones.length; i++) zones[i].marker.setScale(i == selected ? .4f : .28f);
        elapsed += delta;
        fpsFrames++;
        if (elapsed >= .2f) {
            for (Zone zone : zones) zone.playing = zone.player.isPlaying();
            stats.text(
                    String.format(
                            Locale.ROOT,
                            "%.0f FPS / %.2f ms / VSync off",
                            fpsFrames / elapsed,
                            elapsed * 1000 / fpsFrames));
            Zone z = zones[selected];
            meters.text(
                    String.format(
                            Locale.ROOT,
                            "Listener  %.1f, %.1f, %.1f    |    Gain A %.0f%% / B %.0f%%\n"
                                    + "Selected: %s / %s / loop %s / %s",
                            listener.x,
                            listener.y,
                            listener.z,
                            zones[0].player.getEffectiveVolume() * 100,
                            zones[1].player.getEffectiveVolume() * 100,
                            z.name,
                            z.player.isPlaying() ? "playing" : "paused/stopped",
                            z.looping ? "on" : "off",
                            z.muted ? "muted" : "unmuted"));
            elapsed = 0;
            fpsFrames = 0;
        }
    }

    /** Returns one while the given key is held, otherwise zero, for composing movement axes. */
    private static int key(int key) {
        return Keyboard.isKeyDown(key) ? 1 : 0;
    }

    /**
     * Injects native pointer callbacks for deterministic smoke interaction, using window
     * coordinates and GLFW button/action constants.
     */
    private void pointer(float x, float y, int action) {
        long window = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(window, null);
        glfwSetCursorPosCallback(window, cursor);
        cursor.invoke(window, x, y);
        if (action >= 0) {
            var buttons = glfwSetMouseButtonCallback(window, null);
            glfwSetMouseButtonCallback(window, buttons);
            buttons.invoke(window, GLFW_MOUSE_BUTTON_LEFT, action, 0);
        }
    }

    /**
     * Advances the scripted native-input sequence and asserts that the corresponding camera,
     * selection and UI state changes occur.
     */
    private void smokeInput() {
        if (frames == 3 || frames == 4 || frames == 5) {
            NanoSlider volume = sliders.getFirst();
            float x = volume.getAbsoluteX() + volume.getWidth() * (frames == 3 ? .3f : .8f);
            pointer(
                    x,
                    volume.getAbsoluteY() + volume.getHeight() / 2,
                    frames == 3 ? GLFW_PRESS : frames == 5 ? GLFW_RELEASE : -1);
            if (frames == 5) {
                if (zones[0].volume < .6f)
                    throw new AssertionError("Native volume slider drag failed");
                volume.value(.25f);
                zones[0].volume = .25f;
                applyVolume(zones[0]);
            }
        }
        if (frames == 7 || frames == 8) {
            camera.project(
                    zones[1].marker.getPosition(),
                    viewport.getX(),
                    viewport.getY(),
                    viewport.getWidth(),
                    viewport.getHeight(),
                    projected);
            pointer(
                    projected.x,
                    Window.getHeight() - projected.y,
                    frames == 7 ? GLFW_PRESS : GLFW_RELEASE);
        }
        if (frames == 9 && selected != 1)
            throw new AssertionError("Native world source selection failed");
        if (frames == 10 || frames == 11 || frames == 12) {
            camera.project(
                    new Vector3f(0, frames == 10 ? 4 : 2, listener.z),
                    viewport.getX(),
                    viewport.getY(),
                    viewport.getWidth(),
                    viewport.getHeight(),
                    projected);
            pointer(
                    projected.x,
                    Window.getHeight() - projected.y,
                    frames == 10 ? GLFW_PRESS : frames == 12 ? GLFW_RELEASE : -1);
            if (frames == 12) {
                if (Math.abs(listener.y - 2) > .2f)
                    throw new AssertionError("Native listener dragging failed: " + listener);
                listener.set(-4, -1, 2);
            }
        }
        if (frames == 14 || frames == 15) {
            NanoSlider center = sliders.get(2);
            pointer(
                    center.getAbsoluteX() + center.getWidth() * .5f,
                    center.getAbsoluteY() + center.getHeight() / 2,
                    frames == 14 ? GLFW_PRESS : GLFW_RELEASE);
            if (frames == 15) {
                if (Math.abs(zones[1].x) > .2f)
                    throw new AssertionError("Native zone movement failed");
                center.value(4);
                zones[1].x = 4;
                zones[1].rebuild();
            }
        }
        if (frames == 16) {
            selected = 0;
            inspect();
        }
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        if (Window.getWidth() <= 0 || Window.getHeight() <= 0) return;
        Window.clear3D(new Color(.025f, .04f, .065f, 1));
        viewport.render(
                () -> {
                    batch.begin(state);
                    try {
                        batch.submit(grid);
                        for (Zone zone : zones) {
                            batch.submit(zone.guides);
                            batch.submit(zone.marker);
                            if (zone.showWaves())
                                for (Wave wave : zone.waves) batch.submit(wave.rings);
                        }
                        batch.submit(listenerMarker);
                        batch.end();
                    } finally {
                        batch.cancel();
                    }
                });
        ui.draw();
        if (smoke) {
            frames++;
            if (frames == 20) validate();
            if (frames == 30) {
                capture();
                glfwSetWindowShouldClose(Window.getAddress(), true);
            }
        }
    }

    /**
     * Checks distance attenuation, playback/mute gating and retained wave geometry after the
     * scripted interaction sequence.
     */
    private void validate() {
        Zone source = zones[0];
        Vector3f before = new Vector3f(source.marker.getPosition());
        SoundArea originalArea = source.area;
        Wave wave = source.waves.getFirst();
        ModelInstance3D ring = wave.rings.getFirst();
        float radius = wave.radius;
        previewWaves = true;
        source.animateWaves(.5f);
        if (wave.radius <= radius) throw new AssertionError("Wavefront did not expand");
        if (!before.equals(source.marker.getPosition()) || source.area != originalArea)
            throw new AssertionError("Wave visualization moved the sound source");
        if (ring != wave.rings.getFirst())
            throw new AssertionError("Wave animation rebuilt geometry");
        previewWaves = false;
        radius = wave.radius;
        source.animateWaves(.5f);
        if (wave.radius != radius || source.showWaves())
            throw new AssertionError("Paused sound emitted waves");
        source.playing = true;
        source.animateWaves(.1f);
        if (wave.radius <= radius) throw new AssertionError("Playing sound did not emit waves");
        source.muted = true;
        if (source.showWaves()) throw new AssertionError("Muted sound emitted waves");
        source.muted = false;
        source.playing = false;
        previewWaves = true;
        previewButton.text("Silent wave preview: on");
        for (Zone zone : zones) {
            Vector3f position = zone.marker.getPosition();
            Audio.setListenerPosition(position.x, position.y, position.z);
            if (Math.abs(zone.player.getEffectiveVolume() - zone.volume) > .0001f)
                throw new AssertionError("Inside gain");
            Audio.setListenerPosition(100, 100, 100);
            if (zone.player.getEffectiveVolume() != 0) throw new AssertionError("Outside gain");
            zone.player.setVolume(0);
            zone.player.play();
            zone.player.pause();
            if (!zone.player.isPaused()) throw new AssertionError("Playback controls");
            zone.player.setVolume(zone.volume);
        }
        Audio.setListenerPosition(listener.x, listener.y, listener.z);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        System.out.println(
                "Audio Studio smoke passed: expanding waves, stationary sources, playback/mute"
                        + " gating, retained geometry and native interaction.");
    }

    /**
     * Captures the current demo frame or state for its documented workflow; capture-specific
     * overloads choose the output name.
     */
    private void capture() {
        FrameCapture.save(Path.of("build/audio-studio/studio.png"));
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        for (Zone zone : zones) if (zone != null) zone.player.dispose();
        if (ui != null) ui.dispose();
        if (theme != null) theme.close();
        if (batch != null) batch.dispose();
        Audio.setListenerPosition(0, 0, 0);
    }
}
