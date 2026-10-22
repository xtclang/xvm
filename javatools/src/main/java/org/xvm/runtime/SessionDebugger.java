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
import java.util.TreeSet;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;

import org.xvm.asm.op.Nop;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;

/**
 * A debugger that its host drives through calls rather than a terminal, for hosts such as an IDE's
 * debug adapter. The host makes the program's modules known ({@link #addModule}), sets breakpoints
 * by source file and line ({@link #setBreakpoints}), learns of each stop through its
 * {@link Listener}, reads the stopped program's threads and stacks ({@link #threads},
 * {@link #stackTrace}), and resumes it ({@link #resume}). It debugs the runtime it is set on with
 * {@link Runtime#setDebugger}; the host's own protocol is the host's concern.
 *
 * <p>A stop holds the whole runtime: the fiber that stops waits on its interpreter thread, and so
 * does every other fiber that reaches a line while the program is stopped. Each takes a snapshot
 * of its stack before it waits, so the host reads immutable copies and never a running frame. The
 * snapshots are discarded when the program resumes. Fibers keep yielding to each other as they do
 * without a debugger, since this debugger does not step.
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
     * Why and where the program stopped: the reason ("breakpoint"), what stopped it in words,
     * the fiber that stopped, and the IDs of the breakpoints it stopped at.
     */
    public record Stop(String reason, String description, int threadId, List<Integer> breakpointIds) {
    }

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
     * Resume the stopped program; does nothing while it runs.
     */
    public synchronized void resume() {
        if (m_fStopped) {
            m_fStopped = false;
            ++m_cGeneration;
            f_mapStacks.clear();
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
        resume();
    }

    @Override
    public synchronized void detach() {
        m_fDetached = true;
        m_mapBreaks = Map.of();
        m_mapCache  = new ConcurrentHashMap<>();
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
        stop(frame, "breakpoint", "assert:debug", List.of());
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

        Integer[] anBreak = breaksOf(frame);
        if (iPC < anBreak.length && anBreak[iPC] != null) {
            stop(frame, "breakpoint", null, List.of(anBreak[iPC]));
            if (m_fTerminating) {
                return end(frame);
            }
        }
        return iPC + 1;
    }

    @Override
    public void onReturn(Frame frame) {
    }

    @Override
    public int checkBreakPoint(Frame frame, ExceptionHandle hEx) {
        // exceptions are not stopped at; unwind this one without asking again
        return Op.R_EXCEPTION;
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
     * Stop the program at the specified frame, tell the listener, and wait until the host resumes
     * it. If the program is already stopped, wait for that stop to end first.
     */
    private void stop(Frame frame, String sReason, String sDescription, List<Integer> listIds) {
        Stop stop;
        long cGeneration;
        synchronized (this) {
            // another fiber's stop comes first; this one waits for it like any other fiber
            while (m_fStopped && !m_fDetached) {
                f_mapStacks.putIfAbsent(threadId(frame.f_fiber), snapshot(frame));
                if (!waitWhileStopped(frame, m_cGeneration)) {
                    return;
                }
            }
            if (m_fDetached || m_fTerminating) {
                return;
            }

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
            f_mapStacks.putIfAbsent(threadId(frame.f_fiber), snapshot(frame));
            waitWhileStopped(frame, m_cGeneration);
        }
    }

    /**
     * Wait until the specified stop ends. Called while holding this debugger's monitor.
     *
     * @param cGeneration  the stop's generation
     *
     * @return false if the thread was interrupted, which lets it run on
     */
    private boolean waitWhileStopped(Frame frame, long cGeneration) {
        while (m_fStopped && m_cGeneration == cGeneration) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                f_mapStacks.remove(threadId(frame.f_fiber));
                return false;
            }
        }
        return true;
    }

    /**
     * Turn the runtime's "debugger is active" flag on while there are breakpoints or the program
     * is stopped, so that every line is checked; otherwise lines run at full speed.
     */
    private void updateActive() {
        Runtime runtime = m_runtime;
        if (runtime != null) {
            runtime.setDebuggerActive(m_fStopped || !m_mapBreaks.isEmpty());
        }
    }

    /**
     * Take a snapshot of the stack of the fiber at the specified frame, on its own thread.
     */
    private Snapshot snapshot(Frame frame) {
        List<StackFrameInfo> listFrames = new ArrayList<>();
        for (Frame.StackFrame stackFrame : frame.getStackFrameArray()) {
            MethodStructure function = stackFrame.function;
            if (function == null || stackFrame.frame != null && stackFrame.frame.isNative()) {
                continue;
            }

            int nLine = stackFrame.iPC >= 0 ? function.calculateLineNumber(stackFrame.iPC) : 0;
            listFrames.add(new StackFrameInfo(++m_nLastFrame, functionName(function),
                    sourcePath(function), nLine));
        }
        return new Snapshot(frame.f_context.f_sName, List.copyOf(listFrames));
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

    // ----- breakpoint lookup ---------------------------------------------------------------------

    /**
     * @return the breakpoint IDs of the frame's method by op index, or an empty array if the
     *         method has none; computed once per method and set of breakpoints
     */
    private Integer[] breaksOf(Frame frame) {
        Op[]            aOp      = frame.f_aOp;
        MethodStructure function = frame.f_function;
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
     * A waiting fiber's service name and stack.
     */
    private record Snapshot(String name, List<StackFrameInfo> frames) {
    }

    /**
     * A method's source lines, 1-based: the lines it spans and those where its code starts.
     */
    private record MethodLines(int first, int last, NavigableSet<Integer> lines) {
    }

    // ----- fields --------------------------------------------------------------------------------

    private static final Integer[] NO_BREAKS = new Integer[0];

    /**
     * The host's listener.
     */
    private final Listener f_listener;

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
     * Counts the stops and resumes, so a waiting fiber knows which stop it waits for.
     */
    private long m_cGeneration;

    /**
     * The last frame ID handed out.
     */
    private int m_nLastFrame;
}
