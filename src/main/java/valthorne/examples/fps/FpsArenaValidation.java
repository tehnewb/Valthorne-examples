// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.fps;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.Rayf;

import valthorne.graphics.model.ModelBuilder3D;
import valthorne.graphics.model.ModelInstance3D;
import valthorne.graphics.model.Scene3D;
import valthorne.math.physics.BodySettings3D;
import valthorne.math.physics.CollisionShape3D;
import valthorne.math.physics.MotionType3D;
import valthorne.math.physics.PhysicsWorld3D;
import valthorne.math.physics.RigidBody3D;

import java.util.ArrayList;
import java.util.List;

/** Deterministic native gameplay checks; no window, graphics context or asset loading. */
public final class FpsArenaValidation {
    private static final float DT = 1f / 60;
    private static int checks;
    private static float walkDistance, sprintDistance, diagonalDistance, jumpHeight;

    /** Prevents construction of the standalone gameplay validation entry point. */
    private FpsArenaValidation() {}

    /**
     * Runs deterministic native gameplay and ownership checks without creating a window; prints the
     * check count and movement metrics.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) {
        movementJumpAndWallCollision();
        hitscanReloadAndOcclusion();
        grenadesResetAndOwnership();
        damageAndWaveProgression();
        lightEffectsStayBeforeWalls();
        movingParticlesGlowFadeAndOwnTheirLights();
        System.out.printf(
                java.util.Locale.ROOT,
                "FPS_GAMEPLAY_VALIDATED checks=%d walk=%.3f sprint=%.3f diagonal=%.3f jump=%.3f%n",
                checks,
                walkDistance,
                sprintDistance,
                diagonalDistance,
                jumpHeight);
    }

    /**
     * Creates a minimal isolated gameplay world for one scenario, using the supplied borrowed
     * scene.
     */
    private static FpsArenaWorld isolated(Scene3D scene) {
        FpsArenaWorld game = new FpsArenaWorld(scene, null, impact -> {});
        game.setWavesEnabled(false);
        game.clearDrones();
        return game;
    }

