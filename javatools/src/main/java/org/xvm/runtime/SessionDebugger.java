package org.xvm.runtime;

import java.io.IOException;

import java.math.BigInteger;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import java.time.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

import java.util.stream.Collectors;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.OpCallable;
import org.xvm.asm.OpInvocable;
import org.xvm.asm.Version;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Nop;

import org.xvm.compiler.EvalCompiler;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.xConstrainedInteger;
import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;
import org.xvm.runtime.template.numbers.xUnconstrainedInteger;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

/**
 * A debugger that its host drives through calls rather than a terminal, for hosts such as an IDE's
 * debug adapter. The host makes the program's modules known ({@link #addModule}), sets breakpoints
 * by source file and line ({@link #setBreakpoints}), learns of each stop through its
 * {@link Listener}, reads the stopped program's threads and stacks ({@link #threads},
 * {@link #stackTrace}) and the variables of their frames ({@link #scopes}, {@link #variables},
 * {@link #evaluate}), changes them ({@link #setVariable}), has a waiting fiber run code
 * ({@link #run}), and
 * resumes it ({@link #resume}), steps one of its fibers ({@link #step}) or pauses it
 * ({@link #pause}, {@link #stopOnEntry}). It debugs the runtime it is set on with
 * {@link Runtime#setDebugger}; the host's own protocol is the host's concern.
 *
 * <p><b>How a host uses it.</b>
 * <ol>
 * <li>Construct it with the {@link Listener} that learns of its stops and of what its logpoints
 *     log.</li>
 * <li>Make the program's modules known ({@link #addModule}): before setting breakpoints in their
 *     files, which are verified against them, and before {@link #stopOnEntry}, which stops at the
 *     first line of a known module.</li>
 * <li>Set the breakpoints of each source file ({@link #setBreakpoints}), choose the thrown
 *     exceptions that stop the program ({@link #setExceptionStops}), and, to stop at the program's
 *     first line, call {@link #stopOnEntry}. Breakpoints and exception stops can change at any
 *     time after, while the program runs or is stopped.</li>
 * <li>Set it on the runtime that runs the program ({@link Runtime#setDebugger}, which attaches
 *     it), and name the container of the host's own code ({@link #setHost}), before the program's
 *     first op. {@code EmbeddingSupport.runMethod} with a debugger does both, and replaces it with
 *     {@link Debugger#NONE} once the run ends or is closed.</li>
 * <li>When the program stops, {@link Listener#stopped} says where and why. While it is stopped,
 *     the host reads its waiting fibers ({@link #threads}), their stacks ({@link #stackTrace}), the
 *     scopes and variables of their frames ({@link #scopes}, {@link #variables}) and the exception
 *     it stopped at ({@link #exceptionInfo}); it evaluates expressions ({@link #evaluate}), changes
 *     variables ({@link #setVariable}), and has a waiting fiber run code ({@link #run}).</li>
 * <li>The host ends the stop: {@link #resume} lets the program run on, and {@link #step} lets it
 *     run until the stepped fiber reaches the line where the step ends, where it stops again. A
 *     program that runs stops at its next line once the host asks for a {@link #pause}.</li>
 * <li>To stop the program for good, the host calls {@link #terminate}, as closing an embedded
 *     run does. Once another debugger replaces this one ({@link #detach}), it never stops the
 *     program again.</li>
 * </ol>
 *
 * <p><b>Threads and locks.</b> The host may call the methods above from any thread, the
 * listener's included. The listener runs on the interpreter thread of the fiber that stopped, or
 * that reached a logpoint, and never while this debugger holds its lock. A host call takes the
 * lock only briefly, though it may wait for it while a waiting fiber reads variables or compiles
 * code to run, and never waits for the program to run. Reads, evaluations and runs answer with a
 * future, which another thread completes ({@link CompletableFuture#completeAsync}), never while
 * the lock is held, so what the host does with an answer may call this debugger again. One rule
 * follows from where the work is done: the fiber that stopped makes reads of its own frames at
 * once, on the listener's thread, but starts a run in one of its frames only once
 * {@link Listener#stopped} returns, so the listener must not wait for the answer of such a run.
 * The hooks of the {@link Debugger} interface are the runtime's, called on the interpreter thread
 * of the fiber they are about; the host does not call them.
 *
 * <p><b>Handles.</b> A thread ID is its fiber's ID, which stays the same while the fiber lives,
 * from one stop to the next. A frame ID and a variables reference belong to one stop: each stop
 * hands out new ones, and none is handed out twice, so a handle from an earlier stop finds nothing
 * rather than something else. Once the stop ends, by a resume, a step or the program's end, a
 * method given such a handle answers that there is nothing: no frames, scopes or variables, or a
 * failure saying that the frame is not one of the stopped program's. Within a stop,
 * {@link #scopes} answers with the same reference each time it is asked about a frame.
 *
 * <p><b>Failures.</b> An evaluation or a run that fails holds an {@link IllegalArgumentException}
 * whose message is the reason in words, written to be shown to the user as it is, such as
 * "box.fail() threw IllegalState: no." or "The program resumed before the expression was
 * evaluated."; {@link CompletableFuture#join} wraps it in a {@code CompletionException}, and
 * {@link CompletableFuture#get} in an {@code ExecutionException}. A read of variables fails only
 * if a value cannot be read, as an evaluation can too, with the exception that reading it threw.
 *
 * <p><b>Speed.</b> While there is nothing to check, no breakpoint, exception stop, step, pause,
 * stop or run, the runtime's "debugger is active" flag is off, and the program runs at full speed.
 * Otherwise every line it reaches and every exception it throws call this debugger.
 *
 * <p><b>Stops.</b> A stop holds the whole runtime: the fiber that stops waits on its interpreter
 * thread, and so does every other fiber that reaches a line while the program is stopped. Each
 * takes a snapshot of its stack before it waits, so the host reads immutable copies and never a
 * running frame. The snapshots are discarded when the program resumes. Fibers keep yielding to each
 * other as they do without a debugger, also while one of them steps.
 *
 * <p><b>Steps.</b> A step resumes the whole program and stops it again at the next line the stepped
 * fiber reaches: over a call, in the same frame; into one, in any frame of a fiber that the stepped
 * one calls or is called by; out of one, once its frame returns. A frame that returns, or that an
 * exception leaves, during a step over continues it as a step into, so it stops at its caller's
 * next line. Any other stop, such as a breakpoint, ends the step.
 *
 * <p><b>Pauses.</b> A pause stops the program at the next line any of its fibers reaches, with the
 * reason "pause", or, asked for before the program starts, at its first line, with the reason
 * "entry": the first line of a module the host made known, not of the libraries that run before it,
 * or of any code if none is known. A program whose fibers all wait, for input or for each other,
 * stops when one of them next runs.
 *
 * <p><b>Exceptions.</b> A thrown exception stops the program, with the reason "exception", at the
 * frame that throws it, when the host asks for every thrown exception, or for those that no frame
 * will catch: no frame of the throwing fiber, nor of the fibers that called it, has a "catch" for
 * it then. The exception is that frame's first variable, {@link #EXCEPTION}, while the program is
 * stopped there; it stops the program once, not again as it leaves each frame.
 *
 * <p><b>Variables, expressions and code.</b> Variables are read on the interpreter thread of the
 * waiting fiber whose frame holds them, which reads them while it waits, so a read never races the
 * program; {@link #variables} answers once that thread has read them, on another thread, so what
 * the host does with an answer never runs while this debugger holds its lock. Only the waiting
 * fiber's own frames have variables, not those of the fiber that called it, and another service's
 * state is not expanded: a read touches only the waiting service's state and immutable values. A
 * variable's reference to its fields or elements, like a frame's ID, lasts until the program
 * resumes. An expression, such as {@code point.x + 1} or {@code ints[i] > 0}, is evaluated the same
 * way, without running any of the program's code: its operators run the runtime's own native
 * operations ({@link PureExpression}). Any other expression, such as a call, runs as code on the
 * waiting fiber ({@link #run}), which leaves its wait to run it and then waits again, while the
 * program's other fibers stay where they wait; a time limit, a cancel, or the program's resume or
 * end ends such a run before its code does. An object's fields are followed by lazy variables, its
 * computed properties and its {@code toString()}, whose values take running its code: reading a
 * lazy variable's reference runs that code as such a run, for at most {@link #ON_DEMAND}.
 *
 * <p><b>Breakpoints.</b> A breakpoint is installed by file and line: it stops every fiber that
 * reaches the first op of that line in any method compiled from that file. A file is the module's
 * source directory and the class's source path, as the compiler records them, compared as real
 * paths. A breakpoint may have a condition, an expression whose Boolean value must be True; a hit
 * count, which counts the hits whose condition holds; and a log message, which makes it a logpoint
 * that logs the message, with the values of the expressions in braces in it, and does not stop. The
 * fiber that reaches it evaluates them on its own thread, as it would variables; a condition that
 * cannot be evaluated stops the program and says why. Thrown exceptions may stop the program only
 * for some exception classes, those named and the classes that extend them, and the host can read
 * the exception that stopped the program ({@link #exceptionInfo}).
 *
 * <p><b>The host's code, and the program's end.</b> The host's own code, in the container it names
 * with {@link #setHost}, is not debugged, and stacks, the exception's included, end where the host
 * calls the program. When the program is stopped for good ({@link #terminate}), its waiting fibers
 * end without running more of it, and so does any fiber that reaches a line afterwards.
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
     * The most elements of an array that a read of all of them reads; a last entry says how many
     * more there are. A read of a part of them, from a start for a count, reads that part
     * ({@link #variables(int, boolean, Filter, int, int)}).
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
     * The value of a lazy variable, whose value is computed only when the host reads its
     * reference.
     */
    public static final String LAZY = "(...)";

    /**
     * The name of an object's lazy variable that shows its {@code toString()}.
     */
    public static final String TO_STRING = "toString()";

    /**
     * How long the code that computes a lazy variable's value may run (decided 2026-10-23).
     */
    public static final Duration ON_DEMAND = Duration.ofSeconds(1);

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
     * Why nothing is changed by a reference that is not one of this stop's frames or values.
     */
    private static final String NOT_CHANGED =
            "The variable is not one of the stopped program's, so it cannot be changed.";

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
     * The source paths of each module's classes, by module name, once {@link #modules} has read
     * them; they do not change while the program runs.
     */
    private final Map<String, List<String>> moduleSources = new ConcurrentHashMap<>();

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
     * The host's view of the debugger's events. Both are called on an interpreter thread, never
     * while the debugger holds its lock, so they may call the debugger; what they do holds up the
     * fiber that called them, and with a stop, the whole program.
     */
    public interface Listener {
        /**
         * The program has stopped. Called on the interpreter thread that stopped, which waits
         * once this returns, until the host calls {@link #resume}. The snapshots that
         * {@link #threads} and {@link #stackTrace} return are taken before this is called.
         *
         * <p>The listener may read the stopped program here: the stopping fiber reads its own
         * frames at once, on this thread, and the other waiting fibers on theirs. It must not wait
         * for a {@link #run} in a frame of the stopping fiber, which starts only once this
         * returns. If this throws, the program is not held: the stop ends as at a resume, and the
         * exception goes on up the interpreter thread.
         *
         * @param stop  where and why the program stopped
         */
        void stopped(Stop stop);

        /**
         * A logpoint logged a message. Called on the interpreter thread that reached the logpoint,
         * which runs on once this returns; the program does not stop.
         *
         * @param message  the message, with the values of the expressions in it, or why one cannot
         *                 be evaluated, in angle brackets in its place
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
     * that expression's value, and does not stop. The expressions are those that {@link #evaluate}
     * takes, which run none of the program's code.
     *
     * @param id          the breakpoint's ID, which the host chooses, and which a stop at it
     *                    reports ({@link Stop#breakpointIds})
     * @param line        the 1-based line of the source file
     * @param condition   the condition, or null for none
     * @param hitCount    the hit count, or null for none
     * @param logMessage  the log message, or null for a breakpoint that stops
     */
    public record Line(int id, int line, String condition, String hitCount, String logMessage) {
        /**
         * A breakpoint that stops every time a fiber reaches it.
         *
         * @param id    the breakpoint's ID, which the host chooses
         * @param line  the 1-based line of the source file
         */
        public Line(int id, int line) {
            this(id, line, null, null, null);
        }
    }

    /**
     * A breakpoint as installed: its ID, the line it was installed on, which may follow the
     * requested line when that line has no code of its own, whether it was installed, and if not,
     * why.
     *
     * @param id        the requested breakpoint's ID
     * @param line      the 1-based line it is installed on; for one that is not installed, the
     *                  requested line, or the line it would be installed on if only its condition,
     *                  hit count or log message cannot be understood
     * @param verified  true if it is installed
     * @param message   why it is not installed: {@link #NOT_IN_PROGRAM}, {@link #NO_CODE}, or what
     *                  is wrong with its condition, hit count or log message; null if it is
     */
    public record Breakpoint(int id, int line, boolean verified, String message) {
    }

    /**
     * A fiber of the stopped program: its ID, and its service's name, with what the fiber does if
     * it does not wait at a line.
     *
     * @param id    the fiber's ID, the thread ID that the other methods take; it stays the same
     *              while the fiber lives
     * @param name  the name of the fiber's service, such as {@code "Counter"}; for a fiber that
     *              does not wait at a line, followed by what it does, such as {@code "Counter
     *              (waiting)"}; and for a service of another container than the stopped fiber's,
     *              after that container's module, such as {@code "inner.example.org/Counter"}
     */
    public record ThreadInfo(int id, String name) {
    }

    /**
     * A module of the program: its own or a library it uses.
     *
     * @param name       the module's name, such as {@code "ecstasy.xtclang.org"}
     * @param version    the module's version, or null if it has none
     * @param sourceDir  the source directory the module was compiled from, as it recorded it, or
     *                   null if it is not known
     * @param sources    the source paths of the module's classes within that directory, in order
     * @param own        true for a module of the program's own, which {@link #addModule} made
     *                   known, and false for a library
     */
    public record ModuleInfo(String name, String version, String sourceDir, List<String> sources, boolean own) {
    }

    /**
     * A call that the line where a fiber waits makes, which {@link #stepInto} steps into.
     *
     * @param id     the call's ID, which {@link #stepInto} takes, until the stop ends
     * @param label  what the call calls: the name of a method or function and the types of its
     *               parameters, such as {@code "twice(Int)"}, or {@code "new Point(Int, Int)"} for
     *               a constructor
     */
    public record StepTarget(int id, String label) {
    }

    /**
     * One frame of a stopped fiber's stack: an ID unique within the stop, the method's name, the
     * source file, or null when it is not known, the 1-based line, or 0, and the module and source
     * path that the source file is made of.
     *
     * @param id      the frame's ID, which {@link #scopes}, {@link #evaluate} and {@link #run}
     *                take, until the stop ends; only the fiber's own frames have scopes, not those
     *                of the fibers that called it
     * @param name    the method's name after its class's, such as {@code "Point.toString"}, or
     *                after its module's simple name for a method of a module
     * @param path    the source file, as the compiler recorded it: the source directory of the
     *                class's own module and the class's source path; null if it is not known
     * @param line    the 1-based line where the frame is, or 0 if it is not known
     * @param module  the name of the class's module, such as {@code "ecstasy.xtclang.org"}, or null
     * @param source  the class's source path within its module's source directory, such as
     *                {@code "ecstasy/collections/Collection.x"}, or null
     */
    public record StackFrameInfo(int id, String name, String path, int line, String module, String source) {
    }

    /**
     * A scope of a stopped frame: its name, and the reference that {@link #variables} reads its
     * variables by.
     *
     * @param name                the scope's name, {@link #LOCALS}
     * @param variablesReference  the reference that {@link #variables} reads the scope's variables
     *                            by, until the stop ends
     */
    public record Scope(String name, int variablesReference) {
    }

    /**
     * A variable of a scope, or a field or element of a value: its name, its value and its type in
     * words, the reference that {@link #variables} reads its fields or elements by, or 0 if it has
     * none, and whether it is lazy. An array of more than {@link #MAX_ELEMENTS} elements ends with a
     * variable named "..." whose value says how many more there are.
     *
     * <p>A lazy variable is one whose value takes running the program's code: an object's computed
     * property, or its {@code toString()} ({@link #TO_STRING}). Its value is {@link #LAZY}, its type
     * {@code ""}, since it has no value yet, and reading its reference runs the code and answers with
     * one variable, of the same name, that has the value and its type.
     *
     * @param name                the variable's or field's name, an element's index in brackets,
     *                            such as {@code "[0]"}, or the expression that was evaluated or run
     * @param value               the value in words, cut off with "..." after 1000 characters,
     *                            followed by its identity code, such as {@code #1023456}, for a
     *                            mutable object or array; {@link #UNASSIGNED} for a variable that
     *                            has no value yet, {@link #NO_VALUE} for code that has none, and
     *                            {@link #LAZY} for a lazy variable
     * @param type                the value's type in words, or {@code ""} if it is not known
     * @param variablesReference  the reference that {@link #variables} reads the value's fields or
     *                            elements by, or a lazy variable's value, until the stop ends; 0 if
     *                            it has none to show, as for an object of another service
     * @param lazy                true if reading the reference runs code to compute the value
     * @param elements            the number of an array's elements, which the host can read a part
     *                            at a time ({@link #variables(int, boolean, Filter, int, int)}); 0
     *                            for any other value
     */
    public record Variable(String name, String value, String type, int variablesReference, boolean lazy,
                           int elements) {
        /**
         * A variable that is not lazy, and not of an array.
         */
        public Variable(String name, String value, String type, int variablesReference) {
            this(name, value, type, variablesReference, false, 0);
        }

        /**
         * A variable that is not of an array.
         */
        public Variable(String name, String value, String type, int variablesReference, boolean lazy) {
            this(name, value, type, variablesReference, lazy, 0);
        }
    }

    /**
     * Which of a value's children a read gives, as a host asks with DAP's filter: all of them, the
     * named ones, which are a frame's locals and an object's fields and lazy variables, or the
     * indexed ones, which are an array's elements.
     */
    public enum Filter {
        ALL,
        NAMED,
        INDEXED
    }

    /**
     * A name that the text typed in a stopped frame can be completed with: its label, and what it
     * names, {@code "variable"}, {@code "field"}, {@code "property"} or {@code "method"}.
     *
     * @param label  the name
     * @param kind   what it names, as DAP's completion item types call them
     */
    public record Completion(String label, String kind) {
    }

    /**
     * The completions of the name typed at an offset of a text: the part of the text they replace,
     * from start, length characters long, which is the part of the name typed so far, and the names
     * that start with it.
     *
     * @param start   the index in the text of the name's first character
     * @param length  how many characters of the name are typed
     * @param items   the names that start with what is typed
     */
    public record Completions(int start, int length, List<Completion> items) {
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
     *
     * @param reason         {@code "breakpoint"}, also for {@code assert:debug}, {@code "step"},
     *                       {@code "exception"}, {@code "pause"} or {@code "entry"}
     * @param description    what stopped it in words, or null: an exception's type and text,
     *                       {@code "assert:debug"}, or why a breakpoint's condition cannot be
     *                       evaluated
     * @param threadId       the ID of the fiber that stopped
     * @param breakpointIds  the IDs of the breakpoints it stopped at, which the host chose;
     *                       none for {@code assert:debug} and for a stop of any other reason
     */
    public record Stop(String reason, String description, int threadId, List<Integer> breakpointIds) {
    }

    /**
     * The exception the program stopped at: its type and its text, if it has one, the stack where
     * it was thrown, as the runtime writes a stack trace but without the host's frames, and
     * whether no frame will catch it.
     *
     * @param type        the exception's type, such as {@code "IllegalState"}
     * @param text        its text, or null if it has none
     * @param stackTrace  the stack where it was thrown, a frame to a line, of its fiber and of the
     *                    fibers that called it, down to where the host called the program
     * @param uncaught    true if no frame of its fiber, nor of the fibers that called it, will
     *                    catch it
     */
    public record ExceptionInfo(String type, String text, String stackTrace, boolean uncaught) {
    }

    /**
     * Make the code of a module known, so that breakpoints in its source files can be verified.
     * The structures are only read; the runtime may run its own copy of the module.
     *
     * <p>Call it for each module of the program, before setting breakpoints in its files and before
     * {@link #stopOnEntry}. Breakpoints can only be set in the files of known modules, and a stop
     * on entry stops in a known module. The code of other modules, such as libraries, runs under
     * this debugger too: its frames are in the stacks, a step can go into it, and a pause can stop
     * in it.
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
     * <p>The host may call it at any time, while the program runs or is stopped; the breakpoints
     * apply from the next line a fiber reaches. A file is matched by its real path: the path given,
     * and each method's source file as the compiler recorded it, are resolved as the file system
     * resolves them, so a link or a relative path finds the same file. Two breakpoints that end up
     * on one line are both installed, and a stop there reports the first, whose condition, hit
     * count and log message apply.
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
     * Choose which thrown exceptions stop the program, at the frame that throws them, of any
     * class. The host may call it at any time; the choice replaces the one before.
     *
     * @param all       true to stop at every thrown exception
     * @param uncaught  true to stop at the exceptions that no frame will catch
     */
    public synchronized void setExceptionStops(boolean all, boolean uncaught) {
        setExceptionStops(all, List.of(), uncaught, List.of());
    }

    /**
     * Choose which thrown exceptions stop the program, at the frame that throws them, and of which
     * classes: one named, or a class that extends one, or any when none is named. The host may
     * call it at any time; the choice replaces the one before.
     *
     * <p>A class is named by its simple name, such as {@code "IllegalState"} or
     * {@code "OutOfBounds"}, which is matched against the thrown exception's class and the classes
     * it extends; an interface or a mixin of the class does not count. An exception that stops
     * the program does not stop it again as it leaves each frame. Both choices may be made: an
     * exception that no frame will catch then stops the program if either takes it.
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
     * The exception the program stopped at, as the host shows it for a stop with the reason
     * "exception"; the exception is also the throwing frame's first variable,
     * {@link #EXCEPTION}, with its fields.
     *
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
     * @return true while the program is stopped: from just before {@link Listener#stopped} is
     *         called until the stop ends; a run of code in the stop does not end it
     */
    public boolean isStopped() {
        return halt != null;
    }

    /**
     * The modules of the program, while it runs or is stopped: those of its own that {@link
     * #addModule} made known and the libraries they use, as the runtime linked them for the
     * program, in name order; none before the program starts.
     *
     * @return the program's modules
     */
    public List<ModuleInfo> modules() {
        Runtime attached = runtime;
        if (attached == null) {
            return List.of();
        }
        var modules = new TreeMap<String, ModuleStructure>();
        for (Container container : attached.containers()) {
            if (moduleNames.contains(container.getModule().getName())) {
                container.getConstantPool().getFileStructure().children().stream()
                        .filter(module -> !module.isFingerprint())
                        .forEach(module -> modules.putIfAbsent(module.getName(), module));
            }
        }
        return modules.values().stream().map(module -> {
            LiteralConstant dir     = module.getSourceDir();
            Version         version = module.getVersion();
            return new ModuleInfo(module.getName(), version == null ? null : version.toString(),
                    dir == null ? null : dir.getValue(),
                    moduleSources.computeIfAbsent(module.getName(), _ -> sources(module)),
                    moduleNames.contains(module.getName()));
        }).toList();
    }

    /**
     * @return the source paths of the classes of a module, in order
     */
    private static List<String> sources(ModuleStructure module) {
        var paths = new TreeSet<String>();
        var todo  = new ArrayDeque<Component>(List.of(module));
        while (!todo.isEmpty()) {
            Component component = todo.pop();
            if (component instanceof ClassStructure clazz && clazz.getSourcePath() != null) {
                paths.add(clazz.getSourcePath().getValue());
            }
            todo.addAll(component.children());
        }
        return List.copyOf(paths);
    }

    /**
     * The fibers of the stopped program. Those that wait at a line come first: the one that
     * stopped the program, then those that reached a line since, which the host can read. The
     * other fibers of the program's services follow, in every container but the host's and the
     * native one, in the order their services were made, with what they do: waiting, as for an
     * answer from another service, paused, not started, running, as in a native call, or ending.
     * A fiber that reaches a line during the stop moves to the first ones.
     *
     * @return the fibers of the stopped program; none while the program runs
     */
    public synchronized List<ThreadInfo> threads() {
        Halt current = halt;
        if (current == null) {
            return List.of();
        }

        var threads = new ArrayList<ThreadInfo>();
        current.stacks.forEach((id, snapshot) -> threads.add(new ThreadInfo(id, snapshot.name())));
        Container stopped = stoppedContainer(current);
        for (Fiber fiber : programFibers(stopped)) {
            int id = threadId(fiber);
            if (!current.stacks.containsKey(id)) {
                ServiceContext service   = fiber.getContext();
                Container      container = service.getContainer();
                String         name      = container == stopped ? service.getName()
                                                                : container.getModule().getName() + '/' + service.getName();
                threads.add(new ThreadInfo(id, name + " (" + doing(fiber.getStatus()) + ')'));
            }
        }
        return List.copyOf(threads);
    }

    /**
     * The stack of a fiber of the stopped program: its own frames, then those of the fibers that
     * called it, in other services, down to where the host called the program. Native frames are
     * left out, as are the host's. A fiber that waits at a line has the stack it had when it began
     * to wait, whose frames have their variables; one that waits otherwise, is paused or ends has
     * its stack without variables; one that runs or has not started has none.
     *
     * @param threadId  the ID of a fiber that {@link #threads} returned
     *
     * @return the fiber's stack, innermost frame first; empty if the program runs or the fiber has
     *         no stack
     */
    public synchronized List<StackFrameInfo> stackTrace(int threadId) {
        Halt current = halt;
        if (current == null) {
            return List.of();
        }
        Snapshot snapshot = current.stacks.get(threadId);
        return snapshot != null
                ? snapshot.frames()
                : current.others.computeIfAbsent(threadId, id -> othersStack(stoppedContainer(current), id));
    }

    /**
     * The scopes of a frame: one, {@link #LOCALS}, with its exception, if it throws one, its
     * {@code this}, and its named variables. Asked again about the same frame in the same stop, it
     * answers with the same reference.
     *
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
     * <p>Reading runs none of the program's code: a value's text is the runtime's own, its fields
     * are read as they are stored, and an array shows its first {@link #MAX_ELEMENTS} elements. A
     * mutable object's or array's text ends with its identity code, such as {@code #1023456}, as
     * the console debugger's DI shows it, which tells it apart from another with the same fields;
     * an immutable value, such as a const, has none, since its value tells it apart. A
     * value has fields or elements to read if it belongs to the waiting fiber's service or is
     * immutable; an object of another service is read only on that service's own thread, so its
     * variable has no reference. Each value with fields or elements gets a new reference.
     *
     * <p>An object's fields are followed by its lazy variables ({@link Variable#lazy}): one for each
     * computed property, a property without a field whose getter computes its value, and one for its
     * {@code toString()} ({@link #TO_STRING}). Reading a lazy variable's reference runs its code, the
     * getter or {@code toString()}, on the waiting fiber, as {@link #run} runs code, so what it
     * changes stays changed and nothing in it stops the program; it may take at most
     * {@link #ON_DEMAND}. The answer is one variable, named as the lazy one, with the value: a
     * property's value as {@link #run} shows it, or the String that {@code toString()} returned, in
     * quotes as other strings are shown. A run
     * that throws, or that does not end in time, fails the read with the reason, such as
     * "fail threw IllegalState: no." Cancelling the answer ends the run.
     *
     * @param reference  a reference that {@link #scopes} or an earlier read returned
     *
     * @return the variables, in order: a frame's {@code this} and its named registers, an object's
     *         fields and then its lazy variables, an array's elements, or a lazy variable's value;
     *         none if the reference is not one of this stop's, or the program resumes first. Fails
     *         if a value cannot be read, or a lazy variable's code throws or does not end in time.
     */
    public CompletableFuture<List<Variable>> variables(int reference) {
        return variables(reference, false);
    }

    /**
     * Read the variables of a scope, or the fields or elements of a value, as
     * {@link #variables(int)} does, and show their integer values in hexadecimal if hex is true:
     * {@code 0x1F}, and a negative one in its type's two's complement, such as {@code 0xFF} for
     * an {@code Int8} of -1, as a host asks for with a value format.
     *
     * @param hex  true to show integers in hexadecimal
     *
     * @return as {@link #variables(int)} does
     */
    public CompletableFuture<List<Variable>> variables(int reference, boolean hex) {
        return variables(reference, hex, Filter.ALL, 0, 0);
    }

    /**
     * Read a part of the variables of a scope, or of the fields or elements of a value, as
     * {@link #variables(int, boolean)} does: those the filter takes, from the one at start, count of
     * them, or all from there if count is 0. An array's elements are read only for that part, so a
     * host can read a large array a page at a time, as {@link Variable#elements} tells it is one; a
     * read of all of them from start reads its first {@link #MAX_ELEMENTS}, then says how many more
     * there are. A lazy variable's value is not filtered.
     *
     * @param filter  which children to read
     * @param start   the index of the first one to read
     * @param count   how many to read, or 0 for all from start
     *
     * @return as {@link #variables(int)} does, but only the part asked for
     */
    public CompletableFuture<List<Variable>> variables(int reference, boolean hex, Filter filter, int start, int count) {
        var future = new CompletableFuture<List<Variable>>();
        synchronized (this) {
            Halt   current = halt;
            Target target  = current == null ? null : current.targets.get(reference);
            if (target instanceof Lazy lazy) {
                return compute(current, lazy, hex);
            }
            Read read = new Read(target, hex, filter, Math.max(start, 0), Math.max(count, 0), future);
            if (target == null || !request(current, read)) {
                future.complete(List.of());
            }
        }
        return future;
    }

    /**
     * Run the code that computes a lazy variable's value, on the waiting fiber the variable belongs
     * to, as {@link #run} runs code, for at most {@link #ON_DEMAND}. Called while holding this
     * debugger's monitor.
     *
     * @return the variable with the value, or none if its fiber no longer waits; fails, with the
     *         reason in words, if the code throws or does not end in time. Cancelling it ends the run.
     */
    private CompletableFuture<List<Variable>> compute(Halt current, Lazy lazy, boolean hex) {
        if (!current.stacks.containsKey(lazy.thread())) {
            return CompletableFuture.completedFuture(List.of());
        }

        Computed computed = lazy.computed();
        String   name     = computed.property() == null ? TO_STRING : computed.property().getName();
        Run      request  = new Run(current.stacks.get(lazy.thread()).frame(), name, computed, ON_DEMAND, false, hex,
                new CompletableFuture<>());
        queue(current, lazy.thread(), request);

        CompletableFuture<Variable>       answer = answer(request);
        CompletableFuture<List<Variable>> value  = answer.thenApply(List::of);
        value.whenComplete((_, _) -> {
            if (value.isCancelled()) {
                answer.cancel(false);
            }
        });
        return value;
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
     * <p>Since it runs none of the program's code, an evaluation changes nothing and needs no time
     * limit, which suits watches and hovers; breakpoints' conditions and log messages take the
     * same expressions. An expression it refuses, such as a call, can be run instead
     * ({@link #run}), so a host can try this first and run what it refuses.
     *
     * @param frameId     the ID of a frame that {@link #stackTrace} returned
     * @param expression  the expression to evaluate
     *
     * @return the value, as a variable named by the expression, with a reference to its fields or
     *         elements; fails, with the reason in words, if the text is not such an expression, it
     *         cannot be evaluated, the frame is not one of this stop's, or the program resumes first
     */
    public CompletableFuture<Variable> evaluate(int frameId, String expression) {
        return evaluate(frameId, expression, false);
    }

    /**
     * Evaluate an expression in a stopped frame as {@link #evaluate(int, String)} does, and show its
     * integer value in hexadecimal if hex is true, as {@link #variables(int, boolean)} does.
     *
     * @param hex  true to show integers in hexadecimal
     *
     * @return as {@link #evaluate(int, String)} does
     */
    public CompletableFuture<Variable> evaluate(int frameId, String expression, boolean hex) {
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
            if (at == null || !request(current, new Read(new Evaluation(at.thread(), at.frame(), parsed), hex, Filter.ALL, 0, 0,
                    future))) {
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
     * Change a variable of the stopped program: a frame's local, an object's field or an array's
     * element, named as a read by its reference gave it, to the value of an expression, which is
     * evaluated as {@link #evaluate} evaluates one, without running any of the program's code; a
     * literal takes the variable's type, as the compiler converts it, such as {@code 5} for an
     * {@code Int8}. The names in the expression are those of the frame that the local is in, or of
     * the frame where the fiber that holds the object or array waits. The waiting fiber changes it
     * on its own thread, as it reads variables, and the program goes on with the new value once it
     * resumes.
     *
     * <p>What it refuses, saying why: a value that is not of the variable's type; {@code this} and
     * {@link #EXCEPTION}; a variable or field that a {@code Ref} holds, such as a captured variable
     * or a property with an annotation, whose change could run code; a field of an immutable
     * object, such as a const; an element of an immutable array; a computed property, which has no
     * field; and a name the reference does not read.
     *
     * @param reference   a reference that {@link #scopes} or a read returned: a frame's locals, or
     *                    an object's fields or an array's elements
     * @param name        the variable's or field's name, or an element's index in brackets, such
     *                    as {@code "[1]"}
     * @param expression  the expression whose value the variable takes
     * @param hex         true to show the new value's integers in hexadecimal
     *
     * @return the variable with its new value, as a read shows it; fails, with the reason in words,
     *         if the expression cannot be evaluated, the variable cannot take its value, the
     *         reference is not one of this stop's, or the program resumes first
     */
    public CompletableFuture<Variable> setVariable(int reference, String name, String expression, boolean hex) {
        PureExpression parsed;
        try {
            parsed = PureExpression.parse(expression);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        var future = new CompletableFuture<List<Variable>>();
        synchronized (this) {
            Halt   current = halt;
            Target target  = current == null ? null : current.targets.get(reference);
            if (!(target instanceof Locals || target instanceof Children)
                    || !request(current, new Read(new Assignment(target, name, parsed), hex, Filter.ALL, 0, 0, future))) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_CHANGED));
            }
        }
        return future.thenApply(vars -> {
            // a change that the resume abandoned has no variables
            if (vars.isEmpty()) {
                throw new IllegalArgumentException(RESUMED_FIRST);
            }
            return vars.getFirst();
        });
    }

    /**
     * The names that complete the name typed at an offset of a text in a stopped frame, as a debug
     * console offers them: after a dot, the members of the value of the path before it, evaluated
     * as {@link #evaluate} evaluates one, which are its type's fields, properties and methods;
     * otherwise the frame's variables, {@code this}, and the members of {@code this}, its private
     * ones too, which the frame's code can use. Only the names that start with what is typed of the
     * name are given, and nothing of the program runs.
     * The waiting fiber that holds the frame reads them on its own thread, as it reads variables.
     *
     * @param frameId  the ID of a frame that {@link #stackTrace} returned
     * @param text     the text typed so far
     * @param offset   the index in the text where the name being typed ends, such as its length
     *
     * @return the completions, which are none if the path before a dot cannot be evaluated; fails
     *         if the frame is not one of this stop's
     */
    public CompletableFuture<Completions> completions(int frameId, String text, int offset) {
        var answer = new CompletableFuture<Completions>();
        var read   = new CompletableFuture<List<Variable>>();
        synchronized (this) {
            Halt    current = halt;
            FrameAt at      = current == null ? null : current.frames.get(frameId);
            if (at == null
                    || !request(current, new Read(new Completing(at.thread(), at.frame(), text, offset, answer), false,
                            Filter.ALL, 0, 0, read))) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_STOPPED));
            }
        }
        // the read completes once the answer has been made; one that the resume abandoned has none
        Completions none = new Completions(Math.max(0, Math.min(offset, text.length())), 0, List.of());
        return read.thenApply(_ -> answer.getNow(none));
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
     * <p>For a program whose code waits on work that its other fibers do, a host can let the whole
     * program run meanwhile instead ({@link #run(int, String, Duration, boolean)}).
     *
     * <p>This method never waits: it asks the fiber that waits at the frame to run the code, and
     * returns. The fiber starts it when no other run is in progress, and its time limit counts
     * from then, not from the call; a run in a frame of the fiber that stopped the program starts
     * only once {@link Listener#stopped} returns. While a run is in progress, {@link #isStopped}
     * stays true and the program stays stopped.
     *
     * @param frameId     the ID of a frame that {@link #stackTrace} returned
     * @param expression  the expression to run
     * @param limit       how long the run may take once it starts, such as two seconds for an
     *                    expression typed into a debug console
     *
     * @return the value, as a variable named by the expression, whose value is the value's
     *         {@code toString()}, with a reference to its fields or elements; fails, with the reason
     *         in words, if the expression does not compile, it or its value's {@code toString()}
     *         throws, the frame is not one of this stop's, or the run ends before its code does.
     *         Cancelling it ends the run.
     */
    public CompletableFuture<Variable> run(int frameId, String expression, Duration limit) {
        return run(frameId, expression, limit, false);
    }

    /**
     * Run an expression's code in a stopped frame as {@link #run(int, String, Duration)} does, and,
     * if program is true, let the whole program run meanwhile, for a program whose code waits on
     * work that its other fibers do, such as a future that another service completes.
     *
     * <p>Then every other fiber of the program runs too, as without a debugger: a fiber that waits
     * in the stop leaves it, and none stops at a breakpoint, an exception, a pause or an
     * {@code assert:debug}, nor makes a stop of its own; and the program's clocks run. Once the run
     * ends, each of them waits in the same stop again, at the next line it reaches, so the threads
     * and stacks of the stop, and the frames' variables, may have changed: the frame IDs and
     * references of a fiber that left the stop no longer read anything, and {@link #threads} and
     * {@link #stackTrace} give its new ones once it waits again. A fiber that leaves the stop drops
     * the reads and runs asked of it, which fail.
     *
     * @param program  true to let the whole program run while the code does
     *
     * @return as {@link #run(int, String, Duration)} does
     */
    public CompletableFuture<Variable> run(int frameId, String expression, Duration limit, boolean program) {
        return run(frameId, expression, limit, program, false);
    }

    /**
     * Run an expression's code in a stopped frame as {@link #run(int, String, Duration, boolean)}
     * does, and show its value in hexadecimal, if it is an integer and hex is true, instead of by
     * its {@code toString()}, as {@link #variables(int, boolean)} shows integers.
     *
     * @param hex  true to show an integer value in hexadecimal
     *
     * @return as {@link #run(int, String, Duration)} does
     */
    public CompletableFuture<Variable> run(int frameId, String expression, Duration limit, boolean program,
                                           boolean hex) {
        Objects.requireNonNull(limit);
        Run request;
        synchronized (this) {
            Halt    current = halt;
            FrameAt at      = current == null ? null : current.frames.get(frameId);
            if (at == null || !current.stacks.containsKey(at.thread())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_STOPPED));
            }
            request = new Run(at.frame(), expression, null, limit, program, hex, new CompletableFuture<>());
            queue(current, at.thread(), request);
        }
        return answer(request);
    }

    /**
     * Queue a run for the waiting fiber with the specified ID, which starts it once no other run is
     * in progress. Called while holding this debugger's monitor.
     */
    private void queue(Halt current, int threadId, Run request) {
        current.runs.computeIfAbsent(threadId, _ -> new ArrayDeque<>()).add(request);
        notifyAll();
    }

    /**
     * @return the run's answer, whose cancelling ends the run
     */
    private CompletableFuture<Variable> answer(Run request) {
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
     * <p>The stop ends as at a {@link #resume}, and all of the program's fibers run meanwhile, not
     * only the stepped one. Any other stop before the step's end, such as at a breakpoint, ends the
     * step; so does the program's end.
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
     * The calls that the line where a fiber waits makes, in the order of its code, which a step
     * into one of them goes into; asked again in the same stop, the same ones.
     *
     * @param frameId  the ID of the frame where a fiber waits, the first of its stack
     *
     * @return the calls; none for another frame, and none for a call of a function that a
     *         variable holds
     */
    public synchronized List<StepTarget> stepInTargets(int frameId) {
        var current = halt;
        var at      = current == null ? null : current.frames.get(frameId);
        if (at == null) {
            return List.of();
        }
        // only the frame where the fiber waits makes the calls of its line next
        var snapshot = current.stacks.get(at.thread());
        return snapshot != null && snapshot.frame() == at.frame()
                ? current.callsOf.computeIfAbsent(frameId, _ -> calls(current, at))
                : List.of();
    }

    /**
     * Resume the stopped program, stepping the specified fiber into one of the calls of its line,
     * as {@link #step} steps into the next one it makes: the program stops again, with the reason
     * "step", at the first line of the code that call runs, or at the next line, as a step over
     * would, if the line does not make that call.
     *
     * @param threadId  the ID of a fiber that {@link #threads} returned
     * @param targetId  the ID of a call that {@link #stepInTargets} returned for its frame
     *
     * @return false if the program runs, the fiber does not wait, or the call is not one of its
     *         line's; then nothing changes
     */
    public synchronized boolean stepInto(int threadId, int targetId) {
        var current  = halt;
        var call     = current == null ? null : current.calls.get(targetId);
        var snapshot = snapshotOf(threadId);
        if (call == null || snapshot == null || call.thread() != threadId) {
            return false;
        }

        // set first, so lines stay checked as the fibers resume
        var frame = snapshot.frame();
        stepping = new Stepping(Step.IN, frame, frame.getFiber(), call.op());
        release();
        return true;
    }

    /**
     * @return the calls that the line where the frame waits makes, from the op after its start to
     *         the next line's
     */
    private List<StepTarget> calls(Halt current, FrameAt at) {
        var frame = at.frame();
        var ops   = frame.getOps();
        var calls = new ArrayList<StepTarget>();
        for (int pc = frame.getPC() + 1; pc < ops.length && !startsLine(frame, pc); pc++) {
            var label = callLabel(frame, ops[pc].ensureOp());
            if (label != null) {
                int id = handles.incrementAndGet();
                current.calls.put(id, new Call(at.thread(), pc));
                calls.add(new StepTarget(id, label));
            }
        }
        return List.copyOf(calls);
    }

    /**
     * @return what the op calls, as {@link StepTarget#label} says it, or null if it calls nothing
     *         that a step into can name
     */
    private static String callLabel(Frame frame, Op op) {
        MethodConstant method = switch (op) {
            case OpInvocable invoke -> invoke.getInvokedMethod(frame);
            case OpCallable  call   -> call.getCalledMethod(frame);
            default                 -> null;
        };
        if (method == null) {
            return null;
        }
        var name   = method.isConstructor() ? "new " + method.getNamespace().getName() : method.getName();
        var params = Arrays.stream(method.getRawParams()).map(TypeConstant::getValueString).collect(Collectors.joining(", "));
        return name + '(' + params + ')';
    }

    /**
     * Stop the program at the next line any of its fibers reaches, with the reason "pause"; does
     * nothing while it is stopped. It returns at once; the stop comes through
     * {@link Listener#stopped} once a fiber next reaches a line, which, while all of them wait, as
     * for input, is when one of them next runs.
     */
    public synchronized void pause() {
        if (halt == null) {
            pendingPause = new Pause("pause", false);
            updateActive();
        }
    }

    /**
     * Stop the program at its first line, with the reason "entry"; called before it starts, and
     * after the program's modules are made known. The first line is that of a known module, not of
     * a library that runs before it, or the first line of any code if no module is known.
     */
    public synchronized void stopOnEntry() {
        pendingPause = new Pause("entry", true);
        updateActive();
    }

    /**
     * Resume the stopped program; does nothing while it runs.
     *
     * <p>What the stop held goes: its waiting fibers run on, and its frame IDs and references find
     * nothing from now on. Reads that a waiting fiber has not yet made answer with no variables,
     * evaluations and runs that have not started fail, saying that the program resumed first, and
     * a run in progress ends, as {@link #run} says. The program's clocks run again.
     */
    public synchronized void resume() {
        release();
    }

    // ----- Debugger interface --------------------------------------------------------------------

    /**
     * Debug the specified runtime, which {@link Runtime#setDebugger} set this debugger on: its
     * "debugger is active" flag is turned on while there is something to check, so that its
     * services call this debugger at every line and thrown exception, and off again otherwise.
     *
     * @param runtime  the runtime whose services this debugger now debugs
     */
    @Override
    public synchronized void attach(Runtime runtime) {
        this.runtime = runtime;
        updateActive();
    }

    /**
     * Leave the host's own code alone, such as the runner that calls the program: frames of the
     * specified container are never stopped, held or ended, and the stacks, the exception's
     * included, end where the host calls the program. Set before the program starts.
     *
     * @param container  the container of the host's own code
     */
    @Override
    public void setHost(Container container) {
        hostContainer = container;
    }

    /**
     * Stop the program for good, as when the host closes it, before it replaces this debugger: a
     * run in progress ends, saying so, the stop, if there is one, ends, and every fiber of the
     * program ends where it is without running more of it: those that waited in the stop, a run's
     * fiber at its next line, and any other fiber at the next line it reaches. No step or pause is
     * made after, and the program does not stop again.
     */
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

    /**
     * Another debugger has replaced this one on the runtime: the breakpoints, the step and the
     * pause are dropped, a stopped program runs on as at a {@link #resume}, and this debugger never
     * stops the program again, nor turns the runtime's "debugger is active" flag on.
     */
    @Override
    public synchronized void detach() {
        detached     = true;
        installed    = new Installed(Map.of(), Map.of());
        stepping     = null;
        pendingPause = null;
        release();
        runtime = null;
    }

    /**
     * @return false: while this debugger is active, a fiber yields to the other fibers of its
     *         service as it does without a debugger, also while it steps, so that breakpoints and
     *         steps do not change how the program's fibers take turns
     */
    @Override
    public boolean holdsFibers() {
        return false;
    }

    /**
     * The program reached {@code assert:debug}: stop it there as at a breakpoint, with the reason
     * "breakpoint" and the description "assert:debug", and no breakpoint ID. The host's code goes
     * on, and so does a run's code, in which nothing stops; once the program is being stopped for
     * good, the fiber ends there instead.
     *
     * @param frame  the frame that reached it
     * @param pc     the index of the op
     *
     * @return the index of the next op, {@link Op#R_CALL} if the fiber runs code first, or
     *         {@link Op#R_EXCEPTION} if the fiber ends or the run's code unwinds
     */
    @Override
    public int activate(Frame frame, int pc) {
        if (isHost(frame)) {
            return pc + 1;
        }
        Running run = runOf(frame);
        if (run != null) {
            return goOn(run, frame, pc);
        }
        if (runsOn(frame)) {
            return terminating ? end(frame) : pc + 1;
        }

        // assert:debug stops the program as a breakpoint does
        return stop(frame, "breakpoint", "assert:debug", List.of(), null, null, f -> terminating ? end(f) : pc + 1);
    }

    /**
     * A fiber reached the op that marks a line of source, while this debugger is active. The host's
     * code goes on. A run's code goes on too, since nothing stops it, unless the run has ended,
     * when it unwinds. While the program is stopped, the fiber waits here, in the stop; otherwise
     * it stops the program at a breakpoint on the line, where a step ends, or for a pause, logs a
     * logpoint's message, or goes on. Once the program is being stopped for good, the fiber ends
     * here.
     *
     * @param frame  the frame that reached the op
     * @param pc     the index of the op
     *
     * @return the index of the next op, {@link Op#R_CALL} if the fiber runs code first, or
     *         {@link Op#R_EXCEPTION} if the fiber ends or the run's code unwinds
     */
    @Override
    public int checkBreakPoint(Frame frame, int pc) {
        if (isHost(frame)) {
            return pc + 1;
        }
        Running run = runOf(frame);
        if (run != null) {
            return goOn(run, frame, pc);
        }
        if (runsOn(frame)) {
            return terminating ? end(frame) : pc + 1;
        }
        return halt == null ? line(frame, pc) : park(frame, f -> line(f, pc));
    }

    /**
     * A frame returns, while this debugger is active: a step over or out of it goes on as a step
     * into, so that it stops at the next line of the frame's caller.
     *
     * @param frame  the frame that returns
     */
    @Override
    public void onReturn(Frame frame) {
        leave(frame);
    }

    /**
     * An exception is thrown, or leaves a frame, while this debugger is active and the fiber does
     * not end; the runtime asks at each frame the exception leaves. An exception that a run's
     * code throws, or its value's {@code toString()}, and that is not caught on the way out, is
     * the run's answer: it is taken from the frame, and the run ends. Otherwise a step over or out
     * of the frame goes on as a step into, as when the frame returns, and the exception stops the
     * program at the program's first frame that throws it, if the choice made with
     * {@link #setExceptionStops} takes it; it stops the program only once, and not in the host's
     * code nor in a run's.
     *
     * @param frame      the frame that throws the exception, or that it leaves
     * @param exception  the exception
     *
     * @return for a run's answer, the index of the op that ends the run in its native frame, the
     *         frame's caller, which goes on there; {@link Op#R_CALL} if the fiber runs code first;
     *         otherwise {@link Op#R_NEXT}, so that the runtime goes on with the exception
     */
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
                && !runsOn(frame) && stopsAt(exception, frame)) {
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
        if (runsOn(frame)) {
            // as without a debugger: no breakpoint, logpoint, hit count, step or pause
            return pc + 1;
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
        if (step != null && step.frame() == frame && (step.step() != Step.IN || step.call() != Stepping.NO_CALL)) {
            stepping = new Stepping(Step.IN, frame, frame.getFiber());
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
                if (Thread.currentThread().isInterrupted() || runsOn(frame)) {
                    // a run of the whole program lets the fiber go on without its stop
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
            if (runsOn(frame)) {
                // the whole program runs while a run does: the fiber leaves the stop, and waits in
                // it again at the next line it reaches once the run has ended
                forget(current, threadId);
                return null;
            }

            List<Read> reads = current.reads.remove(threadId);
            if (reads != null) {
                reads.forEach(read -> read(current, frame, read));
                continue;
            }

            Deque<Run> runs = current.runs.get(threadId);
            if (running == null && runs != null && !runs.isEmpty()) {
                Running run = prepare(current, frame, runs.poll());
                if (run != null) {
                    running = run;
                    updateActive();
                    if (run.program()) {
                        // the other fibers leave their waits, and the clocks run as without a stop
                        if (current.frozen != null) {
                            current.frozen.unfreezeTime();
                        }
                        notifyAll();
                    }
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
    private boolean request(Halt current, Read read) {
        Snapshot snapshot = current.stacks.get(read.target().thread());
        if (snapshot == null) {
            return false;
        }
        if (snapshot.thread() == Thread.currentThread()) {
            read(current, snapshot.frame(), read);
        } else {
            current.reads.computeIfAbsent(read.target().thread(), _ -> new ArrayList<>()).add(read);
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
        return new Snapshot(frame.getContext().getName(), frames(current, frame), Thread.currentThread(), frame);
    }

    /**
     * @return the stack of the fiber at the specified frame, whose own frames have their variables
     *         in the current stop if it is specified, and none otherwise
     */
    private List<StackFrameInfo> frames(Halt current, Frame frame) {
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

            int             frameId = handles.incrementAndGet();
            int             line    = stackFrame.iPC >= 0 ? function.calculateLineNumber(stackFrame.iPC) : 0;
            ClassStructure  clazz   = function.getContainingClass();
            ModuleStructure module  = clazz == null ? null : module(clazz);
            LiteralConstant source  = clazz == null ? null : clazz.getSourcePath();
            stack.add(new StackFrameInfo(frameId, functionName(function), sourcePath(function), line,
                    module == null ? null : module.getName(), source == null ? null : source.getValue()));
            if (current != null && stackFrame.frame != null && stackFrame.frame.f_fiber == frame.f_fiber) {
                current.frames.put(frameId, new FrameAt(threadId, stackFrame.frame));
            }
        }
        return List.copyOf(stack);
    }

    /**
     * @return the stack of a fiber of the stopped program that does not wait at a line, without
     *         variables: one that waits otherwise, is paused or ends stands still, while one that
     *         runs or has not started has no stack to read
     */
    private List<StackFrameInfo> othersStack(Container stopped, int threadId) {
        for (Fiber fiber : programFibers(stopped)) {
            if (threadId(fiber) == threadId) {
                Frame frame = switch (fiber.getStatus()) {
                    case Waiting, Paused, Terminating -> fiber.getFrame();
                    case Initial, Running             -> null;
                };
                return frame == null ? List.of() : frames(null, frame);
            }
        }
        return List.of();
    }

    /**
     * @return the container of the fiber that stopped the program, or of another fiber that waits
     *         at a line while that one runs code; null if none waits
     */
    private static Container stoppedContainer(Halt current) {
        return current.stacks.values().stream()
                .findFirst()
                .map(snapshot -> snapshot.frame().getContext().getContainer())
                .orElse(null);
    }

    /**
     * @return the fibers of the stopped program's services, in the order the services were made:
     *         those of the program's own container, the one below the host's, or the topmost one
     *         without a host, and of the containers nested in it; not those of the host, of the
     *         native container, or of another program that the runtime runs
     */
    private List<Fiber> programFibers(Container stopped) {
        Runtime attached = runtime;
        if (attached == null || stopped == null) {
            return List.of();
        }
        Container host    = hostContainer;
        Container program = stopped;
        for (Container parent = program.getParent();
                parent != null && parent != host && !(parent instanceof NativeContainer);
                parent = parent.getParent()) {
            program = parent;
        }
        Container root = program;
        return attached.containers().stream()
                .filter(container -> nestedIn(container, root))
                .flatMap(container -> container.getServices().stream())
                .sorted(Comparator.comparingLong(ServiceContext::getId))
                .flatMap(service -> service.getFibers().stream())
                .toList();
    }

    /**
     * @return true if the container is the specified one or nested in it
     */
    private static boolean nestedIn(Container container, Container outer) {
        for (Container each = container; each != null; each = each.getParent()) {
            if (each == outer) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return what a fiber with the specified status does, as {@link #threads} names it
     */
    private static String doing(FiberStatus status) {
        return switch (status) {
            case Initial     -> "not started";
            case Running     -> "running";
            case Paused      -> "paused";
            case Waiting     -> "waiting";
            case Terminating -> "ending";
        };
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
    private void read(Halt current, Frame frame, Read read) {
        boolean                           hex    = read.hex();
        Filter                            filter = read.filter();
        CompletableFuture<List<Variable>> future = read.future();
        List<Variable>                    vars;
        try {
            vars = switch (read.target()) {
                case Locals(int _, Frame scope) -> filter == Filter.INDEXED
                        ? List.of()
                        : part(locals(current, frame, scope, hex), read);
                case Children(int _, ArrayHandle array) -> filter == Filter.NAMED
                        ? List.of()
                        : elements(current, frame, array, hex, read.start(), read.count());
                case Children(int _, GenericHandle object) -> filter == Filter.INDEXED
                        ? List.of()
                        : part(fields(current, frame, object, hex), read);
                case Children _ -> List.of();
                case Evaluation(int _, Frame scope, PureExpression e) -> List.of(resolve(current, frame, scope, e, hex));
                case Assignment assignment -> List.of(assign(current, frame, assignment, hex));
                case Completing completing -> {
                    // nothing waits on the answer itself, so making it here runs none of the host's code
                    completing.answer().complete(complete(frame, completing));
                    yield List.of();
                }
                // a lazy variable's value is computed by a run, never read (variables())
                case Lazy _ -> List.of();
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
    private List<Variable> locals(Halt current, Frame waitingFrame, Frame frame, boolean hex) {
        var             vars      = new ArrayList<Variable>();
        ExceptionHandle exception = frame.m_hException;
        if (exception != null) {
            vars.add(variable(current, waitingFrame, EXCEPTION, exception, hex));
        }

        ObjectHandle self = frame.f_hThis;
        if (self != null) {
            vars.add(variable(current, waitingFrame, "this", self, hex));
        }

        for (int i = 0, count = frame.getCurrentVarCount(); i < count; i++) {
            VarInfo info = frame.getVarInfo(i);
            String  name = info == null ? "" : info.getName();
            if (!name.isEmpty()) {
                vars.add(variable(current, waitingFrame, name, frame.f_ahVar[i], hex));
            }
        }
        return vars;
    }

    /**
     * @return the part of the variables that the read asks for: from its start, as many as its
     *         count, or all from there if its count is 0
     */
    private static List<Variable> part(List<Variable> vars, Read read) {
        int from = Math.min(read.start(), vars.size());
        int to   = read.count() == 0 ? vars.size() : (int) Math.min(vars.size(), (long) from + read.count());
        return List.copyOf(vars.subList(from, to));
    }

    /**
     * @return the array's elements from start, count of them, or, if count is 0, at most
     *         {@link #MAX_ELEMENTS} of them, then how many more there are
     */
    private List<Variable> elements(Halt current, Frame waitingFrame, ArrayHandle array, boolean hex, long start,
                                    int count) {
        var  vars = new ArrayList<Variable>();
        long size = array.m_hDelegate.m_cSize;
        long end  = Math.min(size, start + (count == 0 ? MAX_ELEMENTS : count));
        for (long i = start; i < end; i++) {
            ObjectHandle element = element(waitingFrame, array, i);
            if (element == null) {
                break;
            }
            vars.add(variable(current, waitingFrame, "[" + i + "]", element, hex));
        }
        if (count == 0 && end < size) {
            vars.add(new Variable("...", (size - end) + " more", "", 0));
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
    private Variable resolve(Halt current, Frame waitingFrame, Frame frame, PureExpression expression, boolean hex) {
        return variable(current, waitingFrame, expression.text(), expression.evaluate(waitingFrame, frame), hex);
    }

    /**
     * Give a variable of a frame, an object or an array the value of the assignment's expression,
     * on the thread of the fiber waiting at the specified frame.
     *
     * @return the variable with its new value
     *
     * @throws IllegalArgumentException if the variable cannot take the value, saying why
     */
    private Variable assign(Halt current, Frame waitingFrame, Assignment assignment, boolean hex) {
        String name = assignment.name();
        if (name.equals("this") || name.equals(EXCEPTION)) {
            throw new IllegalArgumentException(name + " cannot be changed.");
        }

        return switch (assignment.target()) {
            case Locals(int _, Frame frame) -> {
                int register = register(frame, name);
                if (frame.f_ahVar[register] instanceof RefHandle) {
                    throw new IllegalArgumentException(name + " is held by a Ref, which could run code to change it,"
                            + " so it is not changed.");
                }
                ObjectHandle value = valueFor(waitingFrame, frame, assignment, frame.getVarInfo(register).getType());
                frame.f_ahVar[register] = value;
                yield variable(current, waitingFrame, name, value, hex);
            }

            case Children(int _, ArrayHandle array) -> {
                long            index    = index(name, array);
                TypeConstant    element  = array.getType().resolveGenericType("Element");
                ObjectHandle    value    = valueFor(waitingFrame, waitingFrame, assignment, element);
                var             template = (xArray) array.getTemplate();
                ExceptionHandle pending  = waitingFrame.m_hException;
                if (template.assignArrayValue(waitingFrame, array, index, value) != Op.R_NEXT) {
                    // an immutable array refuses; the exception must not stay on the waiting frame
                    ExceptionHandle refused = waitingFrame.m_hException;
                    waitingFrame.m_hException = pending;
                    throw new IllegalArgumentException(name + " cannot be changed: " + describe(refused) + '.');
                }
                yield variable(current, waitingFrame, name, value, hex);
            }

            case Children(int _, GenericHandle object) -> {
                FieldInfo field = DebugConsole.getFieldLayout(object.getComposition()).get(name);
                if (field == null) {
                    boolean computed = computedProperties(object).stream()
                            .anyMatch(property -> property.getName().equals(name));
                    throw new IllegalArgumentException(computed
                            ? name + " is computed by its getter, and has no field to change."
                            : typeText(object) + " has no field " + name + '.');
                }
                if (!object.isMutable()) {
                    throw new IllegalArgumentException(name + " is a field of an immutable " + typeText(object)
                            + ", which cannot be changed.");
                }
                if (field.isTransient() || field.isInflated()) {
                    throw new IllegalArgumentException(name + " is held by a Ref, which could run code to change it,"
                            + " so it is not changed.");
                }
                ObjectHandle value = valueFor(waitingFrame, waitingFrame, assignment, field.getType());
                object.setField(field.getIndex(), value);
                yield variable(current, waitingFrame, name, value, hex);
            }

            default -> throw new IllegalArgumentException(name + " cannot be changed.");
        };
    }

    /**
     * @return the completions of the name typed at the offset of the text, in the frame, on the
     *         thread of the fiber waiting at the specified frame
     */
    private static Completions complete(Frame waitingFrame, Completing completing) {
        String text  = completing.text();
        int    end   = Math.max(0, Math.min(completing.offset(), text.length()));
        int    start = end;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            --start;
        }

        List<Completion> names;
        if (start > 0 && text.charAt(start - 1) == '.') {
            // the path before the dot: names, dots and indexes in brackets
            int from  = start - 1;
            int depth = 0;
            while (from > 0) {
                char ch = text.charAt(from - 1);
                if (ch == ']') {
                    ++depth;
                } else if (ch == '[') {
                    if (depth == 0) {
                        break;
                    }
                    --depth;
                } else if (depth == 0 && ch != '.' && !Character.isJavaIdentifierPart(ch)) {
                    break;
                }
                --from;
            }
            names = membersOf(waitingFrame, completing.frame(), text.substring(from, start - 1).strip());
        } else {
            names = namesIn(completing.frame());
        }

        String typed = text.substring(start, end);
        return new Completions(start, end - start, names.stream().filter(name -> name.label().startsWith(typed)).toList());
    }

    /**
     * @return the names a frame's code can use: its variables, innermost first, {@code this}, and
     *         all the members of {@code this}
     */
    private static List<Completion> namesIn(Frame frame) {
        var names = new LinkedHashMap<String, Completion>();
        for (int i = frame.getCurrentVarCount() - 1; i >= 0; i--) {
            VarInfo info = frame.getVarInfo(i);
            String  name = info == null ? "" : info.getName();
            if (!name.isEmpty()) {
                names.putIfAbsent(name, new Completion(name, "variable"));
            }
        }
        ObjectHandle self = frame.f_hThis;
        if (self != null) {
            names.putIfAbsent("this", new Completion("this", "variable"));
            members(self, true).forEach(member -> names.putIfAbsent(member.label(), member));
        }
        return List.copyOf(names.values());
    }

    /**
     * @return the members of the value of the path, all of them for {@code this} and the public
     *         ones otherwise, or none if it cannot be evaluated or is unassigned
     */
    private static List<Completion> membersOf(Frame waitingFrame, Frame frame, String path) {
        ObjectHandle value;
        try {
            value = shown(PureExpression.parse(path).evaluate(waitingFrame, frame));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return value == null ? List.of() : members(value, path.equals("this"));
    }

    /**
     * @return the members of the value's type, all of them or the public ones: its fields and
     *         properties, other than constants and type parameters, in the type's order, then the
     *         names of its methods, other than constructors and functions, sorted
     */
    private static List<Completion> members(ObjectHandle value, boolean all) {
        TypeConstant type = all ? value.getType().ensureAccess(Constants.Access.PRIVATE) : value.getType();
        TypeInfo     info = type.ensureTypeInfo();
        var members = new LinkedHashMap<String, Completion>();
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : info.sortedProperties()) {
            PropertyInfo property = entry.getValue();
            if (!property.isConstant() && !property.isFormalType() && (all || property.getRefAccess() == Constants.Access.PUBLIC)) {
                members.putIfAbsent(property.getName(), new Completion(property.getName(), property.hasField() ? "field" : "property"));
            }
        }
        info.getMethods().entrySet().stream()
                .filter(entry -> {
                    MethodInfo method = entry.getValue();
                    IdentityConstant owner = entry.getKey().getNamespace();
                    return (all || method.getAccess() == Constants.Access.PUBLIC) && !method.isConstructor() && !method.isFunction()
                            && !(owner instanceof PropertyConstant) && !(owner instanceof MethodConstant);
                })
                .map(entry -> entry.getKey().getName())
                .sorted()
                .forEach(name -> members.putIfAbsent(name, new Completion(name, "method")));
        return List.copyOf(members.values());
    }

    /**
     * @return the register of the innermost variable of the frame with the specified name
     *
     * @throws IllegalArgumentException if the frame has none
     */
    private static int register(Frame frame, String name) {
        for (int i = frame.getCurrentVarCount() - 1; i >= 0; i--) {
            VarInfo info = frame.getVarInfo(i);
            if (info != null && name.equals(info.getName())) {
                return i;
            }
        }
        throw new IllegalArgumentException("There is no variable \"" + name + "\" in this frame.");
    }

    /**
     * @return the index that an element's name, such as {@code "[1]"}, gives
     *
     * @throws IllegalArgumentException if it is not an index of the array
     */
    private static long index(String name, ArrayHandle array) {
        long size = array.m_hDelegate.m_cSize;
        if (name.length() > 2 && name.startsWith("[") && name.endsWith("]")) {
            try {
                long index = Long.parseLong(name.substring(1, name.length() - 1));
                if (index >= 0 && index < size) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
                // not an index
            }
        }
        throw new IllegalArgumentException(name + " is not an element of the array, whose size is " + size + '.');
    }

    /**
     * @return the value of the assignment's expression, evaluated in the frame for a variable of the
     *         specified type
     *
     * @throws IllegalArgumentException if it cannot be evaluated, is unassigned, or is not of the type
     */
    private static ObjectHandle valueFor(Frame waitingFrame, Frame frame, Assignment assignment, TypeConstant type) {
        PureExpression expression = assignment.expression();
        ObjectHandle   value      = shown(expression.evaluate(waitingFrame, frame, type));
        if (value == null) {
            throw new IllegalArgumentException(expression.text() + " is unassigned.");
        }
        if (!value.getType().isA(type)) {
            throw new IllegalArgumentException(expression.text() + " is " + typeText(value) + ", not "
                    + type.getValueString() + ", so " + assignment.name() + " cannot take it.");
        }
        return value;
    }

    /**
     * @return the value a reference refers to, null if it is unassigned, or the value itself
     */
    static ObjectHandle shown(ObjectHandle value) {
        return value instanceof RefHandle ref ? ref.getReferent() : value;
    }

    /**
     * @return the object's fields, then a lazy variable for each of its computed properties, and one
     *         for its {@code toString()}
     */
    private List<Variable> fields(Halt current, Frame waitingFrame, GenericHandle object, boolean hex) {
        var vars = new ArrayList<Variable>();
        for (Map.Entry<String, FieldInfo> entry : DebugConsole.getFieldLayout(object.getComposition()).entrySet()) {
            vars.add(variable(current, waitingFrame, entry.getKey(), object.getField(entry.getValue().getIndex()), hex));
        }

        int thread = threadId(waitingFrame.f_fiber);
        for (PropertyInfo property : computedProperties(object)) {
            int reference = reference(current, new Lazy(thread, new Computed(object, property.getIdentity())));
            vars.add(new Variable(property.getName(), LAZY, "", reference, true));
        }
        int reference = reference(current, new Lazy(thread, new Computed(object, null)));
        vars.add(new Variable(TO_STRING, LAZY, "", reference, true));
        return vars;
    }

    /**
     * @return the object's computed properties, in the order they are declared: those that have no
     *         field, so that a getter computes their value, and that any code may read; constants
     *         and type parameters are left out
     */
    static List<PropertyInfo> computedProperties(ObjectHandle object) {
        return Arrays.stream(object.getType().ensureTypeInfo().sortedProperties())
                .map(Map.Entry::getValue)
                .filter(property -> !property.hasField() && !property.isConstant() && !property.isFormalType()
                        && property.getRefAccess() == Constants.Access.PUBLIC)
                .toList();
    }

    /**
     * @return the variable of the specified value, with a reference to its fields or elements if
     *         it has any that the waiting fiber may read
     */
    private Variable variable(Halt current, Frame waitingFrame, String name, ObjectHandle value, boolean hex) {
        ObjectHandle shown = shown(value);
        if (shown == null) {
            return new Variable(name, UNASSIGNED, "", 0);
        }

        int thread   = threadId(waitingFrame.f_fiber);
        int children = hasChildren(waitingFrame, shown) ? reference(current, new Children(thread, shown)) : 0;
        int elements = shown instanceof ArrayHandle array ? (int) Math.min(array.m_hDelegate.m_cSize, Integer.MAX_VALUE) : 0;
        return new Variable(name, valueText(shown, hex), typeText(shown), children, false, children == 0 ? 0 : elements);
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
            // an object without fields still has its toString(), and perhaps computed properties
            case GenericHandle _ -> true;
            default -> false;
        };
    }

    /**
     * @return the value in words, as {@link #valueText(ObjectHandle, boolean)} shows it, with its
     *         integers in decimal
     */
    private static String valueText(ObjectHandle value) {
        return valueText(value, false);
    }

    /**
     * @return the value in words: an integer in hexadecimal if hex is true, and an unsigned one as
     *         unsigned; an array's element type and size, as Java debuggers show arrays; else the
     *         runtime's own text of the value
     *         without the type it starts with, or the type alone for an object; and a mutable
     *         object's or array's identity code after it ({@link #identified})
     */
    private static String valueText(ObjectHandle value, boolean hex) {
        String hexText = hex ? hexText(value) : null;
        if (hexText != null) {
            return hexText;
        }
        if (value instanceof JavaLong integer && integer.getTemplate() instanceof xConstrainedInteger type
                && !type.isSigned() && integer.getValue() < 0) {
            // the runtime holds a UInt64 in a long, so that one of 2^63 or more would read as negative
            return Long.toUnsignedString(integer.getValue());
        }

        if (value instanceof ArrayHandle array) {
            TypeConstant elementType = array.getType().resolveGenericType("Element");
            return identified(value,
                    (elementType == null ? "" : elementType.getValueString()) + '[' + array.m_hDelegate.m_cSize + ']');
        }

        String text = withoutType(value.toString());
        return identified(value, text.isEmpty() ? typeText(value) : cut(text));
    }

    /**
     * @return the text, followed by the value's identity code, such as {@code #1023456}, as the
     *         console debugger's DI shows it, if the value is a mutable object or array, which
     *         another with the same fields or elements is not; an immutable value, such as a const,
     *         is told apart by its value
     */
    private static String identified(ObjectHandle value, String text) {
        boolean object = value instanceof GenericHandle || value instanceof ArrayHandle;
        return object && value.isMutable() && !(value.getComposition() instanceof ProxyComposition)
                ? text + " #" + System.identityHashCode(value)
                : text;
    }

    /**
     * @return an integer's value in hexadecimal, such as {@code 0x1F}: a negative one of a type of
     *         fixed size in its two's complement, such as {@code 0xFF} for an {@code Int8} of -1, and
     *         one of an unbounded type with its sign; null if the value is not an integer
     */
    static String hexText(ObjectHandle value) {
        return switch (value) {
            case JavaLong integer when integer.getTemplate() instanceof xConstrainedInteger type ->
                    hex(BigInteger.valueOf(integer.getValue()), type.getNumBits());
            case LongLongHandle integer -> hex(integer.getValue().toBigInteger(), 128);
            case IntNHandle integer when integer.getTemplate() instanceof xUnconstrainedInteger ->
                    hex(integer.getValue().getBigInteger(), 0);
            default -> null;
        };
    }

    /**
     * @return the value in hexadecimal: in the two's complement of the specified number of bits if
     *         it is negative, or with its sign if the number of bits is 0, for a type without a size
     */
    private static String hex(BigInteger value, int bits) {
        BigInteger shown = value.signum() < 0 && bits > 0 ? value.add(BigInteger.ONE.shiftLeft(bits)) : value;
        return (shown.signum() < 0 ? "-0x" : "0x") + shown.abs().toString(16).toUpperCase();
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
        long firstFiber = Fiber.nextId();
        if (run.computed() != null) {
            // a lazy variable's code is the value's getter or toString(), which needs no compiling
            return new Running(run, waitingFrame.f_fiber, firstFiber, current, null, new int[0], false,
                    waitingFrame.createNativeFrame(runOps, new ObjectHandle[3], Op.A_IGNORE, null),
                    new AtomicReference<>());
        }

        Frame           frame  = run.frame();
        TypeConstant    object = frame.poolContext().typeObject();
        EvalCompiler    value  = new EvalCompiler(frame, "{\nreturn " + run.expression() + ";\n}");
        MethodStructure lambda = value.createLambda(object);
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
        if (run.lambda() != null) {
            run.lambda().getParent().removeChild(run.lambda());
        }

        ObjectHandle[]              registers  = run.trampoline().f_ahVar;
        String                      expression = run.run().expression();
        CompletableFuture<Variable> answer     = run.run().answer();
        Computed                    computed   = run.run().computed();
        boolean                     toString   = computed != null && computed.property() == null;
        synchronized (this) {
            if (running == run) {
                running = null;
                updateActive();
                if (run.program() && halt == run.halt() && run.halt().frozen != null) {
                    run.halt().frozen.freezeTime();
                }
            }
            notifyAll();

            if (run.ended().get() != null) {
                // ended before its code finished, which answered it
                return;
            }
            if (registers[THROWN] instanceof ExceptionHandle thrown) {
                String what = registers[VALUE] == null || toString ? expression : expression + "'s toString()";
                fail(answer, what + " threw " + describe(thrown) + '.');
            } else if (run.statement()) {
                answer.completeAsync(() -> new Variable(expression, NO_VALUE, "", 0));
            } else if (toString) {
                // what toString() returns is a String, shown as other strings are
                String text = valueText(registers[TEXT]);
                answer.completeAsync(() -> new Variable(expression, text, "String", 0));
            } else {
                Variable shown   = variable(run.halt(), run.trampoline().f_framePrev, expression, registers[VALUE],
                        run.run().hex());
                String   hexText = run.run().hex() ? hexText(registers[VALUE]) : null;
                String   text    = hexText != null ? hexText : cut(((StringHandle) registers[TEXT]).getStringValue());
                answer.completeAsync(() -> new Variable(shown.name(), text, shown.type(), shown.variablesReference(), false,
                        shown.elements()));
            }
        }
    }

    /**
     * The first op of a run's native frame: call the compiled expression with the values of the
     * frame's variables that it reads; its value goes on the native frame's stack.
     */
    private int callExpression(Frame trampoline) {
        Running run = running;
        if (run.run().computed() instanceof Computed(ObjectHandle value, PropertyConstant property)) {
            return computeValue(trampoline, value, property);
        }

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
     * The first op of a lazy variable's run: the value of the object's computed property, through
     * its getter, goes on the native frame's stack, or the object itself for its {@code toString()};
     * a getter that fails at once ends the run with that exception.
     */
    private static int computeValue(Frame trampoline, ObjectHandle object, PropertyConstant property) {
        if (property == null) {
            trampoline.pushStack(object);
            return Op.R_NEXT;
        }

        int result = object.getTemplate().getPropertyValue(trampoline, object, property, Op.A_STACK);
        if (result == Op.R_EXCEPTION) {
            trampoline.f_ahVar[THROWN] = trampoline.clearException();
            return END_RUN;
        }
        return result;
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
     * @return true if the frame's fiber runs on during a run of the whole program: a run that lets
     *         the program run is in progress, and the fiber is not one of those that run its code
     */
    private boolean runsOn(Frame frame) {
        Running run = running;
        return run != null && run.program() && !run.includes(frame);
    }

    /**
     * Take the specified fiber out of the stop, which it leaves to run on: its stack, its frames
     * and the references that read them, and the reads and runs asked of it, which fail. Called
     * while holding this debugger's monitor.
     */
    private void forget(Halt current, int threadId) {
        current.stacks.remove(threadId);
        current.frames.values().removeIf(at -> at.thread() == threadId);
        current.localsReferences.keySet().retainAll(current.frames.keySet());
        current.targets.values().removeIf(target -> target.thread() == threadId);
        abandon(current.reads.remove(threadId));
        abandonRuns(current.runs.remove(threadId));
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
        if (!run.program() && run.fiber().getStatus() == Fiber.FiberStatus.Waiting) {
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

        // the class's own module: the file structure's is the program's, also for a library's class
        ModuleStructure module = module(clazz);
        LiteralConstant path   = clazz.getSourcePath();
        LiteralConstant dir    = module == null ? null : module.getSourceDir();
        return path == null || dir == null ? null : dir.getValue() + path.getValue();
    }

    /**
     * @return the module that holds the class, whose source directory its source path is in, or
     *         null
     */
    private static ModuleStructure module(ClassStructure clazz) {
        return clazz.getIdentityConstant().getModuleConstant().getComponent() instanceof ModuleStructure module
                ? module
                : null;
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

        /**
         * The stacks of the fibers that do not wait at a line, by thread ID, once asked for.
         */
        final Map<Integer, List<StackFrameInfo>> others = new HashMap<>();

        /**
         * The calls that {@link #stepInTargets} named, by ID, and the calls of each frame it was
         * asked about, by frame ID.
         */
        final Map<Integer, Call>             calls   = new HashMap<>();
        final Map<Integer, List<StepTarget>> callsOf = new HashMap<>();

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
     * A step that runs: how, the frame and fiber it started at, and, for a step into one of the
     * calls of its line, the index of that call's op, or {@link #NO_CALL}.
     */
    private record Stepping(Step step, Frame frame, Fiber fiber, int call) {
        static final int NO_CALL = -1;

        Stepping(Step step, Frame frame, Fiber fiber) {
            this(step, frame, fiber, NO_CALL);
        }

        /**
         * @return true if the step ends at a line of the specified frame
         */
        boolean endsAt(Frame at) {
            return switch (step) {
                case OVER -> at == frame;
                // into a call of the line: a line that code reached while the frame makes that call
                // runs, or the frame's next line, if the line does not make it
                case IN   -> call == NO_CALL
                        ? !at.isNative() && at.getFiber().isAssociated(fiber)
                        : at == frame || frame.getPC() == call && !at.isNative() && at.getFiber().isAssociated(fiber);
                case OUT  -> false;
            };
        }
    }

    /**
     * A call of a line where a fiber waits: that fiber's ID, and the index of the call's op.
     */
    private record Call(int thread, int op) {
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
     * A change of a variable that a read of the target gave, by its name, to the value of an
     * expression, which the waiting fiber of the target makes.
     */
    private record Assignment(Target target, String name, PureExpression expression) implements Target {
        @Override
        public int thread() {
            return target.thread();
        }
    }

    /**
     * The completions of the name typed at an offset of a text in a frame, which the waiting fiber
     * that holds the frame answers.
     */
    private record Completing(int thread, Frame frame, String text, int offset, CompletableFuture<Completions> answer)
            implements Target {
    }

    /**
     * A lazy variable's value, which a run computes on the waiting fiber.
     */
    private record Lazy(int thread, Computed computed) implements Target {
    }

    /**
     * What a lazy variable's run shows of a value: one of its computed properties, or its
     * {@code toString()} if the property is null.
     */
    private record Computed(ObjectHandle value, PropertyConstant property) {
    }

    /**
     * A read the host asked for, which the waiting fiber makes on its thread: what it reads, whether
     * it shows integers in hexadecimal, which children it takes, from which one and how many of
     * them, 0 for all from there, and its answer.
     */
    private record Read(Target target, boolean hex, Filter filter, int start, int count,
                        CompletableFuture<List<Variable>> future) {
    }

    /**
     * Code the host asks a waiting fiber to run: the frame it runs in, the expression, or the
     * value's property or {@code toString()} that a lazy variable shows, which the expression then
     * names, how long it may run, whether the whole program runs meanwhile, whether an integer value
     * shows in hexadecimal, and the answer.
     */
    private record Run(Frame frame, String expression, Computed computed, Duration limit, boolean program,
                       boolean hex, CompletableFuture<Variable> answer) {
    }

    /**
     * A run in progress: what the host asked for; the fiber it runs on; the ID of the first fiber
     * created after it started; the stop it runs in; its compiled expression, or null for a lazy
     * variable's run, the registers of the frame that the expression reads, and whether it is a
     * statement; the native frame it runs in; and why it was ended before its code finished, if it
     * was.
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

        /**
         * @return true if the whole program runs while the run does
         */
        boolean program() {
            return run.program();
        }
    }

    /**
     * A method's source lines, 1-based: the lines it spans and those where its code starts.
     */
    private record MethodLines(int first, int last, NavigableSet<Integer> lines) {
    }
}
