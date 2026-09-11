// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.scene;

import static org.lwjgl.glfw.GLFW.*;

import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.lighting3d.Lighting3D;
import valthorne.graphics.model.*;
import valthorne.graphics.particle.ParticleEmitter3D;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;
import valthorne.viewport.PerspectiveViewport;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * A seeded raster scene combining hierarchical transforms, mesh picking, shadow mapping, point
 * lights, procedural textures, particles and a 2D overlay.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>The application owns batches, shadow maps, lighting, textures and the particle emitter. Scene
 * instances borrow geometry/material data. Input and all rendering stay on the application thread;
 * positions use the engine's Z-up world convention.
 *
 * <p>Run through {@link valthorne.examples.launcher.ExampleLauncher} for help, validated options
 * and platform checks. Study the accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/scene.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class Scene3DExample implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final MeshRenderState3D state = new MeshRenderState3D().setCamera(camera);
    private final Random random = new Random(7);
    private ModelBatch3D batch;
    private ShadowMap3D shadows;
    private PerspectiveViewport viewport;
    private Texture checker, spark, heading, instructions;
    private TextureBatch overlay;
    private PerformanceOverlay performance;
    private Lighting3D lighting;
    private boolean vsync = true, vDown;
    private ParticleEmitter3D particles;
    private ModelInstance3D rotating, selected;
    private SceneNode3D satellite;
    private float time, azimuth = -.95f, distance = 13f;
    private boolean mouseDown;
    private int frames;
    private Path snapshot;

    /**
     * Starts this application on the process main thread. Use the shared ExampleLauncher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        Scene3DExample example = new Scene3DExample();
        for (String arg : args)
            if (arg.startsWith("--snapshot=")) example.snapshot = Path.of(arg.substring(11));
        JGL.init(
                example,
                JGLConfiguration.defaults()
                        .title("Valthorne 3D | arrows orbit | W/S zoom | click selects")
                        .size(1100, 760)
                        .depthBits(24)
                        .visible(example.snapshot == null));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        batch = new ModelBatch3D();
        overlay = new TextureBatch(128);
        shadows = new ShadowMap3D(1024);
        performance = new PerformanceOverlay();
        lighting = new Lighting3D();
        state.setLighting(lighting);
        viewport = new PerspectiveViewport(Window.getWidth(), Window.getHeight(), camera);
        camera.setClipPlanes(.1f, 100);
        checker = checker();
        spark = spark();
        heading =
                label("VALTHORNE / 3D", "Textured geometry. Real lighting. One scene.", 1040, 110);
        instructions =
                label(
                        "ARROWS  orbit     W / S  zoom     CLICK  select     V  sync",
                        "Meshes + soft shadows + particles + a 2D overlay",
                        1040,
                        82);
        state.setAmbientLight(new Color(.24f, .27f, .34f, 1))
                .setDirectionalLight(new Color(3.8f, 3.45f, 2.9f, 1))
                .setLightDirection(-6, -4, 9)
                .setFog(15, 40, .65f)
                .setFogColor(new Color(.035f, .05f, .09f, 1))
                .setShadowMap(shadows);
        lighting.addLight(
                new PointLight3D()
                        .setPosition(-3, 0, 3)
                        .setColor(new Color(.1f, .65f, 1, 1))
                        .setRange(8)
                        .setIntensity(12));
        shadows.getCamera().setPosition(-6, -4, 9);
        shadows.getCamera().lookAt(0, 0, 0, 0, 0, 1);
        shadows.getCamera().setWorldHeight(20);
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(16, 16))
                        .setMaterial(new Material3D().setTexture(checker)));
        rotating =
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.box(2, 2, 2))
                        .setPosition(0, 0, 1.6f)
                        .setMaterial(
                                new Material3D()
                                        .setTint(new Color(.12f, .75f, .8f, 1))
                                        .setCullBackFaces(true));
        scene.add(rotating);
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.sphere(1.1f, 40, 20))
                        .setPosition(-3, 0, 1.1f)
                        .setMaterial(
                                new Material3D()
                                        .setTint(new Color(.98f, .45f, .18f, 1))
                                        .setRoughness(.22f)
                                        .setMetallic(.5f)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.cylinder(.9f, 2.7f, 32))
                        .setPosition(3, .5f, 1.35f)
                        .setMaterial(new Material3D().setTint(new Color(.52f, .36f, .88f, 1))));
        satellite = new SceneNode3D().setPosition(0, 0, 1.6f);
        satellite.addChild(
                new SceneNode3D()
                        .setModel(ModelBuilder3D.box(.45f, .45f, .45f))
                        .setPosition(2.1f, 0, 1.6f)
                        .setRotation(.4f, .2f, .1f)
                        .setMaterial(new Material3D().setTint(new Color(1, .8f, .25f, 1))));
        scene.addNode(satellite);
        TextureRegion sparkRegion = new TextureRegion(spark);
        Material3D glow = new Material3D().setRenderPass(RenderPass3D.ADDITIVE).setFogMix(0);
        particles =
                new ParticleEmitter3D(
                                120,
                                p -> {
                                    p.setLifetime(2);
                                    p.getPosition().set(-3, 0, 2.3f);
                                    p.getVelocity()
                                            .set(
                                                    (random.nextFloat() - .5f) * 1.6f,
                                                    (random.nextFloat() - .5f) * 1.6f,
                                                    1 + random.nextFloat());
                                    p.getAcceleration().set(0, 0, -.5f);
                                    p.getSprite()
                                            .setTextureRegion(sparkRegion)
                                            .setMaterial(glow)
                                            .setSize(.12f, .12f)
                                            .setColor(new Color(.2f, .75f, 1, 1));
                                })
                        .setEmissionRate(30);
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
        if (snapshot != null) delta = 1f / 60;
        time += delta;
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE))
            glfwSetWindowShouldClose(Window.getAddress(), true);
        if (Keyboard.isKeyDown(GLFW_KEY_LEFT)) azimuth -= delta;
        if (Keyboard.isKeyDown(GLFW_KEY_RIGHT)) azimuth += delta;
        if (Keyboard.isKeyDown(GLFW_KEY_W)) distance = Math.max(7, distance - 5 * delta);
        if (Keyboard.isKeyDown(GLFW_KEY_S)) distance = Math.min(25, distance + 5 * delta);
        camera.setPosition(
                (float) Math.cos(azimuth) * distance,
                (float) Math.sin(azimuth) * distance,
                distance * .64f);
        camera.lookAt(0, 0, 1, 0, 0, 1);
        rotating.setRotation(time * .3f, time * .2f, time * .45f);
        satellite.setYawRadians(-time * .6f);
        particles.update(delta);
        boolean down = Mouse.isButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseDown) {
            camera.rebuild(viewport.getWidth(), viewport.getHeight());
            var ray = viewport.screenToRay(Mouse.getX(), Mouse.getY());
            PickResult3D hit = ray == null ? null : scene.pick(ray);
            if (selected != null) selected.getMaterial().setEmissive(0, 0, 0, 0);
            selected = hit == null ? null : hit.instance();
            if (selected != null) selected.getMaterial().setEmissive(.25f, .35f, .4f, .6f);
        }
        mouseDown = down;
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
        if (viewport.getWidth() != width || viewport.getHeight() != height)
            viewport.update(width, height);
        Window.clear3D(new Color(.035f, .05f, .09f, 1));
        shadows.render(scene);
        viewport.renderWithOverlay(
                () -> {
                    batch.begin(state);
                    try {
                        scene.submit(batch);
                        particles.submit(batch);
                        batch.end();
                    } finally {
                        batch.cancel();
                    }
                },
                () -> {
                    overlay.begin();
                    overlay.draw(heading.sprite(), 30, height - 135);
                    overlay.draw(instructions.sprite(), 30, 25);
                    performance.draw(overlay, 60, height - 172);
                    performance.drawText(
                            overlay, vsync ? "VSYNC ON" : "VSYNC OFF", 460, height - 172);
                    overlay.end();
                });
        if (snapshot != null && ++frames == 90) {
            saveSnapshot();
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        particles.close();
        shadows.dispose();
        lighting.close();
        performance.close();
        batch.dispose();
        overlay.dispose();
        checker.dispose();
        spark.dispose();
        heading.dispose();
        instructions.dispose();
    }

    /** Creates the owned procedural checker texture used by the scene's surfaces. */
    private Texture checker() {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++)
                image.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xff475466 : 0xff303b4a);
        return texture(image);
    }

    /** Creates the owned soft particle sprite texture used by the retained emitter. */
    private Texture spark() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float r = (float) Math.hypot(x - 15.5, y - 15.5) / 16;
                int alpha = (int) (Math.pow(Math.max(0, 1 - r), 2) * 255);
                image.setRGB(x, y, (alpha << 24) | 0xffffff);
            }
        return texture(image);
    }

    /**
     * Rasterizes a title/subtitle card at the requested pixel size and returns its owned GPU
     * texture.
     */
    private Texture label(String title, String subtitle, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(9, 16, 28, 225));
        g.fillRoundRect(0, 0, width, height, 22, 22);
        g.setColor(new java.awt.Color(102, 226, 219));
        g.fillRect(22, 20, 4, height - 40);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, height > 100 ? 30 : 18));
        g.setColor(java.awt.Color.WHITE);
        g.drawString(title, 44, height > 100 ? 45 : 32);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16));
        g.setColor(new java.awt.Color(167, 184, 206));
        g.drawString(subtitle, 44, height > 100 ? 77 : 58);
        g.dispose();
        return texture(image);
    }

    /**
     * Encodes a generated image and creates an owned engine texture on the active graphics thread.
     */
    private Texture texture(BufferedImage image) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return new Texture(bytes.toByteArray());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Writes the requested PNG from the native framebuffer before buffer swap; physical pixel
     * dimensions are determined by the shared capture helper.
     */
    private void saveSnapshot() {
        FrameCapture.save(snapshot);
    }
}
