// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.shared;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL11.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import valthorne.Window;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Writes a rendered example frame as an opaque PNG, with shared error handling and buffer
 * ownership.
 *
 * <p>Call on the graphics thread after composition and before buffer swap, with the window's
 * default framebuffer bound. Dimensions come from the native framebuffer so Retina and scaled
 * displays are captured at their actual pixel size. Readback is synchronous and belongs in capture
 * modes, not the normal game loop. Pixel storage is released before this method returns.
 */
public final class FrameCapture {
    /** Prevents construction of the stateless capture utility. */
    private FrameCapture() {}

    /**
     * Captures the current window framebuffer and creates missing parent directories.
     *
     * @param destination PNG path, resolved relative to the application's working directory
     * @throws IllegalStateException if the framebuffer is unavailable or OpenGL reports an error
     * @throws UncheckedIOException if the image cannot be written
     */
    public static void save(Path destination) {
        try (var stack = MemoryStack.stackPush()) {
            var dimensions = stack.mallocInt(2);
            glfwGetFramebufferSize(
                    Window.getAddress(), dimensions.slice(0, 1), dimensions.slice(1, 1));
            int width = dimensions.get(0), height = dimensions.get(1);
            if (width <= 0 || height <= 0)
                throw new IllegalStateException("Cannot capture an empty framebuffer");
            var pixels =
                    MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            try {
                glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                int error = glGetError();
                if (error != GL_NO_ERROR)
                    throw new IllegalStateException("OpenGL capture error: " + error);
                var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int offset = (y * width + x) * 4;
                        int rgb =
                                Byte.toUnsignedInt(pixels.get(offset)) << 16
                                        | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                        | Byte.toUnsignedInt(pixels.get(offset + 2));
                        image.setRGB(x, height - 1 - y, rgb);
                    }
                }
                Path absolute = destination.toAbsolutePath().normalize();
                Files.createDirectories(absolute.getParent());
                if (!ImageIO.write(image, "png", absolute.toFile()))
                    throw new IOException("PNG writer unavailable");
                System.out.println("Capture: " + destination + " (" + width + " x " + height + ")");
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write capture " + destination, failure);
        }
    }
}
