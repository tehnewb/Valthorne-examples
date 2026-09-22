// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.fps;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;

import valthorne.graphics.Color;
import valthorne.graphics.model.*;
import valthorne.graphics.particle.*;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.math.physics.*;

import java.nio.ByteBuffer;
import java.util.Random;

/** Transparent glowing particles; every moving particle carries one bounded local light. */
final class FpsArenaEffects implements AutoCloseable {
    private final Scene3D scene;
    private final PhysicsWorld3D world;
    private final Random random = new Random(911);
    private final Model3D fleck = ModelBuilder3D.plane(1, 1);
    private final Quaternionf facing = new Quaternionf();
    private Texture particleTexture;
    private final Color[] colors = {
        new Color(1, .45f, .08f, 1), new Color(.12f, .65f, 1, 1), new Color(.8f, .15f, 1, 1)
    };
    private final Vector3f origin = new Vector3f(), direction = new Vector3f(0, 0, 1);
    private final Rayf launchRay = new Rayf();
    private ParticleEmitter3D debris, flares;
    private float burstSpeed = 3;
    boolean physical = true, lights = true;
    float lightGain = 1;
    int palette;
    private int shadowBudget = 2;

    /**
     * Binds bounded effect emitters to the borrowed scene and physics world, selecting physical
     * particles and attached lighting.
     */
    FpsArenaEffects(Scene3D scene, PhysicsWorld3D world, boolean physical, boolean lights) {
        this.scene = scene;
        this.world = world;
        this.physical = physical;
        this.lights = lights;
        rebuild();
    }

    /**
     * Configures one particle's emissive material and optional attached light with the requested
     * brightness and range.
     */
    private void appearance(Particle3D particle, float emission, float power, float range) {
        // Reuse the particle's own material so every lifetime fades independently.
        var material = particle.getModelInstance().getMaterial();
        material.setRenderPass(RenderPass3D.TRANSLUCENT)
                .setTint(colors[palette])
                .setEmissive(colors[palette])
                .setEmissionStrength(emission)
                .setEmissionLightEnabled(false)
                .setMetallic(0)
                .setRoughness(1)
                .setTexture(particleTexture)
                .setCastsShadow(false)
                .setAlphaCutoff(0);
        material.getTint().a(.48f);
        particle.getModelInstance().setRotation(facing);
        particle.getLight()
                .setColor(colors[palette])
                .setRange(range)
                .setIntensity(power * lightGain)
                .setCastsShadows(false);
        particle.setLightEnabled(lights);
    }

    /**
     * Replaces the bounded impact-debris and flare emitters after their physical/lighting
     * configuration changes.
     */
    void rebuild() {
        closeEmitters();
        debris =
                new ParticleEmitter3D(
                                96,
                                p -> {
                                    p.setModel(fleck).setLifetime(1.2f + random.nextFloat() * .8f);
                                    p.getModelInstance().setScale(.16f);
                                    appearance(p, .005f, .7f, 2.5f);
                                    p.getPosition().set(origin);
                                    p.getVelocity()
                                            .set(
                                                    random.nextFloat() * 2 - 1,
                                                    random.nextFloat() * 2 - 1,
                                                    random.nextFloat() * 1.5f)
                                            .mul(burstSpeed)
                                            .fma(1.2f, direction);
                                    if (!physical) p.getAcceleration().set(0, 0, -9.81f);
                                })
                        .attach(scene);
        flares =
                new ParticleEmitter3D(
                                12,
                                p -> {
                                    p.setModel(fleck).setLifetime(6);
                                    p.getModelInstance().setScale(.28f);
                                    appearance(p, .008f, 8, 5);
                                    p.getPosition().set(origin);
                                    p.getVelocity().set(direction).mul(8).add(0, 0, 2);
                                    if (!physical) p.getAcceleration().set(0, 0, -5);
                                })
                        .attach(scene);
        if (physical) {
            var debrisBody =
                    new BodySettings3D(CollisionShape3D.sphere(.05f), MotionType3D.DYNAMIC)
                            .setLayer(3)
                            .setMass(.015f)
                            .setRestitution(.48f)
                            .setFriction(.5f)
                            .setContinuousCollision(true);
            var flareBody =
                    new BodySettings3D(CollisionShape3D.sphere(.075f), MotionType3D.DYNAMIC)
                            .setLayer(3)
                            .setMass(.08f)
                            .setRestitution(.55f)
                            .setGravityFactor(.5f)
                            .setContinuousCollision(true);
            debris.setPhysics(world, p -> debrisBody);
            flares.setPhysics(world, p -> flareBody);
        }
    }

