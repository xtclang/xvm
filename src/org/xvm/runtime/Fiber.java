package org.xvm.runtime;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xNullable;


/**
 * The Fiber represents a single execution thread for a give ServiceContext.
 *
 * A regular natural cross-method call doesn't change the fiber; only a call to another service may.
 *
 * This class is completely thread safe since all the mutating methods here can only be called
 * on the parent ServiceContext native thread.
 */
public class Fiber
    {
    final long f_lId;

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

    // this flag serves a hint that the execution could possibly be resumed;
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

    // Currently active AsyncSection
    private ObjectHandle m_hAsyncSection = xNullable.NULL;

    // list of exceptions to be processed by this fiber when the active AsyncSection is closed
    private List<ExceptionHandle> m_listUnhandledEx;

    // Pending uncaptured futures; values are AsyncSection handlers
    private Map<CompletableFuture, ObjectHandle> m_mapPendingFutures;

    // if specified, indicates an action to be done first as the fiber execution resumes
    private Frame.Continuation m_resume;

    private static AtomicLong s_counter = new AtomicLong();

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
        f_lId = s_counter.getAndIncrement();

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
                // TODO: what if it's already timed out or below the staggered amount?
                m_ldtTimeout = ldtTimeoutFiber - 20;
                }
            else
                {
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

    public long getId()
        {
        return f_lId;
        }

    public FiberStatus getStatus()
        {
        return m_status;
        }

    public ObjectHandle getAsyncSection()
        {
        return m_hAsyncSection;
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
                long cNanos = System.nanoTime() - m_nanoStarted;
                m_nanoStarted = 0;
                f_context.m_cRuntimeNanos += cNanos;
                m_frame = f_context.getCurrentFrame();
                break;

            case Terminated:
                m_frame = null;
                break;
            }
        }

    // the fiber is not ready for execution if it is waiting, not responded and not timed-out
    public boolean isReady()
        {
        return m_status != FiberStatus.Waiting || m_fResponded || isTimedOut();
        }

    public boolean isTimedOut()
        {
        return m_ldtTimeout > 0 && System.currentTimeMillis() > m_ldtTimeout;
        }

    /**
     * Check whether we can proceed with the frame execution.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION} or
     *         or {@link Op#R_BLOCK} values
     */
    public int prepareRun(Frame frame)
        {
        int iResult = Op.R_NEXT;
        if (isTimedOut())
            {
            iResult = frame.raiseException(xException.makeHandle("The service has timed-out"));
            }
        else if (m_status == FiberStatus.Waiting)
            {
            assert frame == m_frame;

            iResult = frame.checkWaitingRegisters();
            if (iResult == Op.R_BLOCK)
                {
                // there are still some "waiting" registers
                m_fResponded = false;
                return Op.R_BLOCK;
                }

            if (m_resume != null && iResult == Op.R_NEXT)
                {
                iResult = m_resume.proceed(frame);
                if (iResult == Op.R_BLOCK)
                    {
                    // we still cannot resume
                    m_fResponded = false;
                    return Op.R_BLOCK;
                    }
                m_resume = null;
                }

            }

        setStatus(FiberStatus.Running);
        m_fResponded = false;
        return iResult;
        }

    public void registerUncapturedRequest(CompletableFuture<?> future)
        {
        Map<CompletableFuture, ObjectHandle> mapPending = m_mapPendingFutures;
        if (mapPending == null)
            {
            mapPending = m_mapPendingFutures = new ConcurrentHashMap<>();
            }

        mapPending.put(future, m_hAsyncSection);

        future.whenComplete((_void, ex) ->
            {
            if (ex != null)
                {
                processUnhandledException(
                    ((ExceptionHandle.WrapperException) ex).getExceptionHandle());
                }

            m_mapPendingFutures.remove(future);
            if (m_mapPendingFutures.isEmpty())
                {
                m_fResponded = true;
                }
            });
        }

    protected void processUnhandledException(ExceptionHandle hException)
        {
        ObjectHandle hAsyncSection = m_hAsyncSection;
        if (hAsyncSection == xNullable.NULL)
            {
            f_context.callUnhandledExceptionHandler(hException);
            }
        else
            {
            // there is an active AsyncSection - defer the exception handling
            List<ExceptionHandle> listEx = m_listUnhandledEx;
            if (listEx == null)
                {
                listEx = m_listUnhandledEx = new ArrayList<>();
                }
            listEx.add(hException);
            }
        }

    /**
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION} or
     *         or {@link Op#R_BLOCK} values
     */
    public int registerAsyncSection(Frame frame, ObjectHandle hSectionNew)
        {
        ObjectHandle hSectionOld = m_hAsyncSection;
        if (hSectionOld != xNullable.NULL)
            {
            // check if all the unguarded calls have completed
            if (isPending(hSectionOld))
                {
                m_resume = frameCaller ->
                    {
                    if (isPending(hSectionOld))
                        {
                        return Op.R_BLOCK;
                        }

                    List<ExceptionHandle> listEx = m_listUnhandledEx;
                    if (listEx != null && !listEx.isEmpty())
                        {
                        GenericHandle hAsyncSection = (GenericHandle) hSectionOld;
                        FunctionHandle hNotify = (FunctionHandle) hAsyncSection.getField("notify");

                        return new CallNotify(listEx, hNotify).proceed(frameCaller);
                        }
                    return Op.R_NEXT;
                    };
                return Op.R_BLOCK;
                }
            }

        m_hAsyncSection = hSectionNew;
        return Op.R_NEXT;
        }

    // Check if there are any pending futures associated with the specified AsyncSection
    private boolean isPending(ObjectHandle hSection)
        {
        return m_mapPendingFutures != null &&  m_mapPendingFutures.values().contains(hSection);
        }

    @Override
    public String toString()
        {
        return "Fiber " + f_lId + " of " + f_context + ": " + m_status.name();
        }

    protected class CallNotify
            implements Frame.Continuation
        {
        private final List<ExceptionHandle> listEx;
        private final FunctionHandle hNotify;

        public CallNotify(List<ExceptionHandle> listEx, FunctionHandle hNotify)
            {
            this.listEx = listEx;
            this.hNotify = hNotify;
            }

        public int proceed(Frame frameCaller)
            {
            while (!listEx.isEmpty())
                {
                switch (hNotify.call1(frameCaller, null,
                        new ObjectHandle[] {listEx.remove(0)}, Op.A_IGNORE))
                    {
                    case Op.R_NEXT:
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        // ignore - according to the AsyncContext contract
                        continue;

                    default:
                        throw new IllegalStateException();
                        }
                    }

            return Op.R_NEXT;
            }
        }
    }
