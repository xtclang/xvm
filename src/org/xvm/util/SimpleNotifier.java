package org.xvm.util;

/**
 * A trivial Notifier implementation.
 *
 * @author gg 2017.03.31
 */
public class SimpleNotifier
        implements Notifier
    {
    @Override
    public void await(long cMillis)
            throws InterruptedException
        {
        synchronized (this)
            {
            wait(cMillis);
            }
        }

    @Override
    public void signal()
        {
        synchronized (this)
            {
            notify();
            }
        }
    }
