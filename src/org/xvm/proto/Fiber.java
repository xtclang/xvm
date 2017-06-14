package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.06.14
 */
public class Fiber
    {
    final ServiceContext f_context;

    final Fiber f_fiberPrev; // a caller's fiber (null for original)
    // the fiber status can only be mutated by the fiber itself
    private FiberStatus m_status;

    // this flag serves a hint that the execution could be resumed;
    // it's set by the responding fiber and cleared reset when the execution resumes
    // the use of this flag is tolerant to the possibility that the "waiting" state times out
    // and the execution resumes before the flag is set; however, we will never lose a
    // "a response has arrived" notification and get stuck waiting
    public volatile boolean m_fResponded;

    // Metrics: the timestamp (in nanos) when the fiber execution has started
    private long m_nanoStarted;

    /**
     * The timeout (timestamp) that this fiber is subject to (optional).
     */
    public long m_ldtTimeout;

    enum FiberStatus
        {
        InitialNew, // a new fiber has not been scheduled for execution yet
        InitialAssociated, // a fiber that is associated with another existing fiber, but
                           // has not been scheduled for execution yet
        Running, // normal execution
        Paused,  // the execution was paused by the scheduler
        Yielded, // the execution was explicitly yielded by the user code
        Waiting, // execution is blocked until the "waiting" futures are completed
        }

    public Fiber(ServiceContext context, Fiber fiberPrev)
        {
        f_context = context;
        f_fiberPrev = fiberPrev;
        m_status = FiberStatus.InitialNew;

        if (fiberPrev == null)
            {
            // an independent fiber is only limited by the timeout of the parent service
            // and in general has no timeout
            }
        else
            {
            long ldtTimeoutFiber = fiberPrev.m_ldtTimeout;
            if (ldtTimeoutFiber > 0)
                {
                // inherit the caller's timeout
                m_ldtTimeout = ldtTimeoutFiber;
                }
            else
                {
                // create a new timeout constraint
                // TODO: the value below should be passed along the inter-service invocation request
                long cTimeoutMillis = fiberPrev.f_context.m_cTimeoutMillis;
                if (cTimeoutMillis > 0)
                    {
                    m_ldtTimeout = System.currentTimeMillis() + cTimeoutMillis;
                    }
                }

            // check if the fiber chain points back to the same service
            // (clearly fiberPrev cannot belong to this service)
            Fiber fiber = fiberPrev.f_fiberPrev;
            while (fiber != null)
                {
                if (fiber.f_context == context)
                    {
                    m_status = FiberStatus.InitialAssociated;
                    break;
                    }
                fiber = fiber.f_fiberPrev;
                }
            }
        }

    public FiberStatus getStatus()
        {
        return m_status;
        }

    // called only from this fiber's execution context
    public void setStatus(FiberStatus status)
        {
        switch (m_status = status)
            {
            default:
            case InitialNew:
            case InitialAssociated:
                throw new IllegalArgumentException();

            case Running:
                m_nanoStarted = System.nanoTime();
                break;

            case Paused:
            case Waiting:
            case Yielded:
                long cNanos = System.nanoTime() - m_nanoStarted;
                m_nanoStarted = 0;
                f_context.m_cRuntimeNanos += cNanos;
                break;
            }
        }

    // only applies to the fiber in waiting status
    public boolean isReady()
        {
        assert m_status == FiberStatus.Waiting;
        return m_fResponded || isTimedOut();
        }

    public boolean isTimedOut()
        {
        return m_ldtTimeout > 0 && System.currentTimeMillis() > m_ldtTimeout;
        }
    }
