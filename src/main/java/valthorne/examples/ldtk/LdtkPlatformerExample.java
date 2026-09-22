// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.ldtk;

import static org.lwjgl.glfw.GLFW.*;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.camera.OrthographicCamera;
import valthorne.graphics.Color;
import valthorne.graphics.debug.PerformanceOverlay;
import valthorne.graphics.map.ldtk.LdtkEntity;
import valthorne.graphics.map.ldtk.LdtkLayer;
import valthorne.graphics.map.ldtk.LdtkMap;
import valthorne.graphics.map.ldtk.LdtkProject;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;
import valthorne.io.file.ValthorneFiles;
import valthorne.viewport.ScreenViewport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * A 4096 by 1440 pixel platformer kingdom loaded from an LDtk project. The entity layer drives
 * terrain and scenery placement while Valthorne's LDtk runtime owns the atlas texture.
 */
public final class LdtkPlatformerExample implements Application {
    private static final String ROOT = "valthorne/ldtk/";
    private static final float WORLD_WIDTH = 4096, WORLD_HEIGHT = 1440;
    private final Color sky = new Color(.035f, .065f, .13f, 1);
    private final OrthographicCamera camera = new OrthographicCamera();
    private TextureBatch batch;
    private Texture white;
    private LdtkProject project;
    private LdtkMap map;
    private LdtkLayer world;
    private ScreenViewport viewport;
    private PerformanceOverlay overlay;
    private float zoom = 2.2f;

    /** Opens the interactive map viewer. */
    public static void main(String[] args) {
        JGL.init(
                new LdtkPlatformerExample(),
                JGLConfiguration.defaults()
                        .title("Valthorne | LDtk Overgrown Kingdom | WASD scroll | Wheel zoom")
                        .size(1280, 720));
    }

    /** Loads the LDtk project and uploads its tileset after the graphics context exists. */
    @Override
    public void init() {
        byte[] json = ValthorneFiles.readBytes(ROOT + "platformer.ldtk");
        byte[] atlas = ValthorneFiles.readBytes(ROOT + "platformer.png");
        project =
                LdtkProject.load(
                        json,
                        ROOT + "platformer.ldtk",
                        (parent, path, dependency) -> atlas.clone());
        map = project.asMap();
        world = project.level("OvergrownKingdom").layer("World");
        batch = new TextureBatch(32768);
        overlay = new PerformanceOverlay();
        viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        viewport.setCamera(camera);
        camera.setCenter(640, 400);
        camera.setZoom(zoom);
        glfwSwapInterval(0);
        white = makeWhiteTexture();
    }

