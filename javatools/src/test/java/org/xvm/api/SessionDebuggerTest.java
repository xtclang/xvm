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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
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
import org.xvm.runtime.SessionDebugger.ExceptionInfo;
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
 * values in them, code run on a stopped fiber, conditions, hit counts and logpoints, stepping over,
 * into and out of calls, stops at thrown exceptions, of all classes or some, and what they tell of
 * the exception, pausing and stopping on entry, assert:debug,
 * other fibers waiting while the program is stopped, with their variables, fibers yielding to each
 * other while breakpoints are set and while one steps, steps into and out of another service's
 * calls, handles that last only until the program resumes, reads asked for as it resumes, and
 * closing a stopped run. The compiled XDK modules come from the test
 * resources, as for {@link EmbeddingSupportTest}.
 */
public class SessionDebuggerTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    /**
     * Why text that is not an expression the debugger evaluates is not evaluated, after the text.
     */
    private static final String NOT_EVALUATED = " is not evaluated: only variables, their fields and array elements,"
            + " literals and operators are, such as point.x + 1 or ints[i] > 0.";

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

    /**
     * A module whose run() loops five times over line 7, with i from 0 to 4 and even true when i
     * is, prints "sum 10" and returns 10.
     */
    private static final String EXPRS = """
            module Exprs {
                enum Color {Red, Green}

                const Point(Int x, Int y);

                const Named(String name) {
                    @Override
                    String toString() = $"<{name}>";
                }

                Int run() {
                    @Inject Console console;
                    Int     i       = 7;
                    Int8    small   = 120;
                    Int8    wrapped = small + 10;
                    UInt8   octet   = 250;
                    Float64 f       = 1.5;
                    Float64 product = f * 3;
                    Dec64   d       = 0.1;
                    Dec64   sum     = d + d + d;
                    Char    c       = 'x';
                    String  s       = "ab";
                    Boolean flag    = True;
                    Color   color   = Color.Green;
                    String? none    = Null;
                    Int[]   ints    = [1, 2, 3];
                    Point   point   = new Point(3, 4);
                    Named   named   = new Named("n");
                    console.print($"{i} {wrapped} {octet} {product} {sum} {c} {s} {flag} {color} {none} {ints} {point} {named}");
                    return i;
                }
            }
            """;
    /**
     * A module whose run() makes a Point on line 7 and adds its y to the sum on line 8, three
     * times, with y from 1 to 3, and returns 6.
     */
    private static final String STALE = """
            module Stale {
                const Point(Int x, Int y);

                Int run() {
                    Int sum = 0;
                    for (Int i : 0 ..< 3) {
                        Point point = new Point(i, i + 1);
                        sum += point.y;
                    }
                    return sum;
                }
            }
            """;

    /**
     * A module whose run() calls a service's add() on lines 13 and 14 and returns 7; add()'s code
     * is on lines 6 and 7.
     */
    private static final String CALLS = """
            module Calls {
                service Counter {
                    Int count;

                    Int add(Int n) {
                        count += n;
                        return count;
                    }
                }

                Int run() {
                    Counter counter = new Counter();
                    Int     first   = counter.add(2);
                    Int     second  = counter.add(3);
                    return first + second;
                }
            }
            """;

    /**
     * A module whose run() starts a service that loops on lines 6 and 7 forever, then prints
     * "spinning" on line 16 and "stepped" on line 17.
     */
    private static final String HOLDS = """
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
                    console.print("stepped");
                }
            }
            """;

    /**
     * A module whose run() makes a Box and a Counter service and calls show(box, 5), which prints
     * the box's count on line 37 and returns 5; run() returns that plus the box's count. A Box's
     * bump() adds to its count on line 6, its fail() throws an IllegalState, and its toString()
     * shows its count.
     */
    private static final String RUNS = """
            module Runs {
                class Box {
                    Int count;

                    Int bump(Int by) {
                        count += by;
                        return count;
                    }

                    Int fail() {
                        throw new IllegalState("no");
                    }

                    @Override
                    String toString() = $"Box({count})";
                }

                service Counter {
                    Int count;

                    Int add(Int n) {
                        count += n;
                        return count;
                    }
                }

                Int run() {
                    @Inject Console console;
                    Box     box     = new Box();
                    Counter counter = new Counter();
                    Int     total   = show(box, 5);
                    return total + box.count;
                }

                Int show(Box box, Int n) {
                    @Inject Console console;
                    console.print($"box {box.count}");
                    return n;
                }
            }
            """;

    /**
     * A module whose run() prints "stopped" on line 5 and returns 3; forever() loops forever, and
     * so does stubborn(), which catches every exception that its loop throws.
     */
    private static final String ENDS = """
            module Ends {
                Int run() {
                    @Inject Console console;
                    Int n = 3;
                    console.print("stopped");
                    return n;
                }

                Int forever(Int n) {
                    Int count = n;
                    for (Int i = 0; i >= 0; ++i) {
                        ++count;
                    }
                    return count;
                }

                Int stubborn(Int n) {
                    Int count = n;
                    for (Int i = 0; i >= 0; ++i) {
                        try {
                            ++count;
                        } catch (Exception e) {
                            ++count;
                        }
                    }
                    return count;
                }
            }
            """;

    private static final String LOOP = """
            module Loop {
                Int run() {
                    @Inject Console console;
                    Int sum = 0;
                    for (Int i : 0 ..< 5) {
                        Boolean even = i % 2 == 0;
                        sum += i;
                    }
                    console.print($"sum {sum}");
                    return sum;
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
            assertFails("\"ints[1]x\"" + NOT_EVALUATED, session, report, "ints[1]x");
            assertFails("point.z: Paths:Point has no field \"z\".", session, report, "point.z");
            assertFails("sum.x: Int has no fields to read.", session, report, "sum.x");
            assertFails("\"report(point, ints, text)\"" + NOT_EVALUATED, session, report, "report(point, ints, text)");

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
    public void operatorsRunTheRuntimesOwnOperationsAndComputeWhatTheProgramDoes() throws Exception {
        Path  source  = compile("Exprs", EXPRS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Exprs"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 29)));

        StringWriter output  = new StringWriter();
        Control      control = run("Exprs", output, session);
        try (control) {
            Stop stop  = stops.next();
            int  frame = session.stackTrace(stop.threadId()).getFirst().id();

            // what the program computed, and the same operations evaluated
            assertEquals(evaluate(session, frame, "wrapped").value(), evaluate(session, frame, "small + 10").value());
            assertEquals("Int8", evaluate(session, frame, "small + 10").type());
            assertEquals(evaluate(session, frame, "product").value(), evaluate(session, frame, "f * 3").value());
            assertEquals(evaluate(session, frame, "sum").value(), evaluate(session, frame, "d + d + d").value());

            // Ecstasy's precedence, and each operator
            Map<String, String> values = new LinkedHashMap<>();
            values.put("i + 1", "8");
            values.put("i * 2 - 3 % 2", "13");
            values.put("(i + 1) * 2", "16");
            values.put("-i", "-7");
            values.put("~i", "-8");
            values.put("i << 2", "28");
            values.put("i & 3 | 8", "11");
            values.put("i ^ 1", "6");
            values.put("1 + 2", "3");
            values.put("octet > 200", "True");
            values.put("c == 'x' && c < 'y'", "True");
            values.put("s == \"ab\" && s < \"b\"", "True");
            values.put("flag && i > 5", "True");
            values.put("!flag || i < 0", "False");
            values.put("flag & False", "False");
            values.put("color == color && color != Null", "True");
            values.put("none == Null", "True");
            values.put("none ?: s", "\"ab\"");
            values.put("i > 5 ? i : 0", "7");
            values.put("1 < i < 10", "True");
            values.put("ints[i - 6]", "2");
            values.put("ints[1] + ints[2]", "5");
            values.put("point.x * point.y", "12");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                assertEquals(entry.getValue(), evaluate(session, frame, entry.getKey()).value(), entry.getKey());
            }
            assertEquals("IntLiteral", evaluate(session, frame, "1 + 2").type());

            // what is not evaluated says why
            assertFails("i / 0 throws OutOfBounds: numbers.Int64 overflow", session, frame, "i / 0");
            assertFails("small + i: + takes two values of the same type, not Int8 and Int.", session, frame, "small + i");
            assertFails("small + 1000: 1000 is out of range for Int8.", session, frame, "small + 1000");
            assertFails("point + 1: + is not evaluated on Exprs:Point without running the program's code.", session,
                    frame, "point + 1");
            assertFails("point == point: == is not evaluated on Exprs:Point without running the program's code.", session,
                    frame, "point == point");
            assertFails("i is Int, not a Boolean.", session, frame, "!i");
            assertFails("\"1 < i > 0\"" + NOT_EVALUATED, session, frame, "1 < i > 0");
            assertFails("\"i = 3\"" + NOT_EVALUATED, session, frame, "i = 3");

            // code that an operation would run is not run, and leaves no trace: a toString() of the
            // program's own, and those the runtime does not make natively, which a string's + calls
            assertFails("s + named would run Named.toString(), which the debugger does not run here.", session, frame,
                    "s + named");
            assertFails("s + i would run Stringable.toString(), which the debugger does not run here.", session, frame,
                    "s + i");
            assertFails("s + s would run String.toString(), which the debugger does not run here.", session, frame, "s + s");

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(7L, control.result());
            assertTrue(output.toString().contains("(x=3, y=4) <n>"), output.toString());
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
    public void aConditionalBreakpointStopsOnlyWhereItsConditionIsTrueAndItsHitCountIsReached()
            throws Exception {
        Path  source  = compile("Conditions", LOOP.replace("Loop", "Conditions"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Conditions"));
        assertEquals(List.of(new Breakpoint(1, 7, true, null)),
                session.setBreakpoints(source.toString(), List.of(new Line(1, 7, " even ", null, null))));

        StringWriter output  = new StringWriter();
        Control      control = run("Conditions", output, session);
        try (control) {
            // the hits where i is even: 0, 2 and 4
            for (String i : List.of("0", "2", "4")) {
                Stop stop = stops.next();
                assertEquals(List.of(1), stop.breakpointIds());
                assertEquals(i, evaluate(session, session.stackTrace(stop.threadId()).getFirst().id(), "i").value());
                session.resume();
            }
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(10L, control.result());
            assertEquals(null, stops.poll());
        }

        // the hit count counts the hits whose condition holds: the second even i is 2
        compile("Counts", LOOP.replace("Loop", "Counts"));
        Stops counted = new Stops();
        var   again   = new SessionDebugger(counted);
        again.addModule(input().loadModule("Counts"));
        again.setBreakpoints(sourceDir.resolve("Counts.x").toString(), List.of(new Line(2, 7, "even", "2", null)));
        try (Control second = run("Counts", new StringWriter(), again)) {
            Stop stop = counted.next();
            assertEquals("2", evaluate(again, again.stackTrace(stop.threadId()).getFirst().id(), "i").value());
            again.resume();
            assertTimeoutPreemptively(RUN_LIMIT, second::join);
            assertEquals(null, counted.poll());
        }
    }

    @Test
    public void conditionsAndLogMessagesAreExpressions() throws Exception {
        Path  source  = compile("Operators", LOOP.replace("Loop", "Operators"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Operators"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 7, "i % 2 == 0 && i > 0", null, null),
                new Line(2, 6, null, null, "i={i}, twice={i * 2}, odd={i % 2 != 0}")));

        try (Control control = run("Operators", new StringWriter(), session)) {
            for (String i : List.of("2", "4")) {
                Stop stop = stops.next();
                assertEquals(i, evaluate(session, session.stackTrace(stop.threadId()).getFirst().id(), "i").value());
                session.resume();
            }
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(10L, control.result());
            assertEquals(null, stops.poll());
            assertEquals(List.of("i=0, twice=0, odd=False", "i=1, twice=2, odd=True", "i=2, twice=4, odd=False",
                                 "i=3, twice=6, odd=True", "i=4, twice=8, odd=False"), stops.logged);
        }
    }

    @Test
    public void aLogpointLogsItsMessageWithTheValuesOfItsPathsAndDoesNotStop() throws Exception {
        Path  source  = compile("Logs", LOOP.replace("Loop", "Logs"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Logs"));
        session.setBreakpoints(source.toString(),
                List.of(new Line(1, 7, null, null, "i={i}, sum={sum} {nope}"), new Line(2, 9, null, null, "sum {sum")));

        StringWriter output  = new StringWriter();
        Control      control = run("Logs", output, session);
        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(10L, control.result());
            assertEquals(null, stops.poll());

            // a path that names nothing says why, and a brace without its pair is text
            String nope = "<There is no variable \"nope\" in this frame.>";
            assertEquals(List.of("i=0, sum=0 " + nope, "i=1, sum=0 " + nope, "i=2, sum=1 " + nope,
                                 "i=3, sum=3 " + nope, "i=4, sum=6 " + nope, "sum {sum"), stops.logged);
            assertTrue(output.toString().contains("sum 10"), output.toString());
        }
    }

    @Test
    public void aConditionThatCannotBeEvaluatedStopsAndSaysWhy() throws Exception {
        Path  source  = compile("BadCondition", LOOP.replace("Loop", "BadCondition"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("BadCondition"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 7, "sum", null, null)));

        try (Control control = run("BadCondition", new StringWriter(), session)) {
            Stop stop = stops.next();
            assertEquals("breakpoint", stop.reason());
            assertEquals("The condition cannot be evaluated: sum is Int, not a Boolean.", stop.description());
            session.terminate();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
        }
    }

    @Test
    public void aConditionHitCountOrLogMessageThatIsNotUnderstoodIsNotInstalled() throws Exception {
        Path source  = compile("NotUnderstood", LOOP.replace("Loop", "NotUnderstood"));
        var  session = new SessionDebugger(_ -> {});
        session.addModule(input().loadModule("NotUnderstood"));

        assertEquals(List.of(new Breakpoint(1, 7, false, "The condition \"i >\"" + NOT_EVALUATED),
                             new Breakpoint(2, 7, false, "The hit count \"often\" is not N, to stop at the Nth hit,"
                                     + " or >= N, > N or % N, for a number N."),
                             new Breakpoint(3, 7, false, "The hit count \"% 0\" is not N, to stop at the Nth hit,"
                                     + " or >= N, > N or % N, for a number N."),
                             new Breakpoint(4, 7, false, "In the log message, \"i +\"" + NOT_EVALUATED),
                             new Breakpoint(5, 7, true, null)),
                session.setBreakpoints(source.toString(), List.of(new Line(1, 7, "i >", null, null),
                        new Line(2, 7, null, "often", null), new Line(3, 7, null, "% 0", null),
                        new Line(4, 7, null, null, "{i +}"), new Line(5, 7, "", " ", ""))));
    }

    @Test
    public void theExceptionThatStoppedTheProgramHasItsTypeTextStackTraceAndWhetherItIsCaught()
            throws Exception {
        compile("ThrowsInfo", THROWS.replace("Throws", "ThrowsInfo"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("ThrowsInfo"));
        session.setExceptionStops(true, false);

        try (Control control = run("ThrowsInfo", new StringWriter(), session)) {
            Stop          stop   = stops.next();
            ExceptionInfo caught = session.exceptionInfo(stop.threadId());
            assertEquals("IllegalState", caught.type());
            assertEquals("caught one", caught.text());
            // the stack where it was thrown, as the call stack shows it, without the runner that
            // hosts the program
            assertEquals("\n\tat check(Int) (ThrowsInfo.x:14)\n\tat run() (ThrowsInfo.x:5)\n\tat ^ThrowsInfo (run())\n",
                    caught.stackTrace());
            assertEquals(List.of("ThrowsInfo.check", "ThrowsInfo.run"),
                    session.stackTrace(stop.threadId()).stream().map(StackFrameInfo::name).toList());
            assertFalse(caught.uncaught());
            assertEquals(null, session.exceptionInfo(stop.threadId() + 1));

            session.resume();
            stop = stops.next();
            ExceptionInfo uncaught = session.exceptionInfo(stop.threadId());
            assertEquals("IllegalArgument", uncaught.type());
            assertEquals("uncaught one", uncaught.text());
            assertTrue(uncaught.uncaught());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(null, session.exceptionInfo(stop.threadId()));
        }
    }

    @Test
    public void exceptionStopsForSomeClassesStopForThemAndTheClassesThatExtendThem() throws Exception {
        // every thrown IllegalArgument: the caught IllegalState does not stop the program
        compile("ThrowsTyped", THROWS.replace("Throws", "ThrowsTyped"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("ThrowsTyped"));
        session.setExceptionStops(true, List.of("IllegalArgument", "OutOfBounds"), false, List.of());

        StringWriter output = new StringWriter();
        try (Control control = run("ThrowsTyped", output, session)) {
            Stop stop = stops.next();
            assertEquals("ThrowsTyped.check:16", where(session, stop));
            assertTrue(output.toString().contains("caught"), output.toString());
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(null, stops.poll());
        }

        // an uncaught Exception: IllegalArgument extends it
        compile("ThrowsBase", THROWS.replace("Throws", "ThrowsBase"));
        Stops based = new Stops();
        var   again = new SessionDebugger(based);
        again.addModule(input().loadModule("ThrowsBase"));
        again.setExceptionStops(false, List.of(), true, List.of("Exception"));
        try (Control control = run("ThrowsBase", new StringWriter(), again)) {
            Stop stop = based.next();
            assertEquals("ThrowsBase.check:16", where(again, stop));
            again.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(null, based.poll());
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
        Path  source  = compile("Holds", HOLDS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Holds"));
        assertTrue(session.setBreakpoints(source.toString(), List.of(new Line(1, 16))).getFirst().verified());

        Control control = run("Holds", new StringWriter(), session);
        Stop    stop    = stops.next();
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
        // the spinner never ends on its own; closing the run ends it. Not in a try-with-resources:
        // closing again after a close that timed out would wait without a limit
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFalse(control.running());
    }

    @Test
    public void anotherServicesWaitingFiberHasItsVariablesAndStandsStillAndAStepEndsInTheSteppedFiber()
            throws Exception {
        Path  source  = compile("Shares", HOLDS.replace("Holds", "Shares"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Shares"));
        assertTrue(session.setBreakpoints(source.toString(), List.of(new Line(1, 16))).getFirst().verified());

        StringWriter output  = new StringWriter();
        Control      control = run("Shares", output, session);
        Stop         stop    = stops.next();
        assertTimeoutPreemptively(RUN_LIMIT, () -> {
            while (session.threads().size() < 2) {
                Thread.sleep(10);
            }
        });

        // the spinner's frame has its variables, which its own thread reads, and its service's
        // state stands still while the program is stopped
        int                   spinner = session.threads().get(1).id();
        int                   frame   = session.stackTrace(spinner).getFirst().id();
        Map<String, Variable> spin    = byName(read(session, session.scopes(frame).getFirst().variablesReference()));
        assertTrue(spin.containsKey("count"), spin.keySet().toString());
        String spins = byName(read(session, spin.get("this").variablesReference())).get("spins").value();
        Thread.sleep(200);
        assertEquals(spins, evaluate(session, frame, "spins").value());

        // the spinner runs on during a step over in run(), which ends at run()'s next line
        Stop stepped = step(session, stops, stop, Step.OVER);
        assertEquals("step", stepped.reason());
        assertEquals("Shares.run:17", where(session, stepped));
        assertTrue(output.toString().contains("spinning"), output.toString());
        assertFalse(output.toString().contains("stepped"), output.toString());

        session.resume();
        // as above, the run is closed outside a try-with-resources
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFalse(control.running());
    }

    @Test
    public void aStepGoesIntoACallToAnotherServiceAndOutToItsCallerOnTheCallingFiber() throws Exception {
        Path  source  = compile("Calls", CALLS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Calls"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 13)));

        Control control = run("Calls", new StringWriter(), session);
        try (control) {
            Stop stop = stops.next();
            assertEquals("Calls.run:13", where(session, stop));

            // into the call: the service's own fiber stops, and its stack goes on with its caller's
            Stop into = step(session, stops, stop, Step.IN);
            assertEquals("Counter.add:6", where(session, into));
            assertNotEquals(stop.threadId(), into.threadId());
            List<StackFrameInfo> frames = session.stackTrace(into.threadId());
            assertEquals("Calls.run", frames.get(1).name());
            assertEquals(13, frames.get(1).line());

            // the service's frame has its variables and its service's state; the caller's frame,
            // of another fiber, has none
            int                   add    = frames.get(0).id();
            Map<String, Variable> locals = byName(read(session, session.scopes(add).getFirst().variablesReference()));
            assertEquals("2", locals.get("n").value());
            assertEquals("0", byName(read(session, locals.get("this").variablesReference())).get("count").value());
            assertEquals("0", evaluate(session, add, "count").value());
            assertTrue(session.scopes(frames.get(1).id()).isEmpty());

            // out of it, to the caller's next line, on the calling fiber
            Stop out = step(session, stops, into, Step.OUT);
            assertEquals("Calls.run:14", where(session, out));
            assertEquals(stop.threadId(), out.threadId());
            assertEquals("2", evaluate(session, session.stackTrace(out.threadId()).getFirst().id(), "first").value());

            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(7L, control.result());
        }
    }

    @Test
    public void aStopsFrameIdsAndReferencesAreNotTheNextStopsAndReadNothingThere() throws Exception {
        Path  source  = compile("Stale", STALE);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Stale"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 8)));

        Control control = run("Stale", new StringWriter(), session);
        try (control) {
            Stop     first  = stops.next();
            int      frame  = session.stackTrace(first.threadId()).getFirst().id();
            int      locals = session.scopes(frame).getFirst().variablesReference();
            Variable point  = byName(read(session, locals)).get("point");
            assertEquals("1", byName(read(session, point.variablesReference())).get("y").value());

            // the next stop, at the same line of the same frame, hands out handles of its own
            session.resume();
            Stop     second     = stops.next();
            int      nextFrame  = session.stackTrace(second.threadId()).getFirst().id();
            int      nextLocals = session.scopes(nextFrame).getFirst().variablesReference();
            Variable nextPoint  = byName(read(session, nextLocals)).get("point");
            assertEquals(first.threadId(), second.threadId());
            assertEquals("2", byName(read(session, nextPoint.variablesReference())).get("y").value());
            assertTrue(Collections.disjoint(List.of(frame, locals, point.variablesReference()),
                    List.of(nextFrame, nextLocals, nextPoint.variablesReference())));

            // the first stop's handles name nothing now, neither its values nor this stop's
            assertTrue(session.scopes(frame).isEmpty());
            assertTrue(read(session, locals).isEmpty());
            assertTrue(read(session, point.variablesReference()).isEmpty());
            assertFails("The frame is not one of the stopped program's, so nothing can be evaluated in it.", session,
                    frame, "point.y");

            // a step resumes the program too, which ends this stop's handles
            step(session, stops, second, Step.OVER);
            assertTrue(session.scopes(nextFrame).isEmpty());
            assertTrue(read(session, nextLocals).isEmpty());

            session.setBreakpoints(source.toString(), List.of());
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(6L, control.result());
        }
    }

    @Test
    public void aReadOrEvaluationAskedForAsTheProgramResumesIsAnsweredWithNothingOrWhy() throws Exception {
        Path  source  = compile("Races", TWICE.replace("Twice", "Races"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Races"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 12)));

        Control control = run("Races", new StringWriter(), session);
        try (control) {
            Stop stop   = stops.next();
            int  frame  = session.stackTrace(stop.threadId()).getFirst().id();
            int  locals = session.scopes(frame).getFirst().variablesReference();

            // the stopped fiber makes reads while it holds the debugger's lock, which this thread
            // holds until the program has resumed, so it never makes these
            CompletableFuture<List<Variable>> vars;
            CompletableFuture<Variable>       value;
            synchronized (session) {
                vars  = session.variables(locals);
                value = session.evaluate(frame, "n");
                session.resume();
            }
            assertTrue(vars.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS).isEmpty());
            assertFails("The program resumed before the expression was evaluated.", value);

            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(42L, control.result());
        }
    }

    @Test
    public void runningCodeAnswersWithItsValuesToStringAndWhatItChangesStaysChanged() throws Exception {
        Path  source  = compile("Runs", RUNS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Runs"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 37)));

        StringWriter output  = new StringWriter();
        Control      control = run("Runs", output, session);
        try (control) {
            Stop                 stop   = stops.next();
            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            int                  show   = frames.get(0).id();
            int                  caller = frames.get(1).id();

            // a call runs, and what it changes stays changed, also for what is read afterwards
            Variable bumped = runCode(session, show, "box.bump(n)");
            assertEquals("box.bump(n)", bumped.name());
            assertEquals("5", bumped.value());
            assertEquals("Int", bumped.type());
            assertEquals("5", evaluate(session, show, "box.count").value());

            // an object's value is its toString(), and it has its fields as a variable does
            Variable box = runCode(session, show, "box");
            assertEquals("Box(5)", box.value());
            assertEquals("Runs:Box", box.type());
            assertEquals("5", byName(read(session, box.variablesReference())).get("count").value());
            assertEquals("15", runCode(session, show, "box.count * 2 + n").value());

            // an expression without a value runs as a statement
            Variable printed = runCode(session, show, "console.print(\"from the console\")");
            assertEquals(SessionDebugger.NO_VALUE, printed.value());
            assertTrue(output.toString().contains("from the console"), output.toString());

            // in the caller's frame, a call into a service that is idle runs too
            assertEquals("2", runCode(session, caller, "counter.add(2)").value());
            assertEquals("2", runCode(session, caller, "counter.count").value());

            // the program is stopped where it was, and goes on as before
            assertTrue(session.isStopped());
            assertEquals("Runs.show:37", where(session, stop));
            assertFalse(output.toString().contains("box 5"), output.toString());
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertTrue(output.toString().contains("box 5"), output.toString());
            assertEquals(10L, control.result());
            assertEquals(null, stops.poll());
        }
    }

    @Test
    public void nothingStopsARunsCodeAndWhatItThrowsOrWhyItDoesNotCompileIsItsAnswer() throws Exception {
        Path  source  = compile("Unstopped", RUNS.replace("Runs", "Unstopped"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Unstopped"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 37), new Line(2, 6)));
        session.setExceptionStops(true, true);

        Control control = run("Unstopped", new StringWriter(), session);
        try (control) {
            Stop stop = stops.next();
            int  show = session.stackTrace(stop.threadId()).getFirst().id();

            // neither the breakpoint in bump() nor the exception stops stop the run's code
            assertEquals("1", runCode(session, show, "box.bump(1)").value());
            assertFails("box.fail() threw IllegalState: no.", session.run(show, "box.fail()", RUN_LIMIT));
            assertEquals(null, stops.poll());

            var failure = assertThrows(ExecutionException.class,
                    () -> session.run(show, "nope + 1", RUN_LIMIT).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));
            Throwable cause  = failure.getCause() instanceof CompletionException wrapped ? wrapped.getCause() : failure.getCause();
            String    reason = cause.getMessage();
            assertTrue(reason.startsWith("nope + 1 does not compile: "), reason);
            assertFails("The frame is not one of the stopped program's, so nothing can be evaluated in it.",
                    session.run(show + 1000, "n", RUN_LIMIT));

            // the run's exception was its own: the program goes on from where it stopped
            session.setBreakpoints(source.toString(), List.of());
            session.setExceptionStops(false, false);
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(6L, control.result());
        }
    }

    @Test
    public void codeRunAtAnExceptionStopLeavesTheProgramsOwnExceptionToBeCaughtAsBefore() throws Exception {
        compile("Rethrows", THROWS.replace("Throws", "Rethrows"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Rethrows"));
        session.setExceptionStops(true, false);

        StringWriter output  = new StringWriter();
        Control      control = run("Rethrows", output, session);
        try (control) {
            // stopped at the IllegalState that run() catches
            Stop stop = stops.next();
            assertEquals("Rethrows.check:14", where(session, stop));
            int check = session.stackTrace(stop.threadId()).getFirst().id();

            // the run throws another exception, which is its answer and stops nothing
            assertEquals("1", runCode(session, check, "n + 1").value());
            assertFails("check(1) threw IllegalArgument: uncaught one.", session.run(check, "check(1)", RUN_LIMIT));
            assertEquals(null, stops.poll());

            // the program's own exception goes on as it did: run() catches it and prints "caught"
            session.setExceptionStops(false, false);
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertTrue(output.toString().contains("caught"), output.toString());
            assertEquals(null, control.result());
        }
    }

    @Test
    public void readsAndOtherRunsWaitForARunAndTheProgramsResumeEndsOne() throws Exception {
        Path                  source  = compile("Waits", TWICE.replace("Twice", "Waits"));
        Stops                 stops   = new Stops();
        var                   session = new SessionDebugger(stops);
        BlockingQueue<String> lines   = new LinkedBlockingQueue<>();
        Semaphore             reading = new Semaphore(0);
        session.addModule(input().loadModule("Waits"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 11)));

        Control control = run("Waits", new StringWriter(), session, _ -> {
            reading.release();
            try {
                return lines.poll(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        });
        try (control) {
            Stop                 stop   = stops.next();
            List<StackFrameInfo> frames = session.stackTrace(stop.threadId());
            int                  twice  = frames.get(0).id();
            int                  caller = frames.get(1).id();
            int                  locals = session.scopes(twice).getFirst().variablesReference();

            // the run waits for a line of input; a read of its fiber's frames and another run,
            // asked for meanwhile, wait for it, one at a time
            CompletableFuture<Variable> line = session.run(caller, "console.readLine()", RUN_LIMIT);
            assertTrue(reading.tryAcquire(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS), () -> "not read: " + answer(line));
            CompletableFuture<List<Variable>> vars   = session.variables(locals);
            CompletableFuture<Variable>       second = session.run(twice, "n + 1", RUN_LIMIT);
            Thread.sleep(200);
            assertFalse(line.isDone(), () -> "answered first: " + answer(line));
            assertFalse(vars.isDone());
            assertFalse(second.isDone(), () -> "answered first: " + answer(second));

            lines.add("typed");
            assertEquals("typed", line.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS).value());
            assertEquals("21", byName(vars.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS)).get("n").value());
            assertEquals("22", second.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS).value());

            // a run that the program's resume finds in progress ends at once; its code, which waits
            // for input, unwinds once it has its line, and the program then goes on
            CompletableFuture<Variable> resumed = session.run(caller, "console.readLine()", RUN_LIMIT);
            assertTrue(reading.tryAcquire(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS), () -> "not read: " + answer(resumed));
            session.resume();
            assertFails("The program resumed before the expression was evaluated.", resumed);
            lines.add("later");
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(42L, control.result());
        }
    }

    @Test
    public void aRunEndsAtItsTimeLimitAlsoWhenItsCodeCatchesExceptionsAndTheProgramStaysStopped()
            throws Exception {
        Path  source  = compile("Ends", ENDS);
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Ends"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 5)));

        Control control = run("Ends", new StringWriter(), session);
        try (control) {
            Stop     stop  = stops.next();
            int      frame = session.stackTrace(stop.threadId()).getFirst().id();
            Duration limit = Duration.ofMillis(300);

            // the code unwinds, so the next run starts at once
            assertFails("forever(n) did not finish within 300 ms, so it was ended.",
                    session.run(frame, "forever(n)", limit));
            assertEquals("4", runCode(session, frame, "n + 1").value());

            // also when it catches the exception that unwinds it, which is raised again at each line
            assertFails("stubborn(n) did not finish within 300 ms, so it was ended.",
                    session.run(frame, "stubborn(n)", limit));
            assertEquals("4", runCode(session, frame, "n + 1").value());

            // the program is stopped where it was, and goes on from there
            assertTrue(session.isStopped());
            assertEquals("Ends.run:5", where(session, stop));
            session.resume();
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(3L, control.result());
        }
    }

    @Test
    public void cancellingARunOrResumingTheProgramEndsTheRunsCode() throws Exception {
        Path  source  = compile("Cancels", ENDS.replace("Ends", "Cancels"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Cancels"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 5)));

        Control control = run("Cancels", new StringWriter(), session);
        try (control) {
            Stop stop  = stops.next();
            int  frame = session.stackTrace(stop.threadId()).getFirst().id();

            // a cancelled run's code unwinds, so the next run starts
            CompletableFuture<Variable> cancelled = session.run(frame, "forever(n)", RUN_LIMIT);
            Thread.sleep(200);
            assertTrue(cancelled.cancel(true));
            assertEquals("4", runCode(session, frame, "n + 1").value());

            // the program's resume ends a run in progress, and the program goes on
            CompletableFuture<Variable> resumed = session.run(frame, "forever(n)", RUN_LIMIT);
            Thread.sleep(200);
            session.resume();
            assertFails("The program resumed before the expression was evaluated.", resumed);
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
            assertEquals(3L, control.result());
        }
    }

    @Test
    public void closingTheProgramEndsARunInProgress() throws Exception {
        Path  source  = compile("Terminates", ENDS.replace("Ends", "Terminates"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Terminates"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 5)));

        Control control = run("Terminates", new StringWriter(), session);
        Stop    stop    = stops.next();
        int     frame   = session.stackTrace(stop.threadId()).getFirst().id();

        CompletableFuture<Variable> running = session.run(frame, "forever(n)", RUN_LIMIT);
        Thread.sleep(200);
        // not in a try-with-resources: closing again after a close that timed out would wait without a limit
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFails("The program was stopped before the expression ran to its end.", running);
        assertFalse(control.running());

        // the stop's clock runs again, although its fiber ended in the run: the next run, whose
        // service is still at work when run() returns, ends, as the runner waits on that clock
        // until its services have nothing left to do
        compile("Afterwards", """
                module Afterwards {
                    service Worker {
                        Int work() {
                            Int sum = 0;
                            for (Int i : 0 ..< 100_000) {
                                sum += i;
                            }
                            return sum;
                        }
                    }

                    void run() {
                        Worker worker = new Worker();
                        worker.work^();
                    }
                }
                """);
        Control after = run("Afterwards", new StringWriter(), null);
        assertTimeoutPreemptively(RUN_LIMIT, after::join);
        // the close is what waits on that clock, so it too has a limit, outside a try-with-resources
        assertTimeoutPreemptively(RUN_LIMIT, after::close);
    }

    @Test
    public void aRunThatCallsAServiceWhoseFiberIsStoppedEndsAtItsTimeLimitAndSaysWhy() throws Exception {
        Path  source  = compile("Blocked", HOLDS.replace("Holds", "Blocked"));
        Stops stops   = new Stops();
        var   session = new SessionDebugger(stops);
        session.addModule(input().loadModule("Blocked"));
        session.setBreakpoints(source.toString(), List.of(new Line(1, 16)));

        Control control = run("Blocked", new StringWriter(), session);
        Stop    stop    = stops.next();
        assertTimeoutPreemptively(RUN_LIMIT, () -> {
            while (session.threads().size() < 2) {
                Thread.sleep(10);
            }
        });

        // the spinner's fiber waits at a line in its service, which therefore cannot answer
        int frame = session.stackTrace(stop.threadId()).getFirst().id();
        assertFails("spinner.spins did not finish within 300 ms, so it was ended. It waits, perhaps for a service"
                + " whose fiber is stopped, which does not run until the program resumes.",
                session.run(frame, "spinner.spins", Duration.ofMillis(300)));

        // once the program resumes, the call is answered and the run's code unwinds; the spinner
        // never ends, so closing the run ends it, outside a try-with-resources as above
        session.resume();
        assertTimeoutPreemptively(RUN_LIMIT, control::close);
        assertFalse(control.running());
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
     * The stops a session reports, in order, and the messages its logpoints log.
     */
    private static class Stops implements SessionDebugger.Listener {
        private final BlockingQueue<Stop> queue  = new LinkedBlockingQueue<>();
        private final List<String>        logged = new CopyOnWriteArrayList<>();

        @Override
        public void stopped(Stop stop) {
            queue.add(stop);
        }

        @Override
        public void logged(String message) {
            logged.add(message);
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

    private static Variable runCode(SessionDebugger session, int frame, String expression) throws Exception {
        return session.run(frame, expression, RUN_LIMIT).get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * @return the value of a run that has ended, or why it failed
     */
    private static String answer(CompletableFuture<Variable> run) {
        return run.handle((value, failure) -> value != null ? value.value() : failure.toString()).join();
    }

    /**
     * Assert that evaluating the path fails with the specified reason.
     */
    private static void assertFails(String reason, SessionDebugger session, int frame, String path) {
        assertFails(reason, session.evaluate(frame, path));
    }

    /**
     * Assert that the evaluation fails with the specified reason.
     */
    private static void assertFails(String reason, CompletableFuture<Variable> evaluation) {
        var failure = assertThrows(ExecutionException.class,
                () -> evaluation.get(RUN_LIMIT.toSeconds(), TimeUnit.SECONDS));
        Throwable cause = failure.getCause() instanceof CompletionException wrapped ? wrapped.getCause() : failure.getCause();
        assertEquals(reason, cause.getMessage());
    }

    private static Map<String, Variable> byName(List<Variable> vars) {
        return vars.stream().collect(Collectors.toMap(Variable::name, Function.identity()));
    }

    private Control run(String moduleName, StringWriter output, SessionDebugger session) {
        return run(moduleName, output, session, ConsoleInput.NONE);
    }

    private Control run(String moduleName, StringWriter output, SessionDebugger session, ConsoleInput consoleInput) {
        ErrorList errs    = new ErrorList(25);
        Control   control = support.runMethod(input(), moduleName, null, EntryPoint.RUN,
                new PrintWriter(output, true), consoleInput, sourceDir.toFile(), session, errs);
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
