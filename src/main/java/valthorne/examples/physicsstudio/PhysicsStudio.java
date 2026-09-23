// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.physicsstudio;

import valthorne.graphics.render.FilamentRenderer3D;
import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.scene.Scene3D;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.opengl.GL43.*;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;
import org.lwjgl.nanovg.NVGPaint;

import valthorne.*;
import valthorne.camera.OrbitCameraController;
import valthorne.camera.PerspectiveCamera;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyAdapter;
import valthorne.event.listeners.MouseAdapter;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.examples.assets.PhysicsStudioModels;
import valthorne.examples.lightrig.StudioLightRig;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.math.physics.*;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIContainer;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.theme.ProfessionalTheme;
import valthorne.viewport.ScreenViewport;

import java.nio.file.Path;
import java.util.*;

/**
 * A combined simulation editor with fourteen focused labs spanning every current three-dimensional
 * physics feature, plus raycast interaction, live contact telemetry and an editable light rig.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>Windows x64, Filament and OpenGL 4.3 are required. The application retains shared gallery
 * meshes while replacing scenario worlds. Emitters close before their borrowed world; listeners
 * detach during disposal. Scene gestures begin only after UI routing decides ownership.
 *
 * <p>Run through the documented Gradle task for validated options and platform checks. Study the
 * accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/physics-studio.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class PhysicsStudio implements Application {
    /** Dark, input-stable gradient surface used for the studio's major chrome regions. */
    private static final class GradientPanel extends NanoPanel {
        private final NVGPaint paint = NVGPaint.create();

        /** Creates a transparent standard panel over a custom vertical gradient. */
        private GradientPanel() {
            var clear = new Color(0, 0, 0, 0);
            backgroundColor(clear);
            hoverBackgroundColor(clear);
            focusedBackgroundColor(clear);
            pressedBackgroundColor(clear);
            disabledBackgroundColor(clear);
            borderWidth(0);
            setStyle(BACKGROUND_COLOR_KEY, clear);
            setStyle(HOVER_BACKGROUND_COLOR_KEY, clear);
            setStyle(FOCUSED_BACKGROUND_COLOR_KEY, clear);
            setStyle(PRESSED_BACKGROUND_COLOR_KEY, clear);
            setStyle(DISABLED_BACKGROUND_COLOR_KEY, clear);
            setStyle(BORDER_WIDTH_KEY, 0f);
        }

        /** Paints the gradient first, then delegates child rendering to the normal panel path. */
        @Override
        public void draw(long vg) {
            float x = getAbsoluteX(), y = getAbsoluteY(), w = getWidth(), h = getHeight();
            nvgLinearGradient(
                    vg,
                    x,
                    y,
                    x + w * .35f,
                    y + h,
                    NanoUtility.color1(new Color(.055f, .075f, .13f, .98f)),
                    NanoUtility.color2(new Color(.018f, .025f, .055f, .98f)),
                    paint);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, 11);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + .5f, y + .5f, w - 1, h - 1, 10.5f);
            nvgStrokeWidth(vg, 1);
            nvgStrokeColor(vg, NanoUtility.color1(new Color(.18f, .38f, .58f, .7f)));
            nvgStroke(vg);
            super.draw(vg);
        }
    }

    private static final int BALL_PIT = 0;
    private static final int SHAPES = 1;
    private static final int MATERIALS = 2;
    private static final int FORCES = 3;
    private static final int BODY_BEHAVIOR = 4;
    private static final int KINEMATICS = 5;
    private static final int SENSORS = 6;
    private static final int LAYERS = 7;
    private static final int JOINTS = 8;
    private static final int CCD_RAYCAST = 9;
    private static final int DOMINOES = 10;
    private static final int STRESS = 11;
    private static final int GALLERY = 12;
    private static final int PARTICLES = 13;
    private static final List<String> SCENARIO_NAMES =
            List.of(
                    "Ball pit / dynamic spheres",
                    "Collision shape gallery",
                    "Friction + restitution lab",
                    "Forces + impulses + torque",
                    "Damping + gravity + sleep",
                    "Kinematic moving platforms",
                    "Sensors + contact events",
                    "Collision layer filtering",
                    "Distance joints + chains",
                    "CCD cannon + raycasting",
                    "Dominoes + rolling ramp",
                    "Stress / 288 mixed bodies",
                    "Mesh + convex hull gallery",
                    "Particle fountain / Jolt");
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final OrbitCameraController orbit = new OrbitCameraController();
    private final Random random = new Random(42);
    private final Rayf ray = new Rayf();
    private final ArrayList<RigidBody3D> dynamic = new ArrayList<>();
    private StudioLightRig rig;
    private PhysicsStudioParticles particles;
    private PhysicsStudioModels gallery;
    private final ArrayList<CollisionShape3D> galleryHulls = new ArrayList<>();
    private NanoLabel lightMarker;
    private NanoComboBox<String> modelList;
    private final Vector3f projectedLight = new Vector3f();
    private int gesture, heldShortcuts, freshShortcut;
    // Observe press edges before UI consumption so a held popup-dismissal key cannot
    // turn into a fresh world shortcut when GLFW sends its next repeat event.
    private final KeyAdapter shortcutEdges =
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyPressEvent e) {
                    int bit = shortcutBit(e.getKey());
                    freshShortcut = (heldShortcuts & bit) == 0 ? bit : 0;
                    heldShortcuts |= bit;
                }

                @Override
                public void keyReleased(KeyReleaseEvent e) {
                    heldShortcuts &= ~shortcutBit(e.getKey());
                }
            };
    private float dragZ, dragOffsetX, dragOffsetY;
    private final MouseAdapter sceneMouse =
            new MouseAdapter() {
                @Override
                public void mousePressed(MousePressEvent e) {
                    beginPointer(e.getX(), e.getY(), e.getButton(), e.isShiftDown());
                }

                @Override
                public void mouseReleased(MouseReleaseEvent e) {
                    if (e.getButton() == gestureButton()) gesture = 0;
                }

                @Override
                public void mouseMoved(MouseMoveEvent e) {
                    movePointer(e.getX(), e.getY());
                }

                @Override
                public void mouseDragged(MouseDragEvent e) {
                    movePointer(e.getToX(), e.getToY());
                }
            };
    private final KeyAdapter sceneKeys =
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyPressEvent e) {
                    int bit = shortcutBit(e.getKey());
                    if (bit == 0) return;
                    if ((freshShortcut & bit) == 0) {
                        e.consume();
                        return;
                    }
                    if (e.getKey() == GLFW_KEY_ESCAPE) {
                        if (rig.isPlacing()) {
                            rig.setPlacing(false);
                            e.consume();
                        } else glfwSetWindowShouldClose(Window.getAddress(), true);
                    } else if (e.getKey() == GLFW_KEY_SPACE) {
                        shoot();
                        e.consume();
                    } else if (e.getKey() == GLFW_KEY_R) {
                        reset();
                        e.consume();
                    }
                }

                @Override
                public void keyReleased(KeyReleaseEvent e) {
                    heldShortcuts &= ~shortcutBit(e.getKey());
                }
            };
    private PhysicsWorld3D physics;
    private FilamentRenderer3D renderer;
    private Model3D cube, sphere, cylinder, domino, ground, platformMesh;
    private UIRoot ui;
    private ProfessionalTheme theme;
    private NanoPanel header, controls, footer;
    private NanoContainer spawnDeck, standardSpawn, particleControls;
    private NanoLabel stats, simulation;
    private NanoButton pause, focusModel;
    private int width, height, vw, vh, frames, scenario = BALL_PIT, initialLights = 3;
    private float speed = 1, gravity = 9.81f, restitution = .25f, mass = 2;
    private float environmentPower = 100;
    private float lastX, lastY, clock, physicsMs, renderMs, reportClock;
    private int reportFrames;
    private boolean paused, benchmark, smoke;
    private boolean lightingEnabled = true;
    private int contactAdded, contactPersisted, contactRemoved;
    private boolean visualParticles;
    private float particleRate = 48;
    private final double[] renderSamples = new double[240], physicsSamples = new double[240];
    private final MouseScrollListener wheel =
            e -> {
                if (gesture == 0 && inViewport()) orbit.zoom(e.preciseYOffset());
            };

    /**
     * Starts this application on the process main thread. Use the shared ExampleLauncher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        var app = new PhysicsStudio();
        for (String a : args) {
            if (a.equals("--benchmark")) app.benchmark = true;
            if (a.equals("--smoke")) app.smoke = true;
            if (a.startsWith("--scenario=")) app.scenario = Integer.parseInt(a.substring(11));
            if (a.startsWith("--lights=")) app.initialLights = Integer.parseInt(a.substring(9));
            if (a.equals("--visual-particles")) app.visualParticles = true;
            if (a.startsWith("--particle-rate="))
                app.particleRate = Float.parseFloat(a.substring(16));
        }
        if (app.scenario < 0 || app.scenario >= SCENARIO_NAMES.size())
            throw new IllegalArgumentException(
                    "Scenario must be between 0 and " + (SCENARIO_NAMES.size() - 1));
        if (!Float.isFinite(app.particleRate) || app.particleRate < 0 || app.particleRate > 120)
            throw new IllegalArgumentException("Particle rate must be between 0 and 120");
        if (app.initialLights < 3 || app.initialLights > StudioLightRig.MAX_LIGHTS)
            throw new IllegalArgumentException("Initial light count must be between 3 and 16");
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .contextVersion(4, 3)
                        .size(1600, 960)
                        .depthBits(24)
                        .visible(!app.benchmark && !app.smoke)
                        .title("Valthorne | Physics Studio - Jolt + Filament"));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        renderer = new FilamentRenderer3D();
        renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
        renderer.setExposure(3.5f);
        renderer.setEnvironmentIntensity(environmentPower);
        glfwSetWindowSizeLimits(Window.getAddress(), 1440, 960, GLFW_DONT_CARE, GLFW_DONT_CARE);
        cube = ModelBuilder3D.box(1, 1, 1);
        sphere = ModelBuilder3D.sphere(.5f, 24, 16);
        cylinder = ModelBuilder3D.cylinder(.5f, 1.4f, 24);
        domino = ModelBuilder3D.box(.22f, .8f, 1.6f);
        ground = ModelBuilder3D.box(24, 20, 1);
        platformMesh = ModelBuilder3D.box(3, 3, .4f);
        particles = new PhysicsStudioParticles();
        particles.physical = !visualParticles;
        particles.rate = particleRate;
        camera.setClipPlanes(.1f, 150);
        camera.setFieldOfViewDegrees(48);
        gallery = new PhysicsStudioModels();
        for (var entry : gallery.entries())
            galleryHulls.add(CollisionShape3D.convexHull(entry.model()));
        resetCamera();
        reset();
        Keyboard.addKeyListener(shortcutEdges);
        ui = new UIRoot();
        ui.setClickable(false);
        ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));
        theme = new ProfessionalTheme(false, 1);
        ui.setTheme(theme.create());
        rig = new StudioLightRig(theme);
        var rigSurface = new Color(.045f, .065f, .105f, .98f);
        var rigBorder = new Color(.18f, .38f, .58f, .7f);
        rig.panel.setStyle(NanoPanel.BACKGROUND_COLOR_KEY, rigSurface);
        rig.panel.setStyle(NanoPanel.HOVER_BACKGROUND_COLOR_KEY, rigSurface);
        rig.panel.setStyle(NanoPanel.FOCUSED_BACKGROUND_COLOR_KEY, rigSurface);
        rig.panel.setStyle(NanoPanel.PRESSED_BACKGROUND_COLOR_KEY, rigSurface);
        rig.panel.setStyle(NanoPanel.BORDER_COLOR_KEY, rigBorder);
        rig.panel.setStyle(NanoPanel.HOVER_BORDER_COLOR_KEY, rigBorder);
        rig.panel.setStyle(NanoPanel.FOCUSED_BORDER_COLOR_KEY, rigBorder);
        rig.panel.setStyle(NanoPanel.PRESSED_BORDER_COLOR_KEY, rigBorder);
        rig.attach(scene);
        while (rig.size() < initialLights) {
            int i = rig.size() - 3;
            rig.add((i % 5 - 2) * 3, (i / 5) * 4 - 5, 3);
            rig.setSelectedPower(20);
        }
        rig.select(rig.models().getFirst());
        buildUi();
        resize();
        Mouse.addScrollListener(wheel);
        Mouse.addMouseListener(sceneMouse);
        Keyboard.addKeyListener(sceneKeys);
        glfwSwapInterval(0);
    }

    /** Restores the authored perspective and orbit framing for the current scenario. */
    private void resetCamera() {
        orbit.reset();
        orbit.zoom(-5);
        orbit.orbit(-65, -35);
        orbit.getTarget().set(0, 0, 1.4f);
    }

    /** Creates the indexed palette material used to distinguish physics bodies. */
    private Color color(int i) {
        return switch (i % 4) {
            case 0 -> new Color(.13f, .62f, .72f, 1);
            case 1 -> new Color(.95f, .48f, .17f, 1);
            case 2 -> new Color(.56f, .4f, .84f, 1);
            default -> new Color(.72f, .78f, .86f, 1);
        };
    }

    /**
     * Adds a visual/body pair with a borrowed mesh and full collision shape, registers it with the
     * world and applies the selected material.
     */
    private RigidBody3D body(
            Model3D mesh,
            CollisionShape3D shape,
            MotionType3D motion,
            float x,
            float y,
            float z,
            int shade) {
        var model =
                new ModelInstance3D()
                        .setModel(mesh)
                        .setMaterial(
                                new Material3D()
                                        .setTint(color(shade))
                                        .setRoughness(mesh == sphere ? .18f : .4f)
                                        .setMetallic(mesh == sphere ? .8f : .15f));
        var b =
                physics.createBody(
                                new BodySettings3D(shape, motion)
                                        .setPosition(x, y, z)
                                        .setMass(mass)
                                        .setFriction(.65f)
                                        .setRestitution(restitution)
                                        .setContinuousCollision(motion == MotionType3D.DYNAMIC))
                        .bind(model);
        scene.add(model);
        if (motion == MotionType3D.DYNAMIC) dynamic.add(b);
        return b;
    }

    /**
     * Recreates the selected scenario's physics world and bodies while preserving reusable gallery
     * and renderer resources.
     */
    private void reset() {
        if (particles != null) particles.close();
        if (physics != null) physics.close();
        scene.clear();
        dynamic.clear();
        gesture = 0;
        random.setSeed(42);
        clock = 0;
        contactAdded = contactPersisted = contactRemoved = 0;
        if (scenario == LAYERS) {
            var layers = new CollisionLayers3D();
            layers.setCollision(1, 2, false);
            physics = new PhysicsWorld3D(PhysicsWorld3D.Settings.defaults(), layers);
        } else physics = new PhysicsWorld3D();
        physics.setGravity(new Vector3f(0, 0, -gravity));
        physics.addContactListener(
                event -> {
                    switch (event.type()) {
                        case ADDED -> contactAdded++;
                        case PERSISTED -> contactPersisted++;
                        case REMOVED -> contactRemoved++;
                    }
                });
        body(ground, CollisionShape3D.box(24, 20, 1), MotionType3D.STATIC, 0, 0, -.5f, 3);
        // Inlaid floor markings share one cube mesh and have no collision overhead.
        for (int i = -10; i <= 10; i += 2) {
            scene.add(
                    new ModelInstance3D()
                            .setModel(cube)
                            .setPosition(i, 0, .004f)
                            .setScale(.014f, 18, .006f)
                            .setMaterial(
                                    new Material3D()
                                            .setTint(new Color(.28f, .34f, .4f, 1))
                                            .setRoughness(.8f)));
        }
        switch (scenario) {
            case BALL_PIT -> buildBallPit();
            case SHAPES -> buildShapeGallery();
            case MATERIALS -> buildMaterialLab();
            case FORCES -> buildForceLab();
            case BODY_BEHAVIOR -> buildBodyBehaviorLab();
            case KINEMATICS -> buildKinematicLab();
            case SENSORS -> buildSensorLab();
            case LAYERS -> buildLayerLab();
            case JOINTS -> buildJointLab();
            case CCD_RAYCAST -> buildCcdLab();
            case DOMINOES -> buildDominoLab();
            case STRESS -> buildStressLab();
            case GALLERY -> buildGallery();
            case PARTICLES -> buildParticleLab();
            default -> throw new AssertionError("Unknown scenario " + scenario);
        }
        if (spawnDeck != null) {
            spawnDeck.clear();
            spawnDeck.add(scenario == PARTICLES ? particleControls : standardSpawn);
        }
        if (rig != null) rig.attach(scene);
        if (focusModel != null)
            focusModel.setEnabled(scenario == GALLERY && modelList.getSelectedIndex() < 3);
        physics.optimizeBroadPhase();
    }

    /** Builds a walled basin filled with seeded, continuously colliding dynamic spheres. */
    private void buildBallPit() {
        particleObstacle(0, 5, 1.5f, 12, .4f, 3, 0, 3);
        particleObstacle(0, -5, 1.5f, 12, .4f, 3, 0, 3);
        particleObstacle(-6, 0, 1.5f, .4f, 10, 3, 0, 3);
        particleObstacle(6, 0, 1.5f, .4f, 10, 3, 0, 3);
        for (int i = 0; i < 112; i++)
            body(
                    sphere,
                    CollisionShape3D.sphere(.5f),
                    MotionType3D.DYNAMIC,
                    random.nextFloat() * 10 - 5,
                    random.nextFloat() * 8 - 4,
                    1 + i / 24f,
                    i);
    }

    /** Demonstrates every moving convex shape plus the static triangle-mesh shape. */
    private void buildShapeGallery() {
        body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, -5, 0, 5, 0);
        body(sphere, CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC, -3, 0, 5, 1);
        body(cylinder, CollisionShape3D.cylinder(.5f, 1.4f), MotionType3D.DYNAMIC, -1, 0, 5, 2);
        body(cylinder, CollisionShape3D.capsule(.5f, .7f), MotionType3D.DYNAMIC, 1, 0, 5, 3);
        body(
                gallery.entries().getFirst().model(),
                galleryHulls.getFirst(),
                MotionType3D.DYNAMIC,
                3,
                0,
                5,
                0);
        var entry = gallery.entries().get(1);
        var mesh = new ModelInstance3D().setModel(entry.model()).setMaterial(entry.material());
        physics.createBody(
                        new BodySettings3D(
                                        CollisionShape3D.mesh(entry.model()), MotionType3D.STATIC)
                                .setPosition(5, 0, 0))
                .bind(mesh);
        scene.add(mesh);
    }

    /** Compares low, medium and high friction against three restitution coefficients. */
    private void buildMaterialLab() {
        for (int i = 0; i < 3; i++) {
            float x = -5 + i * 5;
            particleObstacle(x, 1, 2.2f, 3.8f, 4, .25f, -.35f, i);
            customBody(
                    sphere,
                    new BodySettings3D(CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC)
                            .setPosition(x - 1, 1, 5)
                            .setFriction(i * .6f),
                    i);
            customBody(
                    sphere,
                    new BodySettings3D(CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC)
                            .setPosition(x, -3, 6)
                            .setRestitution(i * .5f),
                    i + 1);
        }
    }

    /** Applies velocity, impulses, force and torque so their different effects are visible. */
    private void buildForceLab() {
        var velocity = body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, -5, 0, 2, 0);
        velocity.setLinearVelocity(0, 3, 4);
        var impulse =
                body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, -2.5f, 0, 2, 1);
        impulse.addImpulse(new Vector3f(0, 6, 8));
        var point = body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, 0, 0, 2, 2);
        point.addImpulse(new Vector3f(0, 8, 4), new Vector3f(.5f, 0, 2.5f));
        var angular =
                body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, 2.5f, 0, 2, 3);
        angular.addAngularImpulse(new Vector3f(0, 0, 8));
        var forced = body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC, 5, 0, 2, 0);
        physics.addBeforeStepListener(
                world -> {
                    forced.addForce(new Vector3f(0, 8, 16));
                    forced.addTorque(new Vector3f(0, 0, 5));
                });
    }

    /** Shows damping, gravity scaling, rotation locking and sleeping policy side by side. */
    private void buildBodyBehaviorLab() {
        float[] gravityFactors = {0, .35f, 1, 2};
        for (int i = 0; i < gravityFactors.length; i++)
            customBody(
                    sphere,
                    new BodySettings3D(CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC)
                            .setPosition(-4.5f + i * 3, 2, 6)
                            .setGravityFactor(gravityFactors[i])
                            .setDamping(i * .4f, i * .4f),
                    i);
        customBody(
                cube,
                new BodySettings3D(CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC)
                        .setPosition(-2, -3, 5)
                        .setRotationLocked(true),
                1);
        customBody(
                cube,
                new BodySettings3D(CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC)
                        .setPosition(2, -3, 5)
                        .setAllowSleeping(false),
                2);
    }

    /** Moves kinematic platforms each fixed step while dynamic passengers respond physically. */
    private void buildKinematicLab() {
        var left =
                body(
                        platformMesh,
                        CollisionShape3D.box(3, 3, .4f),
                        MotionType3D.KINEMATIC,
                        -4,
                        0,
                        2,
                        0);
        var right =
                body(
                        platformMesh,
                        CollisionShape3D.box(3, 3, .4f),
                        MotionType3D.KINEMATIC,
                        4,
                        0,
                        3,
                        2);
        for (int i = 0; i < 12; i++)
            body(
                    i % 2 == 0 ? cube : sphere,
                    i % 2 == 0 ? CollisionShape3D.box(1, 1, 1) : CollisionShape3D.sphere(.5f),
                    MotionType3D.DYNAMIC,
                    i < 6 ? -4 : 4,
                    (i % 6 - 3) * .7f,
                    4 + i % 3,
                    i);
        physics.addBeforeStepListener(
                world -> {
                    float t = world.getStepCount() * world.getFixedTimeStep();
                    left.moveKinematic(
                            new Vector3f(-4, 0, 2 + (float) Math.sin(t) * 1.5f), new Quaternionf());
                    right.moveKinematic(
                            new Vector3f(4 + (float) Math.sin(t * .7f) * 2, 0, 3),
                            new Quaternionf().rotationZ(t * .2f));
                });
    }

    /** Creates non-solid sensor volumes and falling bodies that generate contact events. */
    private void buildSensorLab() {
        for (int i = 0; i < 3; i++) {
            float x = (i - 1) * 4;
            customBody(
                    cube,
                    new BodySettings3D(CollisionShape3D.box(3, 3, 1), MotionType3D.STATIC)
                            .setPosition(x, 0, 2 + i)
                            .setSensor(true),
                    i);
            for (int j = 0; j < 5; j++)
                body(
                        sphere,
                        CollisionShape3D.sphere(.5f),
                        MotionType3D.DYNAMIC,
                        x,
                        0,
                        7 + j,
                        i + j);
        }
    }

    /** Demonstrates custom collision filtering: layer one and two ignore one another. */
    private void buildLayerLab() {
        for (int i = 0; i < 12; i++)
            customBody(
                    sphere,
                    new BodySettings3D(CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC)
                            .setPosition(i % 2 == 0 ? -4 : 4, 0, 2 + i * .7f)
                            .setLinearVelocity(i % 2 == 0 ? 7 : -7, 0, 0)
                            .setLayer(i % 2 + 1),
                    i % 2);
    }

    /** Connects a static anchor and dynamic links with distance constraints. */
    private void buildJointLab() {
        var anchor = body(cube, CollisionShape3D.box(1, 1, 1), MotionType3D.STATIC, 0, 0, 8, 3);
        RigidBody3D previous = anchor;
        for (int i = 0; i < 10; i++) {
            var link =
                    body(
                            sphere,
                            CollisionShape3D.sphere(.5f),
                            MotionType3D.DYNAMIC,
                            0,
                            0,
                            7 - i,
                            i);
            physics.createDistanceJoint(previous, link, new Vector3f(), new Vector3f(), .8f, 1.15f);
            previous = link;
        }
    }

    /** Fires discrete and continuous fast bodies toward thin walls for a CCD comparison. */
    private void buildCcdLab() {
        particleObstacle(0, 0, 2, .15f, 10, 4, 0, 3);
        for (int i = 0; i < 10; i++)
            customBody(
                    sphere,
                    new BodySettings3D(CollisionShape3D.sphere(.22f), MotionType3D.DYNAMIC)
                            .setPosition(-9, -4 + i * .9f, 2)
                            .setLinearVelocity(65, 0, 0)
                            .setContinuousCollision(i >= 5),
                    i >= 5 ? 0 : 1);
    }

    /** Builds a rolling-ball ramp followed by a long domino chain. */
    private void buildDominoLab() {
        particleObstacle(-6, -3, 3, 7, 2, .3f, -.35f, 0);
        body(sphere, CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC, -8, -3, 6, 1);
        for (int i = 0; i < 45; i++)
            body(
                    domino,
                    CollisionShape3D.box(.22f, .8f, 1.6f),
                    MotionType3D.DYNAMIC,
                    -4 + i * .35f,
                    -1 + (float) Math.sin(i * .18f) * 2,
                    .8f,
                    i);
    }

    /** Creates a dense mixed-body stack for broad-phase and solver profiling. */
    private void buildStressLab() {
        for (int z = 0; z < 8; z++)
            for (int y = 0; y < 6; y++)
                for (int x = 0; x < 6; x++)
                    body(
                            (x + y + z) % 2 == 0 ? cube : sphere,
                            (x + y + z) % 2 == 0
                                    ? CollisionShape3D.box(1, 1, 1)
                                    : CollisionShape3D.sphere(.5f),
                            MotionType3D.DYNAMIC,
                            (x - 2.5f) * 1.05f,
                            (y - 2.5f) * 1.05f,
                            1 + z * 1.05f,
                            x + y + z);
    }

    /** Creates and binds a body configured with settings specific to a feature lab. */
    private RigidBody3D customBody(Model3D mesh, BodySettings3D settings, int shade) {
        var model =
                new ModelInstance3D()
                        .setModel(mesh)
                        .setMaterial(
                                new Material3D()
                                        .setTint(color(shade))
                                        .setRoughness(.36f)
                                        .setMetallic(.3f));
        var body = physics.createBody(settings).bind(model);
        scene.add(model);
        if (settings.getMotionType() == MotionType3D.DYNAMIC) dynamic.add(body);
        return body;
    }

    /**
     * Creates the bounded fountain scene and static obstacles used to compare physical and visual
     * particle modes.
     */
    private void buildParticleLab() {
        // A low catch tray and two angled deflectors make contacts easy to inspect.
        particleObstacle(0, 0, .25f, 10, 8, .5f, 0, 0);
        particleObstacle(-5, 0, .65f, .2f, 8, .8f, 0, 3);
        particleObstacle(5, 0, .65f, .2f, 8, .8f, 0, 3);
        particleObstacle(0, 4, .65f, 10, .2f, .8f, 0, 3);
        particleObstacle(0, -4, .65f, 10, .2f, .8f, 0, 3);
        particleObstacle(0, 0, 1.1f, 1.5f, 1.5f, 1.2f, 0, 3);
        particleObstacle(-2.6f, 0, 1.1f, 2.5f, 3, .25f, -.3f, 1);
        particleObstacle(2.6f, 0, 1.1f, 2.5f, 3, .25f, .3f, 2);
        particles.reset(physics, scene, gravity);
    }

    /**
     * Creates a tilted static particle obstacle with full dimensions in world units and the
     * selected palette material.
     */
    private void particleObstacle(
            float x, float y, float z, float w, float d, float h, float tilt, int shade) {
        var model =
                new ModelInstance3D()
                        .setModel(cube)
                        .setScale(w, d, h)
                        .setMaterial(
                                new Material3D()
                                        .setTint(color(shade))
                                        .setRoughness(.48f)
                                        .setMetallic(.25f));
        physics.createBody(
                        new BodySettings3D(CollisionShape3D.box(w, d, h), MotionType3D.STATIC)
                                .setPosition(x, y, z)
                                .setRotation(new Quaternionf().rotationY(tilt))
                                .setFriction(.4f)
                                .setRestitution(.35f))
                .bind(model);
        scene.add(model);
    }

    /** Arranges the shared textured gallery models and their matching physical supports. */
    private void buildGallery() {
        for (int i = 0; i < 3; i++) {
            var entry = gallery.entries().get(i);
            float x = (i - 1) * 5;
            float pedestalWidth = Math.max(3, entry.width() + .4f);
            var pedestal =
                    new ModelInstance3D()
                            .setModel(cube)
                            .setScale(pedestalWidth, 3, .5f)
                            .setMaterial(new Material3D().setTint(color(3)).setRoughness(.4f));
            physics.createBody(
                            new BodySettings3D(
                                            CollisionShape3D.box(pedestalWidth, 3, .5f),
                                            MotionType3D.STATIC)
                                    .setPosition(x, 1, .25f))
                    .bind(pedestal);
            scene.add(pedestal);
            var exhibit =
                    new ModelInstance3D().setModel(entry.model()).setMaterial(entry.material());
            physics.createBody(
                            new BodySettings3D(
                                            CollisionShape3D.mesh(entry.model()),
                                            MotionType3D.STATIC)
                                    .setRotation(entry.rotation())
                                    .setPosition(x, 1, .5f))
                    .bind(exhibit);
            scene.add(exhibit);
        }
        for (int i = 0; i < 7; i++)
            body(
                    sphere,
                    CollisionShape3D.sphere(.5f),
                    MotionType3D.DYNAMIC,
                    -4.5f + i * 1.5f,
                    -3,
                    1 + i * .35f,
                    i);
    }

    /**
     * Spawns a dynamic instance of the selected gallery asset without duplicating its shared
     * geometry.
     */
    private void dropModel() {
        if (dynamic.size() >= 600) return;
        int index = Math.max(0, modelList.getSelectedIndex());
        var model =
                new ModelInstance3D()
                        .setModel(gallery.entries().get(index).model())
                        .setMaterial(gallery.entries().get(index).material());
        var b =
                physics.createBody(
                                new BodySettings3D(galleryHulls.get(index), MotionType3D.DYNAMIC)
                                        .setRotation(gallery.entries().get(index).rotation())
                                        .setPosition(0, -3, 6)
                                        .setMass(mass)
                                        .setRestitution(restitution)
                                        .setFriction(.65f)
                                        .setContinuousCollision(true))
                        .bind(model);
        scene.add(model);
        dynamic.add(b);
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private NanoLabel label(String text, int size) {
        var l = new NanoLabel(text);
        l.setStyle(NanoLabel.FONT_SIZE_KEY, (float) size);
        l.getLayout().noShrink();
        return l;
    }

    /** Reframes the orbit camera around the selected gallery exhibit. */
    private void focusExhibit() {
        int index = modelList.getSelectedIndex();
        if (scenario != GALLERY || index < 0 || index >= 3) return;
        var entry = gallery.entries().get(index);
        orbit.reset();
        orbit.getTarget().set((index - 1) * 5, 1, .5f + entry.height() * .5f);
        float distance = Math.max(entry.height(), Math.max(entry.width(), entry.depth())) * 2.1f;
        orbit.zoom((float) (Math.log(12 / distance) / .12));
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private NanoButton button(String text, Runnable action) {
        var b = new NanoButton(text).action(n -> action.run());
        b.getLayout().height(34).noShrink();
        return b;
    }

    /** Creates an editor panel with the local background, padding and layout conventions. */
    private NanoPanel panel() {
        var p = new GradientPanel();
        p.getLayout().absolute().column().padding(16).gap(10);
        ui.add(p);
        return p;
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(
            String title,
            float min,
            float max,
            float value,
            java.util.function.DoubleConsumer change) {
        slider(controls, title, min, max, value, change);
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(
            UIContainer parent,
            String title,
            float min,
            float max,
            float value,
            java.util.function.DoubleConsumer change) {
        var l = label(title + String.format(Locale.ROOT, "  %.2f", value), 13);
        parent.add(l);
        var s =
                new NanoSlider(min, max, value)
                        .action(
                                n -> {
                                    change.accept(n.getValue());
                                    l.text(
                                            title
                                                    + String.format(
                                                            Locale.ROOT, "  %.2f", n.getValue()));
                                });
        s.getLayout().height(24).noShrink();
        parent.add(s);
    }

    /** Builds scenario, simulation and light controls around the scene viewport. */
    private void buildUi() {
        header = panel();
        header.getLayout().row().itemsCenter().gap(18);
        header.add(label("VALTHORNE / PHYSICS STUDIO", 22));
        header.add(label("JOLT  /  FILAMENT PBR", 14));
        header.add(button("Reset camera", this::resetCamera));
        controls = panel();
        controls.getLayout().gap(6).padding(12);
        controls.add(label("SIMULATION LAB", 16));
        var scenes =
                new NanoComboBox<String>()
                        .items(SCENARIO_NAMES)
                        .selectedIndex(scenario)
                        .onChange(
                                v -> {
                                    scenario = SCENARIO_NAMES.indexOf(v);
                                    reset();
                                });
        scenes.getLayout().widthPercent(100).height(36).noShrink();
        controls.add(scenes);
        pause =
                button(
                        "Pause simulation",
                        () -> {
                            paused = !paused;
                            pause.text(paused ? "Resume simulation" : "Pause simulation");
                        });
        controls.add(pause);
        controls.add(
                button(
                        "Single step",
                        () -> {
                            paused = true;
                            pause.text("Resume simulation");
                            physics.step();
                            particles.update(physics.getFixedTimeStep(), gravity);
                        }));
        controls.add(button("Reset scene [R]", this::reset));
        slider("Time scale", .05f, 2, 1, v -> speed = (float) v);
        slider(
                "Gravity",
                0,
                20,
                gravity,
                v -> {
                    gravity = (float) v;
                    physics.setGravity(new Vector3f(0, 0, -gravity));
                });
        spawnDeck = new NanoContainer();
        spawnDeck.getLayout().column().noShrink();
        controls.add(spawnDeck);
        standardSpawn = new NanoContainer();
        standardSpawn.getLayout().column().gap(6).noShrink();
        standardSpawn.add(label("SPAWN SETTINGS", 14));
        slider(standardSpawn, "Mass / kg", .2f, 20, mass, v -> mass = (float) v);
        slider(standardSpawn, "Bounce", 0, 1, restitution, v -> restitution = (float) v);
        standardSpawn.add(button("Drop metal sphere", () -> spawn(true)));
        standardSpawn.add(button("Drop colored cube", () -> spawn(false)));
        standardSpawn.add(label("IMPORTED MODELS", 14));
        modelList =
                new NanoComboBox<String>()
                        .items(
                                gallery.entries().stream()
                                        .map(PhysicsStudioModels.Entry::name)
                                        .toList())
                        .selectedIndex(0);
        modelList.getLayout().widthPercent(100).height(36).noShrink();
        modelList.onChange(
                value ->
                        focusModel.setEnabled(
                                scenario == GALLERY && modelList.getSelectedIndex() < 3));
        standardSpawn.add(modelList);
        standardSpawn.add(button("Drop selected model", this::dropModel));
        focusModel = button("Focus selected exhibit", this::focusExhibit);
        focusModel.setEnabled(scenario == GALLERY);
        standardSpawn.add(focusModel);
        buildParticleControls();
        spawnDeck.add(scenario == PARTICLES ? particleControls : standardSpawn);
        controls.add(label("ENVIRONMENT", 14));
        var lighting = button("Lighting: enabled", () -> {});
        lighting.action(
                n -> {
                    lightingEnabled = !lightingEnabled;
                    rig.setEnabled(lightingEnabled);
                    renderer.setEnvironmentIntensity(lightingEnabled ? environmentPower : 0);
                    lighting.text(lightingEnabled ? "Lighting: enabled" : "Lighting: disabled");
                });
        controls.add(lighting);
        slider("Exposure", .25f, 8, 3.5f, v -> renderer.setExposure((float) v));
        slider(
                "Environment",
                0,
                1000,
                environmentPower,
                v -> {
                    environmentPower = (float) v;
                    if (lightingEnabled) renderer.setEnvironmentIntensity(environmentPower);
                });
        ui.add(rig.panel);
        lightMarker = label("Selected light", 13);
        lightMarker.setClickable(false);
        lightMarker.getLayout().absolute().width(130).height(24);
        ui.add(lightMarker);
        simulation = label("", 13);
        controls.add(simulation);
        footer = panel();
        stats = label("Warming up...", 14);
        footer.add(stats);
    }

    /** Drops a sphere or box into the current scenario using the configured physical appearance. */
    private void spawn(boolean ball) {
        if (dynamic.size() >= 600) return;
        body(
                ball ? sphere : cube,
                ball ? CollisionShape3D.sphere(.5f) : CollisionShape3D.box(1, 1, 1),
                MotionType3D.DYNAMIC,
                random.nextFloat() * 6 - 3,
                -2,
                8,
                random.nextInt(4));
    }

    /** Builds the fountain emission, body/visual mode and particle appearance controls. */
    private void buildParticleControls() {
        particleControls = new NanoContainer();
        particleControls.getLayout().column().gap(6).noShrink();
        particleControls.add(label("3D PARTICLE LAB", 14));
        var mode =
                button(
                        particles.physical
                                ? "Physics: Jolt collisions"
                                : "Physics: off / visual only",
                        () -> {});
        mode.action(
                n -> {
                    particles.physical = !particles.physical;
                    particles.reset(physics, scene, gravity);
                    mode.text(
                            particles.physical
                                    ? "Physics: Jolt collisions"
                                    : "Physics: off / visual only");
                });
        particleControls.add(mode);
        slider(
                particleControls,
                "Particles / second",
                0,
                120,
                particles.rate,
                v -> particles.setRate((float) v));
        slider(
                particleControls,
                "Lifetime / seconds",
                .5f,
                8,
                particles.lifetime,
                v -> particles.lifetime = (float) v);
        slider(
                particleControls,
                "Particle bounce",
                0,
                1,
                particles.bounce,
                v -> particles.bounce = (float) v);
        var emit = button("Stop emission", () -> {});
        emit.action(
                n -> {
                    particles.setEmitting(!particles.emitting);
                    emit.text(particles.emitting ? "Stop emission" : "Start emission");
                });
        particleControls.add(emit);
        var row = new NanoContainer();
        row.getLayout().row().gap(6).noShrink();
        var burst = button("Burst 48", particles::burst);
        var clear = button("Clear particles", particles::clear);
        burst.getLayout().grow();
        clear.getLayout().grow();
        row.add(burst, clear);
        particleControls.add(row);
        particleControls.add(label("Lifetime / bounce apply to new births", 12));
        particleControls.add(label("Shared mesh / 512 particle limit", 12));
    }

    /** Launches a dynamic projectile from the camera into the physics scene. */
    private void shoot() {
        if (dynamic.size() >= 600) return;
        camera.screenPointToRay(320 + vw * .5f, 60 + vh * .5f, 320, 60, vw, vh, ray);
        body(sphere, CollisionShape3D.sphere(.5f), MotionType3D.DYNAMIC, ray.oX, ray.oY, ray.oZ, 1)
                .setLinearVelocity(new Vector3f(ray.dX, ray.dY, ray.dZ).mul(28));
    }

    /**
     * Returns whether the pointer is inside the scene viewport rather than an adjacent editor
     * panel.
     */
    private boolean inViewport() {
        return ui != null
                && Mouse.getX() >= 320
                && Mouse.getX() < 320 + vw
                && Mouse.getY() >= 60
                && Mouse.getY() < height - 80
                && ui.getCaptured() == null
                && ui.findNodeAt(Mouse.getX(), Mouse.getY(), UINode.CLICKABLE_BIT) == null;
    }

    /**
     * Claims a scene gesture only after UI routing, choosing light manipulation, body dragging,
     * orbit or pan.
     */
    private void beginPointer(float x, float y, int button, boolean shift) {
        if (gesture != 0 || !inViewport()) return;
        lastX = x;
        lastY = y;
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            gesture = 1;
            return;
        }
        if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            gesture = 2;
            return;
        }
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;
        camera.screenPointToRay(x, y, 320, 60, vw, vh, ray);
        if (rig.isPlacing()) {
            var hit = physics.raycast(ray, 150);
            if (hit != null)
                rig.add(hit.position().x, hit.position().y, Math.min(12, hit.position().z + .7f));
            else if (Math.abs(ray.dZ) > 1e-5f) {
                float t = (1 - ray.oZ) / ray.dZ;
                if (t > 0 && t < 150)
                    rig.add(
                            Math.clamp(ray.oX + t * ray.dX, -10, 10),
                            Math.clamp(ray.oY + t * ray.dY, -8, 8),
                            1);
            }
            return;
        }
        ModelInstance3D nearest = null;
        float distance = Float.POSITIVE_INFINITY;
        for (var light : rig.models()) {
            var p = light.getPosition();
            float t = (p.x - ray.oX) * ray.dX + (p.y - ray.oY) * ray.dY + (p.z - ray.oZ) * ray.dZ;
            if (t <= 0 || t >= distance) continue;
            float dx = ray.oX + t * ray.dX - p.x,
                    dy = ray.oY + t * ray.dY - p.y,
                    dz = ray.oZ + t * ray.dZ - p.z;
            float radius = Math.max(.25f, t * .014f);
            if (dx * dx + dy * dy + dz * dz < radius * radius) {
                nearest = light;
                distance = t;
            }
        }
        if (nearest != null) {
            rig.select(nearest);
            dragZ = nearest.getPosition().z;
            if (shift) {
                gesture = 4;
                return;
            }
            if (Math.abs(ray.dZ) > 1e-5f) {
                float t = (dragZ - ray.oZ) / ray.dZ;
                if (t > 0) {
                    dragOffsetX = nearest.getPosition().x - ray.oX - t * ray.dX;
                    dragOffsetY = nearest.getPosition().y - ray.oY - t * ray.dY;
                    gesture = 3;
                }
            }
            return;
        }
        var hit = physics.raycast(ray, 150);
        if (hit != null && hit.body().getMotionType() == MotionType3D.DYNAMIC)
            hit.body()
                    .addImpulse(
                            new Vector3f(ray.dX, ray.dY, ray.dZ).mul(18).add(0, 0, 4),
                            hit.position());
    }

    /** Updates the already claimed gesture from the latest window-coordinate pointer position. */
    private void movePointer(float x, float y) {
        if (gesture == 1) orbit.orbit(x - lastX, lastY - y);
        else if (gesture == 2) orbit.pan(x - lastX, lastY - y, vh);
        else if (gesture == 3 && rig.selected() != null) {
            camera.screenPointToRay(x, y, 320, 60, vw, vh, ray);
            if (Math.abs(ray.dZ) > 1e-5f) {
                float t = (dragZ - ray.oZ) / ray.dZ;
                if (t > 0 && t < 150)
                    rig.moveSelected(
                            Math.clamp(ray.oX + t * ray.dX + dragOffsetX, -12, 12),
                            Math.clamp(ray.oY + t * ray.dY + dragOffsetY, -10, 10),
                            dragZ);
            }
        } else if (gesture == 4 && rig.selected() != null) {
            var p = rig.selected().getPosition();
            rig.moveSelected(
                    p.x,
                    p.y,
                    Math.clamp(p.z + (y - lastY) * orbit.getDistance() * .8f / vh, .15f, 25));
        }
        lastX = x;
        lastY = y;
    }

    /** Returns the GLFW mouse button whose release ends the current scene gesture. */
    private int gestureButton() {
        return gesture == 0
                ? -1
                : gesture == 1
                        ? GLFW_MOUSE_BUTTON_RIGHT
                        : gesture == 2 ? GLFW_MOUSE_BUTTON_MIDDLE : GLFW_MOUSE_BUTTON_LEFT;
    }

    /** Maps a shortcut key into the edge-state bitset used to suppress repeat actions. */
    private static int shortcutBit(int key) {
        return key == GLFW_KEY_ESCAPE ? 1 : key == GLFW_KEY_SPACE ? 2 : key == GLFW_KEY_R ? 4 : 0;
    }

    /**
     * Recomputes viewport and UI layout from the current window dimensions without recreating scene
     * content.
     */
    private void resize() {
        width = Window.getWidth();
        height = Window.getHeight();
        vw = Math.max(1, width - 640);
        vh = Math.max(1, height - 140);
        header.getLayout().left(12).top(12).width(width - 24).height(56);
        controls.getLayout().left(12).top(80).width(296).height(height - 140);
        rig.panel.getLayout().left(width - 308).top(80).width(296).height(height - 140);
        footer.getLayout().left(12).top(height - 48).width(width - 24).height(38);
        ui.layout();
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
        if (gesture != 0 && !Mouse.isButtonDown(gestureButton())) gesture = 0;
        if (!Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) heldShortcuts &= ~1;
        if (!Keyboard.isKeyDown(GLFW_KEY_SPACE)) heldShortcuts &= ~2;
        if (!Keyboard.isKeyDown(GLFW_KEY_R)) heldShortcuts &= ~4;
        long start = System.nanoTime();
        if (!paused) {
            float dt = (benchmark || smoke ? 1f / 60 : Math.min(delta, .1f)) * speed;
            physics.update(dt);
            particles.update(dt, gravity);
        }
        physicsMs = (System.nanoTime() - start) * 1e-6f;
        if (benchmark) orbit.orbit(.15f, 0);
        if (smoke) {
            if (frames == 30) {
                spawn(true);
                spawn(false);
                shoot();
            }
            if (frames == 60) {
                physics.step();
                particles.update(physics.getFixedTimeStep(), gravity);
                orbit.zoom(1);
            }
        }
        reportClock += delta;
        reportFrames++;
        if (reportClock >= .35f) {
            stats.text(
                    String.format(
                            Locale.ROOT,
                            "%.0f FPS   |   Physics %.2f ms   |   Render %.2f ms   |   %d bodies  "
                                + " |   Right drag: orbit   Middle: pan   Wheel: zoom   Click: push"
                                + "   Space: shoot   |   "
                                    + (rig.isPlacing()
                                            ? "Click a surface to place a light"
                                            : "Click/drag a bulb to edit"),
                            reportFrames / reportClock,
                            physicsMs,
                            renderMs,
                            physics.getBodyCount()));
            simulation.text(
                    scenario == PARTICLES
                            ? particles.count()
                                    + " / 512 particles  |  "
                                    + (particles.physical ? "Jolt" : "Visual")
                            : "Step "
                                    + physics.getStepCount()
                                    + "  /  "
                                    + dynamic.size()
                                    + " dynamic bodies  |  contacts "
                                    + contactAdded
                                    + "/"
                                    + contactPersisted
                                    + "/"
                                    + contactRemoved);
            reportClock = 0;
            reportFrames = 0;
        }
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        long start = System.nanoTime();
        Window.clear(new Color(.035f, .048f, .07f, 1));
        orbit.apply(camera);
        glViewport(320, 60, vw, vh);
        renderer.render(scene, camera);
        glViewport(0, 0, width, height);
        var selectedLight = rig.selected();
        if (selectedLight != null) {
            camera.project(selectedLight.getPosition(), 320, 60, vw, vh, projectedLight);
            boolean visible =
                    projectedLight.z > 0
                            && projectedLight.z < 1
                            && projectedLight.x > 320
                            && projectedLight.x < 320 + vw
                            && projectedLight.y > 60
                            && projectedLight.y < 60 + vh;
            lightMarker.setVisible(visible);
            if (visible)
                lightMarker
                        .getLayout()
                        .left(projectedLight.x - 45)
                        .top(height - projectedLight.y - 30);
        } else lightMarker.setVisible(false);
        ui.draw();
        if (benchmark || smoke) glFinish();
        renderMs = (System.nanoTime() - start) * 1e-6f;
        frames++;
        if (smoke) smokeInput();
        if (benchmark && frames > 60 && frames <= 300) {
            renderSamples[frames - 61] = renderMs;
            physicsSamples[frames - 61] = physicsMs;
        }
        if ((smoke && frames == 150) || (benchmark && frames == 300)) {
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Physics studio GL error");
            for (var b : dynamic)
                if (!Float.isFinite(b.getPosition().z()))
                    throw new AssertionError("Nonfinite physics position");
            capture();
            if (benchmark) {
                report("render", renderSamples);
                report("physics", physicsSamples);
            }
            System.out.println(
                    "Physics studio validation passed; scenario="
                            + scenario
                            + ", bodies="
                            + physics.getBodyCount()
                            + ", steps="
                            + physics.getStepCount()
                            + ", cached meshes="
                            + renderer.getCachedMeshCount()
                            + ", lights="
                            + rig.size()
                            + ", particles="
                            + particles.count()
                            + ", particle physics="
                            + particles.physical);
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    private float smokeCameraX;
    private long smokeStep;
    private Vector3f smokeLightPosition;
    private ModelInstance3D smokeLight;
    private RigidBody3D smokeImportedBody;
    private float smokePointerX, smokePointerY;
    private int smokeLightCount, smokeBodyCount;
    private boolean smokeSceneSwitch;

    /**
     * Injects native pointer callbacks for deterministic smoke interaction, using window
     * coordinates and GLFW button/action constants.
     */
    private void pointer(float x, float y, int button, int action) {
        pointer(x, y, button, action, 0);
    }

    /**
     * Injects native pointer callbacks for deterministic smoke interaction, using window
     * coordinates and GLFW button/action constants.
     */
    private void pointer(float x, float y, int button, int action, int modifiers) {
        long window = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(window, null);
        glfwSetCursorPosCallback(window, cursor);
        cursor.invoke(window, x, height - y);
        if (button >= 0) {
            var callback = glfwSetMouseButtonCallback(window, null);
            glfwSetMouseButtonCallback(window, callback);
            callback.invoke(window, button, action, modifiers);
        }
    }

    /** Searches the UI subtree by visible button text; returns null when the target is absent. */
    private static NanoButton findButton(UINode node, String text) {
        if (node instanceof NanoButton button && button.getText().equals(text)) return button;
        if (node instanceof UIContainer container)
            for (var child : container.getChildren()) {
                var found = findButton(child, text);
                if (found != null) return found;
            }
        return null;
    }

    /**
     * Finds a visible button by label and exercises its native click path during smoke validation.
     */
    private void clickButton(String text) {
        var button = findButton(ui, text);
        if (button == null) throw new AssertionError("Missing button: " + text);
        float x = button.getAbsoluteX() + button.getWidth() / 2,
                y = height - button.getAbsoluteY() - button.getHeight() / 2;
        pointer(x, y, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        pointer(x, y, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
    }

    /**
     * Injects a native scroll event at the requested pointer location for viewport routing checks.
     */
    private void smokeScroll(float x, float y) {
        pointer(x, y, -1, 0);
        long window = Window.getAddress();
        var scroll = glfwSetScrollCallback(window, null);
        glfwSetScrollCallback(window, scroll);
        scroll.invoke(window, 0, 2);
    }

    /** Projects the selected light into window coordinates and moves the smoke pointer to it. */
    private void pointAtLight() {
        camera.project(smokeLight.getPosition(), 320, 60, vw, vh, projectedLight);
        smokePointerX = projectedLight.x;
        smokePointerY = projectedLight.y;
    }

    /**
     * Advances the scripted native-input sequence and asserts that the corresponding camera,
     * selection and UI state changes occur.
     */
    private void smokeInput() {
        if (frames == 5) {
            smokeCameraX = camera.getPosition().x();
            pointer(700, 450, GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        }
        if (frames == 6) pointer(760, 470, -1, 0);
        if (frames == 7) {
            pointer(760, 470, GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            if (Math.abs(camera.getPosition().x() - smokeCameraX) < .1f)
                throw new AssertionError("Mouse orbit failed");
        }
        if (frames == 9) pointer(100, height - 172, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        if (frames == 10) pointer(100, height - 172, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        if (frames == 11) {
            if (!paused) throw new AssertionError("Pause button failed");
            smokeStep = physics.getStepCount();
        }
        if (frames == 13) {
            if (physics.getStepCount() != smokeStep)
                throw new AssertionError("Pause continued stepping");
            pointer(100, height - 212, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (frames == 14) pointer(100, height - 212, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        if (frames == 15) {
            if (physics.getStepCount() != smokeStep + 1)
                throw new AssertionError("Single step failed");
            pointer(100, height - 172, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        }
        if (frames == 16) pointer(100, height - 172, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        if (frames == 17) {
            if (paused) throw new AssertionError("Resume failed");
            pointer(700, 450, -1, 0);
            float distance = orbit.getDistance();
            long window = Window.getAddress();
            var scroll = glfwSetScrollCallback(window, null);
            glfwSetScrollCallback(window, scroll);
            scroll.invoke(window, 0, 2);
            if (orbit.getDistance() >= distance) throw new AssertionError("Wheel zoom failed");
            resetCamera();
        }
        if (frames == 19) {
            smokeCameraX = camera.getPosition().x;
            pointer(100, height - 172, GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        }
        if (frames == 20) pointer(750, 480, -1, 0);
        if (frames == 21) {
            pointer(750, 480, GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            if (camera.getPosition().x != smokeCameraX)
                throw new AssertionError("UI press leaked into camera orbit");
            float distance = orbit.getDistance();
            smokeScroll(100, height - 172);
            if (orbit.getDistance() != distance) throw new AssertionError("UI scroll zoomed scene");
        }
        if (frames == 24) {
            smokeLight = rig.models().getFirst();
            smokeLightPosition = new Vector3f(smokeLight.getPosition());
            pointAtLight();
            pointer(smokePointerX, smokePointerY, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
            if (rig.selected() != smokeLight || gesture != 3)
                throw new AssertionError("Bulb picking failed");
            float distance = orbit.getDistance();
            smokeScroll(smokePointerX, smokePointerY);
            if (orbit.getDistance() != distance)
                throw new AssertionError("Scroll interfered with light drag");
        }
        if (frames == 25) {
            pointer(smokePointerX + 40, smokePointerY - 10, GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
            pointer(smokePointerX + 40, smokePointerY - 10, GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            if (gesture != 3)
                throw new AssertionError("Second mouse button interrupted light drag");
        }
        if (frames == 26) {
            pointer(smokePointerX + 40, smokePointerY - 10, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            if (smokeLight.getPosition().distance(smokeLightPosition) < .1f
                    || smokeLight.getPosition().z != smokeLightPosition.z)
                throw new AssertionError("Horizontal light drag failed");
            if (camera.getPosition().x != smokeCameraX)
                throw new AssertionError("Light drag moved camera");
        }
        if (frames == 27) {
            pointAtLight();
            pointer(
                    smokePointerX,
                    smokePointerY,
                    GLFW_MOUSE_BUTTON_LEFT,
                    GLFW_PRESS,
                    GLFW_MOD_SHIFT);
        }
        if (frames == 28) pointer(smokePointerX, smokePointerY + 50, -1, 0);
        if (frames == 29) {
            pointer(smokePointerX, smokePointerY + 50, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            if (smokeLight.getPosition().z <= smokeLightPosition.z + .1f)
                throw new AssertionError("Shift height drag failed");
            rig.moveSelected(smokeLightPosition.x, smokeLightPosition.y, smokeLightPosition.z);
        }
        if (frames == 32) {
            // Capacity behavior has a separate headless test; leave room for mouse placement.
            while (rig.size() > 3) {
                rig.select(rig.models().getLast());
                rig.deleteSelected();
            }
            smokeLightCount = rig.size();
            smokeBodyCount = physics.getBodyCount() - (particles.physical ? particles.count() : 0);
            clickButton("+ Place light");
            if (!rig.isPlacing()) throw new AssertionError("Place light button failed");
        }
        if (frames == 34) {
            camera.project(new Vector3f(0, -6, 0), 320, 60, vw, vh, projectedLight);
            pointer(projectedLight.x, projectedLight.y, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
            pointer(projectedLight.x, projectedLight.y, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            if (rig.size() != smokeLightCount + 1 || rig.isPlacing())
                throw new AssertionError("Surface light placement failed");
            if (physics.getBodyCount() - (particles.physical ? particles.count() : 0)
                    != smokeBodyCount) throw new AssertionError("Light placement spawned a body");
        }
        if (frames == 36) {
            clickButton("Switch off");
            if (rig.selected().getMaterial().getEmissionStrength() != 0)
                throw new AssertionError("Light toggle failed");
        }
        if (frames == 38) {
            clickButton("Switch on");
            rig.setSelectedColor(.2f, .6f, 1);
            rig.setSelectedPower(100);
            if (rig.selected().getMaterial().getEmissionStrength() != 100)
                throw new AssertionError("Light power edit failed");
            clickButton("Duplicate");
            if (rig.size() != smokeLightCount + 2)
                throw new AssertionError("Light duplication failed");
        }
        if (frames == 40) clickButton("Delete");
        if (frames == 42) {
            clickButton("Delete");
            if (rig.size() != smokeLightCount) throw new AssertionError("Light deletion failed");
            rig.select(smokeLight);
        }
        if (frames == 45 && scenario != PARTICLES) {
            smokeBodyCount = physics.getBodyCount();
            clickButton("Drop selected model");
            if (physics.getBodyCount() != smokeBodyCount + 1)
                throw new AssertionError("Imported model drop button failed");
            smokeImportedBody = dynamic.getLast();
        }
        if (frames == 47) {
            clickButton("+ Place light");
            var keys = glfwSetKeyCallback(Window.getAddress(), null);
            glfwSetKeyCallback(Window.getAddress(), keys);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_REPEAT, 0);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0);
            if (rig.isPlacing() || glfwWindowShouldClose(Window.getAddress()))
                throw new AssertionError("Repeated Escape did not safely cancel placement");
        }
        if (frames == 49 && scenario != PARTICLES) {
            modelList.open();
            var keys = glfwSetKeyCallback(Window.getAddress(), null);
            glfwSetKeyCallback(Window.getAddress(), keys);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_REPEAT, 0);
            keys.invoke(Window.getAddress(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0);
            if (modelList.isOpen() || glfwWindowShouldClose(Window.getAddress()))
                throw new AssertionError("Repeated Escape leaked from dropdown to scene");
        }
        if (frames == 65 && scenario == GALLERY) {
            float distance = orbit.getDistance();
            clickButton("Focus selected exhibit");
            if (orbit.getDistance() >= distance || orbit.getTarget().x != -5)
                throw new AssertionError("Exhibit focus button failed");
        }
        if (frames == 74 && scenario == GALLERY) {
            capture("focused-vase.png");
            modelList.selectedIndex(1);
            clickButton("Focus selected exhibit");
        }
        if (frames == 84 && scenario == GALLERY) {
            capture("focused-crate.png");
            modelList.selectedIndex(2);
            clickButton("Focus selected exhibit");
        }
        if (frames == 94 && scenario == GALLERY) {
            capture("focused-bust.png");
            modelList.selectedIndex(0);
            resetCamera();
        }
        if (scenario == PARTICLES) smokeParticles();
        if (frames == 145 && scenario != PARTICLES) {
            float z = smokeImportedBody.getPosition().z;
            if (!Float.isFinite(z) || z < -.1f || z > 5.5f)
                throw new AssertionError("Imported model did not fall and collide: " + z);
            System.out.println(
                    "Editor input validation passed: UI isolation, bulb picking, XY/height drag,"
                            + " placement, toggle, duplicate/delete, imported model drop.");
        }
        if (frames == 146 && scenario == GALLERY) {
            paused = true;
            pause.text("Resume simulation");
            var previousWorld = physics;
            clickButton("Design gallery");
            clickButton("Particle fountain / Jolt");
            if (scenario != PARTICLES
                    || !previousWorld.isClosed()
                    || particles.count() != 24
                    || particleControls.getParent() != spawnDeck
                    || standardSpawn.getParent() != null)
                throw new AssertionError("Gallery to particle scene transition failed");
            smokeSceneSwitch = true;
        }
        if (frames == 147 && smokeSceneSwitch) {
            int bodies = physics.getBodyCount();
            clickButton("Burst 48");
            if (particles.count() != 72
                    || physics.getBodyCount() != bodies + (particles.physical ? 48 : 0))
                throw new AssertionError("Particle controls after scene switch failed");
        }
        if (frames == 148 && smokeSceneSwitch) {
            var previousWorld = physics;
            var previousEmitter = particles.emitter();
            var previousMeshes =
                    previousEmitter.getParticles().stream().map(p -> p.getModelInstance()).toList();
            clickButton("Particle fountain / Jolt");
            clickButton("Design gallery");
            if (scenario != GALLERY
                    || !previousWorld.isClosed()
                    || !previousEmitter.isClosed()
                    || particles.emitter() != null
                    || particles.count() != 0
                    || standardSpawn.getParent() != spawnDeck
                    || particleControls.getParent() != null)
                throw new AssertionError("Particle to gallery scene transition leaked state");
            for (var mesh : previousMeshes)
                if (scene.getRenderables().contains(mesh))
                    throw new AssertionError("Scene transition retained particle mesh");
            for (var light : rig.models())
                if (Collections.frequency(scene.getRenderables(), light) != 1)
                    throw new AssertionError("Scene transition lost or duplicated a light");
        }
        if (frames == 149 && smokeSceneSwitch) {
            int bodies = physics.getBodyCount();
            clickButton("Drop selected model");
            if (physics.getBodyCount() != bodies + 1)
                throw new AssertionError("Restored model controls failed");
            System.out.println(
                    "Scene switching validation passed: gallery to particles and back,"
                            + " world/body/mesh cleanup and restored controls.");
        }
    }

    /**
     * Checks bounded emission, collision/gravity mode changes and ownership of the fountain's
     * particle bodies.
     */
    private void smokeParticles() {
        if (frames == 51) {
            clickButton("Stop emission");
            if (particles.emitting) throw new AssertionError("Particle emission toggle failed");
            clickButton("Clear particles");
            if (particles.count() != 0) throw new AssertionError("Particle clear failed");
            smokeBodyCount = physics.getBodyCount();
            clickButton("Burst 48");
            if (particles.count() != 48
                    || physics.getBodyCount() != smokeBodyCount + (particles.physical ? 48 : 0))
                throw new AssertionError("Particle burst/body ownership failed");
        }
        if (frames == 55) {
            if (particles.physical) clickButton("Physics: Jolt collisions");
            if (particles.physical || physics.getBodyCount() != smokeBodyCount)
                throw new AssertionError("Disabling particle physics leaked bodies");
            clickButton("Clear particles");
            clickButton("Burst 48");
        }
        if (frames == 57) {
            if (particles.count() != 48 || physics.getBodyCount() != smokeBodyCount)
                throw new AssertionError("Visual particles created physics bodies");
            clickButton("Physics: off / visual only");
            if (!particles.physical) throw new AssertionError("Enabling particle physics failed");
            clickButton("Start emission");
        }
        if (frames == 145) {
            int count = particles.count();
            if (count <= 0
                    || count > PhysicsStudioParticles.CAPACITY
                    || physics.getBodyCount() != smokeBodyCount + count)
                throw new AssertionError("Particle/body count mismatch");
            for (var particle : particles.emitter().getParticles()) {
                if (!particle.getPosition().isFinite()
                        || particle.getPosition().z < -.2f
                        || particle.getBody() == null)
                    throw new AssertionError("Particle escaped floor or lost its body");
                if (particle.getPosition().distance(particle.getModelInstance().getPosition())
                        > 1e-4f) throw new AssertionError("Particle render pose drift");
            }
            System.out.println(
                    "Particle input validation passed: emission, clear, burst, physics off/on, mesh"
                            + " pose and floor collision.");
        }
    }

    /**
     * Prints mean, median and 95th-percentile timings in milliseconds; sorting mutates the supplied
     * measurement array.
     */
    private void report(String name, double[] samples) {
        Arrays.sort(samples);
        System.out.printf(
                Locale.ROOT,
                "%s: mean %.3f ms / median %.3f ms / p95 %.3f ms; %s%n",
                name,
                Arrays.stream(samples).average().orElse(0),
                samples[120],
                samples[228],
                glGetString(GL_RENDERER));
    }

    /**
     * Captures the current demo frame or state for its documented workflow; capture-specific
     * overloads choose the output name.
     */
    private void capture() {
        capture("scenario-" + scenario + ".png");
    }

    /**
     * Captures the current demo frame or state for its documented workflow; capture-specific
     * overloads choose the output name.
     */
    private void capture(String filename) {
        FrameCapture.save(Path.of("build/physics-studio", filename));
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        Mouse.removeScrollListener(wheel);
        Mouse.removeMouseListener(sceneMouse);
        Keyboard.removeKeyListener(sceneKeys);
        Keyboard.removeKeyListener(shortcutEdges);
        particles.close();
        physics.close();
        renderer.close();
        gallery.close();
        ui.dispose();
        theme.close();
    }
}
