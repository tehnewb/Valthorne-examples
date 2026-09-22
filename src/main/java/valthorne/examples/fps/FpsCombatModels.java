// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.fps;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.AABBf;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ObjModel3D;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Downloaded, textured CC0 combat assets. Geometry is baked to meters, centered XYZ and Z-up; the
 * rifle barrel and sentry face point along +Y. Models and textures belong to this library, while
 * returned instance materials are independent. Close after instances stop rendering, on the render
 * thread if textures have been uploaded. Loading itself requires no GL context. See
 * docs/fps-arena-combat-models.md for authors, licenses, sources and conversion details.
 */
public final class FpsCombatModels implements AutoCloseable {
    private static final String ROOT = "valthorne/fps-arena/combat/";
    private final List<Entry> entries;
    private boolean closed;

    /**
     * Full model extents in meters, with an origin at the bounding-box center. attachmentOffset()
     * restores the arms' authored grip alignment relative to the centered rifle: apply it in
     * rifle-local space, using the same scale and rotation. Other entries use a zero offset.
     */
    public record Entry(
            String name,
            ObjModel3D model,
            float width,
            float depth,
            float height,
            float roughness,
            float metallic,
            float offsetX,
            float offsetY,
            float offsetZ) {
        /**
         * Returns a fresh material descriptor using this asset's default surface settings;
         * texture-bearing model parts remain shared.
         */
        public Material3D material() {
            return new Material3D().setRoughness(roughness).setMetallic(metallic);
        }

        /**
         * Axes and facing are baked into vertices, so the default instance rotation is identity.
         */
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        /**
         * Returns or writes the local attachment offset used to place the asset relative to its
         * owning rig.
         */
        public Vector3f attachmentOffset() {
            return attachmentOffset(new Vector3f());
        }

        /**
         * Returns or writes the local attachment offset used to place the asset relative to its
         * owning rig.
         */
        public Vector3f attachmentOffset(Vector3f destination) {
            return destination.set(offsetX, offsetY, offsetZ);
        }
    }

    /**
     * Loads the rifle, drone, grenade and arm assets; validates their structure and closes partial
     * results on failure.
     */
    public FpsCombatModels() {
        List<Entry> loaded = new ArrayList<>(5);
        try {
            loaded.add(load("M4A1 carbine", "m4a1", .48f, .25f, 0, 0, 0));
            loaded.add(load("SENTRY-2 industrial robot", "sentry2", .6f, .15f, 0, 0, 0));
            loaded.add(load("Mk2 fragmentation grenade", "mk2", .7f, .08f, 0, 0, 0));
            loaded.add(load("M4A1 magazine", "m4a1_magazine", .48f, .25f, 0, 0, 0));
            loaded.add(
                    load(
                            "First-person arms",
                            "fps_arms",
                            .65f,
                            0,
                            -.123832673f,
                            -.214057371f,
                            -.080805525f));
            entries = List.copyOf(loaded);
        } catch (RuntimeException | Error failure) {
            for (Entry entry : loaded) entry.model().dispose();
            throw failure;
        }
    }

    /** Returns the borrowed first-person rifle entry. */
    public Entry rifle() {
        return entry(0);
    }

    /** Returns the borrowed sentry drone entry. */
    public Entry drone() {
        return entry(1);
    }

    /** Returns the borrowed grenade entry. */
    public Entry grenade() {
        return entry(2);
    }

    /**
     * The rifle author's separate textured magazine, reusable for pickups or reload presentation.
     */
    public Entry ammo() {
        return entry(3);
    }

    /** Returns the borrowed first-person arm entry. */
    public Entry arms() {
        return entry(4);
    }

    /**
     * Returns the library's borrowed model entries. Keep the library open while scene instances
     * reference their geometry.
     */
    public List<Entry> entries() {
        ensureOpen();
        return entries;
    }

    /**
     * Returns the indexed borrowed entry after validating the library lifetime; the caller must not
     * dispose its shared model.
     */
    private Entry entry(int index) {
        ensureOpen();
        return entries.get(index);
    }

