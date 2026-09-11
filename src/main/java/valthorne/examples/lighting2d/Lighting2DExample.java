// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lighting2d;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import valthorne.*;
import valthorne.examples.shared.FrameCapture;
import valthorne.graphics.Color;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.lighting2d.Lighting2D;
import valthorne.graphics.lighting2d.Occluder2D;
import valthorne.graphics.lighting2d.PointLight2D;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.viewport.ScreenViewport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * A 2D composition showing cached light maps, occluders and shadow updates. Static and stress modes
 * make cache invalidation behavior visible.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>The application owns its light renderer, texture batch and white texture. Mutate
 * light/occluder state before rendering; frozen geometry and lights can reuse cached work. Timings
 * are workload measurements, not universal engine performance claims.
 *
 * <p>Run through {@link valthorne.examples.launcher.ExampleLauncher} for help, validated options
 * and platform checks. Study the accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/lighting2d.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class Lighting2DExample implements Application {
    private Lighting2D lighting;
    private PointLight2D cursor;
    private Occluder2D moving;
    private TextureBatch batch;
    private Texture white;
    private PerformanceOverlay text;
    private ScreenViewport viewport;
    private float time;
    private boolean paused, spaceDown, benchmark, animate = true;
    private boolean stress, vsync = true, vDown;
    private Path snapshot;
    private int frames;
    private final double[] samples = new double[300];

    /**
     * Starts this application on the process main thread. Prefer the documented Gradle launcher for
     * validated options and platform checks.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        Lighting2DExample app = new Lighting2DExample();
        for (String arg : args) {
            if (arg.startsWith("--snapshot=")) app.snapshot = Path.of(arg.substring(11));
            if (arg.equals("--benchmark")) app.benchmark = true;
            if (arg.equals("--static")) app.animate = false;
            if (arg.equals("--stress-lights")) app.stress = true;
        }
        JGL.init(
                app,
                JGLConfiguration.defaults()
                        .title(
                                "Valthorne | New 2D lighting | Mouse moves light | Space pauses |"
                                        + " Esc closes")
                        .size(1100, 760)
                        .visible(app.snapshot == null));
    }

    /**
     * Creates the demo scene, rendering resources and input/UI connections after Valthorne has
     * initialized the graphics context.
     */
    @Override
    public void init() {
        lighting = new Lighting2D().setAmbient(new Color(.055f, .075f, .12f, 1));
        batch = new TextureBatch(2048);
        text = new PerformanceOverlay();
        viewport = new ScreenViewport(1100, 760);
        viewport.update(1100, 760);
        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, 0xffffffff);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(pixel, "png", bytes);
            white = new Texture(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lighting.addLight(
                new PointLight2D()
                        .setPosition(250, 400)
                        .setRadius(420)
                        .setIntensity(5)
                        .setColor(new Color(.2f, .55f, 1, 1))
                        .setSourceRadius(15));
        lighting.addLight(
                new PointLight2D()
                        .setPosition(840, 400)
                        .setRadius(440)
                        .setIntensity(5)
                        .setColor(new Color(1, .38f, .12f, 1))
                        .setSourceRadius(15));
        lighting.addLight(
                new PointLight2D()
                        .setPosition(550, 160)
                        .setRadius(470)
                        .setIntensity(4)
                        .setColor(new Color(.25f, 1, .62f, 1))
                        .setCone((float) Math.PI / 2, .45f, .8f)
                        .setSourceRadius(8));
        cursor =
                new PointLight2D()
                        .setPosition(560, 540)
                        .setRadius(330)
                        .setIntensity(3)
                        .setColor(new Color(1, .85f, .45f, 1))
                        .setSourceRadius(12);
        lighting.addLight(cursor);
        lighting.addOccluder(Occluder2D.rectangle(370, 260, 45, 235));
        lighting.addOccluder(Occluder2D.rectangle(670, 310, 45, 235));
        moving = Occluder2D.rectangle(500, 360, 110, 45);
        lighting.addOccluder(moving);
        if (stress)
            for (int i = 0; i < 256; i++)
                lighting.addLight(
                        new PointLight2D()
                                .setPosition((i % 16) * 70 + 20, (i / 16) * 45 + 20)
                                .setRadius(100)
                                .setIntensity(.12f));
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
        boolean v = Keyboard.isKeyDown(GLFW_KEY_V);
        if (v && !vDown) {
            vsync = !vsync;
            glfwSwapInterval(vsync ? 1 : 0);
        }
        vDown = v;
        boolean key = Keyboard.isKeyDown(GLFW_KEY_SPACE);
        if (key && !spaceDown) paused = !paused;
        spaceDown = key;
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE))
            glfwSetWindowShouldClose(Window.getAddress(), true);
        if (!paused && animate) {
            time += (snapshot != null || benchmark) ? 1f / 60 : delta;
            moving.setPosition(495 + 80 * (float) Math.sin(time), 360);
        }
        if (snapshot == null && !benchmark && Mouse.getX() > 0 && Mouse.getY() > 0)
            cursor.setPosition(Mouse.getX(), Mouse.getY());
    }

    /**
     * Draws a tinted rectangle through the shared texture batch in the current 2D coordinate
     * system.
     */
    private void rect(float x, float y, float w, float h, Color color) {
        batch.draw(white, x, y, w, h, color);
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
        long start = System.nanoTime();
        viewport.update(w, h);
        viewport.render(
                () -> {
                    lighting.beginScene(0, 0, w, h, w, h);
                    try {
                        batch.begin();
                        rect(0, 0, w, h, new Color(.48f, .5f, .55f, 1));
                        for (int x = 0; x < w; x += 40)
                            rect(x, 0, 1, h, new Color(.36f, .39f, .45f, 1));
                        for (int y = 0; y < h; y += 40)
                            rect(0, y, w, 1, new Color(.36f, .39f, .45f, 1));
                        rect(370, 260, 45, 235, new Color(.72f, .76f, .82f, 1));
                        rect(670, 310, 45, 235, new Color(.72f, .76f, .82f, 1));
                        rect(moving.getX(), moving.getY(), 110, 45, new Color(.76f, .8f, .86f, 1));
                        batch.end();
                        lighting.endScene();
                    } finally {
                        lighting.cancelScene();
                    }
                    batch.begin();
                    rect(28, h - 146, w - 56, 118, new Color(.025f, .045f, .075f, .95f));
                    rect(28, 25, w - 56, 75, new Color(.025f, .045f, .075f, .95f));
                    text.drawText(batch, "VALTHORNE / 2D LIGHTING", 50, h - 68);
                    text.draw(batch, 50, h - 108);
                    text.drawText(batch, vsync ? "VSYNC ON" : "VSYNC OFF", 460, h - 108);
                    text.drawText(
                            batch,
                            "MOUSE: LIGHT    SPACE: PAUSE WALL    V: SYNC    ESC: CLOSE",
                            50,
                            65);
                    text.drawText(
                            batch, "SOFT SHADOWS / CACHED LIGHT MAP / ONE LIGHT DRAW", 50, 35);
                    batch.end();
                });
        frames++;
        if (benchmark) {
            glFinish();
            if (frames > 120 && frames <= 420)
                samples[frames - 121] = (System.nanoTime() - start) * 1e-6;
            if (frames == 420) {
                java.util.Arrays.sort(samples);
                System.out.printf(
                        java.util.Locale.ROOT,
                        "2D BENCHMARK: mean %.3f ms, median %.3f ms, p95 %.3f ms; map renders %d;"
                                + " shadow uploads %d; %s%n",
                        java.util.Arrays.stream(samples).average().orElse(0),
                        samples[150],
                        samples[285],
                        lighting.getLightMapRenderCount(),
                        lighting.getShadowUploadCount(),
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
     * Writes the requested lighting capture using the native framebuffer's actual pixel dimensions.
     */
    private void save() {
        FrameCapture.save(snapshot);
    }

    /**
     * Releases application-owned rendering, UI and simulation resources before Valthorne destroys
     * the graphics context.
     */
    @Override
    public void dispose() {
        lighting.close();
        batch.dispose();
        white.dispose();
        text.close();
    }
}
