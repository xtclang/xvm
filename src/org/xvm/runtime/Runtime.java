package org.xvm.runtime;


import java.util.concurrent.atomic.AtomicInteger;


/**
 * TODO:
 */
public class Runtime
    {
    final public DaemonPool f_daemons;

    // service id producer
    final AtomicInteger f_idProducer = new AtomicInteger();

    public Runtime()
        {
        f_daemons = new DaemonPool("Worker");
        }

    public void start()
        {
        f_daemons.start();
        }

    public void shutdown()
        {
        f_daemons.shutdown();
        }

    public boolean isIdle()
        {
        // TODO: very naive; replace
        return f_daemons == null || f_daemons.isIdle();
        }
    }