    /** Rejects access after this resource library has been closed. */
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Combat model library is closed");
    }

    /** Loads and validates a combat OBJ with its surface defaults and local attachment offset. */
    private static Entry load(
            String name,
            String file,
            float roughness,
            float metallic,
            float offsetX,
            float offsetY,
            float offsetZ) {
        String source = ROOT + file + ".obj";
        // Source OBJ files already contain centered Z-up geometry. Applying the legacy
        // Y-up conversion here would rotate the weapon and move collider origins.
        ObjModel3D model = ObjModel3D.load(source, FpsCombatModels::readResource, false);
        try {
            AABBf bounds = model.getLocalBounds();
            float width = bounds.maxX - bounds.minX;
            float depth = bounds.maxY - bounds.minY;
            float height = bounds.maxZ - bounds.minZ;
            float tolerance = Math.max(width, Math.max(depth, height)) * 1e-5f;
            if (!bounds.isValid()
                    || !Float.isFinite(width + depth + height)
                    || !(width > 0 && depth > 0 && height > 0)
                    || Math.abs(bounds.minX + bounds.maxX) > tolerance
                    || Math.abs(bounds.minY + bounds.maxY) > tolerance
                    || Math.abs(bounds.minZ + bounds.maxZ) > tolerance
                    || model.getTriangleCount() == 0
                    || model.getParts().isEmpty())
                throw new IllegalStateException("Invalid combat model bounds: " + source);
            int triangles = 0;
            for (ObjModel3D.Part part : model.getParts())
                triangles += part.model().getTriangleCount();
            if (triangles != model.getTriangleCount())
                throw new IllegalStateException("Missing combat material geometry: " + source);
            return new Entry(
                    name, model, width, depth, height, roughness, metallic, offsetX, offsetY,
                    offsetZ);
        } catch (RuntimeException | Error failure) {
            model.dispose();
            throw failure;
        }
    }

    /**
     * Reads a required classpath asset into a byte array and closes its stream; missing assets fail
     * with the requested resource path.
     */
    private static byte[] readResource(String path) throws IOException {
        String resource = path.replace('\\', '/');
        if (!resource.startsWith(ROOT) || resource.contains("../"))
            throw new IOException("Combat dependency is outside the asset directory: " + resource);
        try (InputStream stream = FpsCombatModels.class.getResourceAsStream("/" + resource)) {
            if (stream == null) throw new IOException("Missing combat resource: " + resource);
            return stream.readAllBytes();
        }
    }

    /**
     * Releases resources owned by this component. Call after dependent scene instances or emitters
     * have stopped using them.
     */
    @Override
    public void close() {
        if (closed) return;
        for (Entry entry : entries) entry.model().dispose();
        closed = true;
    }

    /** CPU asset validation; includes finite vertices, unit normals and partition counts. */
    public static void main(String[] args) {
        try (FpsCombatModels library = new FpsCombatModels()) {
            for (Entry entry : library.entries()) {
                for (Model3D.Triangle t : entry.model().getTriangles()) {
                    if (!t.a().isFinite()
                            || !t.b().isFinite()
                            || !t.c().isFinite()
                            || !unit(t.normalA())
                            || !unit(t.normalB())
                            || !unit(t.normalC()))
                        throw new IllegalStateException("Invalid combat surface: " + entry.name());
                }
                System.out.printf(
                        Locale.ROOT,
                        "%s: %,d triangles, %d materials, %.3f x %.3f x %.3f m%n",
                        entry.name(),
                        entry.model().getTriangleCount(),
                        entry.model().getParts().size(),
                        entry.width(),
                        entry.depth(),
                        entry.height());
            }
        }
    }

    /** Checks whether an imported normal has approximately unit length. */
    private static boolean unit(Vector3f normal) {
        return normal.isFinite() && Math.abs(normal.lengthSquared() - 1) < 1e-4f;
    }
}
