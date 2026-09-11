// SPDX-License-Identifier: Apache-2.0

package valthorne.examples.launcher;

import valthorne.examples.audio.AudioStudio;
import valthorne.examples.fps.FpsArena;
import valthorne.examples.lighting2d.Lighting2DExample;
import valthorne.examples.lightingstudio.LightingStudio;
import valthorne.examples.lightingstudio.PathTracingExample;
import valthorne.examples.physics.Physics3DExample;
import valthorne.examples.physicsstudio.PhysicsStudio;
import valthorne.examples.scene.Scene3DExample;
import valthorne.examples.starter.MinimalExample;
import valthorne.examples.ui.UIShowcase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Common command-line entry point for the standalone example collection.
 *
 * <p>Help and argument validation run before native initialization. Each selected application owns
 * its graphics context and runs on the process main thread; launch only one demo per process. The
 * Gradle tasks and generated distribution scripts supply native access and macOS first-thread
 * flags. Use {@code --list}, or {@code <example-id> --help}, without a graphics device.
 */
public final class ExampleLauncher {
    /** Description used by help, validation and platform selection, without loading a renderer. */
    private record Demo(
            String id, String title, String requirement, String options, boolean snapshotSmoke) {}

    /** Stable command names; the option grammar uses a trailing equals sign for valued options. */
    private static final List<Demo> DEMOS =
            List.of(
                    new Demo("starter", "Application lifecycle", "OpenGL 3.3", "--smoke", false),
                    new Demo(
                            "scene",
                            "Raster scene and picking",
                            "OpenGL 3.3",
                            "--smoke --snapshot=",
                            true),
                    new Demo(
                            "physics",
                            "Rigid-body playground",
                            "OpenGL 3.3 + Jolt",
                            "--smoke --snapshot= --benchmark --all-lights --stress-lights",
                            true),
                    new Demo(
                            "lighting2d",
                            "Cached 2D lighting",
                            "OpenGL 3.3",
                            "--smoke --snapshot= --benchmark --static --stress-lights",
                            true),
                    new Demo("ui", "UI component gallery", "OpenGL 3.3", "--smoke", false),
                    new Demo(
                            "audio",
                            "Spatial audio studio",
                            "OpenGL 3.3 + audio device",
                            "--smoke",
                            false),
                    new Demo(
                            "lighting-studio",
                            "Interactive lighting workbench",
                            "OpenGL 4.3",
                            "--smoke --benchmark --benchmark-motion --progressive --pathtracer"
                                    + " --filament-quality= --visual-validation=",
                            false),
                    new Demo(
                            "path-tracing",
                            "Progressive path tracing",
                            "OpenGL 4.3",
                            "--smoke --snapshot= --benchmark --2d --quality=",
                            true),
                    new Demo(
                            "physics-studio",
                            "Physics and lighting studio",
                            "Windows x64 + OpenGL 4.3 + Filament",
                            "--smoke --benchmark --scenario= --lights= --visual-particles"
                                    + " --particle-rate=",
                            false),
                    new Demo(
                            "fps",
                            "Playable first-person arena",
                            "Windows x64 + OpenGL 4.3 + Filament",
                            "--smoke --benchmark --visual-particles --no-particle-lights"
                                    + " --no-particle-shadows --four-particle-shadows",
                            false));

    /** Prevents construction of this process entry point. */
    private ExampleLauncher() {}

