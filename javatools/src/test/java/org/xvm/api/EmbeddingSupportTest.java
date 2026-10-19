package org.xvm.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;

import java.util.List;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport.ConsoleInput;
import org.xvm.api.EmbeddingSupport.Control;
import org.xvm.api.EmbeddingSupport.EntryPoint;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;

import org.xvm.compiler.EvalCompiler;

import org.xvm.runtime.Debugger;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles and runs modules through {@link EmbeddingSupport}: compilation, runs with a console and
 * a file system, failures, closing a run, how the entry method is selected and what it is passed,
 * exact arguments, and console input. The compiled XDK modules come from the test resources, where
 * the build puts them ({@code testXtcModules} in javatools/build.gradle.kts).
 */
public class EmbeddingSupportTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    @TempDir
    static Path dirOut;

    @TempDir
    Path rootDir;

    private static File xdkModules;
    private static EmbeddingSupport support;

    @BeforeAll
    static void configure() throws URISyntaxException {
        URL modules = EmbeddingSupportTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        xdkModules = new File(modules.toURI());
        support = new EmbeddingSupport().configure(repo(), null);
    }

    @Test
    public void compilingReportsInvalidSource() {
        assertEquals("Trivial", compile(helloModule("Trivial")).getName());

        ErrorList errs = new ErrorList(25);
        assertNull(support.compile("module Broken { void run( }", repo(), errs));
        assertTrue(errs.hasSeriousErrors(), errs.getErrors().toString());
    }

    @Test
    public void aModuleRunsWithAConsole() {
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.run(compile(helloModule("Hello")),
                new PrintWriter(output, true), null, null, errs);
        assertNotNull(control, () -> "Hello did not start: " + errs.getErrors());

        try (control) {
            await(control);
        }
        assertTrue(output.toString().contains("hello from Hello"), output.toString());
        assertFalse(errs.hasSeriousErrors(), errs.getErrors().toString());
    }

    @Test
    public void aStoredModuleRunsByNameMoreThanOnce() throws IOException {
        compileAndStore(helloModule("Again"));
        for (int i = 0; i < 2; i++) {
            StringWriter output  = new StringWriter();
            ErrorList    errs    = new ErrorList(25);
            Control      control = support.run(input(), "Again", null,
                    new PrintWriter(output, true), rootDir.toFile(), null, null, errs);
            assertNotNull(control, () -> "Again did not start: " + errs.getErrors());

            try (control) {
                await(control);
            }
            assertTrue(output.toString().contains("hello from Again"), output.toString());
        }
    }

    @Test
    public void aRunUsesTheSuppliedRootDirectory() {
        ModuleStructure module = compile("""
                module FileSystemTest {
                    void run() {
                        @Inject FileStore storage;
                        @Inject Directory rootDir;
                        @Inject Directory homeDir;
                        @Inject Directory curDir;
                        @Inject Directory tmpDir;

                        assert rootDir.path == storage.root.path;
                        assert homeDir.path == storage.root.path;
                        assert curDir.path  == storage.root.path;
                        assert tmpDir.name == ".temp";

                        rootDir.fileFor("root.txt").ensure();
                        tmpDir.fileFor("temporary.txt").ensure();
                    }
                }
                """);
        ErrorList errs    = new ErrorList(25);
        Control   control = support.run(module, null, rootDir.toFile(), null, errs);
        assertNotNull(control, () -> "FileSystemTest did not start: " + errs.getErrors());

        try (control) {
            await(control);
        }
        assertFalse(errs.hasSeriousErrors(), errs.getErrors().toString());
        assertTrue(Files.isRegularFile(rootDir.resolve("root.txt")));
        assertTrue(Files.isRegularFile(rootDir.resolve(".temp/temporary.txt")));
    }

    @Test
    public void aFailedRunReportsItsExceptionThroughTheConsole() {
        String message = "deliberate failure the host must learn about";
        ModuleStructure module = compile("""
                module Crasher {
                    void run() {
                        throw new IllegalState("%s");
                    }
                }
                """.formatted(message));
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.run(module, new PrintWriter(output, true), null, null, errs);
        assertNotNull(control, () -> "Crasher did not start: " + errs.getErrors());

        try (control) {
            await(control);
        }
        assertTrue(output.toString().contains(message), output.toString());
    }

    @Test
    public void closingASuspendedRunTerminatesIt() throws InterruptedException {
        ModuleStructure module = compile("""
                module Waiting {
                    void run() {
                        @Inject Console console;
                        @Inject Timer timer;
                        try {
                            @Future Tuple done;
                            timer.schedule(Duration:1H, () -> {done = ();});
                            console.print("waiting");
                            return done;
                        } catch (Exception e) {
                            console.print("caught termination");
                        }
                    }
                }
                """);
        StringWriter   output  = new StringWriter();
        CountDownLatch waiting = new CountDownLatch(1);
        PrintWriter    console = new PrintWriter(output, true) {
            @Override
            public void println(char[] chars) {
                super.println(chars);
                if (new String(chars).contains("waiting")) {
                    waiting.countDown();
                }
            }
        };
        ErrorList errs    = new ErrorList(25);
        Control   control = support.run(module, console, null, null, errs);
        assertNotNull(control, () -> "Waiting did not start: " + errs.getErrors());

        try (control) {
            // a run that ends before suspending also releases the wait, so it fails below instead
            // of blocking until the time limit
            Thread.ofVirtual().start(() -> {
                try {
                    control.join();
                } finally {
                    waiting.countDown();
                }
            });
            assertTimeoutPreemptively(RUN_LIMIT, () -> waiting.await());
            assertTrue(output.toString().contains("waiting"),
                    () -> "Waiting ended before it suspended: " + output);

            // a close() that waited for the pending one-hour timer would not return in time
            assertTimeoutPreemptively(RUN_LIMIT, control::close);
        }
        assertTrue(output.toString().contains("Service terminated"), output.toString());
        assertFalse(output.toString().contains("caught termination"), output.toString());
    }

    @Test
    public void aRunEndsWhenItsServicesHaveNothingLeftToDo() {
        // as on the command line: run() returns with an alarm pending that keeps the container
        // alive, so the run goes on until it has fired; one that does not keep it alive is dropped
        ModuleStructure module = compile("""
                module Lingers {
                    void run() {
                        @Inject Console console;
                        @Inject Timer timer;
                        timer.start();
                        timer.schedule(Duration.ofMillis(300), () -> console.print("kept"), keepAlive=True);
                        timer.schedule(Duration.ofMinutes(60), () -> console.print("dropped"));
                        console.print("returned");
                    }
                }
                """);
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.run(module, new PrintWriter(output, true), null, null, errs);
        assertNotNull(control, () -> "Lingers did not start: " + errs.getErrors());

        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertFalse(control.running());
        }
        assertTrue(output.toString().contains("returned"), output.toString());
        assertTrue(output.toString().contains("kept"), output.toString());
        assertFalse(output.toString().contains("dropped"), output.toString());
    }

    @Test
    public void nothingOfARunStillRunsOnceItIsClosed() {
        // run() returns with a service still spinning, as a program that starts a server does
        ModuleStructure module = compile("""
                module Leaves {
                    service Spinner {
                        Int spins;

                        void spin() {
                            for (Int count = 0; count >= 0; ++count) {
                                ++spins;
                            }
                        }
                    }

                    void run() {
                        new Spinner().spin^();
                        @Inject Console console;
                        console.print("started");
                    }
                }
                """);
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.run(module, new PrintWriter(output, true), null, null, errs);
        assertNotNull(control, () -> "Leaves did not start: " + errs.getErrors());

        assertTimeoutPreemptively(RUN_LIMIT, () -> {
            while (!output.toString().contains("started")) {
                Thread.sleep(10);
            }
        });
        // not in a try-with-resources: closing again after a close that timed out would wait without a limit
        assertTimeoutPreemptively(RUN_LIMIT, control::close);

        var connector = (InterpreterConnector) support.ensureConnector();
        assertTimeoutPreemptively(RUN_LIMIT, () -> {
            while (!connector.isRuntimeIdle()) {
                Thread.sleep(10);
            }
        }, "The closed run's spinner still runs");
    }

    @Test
    public void closingARunEndsAServiceWhoseFiberEndedWithACallStillPending() {
        // Helper's fiber returns while its asynchronous call to a spinner is still running, so the
        // fiber has ended and still waits for an answer; killing the container must end the
        // service anyway, and not wait for that answer
        ModuleStructure module = compile("""
                module Pending {
                    service Spinner {
                        Int spins;

                        void spin() {
                            for (Int count = 0; count >= 0; ++count) {
                                ++spins;
                            }
                        }
                    }

                    service Helper {
                        void go(Spinner spinner) {
                            spinner.spin^();
                        }
                    }

                    void run() {
                        Helper helper = new Helper();
                        helper.go(new Spinner());
                        @Inject Console console;
                        console.print("started");
                        new Spinner().spin();
                    }
                }
                """);
        StringWriter output  = new StringWriter();
        ErrorList    errs    = new ErrorList(25);
        Control      control = support.run(module, new PrintWriter(output, true), null, null, errs);
        assertNotNull(control, () -> "Pending did not start: " + errs.getErrors());

        assertTimeoutPreemptively(RUN_LIMIT, () -> {
            while (!output.toString().contains("started") && control.running()) {
                Thread.sleep(10);
            }
        });
        assertTrue(control.running(), () -> "Pending ended on its own: " + output);

        // the run never ends on its own; closing it ends every service in its container. Not in a
        // try-with-resources: closing again after a close that timed out would wait without a limit
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFalse(control.running());
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
    public void argumentsForAMethodWithoutParametersAreDroppedWithAWarning() throws IOException {
        compileAndStore(helloModule("Unused"));

        ErrorList errs = runReporting("Unused", new EntryPoint("run", List.of("ignored")));
        assertTrue(errs.getErrors().stream().anyMatch(info -> info.getSeverity() == WARNING
                && info.getCode().equals(EmbeddingSupport.WARN_ARGUMENTS_IGNORED)), errs::toString);
        assertFalse(errs.hasSeriousErrors(), errs::toString);

        assertFalse(runReporting("Unused", EntryPoint.RUN).hasError(EmbeddingSupport.WARN_ARGUMENTS_IGNORED));
    }

    @Test
    public void aMethodThatCannotBeAnEntryPointIsReportedWithTheRunnersMessage() throws IOException {
        compileAndStore(helloModule("Refused"));
        int       consoles = support.registeredConsoles();
        ErrorList errs     = new ErrorList(25);
        Control   control  = support.runMethod(input(), "Refused", null, new EntryPoint("absent"),
                new PrintWriter(new StringWriter(), true), ConsoleInput.NONE, rootDir.toFile(), errs);

        assertNull(control);
        // the reason alone, as xtc run reports it, rather than a failure to create the container
        assertEquals(List.of(EmbeddingSupport.ERR_ENTRY_METHOD),
                errs.getErrors().stream().map(ErrorInfo::getCode).toList());
        assertEquals("Missing method \"absent\" in module Refused", errs.getErrors().getFirst().getMessageText());
        assertEquals(consoles, support.registeredConsoles());
    }

    @Test
    public void assertDebugContinuesSinceAnEmbeddedRunHasNoDebugger() throws IOException {
        compileAndStore("""
                module Breaks {
                    void run() {
                        @Inject Console console;
                        console.print("before");
                        assert:debug;
                        console.print("after");
                    }
                }
                """);
        String output = run("Breaks", "run", List.of(), ConsoleInput.NONE);

        assertEquals(List.of("before", "after"), output.lines().filter(line -> !line.isEmpty()).toList());
    }

    @Test
    public void codeCompiledWhereARunStoppedSeesItsParametersAndInjectedValuesAndLeavesNoFailure()
            throws IOException {
        compileAndStore("""
                module Evals {
                    void run() {
                        show(5);
                    }

                    void show(Int n) {
                        @Inject Random random;
                        assert:debug;
                        @Inject Console console;
                        console.print($"{n} {random.int(1)}");
                    }
                }
                """);

        // what the console debugger's E compiles, at the assert:debug, before anything has asked
        // for the frame's variables: a parameter, a method of an injected value, and a name that
        // does not resolve, whose lambda must not stay in the class; each compiled expression
        // adds a lambda, so the class has two before the one that fails, and still two after it
        var compiled = new ConcurrentHashMap<String, Boolean>();
        var errors   = new ConcurrentHashMap<String, List<String>>();
        var before   = new AtomicInteger();
        var after    = new AtomicInteger();
        var evaluate = new Debugger() {
            @Override
            public int activate(Frame frame, int iPC) {
                ClassStructure clz = frame.f_function.getContainingClass();
                compile(frame, "n + 1");
                compile(frame, "random.int(1)");
                before.set(lambdaCount(clz));
                compile(frame, "nope");
                after.set(lambdaCount(clz));
                return iPC + 1;
            }

            @Override
            public int checkBreakPoint(Frame frame, int iPC) {
                return Debugger.NONE.checkBreakPoint(frame, iPC);
            }

            @Override
            public void onReturn(Frame frame) {
            }

            @Override
            public int checkBreakPoint(Frame frame, ExceptionHandle hEx) {
                return Debugger.NONE.checkBreakPoint(frame, hEx);
            }

            private void compile(Frame frame, String expression) {
                // as the console debugger's E wraps the expression
                String          eval     = "{\nreturn {Object r__ = {\nreturn " + expression
                                         + ";\n}; return r__.toString();};\n}";
                EvalCompiler    compiler = new EvalCompiler(frame, eval);
                MethodStructure lambda   = compiler.createLambda(frame.poolContext().typeString());
                compiled.put(expression, lambda != null);
                // only to say why, if an expression that should compile does not
                errors.put(expression, compiler.getErrors().stream().map(ErrorInfo::getMessageText).toList());
            }

            private static int lambdaCount(ClassStructure clz) {
                return clz.getChild("->") instanceof MultiMethodStructure lambdas
                        ? lambdas.getChildrenCount()
                        : 0;
            }
        };

        var connector = (InterpreterConnector) support.ensureConnector();
        connector.setDebugger(evaluate);
        String output;
        try {
            output = run("Evals", "run", List.of(), ConsoleInput.NONE);
        } finally {
            connector.setDebugger(Debugger.NONE);
        }

        assertTrue(output.contains("5 0"), output);
        assertAll(
            () -> assertTrue(compiled.get("n + 1"), () -> "a parameter: " + errors.get("n + 1")),
            () -> assertTrue(compiled.get("random.int(1)"), () -> "an injected value: " + errors.get("random.int(1)")),
            () -> assertFalse(compiled.get("nope"), "a name that does not resolve"),
            () -> assertEquals(2, before.get(), "the lambdas of the two expressions that compile"),
            () -> assertEquals(before.get(), after.get(), "the lambdas after the one that does not"));
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

    /**
     * Wait for a run to end, and check that it reports a start and a stop in that order.
     */
    private static void await(Control control) {
        Instant started = control.whenStarted();
        assertNotNull(started);

        assertTimeoutPreemptively(RUN_LIMIT, control::join);

        Instant stopped = control.whenStopped();
        assertNotNull(stopped);
        assertFalse(stopped.isBefore(started), () -> started + " to " + stopped);
    }

    private static String helloModule(String moduleName) {
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        console.print("hello from %s");
                    }
                }
                """.formatted(moduleName, moduleName);
    }

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
        return new DirRepository(xdkModules, true);
    }
}