    /** Enforces the local validation invariant and fails immediately when it is violated. */
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /**
     * Asserts that the actual scalar is within the stated numeric tolerance and includes the
     * scenario label on failure.
     */
    private static void near(float expected, float actual, float tolerance, String message) {
        check(
                Float.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                message + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    /** Advances a fixed number of simulation ticks with explicit movement and sprint input. */
    private static void ticks(
            FpsArenaWorld game, int count, float forward, float strafe, boolean sprint) {
        for (int i = 0; i < count; i++) game.update(DT, forward, strafe, sprint, false, 0);
        check(
                game.getPlayerBody().getPosition().isFinite(),
                "player remains finite after simulation");
    }

    /**
     * Moves the player to the requested horizontal position and settles the body for the next
     * scenario.
     */
    private static void placePlayer(FpsArenaWorld game, float x, float y) {
        game.getPlayerBody().setTransform(new Vector3f(x, y, .90f), new Quaternionf());
        game.getPlayerBody().setLinearVelocity(new Vector3f());
        ticks(game, 45, 0, 0, false);
        Vector3f position = game.getPlayerBody().getPosition();
        var support =
                game.getPhysics()
                        .raycast(
                                new Rayf(position.x, position.y, position.z, 0, 0, -1),
                                FpsArenaWorld.PLAYER_HEIGHT / 2 + .14f,
                                game.getPlayerBody());
        check(
                game.isGrounded(),
                "player is grounded on the entry strip: position="
                        + position
                        + ", velocity="
                        + game.getPlayerBody().getLinearVelocity()
                        + ", support="
                        + support);
    }

    /**
     * Verifies walking, sprinting, diagonal speed, jumping and collision against blocking geometry.
     */
    private static void movementJumpAndWallCollision() {
        try (FpsArenaWorld game = isolated(new Scene3D())) {
            placePlayer(game, 0, -14);
            Vector3f start = game.getPlayerBody().getPosition();
            ticks(game, 45, 1, 0, false);
            walkDistance = game.getPlayerBody().getPosition().distance(start);
            check(
                    walkDistance > 2.6f && walkDistance < 3.5f,
                    "walking moves at the intended speed");

            placePlayer(game, 0, -14);
            start = game.getPlayerBody().getPosition();
            ticks(game, 45, 1, 0, true);
            sprintDistance = game.getPlayerBody().getPosition().distance(start);
            check(
                    sprintDistance > walkDistance * 1.4f,
                    "sprinting is meaningfully faster than walking");

            placePlayer(game, 0, -14);
            start = game.getPlayerBody().getPosition();
            ticks(game, 45, 1, 1, false);
            diagonalDistance = game.getPlayerBody().getPosition().distance(start);
            near(walkDistance, diagonalDistance, .12f, "diagonal input is normalized");
            check(
                    game.getPlayerBody().getPosition().x > 1,
                    "positive strafe moves right at yaw zero");

            placePlayer(game, 0, -14);
            float standing = game.getPlayerBody().getPosition().z;
            game.update(DT, 0, 0, false, true, 0);
            float peak = standing;
            for (int i = 0; i < 120; i++) {
                game.update(DT, 0, 0, false, false, 0);
                peak = Math.max(peak, game.getPlayerBody().getPosition().z);
            }
            jumpHeight = peak - standing;
            check(jumpHeight > 1 && jumpHeight < 2.2f, "jump reaches a useful height");
            check(game.isGrounded(), "jump lands back on the floor");
            near(
                    standing,
                    game.getPlayerBody().getPosition().z,
                    .06f,
                    "landing restores standing height");

            placePlayer(game, 0, -16);
            ticks(game, 180, -1, 0, true);
            near(
                    -17.36f,
                    game.getPlayerBody().getPosition().y,
                    .10f,
                    "south wall blocks sustained sprinting");
            near(1, Math.abs(game.getPlayerBody().getRotation().w), 1e-5f, "capsule stays upright");
        }
    }

    /** Aims at the supplied body and fires through the normal gameplay query path. */
    private static void aimAndFire(FpsArenaWorld game, RigidBody3D target) {
        Vector3f eye = game.eyePosition(new Vector3f());
        Vector3f direction = target.getPosition().sub(eye).normalize();
        game.fire(eye, direction);
    }

    /** Checks damage, ammunition/reload timing and that blocking geometry occludes hitscan fire. */
    private static void hitscanReloadAndOcclusion() {
        try (FpsArenaWorld game = isolated(new Scene3D())) {
            placePlayer(game, 0, -12);
            int magazine = game.getAmmo(), reserve = game.getReserve();
            RigidBody3D target = game.spawnDrone(0, -8, 1.7f);
            float health = game.getDroneHealth(target);
            aimAndFire(game, target);
            check(game.getAmmo() == magazine - 1, "shooting consumes one cartridge");
            check(game.getDroneHealth(target) < health, "hitscan damages the aimed target");
            int acceptedAmmo = game.getAmmo();
            game.fire(game.eyePosition(new Vector3f()), new Vector3f(0, 0, 1));
            check(
                    game.getAmmo() == acceptedAmmo,
                    "fire cooldown rejects a duplicate shot in one tick");
            game.reload();
            check(game.isReloading(), "manual reload starts for a partial magazine");
            game.fire(game.eyePosition(new Vector3f()), new Vector3f(0, 0, 1));
            check(game.getAmmo() == acceptedAmmo, "firing is blocked while reloading");
            for (int i = 0; i < 240 && game.isReloading(); i++) ticks(game, 1, 0, 0, false);
            check(!game.isReloading(), "reload finishes within four seconds");
            check(
                    game.getAmmo() == magazine && game.getReserve() == reserve - 1,
                    "reload conserves ammunition between reserve and magazine");

            game.clearDrones();
            int totalBeforeEmptying = game.getAmmo() + game.getReserve();
            for (int i = 0; i < magazine * 2 && game.getAmmo() > 0; i++) {
                game.fire(game.eyePosition(new Vector3f()), new Vector3f(0, 0, 1));
                if (game.getAmmo() == 0) break;
                ticks(game, 15, 0, 0, false);
            }
            check(game.getAmmo() == 0, "sustained shooting can empty the magazine");
            game.fire(game.eyePosition(new Vector3f()), new Vector3f(0, 0, 1));
            check(game.getAmmo() == 0, "empty magazine never produces negative ammo");
            game.reload();
            for (int i = 0; i < 240 && game.isReloading(); i++) ticks(game, 1, 0, 0, false);
            check(game.getAmmo() > 0 && !game.isReloading(), "an empty magazine can be reloaded");
            check(
                    game.getAmmo() + game.getReserve() == totalBeforeEmptying - magazine,
                    "empty reload conserves all remaining cartridges");
            placePlayer(game, 0, -16);
            RigidBody3D behindWall = game.spawnDrone(0, -19, 1.7f);
            float protectedHealth = game.getDroneHealth(behindWall);
            aimAndFire(game, behindWall);
            near(
                    protectedHealth,
                    game.getDroneHealth(behindWall),
                    0,
                    "solid wall blocks hitscan damage");
        }
    }

    /**
     * Checks grenade lifetime and damage, then verifies reset/close removes owned bodies and
     * visuals.
     */
    private static void grenadesResetAndOwnership() {
        Scene3D scene = new Scene3D();
        ModelInstance3D unrelated = new ModelInstance3D().setModel(ModelBuilder3D.box(1, 1, 1));
        scene.add(unrelated);
        FpsArenaWorld game = isolated(scene);
        PhysicsWorld3D closedWorld;
        try {
            placePlayer(game, 0, -12);
            int grenades = game.getGrenadesRemaining();
            game.throwGrenade(
                    game.eyePosition(new Vector3f()), new Vector3f(0, 1, .2f).normalize());
            check(
                    game.getGrenadeCount() == 1 && game.getGrenadesRemaining() == grenades - 1,
                    "grenade launch creates a body and consumes inventory");
            RigidBody3D grenade = game.getGrenadeBodies().getFirst();
            for (int i = 0; i < 360 && game.getGrenadeCount() > 0; i++) ticks(game, 1, 0, 0, false);
            check(
                    game.getGrenadeCount() == 0 && grenade.isDestroyed(),
                    "grenade fuse expires and releases its body");

            game.throwGrenade(
                    game.eyePosition(new Vector3f()), new Vector3f(0, 1, .2f).normalize());
            List<RigidBody3D> oldBodies = new ArrayList<>(game.getGrenadeBodies());
            oldBodies.addAll(game.getDynamicPropBodies());
            oldBodies.add(game.getPlayerBody());
            PhysicsWorld3D oldWorld = game.getPhysics();
            game.reset();
            check(oldWorld.isClosed(), "reset closes the previous physics world");
            check(
                    oldBodies.stream().allMatch(RigidBody3D::isDestroyed),
                    "reset releases all old native bodies");
            check(
                    scene.getRenderables().contains(unrelated),
                    "reset preserves caller-owned scene membership");
            check(
                    game.getGrenadeCount() == 0 && game.getGrenadesRemaining() == grenades,
                    "reset clears active grenades and restores inventory");
            check(
                    game.getPhysics() != oldWorld && !game.getPhysics().isClosed(),
                    "reset creates a live replacement world");
            closedWorld = game.getPhysics();
        } finally {
            game.close();
        }
        check(closedWorld.isClosed(), "close releases the final physics world");
        check(
                scene.getRenderables().equals(List.of(unrelated)),
                "close preserves only the caller-owned model");
        game.close();
    }

    /** Checks player damage, enemy removal, score changes and the wave transition rules. */
    private static void damageAndWaveProgression() {
        try (FpsArenaWorld game = isolated(new Scene3D())) {
            placePlayer(game, 0, -12);
            RigidBody3D drone = game.spawnDrone(0, -8, 1.7f);
            int oldScore = game.getScore(), oldKills = game.getKills(), oldWave = game.getWave();
            for (int i = 0; i < 12 && !drone.isDestroyed(); i++) {
                aimAndFire(game, drone);
                ticks(game, 15, 0, 0, false);
            }
            check(drone.isDestroyed(), "repeated hits defeat a drone");
            check(
                    game.getScore() > oldScore && game.getKills() == oldKills + 1,
                    "defeating a drone awards score and one kill");
            game.setWavesEnabled(true);
            for (int i = 0; i < 600 && game.getWave() <= oldWave; i++) ticks(game, 1, 0, 0, false);
            check(
                    game.getWave() > oldWave && game.getEnemies() > 0,
                    "cleared wave advances and spawns new enemies");
            game.setWavesEnabled(false);
            game.clearDrones();
            game.spawnDrone(0, -8, 1.7f);
            float healthy = game.getHealth();
            for (int i = 0; i < 600 && game.getHealth() >= healthy; i++)
                ticks(game, 1, 0, 0, false);
            check(game.getHealth() < healthy, "enemy attacks damage the player");
            for (int i = 0; i < 2400 && !game.isDead(); i++) ticks(game, 1, 0, 0, false);
            check(game.isDead(), "continued enemy attacks can end the round");
            int finalAmmo = game.getAmmo();
            game.fire(game.eyePosition(new Vector3f()), new Vector3f(0, 1, 0));
            check(game.getAmmo() == finalAmmo, "dead player cannot keep shooting");
            game.reset();
            check(
                    !game.isDead() && game.getHealth() > 0 && game.getKills() == 0,
                    "reset starts a living round with fresh score state");
        }
    }

    /**
     * Verifies that firing adds no stationary flash and that flare origins stay in front of walls.
     */
    private static void lightEffectsStayBeforeWalls() {
        for (boolean physical : new boolean[] {false, true}) {
            Scene3D scene = new Scene3D();
            try (PhysicsWorld3D world = new PhysicsWorld3D();
                    FpsArenaEffects effects = new FpsArenaEffects(scene, world, physical, true)) {
                RigidBody3D player =
                        world.createBody(
                                new BodySettings3D(
                                                CollisionShape3D.capsule(.34f, 1.75f),
                                                MotionType3D.DYNAMIC)
                                        .setPosition(0, 0, .9f));
                RigidBody3D wall =
                        world.createBody(
                                new BodySettings3D(
                                                CollisionShape3D.box(4, .2f, 4),
                                                MotionType3D.STATIC)
                                        .setPosition(0, .5f, 2));
                Vector3f eye = new Vector3f(0, 0, 1.7f), forward = new Vector3f(0, 1, 0);
                check(
                        effects.flare(eye, forward, player),
                        "near-wall flare finds a safe origin, physical=" + physical);
                Vector3f point = scene.getLights().getFirst().getPosition();
                near(
                        .315f,
                        point.y,
                        .001f,
                        "flare birth leaves radius plus clearance before the wall");
                check(
                        point.y > 0 && point.y + .075f < .4f,
                        "flare body starts entirely on the visible side");
                check(
                        effects.flareCount() == 1 && scene.getLights().size() == 1,
                        "accepted flare creates exactly one mesh and attached light");
                effects.rebuild();
                check(scene.getLights().isEmpty(), "rebuild removes the previous flare light");

                effects.muzzle(eye, new Vector3f(.24f, 1, 1.5f), player);
                check(
                        scene.getLights().isEmpty() && effects.particleCount() == 0,
                        "shooting creates no stationary muzzle light or flash particle");

                // This surface is closer than either effect's sphere radius.
                wall.setTransform(new Vector3f(0, .12f, 2), new Quaternionf());
                check(!effects.flare(eye, forward, player), "blocked flare launch is rejected");
                effects.muzzle(eye, new Vector3f(0, 1, 1.7f), player);
                check(
                        effects.particleCount() == 0 && scene.getLights().isEmpty(),
                        "rejected flare and muzzle leave no particle or light membership");
                wall.close();

                for (int i = 0; i < 12; i++) {
                    eye.x = i * .3f;
                    check(effects.flare(eye, forward, player), "unobstructed flare fits capacity");
                    point = scene.getLights().getLast().getPosition();
                    near(.65f, point.y, 1e-5f, "unobstructed flare retains normal launch distance");
                }
                check(!effects.flare(eye, forward, player), "full flare pool rejects launch");
                check(
                        effects.flareCount() == 12 && scene.getLights().size() == 12,
                        "rejected full-pool launch preserves the bounded light count");
                effects.update(0);
                check(
                        effects.shadowLightCount() == 2,
                        "default shadow budget selects only two flare lights");
                effects.setShadowBudget(4);
                check(
                        effects.shadowLightCount() == 4,
                        "higher quality remains capped at four shadow lights");
                effects.lights = false;
                effects.update(0);
                check(
                        effects.shadowLightCount() == 0 && scene.getLights().isEmpty(),
                        "disabled lights release all shadow slots");
                effects.lights = true;
                effects.update(0);
                check(
                        effects.shadowLightCount() == 4,
                        "re-enabled lights restore the selected shadow budget");
                effects.setShadowBudget(0);
                check(
                        effects.shadowLightCount() == 0 && scene.getLights().size() == 12,
                        "shadow toggle preserves all particle lights");
                effects.setShadowBudget(2);
                effects.lightGain = 0;
                effects.update(0);
                check(effects.shadowLightCount() == 0, "zero power never consumes a shadow slot");
                effects.close();
                check(
                        scene.getLights().isEmpty() && scene.getRenderables().isEmpty(),
                        "closing effects releases all scene memberships");
                check(
                        world.getBodyCount() == 1,
                        "closing effects retains only the borrowed player body");
            }
        }
    }

    /**
     * Verifies particle motion, emissive fade and complete removal of owned light and physics
     * resources.
     */
    private static void movingParticlesGlowFadeAndOwnTheirLights() {
        for (boolean physical : new boolean[] {false, true}) {
            Scene3D scene = new Scene3D();
            try (PhysicsWorld3D world = new PhysicsWorld3D();
                    FpsArenaEffects effects = new FpsArenaEffects(scene, world, physical, true)) {
                effects.impact(
                        new FpsArenaWorld.Impact(
                                new Vector3f(0, 0, 2), new Vector3f(0, 0, 1), false));
                check(
                        effects.particleCount() == 10,
                        "impact creates moving debris without a stationary flash");
                check(
                        scene.getLights().size() == effects.particleCount(),
                        "each moving particle owns exactly one light");
                var mesh = (ModelInstance3D) scene.getRenderables().getFirst();
                var material = mesh.getMaterial();
                check(
                        mesh.getModel().getTriangleCount() == 2,
                        "soft particles share a minimal camera-facing quad");
                var nextMaterial = ((ModelInstance3D) scene.getRenderables().get(1)).getMaterial();
                var light = scene.getLights().getFirst();
                check(
                        material != nextMaterial,
                        "particle materials fade independently and are retained by their pools");
                check(
                        material.getRenderPass()
                                        == valthorne.graphics.model.RenderPass3D.TRANSLUCENT
                                && material.getTint().a() > 0
                                && material.getTint().a() < 1,
                        "moving particles use actual partial alpha");
                check(
                        material.getEmissionStrength() > 0 && !material.isEmissionLightEnabled(),
                        "visible glow uses the explicit particle light without an implicit"
                                + " duplicate");
                check(
                        !light.isCastsShadows() && light.getRange() <= 5,
                        "moving lights use bounded ranges without shadow maps");
                Vector3f start = new Vector3f(light.getPosition());
                float alpha = material.getTint().a(),
                        glow = material.getEmissionStrength(),
                        power = light.getIntensity();
                for (int i = 0; i < 30; i++) {
                    if (physical) world.step();
                    effects.update(DT);
                }
                check(
                        light.getPosition().distance(start) > .1f,
                        "particle light moves with its visual body");
                near(
                        0,
                        mesh.getPosition().distance(light.getPosition()),
                        1e-5f,
                        "light and mesh retain the same center");
                Vector3f viewDirection = new Vector3f(.3f, .8f, -.4f).normalize();
                effects.faceCamera(viewDirection, new Vector3f(0, 0, 1));
                Vector3f normal =
                        mesh.getWorldTransform(new org.joml.Matrix4f())
                                .transformDirection(new Vector3f(0, 0, 1))
                                .normalize();
                near(
                        -1,
                        normal.dot(viewDirection),
                        1e-5f,
                        "fleck faces the camera after Jolt synchronizes its body");
                near(
                        0,
                        mesh.getPosition().distance(light.getPosition()),
                        1e-5f,
                        "billboarding leaves the light at its physical center");
                check(
                        material.getTint().a() < alpha
                                && material.getEmissionStrength() < glow
                                && light.getIntensity() < power,
                        "opacity, visible glow and local illumination all fade over lifetime");
                float pausedAlpha = material.getTint().a(),
                        pausedGlow = material.getEmissionStrength();
                effects.lights = false;
                effects.update(0);
                check(
                        scene.getLights().isEmpty(),
                        "paused lighting toggle removes all attached scene lights");
                near(pausedAlpha, material.getTint().a(), 0, "paused update preserves opacity");
                near(
                        pausedGlow,
                        material.getEmissionStrength(),
                        0,
                        "paused update preserves visible glow");
                effects.lights = true;
                effects.update(0);
                check(
                        scene.getLights().size() == effects.particleCount(),
                        "reenabling restores each moving light");
                for (int i = 0; i < 120; i++) {
                    if (physical) world.step();
                    effects.update(DT);
                }
                check(
                        effects.particleCount() == 0
                                && scene.getLights().isEmpty()
                                && scene.getRenderables().isEmpty(),
                        "expired glowing particles release meshes and lights");
                check(world.getBodyCount() == 0, "expired glowing particles release native bodies");
                effects.impact(
                        new FpsArenaWorld.Impact(
                                new Vector3f(0, 0, 2), new Vector3f(0, 0, 1), false));
                check(
                        scene.getLights().size() == 10,
                        "pooled respawn restores all per-particle lights");
                for (var renderable : scene.getRenderables()) {
                    var fresh = ((ModelInstance3D) renderable).getMaterial();
                    near(alpha, fresh.getTint().a(), 0, "pooled respawn resets initial alpha");
                    near(
                            glow,
                            fresh.getEmissionStrength(),
                            0,
                            "pooled respawn resets initial glow");
                }
            }
            check(scene.getLights().isEmpty(), "closing effects clears any final moving lights");
        }
    }
}
