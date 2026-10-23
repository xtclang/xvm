package org.xvm.runtime;

import java.io.IOException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import java.time.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.function.ToIntFunction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;

import org.xvm.compiler.EvalCompiler;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

/**
 * A debugger that its host drives through calls rather than a terminal, for hosts such as an IDE's
 * debug adapter. The host makes the program's modules known ({@link #addModule}), sets breakpoints
 * by source file and line ({@link #setBreakpoints}), learns of each stop through its
 * {@link Listener}, reads the stopped program's threads and stacks ({@link #threads},
 * {@link #stackTrace}) and the variables of their frames ({@link #scopes}, {@link #variables},
 * {@link #evaluate}), has a waiting fiber run code ({@link #run}), and
 * resumes it ({@link #resume}), steps one of its fibers ({@link #step}) or pauses it
 * ({@link #pause}, {@link #stopOnEntry}). It debugs the runtime it is set on with
 * {@link Runtime#setDebugger}; the host's own protocol is the host's concern.
 *
 * <p>A stop holds the whole runtime: the fiber that stops waits on its interpreter thread, and so
 * does every other fiber that reaches a line while the program is stopped. Each takes a snapshot
 * of its stack before it waits, so the host reads immutable copies and never a running frame. The
 * snapshots are discarded when the program resumes. Fibers keep yielding to each other as they do
 * without a debugger, also while one of them steps.
 *
 * <p>A step resumes the whole program and stops it again at the next line the stepped fiber
 * reaches: over a call, in the same frame; into one, in any frame of a fiber that the stepped one
 * calls or is called by; out of one, once its frame returns. A frame that returns, or that an
 * exception leaves, during a step over continues it as a step into, so it stops at its caller's
 * next line. Any other stop, such as a breakpoint, ends the step.
 *
 * <p>A pause stops the program at the next line any of its fibers reaches, with the reason "pause",
 * or, asked for before the program starts, at its first line, with the reason "entry": the first
 * line of a module the host made known, not of the libraries that run before it, or of any code if
 * none is known. A program whose fibers all wait, for input or for each other, stops when one of
 * them next runs.
 *
 * <p>A thrown exception stops the program, with the reason "exception", at the frame that throws
 * it, when the host asks for every thrown exception, or for those that no frame will catch: no
 * frame of the throwing fiber, nor of the fibers that called it, has a "catch" for it then. The
 * exception is that frame's first variable, {@link #EXCEPTION}, while the program is stopped
 * there; it stops the program once, not again as it leaves each frame.
 *
 * <p>Variables are read on the interpreter thread of the waiting fiber whose frame holds them,
 * which reads them while it waits, so a read never races the program; {@link #variables} answers
 * once that thread has read them, on another thread, so what the host does with an answer never
 * runs while this debugger holds its lock. Only the waiting fiber's own frames have variables,
 * not those of the fiber that called it, and another service's state is not expanded: a read
 * touches only the waiting service's state and immutable values. A variable's reference to its
 * fields or elements, like a frame's ID, lasts until the program resumes. An expression, such as
 * {@code point.x + 1} or {@code ints[i] > 0}, is evaluated the same way, without running any of the
 * program's code: its operators run the runtime's own native operations ({@link PureExpression}).
 * Any other expression, such as a call, runs as code on the waiting fiber ({@link #run}), which
 * leaves its wait to run it and then waits again, while the program's other fibers stay where they
 * wait.
 *
 * <p>A breakpoint is installed by file and line: it stops every fiber that reaches the first op of
 * that line in any method compiled from that file. A file is the module's source directory and
 * the class's source path, as the compiler records them, compared as real paths. A breakpoint may
 * have a condition, an expression whose Boolean value must be True; a hit count, which counts the
 * hits whose condition holds; and a log message, which makes it a logpoint that logs the message,
 * with the values of the expressions in braces in it, and does not stop. The fiber that reaches it evaluates
 * them on its own thread, as it would variables; a condition that cannot be evaluated stops the
 * program and says why. Thrown exceptions may stop the program only for some exception classes,
 * those named and the classes that extend them, and the host can read the exception that stopped
 * the program ({@link #exceptionInfo}).
 *
 * <p>The host's own code, in the container it names with {@link #setHost}, is not debugged, and
 * stacks, the exception's included, end where the host calls the program. When
 * the program is stopped for good ({@link #terminate}), its waiting fibers end without running
 * more of it, and so does any fiber that reaches a line afterwards.
 */
public final class SessionDebugger implements Debugger {

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The name of a frame's scope of local variables.
     */
    public static final String LOCALS = "Locals";

    /**
     * The name of the variable that holds the exception a frame is throwing.
     */
    public static final String EXCEPTION = "<exception>";

    /**
     * The value of a variable that has not been assigned.
     */
    public static final String UNASSIGNED = "<unassigned>";

    /**
     * The most elements of an array that {@link #variables} reads; a last entry says how many more
     * there are.
     */
    public static final int MAX_ELEMENTS = 100;

    /**
     * The message of a breakpoint in a file that none of the program's known modules came from.
     */
    public static final String NOT_IN_PROGRAM = "No code of the program was compiled from this file.";

    /**
     * The message of a breakpoint on a line that has no code and is followed by none in its
     * method.
     */
    public static final String NO_CODE = "There is no code on this line.";

    /**
     * The value of an expression that {@link #run} runs as a statement, since it has no value, such
     * as a call to a method that returns nothing.
     */
    public static final String NO_VALUE = "(no value)";

    /**
     * The longest value text {@link #variables} returns; longer text is cut off.
     */
    private static final int MAX_TEXT = 1000;

    /**
     * Why nothing is evaluated or run in a frame that is not one of this stop's.
     */
    private static final String NOT_STOPPED =
            "The frame is not one of the stopped program's, so nothing can be evaluated in it.";

    /**
     * Why an evaluation or a run that the program's resume abandoned has no answer.
     */
    private static final String RESUMED_FIRST = "The program resumed before the expression was evaluated.";

    /**
     * The register of a run's native frame that holds the expression's value.
     */
    private static final int VALUE = 0;

    /**
     * The register of a run's native frame that holds the text of the expression's value.
     */
    private static final int TEXT = 1;

    /**
     * The register of a run's native frame that holds the exception that the expression, or the
     * {@code toString()} of its value, throws.
     */
    private static final int THROWN = 2;

    /**
     * The op of a run's native frame that returns from it, which ends the run.
     */
    private static final int END_RUN = 2;

    private static final Integer[] NO_BREAKS = new Integer[0];

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The host's listener.
     */
    private final Listener listener;

    /**
     * The names of the known modules.
     */
    private final Set<String> moduleNames = ConcurrentHashMap.newKeySet();

    /**
     * The methods compiled from each source file, by real path, from the known modules; guarded
     * by this debugger's monitor.
     */
    private final Map<String, List<MethodLines>> methodsByFile = new HashMap<>();

    /**
     * The real paths of the files looked up, by path.
     */
    private final Map<String, String> realPaths = new ConcurrentHashMap<>();

    /**
     * The last frame ID or variables reference handed out: they share one count, so none is ever
     * reused.
     */
    private final AtomicInteger handles = new AtomicInteger();

    /**
     * The installed breakpoints, replaced as a whole when they change.
     */
    private volatile Installed installed = new Installed(Map.of(), Map.of());

    /**
     * The thrown exceptions that stop the program.
     */
    private volatile ExceptionStops exceptionStops = ExceptionStops.NONE;

    /**
     * The program's stop while it is stopped, or null while it runs.
     */
    private volatile Halt halt;

    /**
     * The step that runs, or null.
     */
    private volatile Stepping stepping;

    /**
     * The pause that is pending, or null.
     */
    private volatile Pause pendingPause;

    /**
     * The last exception the program stopped at, which does not stop it again as it unwinds.
     */
    private volatile ExceptionHandle stoppedException;

    /**
     * The runtime this debugger is attached to, or null.
     */
    private volatile Runtime runtime;

    /**
     * The container of the host's own code, which this debugger leaves alone, or null.
     */
    private volatile Container hostContainer;

    /**
     * True once another debugger has replaced this one.
     */
    private volatile boolean detached;

    /**
     * True once the program is being stopped: its fibers end at their next line.
     */
    private volatile boolean terminating;

    /**
     * The run of a waiting fiber's code that is in progress, or null; there is one at a time.
     */
    private volatile Running running;

