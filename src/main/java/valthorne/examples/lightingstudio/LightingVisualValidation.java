// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lightingstudio;

import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.render.PathTracer3D;
import valthorne.graphics.scene.Scene3D;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.graphics.texture.FrameBuffer;
import valthorne.graphics.texture.Texture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Arrays;

import javax.imageio.ImageIO;

/**
 * Reproducible offscreen glass/metal/diffuse reference and camera-motion workload. Saves raw and
 * filtered converged frames from the same accumulated radiance, plus moving frames. Does not read
 * or overwrite the interactive user's light rig. Run through LightingStudio with {@code
 * --visual-validation=output-directory}.
 *
 * @author Albert Beaupre
 */
public final class LightingVisualValidation {
    /** Output dimensions used identically for all comparisons. */
    private static final int WIDTH = 640, HEIGHT = 480;

    /**
     * Renders controlled references and GPU-completed motion timings in an isolated hidden context.
     * All native resources are released before returning.
     *
     * @param folder destination directory for PNGs and timing output
     * @throws Exception if context creation, image encoding or rendering fails
     */
    public static void run(String folder) throws Exception {
        Path output = Path.of(folder);
        Files.createDirectories(output);
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(WIDTH, HEIGHT, "Lighting visual validation", 0, 0);
        if (window == 0) throw new IllegalStateException("GL context creation failed");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        FrameBuffer target = new FrameBuffer(WIDTH, HEIGHT, true);
        target.begin();
        glViewport(0, 0, WIDTH, HEIGHT);
        Texture checker = checker();
        Scene3D scene = new Scene3D();
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(16, 16))
                        .setMaterial(
                                new Material3D()
                                        .setTint(new Color(.6f, .62f, .65f, 1))
                                        .setRoughness(.65f)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(10, 5))
                        .setPosition(0, 2.5f, 2.5f)
                        .setRotation((float) Math.PI / 2, 0, 0)
                        .setMaterial(new Material3D().setTexture(checker).setRoughness(1)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.sphere(1, 64, 32))
                        .setPosition(0, 0, 1)
                        .setMaterial(
                                new Material3D().setTransmission(1).setIndexOfRefraction(1.5f)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.sphere(1, 64, 32))
                        .setPosition(-2.4f, 0, 1)
                        .setMaterial(
                                new Material3D()
                                        .setTint(new Color(.95f, .7f, .3f, 1))
                                        .setMetallic(1)
                                        .setRoughness(.12f)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.sphere(1, 64, 32))
                        .setPosition(2.4f, 0, 1)
                        .setMaterial(
                                new Material3D()
                                        .setTint(new Color(.12f, .35f, .7f, 1))
                                        .setRoughness(.65f)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(3, 2))
                        .setPosition(-2, -1, 5)
                        .setMaterial(
                                new Material3D()
                                        .setEmissive(1, .94f, .85f, 1)
                                        .setEmissionStrength(8)));
        scene.add(
                new ModelInstance3D()
                        .setModel(ModelBuilder3D.plane(1, 3))
                        .setPosition(4, -1, 3)
                        .setRotation(0, (float) Math.PI / 2, 0)
                        .setMaterial(
                                new Material3D()
                                        .setEmissive(.8f, .9f, 1, 1)
                                        .setEmissionStrength(6)));
        PerspectiveCamera camera = new PerspectiveCamera();
        camera.setFieldOfViewDegrees(48);
        camera.setClipPlanes(.05f, 100);
        camera.setPosition(0, -10, 3.8f);
        camera.lookAt(0, .4f, 1.1f, 0, 0, 1);
        try (PathTracer3D tracer =
                new PathTracer3D()
                        .setSky(.12f, .14f, .18f)
                        .setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE)
                        .setQuality(PathTracer3D.Quality.ULTRA)
                        .setMaxSamples(1024)
                        .setDenoising(false)) {
            while (tracer.getAccumulatedSamples() < 1024) tracer.render(scene, camera);
            save(output.resolve("reference-raw.png"));
            tracer.setDenoising(true);
            tracer.render(scene, camera);
            save(output.resolve("reference-filtered.png"));
            tracer.setRenderMode(PathTracer3D.RenderMode.REALTIME)
                    .setQuality(PathTracer3D.Quality.INTERACTIVE);
            double[] times = new double[120];
            for (int frame = 0; frame < 150; frame++) {
                float x = (float) Math.sin((frame - 75) * .005) * 1.5f;
                camera.setPosition(x, -10, 3.8f);
                camera.lookAt(0, .4f, 1.1f, 0, 0, 1);
                long start = System.nanoTime();
                tracer.render(scene, camera);
                glFinish();
                if (frame >= 30) times[frame - 30] = (System.nanoTime() - start) * 1e-6;
                if (frame == 74 || frame == 75 || frame == 76 || frame == 149)
                    save(output.resolve("motion-" + frame + ".png"));
            }
            for (int frame = 0; frame < 256; frame++) tracer.render(scene, camera);
            save(output.resolve("settled.png"));
            Arrays.sort(times);
            String report =
                    String.format(
                            java.util.Locale.ROOT,
                            "mean %.3f ms, median %.3f ms, p95 %.3f ms%n",
                            Arrays.stream(times).average().orElse(0),
                            times[60],
                            times[114]);
            Files.writeString(output.resolve("timing.txt"), report);
            System.out.print(report);
            if (glGetError() != GL_NO_ERROR)
                throw new AssertionError("OpenGL error in visual validation");
        } finally {
            checker.dispose();
            target.end();
            target.dispose();
            GL.setCapabilities(null);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    /**
     * Creates a high-contrast neutral pattern for judging reflection/refraction.
     *
     * @return caller-owned GPU texture
     * @throws Exception if PNG encoding fails
     */
    static Texture checker() throws Exception {
        BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 512; x++)
                image.setRGB(x, y, ((x / 32 + y / 32) & 1) == 0 ? 0xffdadada : 0xff252525);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new Texture(bytes.toByteArray());
    }

    /**
     * Saves the current framebuffer without changing rendering parameters.
     *
     * @param path PNG output path
     * @throws Exception if writing fails
     */
    private static void save(Path path) throws Exception {
        var bytes = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int i = (y * WIDTH + x) * 4;
                image.setRGB(
                        x,
                        HEIGHT - 1 - y,
                        0xff000000
                                | ((bytes.get(i) & 255) << 16)
                                | ((bytes.get(i + 1) & 255) << 8)
                                | (bytes.get(i + 2) & 255));
            }
        ImageIO.write(image, "png", path.toFile());
    }
}
