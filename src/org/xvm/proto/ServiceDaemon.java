package org.xvm.proto;

import org.xvm.util.Notifier;
import org.xvm.util.SimpleNotifier;

import org.xvm.proto.ServiceContext.Message;

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

    protected final ServiceContext f_context;

    protected Thread m_thread;

    protected final Queue<Message> f_queue = new ConcurrentLinkedQueue<>();

    protected final Notifier f_notifier = new SimpleNotifier();

    private volatile State m_state = State.Initial;

    final static ThreadLocal<ServiceContext> s_tloContext = new ThreadLocal<>();

    enum State {Initial, Starting, Running, Stopping, Stopped;};

    /**
    * Create a ServiceDaemon with the specified name.
    */
    public ServiceDaemon(String sName, ServiceContext context)
        {
        m_sName = sName;
        f_context = context;
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
        thread.setDaemon(true);
        thread.start();
        }

    // ----- Runnable interface -----

    @Override
    public void run()
        {
        s_tloContext.set(f_context);

        setState(State.Running);

        synchronized (this)
            {
            notify();
            }

        Notifier notifier = f_notifier;
        Queue<Message> queue = f_queue;
        try
            {
            while (m_state == State.Running)
                {
                notifier.await(1000);

                Message message = queue.poll();
                while (message != null)
                    {
                    try
                        {
                        message.process(f_context);
                        }
                    catch (Throwable e)
                        {
                        // TODO
                        System.out.println(f_context + " threw unhandled exception: ");
                        e.printStackTrace(System.out);
                        }
                    message = queue.poll();
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

        s_tloContext.set(null);
        }

    public void dispatch(long cMillis)
        {
        try
            {
            f_notifier.await(cMillis);

            Message msg = f_queue.poll();
            if (msg != null)
                {
                msg.process(f_context);
                }
            }
        catch (Throwable e)
            {
            // TODO
            throw new RuntimeException(e);
            }
        }

    // ----- InterService Communications -----

    public void add(Message call)
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
        return "ServiceDaemon{Thread=\"" + getThread() + '\"'
            + ", State=" + m_state.name() + '}';
        }
    }
