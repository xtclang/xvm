package org.xvm.runtime;

import java.io.IOException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
public final class SessionDebugger
        implements Debugger {
    /**
     * Construct a debugger that reports to the specified listener.
     *
     * @param listener  the host's listener
     */
    public SessionDebugger(Listener listener) {
        f_listener = Objects.requireNonNull(listener);
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
     * Make the code of a module known, so that breakpoints in its source files can be verified.
     * The structures are only read; the runtime may run its own copy of the module.
     *
     * @param module  a module of the program
     */
    public synchronized void addModule(ModuleStructure module) {
        f_setModules.add(module.getName());
        index(module);
    }

    /**
     * Replace the breakpoints of one source file. A line without code of its own is moved to the
     * next line with code in the same method.
     *
     * @param sPath      the source file
     * @param listLines  the breakpoints of that file; none removes them all
     *
     * @return each breakpoint as installed, in the order requested
     */
    public synchronized List<Breakpoint> setBreakpoints(String sPath, List<Line> listLines) {
        String                sKey        = canonical(sPath);
        List<MethodLines>     listMethods = f_mapSources.get(sKey);
        Map<Integer, Integer> mapLines    = new HashMap<>();
        List<Breakpoint>      listResult  = new ArrayList<>(listLines.size());
        for (Line line : listLines) {
            int nLine = listMethods == null ? -1 : breakableLine(listMethods, line.line());
            if (nLine < 0) {
                listResult.add(new Breakpoint(line.id(), line.line(), false,
                        listMethods == null ? NOT_IN_PROGRAM : NO_CODE));
            } else {
                mapLines.putIfAbsent(nLine, line.id());
                listResult.add(new Breakpoint(line.id(), nLine, true, null));
            }
        }

        Map<String, Map<Integer, Integer>> mapBreaks = new HashMap<>(m_mapBreaks);
        if (mapLines.isEmpty()) {
            mapBreaks.remove(sKey);
        } else {
            mapBreaks.put(sKey, Map.copyOf(mapLines));
        }
        m_mapBreaks = Map.copyOf(mapBreaks);
        m_mapCache  = new ConcurrentHashMap<>();
        updateActive();
        return listResult;
    }

    /**
     * Choose which thrown exceptions stop the program, at the frame that throws them.
     *
     * @param fAll       true to stop at every thrown exception
     * @param fUncaught  true to stop at the exceptions that no frame will catch
     */
    public synchronized void setExceptionStops(boolean fAll, boolean fUncaught) {
        m_fStopAllThrown = fAll;
        m_fStopUncaught  = fUncaught;
        updateActive();
    }

    /**
     * @return true while the program is stopped
     */
    public boolean isStopped() {
        return m_fStopped;
    }

    /**
     * @return the fibers of the stopped program that wait: the one that stopped first, then those
     *         that reached a line since; none while the program runs
     */
    public synchronized List<ThreadInfo> threads() {
        List<ThreadInfo> list = new ArrayList<>(f_mapStacks.size());
        f_mapStacks.forEach((nThread, snapshot) -> list.add(new ThreadInfo(nThread, snapshot.name())));
        return list;
    }

    /**
     * @param nThread  the ID of a fiber that {@link #threads} returned
     *
     * @return the fiber's stack, innermost frame first; empty if the fiber is not waiting
     */
    public synchronized List<StackFrameInfo> stackTrace(int nThread) {
        Snapshot snapshot = f_mapStacks.get(nThread);
        return snapshot == null ? List.of() : snapshot.frames();
    }

    /**
     * @param nFrame  the ID of a frame that {@link #stackTrace} returned
     *
     * @return the frame's scopes, which are its local variables; none if the frame is not one of
     *         this stop's, or its fiber called it from another fiber's frame
     */
    public synchronized List<Scope> scopes(int nFrame) {
        FrameAt at = f_mapFrames.get(nFrame);
        return at == null
                ? List.of()
                : List.of(new Scope(LOCALS, f_mapLocals.computeIfAbsent(nFrame,
                        _ -> reference(new Target(at.thread(), at.frame(), null)))));
    }

    /**
     * Read the variables of a scope, or the fields or elements of a value. The waiting fiber that
     * holds them reads them on its own thread; the listener may ask from there too. The answer
     * comes on another thread, never while this debugger holds its lock.
     *
     * @param nReference  a reference that {@link #scopes} or an earlier read returned
     *
     * @return the variables, in order: a frame's {@code this} and its named registers, a value's
     *         fields, or an array's elements; none if the reference is not one of this stop's, or
     *         the program resumes first. Fails if a value cannot be read.
     */
    public CompletableFuture<List<Variable>> variables(int nReference) {
        CompletableFuture<List<Variable>> future = new CompletableFuture<>();
        synchronized (this) {
            Target   target   = f_mapTargets.get(nReference);
            Snapshot snapshot = target == null ? null : f_mapStacks.get(target.thread());
            if (snapshot == null) {
                future.complete(List.of());
            } else if (snapshot.thread() == Thread.currentThread()) {
                read(snapshot.frame(), target, future);
            } else {
                f_mapReads.computeIfAbsent(target.thread(), _ -> new ArrayList<>())
                        .add(new Read(target, future));
                notifyAll();
            }
        }
        return future;
    }

    /**
     * Resume the stopped program, stepping the specified fiber: the program stops again, with the
     * reason "step", at the line where the step ends.
     *
     * @param nThread  the ID of a fiber that {@link #threads} returned
     * @param step     how to step it
     *
     * @return false if the program runs, or the fiber does not wait; then nothing changes
     */
    public synchronized boolean step(int nThread, Step step) {
        Snapshot snapshot = m_fStopped ? f_mapStacks.get(nThread) : null;
        if (snapshot == null) {
            return false;
        }

        // set first, so lines stay checked as the fibers resume
        Frame frame = snapshot.frame();
        m_stepping = new Stepping(Objects.requireNonNull(step), frame, frame.f_fiber);
        release();
        return true;
    }

    /**
     * Stop the program at the next line any of its fibers reaches, with the reason "pause"; does
     * nothing while it is stopped.
     */
    public synchronized void pause() {
        if (!m_fStopped) {
            m_pause = new Pause("pause", false);
            updateActive();
        }
    }

    /**
     * Stop the program at its first line, with the reason "entry"; called before it starts, and
     * after the program's modules are made known.
     */
    public synchronized void stopOnEntry() {
        m_pause = new Pause("entry", true);
        updateActive();
    }

    /**
     * Resume the stopped program; does nothing while it runs.
     */
    public synchronized void resume() {
        release();
    }

    /**
     * Let the stopped program run on: the waiting fibers resume, and what they held is discarded.
     */
    private void release() {
        if (m_fStopped) {
            m_fStopped = false;
            ++m_cGeneration;
            f_mapStacks.clear();
            f_mapFrames.clear();
            f_mapLocals.clear();
            f_mapTargets.clear();
            f_mapReads.values().forEach(SessionDebugger::abandon);
            f_mapReads.clear();
            updateActive();
            notifyAll();
        }
    }

    // ----- Debugger interface --------------------------------------------------------------------

    @Override
    public synchronized void attach(Runtime runtime) {
        m_runtime = runtime;
        updateActive();
    }

    @Override
    public void setHost(Container container) {
        m_containerHost = container;
    }

    @Override
    public synchronized void terminate() {
        m_fTerminating = true;
        m_stepping     = null;
        m_pause        = null;
        resume();
    }

    @Override
    public synchronized void detach() {
        m_fDetached = true;
        m_mapBreaks = Map.of();
        m_mapCache  = new ConcurrentHashMap<>();
        m_stepping  = null;
        m_pause     = null;
        resume();
        m_runtime = null;
    }

    @Override
    public boolean holdsFibers() {
        return false;
    }

    @Override
    public int activate(Frame frame, int iPC) {
        if (isHost(frame)) {
            return iPC + 1;
        }

        // assert:debug stops the program as a breakpoint does
        stop(frame, "breakpoint", "assert:debug", List.of(), null);
        return m_fTerminating ? end(frame) : iPC + 1;
    }

    @Override
    public int checkBreakPoint(Frame frame, int iPC) {
        if (isHost(frame)) {
            return iPC + 1;
        }

        if (m_fStopped) {
            park(frame);
        }
        if (m_fTerminating) {
            return end(frame);
        }

        // a breakpoint on the line where a step ends, or a pause, is reported as the breakpoint
        Integer[] anBreak  = breaksOf(frame);
        Stepping  stepping = m_stepping;
        Pause     pause    = m_pause;
        if (iPC < anBreak.length && anBreak[iPC] != null) {
            stop(frame, "breakpoint", null, List.of(anBreak[iPC]), null);
        } else if (stepping != null && stepping.endsAt(frame) && startsLine(frame, iPC)) {
            stop(frame, "step", null, List.of(), stepping);
        } else if (pause != null && startsLine(frame, iPC)
                && (!pause.program() || isProgram(frame))) {
            stop(frame, pause.reason(), null, List.of(), pause);
        }
        return m_fTerminating ? end(frame) : iPC + 1;
    }

    @Override
    public void onReturn(Frame frame) {
        leave(frame);
    }

    @Override
    public int checkBreakPoint(Frame frame, ExceptionHandle hEx) {
        leave(frame);

        // an exception stops the program once, at the first frame of the program that throws it
        if (hEx != m_hExceptionStopped && !frame.isNative() && !isHost(frame)
                && (m_fStopAllThrown || m_fStopUncaught && !isCaught(frame, hEx))) {
            m_hExceptionStopped = hEx;
            stop(frame, "exception", describe(hEx), List.of(), null);
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
        Container containerHost = m_containerHost;
        return containerHost != null && frame.f_context.f_container == containerHost;
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
        Stepping stepping = m_stepping;
        if (stepping != null && stepping.frame() == frame && stepping.step() != Step.IN) {
            m_stepping = new Stepping(Step.IN, frame, frame.f_fiber);
        }
    }

    /**
     * @return true if a frame of the fiber at the specified frame, or of a fiber that called it,
     *         will catch the exception
     */
    private static boolean isCaught(Frame frame, ExceptionHandle hEx) {
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            Frame frameAt = stackFrame.frame;
            if (frameAt != null && !frameAt.isNative() && frameAt.catches(hEx)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the exception's type and its text, if it has one
     */
    private static String describe(ExceptionHandle hEx) {
        String       sType = hEx.getType().getValueString();
        ObjectHandle hText = hEx.getField(null, "text");
        return hText instanceof StringHandle hString
                ? sType + ": " + hString.getStringValue()
                : sType;
    }

    /**
     * @return true if the frame runs code of a module the host made known, or if none is known
     */
    private synchronized boolean isProgram(Frame frame) {
        MethodStructure function = frame.getFunction();
        ClassStructure  clz      = function == null ? null : function.getContainingClass();
        return f_setModules.isEmpty()
                || clz != null
                        && f_setModules.contains(clz.getFileStructure().getModule().getName());
    }

    /**
     * @return true if the op at the specified index starts a line, as a step ends at
     */
    private static boolean startsLine(Frame frame, int iPC) {
        Op[] aOp = frame.f_aOp;
        return aOp != null && iPC < aOp.length
                && aOp[iPC] instanceof Nop nop && nop.getLineCount() != 0;
    }

    /**
     * Stop the program at the specified frame, tell the listener, and wait until the host resumes
     * it. If the program is already stopped, wait for that stop to end first; the stop a step or a
     * pause asked for is then not made, unless that step or pause is still pending.
     *
     * @param request  the step or pause that ends here, or null for any other stop
     */
    private void stop(Frame frame, String sReason, String sDescription, List<Integer> listIds,
                      Object request) {
        Stop stop;
        long cGeneration;
        synchronized (this) {
            // another fiber's stop comes first; this one waits for it like any other fiber
            while (m_fStopped && !m_fDetached) {
                f_mapStacks.computeIfAbsent(threadId(frame.f_fiber), _ -> snapshot(frame));
                if (!waitWhileStopped(frame, m_cGeneration)) {
                    return;
                }
            }
            if (m_fDetached || m_fTerminating
                    || request != null && request != m_stepping && request != m_pause) {
                return;
            }

            // any stop ends the step and the pause
            m_stepping  = null;
            m_pause     = null;
            m_fStopped  = true;
            cGeneration = ++m_cGeneration;
            f_mapStacks.clear();
            int nThread = threadId(frame.f_fiber);
            f_mapStacks.put(nThread, snapshot(frame));
            updateActive();
            stop = new Stop(sReason, sDescription, nThread, listIds);
        }

        // the program's clocks stand still while it is stopped
        Container containerNative = frame.f_context.f_container.getNativeContainer();
        boolean   fUnfreeze       = containerNative.freezeTime();
        try {
            f_listener.stopped(stop);
            synchronized (this) {
                waitWhileStopped(frame, cGeneration);
            }
        } finally {
            if (fUnfreeze) {
                containerNative.unfreezeTime();
            }
        }
    }

    /**
     * Wait at a line while the program is stopped, as one of its fibers.
     */
    private synchronized void park(Frame frame) {
        if (m_fStopped) {
            f_mapStacks.computeIfAbsent(threadId(frame.f_fiber), _ -> snapshot(frame));
            waitWhileStopped(frame, m_cGeneration);
        }
    }

    /**
     * Wait until the specified stop ends, reading the variables the host asks for meanwhile. Called
     * while holding this debugger's monitor.
     *
     * @param cGeneration  the stop's generation
     *
     * @return false if the thread was interrupted, which lets it run on
     */
    private boolean waitWhileStopped(Frame frame, long cGeneration) {
        int nThread = threadId(frame.f_fiber);
        while (m_fStopped && m_cGeneration == cGeneration) {
            List<Read> listReads = f_mapReads.remove(nThread);
            if (listReads != null) {
                listReads.forEach(read -> read(frame, read.target(), read.future()));
                continue;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                f_mapStacks.remove(nThread);
                abandon(f_mapReads.remove(nThread));
                return false;
            }
        }
        return true;
    }

    /**
     * Turn the runtime's "debugger is active" flag on while there are breakpoints or exception
     * stops, the program is stopped, a fiber steps or a pause is pending, so that every line and
     * thrown exception is checked; otherwise lines run at full speed.
     */
    private void updateActive() {
        Runtime runtime = m_runtime;
        if (runtime != null) {
            runtime.setDebuggerActive(m_fStopped || m_stepping != null || m_pause != null
                    || !m_mapBreaks.isEmpty() || m_fStopAllThrown || m_fStopUncaught);
        }
    }

    /**
     * Take a snapshot of the stack of the fiber at the specified frame, on its own thread, and
     * remember the frames of that fiber, whose variables it reads while it waits.
     */
    private Snapshot snapshot(Frame frame) {
        int                  nThread    = threadId(frame.f_fiber);
        List<StackFrameInfo> listFrames = new ArrayList<>();
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            MethodStructure function = stackFrame.function;
            if (function == null || stackFrame.frame != null && stackFrame.frame.isNative()) {
                continue;
            }

            int nFrame = ++m_nLastFrame;
            int nLine  = stackFrame.iPC >= 0 ? function.calculateLineNumber(stackFrame.iPC) : 0;
            listFrames.add(new StackFrameInfo(nFrame, functionName(function),
                    sourcePath(function), nLine));
            if (stackFrame.frame != null && stackFrame.frame.f_fiber == frame.f_fiber) {
                f_mapFrames.put(nFrame, new FrameAt(nThread, stackFrame.frame));
            }
        }
        return new Snapshot(frame.f_context.f_sName, List.copyOf(listFrames),
                Thread.currentThread(), frame);
    }

    private static int threadId(Fiber fiber) {
        return (int) fiber.getId();
    }

    private static String functionName(MethodStructure function) {
        ClassStructure clz   = function.getContainingClass();
        String         sName = clz instanceof ModuleStructure module ? module.getSimpleName()
                             : clz == null                           ? null
                                                                     : clz.getName();
        return sName == null ? function.getName() : sName + '.' + function.getName();
    }

    // ----- variables -----------------------------------------------------------------------------

    /**
     * Read the target's variables on the thread of the fiber waiting at the specified frame, whose
     * stack holds the array elements it reads, and answer the read on another thread, since the
     * host's actions on the answer must not run while this debugger's monitor is held. Called
     * while holding this debugger's monitor.
     */
    private void read(Frame frame, Target target, CompletableFuture<List<Variable>> future) {
        List<Variable> listVars;
        try {
            listVars = target.value() == null
                    ? locals(frame, target.frame())
                    : children(frame, target.value());
        } catch (RuntimeException e) {
            // a value the debugger cannot read fails the read, not the program
            future.completeAsync(() -> {
                throw e;
            });
            return;
        }
        future.completeAsync(() -> listVars);
    }

    /**
     * @return the exception the frame is throwing, if any, then its {@code this} and its named
     *         registers in scope
     */
    private List<Variable> locals(Frame frameWait, Frame frame) {
        List<Variable>  listVars   = new ArrayList<>();
        ExceptionHandle hException = frame.m_hException;
        if (hException != null) {
            listVars.add(variable(frameWait, EXCEPTION, hException));
        }

        ObjectHandle hThis = frame.f_hThis;
        if (hThis != null) {
            listVars.add(variable(frameWait, "this", hThis));
        }

        for (int i = 0, c = frame.getCurrentVarCount(); i < c; i++) {
            VarInfo info  = frame.getVarInfo(i);
            String  sName = info == null ? "" : info.getName();
            if (!sName.isEmpty()) {
                listVars.add(variable(frameWait, sName, frame.f_ahVar[i]));
            }
        }
        return listVars;
    }

    /**
     * @return an array's elements, at most {@link #MAX_ELEMENTS} of them, or an object's fields
     */
    private List<Variable> children(Frame frameWait, ObjectHandle hValue) {
        List<Variable> listVars = new ArrayList<>();
        if (hValue instanceof ArrayHandle hArray) {
            long   cSize    = hArray.m_hDelegate.m_cSize;
            xArray template = (xArray) hArray.getTemplate();
            for (int i = 0, c = (int) Math.min(cSize, MAX_ELEMENTS); i < c; i++) {
                // extractArrayValue returns only R_NEXT or R_EXCEPTION (out of bounds), which
                // must not stay on the waiting frame
                ExceptionHandle hExPrev = frameWait.m_hException;
                if (template.extractArrayValue(frameWait, hArray, i, Op.A_STACK) != Op.R_NEXT) {
                    frameWait.m_hException = hExPrev;
                    break;
                }
                listVars.add(variable(frameWait, "[" + i + "]", frameWait.popStack()));
            }
            if (cSize > MAX_ELEMENTS) {
                listVars.add(new Variable("...", (cSize - MAX_ELEMENTS) + " more", "", 0));
            }
        } else if (hValue instanceof GenericHandle hGeneric) {
            for (Map.Entry<String, FieldInfo> entry :
                    DebugConsole.getFieldLayout(hGeneric.getComposition()).entrySet()) {
                listVars.add(variable(frameWait, entry.getKey(),
                        hGeneric.getField(entry.getValue().getIndex())));
            }
        }
        return listVars;
    }

    /**
     * @return the variable of the specified value, with a reference to its fields or elements if
     *         it has any that the waiting fiber may read
     */
    private Variable variable(Frame frameWait, String sName, ObjectHandle hValue) {
        if (hValue instanceof RefHandle hRef) {
            hValue = hRef.getReferent();
        }
        if (hValue == null) {
            return new Variable(sName, UNASSIGNED, "", 0);
        }

        int nReference = hasChildren(frameWait, hValue)
                ? reference(new Target(threadId(frameWait.f_fiber), null, hValue))
                : 0;
        return new Variable(sName, valueText(hValue), typeText(hValue), nReference);
    }

    /**
     * @return true if the value has fields or elements to show that belong to the waiting fiber's
     *         service or are immutable; another service's state is read only on its own thread
     */
    private static boolean hasChildren(Frame frameWait, ObjectHandle hValue) {
        TypeComposition composition = hValue.getComposition();
        if (composition == null || composition instanceof ProxyComposition
                || hValue instanceof EnumHandle
                || hValue instanceof ServiceHandle hService
                        && hService.f_context != frameWait.f_context) {
            return false;
        }

        return hValue instanceof ArrayHandle hArray
                ? hArray.m_hDelegate.m_cSize > 0
                : hValue instanceof GenericHandle
                        && !DebugConsole.getFieldLayout(composition).isEmpty();
    }

    /**
     * @return the value in words: an array's element type and size, as Java debuggers show
     *         arrays, else the runtime's own text of the value without the type it starts with,
     *         or the type alone for an object
     */
    private static String valueText(ObjectHandle hValue) {
        if (hValue instanceof ArrayHandle hArray) {
            TypeConstant typeElement = hArray.getType().resolveGenericType("Element");
            return (typeElement == null ? "" : typeElement.getValueString())
                    + '[' + hArray.m_hDelegate.m_cSize + ']';
        }

        // ObjectHandle.toString() starts with the type in parentheses, followed by a space
        String sText = hValue.toString();
        if (sText.startsWith("(")) {
            for (int i = 0, cDepth = 0, c = sText.length(); i < c; i++) {
                char ch = sText.charAt(i);
                if (ch == '(') {
                    ++cDepth;
                } else if (ch == ')' && --cDepth == 0) {
                    sText = sText.substring(sText.startsWith(" ", i + 1) ? i + 2 : i + 1);
                    break;
                }
            }
        }
        if (sText.isEmpty()) {
            return typeText(hValue);
        }
        return sText.length() > MAX_TEXT ? sText.substring(0, MAX_TEXT) + "..." : sText;
    }

    private static String typeText(ObjectHandle hValue) {
        // a deferred value has no composition yet
        return hValue.getComposition() == null ? "" : hValue.getType().getValueString();
    }

    /**
     * @return a new reference to the specified target, which lasts until the program resumes
     */
    private int reference(Target target) {
        int nReference = ++m_nLastReference;
        f_mapTargets.put(nReference, target);
        return nReference;
    }

    /**
     * Answer reads that will not be made, because their fiber no longer waits, with no variables,
     * on another thread.
     */
    private static void abandon(List<Read> listReads) {
        if (listReads != null) {
            listReads.forEach(read -> read.future().completeAsync(List::of));
        }
    }

    // ----- breakpoint lookup ---------------------------------------------------------------------

    /**
     * @return the breakpoint IDs of the frame's method by op index, or an empty array if the
     *         method has none; computed once per method and set of breakpoints
     */
    private Integer[] breaksOf(Frame frame) {
        Op[]            aOp      = frame.f_aOp;
        MethodStructure function = frame.getFunction();
        if (aOp == null || function == null) {
            return NO_BREAKS;
        }

        // the op array identifies the method: structures compare by their identity constants
        Map<Op[], Integer[]> mapCache = m_mapCache;
        Integer[]            anBreak  = mapCache.get(aOp);
        if (anBreak == null) {
            anBreak = computeBreaks(function, aOp);
            mapCache.putIfAbsent(aOp, anBreak);
        }
        return anBreak;
    }

    private Integer[] computeBreaks(MethodStructure function, Op[] aOp) {
        Map<String, Map<Integer, Integer>> mapBreaks = m_mapBreaks;
        if (mapBreaks.isEmpty()) {
            return NO_BREAKS;
        }

        String                sPath    = sourcePath(function);
        Map<Integer, Integer> mapLines = sPath == null ? null : mapBreaks.get(canonical(sPath));
        if (mapLines == null) {
            return NO_BREAKS;
        }

        // a line starts at the Nop after which calculateLineNumber() reports it
        Integer[] anBreak = null;
        int       nLine   = 1;
        for (int i = 0, c = aOp.length; i < c; i++) {
            if (aOp[i].ensureOp() instanceof Nop nop) {
                nLine += nop.getLineCount();
                Integer nId = mapLines.get(nLine);
                if (nId != null) {
                    if (anBreak == null) {
                        anBreak = new Integer[c];
                    }
                    anBreak[i] = nId;
                }
            }
        }
        return anBreak == null ? NO_BREAKS : anBreak;
    }

    // ----- source index --------------------------------------------------------------------------

    private void index(Component component) {
        if (component instanceof MethodStructure method && method.getOps() != null) {
            String sPath = sourcePath(method);
            if (sPath != null) {
                NavigableSet<Integer> setLines = new TreeSet<>();
                int                   nLine    = 1;
                for (Op op : method.getOps()) {
                    if (op.ensureOp() instanceof Nop nop) {
                        nLine += nop.getLineCount();
                        setLines.add(nLine);
                    }
                }
                int nFirst = method.getSourceLineNumber() + 1;
                f_mapSources.computeIfAbsent(canonical(sPath), _ -> new ArrayList<>())
                        .add(new MethodLines(nFirst, nFirst + method.getSourceLineCount() - 1, setLines));
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
    private static int breakableLine(List<MethodLines> listMethods, int nLine) {
        int nBest = -1;
        for (MethodLines method : listMethods) {
            if (method.lines().contains(nLine)) {
                return nLine;
            }
            if (method.first() <= nLine && nLine <= method.last()) {
                Integer nNext = method.lines().ceiling(nLine);
                if (nNext != null && (nBest < 0 || nNext < nBest)) {
                    nBest = nNext;
                }
            }
        }
        return nBest;
    }

    /**
     * @return the source file of the method, as the compiler recorded it, or null
     */
    private static String sourcePath(MethodStructure function) {
        ClassStructure clz = function.getContainingClass();
        if (clz == null) {
            return null;
        }

        LiteralConstant constPath = clz.getSourcePath();
        LiteralConstant constDir  = clz.getFileStructure().getModule().getSourceDir();
        return constPath == null || constDir == null
                ? null
                : constDir.getValue() + constPath.getValue();
    }

    /**
     * @return the real path of the specified file, or its normalized absolute path if it does not
     *         exist
     */
    private String canonical(String sPath) {
        return f_mapCanonical.computeIfAbsent(sPath, s -> {
            try {
                Path path = Path.of(s).toAbsolutePath().normalize();
                try {
                    return path.toRealPath().toString();
                } catch (IOException | SecurityException e) {
                    return path.toString();
                }
            } catch (InvalidPathException e) {
                return s;
            }
        });
    }

    // ----- inner records -------------------------------------------------------------------------

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
        boolean endsAt(Frame frameAt) {
            return switch (step) {
                case OVER -> frameAt == frame;
                case IN   -> !frameAt.isNative() && frameAt.f_fiber.isAssociated(fiber);
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
     * What a reference reads, on the thread of the fiber with the specified ID: the frame's locals
     * when there is no value, else the value's fields or elements.
     */
    private record Target(int thread, Frame frame, ObjectHandle value) {
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

    // ----- fields --------------------------------------------------------------------------------

    private static final Integer[] NO_BREAKS = new Integer[0];

    /**
     * The longest value text {@link #variables} returns; longer text is cut off.
     */
    private static final int MAX_TEXT = 1000;

    /**
     * The host's listener.
     */
    private final Listener f_listener;

    /**
     * The names of the known modules.
     */
    private final Set<String> f_setModules = new HashSet<>();

    /**
     * The methods compiled from each source file, by real path, from the known modules.
     */
    private final Map<String, List<MethodLines>> f_mapSources = new HashMap<>();

    /**
     * The real paths of the files looked up, by path.
     */
    private final Map<String, String> f_mapCanonical = new ConcurrentHashMap<>();

    /**
     * The stacks of the waiting fibers while the program is stopped, the stopping fiber first.
     */
    private final Map<Integer, Snapshot> f_mapStacks = new LinkedHashMap<>();

    /**
     * The frames of the waiting fibers that have variables, by frame ID, while the program is
     * stopped.
     */
    private final Map<Integer, FrameAt> f_mapFrames = new HashMap<>();

    /**
     * The reference to each frame's locals that {@link #scopes} handed out, by frame ID.
     */
    private final Map<Integer, Integer> f_mapLocals = new HashMap<>();

    /**
     * What each reference handed out while the program is stopped reads.
     */
    private final Map<Integer, Target> f_mapTargets = new HashMap<>();

    /**
     * The reads that each waiting fiber has yet to make, by fiber ID.
     */
    private final Map<Integer, List<Read>> f_mapReads = new HashMap<>();

    /**
     * The installed breakpoints: by real path, the ID of the breakpoint on each line.
     */
    private volatile Map<String, Map<Integer, Integer>> m_mapBreaks = Map.of();

    /**
     * The breakpoint IDs of each method seen, by op index; replaced when the breakpoints change.
     */
    private volatile Map<Op[], Integer[]> m_mapCache = new ConcurrentHashMap<>();

    /**
     * The runtime this debugger is attached to, or null.
     */
    private volatile Runtime m_runtime;

    /**
     * True while the program is stopped.
     */
    private volatile boolean m_fStopped;

    /**
     * True once another debugger has replaced this one.
     */
    private volatile boolean m_fDetached;

    /**
     * True once the program is being stopped: its fibers end at their next line.
     */
    private volatile boolean m_fTerminating;

    /**
     * The container of the host's own code, which this debugger leaves alone, or null.
     */
    private volatile Container m_containerHost;

    /**
     * The step that runs, or null.
     */
    private volatile Stepping m_stepping;

    /**
     * The pause that is pending, or null.
     */
    private volatile Pause m_pause;

    /**
     * True to stop at every thrown exception.
     */
    private volatile boolean m_fStopAllThrown;

    /**
     * True to stop at the thrown exceptions that no frame will catch.
     */
    private volatile boolean m_fStopUncaught;

    /**
     * The last exception the program stopped at, which does not stop it again as it unwinds.
     */
    private volatile ExceptionHandle m_hExceptionStopped;

    /**
     * Counts the stops and resumes, so a waiting fiber knows which stop it waits for.
     */
    private long m_cGeneration;

    /**
     * The last frame ID handed out.
     */
    private int m_nLastFrame;

    /**
     * The last variables reference handed out.
     */
    private int m_nLastReference;
}
