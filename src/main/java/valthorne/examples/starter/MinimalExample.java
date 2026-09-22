// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.starter;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import valthorne.*;
import valthorne.graphics.Color;

/**
 * The smallest complete application: initialize, update in seconds, render, and dispose. The
 * background color is retained rather than allocated per frame.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>This example owns no textures, audio sources or physics worlds. Add GPU resources in init and
 * release them in dispose; never allocate them before the context is ready.
 *
 * <p>Run through the documented Gradle task for validated options and platform checks. Study the
 * accompanying <a
 * href="https://github.com/tehnewb/Valthorne-examples/blob/main/docs/starter.md">example
 * walkthrough</a> for controls, code navigation and extension exercises.
 */
public final class MinimalExample implements Application {
    private final Color background = new Color(.055f, .075f, .12f, 1); // Reused frame color.
    private int frames; // Completed update callbacks.

    /** Launches the application on the process main thread. */
    public static void main(String[] args) {
        JGL.init(
                new MinimalExample(),
                JGLConfiguration.defaults()
                        .title("Valthorne | Application Starter")
                        .size(960, 540));
    }

    /** Creates application resources after the graphics context exists. */
    @Override
    public void init() {}

    /** Advances simulation in seconds and handles exit requests. */
    @Override
    public void update(float delta) {
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) Window.requestClose();
    }

    /** Clears the frame; add drawing after this call. */
    @Override
    public void render() {
        Window.clear(background);
    }

    /** Releases application-owned resources before the context is destroyed. */
    @Override
    public void dispose() {}
}
