import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manual-suite executable: compile each lexerEnds fixture as a source that ends where its last
 * line does, without the line end that every saved file has, and check that the compiler reports
 * the expected error and stops, rather than hanging or failing with an exception.
 */
public class LexerEndsRunner {
    /**
     * The fixtures, by module name, and the error each must report.
     */
    private static final Map<String, String> EXPECTED = Map.of(
            "UnterminatedString", "LEXER-08",
            "UnterminatedTemplate", "LEXER-08",
            "TemplateExpressionAtEnd", "LEXER-08",
            "EscapeAtEnd", "LEXER-08",
            "CharEscapeAtEnd", "LEXER-05");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected extracted XDK, fixture directory and report directory");
        }

        var xdk      = Path.of(args[0]);
        var fixtures = Path.of(args[1]);
        var reports  = Files.createDirectories(Path.of(args[2]));
        var run      = Files.createTempDirectory(reports, "run-");

        var failures = new StringBuilder();
        for (var entry : EXPECTED.entrySet()) {
            String failure = verify(entry.getKey(), entry.getValue(), xdk, fixtures, run.resolve(entry.getKey()));
            if (failure != null) {
                failures.append(failure).append('\n');
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(failures);
        }
        System.out.println(EXPECTED.size() + " lexer end-of-source cases passed; logs: " + run);
    }

    /**
     * @return why the fixture failed, or null if the compiler reported the expected error and stopped
     */
    private static String verify(String module, String code, Path xdk, Path fixtures, Path work)
            throws IOException, InterruptedException {
        // the end of the source is the end of the fixture's last line
        var source = Files.createDirectories(work).resolve(module + ".x");
        Files.writeString(source, Files.readString(fixtures.resolve(module + ".x")).stripTrailing());

        var output  = Files.createDirectories(work.resolve("output"));
        var log     = work.resolve("compiler.log");
        var java    = ProcessHandle.current().info().command().orElseThrow();
        var process = new ProcessBuilder(java, "-jar", xdk.resolve("javatools.jar").toString(),
                "build", "-L", xdk.toString(), "-o", output.toString(), source.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                return "FAIL " + module + ": the compiler did not stop within 60 s; see " + log;
            }

            var diagnostic = Files.readString(log);
            if (process.exitValue() == 0 || !diagnostic.contains(code) || diagnostic.contains("Exception")) {
                return "FAIL " + module + ": expected " + code + " and no exception; exit=" + process.exitValue()
                        + "; log=" + log + "\n" + diagnostic;
            }
            System.out.println("PASS " + module + ": " + code);
            return null;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }
}
