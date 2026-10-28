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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
import org.xvm.runtime.SessionDebugger.Step;
import org.xvm.runtime.SessionDebugger.Stop;
import org.xvm.runtime.SessionDebugger.Variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs modules compiled from source files under a {@link SessionDebugger}: breakpoints by file and
 * line, how they are verified, the stack of a stop, the variables of its frames and paths to
 * values in them, stepping over,
 * into and out of calls, stops at thrown exceptions, pausing and stopping on entry, assert:debug,
 * other fibers waiting while the program is stopped, fibers yielding to each other while
 * breakpoints are set, and closing a stopped run. The compiled XDK modules come from the test
 * resources, as for {@link EmbeddingSupportTest}.
 */
public class SessionDebuggerTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    @TempDir
    static Path outputDir;

    @TempDir
    Path sourceDir;

    private static EmbeddingSupport support;

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
     * A module whose run() prints "spinning" and then loops on lines 6 and 7 forever.
     */
    private static final String SPINS = """
            module Spins {
                void run() {
                    @Inject Console console;
                    console.print("spinning");
                    Int count = 0;
                    while (True) {
                        ++count;
                    }
                }
            }
            """;

    /**
     * A module whose run() catches the IllegalState that check() throws on line 14 and prints
     * "caught", then calls check() again, which throws an IllegalArgument on line 16 that nothing
     * catches.
     */
    private static final String THROWS = """
            module Throws {
                Int run() {
                    @Inject Console console;
                    try {
                        check(0);
                    } catch (IllegalState e) {
                        console.print("caught");
                    }
                    return check(1);
                }

                Int check(Int n) {
                    if (n == 0) {
                        throw new IllegalState("caught one");
                    }
                    throw new IllegalArgument("uncaught one");
                }
            }
            """;

    /**
     * A module whose run() calls add() on lines 4 and 5, prints "3 6" on line 6 and returns 6 on
     * line 7; add()'s code is on lines 11 and 12.
     */
    private static final String STEPS = """
            module Steps {
                Int run() {
                    @Inject Console console;
                    Int a = add(1, 2);
                    Int b = add(a, 3);
                    console.print($"{a} {b}");
                    return b;
                }

                Int add(Int x, Int y) {
                    Int sum = x + y;
                    return sum;
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
                session.setBreakpoints(sourceDir.resolve("Other.x").toString(), List.of(new Line(4, 1))));
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
            assertEquals(List.of("this", "point", "ints", "text", "sum", "console"), locals.stream().map(Variable::name).toList());
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
                session.variables(point.variablesReference()).whenComplete((_, _) -> held.complete(Thread.holdsLock(session)));
            }
            assertFalse(held.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));

            Variable ints = report.get("ints");
            assertEquals("Int[3]", ints.value());
            List<Variable> elements = read(session, ints.variablesReference());
            assertEquals(List.of("[0]=1", "[1]=2", "[2]=3"), elements.stream().map(e -> e.name() + '=' + e.value()).toList());

            // the caller's frame has its own locals; another service's state is not expanded
            Map<String, Variable> run = byName(read(session, session.scopes(frames.get(1).id()).getFirst().variablesReference()));
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
    public void pathsToVariablesFieldsAndElementsEvaluateWithoutRunningCode() throws Exception {
        Path  source  = compile("Paths", VARS.replace("Vars", "Paths"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Paths"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 21)));

        Control control = run("Paths", new StringWriter(), session);
        try (control) {
            Stop                 stop   = stops.next();
            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            int                  report = frames.get(0).id();

            assertEquals("3", evaluate(session, report, "point.x").value());
            assertEquals("Int", evaluate(session, report, "point.x").type());
            assertEquals("3", evaluate(session, report, " ints[2] ").value());
            assertEquals("7", evaluate(session, report, "sum").value());
            Variable point = evaluate(session, report, "point");
            assertEquals("point", point.name());
            assertNotEquals(0, point.variablesReference());
            assertEquals("4", byName(read(session, point.variablesReference())).get("y").value());

            // what cannot be evaluated says why
            assertFails("There is no variable \"nope\" in this frame.", session, report, "nope");
            assertFails("ints[3]: index 3 is out of bounds for size 3.", session, report, "ints[3]");
            assertFails("\"ints[1]x\" is not evaluated: only variables, their fields and array elements are,"
                    + " such as point.x or ints[1].", session, report, "ints[1]x");
            assertFails("point.z: Paths:Point has no field \"z\".", session, report, "point.z");
            assertFails("sum.x: Int has no fields to read.", session, report, "sum.x");
            assertFails("\"point.x + 1\" is not evaluated: only variables, their fields and array elements are,"
                    + " such as point.x or ints[1].", session, report, "point.x + 1");

            // the caller's frame: another service's state is not read
            int run = frames.get(1).id();
            assertFails("counter.count: another service's state is read only while it is stopped.", session, run,
                    "counter.count");

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertFails("The frame is not one of the stopped program's, so nothing can be evaluated in it.", session,
                    report, "sum");
        }
    }

    @Test
    public void aBareNameIsAFieldOfThisWhenNoVariableHasIt() throws Exception {
        Path source = compile("Boxes", """
                module Boxes {
                    class Box(Int size) {
                        Int doubled() {
                            return size * 2;
                        }
                    }
                    Int run() = new Box(21).doubled();
                }
                """);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Boxes"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 4)));

        Control control = run("Boxes", new StringWriter(), session);
        try (control) {
            Stop stop  = stops.next();
            int  frame = session.stackTrace(stop.threadId()).getFirst().id();
            assertEquals("21", evaluate(session, frame, "size").value());
            assertEquals("21", evaluate(session, frame, "this.size").value());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(42L, control.result());
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
    public void stepsGoOverAndIntoCallsLineByLineAndOverAReturnToTheCallersNextLine()
            throws Exception {
        Path  source  = compile("Steps", STEPS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Steps"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 4)));

        StringWriter output  = new StringWriter();
        Control      control = run("Steps", output, session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("Steps.run:4", where(session, stop));

            // over the call on line 4
            stop = step(session, stops, stop, Step.OVER);
            assertEquals("step", stop.reason());
            assertEquals(List.of(), stop.breakpointIds());
            assertEquals("Steps.run:5", where(session, stop));

            // into the call on line 5, with its caller below
            stop = step(session, stops, stop, Step.IN);
            assertEquals("Steps.add:11", where(session, stop));
            assertEquals("Steps.run", session.stackTrace(stop.threadId()).get(1).name());

            stop = step(session, stops, stop, Step.OVER);
            assertEquals("Steps.add:12", where(session, stop));

            // over the return, to the caller's next line
            stop = step(session, stops, stop, Step.OVER);
            assertEquals("Steps.run:6", where(session, stop));
            assertFalse(output.toString().contains("3 6"), output.toString());

            // a fiber that does not wait cannot be stepped, and the program stays stopped
            assertFalse(session.step(stop.threadId() + 1000, Step.OVER));
            assertTrue(session.isStopped());

            stop = step(session, stops, stop, Step.OVER);
            assertEquals("Steps.run:7", where(session, stop));
            assertTrue(output.toString().contains("3 6"), output.toString());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(6L, control.result());
        }
    }

    @Test
    public void aStepOutStopsAtTheCallersNextLineAndABreakpointOnTheWayEndsAStep() throws Exception {
        Path  source  = compile("StepsOut", STEPS.replace("Steps", "StepsOut"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("StepsOut"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 11)));

        Control control = run("StepsOut", new StringWriter(), session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("StepsOut.add:11", where(session, stop));

            stop = step(session, stops, stop, Step.OUT);
            assertEquals("step", stop.reason());
            assertEquals("StepsOut.run:5", where(session, stop));

            // stepping over line 5 runs its call into add, which stops at the breakpoint there
            stop = step(session, stops, stop, Step.OVER);
            assertEquals("breakpoint", stop.reason());
            assertEquals(List.of(1), stop.breakpointIds());
            assertEquals("StepsOut.add:11", where(session, stop));

            // the step ended there: resuming runs the program to its end
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(6L, control.result());
        }
    }

    @Test
    public void anUncaughtExceptionStopsWhereItIsThrownWithTheExceptionAmongTheVariables()
            throws Exception {
        compile("Throws", THROWS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Throws"));
        session.setExceptionStops(false, true);

        StringWriter output  = new StringWriter();
        Control      control = run("Throws", output, session);
        try (control) {
            // the exception that run() catches does not stop the program
            Stop stop = stops.next();
            assertTrue(output.toString().contains("caught"), output.toString());
            assertEquals("exception", stop.reason());
            assertEquals("Throws.check:16", where(session, stop));
            assertTrue(stop.description().startsWith("IllegalArgument"), stop.description());
            assertTrue(stop.description().endsWith(": uncaught one"), stop.description());

            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            List<Variable>       vars   = read(session, session.scopes(frames.getFirst().id()).getFirst().variablesReference());
            assertEquals(SessionDebugger.EXCEPTION, vars.getFirst().name());
            assertTrue(vars.getFirst().type().contains("IllegalArgument"), vars.getFirst().type());
            assertEquals("1", byName(vars).get("n").value());

            // it stops the program once, not again as it leaves run()
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(null, control.result());
            assertEquals(null, stops.poll());
        }
    }

    @Test
    public void everyThrownExceptionStopsWhenAskedAlsoOneThatIsCaught() throws Exception {
        compile("ThrowsAll", THROWS.replace("Throws", "ThrowsAll"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("ThrowsAll"));
        session.setExceptionStops(true, false);

        StringWriter output  = new StringWriter();
        Control      control = run("ThrowsAll", output, session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("ThrowsAll.check:14", where(session, stop));
            assertTrue(stop.description().endsWith(": caught one"), stop.description());
            assertFalse(output.toString().contains("caught"), output.toString());

            session.resume();
            stop = stops.next();
            assertEquals("ThrowsAll.check:16", where(session, stop));
            assertTrue(output.toString().contains("caught"), output.toString());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(null, stops.poll());
        }
    }

    @Test
    public void pauseStopsARunningProgramAtTheNextLineItReaches() throws Exception {
        compile("Spins", SPINS);
        Stops        stops   = new Stops();
        var          session = new SessionDebugger(stops);
        StringWriter output  = new StringWriter();
        Control      control = run("Spins", output, session);
        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, () -> {
                while (!output.toString().contains("spinning")) {
                    Thread.sleep(10);
                }
            });
            session.pause();
            Stop stop = stops.next();
            assertEquals("pause", stop.reason());
            StackFrameInfo top = session.stackTrace(stop.threadId()).getFirst();
            assertEquals("Spins.run", top.name());
            assertTrue(top.line() == 6 || top.line() == 7, "stopped in the loop: " + top.line());

            // a pause while stopped does nothing: the program runs on after it resumes
            session.pause();
            session.resume();
            Thread.sleep(300);
            assertEquals(null, stops.poll());
            assertFalse(session.isStopped());
        }
        assertFalse(control.running());
    }

    @Test
    public void stopOnEntryStopsAtTheProgramsFirstLine() throws Exception {
        compile("Entry", TWICE.replace("Twice", "Entry"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        // the program's module is known, so the libraries that run before it do not stop it
        session.addModule(input().loadModule("Entry"));
        session.stopOnEntry();

        StringWriter output  = new StringWriter();
        Control      control = run("Entry", output, session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("entry", stop.reason());
            StackFrameInfo top = session.stackTrace(stop.threadId()).getFirst();
            assertEquals("Entry.run", top.name());
            assertTrue(top.line() <= 4, "stopped before the first print: " + top.line());
            assertFalse(output.toString().contains("before"), output.toString());

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
            int spinner = session.threads().get(1).id();
            assertEquals("Spinner.spin", session.stackTrace(spinner).getFirst().name());
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
    private static class Stops implements SessionDebugger.Listener {
        private final BlockingQueue<Stop> queue = new LinkedBlockingQueue<>();

        @Override
        public void stopped(Stop stop) {
            queue.add(stop);
        }

        /**
         * @return the next stop, if there is one already
         */
        Stop poll() {
            return queue.poll();
        }

        Stop next() throws InterruptedException {
            Stop stop = queue.poll(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
            assertNotNull(stop, "the program did not stop");
            return stop;
        }
    }

    /**
     * Step the stopped fiber and wait for the stop where the step ends.
     */
    private static Stop step(SessionDebugger session, Stops stops, Stop stop, Step step)
            throws InterruptedException {
        assertTrue(session.step(stop.threadId(), step));
        return stops.next();
    }

    /**
     * @return the method and line of the stopped fiber's innermost frame, as "Module.method:line"
     */
    private static String where(SessionDebugger session, Stop stop) {
        StackFrameInfo frame = session.stackTrace(stop.threadId()).getFirst();
        return frame.name() + ':' + frame.line();
    }

    private static List<Variable> read(SessionDebugger session, int reference) throws Exception {
        return session.variables(reference).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    private static Variable evaluate(SessionDebugger session, int frame, String path) throws Exception {
        return session.evaluate(frame, path).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Assert that evaluating the path fails with the specified reason.
     */
    private static void assertFails(String reason, SessionDebugger session, int frame, String path) {
        var failure = assertThrows(ExecutionException.class,
                () -> session.evaluate(frame, path).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));
        Throwable cause = failure.getCause() instanceof CompletionException wrapped ? wrapped.getCause() : failure.getCause();
        assertEquals(reason, cause.getMessage());
    }

    private static Map<String, Variable> byName(List<Variable> vars) {
        return vars.stream().collect(Collectors.toMap(Variable::name, Function.identity()));
    }

    private Control run(String moduleName, StringWriter output, SessionDebugger session) {
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), moduleName, null, EntryPoint.RUN,
                new PrintWriter(output, true), ConsoleInput.NONE, sourceDir.toFile(), session, errs);
        assertNotNull(control, () -> moduleName + " did not start: " + errs.getErrors());
        return control;
    }

    /**
     * Write the module's source to its own file and compile it into the test modules.
     *
     * @return the source file
     */
    private Path compile(String moduleName, String source) throws IOException {
        Path file = sourceDir.resolve(moduleName + ".x");
        Files.writeString(file, source);
        ErrorList errs = new ErrorList(25);
        assertTrue(support.compile(file.toFile(), null, new DirRepository(outputDir.toFile(), false), errs),
                () -> "compile failed: " + errs.getErrors());
        return file;
    }

    private static ModuleRepository input() {
        return new DirRepository(outputDir.toFile(), true);
    }
}
