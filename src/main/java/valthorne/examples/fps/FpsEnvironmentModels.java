// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.fps;

import org.joml.Quaternionf;
import org.joml.primitives.AABBf;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.ObjModel3D;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared, downloaded CC0 industrial geometry for the FPS arena. Source authors, exact download
 * hashes and adaptation details are packaged alongside the meshes. Coordinates are already Z-up,
 * centered in XY. Structures are grounded at Z=0; crate and barrel are centered in XYZ for Jolt
 * binding. Geometry and authored material groups are reused by all instances. Loading is CPU-only;
 * close on the render thread after renderer use, because textures may have been uploaded.
 */
public final class FpsEnvironmentModels implements AutoCloseable {
    private static final String ROOT = "valthorne/fps-arena/environment/";

    /** Environment asset roles used to choose placement and collision conventions. */
    public enum Category {
        WALL,
        FLOOR,
        BEAM,
        PILLAR,
        PLATFORM,
        RAMP,
        COVER,
        CRATE,
        BARREL,
        PIPE,
        BACKING
    }

    /** Full model extents; yaw is baked into vertices and is always zero. */
    public record Entry(
            String name,
            Category category,
            ObjModel3D model,
            float width,
            float depth,
            float height,
            float roughness,
            float metallic,
            boolean centered) {
        /** White multiplier preserves every authored color and albedo texture. */
        public Material3D material() {
            return new Material3D()
                    .setRoughness(roughness)
                    .setMetallic(metallic)
                    .setCullBackFaces(false);
        }

        /** Returns the baked display yaw in degrees for this environment asset. */
        public float yawDegrees() {
            return 0;
        }

        /**
         * Returns a new Z-axis rotation quaternion for the asset's authored display yaw; the caller
         * owns the mutable result.
         */
        public Quaternionf rotation() {
            return new Quaternionf();
        }
    }

    private final List<Entry> entries;
    private boolean closed;

    /**
     * Loads the shared industrial environment kit and validates its material groups, bounds and
     * normals.
     */
    public FpsEnvironmentModels() {
        List<Entry> loaded = new ArrayList<>(11);
        try {
            loaded.add(
                    load(
                            "Factory brick and window module",
                            "factory_wall",
                            Category.WALL,
                            .85f,
                            0,
                            false));
            loaded.add(load("Steel deck panel", "steel_deck", Category.FLOOR, .72f, .6f, false));
            loaded.add(load("Factory cornice beam", "factory_beam", Category.BEAM, .85f, 0, false));
            loaded.add(
                    load("Factory brick pier", "factory_pillar", Category.PILLAR, .9f, 0, false));
            loaded.add(
                    load(
                            "Braced steel platform",
                            "steel_platform",
                            Category.PLATFORM,
                            .72f,
                            .6f,
                            false));
            loaded.add(
                    load("Steel access stairs", "steel_stairs", Category.RAMP, .72f, .6f, false));
            loaded.add(
                    load(
                            "Weathered concrete barrier",
                            "concrete_barrier",
                            Category.COVER,
                            .92f,
                            0,
                            false));
            loaded.add(
                    load("Wooden shipping crate", "wooden_crate", Category.CRATE, .82f, 0, true));
            loaded.add(load("Weathered oil drum", "oil_barrel", Category.BARREL, .64f, .45f, true));
            loaded.add(
                    load(
                            "Flanged industrial pipe",
                            "industrial_pipe",
                            Category.PIPE,
                            .7f,
                            .5f,
                            false));
            loaded.add(
                    load(
                            "Opaque factory panel underlay",
                            "factory_backing",
                            Category.BACKING,
                            .95f,
                            0,
                            false));
            entries = List.copyOf(loaded);
        } catch (RuntimeException | Error failure) {
            for (Entry entry : loaded) entry.model().dispose();
            throw failure;
        }
    }

    /** Returns the borrowed factory wall entry. */
    public Entry wall() {
        return entry(0);
    }

    /** Returns the borrowed open-grate deck entry; callers add an opaque backing where needed. */
    public Entry floor() {
        return entry(1);
    }

