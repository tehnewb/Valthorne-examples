// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lightrig;

import org.joml.Vector3f;

import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.scene.Scene3D;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Headless rig-state validation. Uses a unique temporary directory, never the user's saved rig. */
public final class StudioLightRigValidation {
    private static int checks;

    /**
     * Immutable copy of one light's world position and emission channels for persistence
     * comparisons.
     */
    private record Snapshot(float x, float y, float z, float power, float r, float g, float b) {
        /**
         * Copies one retained light's position and emission values into an immutable validation
         * snapshot.
         */
        Snapshot(ModelInstance3D light) {
            this(
                    light.getPosition().x(),
                    light.getPosition().y(),
                    light.getPosition().z(),
                    light.getMaterial().getEmissionStrength(),
                    light.getMaterial().getEmissive().r(),
                    light.getMaterial().getEmissive().g(),
                    light.getMaterial().getEmissive().b());
        }
    }

    /**
     * Runs isolated persistence, bounds and selection checks with temporary files, without a
     * graphics context or user-save mutation.
     *
     * @param args command-line options documented by the example guide
     */
    public static void main(String[] args) throws Exception {
        Path directory =
                Files.createTempDirectory("valthorne-light-rig-validation-").toAbsolutePath();
        Path saved = directory.resolve("roundtrip.properties");
        Path malformed = directory.resolve("malformed.properties");
        Path empty = directory.resolve("empty.properties");
        try {
            StudioLightRig rig = new StudioLightRig();
            Scene3D scene = new Scene3D();
            rig.attach(scene);
            check(rig.size() == 3, "three default lights");
            check(rig.selected() == rig.models().getFirst(), "key selected initially");
            near(350, rig.models().get(0).getMaterial().getEmissionStrength(), "key power");
            near(28, rig.models().get(1).getMaterial().getEmissionStrength(), "fill power");
            near(87.5f, rig.models().get(2).getMaterial().getEmissionStrength(), "rim power");
            rig.attach(scene);
            check(scene.getRenderables().size() == 3, "repeated attach does not duplicate lights");
            scene.clear();
            rig.attach(scene);
            check(scene.getRenderables().size() == 3, "lights survive scene reset");

            ModelInstance3D original = rig.selected();
            rig.moveSelected(3, -2, 4);
            rig.setSelectedPower(177);
            rig.setSelectedColor(.2f, .3f, .4f);
            rig.toggleSelected();
            near(0, original.getMaterial().getEmissionStrength(), "disabled light emits no light");
            rig.duplicateSelected();
            ModelInstance3D copy = rig.selected();
            check(copy != original && rig.size() == 4, "duplicate has independent identity");
            check(
                    copy.getMaterial() != original.getMaterial(),
                    "duplicate has independent material");
            near(3.75f, copy.getPosition().x(), "duplicate is offset");
            near(0, copy.getMaterial().getEmissionStrength(), "duplicate preserves disabled state");
            rig.toggleSelected();
            near(177, copy.getMaterial().getEmissionStrength(), "duplicate retains disabled power");
            rig.setSelectedColor(.8f, .1f, .6f);
            near(
                    .2f,
                    original.getMaterial().getEmissive().r(),
                    "duplicate color does not edit original");
            rig.select(original);
            rig.deleteSelected();
            check(
                    rig.size() == 3 && !scene.getRenderables().contains(original),
                    "delete removes scene instance");

            while (rig.size() < StudioLightRig.MAX_LIGHTS) rig.add(rig.size(), 0, 3);
            ModelInstance3D atCapacity = rig.selected();
            check(rig.add(0, 0, 3) == null, "placement stops at 16 lights");
            rig.duplicateSelected();
            check(rig.size() == 16 && rig.selected() == atCapacity, "duplicate respects capacity");
            rig.setPlacing(true);
            check(!rig.isPlacing(), "placement mode does not enable at capacity");
            rig.deleteSelected();
            rig.setPlacing(true);
            check(rig.isPlacing(), "deleting frees a placement slot");
            check(rig.add(1, 2, 3) != null && !rig.isPlacing(), "placing consumes mode");
            rig.moveSelected(-3, 4, 5);
            rig.setSelectedPower(93);
            rig.setSelectedColor(.7f, .2f, .8f);
            rig.toggleSelected();
            List<Snapshot> expected = snapshot(rig);
            int selectedIndex = rig.models().indexOf(rig.selected());
            check(rig.save(saved), "roundtrip save succeeds");
            rig.deleteSelected();
            rig.moveSelected(0, 0, 1);
            check(rig.load(saved), "roundtrip load succeeds");
            check(
                    snapshot(rig).equals(expected),
                    "roundtrip preserves every position, color and emission state");
            check(
                    rig.models().indexOf(rig.selected()) == selectedIndex,
                    "roundtrip preserves selection");
            near(
                    0,
                    rig.selected().getMaterial().getEmissionStrength(),
                    "loaded selection remains disabled");
            rig.toggleSelected();
            near(
                    93,
                    rig.selected().getMaterial().getEmissionStrength(),
                    "loaded disabled light retains power");
            rig.toggleSelected();
            check(scene.getRenderables().size() == 16, "load replaces scene instances");

            Scene3D replacement = new Scene3D();
            rig.attach(replacement);
            check(
                    scene.getRenderables().isEmpty() && replacement.getRenderables().size() == 16,
                    "attach transfers lights between scenes");
            List<ModelInstance3D> identities = new ArrayList<>(rig.models());
            ModelInstance3D selection = rig.selected();
            Properties baseline = new Properties();
            try (Reader reader = Files.newBufferedReader(saved, StandardCharsets.UTF_8)) {
                baseline.load(reader);
            }
            String[][] invalidFields = {
                {"version", "2"},
                {"count", "17"},
                {"count", "-1"},
                {"selected", "16"},
                {"light.15.values", "NaN,0,2,1,1,1,1"},
                {"light.15.values", "0,0,2,Infinity,1,1,1"},
                {"light.15.values", "0,0,2,-1,1,1,1"},
                {"light.15.values", "0,0,2,1,2,1,1"},
                {"light.15.values", "0,0,0,1,1,1,1"},
                {"light.15.values", "0,0,2,1,1,1,1,"},
                {"light.15.values", "0,0,2"},
                {"light.15.enabled", "sometimes"},
                {"light.15.name", "invalid\nname"}
            };
            for (String[] invalid : invalidFields) {
                Properties values = new Properties();
                values.putAll(baseline);
                values.setProperty(invalid[0], invalid[1]);
                try (Writer writer = Files.newBufferedWriter(malformed, StandardCharsets.UTF_8)) {
                    values.store(writer, "intentionally invalid test rig");
                }
                check(!rig.load(malformed), "malformed field rejected: " + invalid[0]);
                check(
                        rig.models().equals(identities)
                                && rig.selected() == selection
                                && snapshot(rig).equals(expected)
                                && replacement.getRenderables().size() == 16,
                        "malformed load leaves current rig untouched: " + invalid[0]);
            }
            check(!rig.load(directory.resolve("missing.properties")), "missing rig is handled");
            Files.writeString(malformed, "x".repeat(32769));
            check(
                    !rig.load(malformed) && rig.models().equals(identities),
                    "oversized rig is rejected without mutation");

            Vector3f position = new Vector3f(rig.selected().getPosition());
            expectInvalid(() -> rig.moveSelected(Float.NaN, 0, 2));
            check(rig.selected().getPosition().equals(position), "invalid move is atomic");
            Snapshot beforeInvalidColor = new Snapshot(rig.selected());
            expectInvalid(() -> rig.setSelectedColor(.2f, Float.NaN, .3f));
            check(
                    new Snapshot(rig.selected()).equals(beforeInvalidColor),
                    "invalid color is atomic");
            expectInvalid(() -> rig.setSelectedPower(Float.POSITIVE_INFINITY));

            // A saved sparse rig can contain a generated name beyond its current count.
            Properties collision = new Properties();
            collision.putAll(baseline);
            collision.setProperty("light.0.name", "Light 17");
            try (Writer writer = Files.newBufferedWriter(malformed, StandardCharsets.UTF_8)) {
                collision.store(writer, "name collision fixture");
            }
            check(rig.load(malformed), "sparse generated names can load");
            rig.deleteSelected();
            rig.add(0, 0, 3);
            check(rig.save(empty), "rig saves after adding past a sparse generated name");
            Properties renamed = new Properties();
            try (Reader reader = Files.newBufferedReader(empty, StandardCharsets.UTF_8)) {
                renamed.load(reader);
            }
            java.util.HashSet<String> names = new java.util.HashSet<>();
            for (int i = 0; i < rig.size(); i++)
                check(
                        names.add(renamed.getProperty("light." + i + ".name")),
                        "generated light names remain unique");

            while (rig.size() > 0) rig.deleteSelected();
            check(
                    rig.selected() == null && replacement.getRenderables().isEmpty(),
                    "all lights can be deleted");
            check(rig.save(empty), "empty rig saves");
            rig.add(0, 0, 3);
            check(rig.load(empty) && rig.size() == 0 && rig.selected() == null, "empty rig loads");
            check(rig.add(0, 0, 3) != null, "editing resumes after empty rig load");
            System.out.println(
                    "StudioLightRig validation passed: "
                            + checks
                            + " checks; no graphics context; temporary files only.");
        } finally {
            // Only these explicit files were created; no recursive cleanup or user rig access.
            Files.deleteIfExists(saved.resolveSibling(saved.getFileName() + ".tmp"));
            Files.deleteIfExists(empty.resolveSibling(empty.getFileName() + ".tmp"));
            Files.deleteIfExists(saved);
            Files.deleteIfExists(malformed);
            Files.deleteIfExists(empty);
            Files.deleteIfExists(directory);
        }
    }

    /**
     * Copies all model positions and light appearance values for comparison after a save/load or
     * rejected operation.
     */
    private static List<Snapshot> snapshot(StudioLightRig rig) {
        return rig.models().stream().map(Snapshot::new).toList();
    }

    /** Runs an operation that must reject its input and fails if it returns successfully. */
    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Invalid input was accepted");
    }

    /** Asserts an absolute error below 1e-6 and includes the scenario label on failure. */
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < 1e-6f, message + ": " + expected + " vs " + actual);
    }

    /** Enforces the local validation invariant and fails immediately when it is violated. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
