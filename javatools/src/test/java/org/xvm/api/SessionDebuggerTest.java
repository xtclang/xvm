package org.xvm.api;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.List;
import java.util.Map;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Function;

import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport.ConsoleInput;
import org.xvm.api.EmbeddingSupport.Control;
import org.xvm.api.EmbeddingSupport.EntryPoint;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.SessionDebugger;
import org.xvm.runtime.SessionDebugger.Breakpoint;
import org.xvm.runtime.SessionDebugger.Line;
import org.xvm.runtime.SessionDebugger.Scope;
import org.xvm.runtime.SessionDebugger.StackFrameInfo;
import org.xvm.runtime.SessionDebugger.Stop;
import org.xvm.runtime.SessionDebugger.Variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs modules compiled from source files under a {@link SessionDebugger}: breakpoints by file and
 * line, how they are verified, the stack of a stop, the variables of its frames, assert:debug,
 * other fibers waiting while the program is stopped, fibers yielding to each other while
 * breakpoints are set, and closing a stopped run. The compiled XDK modules come from the test resources, as for
 * {@link EmbeddingSupportTest}.
 */
public class SessionDebuggerTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    @TempDir
    static Path dirOut;

    @TempDir
    Path dirSource;

    private static EmbeddingSupport support;

    @BeforeAll
    static void configure() throws URISyntaxException {
        URL modules = SessionDebuggerTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        support = new EmbeddingSupport().configure(new DirRepository(new File(modules.toURI()), true), null);
    }

    @Test
    public void aBreakpointStopsAtItsLineWithTheCallerBelowAndTheProgramGoesOnWhenResumed()
            throws Exception {
        Path  source  = compile("Twice", TWICE);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Twice"));
        List<Breakpoint> breakpoints = session.setBreakpoints(source.toString(), List.of(new Line(7, 11)));
        assertEquals(List.of(new Breakpoint(7, 11, true, null)), breakpoints);

        StringWriter output  = new StringWriter();
        Control      control = run("Twice", output, session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("breakpoint", stop.reason());
            assertEquals(List.of(7), stop.breakpointIds());
            assertTrue(session.isStopped());
            assertTrue(output.toString().contains("before"), output.toString());
            assertFalse(output.toString().contains("after"), output.toString());

            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            assertEquals("Twice.twice", frames.get(0).name());
            assertEquals(11, frames.get(0).line());
            assertEquals(source.toRealPath(), Path.of(frames.get(0).path()).toRealPath());
            assertEquals("Twice.run", frames.get(1).name());
            assertEquals(5, frames.get(1).line());
            assertEquals(stop.threadId(), session.threads().getFirst().id());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(42L, control.result());
            assertTrue(output.toString().contains("after 42"), output.toString());
            assertTrue(session.threads().isEmpty());
        }
    }

    @Test
    public void aBreakpointWithoutCodeOnItsLineMovesToTheNextLineWithCodeOrIsNotInstalled()
            throws Exception {
        Path source  = compile("Moves", TWICE.replace("Twice", "Moves"));
        var  session = new SessionDebugger(_ -> {});
        session.addModule(input().loadModule("Moves"));

        // "Int run() {" has no code of its own, the blank line and the closing brace none to follow
        assertEquals(List.of(new Breakpoint(1, 3, true, null),
                             new Breakpoint(2, 9, false, SessionDebugger.NO_CODE),
                             new Breakpoint(3, 13, false, SessionDebugger.NO_CODE)),
                session.setBreakpoints(source.toString(), List.of(new Line(1, 2), new Line(2, 9), new Line(3, 13))));
        assertEquals(List.of(new Breakpoint(4, 1, false, SessionDebugger.NOT_IN_PROGRAM)),
                session.setBreakpoints(dirSource.resolve("Other.x").toString(), List.of(new Line(4, 1))));
    }

    @Test
    public void aStoppedFramesVariablesHaveTheFieldsOfObjectsAndTheElementsOfArrays()
            throws Exception {
        Path  source  = compile("Vars", VARS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Vars"));
        assertTrue(session.setBreakpoints(source.toString(), List.of(new Line(1, 21))).getFirst().verified());

        Control control = run("Vars", new StringWriter(), session);
        try (control) {
            Stop                 stop   = stops.next();
            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            assertEquals("Vars.report", frames.get(0).name());

            List<Scope> scopes = session.scopes(frames.get(0).id());
            assertEquals(1, scopes.size());
            assertEquals(SessionDebugger.LOCALS, scopes.getFirst().name());

            List<Variable> locals = read(session, scopes.getFirst().variablesReference());
            assertEquals(List.of("this", "point", "ints", "text", "sum", "console"),
                    locals.stream().map(Variable::name).toList());
            Map<String, Variable> report = byName(locals);
            assertEquals("7", report.get("sum").value());
            assertEquals("Int", report.get("sum").type());
            assertEquals(0, report.get("sum").variablesReference());
            assertEquals("\"hi \\\"there\\\"\"", report.get("text").value());

            Variable point = report.get("point");
            assertEquals("Vars:Point", point.type());
            assertEquals("Vars:Point", point.value());
            Map<String, Variable> fields = byName(read(session, point.variablesReference()));
            assertEquals("3", fields.get("x").value());
            assertEquals("4", fields.get("y").value());
            assertEquals("Int", fields.get("y").type());

            // the answer comes on another thread: what the host does with it never runs while
            // the debugger, which the host's other requests need, holds its lock
            var held = new CompletableFuture<Boolean>();
            synchronized (session) {
                session.variables(point.variablesReference())
                        .whenComplete((_, _) -> held.complete(Thread.holdsLock(session)));
            }
            assertFalse(held.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));

            Variable ints = report.get("ints");
            assertEquals("Int[3]", ints.value());
            assertEquals(List.of("[0]=1", "[1]=2", "[2]=3"),
                    read(session, ints.variablesReference()).stream()
                            .map(element -> element.name() + '=' + element.value()).toList());

            // the caller's frame has its own locals; another service's state is not expanded
            Map<String, Variable> run = byName(read(session,
                    session.scopes(frames.get(1).id()).getFirst().variablesReference()));
            assertEquals("Vars.run", frames.get(1).name());
            assertEquals(0, run.get("counter").variablesReference());
            assertNotEquals(0, run.get("point").variablesReference());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            // references last until the program resumes
            assertTrue(read(session, point.variablesReference()).isEmpty());
            assertTrue(session.scopes(frames.get(0).id()).isEmpty());
        }
    }

    @Test
    public void theListenerReadsVariablesOnTheStoppedFibersOwnThread() throws Exception {
        Path source  = compile("Reads", TWICE.replace("Twice", "Reads"));
        var  read    = new CompletableFuture<List<Variable>>();
        var  ref     = new AtomicReference<SessionDebugger>();
        var  session = new SessionDebugger(stop -> {
            SessionDebugger self  = ref.get();
            int             frame = self.stackTrace(stop.threadId()).getFirst().id();
            self.variables(self.scopes(frame).getFirst().variablesReference())
                    .whenComplete((vars, e) -> {
                        if (e == null) {
                            read.complete(vars);
                        } else {
                            read.completeExceptionally(e);
                        }
                    });
        });
        ref.set(session);
        session.addModule(input().loadModule("Reads"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 12)));

        Control control = run("Reads", new StringWriter(), session);
        try (control) {
            Map<String, Variable> vars = byName(read.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));
            assertEquals("21", vars.get("n").value());
            assertEquals("42", vars.get("result").value());
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(42L, control.result());
        }
    }

    @Test
    public void assertDebugStopsTheProgram() throws Exception {
        compile("Pauses", """
                module Pauses {
                    void run() {
                        @Inject Console console;
                        assert:debug;
                        console.print("resumed");
                    }
                }
                """);
        Stops        stops   = new Stops();
        var          session = new SessionDebugger(stops);
        StringWriter output  = new StringWriter();
        Control      control = run("Pauses", output, session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("assert:debug", stop.description());
            assertEquals(4, session.stackTrace(stop.threadId()).getFirst().line());
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertTrue(output.toString().contains("resumed"), output.toString());
        }
    }

    @Test
    public void whileStoppedAFiberOfAnotherServiceWaitsAtItsNextLine() throws Exception {
        Path source = compile("Holds", """
                module Holds {
                    service Spinner {
                        Int spins;

                        void spin() {
                            for (Int count = 0; count >= 0; ++count) {
                                ++spins;
                            }
                        }
                    }

                    void run() {
                        Spinner spinner = new Spinner();
                        spinner.spin^();
                        @Inject Console console;
                        console.print("spinning");
                    }
                }
                """);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Holds"));
        assertTrue(session.setBreakpoints(source.toString(), List.of(new Line(1, 16))).getFirst().verified());

        Control control = run("Holds", new StringWriter(), session);
        try (control) {
            Stop stop = stops.next();
            // the spinning fiber, on a thread of its own, reaches a line of its loop and waits there
            assertTimeoutPreemptively(RUN_LIMIT, () -> {
                while (session.threads().size() < 2) {
                    Thread.sleep(10);
                }
            });
            int nSpinner = session.threads().get(1).id();
            assertEquals("Spinner.spin", session.stackTrace(nSpinner).getFirst().name());
            assertEquals(stop.threadId(), session.threads().getFirst().id());

            session.resume();
            // the spinner never ends on its own; closing the run ends it
            assertTimeoutPreemptively(RUN_LIMIT, control::close);
            assertFalse(control.running());
        }
    }

    @Test
    public void closingAStoppedRunEndsItWhereItIsAndTheNextRunHasNoDebugger() throws Exception {
        Path source  = compile("Closes", TWICE.replace("Twice", "Closes"));
        var  stops   = new Stops();
        var  session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Closes"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 11)));

        StringWriter output  = new StringWriter();
        Control      control = run("Closes", output, session);
        stops.next();
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFalse(control.running());
        assertFalse(session.isStopped());
        // the program ended where it stopped, without running on to its end
        assertEquals(null, control.result());
        assertFalse(output.toString().contains("after"), output.toString());

        // assert:debug continues again: the run's debugger was replaced when the run ended
        compile("After", """
                module After {
                    Int run() {
                        assert:debug;
                        return 3;
                    }
                }
                """);
        Control after = run("After", new StringWriter(), null);
        try (after) {
            assertTimeoutPreemptively(RUN_LIMIT, after::join);
            assertEquals(3L, after.result());
        }
    }

    /**
     * The stops a session reports, in order.
     */
    private static class Stops
            implements SessionDebugger.Listener {
        @Override
        public void stopped(Stop stop) {
            f_queue.add(stop);
        }

        Stop next() throws InterruptedException {
            Stop stop = f_queue.poll(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
            assertNotNull(stop, "the program did not stop");
            return stop;
        }

        private final BlockingQueue<Stop> f_queue = new LinkedBlockingQueue<>();
    }

    private static List<Variable> read(SessionDebugger session, int nReference) throws Exception {
        return session.variables(nReference).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    private static Map<String, Variable> byName(List<Variable> vars) {
        return vars.stream().collect(Collectors.toMap(Variable::name, Function.identity()));
    }

    private Control run(String moduleName, StringWriter output, SessionDebugger session) {
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), moduleName, null, EntryPoint.RUN,
                new PrintWriter(output, true), ConsoleInput.NONE, dirSource.toFile(), session, errs);
        assertNotNull(control, () -> moduleName + " did not start: " + errs.getErrors());
        return control;
    }

    /**
     * Write the module's source to its own file and compile it into the test modules.
     *
     * @return the source file
     */
    private Path compile(String moduleName, String source) throws IOException {
        Path file = dirSource.resolve(moduleName + ".x");
        Files.writeString(file, source);
        ErrorList errs = new ErrorList(25);
        assertTrue(support.compile(file.toFile(), null, new DirRepository(dirOut.toFile(), false), errs),
                () -> "compile failed: " + errs.getErrors());
        return file;
    }

    private static ModuleRepository input() {
        return new DirRepository(dirOut.toFile(), true);
    }

    /**
     * A module whose run() passes an object, an array and a string to a method, which stops on line
     * 21; run() also holds another service.
     */
    private static final String VARS = """
            module Vars {
                const Point(Int x, Int y);

                service Counter {
                    Int count = 5;
                }

                void run() {
                    @Inject Console console;
                    Counter counter = new Counter();
                    Point   point   = new Point(3, 4);
                    Int[]   ints    = [1, 2, 3];
                    String  text    = "hi \\"there\\"";
                    report(point, ints, text);
                    console.print($"done {counter.count}");
                }

                void report(Point point, Int[] ints, String text) {
                    Int sum = point.x + point.y;
                    @Inject Console console;
                    console.print($"{sum} {ints.size} {text}");
                }
            }
            """;

    /**
     * A module whose run() calls a method on line 5; the method's code is on lines 11 and 12.
     */
    private static final String TWICE = """
            module Twice {
                Int run() {
                    @Inject Console console;
                    console.print("before");
                    Int n = twice(21);
                    console.print($"after {n}");
                    return n;
                }

                Int twice(Int n) {
                    Int result = n * 2;
                    return result;
                }
            }
            """;
}