    /** Ceiling and floor intentionally share the same authored deck and texture allocation. */
    public Entry ceiling() {
        return entry(1);
    }

    /** Returns the borrowed factory beam entry. */
    public Entry beam() {
        return entry(2);
    }

    /** Returns the borrowed factory pier entry. */
    public Entry pillar() {
        return entry(3);
    }

    /** Returns the borrowed braced platform entry. */
    public Entry platform() {
        return entry(4);
    }

    /** Stairs ascend from negative Y toward positive Y across a 6x6m footprint and 1.4m rise. */
    public Entry ramp() {
        return entry(5);
    }

    /** Returns the borrowed concrete barrier entry used for arena cover. */
    public Entry cover() {
        return entry(6);
    }

    /** Returns the borrowed crate entry centered for a dynamic physics body. */
    public Entry crate() {
        return entry(7);
    }

    /** Returns the borrowed barrel entry centered for a dynamic physics body. */
    public Entry barrel() {
        return entry(8);
    }

    /** Long axis is X, with flange geometry retained. */
    public Entry pipe() {
        return entry(9);
    }

    /** Continuous 4x4m authored brick panel, doubled into an 8cm horizontal underlay. */
    public Entry backing() {
        return entry(10);
    }

    /**
     * Returns the library's borrowed model entries. Keep the library open while scene instances
     * reference their geometry.
     */
    public List<Entry> entries() {
        if (closed) throw new IllegalStateException("FPS environment assets are closed");
        return entries;
    }

    /**
     * Returns the indexed borrowed entry after validating the library lifetime; the caller must not
     * dispose its shared model.
     */
    private Entry entry(int index) {
        return entries().get(index);
    }

    /**
     * Loads an environment mesh with its category and surface defaults, preserving its specified
     * grounding/centering convention.
     */
    private static Entry load(
            String name,
            String file,
            Category category,
            float roughness,
            float metallic,
            boolean centered) {
        String source = ROOT + file + ".obj";
        ObjModel3D model = ObjModel3D.load(source, FpsEnvironmentModels::readResource, false);
        try {
            AABBf b = model.getLocalBounds();
            float width = b.maxX - b.minX, depth = b.maxY - b.minY, height = b.maxZ - b.minZ;
            float tolerance = Math.max(width, Math.max(depth, height)) * 1e-5f;
            if (!b.isValid()
                    || !Float.isFinite(width + depth + height)
                    || Math.abs(b.minX + b.maxX) > tolerance
                    || Math.abs(b.minY + b.maxY) > tolerance
                    || Math.abs(centered ? b.minZ + b.maxZ : b.minZ) > tolerance
                    || model.getTriangleCount() == 0)
                throw new IllegalStateException("Invalid FPS model bounds: " + source);
            int triangles = 0;
            for (ObjModel3D.Part part : model.getParts()) {
                part.material()
                        .setRoughness(roughness)
                        .setMetallic(metallic)
                        .setCullBackFaces(false);
                triangles += part.model().getTriangleCount();
            }
            if (triangles != model.getTriangleCount())
                throw new IllegalStateException("Lost FPS model material group: " + source);
            return new Entry(
                    name, category, model, width, depth, height, roughness, metallic, centered);
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
            throw new IOException("Model dependency is outside the FPS environment: " + resource);
        try (InputStream stream = FpsEnvironmentModels.class.getResourceAsStream("/" + resource)) {
            if (stream == null)
                throw new IOException("Missing FPS environment resource: " + resource);
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

    /** Headless verification of packaged geometry, bounds, dependencies and material groups. */
    public static void main(String[] args) {
        try (FpsEnvironmentModels models = new FpsEnvironmentModels()) {
            for (Entry entry : models.entries()) {
                System.out.printf(
                        Locale.ROOT,
                        "%s: %d triangles, %d materials, %.3f x %.3f x %.3f, centered=%s%n",
                        entry.name(),
                        entry.model().getTriangleCount(),
                        entry.model().getParts().size(),
                        entry.width(),
                        entry.depth(),
                        entry.height(),
                        entry.centered());
            }
        }
    }
}