    /** Uploads one shared radial opacity mask on the GL thread; headless checks skip this. */
    void prepareRendering() {
        if (particleTexture != null) return;
        int size = 32;
        var pixels = ByteBuffer.allocateDirect(size * size * 4);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                float dx = (x + .5f) * 2 / size - 1, dy = (y + .5f) * 2 / size - 1;
                float radial = Math.max(0, 1 - dx * dx - dy * dy);
                pixels.put((byte) 255)
                        .put((byte) 255)
                        .put((byte) 255)
                        .put((byte) Math.round(255 * radial * radial));
            }
        pixels.flip();
        try (var state = new RenderStateSnapshot3D()) {
            valthorne.PlatformTools.textureUnit(0);
            particleTexture = new Texture(new TextureData(pixels, size, size));
            particleTexture.setFilter(TextureFilter.LINEAR);
        }
        applyTexture(debris);
        applyTexture(flares);
    }

    /**
     * Assigns the shared particle sprite to the emitter while retaining texture ownership in this
     * effect system.
     */
    private void applyTexture(ParticleEmitter3D emitter) {
        var active = emitter.getParticles();
        for (int i = 0; i < active.size(); i++)
            active.get(i).getModelInstance().getMaterial().setTexture(particleTexture);
    }

    /** Faces the shared XY quad toward the camera after physical pose synchronization. */
    void faceCamera(Vector3f forward, Vector3f up) {
        facing.rotationTo(0, 0, 1, -forward.x, -forward.y, -forward.z);
        faceCamera(debris);
        faceCamera(flares);
    }

    /** Configures sprite-facing behavior for the selected particle emitter. */
    private void faceCamera(ParticleEmitter3D emitter) {
        var active = emitter.getParticles();
        for (int i = 0; i < active.size(); i++)
            active.get(i).getModelInstance().setRotation(facing);
    }

    /** Emits the appropriate short-lived particles at a copied gameplay hit position and normal. */
    void impact(FpsArenaWorld.Impact hit) {
        origin.set(hit.position()).fma(.08f, hit.normal());
        direction.set(hit.normal());
        burstSpeed = hit.explosion() ? 7 : 3;
        debris.burst(hit.explosion() ? 36 : 10);
    }

    /**
     * Handles the firing-effect hook without allocating a flash or stationary light. This arena
     * deliberately attaches visible firing effects to moving impact debris instead. The supplied
     * positions and ignored body are reserved for a custom muzzle effect.
     */
    void muzzle(Vector3f eye, Vector3f desiredPosition, RigidBody3D ignoredBody) {
        // Shooting has no stationary light or flash particle. Impact debris owns its lights.
    }

    /**
     * Launches a luminous flare along the supplied aim direction while excluding the player body
     * from placement queries.
     */
    boolean flare(Vector3f eye, Vector3f forward, RigidBody3D ignoredBody) {
        if (flares.getParticleCount() == flares.getCapacity()) return false;
        direction.set(forward).normalize();
        return placeOrigin(eye, .65f, .085f, ignoredBody) && flares.burst(1) == 1;
    }

    /**
     * Clamps the effect origin before the nearest blocking surface, with an explicit world-space
     * clearance.
     */
    private boolean placeOrigin(
            Vector3f eye, float offset, float clearance, RigidBody3D ignoredBody) {
        launchRay.oX = eye.x;
        launchRay.oY = eye.y;
        launchRay.oZ = eye.z;
        launchRay.dX = direction.x;
        launchRay.dY = direction.y;
        launchRay.dZ = direction.z;
        // Leave the sphere radius and a small gap in front of the surface.
        // CCD cannot repair a body that was initially born inside or beyond a wall.
        var hit = world.raycast(launchRay, offset + clearance, ignoredBody);
        if (hit != null) {
            float approach = -hit.normal().dot(direction);
            if (approach <= 0) return false;
            offset = Math.min(offset, hit.distance() - clearance / approach);
            if (offset <= 0) return false;
        }
        origin.set(eye).fma(offset, direction);
        return true;
    }

    /**
     * Advances bounded emitters by elapsed seconds and refreshes flare appearance and shadow
     * assignments.
     *
     * @param delta elapsed time in seconds
     */
    void update(float delta) {
        if (!physical) {
            debris.update(delta);
            flares.update(delta);
        }
        updateAppearance(debris, .005f, .7f);
        updateAppearance(flares, .008f, 8);
        updateFlareShadows();
    }

    /** Caps costly point-light shadows; ordinary debris keeps its inexpensive local light. */
    void setShadowBudget(int budget) {
        if (budget < 0 || budget > 4)
            throw new IllegalArgumentException("Shadow budget must be between 0 and 4");
        shadowBudget = budget;
        updateFlareShadows();
    }

    /**
     * Assigns the configured shadow budget to live flares and disables shadows on all remaining
     * attached lights.
     */
    private void updateFlareShadows() {
        var active = flares.getParticles();
        for (int i = 0; i < active.size(); i++) active.get(i).getLight().setCastsShadows(false);
        if (!lights) return;
        // At most 12 flares and four slots: no sorting, temporary lists or per-frame objects.
        for (int slot = 0; slot < shadowBudget; slot++) {
            Particle3D best = null;
            float strongest = .01f;
            for (int i = 0; i < active.size(); i++) {
                var candidate = active.get(i);
                var light = candidate.getLight();
                if (!light.isCastsShadows() && light.getIntensity() > strongest) {
                    strongest = light.getIntensity();
                    best = candidate;
                }
            }
            if (best == null) break;
            best.getLight().setCastsShadows(true);
        }
    }

    /** Returns the number of live particle lights currently assigned a shadow slot. */
    int shadowLightCount() {
        int count = 0;
        var active = flares.getParticles();
        for (int i = 0; i < active.size(); i++)
            if (active.get(i).getLight().isCastsShadows()) count++;
        return count;
    }

    /** Refreshes particle emission and attached-light power as existing particles age. */
    private void updateAppearance(ParticleEmitter3D emitter, float emission, float power) {
        var active = emitter.getParticles();
        for (int i = 0; i < active.size(); i++) {
            var particle = active.get(i);
            float progress = particle.getProgress();
            float fade = Math.max(0, 1 - progress * progress * (3 - 2 * progress));
            var material = particle.getModelInstance().getMaterial();
            material.getTint().a(.48f * fade);
            material.setEmissionStrength(emission * fade);
            particle.setLightEnabled(lights);
            particle.getLight().setIntensity(power * lightGain * fade);
        }
    }

    /** Returns the total live particle count across the effect emitters. */
    int particleCount() {
        return debris.getParticleCount() + flares.getParticleCount();
    }

    /** Returns the live flare count, excluding impact debris. */
    int flareCount() {
        return flares.getParticleCount();
    }

    /** Returns the number of live lights owned by the active effect particles. */
    int attachedLightCount() {
        return lights ? particleCount() : 0;
    }

    /**
     * Releases resources owned by this component. Call after dependent scene instances or emitters
     * have stopped using them.
     */
    @Override
    public void close() {
        closeEmitters();
        if (particleTexture != null) {
            particleTexture.dispose();
            particleTexture = null;
        }
    }

    /**
     * Closes all allocated emitters and removes their particles, physics bodies and attached
     * lights.
     */
    private void closeEmitters() {
        if (debris != null) {
            debris.close();
            debris = null;
        }
        if (flares != null) {
            flares.close();
            flares = null;
        }
    }
}