    /**
     * Selects a demo, validates its options, and starts it on the calling main thread.
     *
     * @param args demo ID followed by its documented options; no arguments prints the catalog
     * @throws Exception if the selected application cannot load a resource or initialize its
     *     runtime
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || List.of("--list", "--help", "-h").contains(args[0])) {
            printCatalog();
            return;
        }
        Demo demo =
                DEMOS.stream()
                        .filter(candidate -> candidate.id().equals(args[0]))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown example '" + args[0] + "'. Use --list."));
        List<String> options = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        if (options.contains("--help") || options.contains("-h")) {
            System.out.println(
                    demo.title()
                            + "\nRequirements: "
                            + demo.requirement()
                            + "\nOptions: "
                            + demo.options()
                            + "\nGuide: docs/"
                            + demo.id()
                            + ".md\n"
                            + "--smoke runs a bounded check; it still requires a working graphics"
                            + " driver.");
            return;
        }
        validateOptions(demo, options);
        requirePlatform(demo);
        if (demo.snapshotSmoke() && options.remove("--smoke")) {
            if (options.stream().noneMatch(option -> option.startsWith("--snapshot="))) {
                options.add("--snapshot=build/captures/" + demo.id() + ".png");
            }
        }
        String[] forwarded = options.toArray(String[]::new);
        switch (demo.id()) {
            case "starter" -> MinimalExample.main(forwarded);
            case "scene" -> Scene3DExample.main(forwarded);
            case "physics" -> Physics3DExample.main(forwarded);
            case "lighting2d" -> Lighting2DExample.main(forwarded);
            case "ui" -> UIShowcase.main(forwarded);
            case "audio" -> AudioStudio.main(forwarded);
            case "lighting-studio" -> LightingStudio.main(forwarded);
            case "path-tracing" -> PathTracingExample.main(forwarded);
            case "physics-studio" -> PhysicsStudio.main(forwarded);
            case "fps" -> FpsArena.main(forwarded);
            default -> throw new AssertionError("Unmapped example " + demo.id());
        }
    }

    /**
     * Rejects unknown, empty or conflicting command-line options before the window is opened.
     *
     * @param demo selected catalog entry and its allowed option grammar
     * @param options arguments to validate; this method does not mutate them
     */
    private static void validateOptions(Demo demo, List<String> options) {
        List<String> allowed = List.of(demo.options().split(" "));
        for (String option : options) {
            boolean valid =
                    allowed.stream()
                            .anyMatch(
                                    rule ->
                                            rule.endsWith("=")
                                                    ? option.startsWith(rule)
                                                            && option.length() > rule.length()
                                                    : option.equals(rule));
            if (!valid)
                throw new IllegalArgumentException(
                        "Unsupported or empty option '"
                                + option
                                + "' for "
                                + demo.id()
                                + ". Use "
                                + demo.id()
                                + " --help.");
        }
        if (options.contains("--smoke")
                && options.stream().anyMatch(option -> option.startsWith("--benchmark"))) {
            throw new IllegalArgumentException("Choose either --smoke or a benchmark mode.");
        }
        if (options.contains("--no-particle-shadows")
                && options.contains("--four-particle-shadows")) {
            throw new IllegalArgumentException("Choose only one particle-shadow budget.");
        }
    }

    /**
     * Rejects known unsupported OS/renderer combinations; the native runtime checks actual drivers.
     *
     * @param demo selected catalog entry
     */
    private static void requirePlatform(Demo demo) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (demo.requirement().startsWith("Windows x64")
                && !(os.startsWith("windows") && List.of("amd64", "x86_64").contains(arch))) {
            throw new IllegalStateException(
                    demo.title()
                            + " requires Windows x64 for Filament texture sharing."
                            + " Use scene or physics for portable raster examples.");
        }
        if (os.contains("mac") && demo.requirement().contains("4.3")) {
            throw new IllegalStateException(
                    demo.title()
                            + " requires OpenGL 4.3; macOS OpenGL stops at 4.1."
                            + " Use scene, physics, lighting2d, ui or audio instead.");
        }
    }

    /** Prints stable demo IDs, capabilities and the command used to request detailed help. */
    private static void printCatalog() {
        System.out.println("Valthorne examples | Java 25 | Valthorne 2.0.0\n");
        for (Demo demo : DEMOS) {
            System.out.printf("%-18s %-34s %s%n", demo.id(), demo.title(), demo.requirement());
        }
        System.out.println(
                "\n"
                        + "Run: ./gradlew run --args=\"<example-id> --help\"\n"
                        + "Start here: docs/getting-started.md");
    }
}
