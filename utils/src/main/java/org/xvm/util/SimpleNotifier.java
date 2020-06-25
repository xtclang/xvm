package org.xvm.util;

/**
 * A trivial Notifier implementation.
 */
public class SimpleNotifier
        implements Notifier
    {
    private boolean m_fSignaled;

    @Override
    public void await(long cMillis)
            throws InterruptedException
        {
        synchronized (this)
            {
            if (m_fSignaled)
                {
                m_fSignaled = false;
                }
            else
                {
                wait(cMillis);

                if (m_fSignaled)
                    {
                    m_fSignaled = false;
                    }
                }
            }
        }

    @Override
    public void signal()
        {
        synchronized (this)
            {
            m_fSignaled = true;
            notify();
            }
        }
    }