    /**
     * Scrolls with WASD/arrows, zooms around the current view with the wheel, and resets with R.
     */
    @Override
    public void update(float delta) {
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) Window.requestClose();
        float speed = 620f / zoom;
        float dx = axis(GLFW_KEY_D, GLFW_KEY_RIGHT) - axis(GLFW_KEY_A, GLFW_KEY_LEFT);
        float dy = axis(GLFW_KEY_W, GLFW_KEY_UP) - axis(GLFW_KEY_S, GLFW_KEY_DOWN);
        camera.setCenter(
                camera.getCenter().x() + dx * speed * delta,
                camera.getCenter().y() + dy * speed * delta);
        int wheel = Mouse.getScrollY();
        if (wheel != 0) zoom = Math.max(.45f, Math.min(5f, zoom * (float) Math.pow(1.16, wheel)));
        if (Keyboard.isKeyDown(GLFW_KEY_R)) {
            camera.setCenter(640, 400);
            zoom = 2.2f;
        }
        camera.setZoom(zoom);
        clampCamera();
    }

    /** Draws a parallax sky and all visible LDtk entities with conservative camera culling. */
    @Override
    public void render() {
        int width = Window.getWidth(), height = Window.getHeight();
        if (width < 1 || height < 1) return;
        Window.clear(sky);
        viewport.update(width, height);
        viewport.setCamera(camera);
        overlay.frame();
        viewport.render(
                () -> {
                    batch.begin();
                    drawBackdrop(width, height);
                    Texture atlas = map.tilesetTexture(1);
                    for (LdtkEntity entity : world.entities()) {
                        if (!visible(entity, width, height)) continue;
                        switch (entity.identifier()) {
                            case "Platform" -> drawPlatform(atlas, entity);
                            case "Tree" -> drawTree(atlas, entity);
                            case "Mushroom" -> drawMushroom(atlas, entity);
                            default -> {}
                        }
                    }
                    batch.end();
                });
        drawOverlay(width, height);
    }

    /** Draws camera-relative sky and parallax silhouettes behind the loaded world. */
    private void drawBackdrop(int width, int height) {
        float left = camera.getCenter().x() - width * .5f / zoom;
        float bottom = camera.getCenter().y() - height * .5f / zoom;
        float viewWidth = width / zoom, viewHeight = height / zoom;
        batch.draw(white, left, bottom, viewWidth, viewHeight, new Color(.035f, .065f, .13f, 1));
        for (int i = 0; i < 12; i++) {
            float x = i * 420f - camera.getCenter().x() * .08f;
            batch.draw(white, x, 220, 300, 520 + (i % 3) * 90, new Color(.055f, .105f, .16f, 1));
        }
        for (int i = 0; i < 18; i++) {
            float x = i * 260f - camera.getCenter().x() * .16f;
            batch.draw(white, x, 110, 190, 310 + (i % 4) * 45, new Color(.065f, .14f, .16f, 1));
        }
    }

    /** Tiles one visible LDtk platform entity using edge-aware atlas regions. */
    private void drawPlatform(Texture atlas, LdtkEntity entity) {
        int columns = Math.max(1, entity.width() / 16), rows = Math.max(1, entity.height() / 16);
        float baseX = entity.x(), baseY = WORLD_HEIGHT - entity.y() - entity.height();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                boolean top = row == rows - 1, left = column == 0, right = column == columns - 1;
                float sx = top ? (left ? 16 : right ? 48 : 32) : (left ? 16 : right ? 48 : 32);
                float sy = top ? 0 : 16 + ((row + column) & 1) * 16;
                batch.draw(
                        new TextureRegion(atlas, sx, sy, 16, 16),
                        baseX + column * 16,
                        baseY + row * 16,
                        16,
                        16);
            }
        }
    }

    /** Draws one tree entity in bottom-left world coordinates. */
    private void drawTree(Texture atlas, LdtkEntity entity) {
        float y = WORLD_HEIGHT - entity.y() - entity.height();
        batch.draw(new TextureRegion(atlas, 80, 64, 48, 80), entity.x(), y, 48, 80);
    }

    /** Draws a deterministic mushroom variant selected from the entity identifier. */
    private void drawMushroom(Texture atlas, LdtkEntity entity) {
        float y = WORLD_HEIGHT - entity.y() - 16;
        int variant = Math.floorMod(entity.iid().hashCode(), 4);
        batch.draw(new TextureRegion(atlas, 80 + variant * 16, 160, 16, 16), entity.x(), y, 16, 16);
    }

    /** Returns whether an entity intersects the padded camera viewport. */
    private boolean visible(LdtkEntity entity, int width, int height) {
        float halfW = width * .5f / zoom + 100, halfH = height * .5f / zoom + 100;
        float x = entity.x(), y = WORLD_HEIGHT - entity.y() - entity.height();
        return x + entity.width() >= camera.getCenter().x() - halfW
                && x <= camera.getCenter().x() + halfW
                && y + entity.height() >= camera.getCenter().y() - halfH
                && y <= camera.getCenter().y() + halfH;
    }

    /** Draws controls, zoom state, and frame diagnostics in screen coordinates. */
    private void drawOverlay(int width, int height) {
        ScreenViewport ui = new ScreenViewport(width, height);
        ui.update(width, height);
        ui.render(
                () -> {
                    batch.begin();
                    batch.draw(
                            white, 20, height - 92, 600, 66, new Color(.015f, .025f, .055f, .88f));
                    overlay.drawText(batch, "LDTK / OVERGROWN KINGDOM", 38, height - 54);
                    overlay.drawText(
                            batch,
                            "WASD / ARROWS: SCROLL    WHEEL: ZOOM    R: RESET    ESC: CLOSE",
                            38,
                            height - 78);
                    overlay.draw(batch, width - 310, height - 48);
                    overlay.drawText(
                            batch,
                            String.format(java.util.Locale.ROOT, "ZOOM %.2fx / VSYNC OFF", zoom),
                            width - 310,
                            height - 78);
                    batch.end();
                });
    }

    /** Constrains the camera center so the viewport remains inside the world. */
    private void clampCamera() {
        float halfW = Window.getWidth() * .5f / zoom, halfH = Window.getHeight() * .5f / zoom;
        camera.setCenter(
                Math.max(halfW, Math.min(WORLD_WIDTH - halfW, camera.getCenter().x())),
                Math.max(halfH, Math.min(WORLD_HEIGHT - halfH, camera.getCenter().y())));
    }

    /** Returns one while either key assigned to a movement direction is held. */
    private static int axis(int primary, int alternate) {
        return Keyboard.isKeyDown(primary) || Keyboard.isKeyDown(alternate) ? 1 : 0;
    }

    /** Creates the owned one-pixel texture used for solid-color backdrop rectangles. */
    private static Texture makeWhiteTexture() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffffffff);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return new Texture(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Releases all OpenGL and decoded image resources owned by the example. */
    @Override
    public void dispose() {
        if (map != null) map.dispose();
        if (project != null) project.dispose();
        if (batch != null) batch.dispose();
        if (white != null) white.dispose();
        if (overlay != null) overlay.close();
    }
}
