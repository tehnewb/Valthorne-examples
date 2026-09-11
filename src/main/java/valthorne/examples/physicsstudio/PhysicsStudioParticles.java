// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.physicsstudio;

import valthorne.graphics.Color;
import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ModelBuilder3D;
import valthorne.graphics.model.Scene3D;
import valthorne.graphics.particle.ParticleEmitter3D;
import valthorne.math.physics.BodySettings3D;
import valthorne.math.physics.CollisionShape3D;
import valthorne.math.physics.MotionType3D;
import valthorne.math.physics.PhysicsWorld3D;

import java.util.Random;

/** A bounded fountain sharing one mesh and four materials; the studio owns its world. */
final class PhysicsStudioParticles implements AutoCloseable {
    static final int CAPACITY = 512;
    static final float RADIUS = .11f;
    private final Model3D mesh = ModelBuilder3D.sphere(RADIUS, 8, 4);
    private final CollisionShape3D shape = CollisionShape3D.sphere(RADIUS);
    private final Material3D[] materials = {
        material(.04f, .65f, .78f), material(.98f, .35f, .08f),
        material(.72f, .3f, .85f), material(.94f, .76f, .3f)
    };
    private final Random random = new Random(73);
    private ParticleEmitter3D emitter;
    private float currentGravity;
    float rate = 48, lifetime = 4, bounce = .65f;
    boolean physical = true, emitting = true;

    /**
     * Creates a new material descriptor with the requested appearance. Geometry may share the
     * descriptor while independent edits need a copy.
     */
    private static Material3D material(float r, float g, float b) {
        return new Material3D().setTint(new Color(r, g, b, 1)).setMetallic(.45f).setRoughness(.24f);
    }

    /**
     * Closes the previous emitter and binds a replacement to the borrowed world and scene with the
     * requested gravity.
     */
    void reset(PhysicsWorld3D world, Scene3D scene, float gravity) {
        close();
        currentGravity = gravity;
        random.setSeed(73);
        emitter =
                new ParticleEmitter3D(
                        CAPACITY,
                        particle -> {
                            particle.setLifetime(lifetime).setModel(mesh);
                            particle.getModelInstance()
                                    .setMaterial(materials[random.nextInt(materials.length)]);
                            float angle = random.nextFloat() * (float) (Math.PI * 2);
                            float radius = .6f * (float) Math.sqrt(random.nextFloat());
                            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                            particle.getPosition()
                                    .set(dx * radius, dy * radius, 2.3f + random.nextFloat() * .7f);
                            float spread = 1.8f + random.nextFloat() * 1.8f;
                            particle.getVelocity()
                                    .set(dx * spread, dy * spread, 4.5f + random.nextFloat() * 2);
                            if (!physical) particle.getAcceleration().set(0, 0, -currentGravity);
                        });
        if (physical)
            emitter.setPhysics(
                    world,
                    particle ->
                            new BodySettings3D(shape, MotionType3D.DYNAMIC)
                                    .setMass(.045f)
                                    .setFriction(.35f)
                                    .setRestitution(bounce)
                                    .setDamping(.025f, .05f)
                                    .setContinuousCollision(true));
        emitter.attach(scene);
        emitter.setEmissionRate(rate);
        emitter.setEmitting(emitting);
        emitter.burst(24);
    }

    /**
     * Advances emission using elapsed seconds and applies changes to the scene's gravity setting.
     *
     * @param delta elapsed time in seconds
     */
    void update(float delta, float gravity) {
        if (emitter == null || physical) return;
        currentGravity = gravity;
        for (var particle : emitter.getParticles()) particle.getAcceleration().z = -gravity;
        emitter.update(delta);
    }

    /** Applies an emission rate in particles per second to the retained emitter. */
    void setRate(float value) {
        rate = value;
        if (emitter != null) emitter.setEmissionRate(value);
    }

    /**
     * Enables or suspends new emission while existing particles continue through their lifetimes.
     */
    void setEmitting(boolean value) {
        emitting = value;
        if (emitter != null) emitter.setEmitting(value);
    }

    /** Emits one bounded burst using the fountain's configured particle initializer. */
    void burst() {
        if (emitter != null) emitter.burst(48);
    }

    /** Removes live particles and their owned bodies while keeping the emitter reusable. */
    void clear() {
        if (emitter != null) emitter.clear();
    }

    /** Returns the current number of live fountain particles. */
    int count() {
        return emitter == null ? 0 : emitter.getParticleCount();
    }

    /** Returns the borrowed emitter for inspection; its lifetime is managed by this component. */
    ParticleEmitter3D emitter() {
        return emitter;
    }

    /**
     * Releases resources owned by this component. Call after dependent scene instances or emitters
     * have stopped using them.
     */
    @Override
    public void close() {
        if (emitter != null) {
            emitter.close();
            emitter = null;
        }
    }
}
