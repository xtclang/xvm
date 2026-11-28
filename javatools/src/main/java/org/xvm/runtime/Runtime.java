package org.xvm.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicLong;

import java.util.function.Consumer;

import org.xvm.asm.ConstantPool;

import org.xvm.util.concurrent.ConcurrentLinkedBlockingQueue;

/**
 * The runtime.
 */
public class Runtime {
    public Runtime() {
        int parallelism = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
        if (parallelism <= 0) {
            parallelism = java.lang.Runtime.getRuntime().availableProcessors();
        }

        ThreadGroup groupXVM = new ThreadGroup("XVM");
        ThreadFactory factoryXVM = r -> {
            Thread thread = new Thread(groupXVM, r);
            thread.setDaemon(true);
            thread.setName("XvmWorker@" + thread.hashCode());
            return thread;
        };

        // TODO: replace with a fair scheduling based ExecutorService
        f_executorXVM = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.SECONDS,
                new ConcurrentLinkedBlockingQueue<>(), factoryXVM);

        ThreadFactory factoryIO = Thread.ofVirtual()
                .name("IOWorker@", 0)
                .factory();
        f_executorIO = Executors.newThreadPerTaskExecutor(factoryIO);

        // the debugger can be turned off by a system property, unless a host sets one, for example:
        //      JAVA_OPTS=-Dorg.xvm.runtime.debugger=off xec TestSimple
        // the console debugger is not made here: it reads the user's preferences when it is made
        if ("off".equals(System.getProperty("org.xvm.runtime.debugger"))) {
            m_debugger = Debugger.NONE;
        }
    }

    public void start() {
    }

    /**
     * Report a failure of the runtime itself, one that no program caused, such as an exception that
     * ends a service's shutdown: to {@link #setFailureReporter the failure reporter}, which is
     * System.err unless an embedder sets one.
     *
     * @param sMessage  what failed
     * @param e         the failure
     */
    public static void reportFailure(String sMessage, Throwable e) {
        StringWriter trace = new StringWriter();
        try (PrintWriter writer = new PrintWriter(trace)) {
            writer.println(sMessage);
            e.printStackTrace(writer);
        }
        s_failureReporter.accept(trace.toString());
    }

    /**
     * Send the runtime's own failures, each as its message and stack trace, to the specified
     * reporter instead of System.err. An embedder whose System.err is a program's output, as a run
     * worker's is, sends them where its own failures go: that output can be dropped, as a stopped
     * run's is, and these failures are not the program's.
     *
     * @param reporter  the reporter
     *
     * @return the reporter before this one
     */
    public static Consumer<String> setFailureReporter(Consumer<String> reporter) {
        Consumer<String> prior = s_failureReporter;
        s_failureReporter = Objects.requireNonNull(reporter);
        return prior;
    }

    /**
     * Register the specified container (used only for debugging)
     */
    public void registerContainer(Container container) {
        synchronized (f_containers) {
            f_containers.putIfAbsent(container, null);
        }
    }

    /**
     * @return a set of Container objects (used only for debugging)
     */
    public Set<Container> containers() {
        synchronized (f_containers) {
            return new HashSet<>(f_containers.keySet());
        }
    }

    /**
     * @return a container that uses the specified ConstantPool; null if not found
     */
    public Container findContainer(ConstantPool pool) {
        for (Container container : f_containers.keySet()) {
            if (container.getConstantPool() == pool) {
                return container;
            }
        }
        return null;
    }

    /**
     * Submit ServiceContext work for eventual processing by the runtime.
     *
     * @param task the task to process
     */
    protected void submitService(Runnable task) {
        f_executorXVM.submit(task);
        m_lastXvmSubmitNanos = System.nanoTime();
    }

    /**
     * Submit IO work for eventual processing by the runtime.
     *
     * @param task the task to process
     */
    protected void submitIO(Runnable task) {
        f_executorIO.submit(task);
    }

    /**
     * @return a unique id
     */
    public long makeUniqueId() {
        return f_idProducer.getAndIncrement();
    }

    public void shutdownXVM() {
        f_executorIO .shutdown();
        f_executorXVM.shutdown();
    }

    public boolean isIdle() {
        // TODO: very naive; replace
        return m_lastXvmSubmitNanos < System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(10)
            && f_executorXVM.getActiveCount() == 0;
    }

    public boolean isDebuggerActive() {
        return m_fDebugger;
    }

    public void setDebuggerActive(boolean fActive) {
        m_fDebugger = fActive;
    }

    /**
     * @return the debugger of this runtime's services; the console debugger if none was set
     */
    public Debugger getDebugger() {
        Debugger debugger = m_debugger;
        if (debugger == null) {
            m_debugger = debugger = DebugConsole.INSTANCE;
        }
        return debugger;
    }

    /**
     * Set the debugger of this runtime's services.
     */
    public void setDebugger(Debugger debugger) {
        Objects.requireNonNull(debugger);
        Debugger previous = m_debugger;
        m_debugger = debugger;
        if (previous != null && previous != debugger) {
            previous.detach();
        }
        m_fDebugger = false;
        debugger.attach(this);
    }

    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Where {@link #reportFailure} sends the runtime's own failures.
     */
    private static volatile Consumer<String> s_failureReporter = text -> System.err.print(text);

    /**
     * The executor for XVM services.
     */
    public final ThreadPoolExecutor f_executorXVM;

    /**
     * The executor for IO services.
     */
    public final ExecutorService f_executorIO;

    /**
     * The set of containers (stored as a Map with no values); used only for debugging.
     */
    private final Map<Container, Object> f_containers = new WeakHashMap<>();

    /**
     * A unique id producer.
     */
    protected final AtomicLong f_idProducer = new AtomicLong();

    /**
     * The time at which the last service task was submitted.
     */
    private volatile long m_lastXvmSubmitNanos;

    /**
     * The "debugger is active" flag; a host's debugger sets it from the host's own threads.
     */
    private volatile boolean m_fDebugger;

    /**
     * The debugger instance; null until it is set explicitly or lazily assigned.
     */
    private volatile Debugger m_debugger;
}
