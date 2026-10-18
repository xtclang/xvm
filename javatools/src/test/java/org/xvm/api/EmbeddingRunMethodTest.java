package org.xvm.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport.Control;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs modules through {@link EmbeddingSupport#runMethod}: how the entry method is selected and
 * what it is passed, exact arguments, and console input. Needs the installed XDK
 * ({@code ./gradlew xdk:installDist}); without it the tests are skipped.
 */
public class EmbeddingRunMethodTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    @TempDir
    static Path dirOut;

    @TempDir
    Path rootDir;

    private static List<File> xdkDirs;
    private static EmbeddingSupport support;

    @BeforeAll
    static void configure() {
        Path xdk = installedXdk();
        assumeTrue(xdk != null, "the installed XDK is needed; run ./gradlew xdk:installDist");
        xdkDirs = List.of(xdk.resolve("lib").toFile(), xdk.resolve("javatools").toFile());
        support = new EmbeddingSupport().configure(repo(), null);
    }

    @Test
    public void entryMethodSelectionFollowsTheCommandLineRunner() {
        ModuleStructure module = compile("""
                module Selection {
                    void run() {}
                    void greet(String[] args) {}
                    void optional(String[] args = []) {}
                    void two(String first, String second) {}
                    void typed(Int count) {}
                    void overloaded() {}
                    void overloaded(String[] args) {}
                }
                """);
        String[] none = new String[0];
        String[] some = {"a", "b c"};

        assertNull(EmbeddingSupport.entryArguments(module, "run", none));
        assertNull(EmbeddingSupport.entryArguments(module, "run", some));
        assertArrayEquals(none, EmbeddingSupport.entryArguments(module, "greet", null));
        assertArrayEquals(some, EmbeddingSupport.entryArguments(module, "greet", some));
        assertNull(EmbeddingSupport.entryArguments(module, "optional", none));
        assertArrayEquals(some, EmbeddingSupport.entryArguments(module, "optional", some));

        assertRejected(module, "two", "Unsupported arguments");
        assertRejected(module, "typed", "Unsupported argument type");
        assertRejected(module, "overloaded", "Ambiguous method");
        assertRejected(module, "absent", "Missing method");
    }

    @Test
    public void anEntryMethodContributedByAnAnnotationIsFoundAndRuns() throws IOException {
        compileAndStore("""
                module Contributions {
                    annotation Runnable into module {
                        void run() {
                            @Inject Console console;
                            console.print("run from an annotation");
                        }
                    }
                }
                """);
        ModuleStructure module = compile("""
                @Runnable
                module Annotated {
                    package contributions import Contributions;
                    import contributions.Runnable;
                }
                """, input());
        new DirRepository(dirOut.toFile(), false).storeModule(module);

        // the module declares no run method of its own; its annotation contributes one
        assertNull(EmbeddingSupport.entryArguments(module, "run", new String[0]));
        String output = run("Annotated", "run", List.of(), null);
        assertTrue(output.contains("run from an annotation"), output);
    }

    @Test
    public void argumentsArriveExactlyAsGiven() throws IOException {
        compileAndStore("""
                module Arguments {
                    void greet(String[] args) {
                        @Inject Console console;
                        for (String arg : args) {
                            console.print($"arg=[{arg}]");
                        }
                    }
                }
                """);
        String output = run("Arguments", "greet", List.of("one", "two words", "ünï ✓ 𝄞"), null);

        assertTrue(output.contains("arg=[one]"), output);
        assertTrue(output.contains("arg=[two words]"), output);
        assertTrue(output.contains("arg=[ünï ✓ 𝄞]"), output);
    }

    @Test
    public void consoleInputIsReadLineByLineAndEndsWithAnEmptyLine() throws IOException {
        compileAndStore(readerModule("Reader"));
        String output = run("Reader", "run", List.of(),
                new BufferedReader(new StringReader("Ada Lovelace\n")));

        assertTrue(output.contains("name? "), output);
        assertTrue(output.contains("hello [Ada Lovelace]"), output);
        assertTrue(output.contains("rest []"), output);
    }

    @Test
    public void withoutConsoleInputReadLineIsUnsupported() throws IOException {
        compileAndStore(readerModule("Unread"));
        String output = run("Unread", "run", List.of(), null);

        assertFalse(output.contains("hello ["), output);
    }

    @Test
    public void aMissingEntryMethodIsReportedAndNothingRuns() throws IOException {
        compileAndStore("""
                module Missing {
                    void run() {}
                }
                """);
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), "Missing", null, "absent", List.of(),
                new PrintWriter(new StringWriter(), true), null, rootDir.toFile(), errs);

        assertNull(control);
        assertTrue(errs.hasSeriousErrors(), errs.getErrors().toString());
    }

    @Test
    public void aRunThatCannotStartFailsItsJoinOnceAndStillCloses() throws IOException {
        // selection accepts a function, as the command-line runner does, but the runtime's
        // Container.invoke calls only methods, so the runner's start of this task fails
        compileAndStore("""
                module Unstartable {
                    static void run() {}
                }
                """);
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), "Unstartable", null, "run", List.of(),
                new PrintWriter(new StringWriter(), true), null, rootDir.toFile(), errs);
        assertNotNull(control, () -> "Unstartable did not register: " + errs.getErrors());

        CompletionException failure = assertThrows(CompletionException.class, () -> {
            try (control) {
                control.join();
            }
        });
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(control.running());
        assertNull(control.result());
        assertTrue(errs.hasSeriousErrors(), errs.getErrors().toString());
        control.close();

        // with the runner's own task directory, closing deletes it although the task never started
        boolean hadRunnerDir = Files.isDirectory(Path.of(".runner"));
        Control unowned      = support.runMethod(input(), "Unstartable", null, "run", List.of(),
                new PrintWriter(new StringWriter(), true), null, null, new ErrorList(25));
        assertNotNull(unowned);
        assertThrows(CompletionException.class, unowned::join);
        unowned.close();
        List<Path> left = taskDirectories("Unstartable");
        assertTrue(left.isEmpty(), left::toString);
        if (!hadRunnerDir) {
            Files.deleteIfExists(Path.of(".runner"));
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    private String run(String moduleName, String methodName, List<String> args,
                       BufferedReader consoleIn) {
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.runMethod(input(), moduleName, null, methodName, args,
                new PrintWriter(output, true), consoleIn, rootDir.toFile(), errs);
        assertNotNull(control, () -> moduleName + " did not start: " + errs.getErrors());
        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
        }
        return output.toString();
    }

    /** The runner's own task directories for the module, under {@code .runner} in the working directory. */
    private static List<Path> taskDirectories(String moduleName) throws IOException {
        Path runner = Path.of(".runner");
        if (!Files.isDirectory(runner)) {
            return List.of();
        }
        try (var entries = Files.list(runner)) {
            return entries.filter(dir -> dir.getFileName().toString().startsWith(moduleName + "_")).toList();
        }
    }

    private static String readerModule(String moduleName) {
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        String name = console.readLine("name? ");
                        console.print($"hello [{name}]");
                        String rest = console.readLine();
                        console.print($"rest [{rest}]");
                    }
                }
                """.formatted(moduleName);
    }

    private static void assertRejected(ModuleStructure module, String methodName, String reason) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EmbeddingSupport.entryArguments(module, methodName, new String[0]));
        assertTrue(failure.getMessage().contains(reason), failure.getMessage());
    }

    private static ModuleStructure compile(String source) {
        return compile(source, repo());
    }

    private static ModuleStructure compile(String source, ModuleRepository repository) {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = support.compile(source, repository, errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> "compile failed: " + errs.getErrors());
        return module;
    }

    private static void compileAndStore(String source) throws IOException {
        new DirRepository(dirOut.toFile(), false).storeModule(compile(source));
    }

    /** The compiled test modules, ahead of the XDK. */
    private static ModuleRepository input() {
        return new LinkedRepository(new DirRepository(dirOut.toFile(), true), repo());
    }

    private static ModuleRepository repo() {
        List<ModuleRepository> repos = new ArrayList<>();
        for (File dir : xdkDirs) {
            repos.add(new DirRepository(dir, true));
        }
        return new LinkedRepository(repos.toArray(ModuleRepository.NO_REPOS));
    }

    /** The installed XDK of this checkout, found from the test's working directory, or null. */
    private static Path installedXdk() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path xdk = dir.resolve("xdk/build/install/xdk");
            if (Files.isDirectory(xdk.resolve("lib")) && Files.isDirectory(xdk.resolve("javatools"))) {
                return xdk;
            }
        }
        return null;
    }
}
