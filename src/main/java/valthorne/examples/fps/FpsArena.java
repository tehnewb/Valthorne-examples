// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.fps;

import static valthorne.PlatformTools.*;
import static valthorne.ui.Canvas2D.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.event.EventTypes;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyAdapter;
import valthorne.event.listeners.MouseAdapter;
import valthorne.examples.assets.PhysicsStudioModels;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.ui.UIContainer;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.viewport.ScreenViewport;

import java.util.*;

/**
 * A playable first-person sample combining movement, hitscan weapons, grenades, drone waves,
 * destructible props, particle lighting and an interactive pause/settings menu.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>The application owns renderers and asset libraries; FpsArenaWorld owns simulation state;
 * FpsArenaEffects owns emitters and their attachments. Restart closes effects before replacing the
 * world. Windows x64 and OpenGL 4.3 are required for the Filament sharing path.
 *
 * <p>Run through the documented Gradle task for validated options and platform checks. Study the
 * accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/fps.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class FpsArena implements Application {
    private static final Color BACKGROUND = new Color(.025f, .035f, .05f, 1);
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final Vector3f eye = new Vector3f(), forward = new Vector3f(0, 1, 0);
    private final Matrix4f weaponTransform = new Matrix4f();
    private final ArrayList<ModelInstance3D> weapon = new ArrayList<>();
    private final ArrayList<PointLight3D> roomLights = new ArrayList<>();
    private double[] renderSamples, simulationSamples;
    private PhysicsStudioModels models;
    private FpsCombatModels combatModels;
    private FpsEnvironmentModels environmentModels;
    private FpsArenaWorld game;
    private FpsArenaEffects effects;
    private FilamentRenderer3D renderer;
    private UIRoot ui;
    private ProfessionalTheme theme;
    private NanoPanel menu;
    private NanoLabel menuTitle, menuHint;
    private NanoButton resume;
    private Hud hud;
    private int width, height, frames, reportFrames, lastHealth = 100, lastKills;
    private int particleShadowBudget = 2;
    private int shownHealth = -1, shownAmmo = -1, shownReserve = -1;
    private int shownParticles = -1, shownLights = -1, shownBodies = -1;
    private boolean playing,
            started,
            smoke,
            benchmark,
            lookReady,
            jumpQueued,
            physicalParticles = true,
            particleLights = true;
    private float yaw, pitch, lastMouseX, lastMouseY, sensitivity = .0022f, fieldOfView = 72;
    private float exposure = 2.8f, environment = 80, particlePower = 1, keyPower = 180;
    private float reportTime, fps, renderMs, simulationMs, hitFlash, damageFlash, recoil, toastTime;
    private String toast = "",
            performance = "Preparing arena...",
            healthText = "100",
            ammoText = "24",
            reserveText = "/ 120",
            waveText = "WAVE 01",
            scoreText = "000000",
            targetText = "Clear the drones",
            reloadText = "M4A1",
            details = "",
            supplies = "";
    private int palette;
    private final BitSet heldKeys = new BitSet();
    private int freshKey = -1;
    private final KeyAdapter keyEdges =
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyPressEvent e) {
                    int key = e.getKey();
                    freshKey = key >= 0 && !heldKeys.get(key) ? key : -1;
                    if (key >= 0) heldKeys.set(key);
                }

                @Override
                public void keyReleased(KeyReleaseEvent e) {
                    if (e.getKey() >= 0) heldKeys.clear(e.getKey());
                }
            };

    private final KeyAdapter keys =
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyPressEvent e) {
                    int key = e.getKey();
                    if (key < 0 || key != freshKey) return;
                    if (key == Keyboard.ESCAPE) {
                        setPlaying(!playing && !game.isDead());
                        e.consume();
                    } else if (playing) {
                        if (key == Keyboard.SPACE) jumpQueued = true;
                        if (key == Keyboard.R) game.reload();
                        if (key == Keyboard.G && game.throwGrenade(eye, forward))
                            tell("GRENADE OUT");
                        if (key == Keyboard.F && effects.flare(eye, forward, game.getPlayerBody()))
                            tell("LIGHT FLARE DEPLOYED");
                    }
                }
            };
    private final MouseAdapter mouse =
            new MouseAdapter() {
                @Override
                public void mouseMoved(MouseMoveEvent e) {
                    look(e.getToX(), e.getToY());
                }

                @Override
                public void mouseDragged(MouseDragEvent e) {
                    look(e.getToX(), e.getToY());
                }
            };
    private final valthorne.event.EventHandler<WindowFocusEvent> focus =
            e -> {
                if (!e.isFocused()) {
                    heldKeys.clear();
                    if (playing) setPlaying(false);
                }
            };

    /**
     * Starts this application on the process main thread. Use the shared ExampleLauncher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        var app = new FpsArena();
        for (String arg : args) {
            if (arg.equals("--smoke")) app.smoke = true;
            if (arg.equals("--benchmark")) app.benchmark = true;
            if (arg.equals("--visual-particles")) app.physicalParticles = false;
            if (arg.equals("--no-particle-lights")) app.particleLights = false;
            if (arg.equals("--no-particle-shadows")) app.particleShadowBudget = 0;
            if (arg.equals("--four-particle-shadows")) app.particleShadowBudget = 4;
        }
        if (app.benchmark) {
            app.renderSamples = new double[360];
            app.simulationSamples = new double[360];
        }
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .contextVersion(4, 1)
                        .size(1600, 960)
                        .depthBits(24)
                        .visible(!app.smoke && !app.benchmark)
                        .title("Valthorne | LIVE FIRE - Physics + Light Arena"));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        renderer = new FilamentRenderer3D();
        renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
        renderer.setExposure(exposure);
        renderer.setEnvironmentIntensity(environment);
        camera.setClipPlanes(.04f, 100);
        models = new PhysicsStudioModels();
        combatModels = new FpsCombatModels();
        environmentModels = new FpsEnvironmentModels();
        resetGame();
        Keyboard.addKeyListener(keyEdges);
        ui = new UIRoot();
        ui.setClickable(false);
        ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        hud = new Hud();
        hud.setClickable(false);
        ui.add(hud);
        buildMenu();
        resize();
        Keyboard.addKeyListener(keys);
        Mouse.addMouseListener(mouse);
        JGL.subscribe(EventTypes.WINDOW_FOCUS, focus);
        Window.setSizeLimits(640, 480, -1, -1);
        Window.setSwapInterval(SwapInterval.OFF);
        if (benchmark) setPlaying(true);
    }

    /**
     * Replaces the gameplay world and effects, resets HUD/input state, and preserves the reusable
     * asset libraries.
     */
    private void resetGame() {
        if (effects != null) effects.close();
        if (game != null) game.close();
        scene.clear();
        weapon.clear();
        roomLights.clear();
        game =
                new FpsArenaWorld(
                        scene,
                        models,
                        combatModels,
                        environmentModels,
                        impact -> {
                            if (effects != null) effects.impact(impact);
                            hitFlash = .1f;
                        });
        effects = new FpsArenaEffects(scene, game.getPhysics(), physicalParticles, particleLights);
        effects.setShadowBudget(particleShadowBudget);
        effects.prepareRendering();
        effects.lightGain = particlePower;
        effects.palette = palette;
        addLight(0, -6, 7, 1, .88f, .68f, keyPower, 32, true);
        addLight(-9, 5, 5, .2f, .55f, 1, 65, 22, false);
        addLight(9, 9, 5, 1, .35f, .1f, 80, 24, false);
        addLight(0, 15, 8, .7f, .85f, 1, 110, 25, true);
        buildWeapon();
        yaw = pitch = recoil = 0;
        hitFlash = damageFlash = toastTime = 0;
        lastHealth = 100;
        lastKills = 0;
        updateCamera();
        refreshStatus();
        refreshDetails(roomLights.size());
    }

    /**
     * Adds a bounded point light to the arena with world-space position/range and the requested
     * shadow policy.
     */
    private void addLight(
            float x,
            float y,
            float z,
            float r,
            float g,
            float b,
            float intensity,
            float range,
            boolean shadows) {
        var light =
                new PointLight3D()
                        .setPosition(x, y, z)
                        .setColor(new Color(r, g, b, 1))
                        .setIntensity(intensity)
                        .setRange(range)
                        .setCastsShadows(shadows);
        roomLights.add(light);
        scene.addLight(light);
    }

    /** Assembles the first-person rifle and arm visuals from the shared combat asset library. */
    private void buildWeapon() {
        var rifle = combatModels.rifle();
        float scale = .92f / rifle.depth();
        weaponPiece(
                rifle.model(),
                rifle.material().setCastsShadow(false),
                .22f,
                .46f,
                -.26f,
                scale,
                scale,
                scale);
        var arms = combatModels.arms();
        var offset = arms.attachmentOffset().mul(scale).add(.22f, .46f, -.26f);
        weaponPiece(
                arms.model(),
                arms.material().setCastsShadow(false),
                offset.x,
                offset.y,
                offset.z,
                scale,
                scale,
                scale);
    }

    /**
     * Creates a first-person model instance using borrowed geometry, a material and local
     * transform.
     */
    private void weaponPiece(
            Model3D mesh,
            Material3D material,
            float x,
            float y,
            float z,
            float w,
            float d,
            float h) {
        var piece =
                new ModelInstance3D()
                        .setModel(mesh)
                        .setMaterial(material)
                        .setPosition(x, y, z)
                        .setScale(w, d, h);
        scene.add(piece);
        weapon.add(piece);
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private NanoLabel label(String text, int size) {
        var node = new NanoLabel(text);
        node.setStyle(NanoLabel.FONT_SIZE_KEY, (float) size);
        node.getLayout().noShrink();
        return node;
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private NanoButton button(String text, Runnable action) {
        var node = new NanoButton(text).action(n -> action.run());
        node.getLayout().height(36).noShrink();
        return node;
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(
            String text,
            float min,
            float max,
            float value,
            java.util.function.DoubleConsumer change) {
        var caption = label(text + String.format(Locale.ROOT, "  %.2f", value), 12);
        menu.add(caption);
        var slider =
                new NanoSlider(min, max, value)
                        .action(
                                n -> {
                                    change.accept(n.getValue());
                                    caption.text(
                                            text
                                                    + String.format(
                                                            Locale.ROOT, "  %.2f", n.getValue()));
                                });
        slider.getLayout().height(20).noShrink();
        menu.add(slider);
    }

    /**
     * Constructs the pause/menu controls and performance settings without giving them gameplay
     * input ownership.
     */
    private void buildMenu() {
        menu = new NanoPanel();
        menu.getLayout().absolute().column().width(370).padding(20).gap(6);
        menuTitle = label("LIVE FIRE / TEST CONSOLE", 21);
        menu.add(menuTitle);
        menuHint = label("Clear drone waves. Test every impact.", 13);
        menu.add(menuHint);
        resume = button("Enter arena", () -> setPlaying(true));
        menu.add(resume);
        menu.add(
                button(
                        "Restart run",
                        () -> {
                            resetGame();
                            setPlaying(true);
                        }));
        var physicsButton =
                button(
                        physicalParticles
                                ? "Particle physics: Jolt"
                                : "Particle physics: visual only",
                        () -> {});
        physicsButton.action(
                n -> {
                    physicalParticles = !physicalParticles;
                    effects.physical = physicalParticles;
                    effects.rebuild();
                    physicsButton.text(
                            physicalParticles
                                    ? "Particle physics: Jolt"
                                    : "Particle physics: visual only");
                });
        menu.add(physicsButton);
        var lightButton =
                button(particleLights ? "Particle lights: on" : "Particle lights: off", () -> {});
        lightButton.action(
                n -> {
                    particleLights = !particleLights;
                    effects.lights = particleLights;
                    effects.update(0);
                    lightButton.text(
                            particleLights ? "Particle lights: on" : "Particle lights: off");
                });
        menu.add(lightButton);
        var shadowOptions =
                new NanoComboBox<String>()
                        .items(
                                List.of(
                                        "Flare shadows: off",
                                        "Flare shadows: 2",
                                        "Flare shadows: 4"))
                        .selectedIndex(particleShadowBudget / 2)
                        .onChange(
                                value -> {
                                    particleShadowBudget =
                                            value.endsWith("2") ? 2 : value.endsWith("4") ? 4 : 0;
                                    effects.setShadowBudget(particleShadowBudget);
                                });
        shadowOptions.getLayout().height(34).noShrink();
        menu.add(shadowOptions);
        var colors =
                new NanoComboBox<String>()
                        .items(List.of("Amber particles", "Cyan particles", "Violet particles"))
                        .selectedIndex(palette)
                        .onChange(
                                value -> {
                                    palette =
                                            value.startsWith("Amber")
                                                    ? 0
                                                    : value.startsWith("Cyan") ? 1 : 2;
                                    effects.palette = palette;
                                });
        colors.getLayout().height(34).noShrink();
        menu.add(colors);
        slider(
                "Particle light power",
                0,
                3,
                particlePower,
                v -> {
                    particlePower = (float) v;
                    effects.lightGain = particlePower;
                    effects.update(0);
                });
        slider(
                "Exposure",
                .5f,
                5,
                exposure,
                v -> {
                    exposure = (float) v;
                    renderer.setExposure(exposure);
                });
        slider(
                "Environment",
                0,
                250,
                environment,
                v -> {
                    environment = (float) v;
                    renderer.setEnvironmentIntensity(environment);
                });
        slider(
                "Key light",
                0,
                400,
                keyPower,
                v -> {
                    keyPower = (float) v;
                    roomLights.get(0).setIntensity(keyPower);
                });
        slider(
                "Mouse sensitivity",
                .5f,
                3,
                sensitivity * 1000,
                v -> sensitivity = (float) v / 1000);
        menu.add(label("WASD move   Shift sprint   Space jump", 12));
        menu.add(label("Left fire   Right aim   R reload", 12));
        menu.add(label("G grenade   F light flare   Esc console", 12));
        menu.add(button("Exit arena", Window::requestClose));
        ui.add(menu);
    }

    /**
     * Transfers input ownership between the game and menu, including mouse capture and cursor
     * state.
     */
    private void setPlaying(boolean value) {
        if (value && game.isDead()) return;
        playing = value;
        lookReady = false;
        jumpQueued = false;
        if (playing) started = true;
        if (menu != null) {
            menu.setVisible(!playing);
            if (playing) ui.setFocusTo(null);
            resume.text(started ? "Resume run" : "Enter arena");
            resume.setEnabled(!game.isDead());
            menuTitle.text(game.isDead() ? "RUN ENDED" : "LIVE FIRE / TEST CONSOLE");
            menuHint.text(
                    game.isDead()
                            ? "Restart to challenge the next run."
                            : "Clear drone waves. Test every impact.");
        }
        if (!smoke && !benchmark) {
            Mouse.setCursorMode(playing ? Mouse.CURSOR_DISABLED : Mouse.CURSOR_NORMAL);
            Mouse.setRawMouseMotion(playing);
        }
    }

    /** Applies pointer movement to yaw and pitch while clamping the vertical look angle. */
    private void look(float x, float y) {
        if (!playing) return;
        if (lookReady) {
            // Engine mouse payloads use signed 16-bit positions. Modular deltas
            // preserve relative movement when an unbounded captured cursor wraps.
            yaw += (short) ((int) x - (int) lastMouseX) * sensitivity;
            pitch =
                    valthorne.math.MathUtils.clamp(
                            pitch + (short) ((int) y - (int) lastMouseY) * sensitivity,
                            -1.4f,
                            1.4f);
        }
        lastMouseX = x;
        lastMouseY = y;
        lookReady = true;
    }

    /**
     * Synchronizes the first-person camera and weapon transforms to the player eye position and
     * aim.
     */
    private void updateCamera() {
        game.eyePosition(eye);
        float cp = (float) Math.cos(pitch);
        forward.set(
                (float) Math.sin(yaw) * cp, (float) Math.cos(yaw) * cp, (float) Math.sin(pitch));
        camera.getPosition().set(eye);
        camera.getDirection().set(forward);
        camera.getUp().set(0, 0, 1);
        boolean aim = playing && Mouse.isButtonDown(Mouse.RIGHT);
        camera.setFieldOfViewDegrees(aim ? 48 : fieldOfView);
        float reloadPose =
                game.isReloading() ? (float) Math.sin(game.getReloadProgress() * Math.PI) : 0;
        weaponTransform
                .translation(eye)
                .rotateZ(-yaw)
                .rotateX(pitch + recoil * .4f)
                .translate(
                        aim ? -.22f : 0,
                        -recoil,
                        recoil * .25f + (aim ? .15f : 0) - reloadPose * .12f)
                .rotateY(-reloadPose * .35f);
        for (var part : weapon) part.setParentTransform(weaponTransform);
        effects.faceCamera(camera.getDirection(), camera.getUp());
    }

    /**
     * Recomputes viewport and UI layout from the current window dimensions without recreating scene
     * content.
     */
    private void resize() {
        width = Window.getWidth();
        height = Window.getHeight();
        hud.getLayout().absolute().left(0).top(0).width(width).height(height);
        menu.getLayout().left(40).top(Math.max(85, (height - 710) / 2f));
        ui.layout();
    }

    /** Sets the short-lived player feedback message displayed by the HUD. */
    private void tell(String message) {
        toast = message;
        toastTime = 1.8f;
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
        ui.update(delta);
        float dt = smoke || benchmark ? 1f / 60 : Math.min(delta, .05f);
        long start = System.nanoTime();
        if (playing) {
            updateCamera();
            if (Mouse.isButtonDown(Mouse.LEFT) && game.fire(eye, forward)) {
                recoil = .07f;
            }
            float move =
                    (Keyboard.isKeyDown(Keyboard.W) ? 1 : 0)
                            - (Keyboard.isKeyDown(Keyboard.S) ? 1 : 0);
            float strafe =
                    (Keyboard.isKeyDown(Keyboard.D) ? 1 : 0)
                            - (Keyboard.isKeyDown(Keyboard.A) ? 1 : 0);
            game.update(dt, move, strafe, Keyboard.isKeyDown(Keyboard.LEFT_SHIFT), jumpQueued, yaw);
            jumpQueued = false;
            effects.update(dt);
            if (game.getHealth() < lastHealth) damageFlash = .3f;
            if (game.getKills() > lastKills) tell("DRONE DOWN  +100");
            lastHealth = game.getHealth();
            lastKills = game.getKills();
            if (game.isDead()) setPlaying(false);
        }
        simulationMs = (System.nanoTime() - start) * 1e-6f;
        hitFlash = Math.max(0, hitFlash - dt);
        damageFlash = Math.max(0, damageFlash - dt);
        recoil = Math.max(0, recoil - dt * .6f);
        toastTime = Math.max(0, toastTime - dt);
        updateCamera();
        reportTime += delta;
        reportFrames++;
        if (shownHealth != game.getHealth())
            healthText = Integer.toString(shownHealth = game.getHealth());
        if (shownAmmo != game.getAmmo()) ammoText = Integer.toString(shownAmmo = game.getAmmo());
        if (shownReserve != game.getReserve())
            reserveText = "/ " + (shownReserve = game.getReserve());
        reloadText = game.isReloading() ? "RELOADING" : "M4A1";
        if (reportTime >= .25f) {
            fps = reportFrames / reportTime;
            performance =
                    String.format(
                            Locale.ROOT,
                            "%.0f FPS   %.2f ms render   %.2f ms simulation",
                            fps,
                            renderMs,
                            simulationMs);
            refreshStatus();
            reportTime = 0;
            reportFrames = 0;
        }
    }

    /** Refreshes health, ammunition, wave and score text from the current gameplay state. */
    private void refreshStatus() {
        waveText = String.format(Locale.ROOT, "WAVE %02d", game.getWave());
        scoreText = String.format(Locale.ROOT, "%06d", game.getScore());
        supplies = "GRENADES " + game.getGrenadesRemaining() + "    SCORE " + scoreText;
        targetText =
                game.getEnemies() > 0
                        ? game.getEnemies() + " DRONES REMAINING"
                        : "NEXT WAVE IN " + (int) Math.ceil(game.getWaveCountdown());
    }

    /** Updates retained performance/detail labels using the current active light count. */
    private void refreshDetails(int lightCount) {
        int particles = effects.particleCount(), bodies = game.getBodyCount();
        if (shownParticles == particles && shownLights == lightCount && shownBodies == bodies)
            return;
        shownParticles = particles;
        shownLights = lightCount;
        shownBodies = bodies;
        details = particles + " particles  /  " + lightCount + " lights  /  " + bodies + " bodies";
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        long start = System.nanoTime();
        Window.clear(BACKGROUND);
        PlatformTools.viewport(0, 0, width, height);
        renderer.render(scene, camera);
        refreshDetails(renderer.getPointLightCount());
        ui.draw();
        if (smoke || benchmark) PlatformTools.finish();
        renderMs = (System.nanoTime() - start) * 1e-6f;
        frames++;
        if (smoke) smokeFrame();
        if (benchmark) benchmarkFrame();
    }

    /**
     * NanoVG overlay for player status, crosshair and transient feedback; reads gameplay state
     * without owning simulation resources.
     */
    private final class Hud extends NanoContainer {

        /**
         * Sets the NanoVG fill color from packed RGB and normalized opacity without creating a
         * persistent color object.
         */
        private void fill(long vg, int rgb, float alpha) {
            valthorne.ui.Canvas2D.color(vg, rgb, alpha);
        }

        /**
         * Draws a HUD rectangle in window/UI coordinates using the requested packed color and
         * opacity.
         */
        private void box(long vg, float x, float y, float w, float h, int rgb, float alpha) {
            fill(vg, rgb, alpha);
            beginPath(vg);
            roundedRect(vg, x, y, w, h, 8);
            valthorne.ui.Canvas2D.fill(vg);
        }

        /** Draws aligned HUD text using the current NanoVG context and explicit pixel size. */
        private void text(long vg, float x, float y, String text, float size, int rgb, int align) {
            fill(vg, rgb, 1);
            fontFace(vg, "default");
            fontSize(vg, size);
            textAlign(vg, align | ALIGN_TOP);
            valthorne.ui.Canvas2D.text(vg, x, y, text);
        }

        /**
         * Draws the crosshair, status meters and transient gameplay feedback over the rendered
         * arena.
         */
        @Override
        public void draw(long vg) {
            box(vg, 24, 22, 320, 70, 0x07131F, .88f);
            text(vg, 42, 34, "VALTHORNE  /  LIVE FIRE", 21, 0xEDF5FC, ALIGN_LEFT);
            text(vg, 42, 65, "PHYSICS + LIGHT COMBAT ARENA", 11, 0x69D9E8, ALIGN_LEFT);
            box(vg, width / 2f - 130, 22, 260, 70, 0x07131F, .88f);
            text(vg, width / 2f, 31, waveText, 24, 0xFFFFFF, ALIGN_CENTER);
            text(vg, width / 2f, 63, targetText, 12, 0xFFD181, ALIGN_CENTER);
            box(vg, width - 444, 22, 420, 70, 0x07131F, .88f);
            text(vg, width - 426, 34, performance, 13, 0xC2D4E6, ALIGN_LEFT);
            text(vg, width - 426, 60, details, 12, 0x69D9E8, ALIGN_LEFT);
            box(vg, 24, height - 128, 228, 94, 0x07131F, .9f);
            text(vg, 42, height - 114, "VITALS", 11, 0x91A9BF, ALIGN_LEFT);
            text(
                    vg,
                    42,
                    height - 94,
                    healthText,
                    32,
                    game.getHealth() < 30 ? 0xFF6666 : 0xFFFFFF,
                    ALIGN_LEFT);
            text(vg, 120, height - 87, "ARMOR INTEGRITY", 10, 0x91A9BF, ALIGN_LEFT);
            box(vg, 42, height - 48, 192, 4, 0x263E51, 1);
            box(vg, 42, height - 48, 192 * Math.max(0, game.getHealth()) / 100f, 4, 0x55D6BF, 1);
            box(vg, width - 300, height - 138, 276, 104, 0x07131F, .9f);
            text(vg, width - 280, height - 125, reloadText, 12, 0x69D9E8, ALIGN_LEFT);
            text(vg, width - 280, height - 103, ammoText, 42, 0xFFFFFF, ALIGN_LEFT);
            text(vg, width - 205, height - 86, reserveText, 21, 0x91A9BF, ALIGN_LEFT);
            text(vg, width - 280, height - 51, supplies, 11, 0xFFD181, ALIGN_LEFT);
            if (playing) {
                float cx = width * .5f, cy = height * .5f, gap = 6 + recoil * 110;
                fill(vg, 0xDBFAFF, .9f);
                strokeWidth(vg, 1.5f);
                beginPath(vg);
                moveTo(vg, cx - gap - 7, cy);
                lineTo(vg, cx - gap, cy);
                moveTo(vg, cx + gap, cy);
                lineTo(vg, cx + gap + 7, cy);
                moveTo(vg, cx, cy - gap - 7);
                lineTo(vg, cx, cy - gap);
                moveTo(vg, cx, cy + gap);
                lineTo(vg, cx, cy + gap + 7);
                stroke(vg);
                if (hitFlash > 0) {
                    fill(vg, 0xFFD181, 1);
                    beginPath(vg);
                    for (int sx = -1; sx <= 1; sx += 2)
                        for (int sy = -1; sy <= 1; sy += 2) {
                            moveTo(vg, cx + sx * 13, cy + sy * 13);
                            lineTo(vg, cx + sx * 20, cy + sy * 20);
                        }
                    stroke(vg);
                }
                text(
                        vg,
                        width / 2f,
                        height - 28,
                        "WASD move   SHIFT sprint   SPACE jump   R reload   G grenade   F light"
                                + " flare   ESC console",
                        12,
                        0xC2D4E6,
                        ALIGN_CENTER);
            }
            if (toastTime > 0)
                text(vg, width / 2f, height * .64f, toast, 17, 0xFFD181, ALIGN_CENTER);
            if (game.isReloading())
                box(
                        vg,
                        width / 2f - 55,
                        height / 2f + 44,
                        110 * game.getReloadProgress(),
                        3,
                        0x69D9E8,
                        1);
            if (damageFlash > 0) {
                fill(vg, 0xFF3A3A, damageFlash * 1.5f);
                strokeWidth(vg, 18);
                beginPath(vg);
                rect(vg, 4, 4, width - 8, height - 8);
                stroke(vg);
            }
            if (!playing) {
                box(vg, 0, 0, width, height, 0x020810, .32f);
                text(
                        vg,
                        width * .68f,
                        height * .42f,
                        started ? "SIMULATION PAUSED" : "ENTER THE ARENA",
                        36,
                        0xFFFFFF,
                        ALIGN_CENTER);
                text(
                        vg,
                        width * .68f,
                        height * .42f + 54,
                        "Reactive props. Bouncing debris. Lights in motion.",
                        17,
                        0xC2D4E6,
                        ALIGN_CENTER);
            }
        }
    }

    /**
     * Captures the current demo frame or state for its documented workflow; capture-specific
     * overloads choose the output name.
     */
    private void capture(String name) {
        PlatformTools.capture("build/fps-arena/" + name, width, height);
    }

    private Vector3f smokeStart;
    private int smokeAmmo, smokeGrenades, maximumParticles, maximumLights;
    private long smokeStep;

    /**
     * Injects keyboard input through the installed native callback so smoke checks exercise the
     * same routing as interactive input.
     */
    private void key(int code, int action) {
        PlatformTools.injectKey(code, action);
    }

    /** Injects a key press followed by its release to exercise an edge-triggered shortcut. */
    private void tap(int code) {
        key(code, PRESS);
        key(code, RELEASE);
    }

    /**
     * Injects pointer events for deterministic smoke interaction, using window coordinates and
     * engine mouse-button/action constants.
     */
    private void pointer(float x, float y, int button, int action) {
        PlatformTools.injectPointer(x, y, button, action);
    }

    /** Searches the UI subtree by visible button text; returns null when the target is absent. */
    private static NanoButton findButton(UINode node, String text) {
        if (node instanceof NanoButton b && b.getText().equals(text)) return b;
        if (node instanceof UIContainer c)
            for (var child : c.getChildren()) {
                var found = findButton(child, text);
                if (found != null) return found;
            }
        return null;
    }

    /** Locates the named menu button and injects a real pointer click for smoke validation. */
    private void click(String text) {
        var button = findButton(ui, text);
        require(button != null, "Missing button: " + text);
        float x = button.getAbsoluteX() + button.getWidth() / 2;
        float y = height - button.getAbsoluteY() - button.getHeight() / 2;
        pointer(x, y, Mouse.LEFT, PRESS);
        pointer(x, y, Mouse.LEFT, RELEASE);
    }

    /**
     * Fails the smoke sequence with its scenario-specific message when the expected invariant is
     * false.
     */
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /**
     * Executes the frame-indexed play, movement, fire, menu and effects audit and terminates after
     * its final assertions.
     */
    private void smokeFrame() {
        maximumParticles = Math.max(maximumParticles, effects.particleCount());
        maximumLights = Math.max(maximumLights, renderer.getPointLightCount());
        if (frames == 1) capture("console.png");
        if (frames == 2) {
            click("Enter arena");
            require(playing, "Enter arena did not start");
            game.setWavesEnabled(false);
            game.clearDrones();
            smokeStart = new Vector3f(eye);
            key(Keyboard.W, PRESS);
        }
        if (frames == 32) {
            key(Keyboard.W, RELEASE);
            require(eye.y - smokeStart.y > 1.5f, "WASD did not move the player");
            smokeStart.set(eye);
            tap(Keyboard.SPACE);
            require(jumpQueued, "Space was consumed before gameplay");
        }
        if (frames == 43)
            require(
                    eye.z > smokeStart.z + .3f,
                    "Jump input failed: eye="
                            + eye
                            + ", start="
                            + smokeStart
                            + ", grounded="
                            + game.isGrounded());
        if (frames == 65) pointer(800, 480, -1, 0);
        if (frames == 66) pointer(870, 500, -1, 0);
        if (frames == 67) {
            require(
                    Math.abs(yaw) > .1f && pitch > 0,
                    "Mouse look failed: yaw="
                            + yaw
                            + ", pitch="
                            + pitch
                            + ", ready="
                            + lookReady
                            + ", playing="
                            + playing);
            pointer(870, 500, Mouse.RIGHT, PRESS);
        }
        if (frames == 68) {
            require(camera.getFieldOfViewDegrees() == 48, "Aim zoom failed");
            capture("aim.png");
            pointer(870, 500, Mouse.RIGHT, RELEASE);
            yaw = pitch = 0;
        }
        if (frames == 69) {
            lookReady = false;
            pointer(32760, 500, -1, 0);
            pointer(32776, 500, -1, 0);
            require(yaw > 0 && yaw < .1f, "Captured mouse coordinate wrap caused a camera jump");
            yaw = pitch = 0;
            lookReady = false;
        }
        if (frames == 71) game.spawnDrone(eye.x, eye.y + 4, eye.z);
        if (frames == 72) {
            smokeAmmo = game.getAmmo();
            pointer(width / 2f, height / 2f, Mouse.LEFT, PRESS);
        }
        if (frames == 86) capture("particles.png");
        if (frames == 98) {
            pointer(width / 2f, height / 2f, Mouse.LEFT, RELEASE);
            require(
                    game.getAmmo() < smokeAmmo && game.getKills() >= 1,
                    "Shooting did not damage a drone");
            tap(Keyboard.R);
            require(game.isReloading(), "Reload key failed");
        }
        if (frames == 110) {
            tap(Keyboard.F);
            require(effects.flareCount() > 0, "Flare key failed");
        }
        if (frames == 112) {
            smokeGrenades = game.getGrenadesRemaining();
            tap(Keyboard.G);
            require(game.getGrenadesRemaining() == smokeGrenades - 1, "Grenade key failed");
        }
        if (frames == 140) {
            key(Keyboard.ESCAPE, PRESS);
            require(!playing, "Escape did not pause");
            smokeStep = game.getPhysics().getStepCount();
            key(Keyboard.ESCAPE, REPEAT);
            require(!playing, "Repeated Escape resumed the game");
            key(Keyboard.ESCAPE, RELEASE);
        }
        if (frames == 142) {
            require(
                    game.getPhysics().getStepCount() == smokeStep,
                    "Paused game continued stepping");
            click("Particle lights: on");
            require(
                    !effects.lights && effects.attachedLightCount() == 0,
                    "Particle light toggle failed");
            click("Particle physics: Jolt");
            require(
                    !effects.physical && effects.particleCount() == 0,
                    "Physics toggle did not clear effects");
        }
        if (frames == 143) {
            click("Particle physics: visual only");
            click("Particle lights: off");
            click("Amber particles");
            click("Cyan particles");
            require(
                    effects.physical && effects.lights && effects.palette == 1,
                    "Console mode/color edits failed");
        }
        if (frames == 145) {
            capture("console-edited.png");
            click("Resume run");
            require(playing, "Resume failed");
        }
        if (frames == 144) {
            click("Flare shadows: 2");
            click("Flare shadows: 4");
            require(particleShadowBudget == 4, "Flare shadow quality control failed");
        }
        if (frames == 148) tap(Keyboard.F);
        if (frames == 210) {
            require(!game.isReloading() && game.getAmmo() == smokeAmmo, "Reload did not complete");
            require(
                    renderer.getPointLightCount() > roomLights.size(),
                    "Attached lights are missing from Filament");
            require(
                    effects.shadowLightCount() > 0
                            && effects.shadowLightCount() <= particleShadowBudget,
                    "Active flares did not receive bounded shadow slots");
            capture("combat.png");
        }
        if (frames == 280) {
            require(game.getGrenadeCount() == 0, "Grenade did not expire/explode");
            require(
                    eye.isFinite()
                            && game.getHealth() > 0
                            && maximumParticles > 12
                            && maximumLights > 4,
                    "Combat validation incomplete");
            var previousWorld = game.getPhysics();
            tap(Keyboard.ESCAPE);
            click("Restart run");
            require(
                    previousWorld.isClosed() && playing && game.getHealth() == 100,
                    "Restart leaked or failed");
            require(
                    toastTime == 0
                            && scoreText.equals("000000")
                            && supplies.startsWith("GRENADES 4"),
                    "Restart retained stale combat HUD state");
        }
        if (frames == 290) {
            require(PlatformTools.graphicsError() == 0, "FPS renderer GL error");
            capture("ready.png");
            System.out.println(
                    "FPS_INPUT_VALIDATED movement, jump, mouse look, aim, shooting, reload,"
                        + " grenades, flares, lighting/physics/color edits, pause/repeat isolation"
                        + " and restart; peak particles="
                            + maximumParticles
                            + ", peak lights="
                            + maximumLights);
            Window.requestClose();
        }
    }

    /** Collects a bounded set of render/physics samples after warm-up, reports them and exits. */
    private void benchmarkFrame() {
        long actionStart = System.nanoTime();
        if (frames == 1) {
            game.setWavesEnabled(false);
            game.clearDrones();
            game.spawnDrone(-7, 14, 1.7f);
            game.spawnDrone(7, 16, 1.7f);
        }
        yaw = (float) Math.sin(frames * .012) * .28f;
        pitch = -.03f;
        if (frames % 8 == 0 && game.fire(eye, forward)) recoil = .07f;
        if (game.getAmmo() == 0) game.reload();
        if (frames % 45 == 0) effects.flare(eye, forward, game.getPlayerBody());
        if (frames == 100 || frames == 260) game.throwGrenade(eye, forward);
        // Include scripted firing, raycasts and effect births, which run here
        // instead of through real input callbacks in this repeatable workload.
        double actionMs = (System.nanoTime() - actionStart) * 1e-6;
        maximumParticles = Math.max(maximumParticles, effects.particleCount());
        maximumLights = Math.max(maximumLights, renderer.getPointLightCount());
        if (frames > 90 && frames <= 450) {
            renderSamples[frames - 91] = renderMs;
            simulationSamples[frames - 91] = simulationMs + actionMs;
        }
        if (frames == 450) {
            require(
                    !game.isDead() && eye.isFinite() && PlatformTools.graphicsError() == 0,
                    "Invalid FPS benchmark state");
            report("render", renderSamples);
            report("simulation", simulationSamples);
            capture("benchmark-" + physicalParticles + "-" + particleLights + ".png");
            System.out.println(
                    "FPS_BENCHMARK_VALIDATED physical="
                            + physicalParticles
                            + ", particleLights="
                            + particleLights
                            + ", particleShadowBudget="
                            + particleShadowBudget
                            + ", peakParticles="
                            + maximumParticles
                            + ", peakLights="
                            + maximumLights
                            + ", bodies="
                            + game.getBodyCount()
                            + ", cachedMeshes="
                            + renderer.getCachedMeshCount());
            Window.requestClose();
        }
    }

    /**
     * Prints mean, median and 95th-percentile timings in milliseconds; sorting mutates the supplied
     * measurement array.
     */
    private static void report(String name, double[] samples) {
        Arrays.sort(samples);
        System.out.printf(
                Locale.ROOT,
                "%s mean %.3f ms / median %.3f ms / p95 %.3f ms; %s%n",
                name,
                Arrays.stream(samples).average().orElseThrow(),
                samples[samples.length / 2],
                samples[(int) (samples.length * .95)],
                PlatformTools.rendererName());
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        Mouse.setCursorMode(Mouse.CURSOR_NORMAL);
        Mouse.setRawMouseMotion(false);
        Keyboard.removeKeyListener(keys);
        Keyboard.removeKeyListener(keyEdges);
        Mouse.removeMouseListener(mouse);
        JGL.unsubscribe(EventTypes.WINDOW_FOCUS, focus);
        effects.close();
        game.close();
        renderer.close();
        environmentModels.close();
        combatModels.close();
        models.close();
        ui.dispose();
        theme.close();
    }
}
