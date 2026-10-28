package org.xvm.runtime;

import java.io.IOException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Frame.VarInfo;
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
 * {@link #stackTrace}) and the variables of their frames ({@link #scopes}, {@link #variables}), and
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
 * fields or elements, like a frame's ID, lasts until the program resumes.
 *
 * <p>A breakpoint is installed by file and line: it stops every fiber that reaches the first op of
 * that line in any method compiled from that file. A file is the module's source directory and
 * the class's source path, as the compiler records them, compared as real paths.
 *
 * <p>The host's own code, in the container it names with {@link #setHost}, is not debugged. When
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
     * The longest value text {@link #variables} returns; longer text is cut off.
     */
    private static final int MAX_TEXT = 1000;

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
    private volatile Installed installed = new Installed(Map.of());

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
    }

    /**
     * A breakpoint the host requests: its ID, which the host chooses, and its 1-based line.
     */
    public record Line(int id, int line) {
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
     * next line with code in the same method.
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
        var               result    = new ArrayList<Breakpoint>(lines.size());
        for (Line request : lines) {
            int line = methods == null ? -1 : breakableLine(methods, request.line());
            if (line < 0) {
                result.add(new Breakpoint(request.id(), request.line(), false, methods == null ? NOT_IN_PROGRAM : NO_CODE));
            } else {
                idsByLine.putIfAbsent(line, request.id());
                result.add(new Breakpoint(request.id(), line, true, null));
            }
        }

        var idsByFile = new HashMap<>(installed.idsByFile());
        if (idsByLine.isEmpty()) {
            idsByFile.remove(file);
        } else {
            idsByFile.put(file, Map.copyOf(idsByLine));
        }
        installed = new Installed(Map.copyOf(idsByFile));
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
        exceptionStops = new ExceptionStops(all, uncaught);
        updateActive();
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
            Halt     current  = halt;
            Target   target   = current == null ? null : current.targets.get(reference);
            Snapshot snapshot = target == null ? null : current.stacks.get(target.thread());
            if (snapshot == null) {
                future.complete(List.of());
            } else if (snapshot.thread() == Thread.currentThread()) {
                read(current, snapshot.frame(), target, future);
            } else {
                current.reads.computeIfAbsent(target.thread(), _ -> new ArrayList<>()).add(new Read(target, future));
                notifyAll();
            }
        }
        return future;
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
        release();
    }

    @Override
    public synchronized void detach() {
        detached     = true;
        installed    = new Installed(Map.of());
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

        // assert:debug stops the program as a breakpoint does
        stop(frame, "breakpoint", "assert:debug", List.of(), null);
        return terminating ? end(frame) : pc + 1;
    }

    @Override
    public int checkBreakPoint(Frame frame, int pc) {
        if (isHost(frame)) {
            return pc + 1;
        }

        if (halt != null) {
            park(frame);
        }
        if (terminating) {
            return end(frame);
        }

        // a breakpoint on the line where a step ends, or a pause, is reported as the breakpoint
        Integer[] breaks = breaksOf(frame);
        Stepping  step   = stepping;
        Pause     pause  = pendingPause;
        if (pc < breaks.length && breaks[pc] != null) {
            stop(frame, "breakpoint", null, List.of(breaks[pc]), null);
        } else if (step != null && step.endsAt(frame) && startsLine(frame, pc)) {
            stop(frame, "step", null, List.of(), step);
        } else if (pause != null && startsLine(frame, pc) && (!pause.program() || isProgram(frame))) {
            stop(frame, pause.reason(), null, List.of(), pause);
        }
        return terminating ? end(frame) : pc + 1;
    }

    @Override
    public void onReturn(Frame frame) {
        leave(frame);
    }

    @Override
    public int checkBreakPoint(Frame frame, ExceptionHandle exception) {
        leave(frame);

        // an exception stops the program once, at the first frame of the program that throws it
        if (exception != stoppedException && !frame.isNative() && !isHost(frame) && stopsAt(exception, frame)) {
            stoppedException = exception;
            stop(frame, "exception", describe(exception), List.of(), null);
        }

        // asked again at each frame the exception leaves, so a step learns that its frame is left;
        // a fiber that is ending meanwhile ends at its next line
        return Op.R_NEXT;
    }

    // ----- stopping ------------------------------------------------------------------------------

    /**
     * @return true if the frame runs the host's own code, which this debugger leaves alone
     */
    private boolean isHost(Frame frame) {
        Container host = hostContainer;
        return host != null && frame.f_context.f_container == host;
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
        return stops.all() || stops.uncaught() && !isCaught(frame, exception);
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
    private static String describe(ExceptionHandle exception) {
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
     * @param request  the step or pause that ends here, or null for any other stop
     */
    private void stop(Frame frame, String reason, String description, List<Integer> breakpointIds, Object request) {
        Stop stop;
        Halt current;
        synchronized (this) {
            // another fiber's stop comes first; this one waits for it like any other fiber
            for (Halt other = halt; other != null && !detached; other = halt) {
                waitAt(other, frame);
                if (!waitWhile(other, frame)) {
                    return;
                }
            }
            if (detached || terminating || request != null && request != stepping && request != pendingPause) {
                return;
            }

            // any stop ends the step and the pause
            stepping     = null;
            pendingPause = null;
            current      = new Halt();
            halt         = current;
            int threadId = threadId(frame.f_fiber);
            current.stacks.put(threadId, snapshot(current, frame));
            updateActive();
            stop = new Stop(reason, description, threadId, breakpointIds);
        }

        // the program's clocks stand still while it is stopped
        Container nativeContainer = frame.f_context.f_container.getNativeContainer();
        boolean   unfreeze        = nativeContainer.freezeTime();
        try {
            listener.stopped(stop);
            synchronized (this) {
                waitWhile(current, frame);
            }
        } finally {
            if (unfreeze) {
                nativeContainer.unfreezeTime();
            }
        }
    }

    /**
     * Wait at a line while the program is stopped, as one of its fibers.
     */
    private synchronized void park(Frame frame) {
        Halt current = halt;
        if (current != null) {
            waitAt(current, frame);
            waitWhile(current, frame);
        }
    }

    /**
     * Take part in the specified stop as a fiber that waits at the specified frame. Called while
     * holding this debugger's monitor.
     */
    private void waitAt(Halt current, Frame frame) {
        current.stacks.computeIfAbsent(threadId(frame.f_fiber), _ -> snapshot(current, frame));
    }

    /**
     * Wait until the specified stop ends, reading the variables the host asks for meanwhile. Called
     * while holding this debugger's monitor.
     *
     * @return false if the thread was interrupted, which lets it run on
     */
    private boolean waitWhile(Halt current, Frame frame) {
        int threadId = threadId(frame.f_fiber);
        while (halt == current) {
            List<Read> reads = current.reads.remove(threadId);
            if (reads != null) {
                reads.forEach(read -> read(current, frame, read.target(), read.future()));
                continue;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                current.stacks.remove(threadId);
                abandon(current.reads.remove(threadId));
                return false;
            }
        }
        return true;
    }

    /**
     * Let the stopped program run on: the waiting fibers resume, and what they held is discarded.
     * Called while holding this debugger's monitor.
     */
    private void release() {
        Halt current = halt;
        if (current != null) {
            halt = null;
            current.reads.values().forEach(SessionDebugger::abandon);
            updateActive();
            notifyAll();
        }
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
     * stops, the program is stopped, a fiber steps or a pause is pending, so that every line and
     * thrown exception is checked; otherwise lines run at full speed.
     */
    private void updateActive() {
        Runtime attached = runtime;
        if (attached != null) {
            boolean stopping = halt != null || stepping != null || pendingPause != null;
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

    private static String functionName(MethodStructure function) {
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
                case Locals(int _, Frame scope)          -> locals(current, frame, scope);
                case Children(int _, ObjectHandle value) -> children(current, frame, value);
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
        var  vars     = new ArrayList<Variable>();
        long size     = array.m_hDelegate.m_cSize;
        var  template = (xArray) array.getTemplate();
        for (int i = 0, count = (int) Math.min(size, MAX_ELEMENTS); i < count; i++) {
            // extractArrayValue returns only R_NEXT or R_EXCEPTION (out of bounds), which must not
            // stay on the waiting frame
            ExceptionHandle pending = waitingFrame.m_hException;
            if (template.extractArrayValue(waitingFrame, array, i, Op.A_STACK) != Op.R_NEXT) {
                waitingFrame.m_hException = pending;
                break;
            }
            vars.add(variable(current, waitingFrame, "[" + i + "]", waitingFrame.popStack()));
        }
        if (size > MAX_ELEMENTS) {
            vars.add(new Variable("...", (size - MAX_ELEMENTS) + " more", "", 0));
        }
        return vars;
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
        ObjectHandle shown = value instanceof RefHandle ref ? ref.getReferent() : value;
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

    private static String typeText(ObjectHandle value) {
        // a deferred value has no composition yet
        return value.getComposition() == null ? "" : value.getType().getValueString();
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
     * A stop of the program, and what the host reads while it lasts: the stacks of the waiting
     * fibers, the stopping fiber first; the frames that have variables, by frame ID; the reference
     * to each frame's locals that {@link #scopes} handed out; what each reference reads; and the
     * reads each waiting fiber has yet to make, by fiber ID. Guarded by the debugger's monitor,
     * and dropped as a whole when the program resumes.
     */
    private static final class Halt {
        final Map<Integer, Snapshot>   stacks           = new LinkedHashMap<>();
        final Map<Integer, FrameAt>    frames           = new HashMap<>();
        final Map<Integer, Integer>    localsReferences = new HashMap<>();
        final Map<Integer, Target>     targets          = new HashMap<>();
        final Map<Integer, List<Read>> reads            = new HashMap<>();
    }

    /**
     * The installed breakpoints: by real path, the ID of the breakpoint on each line; and the
     * breakpoint IDs of each method seen, by op index, computed for these breakpoints.
     */
    private record Installed(Map<String, Map<Integer, Integer>> idsByFile, Map<Op[], Integer[]> idsByOps) {
        Installed(Map<String, Map<Integer, Integer>> idsByFile) {
            this(idsByFile, new ConcurrentHashMap<>());
        }
    }

    /**
     * The thrown exceptions that stop the program: every one, or those that no frame will catch.
     */
    private record ExceptionStops(boolean all, boolean uncaught) {
        static final ExceptionStops NONE = new ExceptionStops(false, false);

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
     * A read the host asked for, which the waiting fiber makes on its thread.
     */
    private record Read(Target target, CompletableFuture<List<Variable>> future) {
    }

    /**
     * A method's source lines, 1-based: the lines it spans and those where its code starts.
     */
    private record MethodLines(int first, int last, NavigableSet<Integer> lines) {
    }
}
