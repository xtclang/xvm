import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.concurrent.TimeUnit;

/** Manual-suite executable: compile the duplicateTypes fixtures and check the real CLI results. */
public class DuplicateTypesRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected extracted XDK, fixture directory and report directory");
        }

        var xdk      = Path.of(args[0]);
        var fixtures = Path.of(args[1]);
        var reports  = Files.createDirectories(Path.of(args[2]));
        var run      = Files.createTempDirectory(reports, "run-");

        for (var scenario : Scenario.values()) {
            verify(scenario, xdk, fixtures, run.resolve(scenario.name()));
        }
        System.out.println(Scenario.values().length + " compiler regression cases passed; logs: " + run);
    }

    private static void verify(Scenario scenario, Path xdk, Path fixtures, Path work)
            throws IOException, InterruptedException {
        var sources = work.resolve("sources");
        copySources(fixtures, sources);
        switch (scenario) {
        case INLINE_ONLY    -> Files.delete(sources.resolve("App/util/Taken.x"));
        case COMPANION_ONLY -> Files.writeString(sources.resolve("App/util.x"), "package util {}\n");
        default             -> { }
        }

        var output  = Files.createDirectories(work.resolve("output"));
        var log     = work.resolve("compiler.log");
        var java    = ProcessHandle.current().info().command().orElseThrow();
        var process = new ProcessBuilder(java, "-jar", xdk.resolve("javatools.jar").toString(),
                "build", "-L", xdk.toString(), "-o", output.toString(),
                sources.resolve(scenario.module + ".x").toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new AssertionError(scenario + " compiler timed out; see " + log);
            }

            var diagnostic = Files.readString(log);
            var binary     = output.resolve(scenario.module + ".xtc");
            boolean passed = scenario.duplicate
                    ? process.exitValue() == 1 && diagnostic.contains("COMPILER-148") &&
                            diagnostic.contains("\"Taken\"") && !diagnostic.contains("Exception") &&
                            !Files.exists(binary)
                    : process.exitValue() == 0 && Files.isRegularFile(binary);
            if (!passed) {
                throw new AssertionError(scenario + " expected " +
                        (scenario.duplicate ? "COMPILER-148 without a compiler crash" : "successful compilation") +
                        "; exit=" + process.exitValue() + "; log=" + log + "\n" + diagnostic);
            }
            System.out.println("PASS " + scenario + ": " +
                    (scenario.duplicate ? "COMPILER-148" : "compiled " + scenario.module));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    private static void copySources(Path source, Path target) throws IOException {
        try (var files = Files.walk(source)) {
            for (var file : files.filter(path -> path.toString().endsWith(".x")).toList()) {
                var copy = target.resolve(source.relativize(file));
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy);
            }
        }
    }

    private enum Scenario {
        DUPLICATE_COMPANION("App", true),
        DUPLICATE_INLINE("Inline", true),
        INLINE_ONLY("App", false),
        COMPANION_ONLY("App", false),
        DISTINCT_SCOPES("DistinctScopes", false);

        Scenario(String module, boolean duplicate) {
            this.module    = module;
            this.duplicate = duplicate;
        }

        private final String  module;
        private final boolean duplicate;
    }
}
