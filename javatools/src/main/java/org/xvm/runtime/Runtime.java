package org.xvm.runtime;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * TODO:
 */
public class Runtime
    {
    final public ThreadPoolExecutor f_daemons;

    // service id producer
    final AtomicInteger f_idProducer = new AtomicInteger();

    /**
     * The time at which the last task was submitted.
     */
    volatile long m_lastSubmitNanos;

    public Runtime()
        {
        int parallelism = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
        if (parallelism <= 0) {
            parallelism = java.lang.Runtime.getRuntime().availableProcessors();
        }

        String sName = "Worker";
        ThreadGroup group = new ThreadGroup(sName);
        ThreadFactory factory = r -> {
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
    }
