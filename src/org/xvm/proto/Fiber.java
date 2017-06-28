package org.xvm.proto;

import org.xvm.asm.MethodStructure;

/**
 * TODO:
 *
 * @author gg 2017.06.14
 */
public class Fiber
    {
    final ServiceContext f_context;

    // the caller's fiber (null for original)
    final Fiber f_fiberCaller;

    // the caller's frame id
    final int f_iCallerId;

    // the function of the caller's service invocation Op
    final MethodStructure f_fnCaller;

    // the fiber status can only be mutated by the fiber itself
    private FiberStatus m_status;

    // if the fiber is not running, the frame it was suspended at
    public Frame m_frame;

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
        Terminated,
        }

    public Fiber(ServiceContext context, ServiceContext.Message msgCall)
        {
        f_context = context;

        Fiber fiberCaller = f_fiberCaller = msgCall.f_fiberCaller;

        f_iCallerId = msgCall.f_iCallerId;
        f_fnCaller = msgCall.f_fnCaller;

        m_status = FiberStatus.InitialNew;

        if (fiberCaller == null)
            {
            // an independent fiber is only limited by the timeout of the parent service
            // and in general has no timeout
            }
        else
            {
            long ldtTimeoutFiber = fiberCaller.m_ldtTimeout;
            if (ldtTimeoutFiber > 0)
                {
                // inherit the caller's timeout,
                // but stagger it a bit to have the callee to time-out first
                m_ldtTimeout = ldtTimeoutFiber - 20;
                }
            else
                {
                // TODO: what is the API to the context's (rather than fiber's) timeout?
                long cTimeoutMillis = context.m_cTimeoutMillis;
                if (cTimeoutMillis > 0)
                    {
                    m_ldtTimeout = System.currentTimeMillis() + cTimeoutMillis;
                    }
                }

            // check if the fiber chain points back to the same service
            // (clearly fiberCaller cannot belong to this service)
            fiberCaller = fiberCaller.f_fiberCaller;
            while (fiberCaller != null)
                {
                if (fiberCaller.f_context == context)
                    {
                    m_status = FiberStatus.InitialAssociated;
                    break;
                    }
                fiberCaller = fiberCaller.f_fiberCaller;
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
                m_frame = null;
                break;

            case Waiting:
            case Paused:
            case Yielded:
            case Terminated:
                long cNanos = System.nanoTime() - m_nanoStarted;
                m_nanoStarted = 0;
                f_context.m_cRuntimeNanos += cNanos;
                m_frame = f_context.getCurrentFrame();
                break;
            }
        }

    // the only thing that is not ready for execution is a
    // waiting, not responded and not timed-out fiber
    public boolean isReady()
        {
        return m_status != FiberStatus.Waiting || m_fResponded || isTimedOut();
        }

    public boolean isTimedOut()
        {
        return m_ldtTimeout > 0 && System.currentTimeMillis() > m_ldtTimeout;
        }

    @Override
    public String toString()
        {
        return "Fiber of " + f_context + ": " + m_status.name();
        }
    }
