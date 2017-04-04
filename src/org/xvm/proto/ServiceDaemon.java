package org.xvm.proto;

import org.xvm.util.Notifier;
import org.xvm.util.SimpleNotifier;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A single thread Service daemon.
 *
 * @author gg 2017.03.31
 */
public class ServiceDaemon
        implements Runnable
    {
    protected String m_sName;

    protected Thread m_thread;

    protected final Queue<Invocation> f_queue = new ConcurrentLinkedQueue<>();

    protected final Notifier f_notifier = new SimpleNotifier();

    private volatile State m_state;

    enum State {Initial, Starting, Running, Stopping, Stopped};

    /**
    * Create a ServiceDaemon with the specified name.
    */
    public ServiceDaemon(String sName)
        {
        m_sName = sName;
        }

    /**
    * Start the ServiceDaemon.
    */
    public synchronized void start()
        {
        if (m_state != State.Initial)
            {
            throw new IllegalStateException("Already started");
            }

        setState(State.Starting);

        Thread thread = m_thread = new Thread(this, m_sName);
        thread.start();

        try
            {
            wait();
            }
        catch (InterruptedException e)
            {
            throw new RuntimeException("Failed to start " + this, e);
            }
        }

    // ----- Runnable interface -----

    @Override
    public void run()
        {
        setState(State.Running);

        synchronized (this)
            {
            notify();
            }

        Notifier notifier = f_notifier;
        Queue<Invocation> queue = f_queue;
        try
            {
            while (m_state == State.Running)
                {
                notifier.await(1000);

                Invocation call = queue.poll();
                while (call != null)
                    {
                    try
                        {
                        process(call);
                        }
                    catch (Throwable e)
                        {
                        // TODO
                        }
                    call = queue.poll();
                    }
                }
            }
        catch (Throwable e)
            {
            // TODO
            }

        f_queue.clear();
        m_thread = null;

        setState(State.Stopped);
        }


    protected void process(Invocation call)
        {

        }

    // ----- InterService Communications -----

    public void add(Invocation call)
        {
        f_queue.add(call);
        f_notifier.signal();
        }


    // ----- Service interface -----

    public synchronized void shutdown()
        {
        if (m_state == State.Running)
            {
            m_state = State.Stopping;
            }
        f_notifier.signal();
        }

    public void kill()
        {
        m_thread.interrupt();
        }


    // ----- Helpers -----

    /**
    * @return the thread object or null if the daemon is not started
    */
    public Thread getThread()
        {
        return m_thread;
        }

    /**
    * @return true if the current thread is the service thread
    */
    public boolean isServiceThread()
        {
        return Thread.currentThread() == getThread();
        }

    protected synchronized void setState(State state)
        {
        if (state.ordinal() > m_state.ordinal())
            {
            m_state = state;
            }
        }

    @Override
    public String toString()
        {
        return "ServiceDaemon{Thread=\"" + getThread() + '\"'
            + ", State=" + m_state.name() + '}';
        }


    /**
     * Represents a call from one service onto another.
     */
    public static class Invocation
        {

        }
    }
