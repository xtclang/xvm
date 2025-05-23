package org.xvm.runtime;


import java.lang.ref.WeakReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext.Message;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * The Fiber represents a single execution thread for a give ServiceContext.
 *
 * A regular natural cross-method call doesn't change the fiber; only a call to another service may.
 *
 * This class is completely thread safe since all the mutating methods here can only be called
 * on the parent ServiceContext native thread.
 *
 * It implements Comparable to allow a registry of Fiber objects in a concurrent set.
 */
public class Fiber
        implements Comparable<Fiber>
    {
    public Fiber(ServiceContext context, Message msgCall)
        {
        f_lId        = s_counter.getAndIncrement();
        f_context    = context;
        f_iCallerId  = msgCall.f_iCallerId;
        f_fnCaller   = msgCall.f_fnCaller;
        m_status     = FiberStatus.Initial;

        Fiber fiberCaller = msgCall.f_fiberCaller;
        if (fiberCaller == null)
            {
            f_nDepth     = 0;
            f_refCaller  = null;
            m_ldtTimeout = 0L;
            m_hTimeout   = xNullable.NULL;
            m_mapTokens  = null;
            m_fCloneMap  = false;
            }
        else
            {
            f_nDepth     = msgCall.getCallDepth() + 1;
            f_refCaller  = new WeakReference<>(fiberCaller);
            m_ldtTimeout = msgCall.getTimeoutStamp();
            m_hTimeout   = msgCall.getTimeoutHandle();

            // if the call is async, we must clone context tokens on the spot
            Map<ObjectHandle, ObjectHandle> mapTokens = msgCall.f_mapTokens;
            if (mapTokens != null && msgCall.isAsync())
                {
                m_mapTokens = new HashMap<>(mapTokens);
                m_fCloneMap = false;
                }
            else
                {
                m_mapTokens = mapTokens;
                m_fCloneMap = true;
                }
            }
        }

    /**
     * @retutn the caller's fiber
     */
    protected Fiber getCaller()
        {
        return f_refCaller == null ? null : f_refCaller.get();
        }

    /**
     * @return the Timeout? handle
     */
    public ObjectHandle getTimeoutHandle()
        {
        return m_hTimeout;
        }

    /**
     * Set the fiber's timeout based on the specified Timeout? handle and the timeout timestamp.
     */
    public void setTimeoutHandle(ObjectHandle hTimeout, long ldtTimeout)
        {
        assert hTimeout != null;

        m_hTimeout   = hTimeout;
        m_ldtTimeout = ldtTimeout;
        }

    /**
     * @return the current timeout timestamp in milliseconds (using Container.currentTimeMillis());
     *         zero if there is no timeout
     */
    public long getTimeoutStamp()
        {
        return m_ldtTimeout;
        }

    /**
     * Clear the timeout stamp. This must be called when a TimedOut exception is thrown or re-thrown.
     */
    public void clearTimeout()
        {
        m_ldtTimeout = 0L;
        m_hTimeout   = xNullable.NULL;
        }

    /**
     * Get the map of SharedContext.Token objects for read-only access.
     */
    public Map<ObjectHandle, ObjectHandle> getTokens()
        {
        return m_mapTokens;
        }

    /**
     * Get the map of SharedContext.Token objects for write access.
     */
    public Map<ObjectHandle, ObjectHandle> ensureTokens()
        {
        Map<ObjectHandle, ObjectHandle> map = m_mapTokens;
        if (map == null)
            {
            m_mapTokens = map = new HashMap<>();
            m_fCloneMap = false;
            }
        else if (m_fCloneMap)
            {
            m_mapTokens = map = new HashMap<>(map);
            m_fCloneMap = false;
            }
        return map;
        }

    /**
     * @return true iff this fiber points back (as a logical thread of execution) to a fiber on the
     *         specified service
     */
    public boolean isContinuationOf(Fiber fiberOrig)
        {
        if (f_context == fiberOrig.f_context)
            {
            return true;
            }

        Fiber fiberCaller = getCaller();
        return fiberCaller != null && fiberCaller.isContinuationOf(fiberOrig);
        }

    /**
     * @return true iff this fiber chain has a common origin with the specified fiber
     */
    public boolean isAssociated(Fiber that)
        {
        if (this.f_context == that.f_context)
            {
            return this == that;
            }

        Fiber fiberCaller = this.getCaller();
        if (fiberCaller == null)
            {
            fiberCaller = that.getCaller();
            return fiberCaller != null && this.isAssociated(fiberCaller);
            }

        // by switching the target to "that" we alternate the descending checks:
        // (f1 ~ f2) --> (f2.prev ~ f1) --> (f1.prev ~ f2.prev) --> ...
        return that.isAssociated(fiberCaller);
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

    /**
     * Set the fiber's status; called only from this fiber's service thread.
     *
     * @param status  the status
     * @param cOps    the number of ops this fiber has processed since the last status update
     */
    public void setStatus(FiberStatus status, long cOps)
        {
        switch (m_status = status)
            {
            default:
            case Initial:
                throw new IllegalArgumentException();

            case Running:
                m_nanoStarted = f_context.f_container.nanoTime();
                m_frame = null;
                break;

            case Waiting:
            case Paused:
                long cNanos = f_context.f_container.nanoTime() - m_nanoStarted;
                m_nanoStarted = 0;
                f_context.m_cRuntimeNanos += cNanos;
                m_frame = f_context.getCurrentFrame();
                m_cOps += cOps;
                break;

            case Terminating:
                m_frame = null;
                break;
            }
        }

    /**
     * Obtain the current frame for this Fiber.
     */
    public Frame getFrame()
        {
        return switch (m_status)
            {
            case Initial     -> null;
            case Running     -> f_context.getCurrentFrame();
            case Waiting,
                 Paused,
                 Terminating -> m_frame;
            };
        }

    /*
     * @return true iff the fiber is ready for execution (e.g. not waiting, responded or timed-out)
     */
    public boolean isReady()
        {
        return m_status != FiberStatus.Waiting || m_fResponded || isTimedOut();
        }

    /*
     * @return true iff the fiber is waiting
     */
    public boolean isWaiting()
        {
        return m_status == FiberStatus.Waiting;
        }

    /**
     * @return true iff the fiber has timed out
     */
    public boolean isTimedOut()
        {
        return m_ldtTimeout > 0 && f_context.f_container.currentTimeMillis() > m_ldtTimeout;
        }

    /**
     * @return the caller fiber if it exists and is traceable from this fiber's context
     */
    public Fiber traceCaller()
        {
        Fiber fiberCaller = getCaller();

        if (fiberCaller != null &&
            fiberCaller.f_context.f_container != f_context.f_container)
            {
            // TODO check the container relationship?
            }

        return fiberCaller;
        }

    /**
     * @return the calling container
     */
    public Container getCallingContainer()
        {
        Fiber fiberCaller = getCaller();
        return fiberCaller == null ? null : fiberCaller.f_context.f_container;
        }
    /**
     * Check whether we can proceed with the frame execution.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION} or
     *         {@link Op#R_BLOCK} values
     */
    public int prepareRun(Frame frame)
        {
        int iResult;
        if (isTimedOut())
            {
            iResult = frame.raiseException(
                        xException.timedOut(frame, "The service has timed-out", getTimeoutHandle()));
            clearTimeout();
            }
        else
            {
            iResult = Op.R_NEXT;
            switch (getStatus())
                {
                case Waiting:
                    assert frame == m_frame;

                    if (m_resume != null)
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

                    if (frame.m_hException != null)
                        {
                        iResult = Op.R_EXCEPTION;
                        }
                    break;

                case Terminating:
                    return frame.raiseException(xException.serviceTerminated(frame, f_context.f_sName));
                }
            }

        setStatus(FiberStatus.Running, 0);
        m_fResponded = false;
        return iResult;
        }

    /**
     * Register an invoke/call request to another service.
     *
     * @param request  the request
     */
    public void registerRequest(Message request)
        {
        addDependee(request);

        m_cPending++;

        request.f_future.whenComplete((_void, ex) ->
            {
            removeDependee(request);

            if (--m_cPending == 0 && m_status == FiberStatus.Terminating)
                {
                f_context.terminateFiber(this, 0);
                }
            });
        }

    protected void addDependee(Message request)
        {
        if (request.m_fiber == this)
            {
            return;
            }

        Object oPending = m_oPendingRequests;
        if (oPending == null)
            {
            m_oPendingRequests = request;
            }
        else if (oPending instanceof Message requestPrev)
            {
            Map<CompletableFuture, Message> mapPending = new HashMap<>();
            mapPending.put(requestPrev.f_future, requestPrev);
            mapPending.put(request.f_future, request);
            m_oPendingRequests = mapPending;
            }
        else
            {
            Map<CompletableFuture, Message> mapPending = (Map<CompletableFuture, Message>) oPending;
            mapPending.put(request.f_future, request);
            }
        }

    protected void removeDependee(Message request)
        {
        if (request.m_fiber == this)
            {
            return;
            }

        Object oPending = m_oPendingRequests;
        if (oPending instanceof Message)
            {
            m_oPendingRequests = null;
            }
        else
            {
            ((Map<CompletableFuture, Message>) oPending).remove(request.f_future);
            }
        }

    /**
     * A notification indicating that a request sent by this fiber to another service has been
     * processed.
     */
    public void onResponse()
        {
        m_fResponded = true;
        }

    /**
     * Uncaptured request is a "fire and forget" call that needs to be tracked and reported
     * to an UnhandledExceptionHandler if such a handle was registered naturally.
     */
    public void registerUncapturedRequest(Message request)
        {
        Map<CompletableFuture, ObjectHandle> mapPending = m_mapPendingUncaptured;
        if (mapPending == null)
            {
            m_mapPendingUncaptured = mapPending = new HashMap<>();
            }

        m_cPending++;

        CompletableFuture future = request.f_future;
        mapPending.put(future, m_hAsyncSection);

        future.whenComplete((_void, ex) ->
            {
            if (ex != null)
                {
                processUnhandledException(
                    ((ExceptionHandle.WrapperException) ex).getExceptionHandle());
                }

            m_mapPendingUncaptured.remove(future);
            if (--m_cPending == 0 && getStatus() == FiberStatus.Terminating)
                {
                f_context.terminateFiber(this, 0);
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
                m_listUnhandledEx = listEx = new ArrayList<>();
                }
            listEx.add(hException);
            }
        }

    /**
     * Note: this API is the sole reason that {@link ClassTemplate#invokeNative1} is allowed to
     * return R_BLOCK value.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION} or
     *         {@link Op#R_BLOCK} values
     */
    public int registerAsyncSection(Frame frame, ObjectHandle hSectionNew)
        {
        ObjectHandle hSectionOld = m_hAsyncSection;
        if (hSectionOld != xNullable.NULL)
            {
            // check if all the unguarded calls have completed
            if (isSectionPending(hSectionOld))
                {
                m_resume = frameCaller ->
                    {
                    if (isSectionPending(hSectionOld))
                        {
                        return Op.R_BLOCK;
                        }

                    List<ExceptionHandle> listEx = m_listUnhandledEx;
                    if (listEx != null && !listEx.isEmpty())
                        {
                        GenericHandle  hAsyncSection = (GenericHandle) hSectionOld;
                        FunctionHandle hNotify       = (FunctionHandle) hAsyncSection.
                                                            getField(frameCaller, "notify");
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

    /**
     * Check if there are any pending futures associated with this fiber.
     */
    protected boolean hasPendingRequests()
        {
        return m_status == FiberStatus.Waiting || m_cPending > 0;
        }

    /**
     * Check if there are any pending futures associated with the specified AsyncSection.
     */
    private boolean isSectionPending(ObjectHandle hSection)
        {
        return m_mapPendingUncaptured != null && m_mapPendingUncaptured.containsValue(hSection);
        }

    /**
     * @return the blocking frame
     */
    public Frame getBlocker()
        {
        return m_frameBlocker;
        }

    /**
     * Set or clear the frame that blocks this fiber's execution.
     */
    protected void setBlocker(Frame frameBlocker)
        {
        m_frameBlocker = frameBlocker;
        }


    // ----- Debugging support ---------------------------------------------------------------------

    /**
     * @return human-readable status of a waiting fiber
     */
    public String reportWaiting()
        {
        assert m_status == FiberStatus.Waiting
            || m_status == FiberStatus.Terminating;

        Object oPending = m_oPendingRequests;
        if (oPending == null)
            {
            return " for closure";
            }

        // TODO: check for the deadlock
        if (oPending instanceof Message msg)
            {
            Fiber fiber = msg.m_fiber;
            return " for " + (fiber == null ? "initial" : fiber);
            }

        StringBuilder sb     = new StringBuilder(" for [");
        boolean       fFirst = true;
        for (Message request : ((Map<CompletableFuture, Message>) oPending).values())
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(", ");
                }
            Fiber fiber = request.m_fiber;
            sb.append(fiber);
            }
        sb.append(']');
        return sb.toString();
        }


    // ----- Comparable & Object methods -----------------------------------------------------------

    @Override
    public int compareTo(Fiber that)
        {
        return Long.compare(this.f_lId, that.f_lId);
        }

    @Override
    public int hashCode()
        {
        return Long.hashCode(f_lId);
        }

    @Override
    public boolean equals(Object obj)
        {
        return obj instanceof Fiber fiber && f_lId == fiber.f_lId;
        }

    @Override
    public String toString()
        {
        return "Fiber " + f_lId + " (" + m_status.name() + ')';
        }


    // ----- inner classes -------------------------------------------------------------------------

    /**
     * Support for "notify" call.
     */
    protected static class CallNotify
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


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The id.
     */
    private final long f_lId;

    /**
     * The parent context.
     */
    final ServiceContext f_context;

    /**
     * A weak reference to the  caller's fiber (null for "inception" fibers).
     */
    final WeakReference<Fiber> f_refCaller;

    /*
     * The caller's frame id (used for stack trace only).
     */
    final int f_iCallerId;

    /**
     * The function of the caller's service invocation Op (used for stack trace only).
     */
    final MethodStructure f_fnCaller;

    /**
     * The fiber call stack depth.
     */
    final int f_nDepth;

    /**
     * The fiber status (can only be mutated by the fiber itself).
     */
    private volatile FiberStatus m_status;

    /**
     * If the fiber is not running, the frame it was suspended at.
     */
    private Frame m_frame;

    /**
     * This flag serves a hint that the execution could possibly be resumed; it's set by the
     * responding fiber and reset when the execution resumes; the use of this flag is tolerant to
     * the possibility that the "waiting" state times-out and the execution resumes before the flag
     * is set or that the flag is set proactively or optimistically; however, we must never lose
     * this "response has arrived" notification and get stuck waiting.
     */
    public volatile boolean m_fResponded;

    /**
     * Metrics: the timestamp (in nanos) when the fiber execution has started.
     */
    private long m_nanoStarted;

    /**
     * The timeout that this fiber is subject to (optional).
     */
    private ObjectHandle m_hTimeout;

    /**
     * The timeout (timestamp) that this fiber is subject to (optional).
     */
    private long m_ldtTimeout;

    /**
     * A "service-local" map of SharedContext.Token objects keyed by the SharedContext objects.
     * This map is using a "copy-on-write" policy.
     *
     * Both ContextToken and SharedContext are "consts" and are "pass-through" across services.
     */
    private Map<ObjectHandle, ObjectHandle> m_mapTokens;

    /**
     * This flag indicates that the token map needs to be cloned if {@link #ensureTokens()} is called.
     */
    private boolean m_fCloneMap;

    /**
     * Metrics: the total number of ops this fiber has executed.
     */
    private long m_cOps;

    /**
     * Currently active AsyncSection.
     */
    private ObjectHandle m_hAsyncSection = xNullable.NULL;

    /**
     * List of exceptions to be processed by this fiber when the active AsyncSection is closed.
     */
    private List<ExceptionHandle> m_listUnhandledEx;

    /**
     * Counter of pending requests originated by this fiber. Can be mutated only on the fiber's
     * service thread.
     */
    private int m_cPending;

    /**
     * Pending requests: Message | Map<CompletableFuture, Message>.
     */
    private Object m_oPendingRequests;

    /**
     * Pending uncaptured futures; values are AsyncSection? handlers. Can be accessed only on the
     * fiber's service thread.
     */
    private Map<CompletableFuture, ObjectHandle> m_mapPendingUncaptured;

    /**
     * If specified, indicates an action to be performed as the fiber execution resumes.
     */
    private Frame.Continuation m_resume;

    /**
     * If specified, indicates a frame that blocks this fiber's execution. Used for deadlock
     * detection.
     */
    private Frame m_frameBlocker;

    /**
     * The counter used to create fibers ids.
     */
    private static final AtomicLong s_counter = new AtomicLong();

    public enum FiberStatus
        {
        Initial           (3), // a new fiber has not been scheduled for execution yet
        Running           (4), // normal execution
        Paused            (2), // the execution was paused by the scheduler
        Waiting           (1), // execution is blocked until the "waiting" futures are completed
        Terminating       (0); // the fiber has been terminated, but some requests are still pending

        /**
         * @param nActivity  the activity index
         */
        FiberStatus(int nActivity)
            {
            this.nActivity = nActivity;
            }

        FiberStatus moreActive(FiberStatus that)
            {
            return that == null || this.nActivity >= that.nActivity
                    ? this
                    : that;
            }

        /**
         * The activity index; the higher the index, the more active the status
         */
        private final int nActivity;
        }
    }