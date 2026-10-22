package org.xvm.runtime;

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
    }

    public void start() {
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
     * @return the debugger of this runtime's services: the one the host set, or the terminal
     *         {@link DebugConsole}
     */
    public Debugger getDebugger() {
        Debugger debugger = m_debugger;
        return debugger == null ? DebugConsole.INSTANCE : debugger;
    }

    /**
     * Set the debugger of this runtime's services; a host without a terminal for the
     * {@link DebugConsole} sets {@link Debugger#NONE}. The debugger it replaces is detached, and
     * the new one attached with the "debugger is active" flag off.
     *
     * @param debugger  the debugger to use
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
     * The debugger the host set, or null for the {@link DebugConsole}, which is created on first
     * use.
     */
    private volatile Debugger m_debugger;
}
