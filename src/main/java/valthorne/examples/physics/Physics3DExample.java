// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.physics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;

import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.lighting3d.Lighting3D;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.*;
import valthorne.math.physics.*;
import valthorne.viewport.PerspectiveViewport;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * A small rigid-body playground demonstrating fixed-step Jolt simulation, render bindings, static
 * and dynamic shapes, continuous collision and camera interaction.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>The application owns its physics world and closes it before rebuilding the playground. Body
 * transforms are synchronized by physics; do not independently animate a bound model. Distances use
 * world units and time is measured in seconds.
 *
 * <p>Run through the documented Gradle task for validated options and platform checks. Study the
 * accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/physics.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class Physics3DExample implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final MeshRenderState3D state = new MeshRenderState3D().setCamera(camera);
    private final Random random = new Random(42);
    private PhysicsWorld3D physics;
    private ModelBatch3D batch;
    private ShadowMap3D shadows;
    private PerspectiveViewport viewport;
    private TextureBatch overlay;
    private Texture heading, footer;
    private Model3D cube, sphere;
    private RigidBody3D platform;
    private float elapsed, orbit = -1.05f;
    private boolean spaceDown, mouseDown, resetDown;
    private int frames;
    private Path snapshot;
    private PerformanceOverlay performance;
    private Lighting3D lighting;
    private boolean allLights, stress;
    private boolean vsync = true, vDown;
    private boolean benchmark;
    private long renderStart;
    private final double[] renderSamples = new double[300];

    /**
     * Starts this application on the process main thread. Use the shared ExampleLauncher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        Physics3DExample app = new Physics3DExample();
        for (String arg : args)
            if (arg.startsWith("--snapshot=")) app.snapshot = Path.of(arg.substring(11));
        for (String arg : args) if (arg.equals("--benchmark")) app.benchmark = true;
        for (String arg : args)
            if (arg.equals("--all-lights")) app.allLights = true;
            else if (arg.equals("--stress-lights")) app.stress = true;
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .title(
                                "Valthorne + Jolt | Space drops | Click pushes | Arrows orbit | R"
                                        + " resets")
                        .size(1100, 760)
                        .depthBits(24)
                        .visible(app.snapshot == null));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        batch = new ModelBatch3D();
        shadows = new ShadowMap3D(1024);
        overlay = new TextureBatch(128);
        performance = new PerformanceOverlay();
        if (benchmark) {
            glfwSwapInterval(0);
            vsync = false;
        }
        lighting = new Lighting3D().setTiledCullingEnabled(!allLights);
        state.setLighting(lighting).setDirectionalLight(new Color(3.8f, 3.45f, 2.9f, 1));
        viewport = new PerspectiveViewport(Window.getWidth(), Window.getHeight(), camera);
        camera.setClipPlanes(.1f, 100);
        state.setAmbientLight(new Color(.24f, .27f, .32f, 1))
                .setLightDirection(-6, -4, 9)
                .setFog(25, 70, .8f)
                .setFogColor(new Color(.035f, .05f, .09f, 1))
                .setShadowMap(shadows);
        shadows.getCamera().setPosition(-6, -4, 9);
        shadows.getCamera().lookAt(0, 0, 0, 0, 0, 1);
        shadows.getCamera().setWorldHeight(24);
        shadows.setSoftness(2.5f).setBias(.0006f);
        lighting.addLight(
                new PointLight3D()
                        .setPosition(-3, -1, 4)
                        .setColor(new Color(.32f, .55f, 1, 1))
                        .setRange(9)
                        .setIntensity(22));
        lighting.addLight(
                new PointLight3D()
                        .setPosition(4, 2, 5)
                        .setColor(new Color(1, .35f, .12f, 1))
                        .setRange(9)
                        .setIntensity(28));
        if (stress)
            for (int i = 0; i < 256; i++) {
                float x = (i % 16 - 7.5f) * 1.6f, y = (i / 16 - 7.5f) * 1.6f;
                lighting.addLight(
                        new PointLight3D()
                                .setPosition(x, y, .7f)
                                .setRange(2)
                                .setIntensity(.3f)
                                .setColor(
                                        new Color(
                                                .4f + (i % 3) * .2f, .3f + (i % 5) * .1f, .6f, 1)));
            }
        heading =
                label(
                        "VALTHORNE + JOLT",
                        "Tiled lighting / soft shadows / roughness and metallic materials",
                        true);
        footer =
                label(
                        "SPACE  drop ball     CLICK  push     ARROWS  orbit     R  reset     V "
                                + " sync",
                        "The turquoise platform is kinematic. V toggles the frame-rate cap.",
                        false);
        cube = ModelBuilder3D.box(1, 1, 1);
        sphere = ModelBuilder3D.sphere(.5f, 24, 12);
        reset();
    }

    /**
     * Rebuilds the seeded rigid-body playground, removing the previous world and its body bindings
     * first.
     */
    private void reset() {
        if (physics != null) physics.close();
        scene.clear();
        random.setSeed(42);
        elapsed = 0;
        physics = new PhysicsWorld3D();
        add(
                CollisionShape3D.box(18, 18, 1),
                ModelBuilder3D.box(18, 18, 1),
                MotionType3D.STATIC,
                new Vector3f(0, 0, -.5f),
                new Color(.42f, .46f, .53f, 1));
        for (int level = 0; level < 5; level++)
            for (int column = 0; column < 5 - level; column++) {
                add(
                        CollisionShape3D.box(1, 1, 1),
                        cube,
                        MotionType3D.DYNAMIC,
                        new Vector3f(column - (4 - level) * .5f, -.6f, .52f + level * 1.02f),
                        new Color(.55f + .04f * level, .46f + .04f * column, .8f, 1));
            }
        for (int i = 0; i < 4; i++)
            add(
                    CollisionShape3D.sphere(.5f),
                    sphere,
                    MotionType3D.DYNAMIC,
                    new Vector3f(-4 + i * .8f, 2, 2 + i * .7f),
                    new Color(1, .48f, .16f, 1));
        platform =
                add(
                        CollisionShape3D.box(3, 2, .35f),
                        ModelBuilder3D.box(3, 2, .35f),
                        MotionType3D.KINEMATIC,
                        new Vector3f(3, 2, 1),
                        new Color(.12f, .7f, .75f, 1));
        add(
                CollisionShape3D.box(1, 1, 1),
                cube,
                MotionType3D.DYNAMIC,
                new Vector3f(3, 2, 2.5f),
                new Color(1, .75f, .2f, 1));
        physics.addBeforeStepListener(
                world -> {
                    elapsed += world.getFixedTimeStep();
                    platform.moveKinematic(
                            new Vector3f(3, 2, 1 + .5f * (float) Math.sin(elapsed)),
                            new Quaternionf());
                });
        physics.optimizeBroadPhase();
    }

    /**
     * Creates and binds one owned rigid body to a scene instance using the supplied shape, mesh,
     * world position and tint.
     */
    private RigidBody3D add(
            CollisionShape3D shape,
            Model3D geometry,
            MotionType3D motion,
            Vector3f position,
            Color color) {
        ModelInstance3D model =
                new ModelInstance3D()
                        .setModel(geometry)
                        .setMaterial(
                                new Material3D()
                                        .setTint(color)
                                        .setRoughness(
                                                geometry == sphere
                                                        ? .23f
                                                        : motion == MotionType3D.STATIC
                                                                ? .85f
                                                                : .38f)
                                        .setMetallic(geometry == sphere ? .55f : .08f));
        RigidBody3D body =
                physics.createBody(
                                new BodySettings3D(shape, motion)
                                        .setPosition(position)
                                        .setRestitution(motion == MotionType3D.DYNAMIC ? .2f : 0)
                                        .setFriction(.65f))
                        .bind(model);
        scene.add(model);
        return body;
    }

    /**
     * Processes input and advances this demo using elapsed seconds; rendering and resource
     * destruction remain in their lifecycle callbacks.
     *
     * @param delta elapsed time in seconds
     */
    @Override
    public void update(float delta) {
        boolean v = Keyboard.isKeyDown(GLFW_KEY_V);
        if (v && !vDown) {
            vsync = !vsync;
            glfwSwapInterval(vsync ? 1 : 0);
        }
        vDown = v;
        if (snapshot != null || benchmark) delta = 1f / 60;
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE))
            glfwSetWindowShouldClose(Window.getAddress(), true);
        if (Keyboard.isKeyDown(GLFW_KEY_LEFT)) orbit -= delta;
        if (Keyboard.isKeyDown(GLFW_KEY_RIGHT)) orbit += delta;
        camera.setPosition(16 * (float) Math.cos(orbit), 16 * (float) Math.sin(orbit), 10);
        camera.lookAt(0, 0, 1.3f, 0, 0, 1);
        camera.rebuild(viewport.getWidth(), viewport.getHeight());
        boolean reset = Keyboard.isKeyDown(GLFW_KEY_R);
        if (reset && !resetDown) reset();
        resetDown = reset;
        boolean space = Keyboard.isKeyDown(GLFW_KEY_SPACE);
        if (space && !spaceDown && physics.getBodyCount() < 200)
            add(
                    CollisionShape3D.sphere(.5f),
                    sphere,
                    MotionType3D.DYNAMIC,
                    new Vector3f(random.nextFloat() * 4 - 2, -.5f, 8),
                    new Color(1, .45f, .12f, 1));
        spaceDown = space;
        boolean mouse = Mouse.isButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (mouse && !mouseDown) {
            Rayf ray = viewport.screenToRay(Mouse.getX(), Mouse.getY());
            PhysicsRayHit3D hit = ray == null ? null : physics.raycast(ray, 100);
            if (hit != null && hit.body().getMotionType() == MotionType3D.DYNAMIC)
                hit.body()
                        .addImpulse(
                                new Vector3f(ray.dX, ray.dY, ray.dZ).mul(7).add(0, 0, 3),
                                hit.position());
        }
        mouseDown = mouse;
        physics.update(delta);
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        int width = Window.getWidth(), height = Window.getHeight();
        if (width <= 0 || height <= 0) return;
        performance.frame();
        renderStart = System.nanoTime();
        if (viewport.getWidth() != width || viewport.getHeight() != height)
            viewport.update(width, height);
        Window.clear3D(new Color(.035f, .05f, .09f, 1));
        shadows.render(scene);
        viewport.renderWithOverlay(
                () -> scene.render(batch, state),
                () -> {
                    overlay.begin();
                    overlay.draw(heading.sprite(), 30, height - 135);
                    overlay.draw(footer.sprite(), 30, 25);
                    performance.draw(overlay, 60, height - 172);
                    performance.drawText(
                            overlay, vsync ? "VSYNC ON" : "VSYNC OFF", 460, height - 172);
                    overlay.end();
                });
        frames++;
        if (benchmark) {
            glFinish();
            if (frames > 120 && frames <= 420)
                renderSamples[frames - 121] = (System.nanoTime() - renderStart) * 1e-6;
            if (frames == 420) {
                java.util.Arrays.sort(renderSamples);
                System.out.printf(
                        java.util.Locale.ROOT,
                        "RENDER BENCHMARK: mean %.3f ms, median %.3f ms, p95 %.3f ms; %s%n",
                        java.util.Arrays.stream(renderSamples).average().orElse(0),
                        renderSamples[150],
                        renderSamples[285],
                        glGetString(GL_RENDERER));
                glfwSetWindowShouldClose(Window.getAddress(), true);
            }
        }
        if (snapshot != null && frames == 150) {
            save();
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        physics.close();
        shadows.dispose();
        lighting.close();
        batch.dispose();
        overlay.dispose();
        heading.dispose();
        footer.dispose();
        performance.close();
    }

    /** Builds an owned texture containing the playground title and control legend. */
    private Texture label(String title, String subtitle, boolean large) {
        BufferedImage image =
                new BufferedImage(1040, large ? 110 : 82, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(9, 16, 28, 225));
        g.fillRoundRect(0, 0, image.getWidth(), image.getHeight(), 22, 22);
        g.setColor(new java.awt.Color(102, 226, 219));
        g.fillRect(22, 20, 4, image.getHeight() - 40);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, large ? 30 : 18));
        g.setColor(java.awt.Color.WHITE);
        g.drawString(title, 44, large ? 45 : 32);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16));
        g.setColor(new java.awt.Color(167, 184, 206));
        g.drawString(subtitle, 44, large ? 77 : 58);
        g.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return new Texture(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a PNG of the settled simulation using the native framebuffer dimensions. */
    private void save() {
        FrameCapture.save(snapshot);
    }
}