    /**
     * The ops of the native frame that a run's code runs in, on the waiting fiber, above the frame
     * where it waits: call the compiled expression, make its value's text, and return, which ends the
     * run.
     */
    private final Op[] runOps = {
        nativeOp("RunExpression", this::callExpression),
        nativeOp("ShowValue", this::showValue),
        nativeOp("EndRun", _ -> Op.R_RETURN),
    };

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a debugger that reports to the specified listener.
     *
     * @param listener  the host's listener
     */
    public SessionDebugger(Listener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    // ----- host API ------------------------------------------------------------------------------

    /**
     * The host's view of the debugger's events.
     */
    public interface Listener {
        /**
         * The program has stopped. Called on the interpreter thread that stopped, which waits
         * once this returns, until the host calls {@link #resume}. The snapshots that
         * {@link #threads} and {@link #stackTrace} return are taken before this is called.
         *
         * @param stop  where and why the program stopped
         */
        void stopped(Stop stop);

        /**
         * A logpoint logged a message. Called on the interpreter thread that reached the logpoint,
         * which runs on once this returns.
         *
         * @param message  the message, with the values of the expressions in it
         */
        default void logged(String message) {
        }
    }

    /**
     * A breakpoint the host requests: its ID, which the host chooses, its 1-based line, and how it
     * stops, each null when not given: a condition, an expression whose Boolean value must be True,
     * such as {@code done} or {@code i % 2 == 0}; a hit count, "N" to stop at the Nth hit, or
     * ">= N", "> N" or "% N", counting the hits whose condition holds; and a log message, which
     * makes it a logpoint: it logs the message, with each {@code {expression}} in it replaced by
     * that expression's value, and does not stop.
     */
    public record Line(int id, int line, String condition, String hitCount, String logMessage) {
        public Line(int id, int line) {
            this(id, line, null, null, null);
        }
    }

    /**
     * A breakpoint as installed: its ID, the line it was installed on, which may follow the
     * requested line when that line has no code of its own, whether it was installed, and if not,
     * why.
     */
    public record Breakpoint(int id, int line, boolean verified, String message) {
    }

    /**
     * A stopped or waiting fiber: its ID, and its service's name.
     */
    public record ThreadInfo(int id, String name) {
    }

    /**
     * One frame of a stopped fiber's stack: an ID unique within the stop, the method's name, the
     * source file, or null when it is not known, and the 1-based line, or 0.
     */
    public record StackFrameInfo(int id, String name, String path, int line) {
    }

    /**
     * A scope of a stopped frame: its name, and the reference that {@link #variables} reads its
     * variables by.
     */
    public record Scope(String name, int variablesReference) {
    }

    /**
     * A variable of a scope, or a field or element of a value: its name, its value and its type in
     * words, and the reference that {@link #variables} reads its fields or elements by, or 0 if it
     * has none.
     */
    public record Variable(String name, String value, String type, int variablesReference) {
    }

    /**
     * How the host steps a stopped fiber.
     */
    public enum Step {
        /**
         * To the next line of the same frame, or of its caller once it returns.
         */
        OVER,

        /**
         * To the next line the fiber reaches, also in a method that it calls.
         */
        IN,

        /**
         * To the next line of the frame's caller.
         */
        OUT
    }

    /**
     * Why and where the program stopped: the reason ("breakpoint", "step", "exception", "pause" or
     * "entry"), what stopped it in words (an exception's type and text), the fiber that stopped,
     * and the IDs of the breakpoints it stopped at.
     */
    public record Stop(String reason, String description, int threadId, List<Integer> breakpointIds) {
    }

    /**
     * The exception the program stopped at: its type and its text, if it has one, the stack where
     * it was thrown, as the runtime writes a stack trace but without the host's frames, and
     * whether no frame will catch it.
     */
    public record ExceptionInfo(String type, String text, String stackTrace, boolean uncaught) {
    }

    /**
     * Make the code of a module known, so that breakpoints in its source files can be verified.
     * The structures are only read; the runtime may run its own copy of the module.
     *
     * @param module  a module of the program
     */
    public synchronized void addModule(ModuleStructure module) {
        moduleNames.add(module.getName());
        index(module);
    }

    /**
     * Replace the breakpoints of one source file. A line without code of its own is moved to the
     * next line with code in the same method. A breakpoint whose condition, hit count or log
     * message cannot be understood is not installed. The hit counts start again.
     *
     * @param path   the source file
     * @param lines  the breakpoints of that file; none removes them all
     *
     * @return each breakpoint as installed, in the order requested
     */
    public synchronized List<Breakpoint> setBreakpoints(String path, List<Line> lines) {
        String            file      = canonical(path);
        List<MethodLines> methods   = methodsByFile.get(file);
        var               idsByLine = new HashMap<Integer, Integer>();
        var               options   = new HashMap<Integer, Options>();
        var               result    = new ArrayList<Breakpoint>(lines.size());
        for (Line request : lines) {
            int line = methods == null ? -1 : breakableLine(methods, request.line());
            if (line < 0) {
                result.add(new Breakpoint(request.id(), request.line(), false, methods == null ? NOT_IN_PROGRAM : NO_CODE));
                continue;
            }
            try {
                Options.of(request).ifPresent(given -> options.put(request.id(), given));
            } catch (IllegalArgumentException e) {
                result.add(new Breakpoint(request.id(), line, false, e.getMessage()));
                continue;
            }
            idsByLine.putIfAbsent(line, request.id());
            result.add(new Breakpoint(request.id(), line, true, null));
        }

        // the file's earlier breakpoints and their options are replaced
        Installed current     = installed;
        var       idsByFile   = new HashMap<>(current.idsByFile());
        var       optionsById = new HashMap<>(current.optionsById());
        Map<Integer, Integer> earlier = idsByFile.remove(file);
        if (earlier != null) {
            earlier.values().forEach(optionsById::remove);
        }
        if (!idsByLine.isEmpty()) {
            idsByFile.put(file, Map.copyOf(idsByLine));
            optionsById.putAll(options);
        }
        installed = new Installed(Map.copyOf(idsByFile), Map.copyOf(optionsById));
        updateActive();
        return result;
    }

    /**
     * Choose which thrown exceptions stop the program, at the frame that throws them.
     *
     * @param all       true to stop at every thrown exception
     * @param uncaught  true to stop at the exceptions that no frame will catch
     */
    public synchronized void setExceptionStops(boolean all, boolean uncaught) {
        setExceptionStops(all, List.of(), uncaught, List.of());
    }

    /**
     * Choose which thrown exceptions stop the program, at the frame that throws them, and of which
     * classes: one named, or a class that extends one, or any when none is named.
     *
     * @param all            true to stop at every thrown exception
     * @param allTypes       the names of the exception classes that every thrown exception means,
     *                       or none for all of them
     * @param uncaught       true to stop at the exceptions that no frame will catch
     * @param uncaughtTypes  the names of the exception classes that the uncaught exceptions mean, or
     *                       none for all of them
     */
    public synchronized void setExceptionStops(boolean all, List<String> allTypes, boolean uncaught, List<String> uncaughtTypes) {
        exceptionStops = new ExceptionStops(all, Set.copyOf(allTypes), uncaught, Set.copyOf(uncaughtTypes));
        updateActive();
    }

    /**
     * @param threadId  the ID of a fiber that {@link #threads} returned
     *
     * @return the exception that the fiber stopped the program at, or null if it stopped it for
     *         another reason, it is not the fiber that stopped it, or the program runs
     */
    public synchronized ExceptionInfo exceptionInfo(int threadId) {
        Halt current = halt;
        return current != null && current.threadId == threadId ? current.exception : null;
    }

    /**
     * @return true while the program is stopped
     */
    public boolean isStopped() {
        return halt != null;
    }

    /**
     * @return the fibers of the stopped program that wait: the one that stopped first, then those
     *         that reached a line since; none while the program runs
     */
    public synchronized List<ThreadInfo> threads() {
        Halt current = halt;
        if (current == null) {
            return List.of();
        }
        return current.stacks.entrySet().stream().map(entry -> new ThreadInfo(entry.getKey(), entry.getValue().name())).toList();
    }

    /**
     * @param threadId  the ID of a fiber that {@link #threads} returned
     *
     * @return the fiber's stack, innermost frame first; empty if the fiber is not waiting
     */
    public synchronized List<StackFrameInfo> stackTrace(int threadId) {
        Snapshot snapshot = snapshotOf(threadId);
        return snapshot == null ? List.of() : snapshot.frames();
    }

    /**
     * @param frameId  the ID of a frame that {@link #stackTrace} returned
     *
     * @return the frame's scopes, which are its local variables; none if the frame is not one of
     *         this stop's, or its fiber called it from another fiber's frame
     */
    public synchronized List<Scope> scopes(int frameId) {
        Halt    current = halt;
        FrameAt at      = current == null ? null : current.frames.get(frameId);
        if (at == null) {
            return List.of();
        }
        var target    = new Locals(at.thread(), at.frame());
        int reference = current.localsReferences.computeIfAbsent(frameId, _ -> reference(current, target));
        return List.of(new Scope(LOCALS, reference));
    }

    /**
     * Read the variables of a scope, or the fields or elements of a value. The waiting fiber that
     * holds them reads them on its own thread; the listener may ask from there too. The answer
     * comes on another thread, never while this debugger holds its lock.
     *
     * @param reference  a reference that {@link #scopes} or an earlier read returned
     *
     * @return the variables, in order: a frame's {@code this} and its named registers, a value's
     *         fields, or an array's elements; none if the reference is not one of this stop's, or
     *         the program resumes first. Fails if a value cannot be read.
     */
    public CompletableFuture<List<Variable>> variables(int reference) {
        var future = new CompletableFuture<List<Variable>>();
        synchronized (this) {
            Halt   current = halt;
            Target target  = current == null ? null : current.targets.get(reference);
            if (target == null || !request(current, target, future)) {
                future.complete(List.of());
            }
        }
        return future;
    }

    /**
     * Evaluate an expression in a stopped frame without running any of the program's code: a
     * variable of the frame, {@code this}, {@link #EXCEPTION} or a field of {@code this}, their
     * fields ({@code .name}) and array elements ({@code [index]}), literals, and operators on
     * numbers, strings, characters, Booleans, enum values and {@code Null}, such as
     * {@code point.x + 1} or {@code ints[i] > 0} ({@link PureExpression}). The innermost variable
     * of a name is the one meant. The waiting fiber that holds the frame evaluates it on its own
     * thread, as {@link #variables} reads variables.
     *
     * @param frameId     the ID of a frame that {@link #stackTrace} returned
     * @param expression  the expression to evaluate
     *
     * @return the value, as a variable named by the expression, with a reference to its fields or
     *         elements; fails, with the reason in words, if the text is not such an expression, it
     *         cannot be evaluated, the frame is not one of this stop's, or the program resumes first
     */
    public CompletableFuture<Variable> evaluate(int frameId, String expression) {
        PureExpression parsed;
        try {
            parsed = PureExpression.parse(expression);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        var future = new CompletableFuture<List<Variable>>();
        synchronized (this) {
            Halt    current = halt;
            FrameAt at      = current == null ? null : current.frames.get(frameId);
            if (at == null || !request(current, new Evaluation(at.thread(), at.frame(), parsed), future)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_STOPPED));
            }
        }
        return future.thenApply(vars -> {
            // a read that the resume abandoned has no variables
            if (vars.isEmpty()) {
                throw new IllegalArgumentException(RESUMED_FIRST);
            }
            return vars.getFirst();
        });
    }

