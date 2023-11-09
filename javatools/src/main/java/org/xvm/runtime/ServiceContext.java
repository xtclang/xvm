package org.xvm.runtime;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.ref.WeakReference;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimerTask;
import java.util.WeakHashMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import java.util.concurrent.atomic.AtomicLong;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.asm.op.Return_0;

import org.xvm.runtime.ObjectHandle.TransientId;
import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle.WrapperException;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.PropertyOperation10;
import org.xvm.runtime.template.xService.PropertyOperation01;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.runtime.template._native.temporal.xLocalClock;


/**
 * The service context.
 */
public class ServiceContext
    {
    ServiceContext(Container container, String sName, long lId)
        {
        f_container = container;
        f_pool      = container.getConstantPool();
        f_sName     = sName;
        f_lId       = lId;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public Runtime getRuntime()
        {
        return f_container.f_runtime;
        }

    public LinkerContext getLinkerContext()
        {
        return f_container;
        }

    public ServiceContext getMainContext()
        {
        return f_container.getServiceContext();
        }

    public ServiceHandle getService()
        {
        return m_hService;
        }

    public void setService(ServiceHandle hService)
        {
        assert m_hService == null || m_hService.isStruct() && !hService.isStruct();
        m_hService = hService;
        }

    /**
     * Supporting method for natural Service.registerTimeout() API.
     *
     * @return the current Timeout? handle
     */
    public ObjectHandle getTimeoutHandle()
        {
        return m_hTimeout;
        }

    /**
     * Supporting method for natural Service.registerTimeout() API.
     *
     * @param hTimeout  the new Timeout? handle
     */
    public void setTimeoutHandle(ObjectHandle hTimeout)
        {
        assert hTimeout != null;

        m_hTimeout = hTimeout;
        }

    /**
     * Supporting method for Service.synchronizedSection.get().
     *
     * @return the current SynchronizedSection? handle
     */
    public ObjectHandle getSynchronizedSection()
        {
        return m_hSynchronizedSection;
        }

    /**
     * Supporting method for Service.registerSynchronizedSection().
     *
     * @param frame     the frame that registers or unregisters the SynchronizedSection
     * @param hSection  the new SynchronizedSection? handle
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int setSynchronizedSection(Frame frame, ObjectHandle hSection)
        {
        assert hSection != null;

        if (m_hSynchronizedSection != xNullable.NULL && m_fiberSyncOwner != frame.f_fiber)
            {
            // should never happen
            return frame.raiseException("Attempt to reset unowned SynchronizedSection");
            }

        m_hSynchronizedSection = hSection;

        if (hSection == xNullable.NULL)
            {
            setSynchronicity(null, Synchronicity.Concurrent);
            }
        else
            {
            ObjectHandle hCritical = ((GenericHandle) hSection).getField(null, "critical");

            setSynchronicity(frame.f_fiber, ((BooleanHandle) hCritical).get() ?
                    Synchronicity.Critical : Synchronicity.Synchronized);
            }

        return Op.R_NEXT;
        }

    /**
     * Set the synchronicity values.
     *
     * @param fiber          the owner fiber
     * @param synchronicity  the Synchronicity value
     */
    public void setSynchronicity(Fiber fiber, Synchronicity synchronicity)
        {
        m_fiberSyncOwner = fiber;
        m_synchronicity  = synchronicity;
        }

    /**
     * @return the currently active frame
     */
    public Frame getCurrentFrame()
        {
        return m_frameCurrent;
        }

    /**
     * @return the currently active fiber
     */
    public Fiber getCurrentFiber()
        {
        // Note: this method can be called by another service
        Frame frameCurrent = m_frameCurrent;
        return frameCurrent == null ? null : frameCurrent.f_fiber;
        }

    /**
     * @return the set of Fibers for this context
     */
    public Set<Fiber> getFibers()
        {
        return f_setFibers;
        }

    /**
     * @return the ServiceContext associated with the current Java thread
     */
    public static ServiceContext getCurrentContext()
        {
        return s_tloContext.get()[0];
        }

    /**
     * Note: the value of {@link Synchronicity#Concurrent Concurrent} here actually means
     * "frame-dependent".
     *
     * @return the current service Synchronicity value
     */
    public Synchronicity getSynchronicity()
        {
        return m_synchronicity;
        }

    /**
     * @return the synchronized section owner
     */
    public Fiber getSynchronizationOwner()
        {
        return m_fiberSyncOwner;
        }

    /**
     * Check if a debugging session is on.
     */
    public boolean isDebuggerActive()
        {
        // for now, we use a global flag, but should allow debugging of an individual
        // service/container.
        return f_container.f_runtime.isDebuggerActive();
        }

    /**
     * Set or clear a debugging session flag.
     */
    public void setDebuggerActive(boolean fActive)
        {
        f_container.f_runtime.setDebuggerActive(fActive);
        }

    /**
     * @return the active debugger
     */
    public Debugger getDebugger()
        {
        return DebugConsole.INSTANCE;
        }


    // ----- Op support ----------------------------------------------------------------------------

    /**
     * Retrieve an Op specific info.
     *
     * @param op       the op
     * @param category the category of the cached info (op specific)
     *
     * @return the op info for the specified category
     */
    public Object getOpInfo(Op op, Enum category)
        {
        EnumMap mapByCategory = f_mapOpInfo.get(op);
        if (mapByCategory == null)
            {
            return null;
            }
        WeakReference ref = (WeakReference) mapByCategory.get(category);
        return ref == null ? null : ref.get();
        }

    /**
     * Store an Op specific info.
     *
     * @param op       the op
     * @param category the category of the cached info (op specific)
     * @param info     the info
     */
    public void setOpInfo(Op op, Enum category, Object info)
        {
        f_mapOpInfo.computeIfAbsent(op, (op_) -> new EnumMap(category.getClass()))
                   .put(category, new WeakReference(info));
        }

    /**
     * @return service-local value represented by the specified ref
     */
    public ObjectHandle getTransientValue(TransientId ref)
        {
        return ensureTransientMap().get(ref);
        }

    /**
     * Set service-local value represented by the specified ref.
     */
    public void setTransientValue(TransientId ref, ObjectHandle value)
        {
        ensureTransientMap().put(ref, value);
        }

    /**
     * @return the map of service-local values; accessed only by this service
     */
    private Map<TransientId, ObjectHandle> ensureTransientMap()
        {
        Map<TransientId, ObjectHandle> map = m_mapTransient;
        if (map == null)
            {
            map = m_mapTransient = new WeakHashMap<>();
            }
        return map;
        }

    /**
     * @return the map of callbacks keyed by unique ids
     */
    protected Map<Long, FunctionHandle> ensureCallbackMap()
        {
        Map<Long, FunctionHandle> map = m_mapCallbacks;
        if (map == null)
            {
            map = m_mapCallbacks = new HashMap<>();
            }
        return map;
        }

    /**
     * @return the map of callbacks keyed by unique ids
     */
    protected Map<Long, FunctionHandle> getCallbackMap()
        {
        return m_mapCallbacks;
        }


    // ----- scheduling  ---------------------------------------------------------------------------

    /**
     * Attempt to complete all pending work.
     *
     * @return true if the context has no further processing to perform at this time
     */
    protected boolean drainWork()
        {
        ServiceContext[] tloCtx = s_tloContext.get();
        ServiceContext ctxPrior = tloCtx[0];
        tloCtx[0] = this;

        try
            {
            Frame frame = nextFiber();

            if (frame != null)
                {
                try (var ignored = ConstantPool.withPool(frame.poolContext()))
                    {
                    frame = execute(frame);

                    if (frame != null)
                        {
                        suspendFiber(frame);
                        return false;
                        }
                    }
                catch (Throwable e)
                    {
                    e.printStackTrace(System.err);
                    System.exit(-1);
                    }
                }
            }
        finally
            {
            tloCtx[0] = ctxPrior;

            if (ctxPrior != null)
                {
                // now that we've switched back to the caller's service context process any responses
                // which may have arrived
                ctxPrior.processResponses();
                }
            }

        return !f_queueSuspended.isReady();
        }

    /**
     * Ensure this service is scheduled for processing.
     *
     * @param fAsync  if true, avoid the in-line optimization for the request execution
     */
    protected void ensureScheduled(boolean fAsync)
        {
        if (tryAcquireSchedulingLock())
            {
            execute(!fAsync);
            }
        // else; already scheduled
        }

    /**
     * Execute any outstanding work for this service.
     */
    public void execute(boolean fAllowInlineExecution)
        {
        if (fAllowInlineExecution && drainWork())
            {
            if (getStatus() == ServiceStatus.Terminated)
                {
                f_container.terminate(this);
                }
            else
                {
                releaseSchedulingLock();
                }
            }
        else
            {
            f_container.schedule(this);
            }
        }

    /**
     * Attempt to acquire the context's scheduling lock.
     *
     * @return {@code true} iff acquired
     */
    protected boolean tryAcquireSchedulingLock()
        {
        // we avoid a pre-check for LOCK_AVAILABLE thus ensuring that if lock is currently held
        // that our failure to obtain it is visible, see releaseSchedulingLock for details on how
        // this is utilized
        return (long) SCHEDULING_LOCK_HANDLE.getAndAdd(this, 1L) == 0L;
        }

    /**
     * Release the context lock.
     */
    protected void releaseSchedulingLock()
        {
        // If isContended is true then the service requires more processing, so we can immediately
        // reschedule, thus transferring our lock ownership. Between checking that state and releasing
        // the lock isContended could transition to true and the thread doing that transition would
        // fail to get our yet to be released lock. We could defend against this by checking isContended
        // after releasing but portions (FiberQueue) of isContended are not thread-safe, and thus we
        // shouldn't query it after releasing the lock. Instead, we defend against this by detecting
        // contention on the lock itself and inferring that the contending thread must have injected
        // more work, so we must reschedule on their behalf. Note if we're wrong and the contention
        // doesn't represent new work then the scheduled task will be a no-op and just come back here
        // to release again, and is thus safe.

        long lLockPreState = m_lLockScheduling; // read lock state prior to isContended check
        if (isContended() || !SCHEDULING_LOCK_HANDLE.compareAndSet(this, lLockPreState, 0L))
            {
            // we've detected service or lock contention, reschedule
            f_container.schedule(this);
            }
        else if (!f_setFibers.isEmpty())
            {
            // make sure to wake up for the nearest timeout
            long ldtTimeout = Long.MAX_VALUE;
            for (Fiber fiber : f_setFibers)
                {
                long ldtTimeoutFiber = fiber.getTimeoutStamp();
                if (ldtTimeoutFiber > 0)
                    {
                    ldtTimeout = Math.min(ldtTimeout, ldtTimeoutFiber);
                    }
                }

            if (ldtTimeout != Long.MAX_VALUE)
                {
                f_wakeUpScheduler.schedule(ldtTimeout);
                }
            }
        }

    /**
     * Add a message to the service request queue.
     *
     * @param msg     the request message
     * @param fAsync  if true, avoid the in-line optimization for the request execution
     *
     * @return true if the service has become "overwhelmed" - too many outstanding messages
     */
    public boolean addRequest(Message msg, boolean fAsync)
        {
        f_queueMsg.add(msg);
        ensureScheduled(fAsync);
        return isOverwhelmed();
        }

    /**
     * @return true if the service has too many outstanding messages
     */
    public boolean isOverwhelmed()
        {
        return f_queueMsg.size() + f_queueSuspended.size() > QUEUE_THRESHOLD;
        }

    /**
     * Add a response message to the service response queue.
     */
    private void respond(Response response)
        {
        f_queueResponse.add(response);
        ensureScheduled(true);
        }

    /**
     * Process all queued responses.
     */
    private void processResponses()
        {
        Response response;
        while ((response = f_queueResponse.poll()) != null)
            {
            response.run();
            }
        }

    /**
     * Get a next frame ready for execution.
     *
     * @return a Frame to execute or null if this service doesn't have any frames ready for
     *         execution
     */
    private Frame nextFiber()
        {
        // responses have the highest priority and no natural code runs there;
        // process all we've got so far
        processResponses();

        // pickup all the messages, but keep them in the "initial" state
        FiberQueue qFiber = f_queueSuspended;

        Message message;
        while ((message = f_queueMsg.poll()) != null)
            {
            qFiber.add(message.createFrame(this));
            }

        // allow initial timeouts to be processed always, since they won't run any natural code
        // TODO: return ?f_queueSuspended.getInitialTimeout();

        // a paused frame must be resumed first
        return m_frameCurrent == null
                ? qFiber.getReady()
                : m_frameCurrent;
        }

    /**
     * Suspend the fiber that the specified frame belongs to.
     *
     * @param frame  the frame to suspend
     */
    public void suspendFiber(Frame frame)
        {
        switch (frame.f_fiber.getStatus())
            {
            case Running -> throw new IllegalStateException();

            case Initial -> f_queueSuspended.add(frame);

            case Waiting ->
                    {
                    m_frameCurrent = null;
                    f_queueSuspended.add(frame);
                    }

            case Paused -> m_frameCurrent = frame; // we must resume this frame
            }
        }

    /**
     * Start or resume execution of the specified frame.
     *
     * @param frame  the frame to execute
     *
     * @return a frame that has been suspended or null if the fiber associated with this frame has
     *         finished execution or has been terminated due to an exception or any other means
     */
    public Frame execute(Frame frame)
        {
        Fiber fiber   = frame.f_fiber;
        int   iPC     = frame.m_iPC;
        int   iPCLast = iPC;

        m_frameCurrent = frame;

        switch (fiber.prepareRun(frame))
            {
            case Op.R_NEXT:
                // proceed as is
                break;

            case Op.R_CALL:
                // there was a deferred action
                frame = m_frameCurrent = frame.m_frameNext;
                iPC = 0;
                break;

            case Op.R_EXCEPTION:
                iPC = Op.R_EXCEPTION;
                break;

            case Op.R_BLOCK: // there are still some non-completed futures
                return frame;

            default:
                throw new IllegalStateException();
            }

        Op[] aOp  = frame.f_aOp;
        int  cOps = 0;

    nextOp:
        while (true)
            {
            while (iPC >= 0) // main loop
                {
                frame.m_iPC = iPC;

                if (++cOps > 1_000_000)
                    {
                    fiber.setStatus(FiberStatus.Paused, cOps);
                    return frame;
                    }

                try
                    {
                    iPC = aOp[iPC].process(frame, iPCLast = iPC);
                    }
                catch (Throwable e)
                    {
                    e.printStackTrace(System.err);
                    iPC = frame.raiseException("Run-time error: " + e);
                    }

                if (iPC == Op.R_NEXT)
                    {
                    iPC = iPCLast + 1;
                    }
                }

            switch (iPC)
                {
                case Op.R_RETURN_CALL:
                    frame = frame.f_framePrev;
                    // fall-through

                case Op.R_CALL:
                    m_frameCurrent = frame.m_frameNext;
                    frame.m_frameNext = null;
                    frame = m_frameCurrent;
                    aOp = frame.f_aOp;
                    // a new frame can already be in the "exception" state
                    iPC = frame.m_hException == null ? 0 : Op.R_EXCEPTION;
                    break;

                case Op.R_RETURN:
                    {
                    if (isDebuggerActive())
                        {
                        getDebugger().onReturn(frame);
                        }

                    Frame.Continuation continuation = frame.m_continuation;
                    frame = m_frameCurrent = frame.f_framePrev; // GC the old frame

                    if (frame != null)
                        {
                        iPC = frame.m_iPC + 1;
                        }
                    if (continuation != null)
                        {
                        int iResult = continuation.proceed(frame);
                        switch (iResult)
                            {
                            case Op.R_NEXT:
                                break;

                            case Op.R_EXCEPTION:
                                // continuation is allowed to "throw"
                                assert frame.m_hException != null;

                                iPC = Op.R_EXCEPTION;
                                continue; // nextOp

                            case Op.R_CALL:
                                assert frame.m_frameNext != null;

                                m_frameCurrent = frame.m_frameNext;
                                frame.m_frameNext = null;
                                frame = m_frameCurrent;

                                aOp = frame.f_aOp;
                                iPC = 0;
                                continue; // nextOp

                            case Op.R_RETURN:
                                iPC = Op.R_RETURN;
                                continue; // nextOp

                            default:
                                if (iResult < 0)
                                    {
                                    throw new IllegalStateException();
                                    }
                                iPC = iResult;
                                break;
                            }
                        }

                    if (frame == null)
                        {
                        // all done
                        terminateFiber(fiber);
                        return m_frameCurrent = null;
                        }

                    aOp = frame.f_aOp;
                    break;
                    }

                case Op.R_RETURN_EXCEPTION:
                    frame = frame.f_framePrev;
                    // fall-through

                case Op.R_EXCEPTION:
                    {
                    ExceptionHandle hException = frame.m_hException;
                    assert hException != null;

                    boolean fDebugger = isDebuggerActive();

                    while (true)
                        {
                        if (fDebugger)
                            {
                            iPC = getDebugger().checkBreakPoint(frame, hException);
                            switch (iPC)
                                {
                                case Op.R_NEXT:
                                    break;

                                case Op.R_CALL:
                                    // the debugger made a natural call
                                    m_frameCurrent = frame.m_frameNext;
                                    frame.m_frameNext = null;
                                    frame = m_frameCurrent;

                                    aOp = frame.f_aOp;
                                    iPC = 0;
                                    continue nextOp;

                                case Op.R_EXCEPTION:
                                    // unwind the exception without stopping in the debugger
                                    fDebugger = false;
                                    break;

                                default:
                                    assert iPC >= 0 && frame.m_hException == null;

                                    // the debugger has handled (reported) the exception
                                    frame = m_frameCurrent = frame.f_framePrev;
                                    aOp   = frame.f_aOp;
                                    continue nextOp;
                                }
                            }

                        iPC = frame.findGuard(hException);
                        if (iPC >= 0)
                            {
                            // handled exception; go to the handler
                            m_frameCurrent = frame;
                            aOp = frame.f_aOp;
                            break;
                            }

                        // not handled by this frame
                        Frame frameCaller = frame.f_framePrev;
                        if (frameCaller != null)
                            {
                            frame = frameCaller;
                            frame.raiseException(hException);
                            continue;
                            }

                        // no one handled the exception, and we have reached the "proto-frame";
                        // it will process the exception
                        frame.raiseException(hException);
                        m_frameCurrent = frame;
                        aOp = frame.f_aOp;
                        iPC = frame.m_iPC + 1;
                        break;
                        }
                    break;
                    }

                case Op.R_REPEAT:
                    fiber.setStatus(FiberStatus.Waiting, cOps);
                    return frame;

                case Op.R_BLOCK:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Waiting, cOps);
                    return frame;

                case Op.R_PAUSE:
                    fiber.setStatus(FiberStatus.Paused, cOps);
                    return frame;

                default:
                    throw new IllegalStateException("Invalid code: " + iPC);
                }
            }
        }

    /**
     * Create a "proto"-frame.
     *
     * @param msg        the message to create a frame for
     * @param cReturns   the number of return values (-1 means a Tuple return)
     * @param aopNative  the underlying native ops
     *
     * @return a new Frame
     */
    protected Frame createServiceEntryFrame(Message msg, int cReturns, Op[] aopNative)
        {
        TypeConstant typeReturn;
        switch (cReturns)
            {
            case -1 -> {typeReturn = f_pool.typeTuple0(); cReturns = 1;}

            case 0  -> typeReturn = null;

            default -> typeReturn = f_pool.typeObject();
            }

        // create a pseudo frame that has variables to collect the return values
        ObjectHandle[] ahVar = new ObjectHandle[cReturns];

        Fiber fiber = createFiber(msg);
        Frame frame = new Frame(fiber, msg.f_iCallerPC, aopNative, ahVar, Op.A_IGNORE, null);

        for (int nVar = 0; nVar < cReturns; nVar++)
            {
            frame.f_aInfo[nVar] = frame.new VarInfo(typeReturn, Frame.VAR_STANDARD);
            }
        return frame;
        }

    /**
     * Create a new fiber for this service.
     *
     * @param msg  the message that caused the fiber creation
     *
     * @return a new fiber
     */
    protected Fiber createFiber(Message msg)
        {
        Fiber fiber = new Fiber(this, msg);
        f_setFibers.add(fiber);
        return fiber;
        }

    /**
     * Terminate the specified fiber.
     *
     * @param fiber  the fiber that has terminated
     *
     * @return a new fiber
     */
    protected void terminateFiber(Fiber fiber)
        {
        if (fiber == m_fiberSyncOwner)
            {
            // they somehow terminated the fiber without exiting the critical section;
            // should it be an exception?
            m_fiberSyncOwner = null;
            m_synchronicity  = Synchronicity.Concurrent;
            }

        if (fiber.hasPendingRequests())
            {
            fiber.setStatus(FiberStatus.Terminating, 0);
            }
        else
            {
            f_setFibers.remove(fiber);
            }
        }

    /**
     * Shut down all fibers.
     */
    public int shutdown(Frame frame)
        {
        if (m_hService != null)
            {
            // TODO: fire every registered ShuttingDownNotification

            // TODO MF: need a better lock to avoid messages getting into the queue after this point
            m_hService = null;

            FiberQueue qFiber = f_queueSuspended;

            // process all outstanding messages
            Message message;
            while ((message = f_queueMsg.poll()) != null)
                {
                qFiber.add(message.createFrame(this));
                }

            Set<Fiber> setFibers = f_setFibers;
            Fiber      fiberThis = frame == null ? null : frame.f_fiber;

            while (!qFiber.isEmpty())
                {
                Frame frameNext = qFiber.getAny();
                Fiber fiber     = frameNext.f_fiber;

                if (fiber != fiberThis)
                    {
                    fiber.setStatus(FiberStatus.Terminating, 0);

                    // this will respond immediately with an exception from "Fiber.prepareRun()"
                    execute(frameNext);

                    setFibers.remove(fiber);
                    }
                }

            assert setFibers.isEmpty() ||
                   setFibers.size() == 1 && setFibers.contains(fiberThis); // just this fiber left

            f_container.terminate(this);
            }

        return Op.R_NEXT;
        }


    // ----- x:Service methods ---------------------------------------------------------------------

    /**
     * @return the status indicator (the names must be congruent to natural Service.StatusIndicator)
     */
    public ServiceStatus getStatus()
        {
        // TODO: ShuttingDown is not currently supported

        if (m_hService == null)
            {
            return ServiceStatus.Terminated;
            }

        FiberStatus statusActive = null;
        for (Fiber fiber : f_setFibers)
            {
            statusActive = fiber.getStatus().moreActive(statusActive);
            }

        if (statusActive == null)
            {
            return ServiceStatus.Idle;
            }

        return switch (statusActive)
            {
            case Initial, Running, Paused -> ServiceStatus.Busy;
            case Waiting                  -> ServiceStatus.BusyWaiting;
            case Terminating              -> ServiceStatus.IdleWaiting;
            };
        }

    /**
     * A service is considered to be contended if it is running and if any other requests are
     * pending for the service.
     *
     * @return true iff the service is contended
     */
    public boolean isContended()
        {
        return m_frameCurrent != null || !f_queueResponse.isEmpty() ||
                !f_queueMsg.isEmpty() || f_queueSuspended.isReady();
        }

    /**
     * @return true iff the service is Idle
     */
    public boolean isIdle()
        {
        return getStatus() == ServiceStatus.Idle &&
            (m_atomicNotifications == null || m_atomicNotifications.get() == 0);
        }


    // ----- support for native notifications (e.g. timers, file listeners, etc. -------------------

    /**
     * Register a notification for this service.
     *
     * Note, that this method must be called on this service thread (fiber).
     */
    public void registerNotification()
        {
        AtomicLong counter = m_atomicNotifications;
        if (counter == null)
            {
            m_atomicNotifications = counter = new AtomicLong();
            }
        counter.getAndIncrement();
        }

    /**
     * Unregister a notification for this service. Note, that this method can be called on any thread.
     */
    public void unregisterNotification()
        {
        AtomicLong counter = m_atomicNotifications;
        assert counter != null;
        counter.getAndDecrement();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Check if all the arguments are pass-through from this service to the destination service;
     * replace the proxyable ones with the corresponding proxy handles.
     *
     * @param frame   the current frame
     * @param ctxDst  the service context that the arguments are to be sent to
     * @param atype   (optional) the declared type for the arguments, which, if specified, could be
     *                used to proxy the values
     * @param ahArg   the actual arguments
     *
     * @return Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION
     */
    public int validatePassThrough(Frame frame, ServiceContext ctxDst,
                                   TypeConstant[] atype, ObjectHandle[] ahArg)
        {
        // no need to check the container sharing unless we're crossing the container boundaries
        Container container = ctxDst.f_container == f_container ? null : ctxDst.f_container;

        return validatePassThroughArgs(frame, container, atype, ahArg, ahArg.length, 0);
        }

    /**
     * Same as the method above, but allows specifying the number of arguments.
     */
    public int validatePassThrough(Frame frame, ServiceContext ctxDst,
                                   TypeConstant[] atype, ObjectHandle[] ahArg, int cArgs)
        {
        Container container = ctxDst.f_container == f_container ? null : ctxDst.f_container;

        return validatePassThroughArgs(frame, container, atype, ahArg, cArgs, 0);
        }

    private int validatePassThroughArgs(Frame frame, Container container, TypeConstant[] atype,
                                        ObjectHandle[] ahArg, int cArgs, int ixStart)
        {
        for (int i = ixStart; i < cArgs; i++)
            {
            ObjectHandle hArg = ahArg[i];
            if (hArg == null)
                {
                // arguments tail is always empty
                break;
                }

            if (hArg.isPassThrough(container))
                {
                if (hArg instanceof FunctionHandle hFn)
                    {
                    // this service is returning a function; any call to that function will be
                    // executed in the context of this service
                    ahArg[i] = xRTFunction.makeAsyncDelegatingHandle(getService(), hFn);
                    }
                }
            else
                {
                int ix = i;
                if (hArg.getType().isA(frame.poolContext().typeAutoFreezable()))
                    {
                    return Utils.callFreeze(frame, hArg, null, frameCaller ->
                        {
                        ahArg[ix] = frameCaller.popStack();
                        return validatePassThroughArgs(
                                frameCaller, container, atype, ahArg, cArgs, ix+1);
                        });
                    }

                switch (hArg.getTemplate().createProxyHandle(
                            frame, this, hArg,
                            atype == null || atype.length == 0 ? null : atype[i]))
                    {
                    case Op.R_NEXT:
                        ahArg[i] = frame.popStack();
                        break;

                    case Op.R_CALL:
                        {
                        frame.m_frameNext.addContinuation(frameCaller ->
                            {
                            ahArg[ix] = frameCaller.popStack();
                            return validatePassThroughArgs(
                                    frameCaller, container, atype, ahArg, cArgs, ix+1);
                            });
                        return Op.R_CALL;
                        }

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            }
        return Op.R_NEXT;
        }

    /**
     * Post an asynchronous "call later" message to this context. Any exception thrown by the
     * called function will be reported as an "UnhandledExceptionNotification" (see Service.x).
     *
     * Unlike any of the "send*" methods below, there is no "originating" fiber in this case and
     * the future registration is done by the request itself.
     *
     * @return a CompletableFuture for the call or null if the service has terminated
     */
    public CompletableFuture<ObjectHandle> callLater(FunctionHandle hFunction, ObjectHandle[] ahArg)
        {
        CompletableFuture<ObjectHandle> future = postRequest(null, hFunction, ahArg, 0);

        if (future != null)
            {
            future.whenComplete((r, x) ->
                {
                if (x != null)
                    {
                    callUnhandledExceptionHandler(((WrapperException) x).getExceptionHandle());
                    }
                });
            }

        return future;
        }

    /**
     * Post an asynchronous "call later" message to this context.
     *
     * The caller is responsible for handling any potential exceptions thrown by the called
     * function, which would be provided via the returned CompletableFuture.
     *
     * @param frame  (optional) the caller's frame
     *
     * @return a CompletableFuture for the call or null if the service has terminated
     */
    public CompletableFuture<ObjectHandle> postRequest(Frame frame, FunctionHandle hFunction,
                                                       ObjectHandle[] ahArg, int cReturns)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return null;
            }

        Request request = new CallLaterRequest(frame, hFunction, ahArg, cReturns);

        // TODO: should we reject (throw) if the service overwhelmed?
        addRequest(request, true);

        return request.f_future;
        }

    /**
     * Send an asynchronous Op-based message to this context with one return value.
     *
     * @param frame   the caller's frame
     * @param op      the op to execute
     * @param iReturn the register id to place the result into
     * @param typeRet (optional) the expected type of the return value which, if specified, could
     *                be used to return a proxy
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendOp1Request(Frame frame, Op op, int iReturn, TypeConstant... typeRet)
        {
        assert iReturn != Op.A_IGNORE_ASYNC;

        OpRequest request = new OpRequest(frame, op, iReturn == Op.A_IGNORE ? 0 : 1, () -> typeRet);

        addRequest(request, frame.isDynamicVar(iReturn));

        frame.f_fiber.registerRequest(request);

        return frame.assignFutureResult(iReturn, request.f_future);
        }

    /**
     * Send an asynchronous "construct service" request to this context.
     *
     * @param frame  the caller's frame
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendConstructRequest(Frame frame, TypeComposition clazz,
                                    MethodStructure constructor,
                                    ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        Op opConstruct = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                xService service = (xService) clazz.getTemplate();

                return service.constructSync(frame, constructor, clazz, hParent, ahArg,
                        iReturn == A_IGNORE ? A_IGNORE : 0);
                }

            public String toString()
                {
                return "Construct: " + constructor.getContainingClass().getName();
                }
            };

        return sendOp1Request(frame, opConstruct, iReturn);
        }

    /**
     * Send an asynchronous "allocate structure" request to this context.
     *
     * @param frame  the caller's frame
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendAllocateRequest(Frame frame, TypeComposition clazz, ObjectHandle hParent, int iReturn)
        {
        assert iReturn != Op.A_IGNORE;

        Op opAllocate = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                xService service = (xService) (hParent != null && hParent.isService()
                        ? hParent.getService().getTemplate()
                        : clazz.getTemplate());

                return service.allocateSync(frame, clazz, hParent, 0);
                }

            public String toString()
                {
                return "Allocate: " + ServiceContext.this.f_sName;
                }
            };

        return sendOp1Request(frame, opAllocate, iReturn);
        }

    /**
     * Send an asynchronous "invoke" request with zero or one return value.
     *
     * @param frame    the caller's frame
     * @param fTuple   if true, the tuple is expected as a result of async execution
     * @param iReturn  a register id ({@link Op#A_IGNORE_ASYNC} for fire and forget)
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendInvoke1Request(Frame frame, FunctionHandle hFunction,
                                  ObjectHandle hTarget, ObjectHandle[] ahArg, boolean fTuple, int iReturn)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frame.raiseException(xException.serviceTerminated(frame, f_sName));
            }

        boolean fAsync;
        boolean fHandleExceptions;
        int     cReturns;
        switch (iReturn)
            {
            case Op.A_IGNORE_ASYNC ->
                {
                assert !fTuple;
                fAsync   = fHandleExceptions = true;
                cReturns = 0;
                }

            case Op.A_IGNORE ->
                {
                assert !fTuple;
                fAsync   = fHandleExceptions = false;
                cReturns = 0;
                }

            default ->
                {
                fAsync            = frame.isDynamicVar(iReturn);
                fHandleExceptions = false;
                cReturns          = fTuple ? -1 : 1;
                }
            }

        Op opCall = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                return switch (cReturns)
                    {
                    case -1 -> hFunction.callT(frame, hTarget, ahArg, 0);
                    case 0  -> hFunction.call1(frame, hTarget, ahArg, A_IGNORE);
                    case 1  -> hFunction.call1(frame, hTarget, ahArg, 0);
                    default -> throw new IllegalStateException();
                    };
                }

            public String toString()
                {
                return hFunction.getMethodId().getPathString();
                }
            };


        Supplier<TypeConstant[]> supplier = cReturns == 0
                ? null
                : () -> resolveFormalReturnTypes(hFunction, ahArg);
        OpRequest request = new OpRequest(frame, opCall, cReturns, supplier);

        boolean fOverwhelmed = addRequest(request, fAsync);

        Fiber                           fiber  = frame.f_fiber;
        CompletableFuture<ObjectHandle> future = request.f_future;

        if (fHandleExceptions)
            {
            // in the case of an ignored return and underwhelmed queue - fire and forget
            if (!fOverwhelmed)
                {
                if (future.isDone())
                    {
                    return frame.assignFutureResult(iReturn, future);
                    }
                fiber.registerUncapturedRequest(request);
                return Op.R_NEXT;
                }

            // consider not to block the caller if it is *not* the reason of the callee being
            // overwhelmed, which would require some additional knowledge being retained
            }

        fiber.registerRequest(request);

        return frame.assignFutureResult(iReturn, future);
        }

    /**
     * Send an asynchronous "invoke" request with multiple return values.
     *
     * @param frame  the caller's frame
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendInvokeNRequest(Frame frame, FunctionHandle hFunction,
                                  ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frame.raiseException(xException.serviceTerminated(frame, f_sName));
            }

        int cReturns = aiReturn.length;
        Op  opCall   = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                // the pseudo-frame's vars are the return values
                int[] aiReturn = new int[cReturns];
                for (int i = 0; i < cReturns; i++)
                    {
                    aiReturn[i] = i;
                    }

                return hFunction.callN(frame, hTarget, ahArg, aiReturn);
                }

            public String toString()
                {
                return hFunction.getMethodId().getPathString();
                }
            };

        Supplier<TypeConstant[]>          supplier = () -> resolveFormalReturnTypes(hFunction, ahArg);
        OpRequest                         request  = new OpRequest(frame, opCall, cReturns, supplier);
        CompletableFuture<ObjectHandle[]> future   = request.f_future;

        boolean fOverwhelmed = addRequest(request, false);

        Fiber fiber = frame.f_fiber;
        if (cReturns == 0)
            {
            fiber.registerUncapturedRequest(request);
            return fOverwhelmed || future.isDone()
                ? frame.assignFutureResult(Op.A_IGNORE, (CompletableFuture) future)
                : Op.R_NEXT;
            }

        fiber.registerRequest(request);
        if (cReturns == 1)
            {
            CompletableFuture<ObjectHandle> cfReturn =
                    future.thenApply(ahResult -> ahResult[0]);
            return frame.assignFutureResult(aiReturn[0], cfReturn);
            }

        // TODO replace with: assignFutureResults()
        return frame.call(frame.createWaitFrame(future, aiReturn));
        }

    /**
     * Helper method to resolve the formal type parameters in the function's return type.
     *
     * This method is only called if the value returned by the service is not immutable and needs
     * to be proxied. In that case the actual return value type could be used to resolve generic
     * return types (see ClassTemplate.createProxyHandle), but formal type parameters can only be
     * resolved using the type parameters types that are passed in by the caller.
     */
    private TypeConstant[] resolveFormalReturnTypes(FunctionHandle hFunction, ObjectHandle[] ahArg)
        {
        TypeConstant[] atype  = hFunction.getReturnTypes();
        int            cTypes = atype.length;

        if (cTypes > 0)
            {
            GenericTypeResolver resolver = new GenericTypeResolver()
                {
                @Override
                public TypeConstant resolveGenericType(String sFormalName)
                    {
                    return null;
                    }

                @Override
                public TypeConstant resolveFormalType(FormalConstant constFormal)
                    {
                    if (constFormal instanceof TypeParameterConstant constTypeParam)
                        {
                        int             nRegister = constTypeParam.getRegister();
                        MethodConstant  idMethod  = hFunction.getMethodId();
                        MethodStructure method    = idMethod == null
                                ? null
                                : (MethodStructure) idMethod.getComponent();
                        if (method != null && nRegister < method.getTypeParamCount() &&
                                method.getParam(nRegister).getName().equals(constTypeParam.getName()))
                            {
                            return ahArg[nRegister].getType().getParamType(0);
                            }
                        }

                    return null;
                    }
                };

            boolean fClone = true;
            for (int i = 0; i < cTypes; i++)
                {
                TypeConstant type = atype[i];
                if (type.containsTypeParameter(true))
                    {
                    TypeConstant typeResolved = type.resolveGenerics(f_pool, resolver);
                    if (typeResolved != type)
                        {
                        if (fClone)
                            {
                            atype = atype.clone();
                            }
                        atype[i] = typeResolved;
                        }
                    }
                }
            }
        return atype;
        }

    /**
     * Send an asynchronous property "read" operation request.
     *
     * @param frame  the caller's frame
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendProperty01Request(Frame frame, ObjectHandle hTarget,
                                     PropertyConstant idProp, int iReturn, PropertyOperation01 op)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frame.raiseException(xException.serviceTerminated(frame, f_sName));
            }

        Op opGet = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                int iResult = op.invoke(frame, hTarget, idProp,
                                iReturn == A_IGNORE ? A_IGNORE : 0);

                // don't return a FutureHandle, but wait till it's done
                return idProp.isFutureVar() && iResult == Op.R_NEXT
                    ? ((FutureHandle) frame.f_ahVar[0]).waitAndAssign(frame, 0)
                    : iResult;
                }

            public String toString()
                {
                return idProp.getPathString();
                }
            };

        return sendOp1Request(frame, opGet, iReturn, idProp.getType());
        }

    /**
     * Send an asynchronous property "update" operation request. The caller will be blocked until
     * the asynchronous operation completes. This method could is also used to update Var referents.
     *
     * @param idProp if null, this request updates a Var referent; property value otherwise
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendProperty10Request(Frame frame, ObjectHandle hTarget,
                                     PropertyConstant idProp, ObjectHandle hValue, PropertyOperation10 op)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frame.raiseException(xException.serviceTerminated(frame, f_sName));
            }

        // no need to check the container sharing unless we're crossing the container boundaries
        Container containerDst = frame.f_context.f_container == f_container ? null : f_container;

        if (hValue.isPassThrough(containerDst))
            {
            return completeSendProperty10(frame, hTarget, idProp, hValue, op);
            }

        if (idProp == null)
            {
            return frame.raiseException(xException.mutableObject(frame, hValue.getType()));
            }

        ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
        TypeConstant[] atype   = new TypeConstant[] {idProp.getType()};
        switch (frame.f_context.validatePassThrough(frame, this, atype, ahValue, 1))
            {
            case Op.R_NEXT:
                return completeSendProperty10(frame, hTarget, idProp, ahValue[0], op);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeSendProperty10(frameCaller, hTarget, idProp, ahValue[0], op));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    private int completeSendProperty10(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                       ObjectHandle hPassValue, PropertyOperation10 op)
        {
        Op opSet = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                return op.invoke(frame, hTarget, idProp, hPassValue);
                }

            public String toString()
                {
                return idProp == null ? "<Referent>" : idProp.getPathString();
                }
            };

        return sendOp1Request(frame, opSet, Op.A_IGNORE);
        }

    /**
     * Send an asynchronous "constant initialization" message.
     */
    public CompletableFuture<ObjectHandle> sendConstantRequest(Frame frame,
                                                               List<SingletonConstant> listConstants)
        {
        Op opInit = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                return Utils.initConstants(frame, listConstants,
                    frameCaller -> frameCaller.assignValue(0, xNullable.NULL));
                }

            public String toString()
                {
                return "StaticInitializationRequest";
                }
            };

        OpRequest request = new OpRequest(frame, opInit, 1, null);

        addRequest(request, false);

        return request.f_future;
        }

    protected void callUnhandledExceptionHandler(ExceptionHandle hException)
        {
        FunctionHandle hFunction = m_hExceptionHandler;
        if (hFunction == null)
            {
            hFunction = new NativeFunctionHandle((frame, ahArg, iReturn) ->
                {
                switch (Utils.callToString(frame, ahArg[0]))
                    {
                    case Op.R_NEXT ->
                        {
                        Utils.log(frame, "\nUnhandled exception: " +
                            ((StringHandle) frame.popStack()).getStringValue());
                        return Op.R_NEXT;
                        }

                    case Op.R_CALL ->
                        {
                        frame.m_frameNext.addContinuation(frameCaller ->
                            {
                            Utils.log(frameCaller, "\nUnhandled exception: " +
                                ((StringHandle) frameCaller.popStack()).getStringValue());
                            return Op.R_NEXT;
                            });
                        return Op.R_CALL;
                        }

                    default -> throw new IllegalStateException();
                    }
                });
            }

        // ignore any exception coming out of the handler
        postRequest(null, hFunction, new ObjectHandle[]{hException}, 0);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Service \"")
          .append(f_sName)
          .append("\" (id=")
          .append(f_lId)
          .append(')');

        if (m_synchronicity != Synchronicity.Concurrent)
            {
            sb.append(" ")
              .append(m_synchronicity.name());
            }
        if (m_frameCurrent != null)
            {
            sb.append(" ")
              .append(m_frameCurrent);
            }
        return sb.toString();
        }


    // --- inner classes ---------------------------------------------------------------------------

    /**
     * Base class for cross-service communications.
     */
    public abstract static class Message
        {
        public final Fiber           f_fiberCaller;
        public final MethodStructure f_fnCaller;
        public final int             f_iCallerId; // the FrameId of the caller
        public final int             f_iCallerPC; // the PC of the caller

        protected Message(Frame frameCaller)
            {
            if (frameCaller == null)
                {
                f_fiberCaller = null;
                f_fnCaller    = null;
                f_iCallerId   = 0;
                f_iCallerPC   = -1;
                }
            else
                {
                f_fiberCaller = frameCaller.f_fiber;
                f_fnCaller    = frameCaller.f_function;
                f_iCallerId   = frameCaller.f_iId;
                f_iCallerPC   = frameCaller.m_iPC;
                }
            }

        abstract Frame createFrame(ServiceContext context);
        }

    /**
     * Base class for an asynchronous cross-service Message based on a CompletableFuture.
     */
    public abstract static class Request
            extends Message
        {
        protected Request(Frame frameCaller)
            {
            super(frameCaller);

            f_future = new CompletableFuture();
            }

        /**
         * The CompletableFuture associates with this request.
         */
        public final CompletableFuture f_future;
        /**
         * The Fiber this request runs on.
         */
        public Fiber m_fiber;

        /**
         * Send the specified number of return values back to the caller.
         */
        protected void sendResponse(Fiber fiberCaller, Frame frame, CompletableFuture future, int cReturns)
            {
            ServiceContext ctxDst = fiberCaller.f_context;

            switch (cReturns)
                {
                case 0:
                    ctxDst.respond(new Response<ObjectHandle>(
                            fiberCaller, xTuple.H_VOID, frame.m_hException, future));
                    break;

                case 1:
                    {
                    ObjectHandle    hReturn    = frame.f_ahVar[0];
                    ExceptionHandle hException = frame.m_hException;

                    ctxDst.respond(
                            new Response<ObjectHandle>(fiberCaller, hReturn, hException, future));
                    break;
                    }

                case -1: // tuple return
                    {
                    ObjectHandle[]  ahReturn   = frame.f_ahVar;
                    ExceptionHandle hException = frame.m_hException;
                    TupleHandle     hTuple     = hException == null ? (TupleHandle) ahReturn[0] : null;

                    ctxDst.respond(
                            new Response<ObjectHandle>(fiberCaller, hTuple, hException, future));
                    break;
                    }

                default:
                    {
                    assert cReturns > 1;
                    ObjectHandle[]  ahReturn   = frame.f_ahVar;
                    ExceptionHandle hException = frame.m_hException;

                    if (hException == null)
                        {
                        for (int i = 0, c = ahReturn.length; i < c; i++)
                            {
                            ObjectHandle hReturn = ahReturn[i];
                            if (hReturn == null)
                                {
                                // this is only possible for a conditional return of "False"
                                assert i > 0 && ahReturn[0].equals(xBoolean.FALSE);

                                // since "null" indicates a deferred future value, replace it with
                                // the DEFAULT value (see Utils.GET_AND_RETURN)
                                ahReturn[i] = ObjectHandle.DEFAULT;
                                }
                            }
                        }
                    ctxDst.respond(
                            new Response<ObjectHandle[]>(fiberCaller, ahReturn, hException, future));
                    break;
                    }
                }
            }
        }

    /**
     * A cross-service Op based Request.
     */
    public static class OpRequest
            extends Request
        {
        /**
         * @param supplierRet  (optional) the supplier of return types to be used *only* if the
         *                     request's return values need to be proxied
         */
        protected OpRequest(Frame frameCaller, Op op, int cReturns, Supplier<TypeConstant[]> supplierRet)
            {
            super(frameCaller);

            f_op          = op;
            f_cReturns    = cReturns;
            f_supplierRet = supplierRet;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opCheck = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return checkResponse(f_fiberCaller, frame, f_cReturns, 0);
                    }
                @Override
                public String toString()
                    {
                    return "Check";
                    }
                };

            Op opRespond = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    sendResponse(f_fiberCaller, frame, f_future, f_cReturns);
                    return iPC + 1;
                    }
                @Override
                public String toString()
                    {
                    return "Respond";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                        new Op[]{f_op, opCheck, opRespond, Return_0.INSTANCE});

            m_fiber = frame0.f_fiber;
            return frame0;
            }

        /**
         * Check the response values and call "freeze" or create proxies if necessary.
         */
        protected int checkResponse(Fiber fiberCaller, Frame frame, int cReturns, int index)
            {
            ServiceContext ctxSrc = frame.f_context;
            ServiceContext ctxDst = fiberCaller.f_context;

            switch (cReturns)
                {
                case 0:
                    break;

                case 1:
                    if (frame.m_hException == null)
                        {
                        int iResult = ctxSrc.validatePassThrough(frame, ctxDst,
                                        f_supplierRet == null ? null : f_supplierRet.get(),
                                        frame.f_ahVar, 1);
                        if (iResult == Op.R_EXCEPTION)
                            {
                            Arrays.fill(frame.f_ahVar, null);
                            }
                        return iResult;
                        }
                    break;

                case -1: // tuple return
                    {
                    ObjectHandle[]  ahReturn   = frame.f_ahVar;
                    ExceptionHandle hException = frame.m_hException;
                    if (hException == null)
                        {
                        TupleHandle hTuple = (TupleHandle) ahReturn[0];
                        if (hTuple != null)
                            {
                            ObjectHandle[] ahValue = hTuple.m_ahValue;
                            switch (ctxSrc.validatePassThrough(frame, ctxDst, null, ahValue))
                                {
                                case Op.R_NEXT:
                                    // all values are pass-through; mark the tuple itself immutable;
                                    // we can do it since this tuple was created automatically by
                                    // "frame.assignTuple()" method
                                    hTuple.makeImmutable();
                                    break;

                                case Op.R_CALL:
                                    frame.m_frameNext.addContinuation(frameCaller ->
                                        {
                                        hTuple.makeImmutable();
                                        return Op.R_NEXT;
                                        });
                                    return Op.R_CALL;

                                case Op.R_EXCEPTION:
                                    Arrays.fill(ahValue, null);
                                    return Op.R_EXCEPTION;

                                default:
                                    throw new IllegalStateException();
                                }
                            }
                        }
                    break;
                    }

                default:
                    {
                    assert cReturns > 1;
                    ObjectHandle[]  ahReturn   = frame.f_ahVar;
                    ExceptionHandle hException = frame.m_hException;

                    if (hException == null)
                        {
                        for (int i = index; i < cReturns; i++)
                            {
                            ObjectHandle hReturn = ahReturn[i];
                            if (hReturn == null)
                                {
                                // this is only possible for a conditional return of "False"
                                assert i > 0 && ahReturn[0].equals(xBoolean.FALSE);

                                // since "null" indicates a deferred future value, replace it with
                                // the DEFAULT value (see Utils.GET_AND_RETURN)
                                ahReturn[i] = ObjectHandle.DEFAULT;
                                }
                            }

                        int iResult = ctxSrc.validatePassThrough(frame, ctxDst,
                                        f_supplierRet == null ? null : f_supplierRet.get(),
                                        ahReturn, cReturns);
                        if (iResult == Op.R_EXCEPTION)
                            {
                            Arrays.fill(ahReturn, null);
                            }
                        return iResult;
                        }
                    break;
                    }
                }
            return Op.R_NEXT;
            }

        @Override
        public String toString()
            {
            return f_op.toString();
            }

        private final Op                       f_op;
        private final int                      f_cReturns;
        private final Supplier<TypeConstant[]> f_supplierRet;
        }

    /**
     * Represents a natural "fire and forget" or a native call request to a service.
     */
    public static class CallLaterRequest
            extends Request
        {
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int            f_cReturns;

        public CallLaterRequest(Frame frameCaller, FunctionHandle hFunction, ObjectHandle[] ahArg,
                                int cReturns)
            {
            super(frameCaller);

            assert cReturns <= 1; // multiple returns are not supported for now

            f_hFunction = hFunction;
            f_ahArg     = ahArg;
            f_cReturns  = cReturns;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return f_hFunction.call1(frame, null, f_ahArg, f_cReturns == 0 ? A_IGNORE : 0);
                    }

                public String toString()
                    {
                    return "CallLaterRequest: " + f_hFunction.getName();
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                    new Op[] {opCall, Return_0.INSTANCE});

            m_fiber = frame0.f_fiber;

            if (f_fiberCaller == null)
                {
                // since there was no originating fiber, we need to register the request on-the-spot
                frame0.f_fiber.registerRequest(this);

                frame0.addContinuation(_null ->
                    {
                    // "callLater" has returned
                    ExceptionHandle hException = frame0.m_hException;
                    if (hException == null)
                        {
                        f_future.complete(f_cReturns == 0 ? xTuple.H_VOID : frame0.f_ahVar[0]);
                        }
                    else
                        {
                        f_future.completeExceptionally(hException.getException());
                        }
                    return Op.R_NEXT;
                    });
                }
            else
                {
                frame0.addContinuation(_null ->
                    {
                    sendResponse(f_fiberCaller, frame0, f_future, f_cReturns);
                    return Op.R_NEXT;
                    });
                }

            return frame0;
            }
        }

    /**
     * Represents a service call return.
     */
    public static class Response<T>
            implements Runnable
        {
        private final Fiber                f_fiberCaller;
        private final T                    f_return;
        private final ExceptionHandle      f_hException;
        private final CompletableFuture<T> f_future;

        public Response(Fiber fiberCaller, T returnValue, ExceptionHandle hException,
                        CompletableFuture<T> future)
            {
            assert returnValue != null || hException != null;

            f_fiberCaller = fiberCaller;
            f_hException  = hException;
            f_return      = returnValue;
            f_future      = future;
            }

        @Override
        public void run()
            {
            f_fiberCaller.onResponse();

            if (f_hException == null)
                {
                f_future.complete(f_return);
                }
            else
                {
                f_future.completeExceptionally(f_hException.getException());
                }
            }
        }

    /**
     * The wake-up scheduler.
     */
    protected class WakeUpScheduler
        {
        protected void schedule(long ldtWakeUp)
            {
            long ldtNow = System.currentTimeMillis();
            if (f_ldtScheduled > 0)
                {
                if (ldtNow <= f_ldtScheduled && f_ldtScheduled <= ldtWakeUp)
                    {
                    // the current wake up covers the new one; nothing to do
                    return;
                    }

                if (ldtNow < f_ldtScheduled)
                    {
                    m_taskCurrent.cancel();
                    }
                }

            f_ldtScheduled = ldtWakeUp;
            m_taskCurrent  = new TimerTask()
                {
                public void run()
                    {
                    ensureScheduled(false);
                    }
                };

            xLocalClock.TIMER.schedule(m_taskCurrent, Math.max(1, ldtWakeUp - ldtNow));
            }

        private long      f_ldtScheduled; // when
        private TimerTask m_taskCurrent;  // what
        }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * The container this service belongs to.
     */
    public final Container f_container;

    /**
     * The queue size threshold at which the caller should be pushed back.
     */
    public static final int QUEUE_THRESHOLD = 256;

    /**
     * The container's ConstantPool.
     */
    public final ConstantPool f_pool;

    /**
     * The service id.
     */
    public final long f_lId;

    /**
     * The service name.
     */
    public final String f_sName;

    /**
     * The service handle.
     */
    private ServiceHandle m_hService;

    /**
     * The unhandled exception notification.
     */
    public FunctionHandle m_hExceptionHandler;

    /**
     * The counter used to create Frame ids
     */
    protected int m_iFrameCounter;

    /**
     * The current Timeout that will be used by the service when it invokes other services.
     */
    private ObjectHandle m_hTimeout = xNullable.NULL;

    /**
     * The current SynchronizedSection for the service.
     */
    private ObjectHandle m_hSynchronizedSection = xNullable.NULL;

    /**
     * Metrics: the total time (in nanos) this service has been running.
     */
    protected long m_cRuntimeNanos;

    /**
     * Support for Clock adn Timer: the count of pending timer events.
     */
    protected AtomicLong m_atomicNotifications;

    /**
     * The current frame.
     */
    private Frame m_frameCurrent;

    /**
     * The queue of incoming messages.
     */
    private final Queue<Message> f_queueMsg = new ConcurrentLinkedQueue<>();

    /**
     * The queue of message responses.
     */
    private final Queue<Response> f_queueResponse = new ConcurrentLinkedQueue<>();

    /**
     * The set of active fibers. It can be [read] accessed by outside threads.
     */
    private final Set<Fiber> f_setFibers = new ConcurrentSkipListSet<>();

    /**
     * The queue of suspended fibers.
     */
    private final FiberQueue f_queueSuspended = new FiberQueue(this);

    /**
     * The reentrancy policy. Must be the same names as in natural Service.Synchronicity.
     */
    public enum Synchronicity {Concurrent, Synchronized, Critical}

    /**
     * The current value of service synchronicity.
     */
    private Synchronicity m_synchronicity = Synchronicity.Concurrent;

    /**
     * If specified, indicates the current synchronized section owner.
     */
    private Fiber m_fiberSyncOwner;

    /**
     * The context scheduling "lock", atomic operations are performed via {@link #SCHEDULING_LOCK_HANDLE}.
     * <p>
     * This lock must be acquired in order to schedule context processing and is not released until
     * the context is no longer scheduled.
     * <p>
     * The lock is implemented as a volatile counter, the thread which transitions from 0 to 1 becomes
     * the lock holder will release the lock by setting back via a getAndSet(0), which, if it yields
     * a prior value of something other than 1, indicates the lock contention.
     */
    volatile long m_lLockScheduling;

    /**
     * The current service status. Must be the same names as in natural Service.StatusIndicator.
     */
    public enum ServiceStatus
        {
        Idle,
        IdleWaiting,
        Busy,
        BusyWaiting,
        ShuttingDown,
        Terminated
        }

    /**
     * The context served by the current thread.
     */
    static final ThreadLocal<ServiceContext[]> s_tloContext = ThreadLocal.withInitial(() -> new ServiceContext[1]);

    /**
     * VarHandle for {@link #m_lLockScheduling}.
     */
    static final VarHandle SCHEDULING_LOCK_HANDLE;
    static
        {
        try
            {
            SCHEDULING_LOCK_HANDLE = MethodHandles.lookup().findVarHandle(ServiceContext.class,
                "m_lLockScheduling", long.class);
            }
        catch (IllegalAccessException | NoSuchFieldException e)
            {
            throw new IllegalStateException(e);
            }
        }

    /**
     * A "service-local" cache for run-time information that needs to be calculated by various ops.
     * Since only one fiber can access the service context at any time, a simple HashMap is used.
     * To prevent leaks, the values in the EnumMap are WekRef objects.
     */
    private final Map<Op, EnumMap> f_mapOpInfo = new WeakHashMap<>();

    /**
     * A "service-local" cache for transient field values.
     */
    private Map<TransientId, ObjectHandle> m_mapTransient;

    /**
     * A "service-local" cache for service callbacks.
     */
    private Map<Long, FunctionHandle> m_mapCallbacks;

    /**
     * A wake-up scheduler to process registered timeouts.
     */
    private final WakeUpScheduler f_wakeUpScheduler = new WakeUpScheduler();
    }