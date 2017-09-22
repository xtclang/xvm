package org.xvm.runtime;


import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

import org.xvm.util.Notifier;
import org.xvm.util.SimpleNotifier;


/**
 * A simple daemon pool.
 *
 * @author gg 2017.03.31
 */
public class DaemonPool
        implements Runnable
    {
    protected String m_sName;
    protected Thread m_thread;

    protected final Notifier f_notifier = new SimpleNotifier();

    private volatile State m_state = State.Initial;

    public volatile boolean m_fWaiting = true;

    enum State {Initial, Starting, Running, Stopping, Stopped;};

    private List<ServiceContext> f_listServices = new CopyOnWriteArrayList<>();

    /**
    * Create a DaemonPool with the specified name.
    */
    public DaemonPool(String sName)
        {
        m_sName = sName;
        }

    /**
    * Start the DaemonPool.
    */
    public synchronized void start()
        {
        if (m_state != State.Initial)
            {
            throw new IllegalStateException("Already started");
            }

        setState(State.Starting);

        Thread thread = m_thread = new Thread(new ThreadGroup(m_sName), this);
        thread.setDaemon(true);
        thread.start();

        while (!isStarted())
            {
            try
                {
                wait(1000);
                }
            catch (InterruptedException e)
                {
                throw new IllegalStateException("Failed to start");
                }
            }
        }

    public void addService(ServiceContext context)
        {
        f_listServices.add(context);
        }

    public void removeService(ServiceContext context)
        {
        f_listServices.remove(context);
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

        try
            {
            boolean fNothingToDo = false;

            while (m_state == State.Running)
                {
                if (fNothingToDo)
                    {
                    m_fWaiting = true;
                    notifier.await(10); // min of all registered timeouts
                    m_fWaiting = false;
                    }
                else
                    {
                    fNothingToDo = true;
                    }

                for (ServiceContext context : f_listServices)
                    {
                    Frame frame = context.nextFiber();

                    if (frame != null)
                        {
                        fNothingToDo = false;
                        try
                            {
                            frame = context.execute(frame);
                            if (frame != null)
                                {
                                context.suspendFiber(frame);
                                }
                            }
                        catch (Throwable e)
                            {
                            // TODO: RTError
                            frame = context.getCurrentFrame();
                            Utils.log("\nUnhandled exception at " +
                                    frame + ", iPC=" + (frame == null ? 0 : frame.m_iPC));
                            e.printStackTrace(System.out);
                            System.exit(-1);
                            }
                        }
                    }
                }
            }
        catch (InterruptedException e)
            {
            setState(State.Stopping);
            }
        catch (Throwable e)
            {
            e.printStackTrace();
            System.exit(1);
            }

        m_thread = null;

        setState(State.Stopped);
        }

    // ----- InterService Communications -----

    public void signal()
        {
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

    // ----- Helpers -----

    /**
    * @return the thread object or null if the daemon is not started
    */
    public Thread getThread()
        {
        return m_thread;
        }

    public boolean isStarted()
        {
        return m_state.ordinal() >= State.Running.ordinal();
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
        return "DaemonPool{Thread=\"" + getThread() + '\"'
            + ", State=" + m_state.name() + '}';
        }
    }
