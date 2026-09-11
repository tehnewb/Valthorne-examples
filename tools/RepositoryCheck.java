import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks local documentation links and the exact redistributable resource inventory using JDK 25.
 */
public final class RepositoryCheck {
    /** Prevents construction of the standalone repository verification tool. */
    private RepositoryCheck() {}

    /**
     * Verifies links and asset digests relative to the repository's working directory.
     *
     * @param args unused; the Gradle task supplies the repository as working directory
     * @throws Exception if a document, inventory entry or resource cannot be read or validated
     */
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        List<String> failures = new ArrayList<>();
        var links = Pattern.compile("!?\\[[^\\]\\r\\n]*\\]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
        int documents = 0;
        try (var paths = Files.walk(root)) {
            for (Path document :
                    paths.filter(path -> path.toString().endsWith(".md"))
                            .filter(path -> !root.relativize(path).startsWith("build"))
                            .filter(path -> !root.relativize(path).startsWith(".git"))
                            .toList()) {
                documents++;
                String content = Files.readString(document).replaceAll("(?s)```.*?```", "");
                var matcher = links.matcher(content);
                while (matcher.find()) {
                    String target = matcher.group(1).split("#", 2)[0];
                    if (target.isEmpty()
                            || target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
                            || target.startsWith("/")) continue;
                    target =
                            java.net.URLDecoder.decode(
                                    target.replace("+", "%2B"), StandardCharsets.UTF_8);
                    if (!Files.exists(document.getParent().resolve(target)))
                        failures.add(root.relativize(document) + " -> " + target);
                }
            }
        }
        int assets = 0;
        var inventory = new java.util.HashSet<Path>();
        for (String line : Files.readAllLines(root.resolve("tools/resources.sha256"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\s+", 2);
            Path resource = root.resolve(fields[1]).normalize();
            if (!resource.startsWith(root.resolve("src/main/resources")))
                throw new IllegalStateException("Invalid resource path: " + fields[1]);
            inventory.add(resource);
            if (!Files.isRegularFile(resource)) {
                failures.add("Missing asset: " + fields[1]);
                continue;
            }
            String digest =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(Files.readAllBytes(resource)));
            if (!digest.equals(fields[0])) failures.add("Resource checksum changed: " + fields[1]);
            assets++;
        }
        try (var resources = Files.walk(root.resolve("src/main/resources"))) {
            resources
                    .filter(Files::isRegularFile)
                    .filter(path -> !inventory.contains(path))
                    .forEach(
                            path ->
                                    failures.add(
                                            "Asset missing from inventory: "
                                                    + root.relativize(path)));
        }
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.printf(
                "Verified links in %d documents and checksums for %d packaged resources.%n",
                documents, assets);
    }
}
