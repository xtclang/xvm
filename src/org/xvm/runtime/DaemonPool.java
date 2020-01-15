package org.xvm.runtime;

import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

/**
 * A simple daemon pool.
 */
public class DaemonPool
        implements Runnable
    {
    protected String m_sName;

    private volatile State m_state = State.Initial;

    private final AtomicInteger f_cActive = new AtomicInteger();

    enum State {Initial, Starting, Running, Stopping, Stopped;};

    private Set<ServiceContext> f_listServices = ConcurrentHashMap.newKeySet();

    private final ExecutorService f_executor;

    /**
    * Create a DaemonPool with the specified name.
    */
    public DaemonPool(String sName)
        {
        m_sName = sName;

        ThreadGroup group = new ThreadGroup(m_sName);
        f_executor = Executors.newCachedThreadPool(r ->
            {
            Thread thread = new Thread(group, r);
            thread.setDaemon(true);
            thread.setName(m_sName + "@" + thread.hashCode());
            return thread;
            });
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

        int concurrency = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
        if (concurrency <= 0) {
            concurrency = java.lang.Runtime.getRuntime().availableProcessors();
        }

        f_cActive.set(concurrency);
        setState(State.Running);

        for (int i = 0; i < concurrency; ++i)
            {
            f_executor.submit(this);
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

    public boolean isIdle()
        {
        return f_cActive.get() == 0;
        }

    // ----- Runnable interface -----

    @Override
    public void run()
        {
        try
            {
            boolean fNothingToDo = false;

            while (m_state == State.Running)
                {
                if (fNothingToDo)
                    {
                    f_cActive.decrementAndGet();
                    synchronized (this)
                        {
                        wait(10); // min of all registered timeouts
                        }
                    f_cActive.incrementAndGet();
                    }
                else
                    {
                    fNothingToDo = true;
                    }

                for (ServiceContext context : f_listServices)
                    {
                    if (context.tryLock())
                        {
                        try
                            {
                            Frame frame = context.nextFiber();

                            if (frame != null)
                                {
                                fNothingToDo = false;
                                try
                                    {
                                    ConstantPool.setCurrentPool(frame.poolContext());

                                    frame = context.execute(frame);
                                    if (frame != null)
                                        {
                                        context.suspendFiber(frame);
                                        }

                                    ConstantPool.setCurrentPool(null);
                                    }
                                catch (Throwable e)
                                    {
                                    // TODO: RTError
                                    frame = context.getCurrentFrame();
                                    if (frame != null)
                                        {
                                        MethodStructure function = frame.f_function;
                                        int nLine = 0;
                                        if (function != null)
                                            {
                                            nLine = function.calculateLineNumber(frame.m_iPC);
                                            }

                                        Utils.log(frame, "\nUnhandled exception at " + frame
                                                + (nLine > 0 ? "; line=" + nLine : "; iPC=" + frame.m_iPC));
                                        }
                                    e.printStackTrace(System.out);
                                    System.exit(-1);
                                    }
                                }
                            }
                        finally
                            {
                            context.releaseLock();
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
        }

    // ----- InterService Communications -----

    public void signal()
        {
        synchronized (this)
            {
            notify();
            }
        }

    // ----- Service interface -----

    public synchronized void shutdown()
        {
        if (m_state == State.Running)
            {
            m_state = State.Stopping;
            notifyAll();
            f_executor.shutdown();
            setState(State.Stopped);
            }
        }

    // ----- Helpers -----

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
        return "DaemonPool{Executor='" + f_executor + "', State=" + m_state.name() + '}';
        }
    }
