import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

/**
 * Checks documentation coverage with the JDK parser, without resolving engine dependencies.
 *
 * <p>Run with {@code java tools/DocumentationCheck.java src/main/java}. Named types and explicitly
 * declared methods, including private helpers, require a Javadoc contract. Anonymous adapters
 * inherit their surrounding callback context and are excluded. Javadoc itself validates markup and
 * links.
 */
public final class DocumentationCheck {
    /** Prevents construction of the command-line verification tool. */
    private DocumentationCheck() {}

    /**
     * Parses all Java sources and rejects missing type or method documentation.
     *
     * @param args source root, optionally followed by {@code --inventory} to report without failure
     * @throws Exception when source files cannot be read or the JDK compiler is unavailable
     */
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "src/main/java" : args[0]);
        List<Path> paths;
        try (var files = Files.walk(root)) {
            paths = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("Documentation checking requires JDK 25.");
        int[] count = {0, 0};
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    manager,
                                    null,
                                    List.of("-proc:none"),
                                    null,
                                    manager.getJavaFileObjectsFromPaths(paths));
            DocTrees docs = DocTrees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                new TreePathScanner<Void, String>() {
                    @Override
                    public Void visitClass(ClassTree type, String owner) {
                        if (type.getSimpleName().length() == 0) return null;
                        inspect(type.getSimpleName().toString());
                        return super.visitClass(type, type.getSimpleName().toString());
                    }

                    @Override
                    public Void visitMethod(MethodTree method, String owner) {
                        inspect(
                                owner
                                        + "."
                                        + method.getName()
                                        + "("
                                        + method.getParameters()
                                        + ")");
                        return super.visitMethod(method, owner);
                    }

                    /** Reports a missing contract at its original source position. */
                    private void inspect(String signature) {
                        count[0]++;
                        if (docs.getDocCommentTree(getCurrentPath()) != null) return;
                        count[1]++;
                        long offset =
                                docs.getSourcePositions()
                                        .getStartPosition(unit, getCurrentPath().getLeaf());
                        System.out.println(
                                Path.of(unit.getSourceFile().toUri())
                                        + "|"
                                        + offset
                                        + "|"
                                        + unit.getLineMap().getLineNumber(offset)
                                        + "|"
                                        + signature);
                    }
                }.scan(unit, "");
            }
        }
        System.out.printf(
                "Documentation: %d declarations, %d missing contracts.%n", count[0], count[1]);
        if (count[1] != 0 && !List.of(args).contains("--inventory")) System.exit(1);
    }
}
