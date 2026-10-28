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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport.ConsoleInput;
import org.xvm.api.EmbeddingSupport.Control;
import org.xvm.api.EmbeddingSupport.EntryPoint;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

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
                    void extra(String[] args, Int count = 0, String name = "") {}
                    void two(String first, String second) {}
                    void three(String[] args, String first, String second = "") {}
                    void typed(Int count) {}
                    void overloaded() {}
                    void overloaded(String[] args) {}
                    static void utility() {}
                    void mixed() {}
                    static void mixed(String[] args) {}
                }
                """);
        String[] none = new String[0];
        String[] some = {"a", "b c"};

        assertFalse(passes(module, "run", none));
        assertFalse(passes(module, "run", some));
        assertTrue(passes(module, "greet", none));
        assertTrue(passes(module, "greet", some));
        // without arguments, nothing is passed and the parameter's default applies
        assertFalse(passes(module, "optional", none));
        assertTrue(passes(module, "optional", some));
        // the array goes to the first parameter, and the others take their defaults
        assertTrue(passes(module, "extra", none));
        assertTrue(passes(module, "extra", some));
        // the runtime calls only methods: a function of the same name does not compete
        assertFalse(passes(module, "mixed", some));

        assertRejected(module, "two", "Unsupported arguments");
        assertRejected(module, "three", "Unsupported arguments");
        assertRejected(module, "typed", "Unsupported argument type");
        assertRejected(module, "overloaded", "Ambiguous method");
        assertRejected(module, "absent", "Missing method");
        // functions are not entry points
        assertRejected(module, "utility", "Missing method");
    }

    @Test
    public void anUnlinkedModuleIsRejected() throws IOException {
        compileAndStore("""
                module Unlinked {
                    void run() {}
                }
                """);
        ModuleStructure module = new DirRepository(dirOut.toFile(), true).loadModule("Unlinked");
        assertNotNull(module);
        assertFalse(module.getFileStructure().isLinked());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EmbeddingSupport.entryMethod(module, EntryPoint.RUN));
        assertTrue(failure.getMessage().contains("not linked"), failure.getMessage());
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
        assertFalse(passes(module, "run", new String[0]));
        String output = run("Annotated", "run", List.of(), ConsoleInput.NONE);
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
        String output = run("Arguments", "greet", List.of("one", "two words", "ünï ✓ 𝄞"), ConsoleInput.NONE);

        assertTrue(output.contains("arg=[one]"), output);
        assertTrue(output.contains("arg=[two words]"), output);
        assertTrue(output.contains("arg=[ünï ✓ 𝄞]"), output);
    }

    @Test
    public void anOptionalArgumentArrayIsPassedOnlyWhenArgumentsAreGiven() throws IOException {
        compileAndStore("""
                module Defaults {
                    void run(String[] args = ["fallback"], Int times = 2) {
                        @Inject Console console;
                        console.print($"args={args.size}:{args[0]} times={times}");
                    }
                }
                """);

        String output = run("Defaults", "run", List.of(), ConsoleInput.NONE);
        assertTrue(output.contains("args=1:fallback times=2"), output);

        output = run("Defaults", "run", List.of("given"), ConsoleInput.NONE);
        assertTrue(output.contains("args=1:given times=2"), output);
    }

    @Test
    public void consoleInputIsReadLineByLineAndEndsWithAnEmptyLine() throws IOException {
        compileAndStore(readerModule("Reader"));
        String output = run("Reader", "run", List.of(),
                ConsoleInput.of(new BufferedReader(new StringReader("Ada Lovelace\n"))));

        assertTrue(output.contains("name? "), output);
        assertTrue(output.contains("hello [Ada Lovelace]"), output);
        assertTrue(output.contains("rest []"), output);
    }

    @Test
    public void consoleInputIsReadOnAnIoThreadAndToldWhetherToEcho() throws IOException {
        compileAndStore("""
                module Secret {
                    void run() {
                        @Inject Console console;
                        String name = console.readLine("name? ");
                        String word = console.readLine("password? ", True);
                        console.print($"read [{name}] and {word.size} characters");
                    }
                }
                """);
        var lines   = new ConcurrentLinkedQueue<>(List.of("Ada", "secret"));
        var echo    = new CopyOnWriteArrayList<Boolean>();
        var readers = new CopyOnWriteArrayList<Thread>();
        String output = run("Secret", "run", List.of(), suppressEcho -> {
            echo.add(suppressEcho);
            readers.add(Thread.currentThread());
            return lines.poll();
        });

        assertTrue(output.contains("read [Ada] and 6 characters"), output);
        assertEquals(List.of(false, true), echo);
        // waiting for a line occupies a virtual IO thread, not one of the runtime's service workers
        assertEquals(2, readers.size());
        assertTrue(readers.stream().allMatch(Thread::isVirtual), readers.toString());
    }

    @Test
    public void withoutConsoleInputReadLineIsUnsupported() throws IOException {
        compileAndStore(readerModule("Unread"));
        String output = run("Unread", "run", List.of(), ConsoleInput.NONE);

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
        Control   control = support.runMethod(input(), "Missing", null, new EntryPoint("absent"),
                new PrintWriter(new StringWriter(), true), ConsoleInput.NONE, rootDir.toFile(), errs);

        assertNull(control);
        assertTrue(errs.hasSeriousErrors(), errs.getErrors().toString());
    }

    @Test
    public void aRunThatCannotStartFailsItsJoinOnceAndStillCloses() throws IOException {
        compileAndStore("""
                module Unstartable {
                    void run() {}
                }
                """);
        // the file system refuses a name with a NUL character, so the runner cannot start the task
        File      blocked = new File(rootDir.toFile(), "no\u0000root");
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), "Unstartable", null, EntryPoint.RUN,
                new PrintWriter(new StringWriter(), true), ConsoleInput.NONE, blocked, errs);
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
    }

    // ----- helpers -------------------------------------------------------------------------------

    private String run(String moduleName, String methodName, List<String> args,
                       ConsoleInput consoleIn) {
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.runMethod(input(), moduleName, null,
                new EntryPoint(methodName, args), new PrintWriter(output, true), consoleIn,
                rootDir.toFile(), errs);
        assertNotNull(control, () -> moduleName + " did not start: " + errs.getErrors());
        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
        }
        return output.toString();
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

    private static boolean passes(ModuleStructure module, String methodName, String[] args) {
        EntryPoint entry = new EntryPoint(methodName, args);
        return EmbeddingSupport.passesArguments(EmbeddingSupport.entryMethod(module, entry), entry);
    }

    private static void assertRejected(ModuleStructure module, String methodName, String reason) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EmbeddingSupport.entryMethod(module, new EntryPoint(methodName)));
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
