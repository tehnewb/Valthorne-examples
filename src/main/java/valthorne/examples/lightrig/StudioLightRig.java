// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.lightrig;

import org.joml.Vector3f;

import valthorne.graphics.Color;
import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ModelBuilder3D;
import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.scene.Scene3D;
import valthorne.ui.nodes.nano.NanoButton;
import valthorne.ui.nodes.nano.NanoComboBox;
import valthorne.ui.nodes.nano.NanoContainer;
import valthorne.ui.nodes.nano.NanoLabel;
import valthorne.ui.nodes.nano.NanoPanel;
import valthorne.ui.nodes.nano.NanoScrollPanel;
import valthorne.ui.nodes.nano.NanoSlider;
import valthorne.ui.theme.ProfessionalTheme;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.DoubleConsumer;

/** Editable example light rig. The same bulb instances survive physics scene resets. */
public final class StudioLightRig {
    public static final int MAX_LIGHTS = 16;
    public static final Path SAVE_PATH = Path.of("build", "physics-studio", "rig.properties");
    private static final float LIMIT = 25, MIN_HEIGHT = .15f, MAX_POWER = 1200;
    private static final List<String> PALETTE_NAMES =
            List.of("Warm white", "Daylight", "Ice blue", "Mint", "Rose", "Violet", "Amber");
    private static final String[] FIELD_NAMES = {
        "Power", "X / meters", "Y / meters", "Height / meters", "Red", "Green", "Blue"
    };
    private static final Color[] PALETTE = {
        new Color(1, .9f, .72f, 1),
        new Color(1, 1, 1, 1),
        new Color(.4f, .64f, 1, 1),
        new Color(.28f, 1, .66f, 1),
        new Color(1, .3f, .5f, 1),
        new Color(.64f, .35f, 1, 1),
        new Color(1, .52f, .16f, 1)
    };

    public final NanoPanel panel = new NanoPanel();
    private final Model3D bulb = ModelBuilder3D.sphere(.125f, 16, 10);
    private final ArrayList<Light> lights = new ArrayList<>(MAX_LIGHTS);
    private final ArrayList<ModelInstance3D> models = new ArrayList<>(MAX_LIGHTS);
    private final List<ModelInstance3D> readOnlyModels = Collections.unmodifiableList(models);
    private final ArrayList<NanoSlider> sliders = new ArrayList<>(7);
    private final ArrayList<NanoLabel> sliderLabels = new ArrayList<>(7);
    private final float[] displayedValues = {
        Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN
    };
    private final NanoComboBox<Light> lightList = new NanoComboBox<>();
    private final NanoLabel heading = label("LIGHT RIG", 17);
    private final NanoLabel selection = label("SELECTED LIGHT", 12);
    private final NanoLabel status = label("Click a bulb to select it.", 12);
    private final NanoButton place, duplicate, delete, toggle;
    private final Color placementColor = PALETTE[0].copy();
    private Scene3D scene;
    private Light selected;
    private int nextId = 1;
    private boolean updating, placing, listDirty = true, enabled = true;
    private Runnable onChange = () -> {};

    /**
     * Mutable editor state for a retained emissive light model; changes are applied through the
     * owning rig.
     */
    private static final class Light {
        final String name;
        final ModelInstance3D model;
        final Color color;
        float power;
        boolean enabled;

        /**
         * Binds the editable light name and retained model to its color, power and enabled state.
         */
        Light(String name, ModelInstance3D model, Color color, float power, boolean enabled) {
            this.name = name;
            this.model = model;
            this.color = color.copy();
            this.power = power;
            this.enabled = enabled;
        }
    }

    /**
     * Creates a retained light editor using the supplied theme colors and the default three-light
     * arrangement.
     */
    public StudioLightRig(ProfessionalTheme theme) {
        this(Objects.requireNonNull(theme, "theme").accent, theme.muted, theme.error);
    }

