// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lightingstudio;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

import valthorne.*;
import valthorne.camera.PerspectiveCamera;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.TextureBatch;
import valthorne.viewport.ScreenViewport;

import java.nio.file.*;

/**
 * A focused progressive-lighting demo with diffuse, metallic and transmissive materials, emissive
 * area lights, and a switchable 2D view. Launching this class always opens this demo.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>Both tracers and overlays belong to the application and are released in dispose.
 * Camera/material changes invalidate progressive accumulation. Snapshot mode waits for 512
 * accumulated samples; benchmark mode measures a separate bounded workload. OpenGL 4.3 is required.
 *
 * <p>Run through the documented Gradle task for validated options and platform checks. Study the
 * accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/path-tracing.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class PathTracingExample implements Application {
    private final Scene3D scene = new Scene3D();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private PathTracer3D tracer;
    private TextureBatch overlay;
    private PerformanceOverlay text;
    private ScreenViewport viewport;
    private valthorne.graphics.lighting2d.PathTracer2D flat;
    private boolean flatView, tabDown, dDown;
    private boolean benchmark;
    private PathTracer3D.Quality requestedQuality;
    private final double[] timings = new double[120];
    private float angle = -1.45f;
    private boolean vDown, vsync = true;
    private Path snapshot;
    private int frames;

    /**
     * Starts this application on the process main thread. Prefer the documented Gradle launcher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) throws Exception {
        PathTracingExample app = new PathTracingExample();
        for (String arg : args) {
            if (arg.startsWith("--snapshot=")) app.snapshot = Path.of(arg.substring(11));
            if (arg.equals("--2d")) app.flatView = true;
            if (arg.equals("--benchmark")) app.benchmark = true;
            if (arg.startsWith("--quality="))
                app.requestedQuality =
                        PathTracer3D.Quality.valueOf(
                                arg.substring(10).toUpperCase(java.util.Locale.ROOT));
        }
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .contextVersion(4, 3)
                        .size(1100, 760)
                        .depthBits(24)
                        .visible(app.snapshot == null && !app.benchmark)
                        .title(
                                "Valthorne | Path-traced lighting | Tab 2D/3D | Arrows orbit |"
                                        + " 1/2/3 quality | D filter | V sync"));
    }

    /** Adds a procedural model instance to the path-traced scene at the supplied world position. */
    private void model(Model3D model, float x, float y, float z, Material3D material) {
        scene.add(new ModelInstance3D().setModel(model).setPosition(x, y, z).setMaterial(material));
    }

    /**
     * Creates a rough, tinted surface material for the path-traced room; metallic and transmissive
     * variants are derived by the caller.
     */
    private Material3D paint(float r, float g, float b) {
        return new Material3D().setTint(new Color(r, g, b, 1)).setRoughness(.7f);
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        tracer =
                new PathTracer3D()
                        .setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE)
                        .setSky(.012f, .016f, .025f)
                        .setQuality(PathTracer3D.Quality.HIGH);
        if (snapshot != null) tracer.setQuality(PathTracer3D.Quality.ULTRA).setMaxSamples(512);
        overlay = new TextureBatch(512);
        text = new PerformanceOverlay();
        viewport = new ScreenViewport(1100, 760);
        camera.setClipPlanes(.1f, 100);
        model(ModelBuilder3D.box(12, 12, .2f), 0, 0, -.1f, paint(.72f, .72f, .69f));
        model(ModelBuilder3D.box(12, .2f, 6), 0, 4, 3, paint(.8f, .8f, .78f));
        model(ModelBuilder3D.box(.2f, 8, 6), -5, 0, 3, paint(.65f, .13f, .075f));
        model(ModelBuilder3D.box(.2f, 8, 6), 5, 0, 3, paint(.08f, .32f, .58f));
        model(
                ModelBuilder3D.sphere(1.15f, 64, 32),
                -2.7f,
                0,
                1.2f,
                paint(.94f, .7f, .32f).setMetallic(1).setRoughness(.16f));
        model(
                ModelBuilder3D.sphere(1.15f, 64, 32),
                0,
                .2f,
                1.2f,
                paint(.99f, 1, 1).setTransmission(1).setIndexOfRefraction(1.5f).setRoughness(0));
        model(
                ModelBuilder3D.sphere(1.15f, 64, 32),
                2.7f,
                .4f,
                1.2f,
                paint(.08f, .3f, .42f).setMetallic(.35f).setRoughness(.24f));
        model(ModelBuilder3D.box(2.3f, 1.6f, 1.6f), -.8f, 2.4f, .8f, paint(.75f, .19f, .09f));
        model(
                ModelBuilder3D.box(3.8f, 2.2f, .025f),
                -1,
                .2f,
                5.7f,
                new Material3D().setEmissive(1, .94f, .83f, 1).setEmissionStrength(14));
        model(
                ModelBuilder3D.box(.025f, 2, 3),
                4.8f,
                -1,
                3,
                new Material3D().setEmissive(.72f, .85f, 1, 1).setEmissionStrength(7));
        model(
                ModelBuilder3D.box(1.5f, .025f, 2.8f),
                -3,
                3.8f,
                3,
                new Material3D().setEmissive(1, .55f, .25f, 1).setEmissionStrength(4));
        flat = new valthorne.graphics.lighting2d.PathTracer2D().setView(0, 0, 9, 20);
        flat.getTracer()
                .setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE)
                .setSky(.008f, .012f, .02f)
                .setQuality(
                        snapshot == null ? PathTracer3D.Quality.HIGH : PathTracer3D.Quality.ULTRA)
                .setMaxSamples(snapshot == null ? 4096 : 512);
        flat.addSurface(-7, -5, 14, 10, 0, paint(.65f, .65f, .62f).setRoughness(.4f));
        flat.addWall(-3, -2, .4f, 4, 1, paint(.7f, .08f, .03f));
        flat.addWall(2.6f, -2, .4f, 4, 1, paint(.03f, .25f, .65f));
        flat.addWall(
                -.7f,
                -.3f,
                1.4f,
                .6f,
                .8f,
                paint(.9f, .65f, .28f).setMetallic(1).setRoughness(.12f));
        flat.addAreaLight(-4, 1, 1.8f, .3f, new Color(1, .5f, .2f, 1), 20);
        flat.addAreaLight(4, 1, 2, .4f, new Color(.25f, .6f, 1, 1), 18);
        flat.addAreaLight(0, -2.5f, 2.5f, .45f, new Color(.75f, 1, .85f, 1), 16);
        if (requestedQuality != null) {
            tracer.setQuality(requestedQuality);
            flat.getTracer().setQuality(requestedQuality);
        }
        if (benchmark) {
            glfwSwapInterval(0);
            vsync = false;
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
        boolean tab = Keyboard.isKeyDown(GLFW_KEY_TAB);
        if (tab && !tabDown) flatView = !flatView;
        tabDown = tab;
        boolean d = Keyboard.isKeyDown(GLFW_KEY_D);
        if (d && !dDown) active().setDenoising(!active().isDenoising());
        dDown = d;
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE))
            glfwSetWindowShouldClose(Window.getAddress(), true);
        if (Keyboard.isKeyDown(GLFW_KEY_LEFT)) angle -= delta * .45f;
        if (Keyboard.isKeyDown(GLFW_KEY_RIGHT)) angle += delta * .45f;
        if (Keyboard.isKeyDown(GLFW_KEY_1)) active().setQuality(PathTracer3D.Quality.INTERACTIVE);
        if (Keyboard.isKeyDown(GLFW_KEY_2)) active().setQuality(PathTracer3D.Quality.HIGH);
        if (Keyboard.isKeyDown(GLFW_KEY_3)) active().setQuality(PathTracer3D.Quality.ULTRA);
        boolean v = Keyboard.isKeyDown(GLFW_KEY_V);
        if (v && !vDown) {
            vsync = !vsync;
            glfwSwapInterval(vsync ? 1 : 0);
        }
        vDown = v;
        camera.setPosition(11 * (float) Math.cos(angle), 11 * (float) Math.sin(angle), 5.1f);
        camera.lookAt(0, .5f, 1.7f, 0, 0, 1);
    }

    /** Returns the borrowed tracer selected by the 2D/3D view toggle. */
    private PathTracer3D active() {
        return flatView ? flat.getTracer() : tracer;
    }

    /**
     * Composes the scene and overlays on the owning graphics thread, then performs any requested
     * capture or benchmark bookkeeping.
     */
    @Override
    public void render() {
        int w = Window.getWidth(), h = Window.getHeight();
        if (w < 1 || h < 1) return;
        text.frame();
        long started = System.nanoTime();
        glViewport(0, 0, w, h);
        if (flatView) flat.render();
        else tracer.render(scene, camera);
        viewport.update(w, h);
        viewport.render(
                () -> {
                    overlay.begin();
                    text.drawText(overlay, "VALTHORNE / PATH-TRACED LIGHTING", 28, h - 38);
                    text.draw(overlay, 28, h - 70);
                    text.drawText(
                            overlay,
                            (flatView ? "2D / " : "3D / ")
                                    + active().getQuality()
                                    + " / "
                                    + active().getAccumulatedSamples()
                                    + " SAMPLES",
                            420,
                            h - 70);
                    text.drawText(
                            overlay, "BOUNCED LIGHT / SOFT AREA SHADOWS / METAL / GLASS", 28, 55);
                    text.drawText(
                            overlay,
                            "TAB: 2D/3D   ARROWS: ORBIT   1/2/3: QUALITY   D: FILTER",
                            28,
                            27);
                    overlay.end();
                });
        frames++;
        if (benchmark) {
            glFinish();
            if (frames > 30 && frames <= 150)
                timings[frames - 31] = (System.nanoTime() - started) * 1e-6;
            if (frames == 150) {
                java.util.Arrays.sort(timings);
                System.out.printf(
                        java.util.Locale.ROOT,
                        "PATH TRACE %s %s: mean %.3f ms, median %.3f ms, p95 %.3f ms; %s%n",
                        flatView ? "2D" : "3D",
                        active().getQuality(),
                        java.util.Arrays.stream(timings).average().orElse(0),
                        timings[60],
                        timings[114],
                        glGetString(GL_RENDERER));
                glfwSetWindowShouldClose(Window.getAddress(), true);
            }
        }
        if (snapshot != null && active().getAccumulatedSamples() >= 512) {
            save();
            System.out.printf(
                    "Path tracing: %d triangles, %d BVH nodes, %d builds, %d frames; %s%n",
                    active().getTriangleCount(),
                    active().getBvhNodeCount(),
                    active().getSceneBuildCount(),
                    frames,
                    glGetString(GL_RENDERER));
            glfwSetWindowShouldClose(Window.getAddress(), true);
        }
    }

    /** Writes the converged path-traced framebuffer to the requested snapshot PNG. */
    private void save() {
        FrameCapture.save(snapshot);
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        tracer.close();
        flat.close();
        overlay.dispose();
        text.close();
    }
}