    /**
     * Run an expression's code in a stopped frame, on the fiber that waits there: any expression the
     * compiler compiles in the frame, such as {@code list.size}, {@code point.toString()} or
     * {@code counter.add(1)}, which {@link #evaluate} refuses. The value is shown by its
     * {@code toString()}, which runs too; an expression that has no value, such as a call to a
     * method that returns nothing, runs as a statement, whose value is {@link #NO_VALUE}.
     *
     * <p>The fiber leaves its wait to run the code, as a call from the frame where it waits, and
     * then waits there again, in the same stop, without a new one. What the code changes stays
     * changed: the fields it assigns and the objects it changes. A variable of the frame that it
     * assigns is a copy, so the frame's own keeps its value. Nothing in the code stops the program:
     * no breakpoint, exception stop or pause; an exception it throws is its answer.
     *
     * <p>Which fibers run meanwhile, and why: the fiber that runs the code, and the fibers of other
     * services that it calls, which began after the run did. Every other fiber stays where it waits,
     * so what the user inspects does not change underneath. A Java debugger instead lets every thread
     * run while it evaluates, by default: Java threads share memory and locks, so a method evaluated
     * on one stopped thread can need a lock that another stopped thread holds, and only letting them
     * all run avoids that deadlock. An Ecstasy service runs one fiber at a time and shares no mutable
     * state with other services, so the code can only wait on another service by calling it, and the
     * fibers that the call reaches run. What it cannot do, which the time limit then ends: call into
     * a service whose own fiber is stopped in the middle of a method, since that fiber holds the
     * service's thread; wait for work that an unrelated fiber would do, such as a future that another
     * service completes; and wait while its own service runs another of its fibers, which waits at its
     * next line and holds the service's thread. The program's clocks stand still, as they do while it
     * is stopped, so code that waits for a timer waits until the time limit too.
     *
     * <p>How a run ends before its code does: at its time limit, which counts from when it starts;
     * when the host cancels its answer; when the program resumes or steps; and when the program is
     * stopped for good ({@link #terminate}). The answer then fails at once, with the reason, and the
     * code unwinds: from every line it, or a fiber it called, reaches, an {@code IllegalState} is
     * raised, again at each line, so that a "catch" cannot keep it going, until it is back where the
     * run called it; once the program is stopped for good, its fiber ends there instead. Code that
     * waits, as for a stopped service, or in a native call that blocks, reaches no line, so it unwinds
     * only once it gets its answer, such as when the program resumes; until then the fiber does not
     * wait in the stop again, and other runs, and reads of its frames, wait for it.
     *
     * <p>One run at a time: another, from any frame, starts once this one ends, and reads of the
     * running fiber's frames are made once it waits again; those of other fibers are not delayed.
     *
     * @param frameId     the ID of a frame that {@link #stackTrace} returned
     * @param expression  the expression to run
     * @param limit       how long the run may take once it starts
     *
     * @return the value, as a variable named by the expression, whose value is the value's
     *         {@code toString()}, with a reference to its fields or elements; fails, with the reason
     *         in words, if the expression does not compile, it or its value's {@code toString()}
     *         throws, the frame is not one of this stop's, or the run ends before its code does.
     *         Cancelling it ends the run.
     */
    public CompletableFuture<Variable> run(int frameId, String expression, Duration limit) {
        Objects.requireNonNull(limit);
        Run request;
        synchronized (this) {
            Halt    current = halt;
            FrameAt at      = current == null ? null : current.frames.get(frameId);
            if (at == null || !current.stacks.containsKey(at.thread())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_STOPPED));
            }
            request = new Run(at.frame(), expression, limit, new CompletableFuture<>());
            current.runs.computeIfAbsent(at.thread(), _ -> new ArrayDeque<>()).add(request);
            notifyAll();
        }

        // cancelling the answer ends the run
        CompletableFuture<Variable> answer = request.answer();
        answer.whenComplete((_, _) -> {
            if (answer.isCancelled()) {
                cancel(request);
            }
        });
        return answer;
    }

    /**
     * Resume the stopped program, stepping the specified fiber: the program stops again, with the
     * reason "step", at the line where the step ends.
     *
     * @param threadId  the ID of a fiber that {@link #threads} returned
     * @param step      how to step it
     *
     * @return false if the program runs, or the fiber does not wait; then nothing changes
     */
    public synchronized boolean step(int threadId, Step step) {
        Snapshot snapshot = snapshotOf(threadId);
        if (snapshot == null) {
            return false;
        }

        // set first, so lines stay checked as the fibers resume
        Frame frame = snapshot.frame();
        stepping = new Stepping(Objects.requireNonNull(step), frame, frame.f_fiber);
        release();
        return true;
    }

    /**
     * Stop the program at the next line any of its fibers reaches, with the reason "pause"; does
     * nothing while it is stopped.
     */
    public synchronized void pause() {
        if (halt == null) {
            pendingPause = new Pause("pause", false);
            updateActive();
        }
    }

    /**
     * Stop the program at its first line, with the reason "entry"; called before it starts, and
     * after the program's modules are made known.
     */
    public synchronized void stopOnEntry() {
        pendingPause = new Pause("entry", true);
        updateActive();
    }

    /**
     * Resume the stopped program; does nothing while it runs.
     */
    public synchronized void resume() {
        release();
    }

    // ----- Debugger interface --------------------------------------------------------------------

    @Override
    public synchronized void attach(Runtime runtime) {
        this.runtime = runtime;
        updateActive();
    }

    @Override
    public void setHost(Container container) {
        hostContainer = container;
    }

    @Override
    public synchronized void terminate() {
        terminating  = true;
        stepping     = null;
        pendingPause = null;

        // a run's fiber ends at its next line, without coming back to answer
        Running run = running;
        if (run != null) {
            endRun(run, "The program was stopped before the expression ran to its end.");
        }
        release();
    }

    @Override
    public synchronized void detach() {
        detached     = true;
        installed    = new Installed(Map.of(), Map.of());
        stepping     = null;
        pendingPause = null;
        release();
        runtime = null;
    }

    @Override
    public boolean holdsFibers() {
        return false;
    }

    @Override
    public int activate(Frame frame, int pc) {
        if (isHost(frame)) {
            return pc + 1;
        }
        Running run = runOf(frame);
        if (run != null) {
            return goOn(run, frame, pc);
        }

        // assert:debug stops the program as a breakpoint does
        return stop(frame, "breakpoint", "assert:debug", List.of(), null, null, f -> terminating ? end(f) : pc + 1);
    }

    @Override
    public int checkBreakPoint(Frame frame, int pc) {
        if (isHost(frame)) {
            return pc + 1;
        }
        Running run = runOf(frame);
        if (run != null) {
            return goOn(run, frame, pc);
        }
        return halt == null ? line(frame, pc) : park(frame, f -> line(f, pc));
    }

    @Override
    public void onReturn(Frame frame) {
        leave(frame);
    }

    @Override
    public int checkBreakPoint(Frame frame, ExceptionHandle exception) {
        Running run = running;
        if (run != null && frame.f_framePrev == run.trampoline() && !frame.catches(exception)) {
            // the run's code throws, or its value's toString() does: that is its answer, and the
            // run's native frame returns
            frame.clearException();
            run.trampoline().f_ahVar[THROWN] = exception;
            return END_RUN;
        }
        leave(frame);

        // an exception stops the program once, at the first frame of the program that throws it
        if (exception != stoppedException && !frame.isNative() && !isHost(frame) && runOf(frame) == null
                && stopsAt(exception, frame)) {
            stoppedException = exception;
            return stop(frame, "exception", describe(exception), List.of(), null,
                    infoOf(frame, exception, !isCaught(frame, exception)), _ -> Op.R_NEXT);
        }

        // asked again at each frame the exception leaves, so a step learns that its frame is left;
        // a fiber that is ending meanwhile ends at its next line
        return Op.R_NEXT;
    }

    // ----- stopping ------------------------------------------------------------------------------

    /**
     * Check a line that the frame reaches while the program runs: stop at a breakpoint, where a
     * step ends, or for a pause.
     */
    private int line(Frame frame, int pc) {
        if (terminating) {
            return end(frame);
        }

        // a breakpoint on the line where a step ends, or a pause, is reported as the breakpoint; a
        // logpoint, or a breakpoint whose condition or hit count does not hold, lets them stop
        Integer[]            breaks = breaksOf(frame);
        Integer              id     = pc < breaks.length ? breaks[pc] : null;
        Hit                  hit    = id == null ? Hit.RUN : hit(frame, id);
        Stepping             step   = stepping;
        Pause                pause  = pendingPause;
        ToIntFunction<Frame> next   = f -> terminating ? end(f) : pc + 1;
        if (hit.stops()) {
            return stop(frame, "breakpoint", hit.description(), List.of(id), null, null, next);
        }
        if (step != null && step.endsAt(frame) && startsLine(frame, pc)) {
            return stop(frame, "step", null, List.of(), step, null, next);
        }
        if (pause != null && startsLine(frame, pc) && (!pause.program() || isProgram(frame))) {
            return stop(frame, pause.reason(), null, List.of(), pause, null, next);
        }
        return next.applyAsInt(frame);
    }

    /**
     * @return true if the frame runs the host's own code, which this debugger leaves alone
     */
    private boolean isHost(Frame frame) {
        Container host = hostContainer;
        return host != null && frame.f_context.f_container == host;
    }

    /**
     * @return true if the stack's entry runs the host's own code: by its frame, or, for a caller
     *         whose fiber has moved on so that only its method is known, by that method's module
     */
    private boolean isHost(Frame.StackFrame stackFrame) {
        Container host = hostContainer;
        if (host == null) {
            return false;
        }
        return stackFrame.frame != null
                ? isHost(stackFrame.frame)
                : stackFrame.function != null && stackFrame.function.getIdentityConstant().getModuleConstant().equals(host.getModule());
    }

    /**
     * End the frame's fiber without running more of the program: a terminating fiber unwinds an
     * exception without looking for a handler, as the fibers of a terminated service do.
     */
    private static int end(Frame frame) {
        frame.f_fiber.setStatus(Fiber.FiberStatus.Terminating, 0);
        return frame.raiseException(xException.serviceTerminated(frame, frame.f_context.f_sName));
    }

    /**
     * The frame returns, or an exception leaves it: a step over or out of it continues as a step
     * into, to stop at the next line of its caller, which may run on the fiber that called this
     * one.
     */
    private void leave(Frame frame) {
        Stepping step = stepping;
        if (step != null && step.frame() == frame && step.step() != Step.IN) {
            stepping = new Stepping(Step.IN, frame, frame.f_fiber);
        }
    }

    /**
     * @return true if the exception stops the program as thrown at the specified frame
     */
    private boolean stopsAt(ExceptionHandle exception, Frame frame) {
        ExceptionStops stops = exceptionStops;
        return stops.all() && isOneOf(exception, stops.allTypes())
                || stops.uncaught() && isOneOf(exception, stops.uncaughtTypes()) && !isCaught(frame, exception);
    }

    /**
     * @return true if the exception's class, or a class it extends, has one of the specified names,
     *         or if none is specified
     */
    private static boolean isOneOf(ExceptionHandle exception, Set<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        for (ClassStructure clazz = exception.getTemplate().getStructure(); clazz != null; clazz = clazz.getSuper()) {
            if (types.contains(clazz.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return what the host reads of the exception the program stops at, in the frame that throws it
     */
    private ExceptionInfo infoOf(Frame frame, ExceptionHandle exception, boolean uncaught) {
        ObjectHandle text = exception.getField(null, "text");
        return new ExceptionInfo(exception.getType().getValueString(),
                text instanceof StringHandle string ? string.getStringValue() : null, stackTraceOf(frame), uncaught);
    }

    /**
     * @return the stack where the frame is, of its fiber and the fibers that called it, as the
     *         runtime writes an exception's stack trace, up to the host's frames
     */
    private String stackTraceOf(Frame frame) {
        var     trace     = new StringBuilder();
        boolean fiberEnds = false;
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            Frame at = stackFrame.frame;
            if (isHost(stackFrame) || at == null && stackFrame.function == null) {
                break;
            }
            if (fiberEnds) {
                trace.append("\n\t    =========");
            }
            trace.append("\n\t").append(at == null
                    ? Frame.formatFrameDetails(null, stackFrame.function, -1, null, null)
                    : Frame.formatFrameDetails(at.f_context, stackFrame.function, stackFrame.iPC, at.getOps(), at.f_framePrev));
            fiberEnds = stackFrame.boundary;
        }
        return trace.append('\n').toString();
    }

    /**
     * @return what the breakpoint with the specified ID does as the frame reaches it: it stops,
     *         saying why if its condition cannot be evaluated, unless its condition or its hit count
     *         does not hold, or it is a logpoint, which logs its message and runs on
     */
    private Hit hit(Frame frame, int id) {
        Options options = installed.optionsById().get(id);
        if (options == null) {
            return Hit.STOP;
        }
        if (options.condition() != null) {
            try {
                if (!options.condition().isTrue(frame, frame)) {
                    return Hit.RUN;
                }
            } catch (IllegalArgumentException e) {
                return new Hit(true, "The condition cannot be evaluated: " + e.getMessage());
            }
        }
        if (options.hitCount() != null && !options.hitCount().counts()) {
            return Hit.RUN;
        }
        if (options.log() != null) {
            listener.logged(message(frame, options.log()));
            return Hit.RUN;
        }
        return Hit.STOP;
    }

    /**
     * @return the log message with the value of each expression in it, or why it cannot be
     *         evaluated
     */
    private static String message(Frame frame, List<LogPart> parts) {
        var message = new StringBuilder();
        for (LogPart part : parts) {
            switch (part) {
                case Text(String text) -> message.append(text);
                case Value(PureExpression expression) -> {
                    try {
                        ObjectHandle value = shown(expression.evaluate(frame, frame));
                        message.append(value == null ? UNASSIGNED : valueText(value));
                    } catch (IllegalArgumentException e) {
                        message.append('<').append(e.getMessage()).append('>');
                    }
                }
            }
        }
        return message.toString();
    }

    /**
     * @return true if a frame of the fiber at the specified frame, or of a fiber that called it,
     *         will catch the exception
     */
    private static boolean isCaught(Frame frame, ExceptionHandle exception) {
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            Frame caller = stackFrame.frame;
            if (caller != null && !caller.isNative() && caller.catches(exception)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the exception's type and its text, if it has one
     */
    static String describe(ExceptionHandle exception) {
        String       type = exception.getType().getValueString();
        ObjectHandle text = exception.getField(null, "text");
        return text instanceof StringHandle string ? type + ": " + string.getStringValue() : type;
    }

    /**
     * @return true if the frame runs code of a module the host made known, or if none is known
     */
    private boolean isProgram(Frame frame) {
        ClassStructure clazz = frame.getContainingClass();
        return moduleNames.isEmpty() || clazz != null && moduleNames.contains(clazz.getFileStructure().getModule().getName());
    }

    /**
     * @return true if the op at the specified index starts a line, as a step ends at
     */
    private static boolean startsLine(Frame frame, int pc) {
        Op[] ops = frame.getOps();
        return ops != null && pc < ops.length && ops[pc] instanceof Nop nop && nop.getLineCount() != 0;
    }

    /**
     * Stop the program at the specified frame, tell the listener, and wait until the host resumes
     * it. If the program is already stopped, wait for that stop to end first; the stop a step or a
     * pause asked for is then not made, unless that step or pause is still pending.
     *
     * @param request    the step or pause that ends here, or null for any other stop
     * @param exception  the exception the program stops at, or null
     * @param next       what the hook that stops returns once the stop ends
     *
     * @return {@link Op#R_CALL} if the fiber runs code first ({@link #run}), which the hook returns;
     *         otherwise what next returns
     */
    private int stop(Frame frame, String reason, String description, List<Integer> breakpointIds, Object request,
                     ExceptionInfo exception, ToIntFunction<Frame> next) {
        Stop stop;
        Halt current;
        synchronized (this) {
            // another fiber's stop comes first; this one waits for it like any other fiber
            for (Halt other = halt; other != null && !detached; other = halt) {
                waitAt(other, frame);
                Running run = waitWhile(other, frame);
                if (run != null) {
                    // once the run ends, the fiber waits for that stop again before it makes its own
                    return start(run, frame, f -> stop(f, reason, description, breakpointIds, request, exception, next));
                }
                if (Thread.currentThread().isInterrupted()) {
                    return next.applyAsInt(frame);
                }
            }
            if (detached || terminating || request != null && request != stepping && request != pendingPause) {
                return next.applyAsInt(frame);
            }

            // any stop ends the step and the pause; the program's clocks stand still until the
            // stop ends, also while a fiber runs code in it
            int       threadId        = threadId(frame.f_fiber);
            Container nativeContainer = frame.f_context.f_container.getNativeContainer();
            stepping     = null;
            pendingPause = null;
            current      = new Halt(threadId, exception, nativeContainer.freezeTime() ? nativeContainer : null);
            halt         = current;
            current.stacks.put(threadId, snapshot(current, frame));
            updateActive();
            stop = new Stop(reason, description, threadId, breakpointIds);
        }

        try {
            listener.stopped(stop);
        } catch (RuntimeException | Error e) {
            // a stop that the host was not told of does not hold the program
            synchronized (this) {
                if (halt == current) {
                    release();
                }
            }
            throw e;
        }
        return await(current, frame, next);
    }

    /**
     * Wait at a line while the program is stopped, as one of its fibers.
     *
     * @param next  what the hook returns once the stop ends
     *
     * @return {@link Op#R_CALL} if the fiber runs code first, which the hook returns; otherwise what
     *         next returns
     */
    private int park(Frame frame, ToIntFunction<Frame> next) {
        Halt current;
        synchronized (this) {
            current = halt;
            if (current != null) {
                waitAt(current, frame);
            }
        }
        return current == null ? next.applyAsInt(frame) : await(current, frame, next);
    }

    /**
     * Wait in the specified stop, as one of its fibers, until it ends. A run of code that the host
     * asks of this fiber meanwhile starts at once, as the hook returns into it, and once it ends the
     * fiber waits here again.
     *
     * @param next  what the hook returns once the stop ends
     *
     * @return {@link Op#R_CALL} if a run starts, which the hook returns; otherwise what next returns
     */
    private int await(Halt current, Frame frame, ToIntFunction<Frame> next) {
        Running run;
        synchronized (this) {
            run = waitWhile(current, frame);
        }
        return run == null ? next.applyAsInt(frame) : start(run, frame, f -> await(current, f, next));
    }

    /**
     * Take part in the specified stop as a fiber that waits at the specified frame. Called while
     * holding this debugger's monitor.
     */
    private void waitAt(Halt current, Frame frame) {
        current.stacks.computeIfAbsent(threadId(frame.f_fiber), _ -> snapshot(current, frame));
    }

    /**
     * Wait until the specified stop ends, reading the variables the host asks for meanwhile, or
     * until the host asks this fiber to run code, when no other run is in progress. Called while
     * holding this debugger's monitor.
     *
     * @return the run to start, or null once the stop ends or the thread is interrupted, which lets
     *         it run on
     */
    private Running waitWhile(Halt current, Frame frame) {
        int threadId = threadId(frame.f_fiber);
        while (halt == current) {
            List<Read> reads = current.reads.remove(threadId);
            if (reads != null) {
                reads.forEach(read -> read(current, frame, read.target(), read.future()));
                continue;
            }

            Deque<Run> runs = current.runs.get(threadId);
            if (running == null && runs != null && !runs.isEmpty()) {
                Running run = prepare(current, frame, runs.poll());
                if (run != null) {
                    running = run;
                    updateActive();
                    // the deadline's timer is cancelled once the run is answered, so that it does
                    // not hold the run, and through it the program, until the limit has passed;
                    // expire() waits for this monitor, so not on the JDK's one timer thread
                    run.run().answer().copy().orTimeout(run.limit().toNanos(), TimeUnit.NANOSECONDS)
                            .whenCompleteAsync((_, failure) -> {
                                if (failure instanceof TimeoutException) {
                                    expire(run);
                                }
                            });
                    return run;
                }
                continue;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                current.stacks.remove(threadId);
                abandon(current.reads.remove(threadId));
                abandonRuns(current.runs.remove(threadId));
                return null;
            }
        }
        return null;
    }

    /**
     * Let the stopped program run on: the waiting fibers resume, what they held is discarded, a run
     * in progress ends, and the program's clocks run again. Called while holding this debugger's
     * monitor.
     */
    private void release() {
        Halt current = halt;
        if (current != null) {
            Running run = running;
            if (run != null) {
                endRun(run, RESUMED_FIRST);
            }
            halt = null;
            if (current.frozen != null) {
                // on whichever thread ends the stop: its fiber may not come back to do it, as when
                // the program is stopped for good while that fiber runs code
                current.frozen.unfreezeTime();
            }
            current.reads.values().forEach(SessionDebugger::abandon);
            current.runs.values().forEach(SessionDebugger::abandonRuns);
            updateActive();
            notifyAll();
        }
    }

    /**
     * Have the waiting fiber that the target belongs to read it, on its own thread, or on this one
     * if it is that thread, as the listener's may be. Called while holding this debugger's monitor.
     *
     * @return false if that fiber does not wait
     */
    private boolean request(Halt current, Target target, CompletableFuture<List<Variable>> future) {
        Snapshot snapshot = current.stacks.get(target.thread());
        if (snapshot == null) {
            return false;
        }
        if (snapshot.thread() == Thread.currentThread()) {
            read(current, snapshot.frame(), target, future);
        } else {
            current.reads.computeIfAbsent(target.thread(), _ -> new ArrayList<>()).add(new Read(target, future));
            notifyAll();
        }
        return true;
    }

    /**
     * @return the snapshot of the specified fiber, if the program is stopped and the fiber waits
     */
    private Snapshot snapshotOf(int threadId) {
        Halt current = halt;
        return current == null ? null : current.stacks.get(threadId);
    }

    /**
     * Turn the runtime's "debugger is active" flag on while there are breakpoints or exception
     * stops, the program is stopped, a fiber runs code or steps, or a pause is pending, so that
     * every line and thrown exception is checked; otherwise lines run at full speed. A run needs
     * the exceptions its code throws, also once the program has resumed.
     */
    private void updateActive() {
        Runtime attached = runtime;
        if (attached != null) {
            boolean stopping = halt != null || running != null || stepping != null || pendingPause != null;
            boolean watching = !installed.idsByFile().isEmpty() || exceptionStops.any();
            attached.setDebuggerActive(stopping || watching);
        }
    }

    /**
     * Take a snapshot of the stack of the fiber at the specified frame, on its own thread, and
     * remember the frames of that fiber, whose variables it reads while it waits.
     */
    private Snapshot snapshot(Halt current, Frame frame) {
        int threadId = threadId(frame.f_fiber);
        var stack    = new ArrayList<StackFrameInfo>();
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            MethodStructure function = stackFrame.function;
            if (isHost(stackFrame)) {
                // the host calls the program, so the rest of the stack is the host's own code
                break;
            }
            if (function == null || stackFrame.frame != null && stackFrame.frame.isNative()) {
                continue;
            }

            int frameId = handles.incrementAndGet();
            int line    = stackFrame.iPC >= 0 ? function.calculateLineNumber(stackFrame.iPC) : 0;
            stack.add(new StackFrameInfo(frameId, functionName(function), sourcePath(function), line));
            if (stackFrame.frame != null && stackFrame.frame.f_fiber == frame.f_fiber) {
                current.frames.put(frameId, new FrameAt(threadId, stackFrame.frame));
            }
        }
        return new Snapshot(frame.f_context.f_sName, List.copyOf(stack), Thread.currentThread(), frame);
    }

    private static int threadId(Fiber fiber) {
        return (int) fiber.getId();
    }

    static String functionName(MethodStructure function) {
        String className = switch (function.getContainingClass()) {
            case null                   -> null;
            case ModuleStructure module -> module.getSimpleName();
            case ClassStructure clazz   -> clazz.getName();
        };
        return className == null ? function.getName() : className + '.' + function.getName();
    }

    // ----- variables -----------------------------------------------------------------------------

    /**
     * Read the target's variables on the thread of the fiber waiting at the specified frame, whose
     * stack holds the array elements it reads, and answer the read on another thread, since the
     * host's actions on the answer must not run while this debugger's monitor is held. Called
     * while holding this debugger's monitor.
     */
    private void read(Halt current, Frame frame, Target target, CompletableFuture<List<Variable>> future) {
        List<Variable> vars;
        try {
            vars = switch (target) {
                case Locals(int _, Frame scope)                  -> locals(current, frame, scope);
                case Children(int _, ObjectHandle value)         -> children(current, frame, value);
                case Evaluation(int _, Frame scope, PureExpression e) -> List.of(resolve(current, frame, scope, e));
            };
        } catch (RuntimeException e) {
            // a value the debugger cannot read fails the read, not the program
            future.completeAsync(() -> {
                throw e;
            });
            return;
        }
        future.completeAsync(() -> vars);
    }

    /**
     * @return the exception the frame is throwing, if any, then its {@code this} and its named
     *         registers in scope
     */
    private List<Variable> locals(Halt current, Frame waitingFrame, Frame frame) {
        var             vars      = new ArrayList<Variable>();
        ExceptionHandle exception = frame.m_hException;
        if (exception != null) {
            vars.add(variable(current, waitingFrame, EXCEPTION, exception));
        }

        ObjectHandle self = frame.f_hThis;
        if (self != null) {
            vars.add(variable(current, waitingFrame, "this", self));
        }

        for (int i = 0, count = frame.getCurrentVarCount(); i < count; i++) {
            VarInfo info = frame.getVarInfo(i);
            String  name = info == null ? "" : info.getName();
            if (!name.isEmpty()) {
                vars.add(variable(current, waitingFrame, name, frame.f_ahVar[i]));
            }
        }
        return vars;
    }

    /**
     * @return an array's elements, or an object's fields
     */
    private List<Variable> children(Halt current, Frame waitingFrame, ObjectHandle value) {
        return switch (value) {
            case ArrayHandle array    -> elements(current, waitingFrame, array);
            case GenericHandle object -> fields(current, waitingFrame, object);
            default                   -> List.of();
        };
    }

    /**
     * @return the array's elements, at most {@link #MAX_ELEMENTS} of them, then how many more
     *         there are
     */
    private List<Variable> elements(Halt current, Frame waitingFrame, ArrayHandle array) {
        var  vars = new ArrayList<Variable>();
        long size = array.m_hDelegate.m_cSize;
        for (int i = 0, count = (int) Math.min(size, MAX_ELEMENTS); i < count; i++) {
            ObjectHandle element = element(waitingFrame, array, i);
            if (element == null) {
                break;
            }
            vars.add(variable(current, waitingFrame, "[" + i + "]", element));
        }
        if (size > MAX_ELEMENTS) {
            vars.add(new Variable("...", (size - MAX_ELEMENTS) + " more", "", 0));
        }
        return vars;
    }

    /**
     * @return the array's element at the specified index, read through the waiting frame's stack,
     *         or null if it cannot be read
     */
    static ObjectHandle element(Frame waitingFrame, ArrayHandle array, long index) {
        // extractArrayValue returns only R_NEXT or R_EXCEPTION (out of bounds), which must not stay
        // on the waiting frame
        var             template = (xArray) array.getTemplate();
        ExceptionHandle pending  = waitingFrame.m_hException;
        if (template.extractArrayValue(waitingFrame, array, index, Op.A_STACK) != Op.R_NEXT) {
            waitingFrame.m_hException = pending;
            return null;
        }
        return waitingFrame.popStack();
    }

    /**
     * @return the expression's value, as a variable named by the expression
     */
    private Variable resolve(Halt current, Frame waitingFrame, Frame frame, PureExpression expression) {
        return variable(current, waitingFrame, expression.text(), expression.evaluate(waitingFrame, frame));
    }

    /**
     * @return the value a reference refers to, null if it is unassigned, or the value itself
     */
    static ObjectHandle shown(ObjectHandle value) {
        return value instanceof RefHandle ref ? ref.getReferent() : value;
    }

    /**
     * @return the object's fields
     */
    private List<Variable> fields(Halt current, Frame waitingFrame, GenericHandle object) {
        var vars = new ArrayList<Variable>();
        for (Map.Entry<String, FieldInfo> entry : DebugConsole.getFieldLayout(object.getComposition()).entrySet()) {
            vars.add(variable(current, waitingFrame, entry.getKey(), object.getField(entry.getValue().getIndex())));
        }
        return vars;
    }

    /**
     * @return the variable of the specified value, with a reference to its fields or elements if
     *         it has any that the waiting fiber may read
     */
    private Variable variable(Halt current, Frame waitingFrame, String name, ObjectHandle value) {
        ObjectHandle shown = shown(value);
        if (shown == null) {
            return new Variable(name, UNASSIGNED, "", 0);
        }

        int thread   = threadId(waitingFrame.f_fiber);
        int children = hasChildren(waitingFrame, shown) ? reference(current, new Children(thread, shown)) : 0;
        return new Variable(name, valueText(shown), typeText(shown), children);
    }

    /**
     * @return true if the value has fields or elements to show that belong to the waiting fiber's
     *         service or are immutable; another service's state is read only on its own thread
     */
    private static boolean hasChildren(Frame waitingFrame, ObjectHandle value) {
        TypeComposition composition = value.getComposition();
        if (composition == null || composition instanceof ProxyComposition) {
            return false;
        }

        return switch (value) {
            case EnumHandle _ -> false;
            case ServiceHandle service when service.f_context != waitingFrame.f_context -> false;
            case ArrayHandle array -> array.m_hDelegate.m_cSize > 0;
            case GenericHandle _ -> !DebugConsole.getFieldLayout(composition).isEmpty();
            default -> false;
        };
    }

    /**
     * @return the value in words: an array's element type and size, as Java debuggers show
     *         arrays, else the runtime's own text of the value without the type it starts with,
     *         or the type alone for an object
     */
    private static String valueText(ObjectHandle value) {
        if (value instanceof ArrayHandle array) {
            TypeConstant elementType = array.getType().resolveGenericType("Element");
            return (elementType == null ? "" : elementType.getValueString()) + '[' + array.m_hDelegate.m_cSize + ']';
        }

        String text = withoutType(value.toString());
        if (text.isEmpty()) {
            return typeText(value);
        }
        return cut(text);
    }

    /**
     * @return the text, cut off after {@link #MAX_TEXT} characters
     */
    private static String cut(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "..." : text;
    }

    /**
     * @return the text without the type in parentheses, and the space after it, that
     *         ObjectHandle.toString() starts with
     */
    private static String withoutType(String text) {
        if (text.startsWith("(")) {
            for (int i = 0, depth = 0, length = text.length(); i < length; i++) {
                char ch = text.charAt(i);
                if (ch == '(') {
                    ++depth;
                } else if (ch == ')' && --depth == 0) {
                    return text.substring(text.startsWith(" ", i + 1) ? i + 2 : i + 1);
                }
            }
        }
        return text;
    }

    static String typeText(ObjectHandle value) {
        // a deferred value has no composition yet; an enum value's own class, such as True, is not
        // the type the program declares, its enumeration, such as Boolean
        if (value.getComposition() == null) {
            return "";
        }
        return value instanceof EnumHandle
                ? value.getTemplate().getClassConstant().getValueString()
                : value.getType().getValueString();
    }

    /**
     * @return a new reference to the specified target, which lasts until the program resumes
     */
    private int reference(Halt current, Target target) {
        int reference = handles.incrementAndGet();
        current.targets.put(reference, target);
        return reference;
    }

    /**
     * Answer reads that will not be made, because their fiber no longer waits, with no variables,
     * on another thread.
     */
    private static void abandon(List<Read> reads) {
        if (reads != null) {
            reads.forEach(read -> read.future().completeAsync(List::of));
        }
    }

    // ----- running code --------------------------------------------------------------------------

    /**
     * Compile the run's expression in its frame, and make the native frame that runs it on the
     * fiber waiting at the specified frame, above that frame; an expression that does not compile
     * fails the run. Called on that fiber's thread, while holding this debugger's monitor.
     *
     * @return the run, ready to start, or null if the expression does not compile
     */
    private Running prepare(Halt current, Frame waitingFrame, Run run) {
        long            firstFiber = Fiber.nextId();
        Frame           frame      = run.frame();
        TypeConstant    object     = frame.poolContext().typeObject();
        EvalCompiler    value      = new EvalCompiler(frame, "{\nreturn " + run.expression() + ";\n}");
        MethodStructure lambda     = value.createLambda(object);
        if (lambda != null) {
            return new Running(run, waitingFrame.f_fiber, firstFiber, current, lambda, value.getArgs(), false,
                    waitingFrame.createNativeFrame(runOps, new ObjectHandle[3], Op.A_IGNORE, null),
                    new AtomicReference<>());
        }

        // an expression that has no value runs as a statement
        EvalCompiler statement = new EvalCompiler(frame, "{\n" + run.expression() + ";\nreturn Null;\n}");
        lambda = statement.createLambda(object);
        if (lambda != null) {
            return new Running(run, waitingFrame.f_fiber, firstFiber, current, lambda, statement.getArgs(), true,
                    waitingFrame.createNativeFrame(runOps, new ObjectHandle[3], Op.A_IGNORE, null),
                    new AtomicReference<>());
        }

        List<String> errors = value.getErrors().stream().map(error -> error.getMessageText()).toList();
        fail(run.answer(), run.expression() + " does not compile: " + String.join(" ", errors));
        return null;
    }

    /**
     * Start a run on the fiber whose hook waits at the specified frame: the hook returns
     * {@link Op#R_CALL} into the run's native frame, which returns to the frame once the run ends,
     * and the fiber then goes on as rest says, which waits where the fiber waited before. A frame
     * that waits at an exception it throws goes on throwing it.
     *
     * @return {@link Op#R_CALL}
     */
    private int start(Running run, Frame frame, ToIntFunction<Frame> rest) {
        ExceptionHandle throwing = frame.clearException();
        run.trampoline().addContinuation(f -> {
            finish(run);
            f.m_hException = throwing;
            int next = rest.applyAsInt(f);
            return next == Op.R_NEXT && f.m_hException != null ? Op.R_EXCEPTION : next;
        });
        return frame.call(run.trampoline());
    }

    /**
     * End the run: answer it, with the value or the exception that its native frame holds, and
     * remove its compiled expression from the class it was added to. Called on the run's fiber's
     * thread.
     */
    private void finish(Running run) {
        run.lambda().getParent().removeChild(run.lambda());

        ObjectHandle[]              registers  = run.trampoline().f_ahVar;
        String                      expression = run.run().expression();
        CompletableFuture<Variable> answer     = run.run().answer();
        synchronized (this) {
            if (running == run) {
                running = null;
                updateActive();
            }
            notifyAll();

            if (run.ended().get() != null) {
                // ended before its code finished, which answered it
                return;
            }
            if (registers[THROWN] instanceof ExceptionHandle thrown) {
                String what = registers[VALUE] == null ? expression : expression + "'s toString()";
                fail(answer, what + " threw " + describe(thrown) + '.');
            } else if (run.statement()) {
                answer.completeAsync(() -> new Variable(expression, NO_VALUE, "", 0));
            } else {
                Variable shown = variable(run.halt(), run.trampoline().f_framePrev, expression, registers[VALUE]);
                String   text  = cut(((StringHandle) registers[TEXT]).getStringValue());
                answer.completeAsync(() -> new Variable(shown.name(), text, shown.type(), shown.variablesReference()));
            }
        }
    }

    /**
     * The first op of a run's native frame: call the compiled expression with the values of the
     * frame's variables that it reads; its value goes on the native frame's stack.
     */
    private int callExpression(Frame trampoline) {
        Running        run   = running;
        Frame          frame = run.run().frame();
        int[]          vars  = run.args();
        ObjectHandle[] args  = new ObjectHandle[run.lambda().getMaxVars()];
        for (int i = 0; i < vars.length; i++) {
            try {
                args[i] = frame.getArgument(vars[i]);
            } catch (ExceptionHandle.WrapperException e) {
                args[i] = new DeferredCallHandle(e.getExceptionHandle());
            }
        }

        ObjectHandle self = frame.f_function.isFunction() ? null : frame.getThis();
        return Op.anyDeferred(args)
                ? new Utils.GetArguments(args, f -> call(f, run, self, args)).doNext(trampoline)
                : call(trampoline, run, self, args);
    }

    /**
     * Call the run's compiled expression from its native frame; a call that fails at once, as at a
     * stack overflow, ends the run with that exception.
     */
    private static int call(Frame trampoline, Running run, ObjectHandle self, ObjectHandle[] args) {
        int result = trampoline.call1(run.lambda(), self, args, Op.A_STACK);
        if (result == Op.R_EXCEPTION) {
            trampoline.f_ahVar[THROWN] = trampoline.clearException();
            return END_RUN;
        }
        return result;
    }

    /**
     * The second op of a run's native frame: the expression's value goes to the {@link #VALUE}
     * register, and its {@code toString()} to the {@link #TEXT} register; a statement has no value
     * to show.
     */
    private int showValue(Frame trampoline) {
        ObjectHandle[] registers = trampoline.f_ahVar;
        registers[VALUE] = trampoline.popStack();
        if (running.statement()) {
            return Op.R_NEXT;
        }

        return switch (Utils.callToString(trampoline, registers[VALUE])) {
            case Op.R_NEXT -> {
                registers[TEXT] = trampoline.popStack();
                yield Op.R_NEXT;
            }
            case Op.R_CALL -> {
                trampoline.m_frameNext.addContinuation(f -> {
                    registers[TEXT] = f.popStack();
                    return Op.R_NEXT;
                });
                yield Op.R_CALL;
            }
            default -> {
                // a native toString() threw
                registers[THROWN] = trampoline.clearException();
                yield Op.R_NEXT;
            }
        };
    }

    /**
     * @return the run in progress if the frame runs its code, or null
     */
    private Running runOf(Frame frame) {
        Running run = running;
        return run != null && run.includes(frame) ? run : null;
    }

    /**
     * Let a run's code go on past a line or an {@code assert:debug}, since nothing stops it; code
     * that was ended, by its deadline, a cancel or the program's resume, unwinds instead, from every
     * line it reaches, so that a "catch" in it cannot keep it going; and once the program is being
     * stopped for good, its fiber ends there.
     */
    private int goOn(Running run, Frame frame, int pc) {
        if (terminating) {
            return end(frame);
        }

        String ended = run.ended().get();
        return ended == null ? pc + 1 : frame.raiseException(xException.illegalState(frame, ended));
    }

    /**
     * End the run in progress before its code does, for the specified reason, which is its answer:
     * its code unwinds from the next line it reaches. Does nothing if the run has ended already.
     * Called while holding this debugger's monitor.
     */
    private void endRun(Running run, String reason) {
        if (running == run && run.ended().compareAndSet(null, reason)) {
            fail(run.run().answer(), reason);
        }
    }

    /**
     * End the run at its deadline, if it is still in progress.
     */
    private synchronized void expire(Running run) {
        if (running != run) {
            return;
        }

        // a fiber that waits has called something that does not answer while the program is stopped
        String expression = run.run().expression();
        String reason     = expression + " did not finish within " + inWords(run.limit()) + ", so it was ended.";
        if (run.fiber().getStatus() == Fiber.FiberStatus.Waiting) {
            reason += " It waits, perhaps for a service whose fiber is stopped, which does not run until the"
                    + " program resumes.";
        }
        endRun(run, reason);
    }

    /**
     * End the run that the host cancelled, or take it out of its fiber's queue if it has not
     * started.
     */
    private synchronized void cancel(Run request) {
        Halt current = halt;
        if (current != null) {
            current.runs.values().forEach(queued -> queued.remove(request));
        }

        Running run = running;
        if (run != null && run.run() == request) {
            endRun(run, request.expression() + " was cancelled.");
        }
    }

    /**
     * @return the time limit in words, such as "2 seconds" or "300 ms"
     */
    private static String inWords(Duration limit) {
        long millis = limit.toMillis();
        if (millis % 1000 != 0) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    /**
     * @return an op of a native frame that does what the specified function does
     */
    private static Op nativeOp(String name, ToIntFunction<Frame> process) {
        return new Op() {
            @Override
            public int process(Frame frame, int pc) {
                return process.applyAsInt(frame);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /**
     * Fail the answer with the specified reason, on another thread.
     */
    private static void fail(CompletableFuture<Variable> answer, String reason) {
        answer.completeAsync(() -> {
            throw new IllegalArgumentException(reason);
        });
    }

    /**
     * Answer runs that will not start, since the program resumes first.
     */
    private static void abandonRuns(Deque<Run> runs) {
        if (runs != null) {
            runs.forEach(run -> fail(run.answer(), RESUMED_FIRST));
        }
    }

    // ----- breakpoint lookup ---------------------------------------------------------------------

    /**
     * @return the breakpoint IDs of the frame's method by op index, or an empty array if the
     *         method has none; computed once per method and set of breakpoints
     */
    private Integer[] breaksOf(Frame frame) {
        Op[]            ops      = frame.getOps();
        MethodStructure function = frame.getFunction();
        if (ops == null || function == null) {
            return NO_BREAKS;
        }

        // the op array identifies the method: structures compare by their identity constants
        Installed current = installed;
        return current.idsByOps().computeIfAbsent(ops, _ -> computeBreaks(current, function, ops));
    }

    private Integer[] computeBreaks(Installed current, MethodStructure function, Op[] ops) {
        if (current.idsByFile().isEmpty()) {
            return NO_BREAKS;
        }

        String                path      = sourcePath(function);
        Map<Integer, Integer> idsByLine = path == null ? null : current.idsByFile().get(canonical(path));
        if (idsByLine == null) {
            return NO_BREAKS;
        }

        // a line starts at the Nop after which calculateLineNumber() reports it
        Integer[] breaks = null;
        int       line   = 1;
        for (int i = 0, count = ops.length; i < count; i++) {
            if (ops[i].ensureOp() instanceof Nop nop) {
                line += nop.getLineCount();
                Integer id = idsByLine.get(line);
                if (id != null) {
                    if (breaks == null) {
                        breaks = new Integer[count];
                    }
                    breaks[i] = id;
                }
            }
        }
        return breaks == null ? NO_BREAKS : breaks;
    }

    // ----- source index --------------------------------------------------------------------------

    private void index(Component component) {
        if (component instanceof MethodStructure method && method.getOps() != null) {
            String path = sourcePath(method);
            if (path != null) {
                var codeLines = new TreeSet<Integer>();
                int line      = 1;
                for (Op op : method.getOps()) {
                    if (op.ensureOp() instanceof Nop nop) {
                        line += nop.getLineCount();
                        codeLines.add(line);
                    }
                }
                int first = method.getSourceLineNumber() + 1;
                var spans = new MethodLines(first, first + method.getSourceLineCount() - 1, codeLines);
                methodsByFile.computeIfAbsent(canonical(path), _ -> new ArrayList<>()).add(spans);
            }
        }

        for (Component child : component.children()) {
            index(child);
        }
    }

    /**
     * @return the line a breakpoint on the specified line is installed on: the line itself if
     *         code starts on it, else the next line where code starts in a method that spans it,
     *         or -1
     */
    private static int breakableLine(List<MethodLines> methods, int line) {
        int best = -1;
        for (MethodLines method : methods) {
            if (method.lines().contains(line)) {
                return line;
            }
            if (method.first() <= line && line <= method.last()) {
                Integer next = method.lines().ceiling(line);
                if (next != null && (best < 0 || next < best)) {
                    best = next;
                }
            }
        }
        return best;
    }

    /**
     * @return the source file of the method, as the compiler recorded it, or null
     */
    private static String sourcePath(MethodStructure function) {
        ClassStructure clazz = function.getContainingClass();
        if (clazz == null) {
            return null;
        }

        LiteralConstant path = clazz.getSourcePath();
        LiteralConstant dir  = clazz.getFileStructure().getModule().getSourceDir();
        return path == null || dir == null ? null : dir.getValue() + path.getValue();
    }

    /**
     * @return the real path of the specified file, or its normalized absolute path if it does not
     *         exist
     */
    private String canonical(String path) {
        return realPaths.computeIfAbsent(path, given -> {
            try {
                Path absolute = Path.of(given).toAbsolutePath().normalize();
                try {
                    return absolute.toRealPath().toString();
                } catch (IOException | SecurityException e) {
                    return absolute.toString();
                }
            } catch (InvalidPathException e) {
                return given;
            }
        });
    }

    // ----- inner types ---------------------------------------------------------------------------

    /**
     * A stop of the program, and what the host reads while it lasts: the fiber that stopped it and
     * the exception it stopped at, if any; the container whose clock it froze, which it unfreezes
     * when it ends, or null if the clock was frozen already; the stacks of the waiting
     * fibers, the stopping fiber first; the frames that have variables, by frame ID; the reference
     * to each frame's locals that {@link #scopes} handed out; what each reference reads; and the
     * reads each waiting fiber has yet to make and the code it has yet to run, by fiber ID. Guarded
     * by the debugger's monitor, and dropped as a whole when the program resumes.
     */
    private static final class Halt {
        final int                      threadId;
        final ExceptionInfo            exception;
        final Container                frozen;
        final Map<Integer, Snapshot>   stacks           = new LinkedHashMap<>();
        final Map<Integer, FrameAt>    frames           = new HashMap<>();
        final Map<Integer, Integer>    localsReferences = new HashMap<>();
        final Map<Integer, Target>     targets          = new HashMap<>();
        final Map<Integer, List<Read>> reads            = new HashMap<>();
        final Map<Integer, Deque<Run>> runs             = new HashMap<>();

        Halt(int threadId, ExceptionInfo exception, Container frozen) {
            this.threadId  = threadId;
            this.exception = exception;
            this.frozen    = frozen;
        }
    }

    /**
     * The installed breakpoints: by real path, the ID of the breakpoint on each line; the options
     * of the breakpoints that have any, by ID; and the breakpoint IDs of each method seen, by op
     * index, computed for these breakpoints.
     */
    private record Installed(Map<String, Map<Integer, Integer>> idsByFile, Map<Integer, Options> optionsById,
                             Map<Op[], Integer[]> idsByOps) {
        Installed(Map<String, Map<Integer, Integer>> idsByFile, Map<Integer, Options> optionsById) {
            this(idsByFile, optionsById, new ConcurrentHashMap<>());
        }
    }

    /**
     * How a breakpoint stops, from what the host gave: its condition, its hit count, and the parts
     * of its log message, each null when not given.
     */
    private record Options(PureExpression condition, HitCount hitCount, List<LogPart> log) {
        /**
         * @return the options of the requested breakpoint, if it has any
         *
         * @throws IllegalArgumentException if a condition, hit count or log message cannot be
         *                                  understood, saying which and why
         */
        static Optional<Options> of(Line request) {
            String condition = blankless(request.condition());
            String hitCount  = blankless(request.hitCount());
            String log       = request.logMessage() == null || request.logMessage().isEmpty() ? null : request.logMessage();
            if (condition == null && hitCount == null && log == null) {
                return Optional.empty();
            }
            PureExpression parsed = null;
            if (condition != null) {
                try {
                    parsed = PureExpression.parse(condition);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("The condition " + e.getMessage(), e);
                }
            }
            return Optional.of(new Options(parsed, hitCount == null ? null : HitCount.parse(hitCount),
                    log == null ? null : LogPart.parse(log)));
        }

        private static String blankless(String text) {
            return text == null || text.isBlank() ? null : text.strip();
        }
    }

    /**
     * A breakpoint's hit count: how it compares the hits whose condition holds with the count, and
     * those hits so far, since its file's breakpoints were set.
     */
    private record HitCount(String comparison, int count, AtomicInteger hits) {
        private static final Pattern FORM = Pattern.compile("(>=|>|==|%)?\\s*(\\d{1,9})");

        static HitCount parse(String text) {
            Matcher matcher = FORM.matcher(text);
            if (!matcher.matches() || "%".equals(matcher.group(1)) && Integer.parseInt(matcher.group(2)) == 0) {
                throw new IllegalArgumentException("The hit count \"" + text
                        + "\" is not N, to stop at the Nth hit, or >= N, > N or % N, for a number N.");
            }
            return new HitCount(matcher.group(1) == null ? "==" : matcher.group(1), Integer.parseInt(matcher.group(2)),
                    new AtomicInteger());
        }

        /**
         * @return true if this hit stops the program, counting it
         */
        boolean counts() {
            int hit = hits.incrementAndGet();
            return switch (comparison) {
                case ">=" -> hit >= count;
                case ">"  -> hit > count;
                case "%"  -> hit % count == 0;
                default   -> hit == count;
            };
        }
    }

    /**
     * A part of a log message: text, or an expression whose value replaces it.
     */
    private sealed interface LogPart {
        /**
         * @return the parts of the log message: each {@code {expression}} is an expression, and
         *         the rest is text, as is a brace without its pair
         *
         * @throws IllegalArgumentException if a part in braces is not an expression
         */
        static List<LogPart> parse(String message) {
            var parts = new ArrayList<LogPart>();
            int at    = 0;
            while (at < message.length()) {
                int open  = message.indexOf('{', at);
                int close = open < 0 ? -1 : message.indexOf('}', open + 1);
                if (close < 0) {
                    parts.add(new Text(message.substring(at)));
                    break;
                }
                if (open > at) {
                    parts.add(new Text(message.substring(at, open)));
                }
                try {
                    parts.add(new Value(PureExpression.parse(message.substring(open + 1, close))));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("In the log message, " + e.getMessage(), e);
                }
                at = close + 1;
            }
            return List.copyOf(parts);
        }
    }

    private record Text(String text) implements LogPart {
    }

    private record Value(PureExpression expression) implements LogPart {
    }

    /**
     * What a breakpoint does as a fiber reaches it: whether it stops, and what it says then.
     */
    private record Hit(boolean stops, String description) {
        static final Hit STOP = new Hit(true, null);
        static final Hit RUN  = new Hit(false, null);
    }

    /**
     * The thrown exceptions that stop the program: every one, or those that no frame will catch,
     * each of the classes named, and the classes that extend them, or of any when none is named.
     */
    private record ExceptionStops(boolean all, Set<String> allTypes, boolean uncaught, Set<String> uncaughtTypes) {
        static final ExceptionStops NONE = new ExceptionStops(false, Set.of(), false, Set.of());

        boolean any() {
            return all || uncaught;
        }
    }

    /**
     * A waiting fiber's service name and stack, the thread it waits on, and the frame it waits at.
     */
    private record Snapshot(String name, List<StackFrameInfo> frames, Thread thread, Frame frame) {
    }

    /**
     * A step that runs: how, and the frame and fiber it started at.
     */
    private record Stepping(Step step, Frame frame, Fiber fiber) {
        /**
         * @return true if the step ends at a line of the specified frame
         */
        boolean endsAt(Frame at) {
            return switch (step) {
                case OVER -> at == frame;
                case IN   -> !at.isNative() && at.f_fiber.isAssociated(fiber);
                case OUT  -> false;
            };
        }
    }

    /**
     * A pause that is pending: the reason of the stop it asks for, and whether it stops only in
     * the program's own modules.
     */
    private record Pause(String reason, boolean program) {
    }

    /**
     * A frame of a waiting fiber, by that fiber's ID.
     */
    private record FrameAt(int thread, Frame frame) {
    }

    /**
     * What a reference reads, on the thread of the waiting fiber with the specified ID.
     */
    private sealed interface Target {
        int thread();
    }

    /**
     * A frame's locals.
     */
    private record Locals(int thread, Frame frame) implements Target {
    }

    /**
     * A value's fields or elements.
     */
    private record Children(int thread, ObjectHandle value) implements Target {
    }

    /**
     * An expression to evaluate in a frame.
     */
    private record Evaluation(int thread, Frame frame, PureExpression expression) implements Target {
    }

    /**
     * A read the host asked for, which the waiting fiber makes on its thread.
     */
    private record Read(Target target, CompletableFuture<List<Variable>> future) {
    }

    /**
     * Code the host asks a waiting fiber to run: the frame it runs in, the expression, how long it
     * may run, and the answer.
     */
    private record Run(Frame frame, String expression, Duration limit, CompletableFuture<Variable> answer) {
    }

    /**
     * A run in progress: what the host asked for; the fiber it runs on; the ID of the first fiber
     * created after it started; the stop it runs in; its compiled expression, the registers of the
     * frame that the expression reads, and whether it is a statement; the native frame it runs in;
     * and why it was ended before its code finished, if it was.
     */
    private record Running(Run run, Fiber fiber, long firstFiber, Halt halt, MethodStructure lambda, int[] args,
                           boolean statement, Frame trampoline, AtomicReference<String> ended) {
        /**
         * @return true if the frame runs this run's code: on its fiber, or on a fiber of another
         *         service that the code calls, which began after the run did
         */
        boolean includes(Frame frame) {
            Fiber other = frame.f_fiber;
            return other == fiber || other.getId() >= firstFiber && other.isAssociated(fiber);
        }

        /**
         * @return how long the run may take
         */
        Duration limit() {
            return run.limit();
        }
    }

    /**
     * A method's source lines, 1-based: the lines it spans and those where its code starts.
     */
    private record MethodLines(int first, int last, NavigableSet<Integer> lines) {
    }
}