    /** Headless construction for isolated scene-state and persistence validation. */
    StudioLightRig() {
        this(new Color(0xFF76A5FF), new Color(0xFF9AAAC0), new Color(0xFFF87171));
    }

    /**
     * Creates a retained light editor using the supplied theme colors and the default three-light
     * arrangement.
     */
    private StudioLightRig(Color accent, Color muted, Color error) {
        panel.getLayout().absolute().column().width(280).height(810).padding(12).gap(7);
        heading.setStyle(NanoLabel.COLOR_KEY, accent);
        selection.setStyle(NanoLabel.COLOR_KEY, muted);
        status.setStyle(NanoLabel.COLOR_KEY, muted);
        panel.add(heading);
        lightList
                .formatter(light -> light.name + (light.enabled ? "" : " / off"))
                .onChange(
                        light -> {
                            if (!updating) select(light.model);
                        });
        lightList.getLayout().widthPercent(100).height(34).noShrink();
        panel.add(lightList);
        place = button("+ Place light", () -> setPlacing(!placing));
        duplicate = button("Duplicate", this::duplicateSelected);
        delete = button("Delete", this::deleteSelected);
        delete.setStyle(NanoButton.TEXT_COLOR_KEY, error);
        toggle = button("Switch off", this::toggleSelected);
        panel.add(row(place, duplicate));
        panel.add(row(toggle, delete));
        panel.add(selection);

        var scroll = new NanoScrollPanel().horizontal(false).horizontalBar(false);
        scroll.getLayout().widthPercent(100).height(0).grow().minHeight(0);
        var fields = new NanoContainer();
        fields.getLayout().column().widthPercent(100).gap(5).noShrink();
        scroll.setContent(fields);
        panel.add(scroll);
        fields.add(label("COLOR PRESET", 12));
        var palette =
                new NanoComboBox<String>()
                        .items(PALETTE_NAMES)
                        .selectedIndex(0)
                        .onChange(
                                value -> {
                                    int index = PALETTE_NAMES.indexOf(value);
                                    placementColor.set(PALETTE[index]);
                                    if (selected != null)
                                        setSelectedColor(
                                                placementColor.r(),
                                                placementColor.g(),
                                                placementColor.b());
                                    message("Placement color: " + value);
                                });
        palette.getLayout().widthPercent(100).height(32).noShrink();
        fields.add(palette);
        fields.add(label("INTENSITY & POSITION", 12));
        slider(fields, "Power", 0, MAX_POWER, value -> selected.power = (float) value);
        slider(
                fields,
                "X / meters",
                -LIMIT,
                LIMIT,
                value -> selected.model.getPosition().x = (float) value);
        slider(
                fields,
                "Y / meters",
                -LIMIT,
                LIMIT,
                value -> selected.model.getPosition().y = (float) value);
        slider(
                fields,
                "Height / meters",
                MIN_HEIGHT,
                LIMIT,
                value -> selected.model.getPosition().z = (float) value);
        fields.add(label("CUSTOM COLOR", 12));
        slider(
                fields,
                "Red",
                0,
                1,
                value ->
                        selected.color.set(
                                (float) value, selected.color.g(), selected.color.b(), 1));
        slider(
                fields,
                "Green",
                0,
                1,
                value ->
                        selected.color.set(
                                selected.color.r(), (float) value, selected.color.b(), 1));
        slider(
                fields,
                "Blue",
                0,
                1,
                value ->
                        selected.color.set(
                                selected.color.r(), selected.color.g(), (float) value, 1));
        sliders.get(4).setStyle(NanoSlider.FILL_COLOR_KEY, new Color(.88f, .3f, .35f, 1));
        sliders.get(5).setStyle(NanoSlider.FILL_COLOR_KEY, new Color(.25f, .74f, .46f, 1));
        sliders.get(6).setStyle(NanoSlider.FILL_COLOR_KEY, new Color(.3f, .56f, .98f, 1));
        var help =
                label(
                        "Drag bulb: move on floor plane\n"
                                + "Shift + drag: change height\n"
                                + "Esc: cancel placement",
                        12);
        help.setStyle(NanoLabel.COLOR_KEY, muted);
        help.getLayout().height(54);
        panel.add(help);
        panel.add(row(button("Save rig", this::save), button("Load rig", this::load)));
        status.getLayout().height(36);
        panel.add(status);

        append("Key", -5, -4, 8, PALETTE[0], 350, true);
        append("Fill", 6, -1, 6, new Color(.45f, .6f, 1, 1), 28, true);
        append("Rim", 0, 6, 8, new Color(.8f, .9f, 1, 1), 87.5f, true);
        selected = lights.getFirst();
        refresh();
    }

