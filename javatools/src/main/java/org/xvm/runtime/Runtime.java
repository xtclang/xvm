package org.xvm.runtime;


import java.util.Queue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * The runtime.
 */
public class Runtime
    {
    public Runtime()
        {
        int parallelism = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
        if (parallelism <= 0)
            {
            parallelism = java.lang.Runtime.getRuntime().availableProcessors();
            }

        String        sName   = "Worker";
        ThreadGroup   group   = new ThreadGroup(sName);
        ThreadFactory factory = r ->
            {
            Thread thread = new Thread(group, r);
            thread.setDaemon(true);
            thread.setName(sName + "@" + thread.hashCode());
            return thread;
            };

        // TODO: replace with a fair scheduling based ExecutorService; and a concurrent blocking queue
        f_daemons = new ThreadPoolExecutor(parallelism, parallelism,
            0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
        }

    public void start()
        {
        }

    /**
     * Submit work for eventual processing by the runtime.
     *
     * @param task the task to process
     */
    void submit(Runnable task)
        {
        f_daemons.submit(task);
        m_lastSubmitNanos = System.nanoTime();
        }

    public void shutdown()
        {
        f_daemons.shutdown();
        }

    public boolean isIdle()
        {
        // TODO: very naive; replace
        return m_lastSubmitNanos < System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(10)
            && f_daemons.getActiveCount() == 0;
        }

    public boolean isDebuggerActive()
        {
        return m_fDebugger;
        }

    public void setDebuggerActive(boolean fActive)
        {
        m_fDebugger = fActive;
        }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * The executor.
     */
    public final ThreadPoolExecutor f_daemons;

    /**
     * The set of containers.
     */
    public final Queue<Container> f_containers = new ConcurrentLinkedQueue();

    /**
     * A service id producer.
     */
    protected final AtomicInteger f_idProducer = new AtomicInteger();

    /**
     * The time at which the last task was submitted.
     */
    private volatile long m_lastSubmitNanos;

    /**
     * The "debugger is active" flag.
     */
    private boolean m_fDebugger;
    }
