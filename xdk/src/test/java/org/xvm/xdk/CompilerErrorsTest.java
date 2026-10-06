package org.xvm.xdk;

import java.io.File;
import java.io.IOException;

import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import java.util.regex.Pattern;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.xvm.api.ModuleCompiler;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;

import org.xvm.util.Severity;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative compiler tests: modules that must not compile, each checked for exactly the errors the
 * compiler is expected to report. Each {@code .x} file in the {@code compiler-errors} test resource
 * directory is one such module.
 *
 * <p>A source file marks each line on which an error is expected with a comment naming its code,
 * for example {@code i = s.size;  // expect-error: COMPILER-36}; several codes are separated by
 * commas. The file passes if it does not compile, and the errors the compiler reports, by line and
 * code, are exactly the marked ones: a missing error, an error on another line and an unmarked
 * error all fail. Codes are compared rather than message text, so messages can be reworded freely.
 * Warnings are not checked.
 *
 * <p>The compiler works in four stages: it registers structures, resolves names, validates content
 * and generates code. Each stage can report errors, but the compiler does not go on to the next
 * stage once a stage has reported any. All the cases in a file are compiled together, so they must
 * all fail in the same stage, and a case that fails in another stage needs a file of its own. For
 * example, every case in {@code errors.x} fails in code generation.
 */
class CompilerErrorsTest {
    private static final String RESOURCE_DIR = "/compiler-errors";

    private static final String CODE_REGEX = "[A-Z]+-[A-Z0-9]+";

    private static final Pattern CODE = Pattern.compile(CODE_REGEX);

    private static final Pattern MARKER = Pattern.compile(
            "//\\s*expect-error:\\s*(" + CODE_REGEX + "(?:\\s*,\\s*" + CODE_REGEX + ")*)");

    private static final Comparator<Diagnostic> ORDER =
            Comparator.comparingInt(Diagnostic::line).thenComparing(Diagnostic::code);

    private static ModuleCompiler compiler;

    /**
     * An error code reported (or expected) on a one-based source line; line 0 means no position.
     */
    private record Diagnostic(int line, String code) {
        @Override
        public String toString() {
            return line == 0 ? code + " (no position)" : "line " + line + ": " + code;
        }
    }

    @BeforeAll
    static void createCompiler() {
        // the xdk test task depends on installDist, and runs in the xdk project directory
        File xdk = new File(System.getProperty("user.dir"), "build/install/xdk");
        File lib = new File(xdk, "lib");
        assertTrue(new File(lib, "ecstasy.xtc").isFile(),
                () -> "XDK not installed at " + xdk + "; run ./gradlew xdk:installDist");

        compiler = new ModuleCompiler(new LinkedRepository(
                new DirRepository(lib, true),
                new DirRepository(new File(xdk, "javatools"), true)));
    }

    @Test
    void compilesValidModule() {
        ErrorList errs = new ErrorList(10);

        assertNotNull(compiler.compile("module Valid { void run() {} }", null, errs),
                () -> "Valid did not compile: " + errs.getErrors());
    }

    @ParameterizedTest
    @MethodSource("negativeTestFiles")
    void reportsExactlyTheMarkedErrors(String fileName) throws IOException, URISyntaxException {
        String    source = Files.readString(resourceDir().resolve(fileName));
        ErrorList errs   = new ErrorList(1000);

        assertNull(compiler.compile(source, null, errs), fileName + " compiled, but must not");

        var expected   = expectedErrors(source);
        var reported   = reportedErrors(errs);
        var missing    = expected.stream().filter(not(reported::contains)).toList();
        var unexpected = reported.stream().filter(not(expected::contains)).toList();
        assertTrue(missing.isEmpty() && unexpected.isEmpty(),
                () -> fileName + ": missing " + missing + ", unexpected " + unexpected
                        + (missing.isEmpty() ? "" : "; all of a file's cases must fail in the"
                                + " same compiler stage, as the compiler stops after a stage"
                                + " with errors"));
    }

    /**
     * @return the names of the negative test files: all the .x files in the resource directory
     */
    static List<String> negativeTestFiles() throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.list(resourceDir())) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".x"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * @return the errors the markers in the source expect
     */
    private static SortedSet<Diagnostic> expectedErrors(String source) {
        List<String> lines = source.lines().toList();
        return IntStream.range(0, lines.size()).boxed()
                .flatMap(i -> MARKER.matcher(lines.get(i)).results()
                        .flatMap(marker -> CODE.matcher(marker.group(1)).results())
                        .map(code -> new Diagnostic(i + 1, code.group())))
                .collect(toCollection(() -> new TreeSet<>(ORDER)));
    }

    /**
     * @return the errors the compiler reported
     */
    private static SortedSet<Diagnostic> reportedErrors(ErrorList errs) {
        return errs.getErrors().stream()
                .filter(info -> info.getSeverity().isAtLeast(Severity.ERROR))
                .map(info -> new Diagnostic(info.getSource() == null ? 0 : info.getLine() + 1,
                        info.getCode()))
                .collect(toCollection(() -> new TreeSet<>(ORDER)));
    }

    private static Path resourceDir() throws URISyntaxException {
        URL dir = CompilerErrorsTest.class.getResource(RESOURCE_DIR);
        assertNotNull(dir, () -> "Missing test resource directory " + RESOURCE_DIR);
        return Path.of(dir.toURI());
    }
}