    /**
     * Creates a label for this demo with its local typography and sizing conventions; the returned
     * node is attached by the caller.
     */
    private static NanoLabel label(String text, float size) {
        var label = new NanoLabel(text);
        label.setStyle(NanoLabel.FONT_SIZE_KEY, size);
        label.getLayout().height(size + 6).noShrink();
        return label;
    }

    /**
     * Creates a UI button bound to the supplied action; the action executes through normal UI event
     * dispatch.
     */
    private static NanoButton button(String text, Runnable action) {
        var button = new NanoButton(text).action(value -> action.run());
        button.setStyle(NanoButton.FONT_SIZE_KEY, 13f);
        button.getLayout().height(32).noShrink();
        return button;
    }

    /** Creates a horizontal two-button editor row without taking ownership of the parent panel. */
    private static NanoContainer row(NanoButton first, NanoButton second) {
        var row = new NanoContainer();
        row.getLayout().row().height(32).widthPercent(100).gap(6).noShrink();
        first.getLayout().width(0).grow();
        second.getLayout().width(0).grow();
        row.add(first);
        row.add(second);
        return row;
    }

    /**
     * Builds a labeled numeric editor and connects value changes to the supplied callback; bounds
     * use the edited property's units.
     */
    private void slider(
            NanoContainer fields, String name, float min, float max, DoubleConsumer edit) {
        NanoLabel title = label(name, 12);
        var slider =
                new NanoSlider(min, max, min)
                        .action(
                                value -> {
                                    if (updating || selected == null) return;
                                    edit.accept(value.getValue());
                                    placementColor.set(selected.color);
                                    apply(selected);
                                    title.text(
                                            name
                                                    + String.format(
                                                            Locale.ROOT,
                                                            "   %.2f",
                                                            value.getValue()));
                                    onChange.run();
                                });
        slider.getLayout().height(20).widthPercent(100).noShrink();
        fields.add(title);
        fields.add(slider);
        sliders.add(slider);
        sliderLabels.add(title);
    }

    /** Reattaches retained bulbs after the caller rebuilds or replaces its scene. */
    public void attach(Scene3D target) {
        Objects.requireNonNull(target, "scene");
        if (scene != null && scene != target) for (Light light : lights) scene.remove(light.model);
        scene = target;
        for (Light light : lights) {
            if (!scene.getRenderables().contains(light.model)) scene.add(light.model);
        }
    }

    /**
     * Returns an unmodifiable borrowed view of the retained light models; the rig owns their list
     * and geometry.
     */
    public List<ModelInstance3D> models() {
        return readOnlyModels;
    }

    /** Returns the selected borrowed light model, or null when there is no selection. */
    public ModelInstance3D selected() {
        return selected == null ? null : selected.model;
    }

    /** Returns whether the next eligible scene click should place a light. */
    public boolean isPlacing() {
        return placing;
    }

    /** Returns the number of editable lights currently held by the rig. */
    public int size() {
        return lights.size();
    }

    /** Registers the callback used to invalidate rendering after rig edits. */
    public void setOnChange(Runnable listener) {
        onChange = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Selects the supplied model when it belongs to the rig; returns whether a matching light was
     * found.
     */
    public boolean select(ModelInstance3D model) {
        for (Light light : lights) {
            if (light.model == model) {
                selected = light;
                refresh();
                message(light.name + " selected. Drag to move.");
                return true;
            }
        }
        return false;
    }

    /** Enables or cancels interactive placement and updates the editor status. */
    public void setPlacing(boolean enabled) {
        placing = enabled && lights.size() < MAX_LIGHTS;
        place.text(placing ? "Cancel placement" : "+ Place light");
        message(
                enabled && !placing
                        ? "Maximum of 16 lights reached."
                        : placing
                                ? "Click the scene to place a light.\nEscape cancels."
                                : "Placement cancelled.");
    }

    /** Adds a selected light using the current palette color; returns null at capacity. */
    public ModelInstance3D add(float x, float y, float z) {
        if (lights.size() == MAX_LIGHTS) {
            message("Maximum of 16 lights reached.");
            return null;
        }
        selected = append(nextLightName(), x, y, z, placementColor, 80, true);
        placing = false;
        changed("Light placed. Drag to move it.");
        return selected.model;
    }

    /**
     * Moves the selected light to validated world coordinates and notifies the host; does nothing
     * without a selection.
     */
    public void moveSelected(float x, float y, float z) {
        if (selected == null) return;
        selected.model.setPosition(
                coordinate(x, -LIMIT), coordinate(y, -LIMIT), coordinate(z, MIN_HEIGHT));
        refresh();
        onChange.run();
    }

    /**
     * Applies validated nonnegative emission power to the selected light and refreshes its
     * visual/editor state.
     */
    public void setSelectedPower(float power) {
        if (selected == null) return;
        selected.power = checked(power, 0, MAX_POWER, "power");
        apply(selected);
        changed("Light power updated.");
    }

    /**
     * Applies finite RGB channel values to the selected light, updates its material and notifies
     * the host.
     */
    public void setSelectedColor(float red, float green, float blue) {
        checked(red, 0, 1, "red");
        checked(green, 0, 1, "green");
        checked(blue, 0, 1, "blue");
        placementColor.set(red, green, blue, 1);
        if (selected == null) return;
        selected.color.set(placementColor);
        apply(selected);
        changed("Light color updated.");
    }

    /** Creates an offset copy of the selected light if the bounded rig has capacity. */
    public void duplicateSelected() {
        if (selected == null || lights.size() == MAX_LIGHTS) return;
        Vector3f p = selected.model.getPosition();
        selected =
                append(
                        nextLightName(),
                        p.x() + .75f,
                        p.y(),
                        p.z(),
                        selected.color,
                        selected.power,
                        selected.enabled);
        changed("Light duplicated.");
    }

    /**
     * Removes the selected light from both scene and editor, then selects a remaining light when
     * available.
     */
    public void deleteSelected() {
        if (selected == null) return;
        int index = lights.indexOf(selected);
        if (scene != null) scene.remove(selected.model);
        models.remove(selected.model);
        lights.remove(index);
        listDirty = true;
        selected = lights.isEmpty() ? null : lights.get(Math.min(index, lights.size() - 1));
        changed("Light deleted.");
    }

    /** Toggles the selected light's enabled state without destroying its retained model. */
    public void toggleSelected() {
        if (selected == null) return;
        selected.enabled = !selected.enabled;
        apply(selected);
        changed(selected.enabled ? "Light switched on." : "Light switched off.");
    }

    /**
     * Finds the next unused display name so deleted or loaded light IDs do not create duplicates.
     */
    private String nextLightName() {
        while (true) {
            String candidate = "Light " + nextId++;
            if (nextId < 1) nextId = 1;
            boolean used = false;
            for (Light light : lights)
                if (light.name.equals(candidate)) {
                    used = true;
                    break;
                }
            if (!used) return candidate;
        }
    }

    /**
     * Adds one named, retained light and its scene instance using validated transform and
     * appearance values.
     */
    private Light append(
            String name, float x, float y, float z, Color color, float power, boolean enabled) {
        var model =
                new ModelInstance3D()
                        .setModel(bulb)
                        .setPosition(
                                coordinate(x, -LIMIT),
                                coordinate(y, -LIMIT),
                                coordinate(z, MIN_HEIGHT))
                        .setMaterial(new Material3D().setRoughness(.25f).setCastsShadow(false));
        Light light = new Light(name, model, color, power, enabled);
        apply(light);
        lights.add(light);
        models.add(model);
        listDirty = true;
        if (scene != null) scene.add(model);
        return light;
    }

    /** Synchronizes one light's model material with its color, power and enabled state. */
    private void apply(Light light) {
        light.model
                .getMaterial()
                .setTint(light.color)
                .setEmissive(light.color)
                .setEmissionStrength(enabled && light.enabled ? light.power : 0);
    }

    /** Enables or suppresses the complete rig without losing individual light settings. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (var light : lights) apply(light);
        message(enabled ? "Lighting enabled." : "Lighting disabled.");
        onChange.run();
    }

    /** Returns whether this rig currently emits light. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Refreshes editor state, reports the action and invokes the registered host invalidation
     * callback.
     */
    private void changed(String text) {
        refresh();
        message(text);
        onChange.run();
    }

    /** Updates the status label with user-visible feedback from the last editor operation. */
    private void message(String text) {
        status.text(text);
    }

    /** Synchronizes controls after mouse edits without invoking their callbacks. */
    public void refresh() {
        updating = true;
        try {
            heading.text("LIGHT RIG  /  " + lights.size() + " OF 16");
            if (listDirty) {
                lightList.items(lights);
                listDirty = false;
            }
            lightList.selectedIndex(selected == null ? -1 : lights.indexOf(selected));
            selection.text(
                    selected == null
                            ? "NO LIGHT SELECTED"
                            : selected.name.toUpperCase(Locale.ROOT)
                                    + (selected.enabled ? "  /  ON" : "  /  OFF"));
            place.text(placing ? "Cancel placement" : "+ Place light");
            duplicate.setEnabled(selected != null && lights.size() < MAX_LIGHTS);
            delete.setEnabled(selected != null);
            toggle.setEnabled(selected != null);
            toggle.text(selected != null && selected.enabled ? "Switch off" : "Switch on");
            for (NanoSlider slider : sliders) slider.setEnabled(selected != null);
            if (selected == null) return;
            Vector3f p = selected.model.getPosition();
            for (int i = 0; i < FIELD_NAMES.length; i++) {
                float value =
                        switch (i) {
                            case 0 -> selected.power;
                            case 1 -> p.x();
                            case 2 -> p.y();
                            case 3 -> p.z();
                            case 4 -> selected.color.r();
                            case 5 -> selected.color.g();
                            default -> selected.color.b();
                        };
                sliders.get(i).value(value);
                if (displayedValues[i] != value) {
                    displayedValues[i] = value;
                    sliderLabels
                            .get(i)
                            .text(FIELD_NAMES[i] + String.format(Locale.ROOT, "   %.2f", value));
                }
            }
        } finally {
            updating = false;
        }
    }

    /**
     * Writes a bounded versioned light rig using a sibling temporary file and replacement,
     * preserving the prior save if validation fails.
     */
    public void save() {
        save(SAVE_PATH);
    }

    /**
     * Writes a bounded versioned light rig using a sibling temporary file and replacement,
     * preserving the prior save if validation fails.
     */
    boolean save(Path destination) {
        Objects.requireNonNull(destination, "destination");
        Properties values = new Properties();
        values.setProperty("version", "1");
        values.setProperty("count", Integer.toString(lights.size()));
        values.setProperty(
                "selected", Integer.toString(selected == null ? -1 : lights.indexOf(selected)));
        for (int i = 0; i < lights.size(); i++) {
            Light light = lights.get(i);
            Vector3f p = light.model.getPosition();
            String prefix = "light." + i + ".";
            values.setProperty(prefix + "name", light.name);
            values.setProperty(prefix + "enabled", Boolean.toString(light.enabled));
            values.setProperty(
                    prefix + "values",
                    p.x()
                            + ","
                            + p.y()
                            + ","
                            + p.z()
                            + ","
                            + light.power
                            + ","
                            + light.color.r()
                            + ","
                            + light.color.g()
                            + ","
                            + light.color.b());
        }
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Valthorne Physics Studio light rig");
            }
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            message("Rig saved to physics-studio/rig.properties.");
            return true;
        } catch (IOException ex) {
            message("Could not save the light rig.");
            return false;
        }
    }

    /**
     * Validated intermediate persistence record, held separately until a complete file can replace
     * live state.
     */
    private record SavedLight(String name, float[] values, boolean enabled) {}

    /** Validates the whole saved rig before replacing any current light. */
    public void load() {
        load(SAVE_PATH);
    }

    /**
     * Parses and validates a bounded properties file before replacing live rig state; failures
     * leave existing lights intact.
     */
    boolean load(Path source) {
        Objects.requireNonNull(source, "source");
        ArrayList<SavedLight> saved;
        int selectedIndex;
        try {
            if (!Files.exists(source)) {
                message("No saved rig yet. Use Save rig first.");
                return false;
            }
            if (Files.size(source) > 32768) throw new IOException("Rig is too large");
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            if (!"1".equals(properties.getProperty("version")))
                throw new IllegalArgumentException("version");
            int count = Integer.parseInt(properties.getProperty("count"));
            if (count < 0 || count > MAX_LIGHTS) throw new IllegalArgumentException("count");
            selectedIndex = Integer.parseInt(properties.getProperty("selected", "-1"));
            if (selectedIndex < -1 || selectedIndex >= count)
                throw new IllegalArgumentException("selection");
            saved = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String prefix = "light." + i + ".";
                String name = properties.getProperty(prefix + "name", "Light " + (i + 1));
                if (name.isBlank()
                        || name.length() > 32
                        || name.indexOf('\n') >= 0
                        || name.indexOf('\r') >= 0) throw new IllegalArgumentException("name");
                String[] parts = properties.getProperty(prefix + "values", "").split(",", -1);
                if (parts.length != 7) throw new IllegalArgumentException("values");
                float[] v = new float[7];
                for (int j = 0; j < 7; j++) v[j] = Float.parseFloat(parts[j]);
                checked(v[0], -LIMIT, LIMIT, "x");
                checked(v[1], -LIMIT, LIMIT, "y");
                checked(v[2], MIN_HEIGHT, LIMIT, "height");
                checked(v[3], 0, MAX_POWER, "power");
                for (int j = 4; j < 7; j++) checked(v[j], 0, 1, "color");
                String enabled = properties.getProperty(prefix + "enabled", "true");
                if (!enabled.equals("true") && !enabled.equals("false"))
                    throw new IllegalArgumentException("enabled");
                saved.add(new SavedLight(name, v, Boolean.parseBoolean(enabled)));
            }
        } catch (IOException | IllegalArgumentException ex) {
            message("Could not load rig. Current lights kept.");
            return false;
        }
        if (scene != null) for (Light light : lights) scene.remove(light.model);
        lights.clear();
        models.clear();
        listDirty = true;
        for (SavedLight light : saved) {
            float[] v = light.values();
            append(
                    light.name(),
                    v[0],
                    v[1],
                    v[2],
                    new Color(v[4], v[5], v[6], 1),
                    v[3],
                    light.enabled());
        }
        selected = selectedIndex < 0 ? null : lights.get(selectedIndex);
        nextId = Math.max(nextId, saved.size() + 1);
        placing = false;
        // Callback failures must not be mislabeled as malformed files after a successful load.
        changed("Loaded " + saved.size() + " lights from saved rig.");
        return true;
    }

    /** Checks a finite position component against the supported world-coordinate bounds. */
    private static float coordinate(float value, float min) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Position must be finite");
        return Math.max(min, Math.min(LIMIT, value));
    }

    /**
     * Validates a finite scalar against inclusive bounds and names the invalid property in the
     * exception.
     */
    private static float checked(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
