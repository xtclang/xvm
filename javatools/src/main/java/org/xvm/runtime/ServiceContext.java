package org.xvm.runtime;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimerTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Return_0;

import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle.WrapperException;

import org.xvm.runtime.template.xBoolean;
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

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.runtime.template._native.temporal.xLocalClock;


/**
 * The service context.
 */
public class ServiceContext
    {
    ServiceContext(Container container, ConstantPool pool, String sName, int nId)
        {
        f_container     = container;
        f_sName         = sName;
        f_nId           = nId;
        f_templates     = container.f_templates;
        f_pool          = pool;
        f_queueMsg      = new ConcurrentLinkedQueue<>();
        f_queueResponse = new ConcurrentLinkedQueue<>();
        m_hTimeout      = xNullable.NULL;
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
        assert m_hService == null ||
            m_hService.isStruct() && !hService.isStruct();
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
     * Supporting method for natural Service.registerCriticalSection() API.
     *
     * @return the current CriticalSection? handle
     */
    public ObjectHandle getCriticalSection()
        {
        return m_hCriticalSection;
        }

    /**
     * Supporting method for natural Service.registerCriticalSection() API.
     *
     * @param hCriticalSection  the new CriticalSection? handle
     */
    public void setCriticalSection(ObjectHandle hCriticalSection)
        {
        assert hCriticalSection != null;

        m_hCriticalSection = hCriticalSection;
        }

    /**
     * @return the currently active frame
     */
    public Frame getCurrentFrame()
        {
        return m_frameCurrent;
        }

    /**
     * @return the ServiceContext associated with the current Java thread
     */
    public static ServiceContext getCurrentContext()
        {
        return s_tloContext.get()[0];
        }

    /**
     * @return the reentrancy policy
     */
    public Reentrancy getReentrancy()
        {
        return m_reentrancy;
        }

    /**
     * Set the reentrancy policy.
     */
    public void setReentrancy(Reentrancy reentrancy)
        {
        m_reentrancy = reentrancy;
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
        return mapByCategory == null ? null : mapByCategory.get(category);
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
                   .put(category, info);
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
                try (var x = ConstantPool.withPool(frame.poolContext()))
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
                    // TODO: RTError
                    frame = getCurrentFrame();
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
            tloCtx[0] = ctxPrior;

            if (ctxPrior != null)
                {
                // now that we've switched back to the caller's service context process any responses
                // which may have arrived
                ctxPrior.processResponses();
                }
            }

        return true;
        }

    /**
     * Ensure this service is scheduled for processing.
     */
    protected void ensureScheduled()
        {
        if (tryAcquireSchedulingLock())
            {
            // try to complete processing locally if possible
            execute();
            }
        // else; already scheduled
        }

    /**
     * Execute any outstanding work for this service.
     */
    public void execute()
        {
        if (drainWork())
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
        // If isContended is true then the service requires more processing and we can immediately
        // reschedule, thus transferring our lock ownership. Between checking that state and releasing
        // the lock isContended could transition to true and the thread doing that transition would
        // fail to get our yet to be released lock. We could defend against this by checking isContended
        // after releasing but portions (FiberQueue) of isContended are not thread-safe and thus we
        // shouldn't query it after releasing the lock. Instead we defend against this by detecting
        // contention on the lock itself and inferring that the contending thread must have injected
        // more work and thus we must reschedule on their behalf. Note if we're wrong and the contention
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
     * @param msg  the request message
     *
     * @return true if the service has become "overwhelmed" - too many outstanding messages
     */
    public boolean addRequest(Message msg)
        {
        f_queueMsg.add(msg);
        ensureScheduled();
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
     * Add a message to the service request queue.
     *
     * @param response  the response message
     */
    public void respond(Response response)
        {
        f_queueResponse.add(response);
        ensureScheduled();
        }

    protected void processResponses()
        {
        Queue<Response> qResponse = f_queueResponse;
        Response response;
        while ((response = qResponse.poll()) != null)
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
    public Frame nextFiber()
        {
        // responses have the highest priority and no natural code runs there;
        // process all we've got so far
        processResponses();

        // pickup all the messages, but keep them in the "initial" state
        Queue<Message> qMsg = f_queueMsg;
        Message message;
        while ((message = qMsg.poll()) != null)
            {
            f_queueSuspended.add(message.createFrame(this));
            }

        // allow initial timeouts to be processed always, since they won't run any natural code
        // TODO: return ?f_queueSuspended.getInitialTimeout();

        Frame frameCurrent = m_frameCurrent;
        if (frameCurrent != null)
            {
            // resume the paused frame (could be forbidden reentrancy)
            return frameCurrent.f_fiber.isReady() ? frameCurrent : null;
            }

        FiberQueue qSuspended = f_queueSuspended;
        if (qSuspended.isEmpty())
            {
            // nothing to do
            return null;
            }

        switch (m_reentrancy)
            {
            default:
            case Forbidden:
                throw new IllegalStateException(); // assert

            case Exclusive:
            case Prioritized:
                return qSuspended.getAssociatedOrYielded();

            case Open:
                return qSuspended.getAnyReady();
            }
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
            case Running:
                throw new IllegalStateException();

            case InitialNew:
            case InitialAssociated:
                f_queueSuspended.add(frame);
                break;

            case Waiting:
            case Yielded:
                if (m_reentrancy == Reentrancy.Forbidden)
                    {
                    m_frameCurrent = frame;
                    }
                else
                    {
                    m_frameCurrent = null;
                    f_queueSuspended.add(frame);
                    }
                break;

            case Paused:
                // we must resume this frame
                m_frameCurrent = frame;
                break;
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

            case Op.R_BLOCK:
                // there are still some "waiting" registers
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

                if (++cOps > 100)
                    {
                    if (frame.f_nDepth > 100)
                        {
                        iPC = frame.raiseException("Stack overflow");
                        break;
                        }
                    fiber.setStatus(FiberStatus.Paused, cOps);
                    return frame;
                    }

                iPC = aOp[iPC].process(frame, iPCLast = iPC);

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
                                continue nextOp;

                            case Op.R_CALL:
                                assert frame.m_frameNext != null;

                                m_frameCurrent = frame.m_frameNext;
                                frame.m_frameNext = null;
                                frame = m_frameCurrent;

                                aOp = frame.f_aOp;
                                iPC = 0;
                                continue nextOp;

                            case Op.R_RETURN:
                                iPC = Op.R_RETURN;
                                continue nextOp;

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
                        return null;
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

                    if (isDebuggerActive())
                        {
                        getDebugger().checkBreakPoint(frame, hException);
                        }

                    while (true)
                        {
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
                            continue;
                            }

                        // no one handled the exception and we have reached the "proto-frame";
                        // it will process the exception
                        if (frame.m_continuation == null)
                            {
                            throw new IllegalStateException(
                                "Proto-frame is missing the continuation: " + hException);
                            }

                        frame.raiseException(hException);

                        switch (frame.m_continuation.proceed(null))
                            {
                            case Op.R_NEXT:
                                // this fiber is done
                                break;

                            default:
                                // the proto-frame never calls anything naturally nor throws
                                throw new IllegalStateException();
                            }

                        terminateFiber(fiber);
                        return m_frameCurrent = null;
                        }
                    break;
                    }

                case Op.R_REPEAT:
                    frame.m_iPC = iPCLast;
                    fiber.setStatus(FiberStatus.Waiting, cOps);
                    return frame;

                case Op.R_BLOCK:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Waiting, cOps);
                    return frame;

                case Op.R_YIELD:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Yielded, cOps);
                    return frame;

                case Op.R_PAUSE:
                    frame.m_iPC = iPCLast;
                    fiber.setStatus(FiberStatus.Paused, cOps);
                    return frame;

                case Op.R_DEBUG:
                    iPC = getDebugger().enter(frame, iPCLast);
                    if (iPC == Op.R_NEXT)
                        {
                        iPC = iPCLast + 1;
                        }
                    break;

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
            case -1:
                cReturns   = 1;
                typeReturn = f_pool.typeTuple0();
                break;

            case 0:
                typeReturn = null;
                break;

            default:
                typeReturn = f_pool.typeObject();
                break;
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

            Queue<Message> qMsg   = f_queueMsg;
            FiberQueue     qFiber = f_queueSuspended;

            // process all outstanding messages
            Message message;
            while ((message = qMsg.poll()) != null)
                {
                qFiber.add(message.createFrame(this));
                }

            Set<Fiber> setFibers = f_setFibers;
            Fiber      fiberThis = frame.f_fiber;

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

            assert setFibers.size() == 1 && setFibers.contains(fiberThis); // just this fiber left
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

        switch (statusActive)
            {
            case InitialNew:
            case InitialAssociated:
            case Running:
            case Paused:
            case Yielded:
                return ServiceStatus.Busy;

            case Waiting:
                return ServiceStatus.BusyWaiting;

            case Terminating:
                return ServiceStatus.IdleWaiting;

            default:
                throw new IllegalStateException();
            }
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
     * Unregister a notification for this service. Note, that this method can be
     * called on on any thread.
     */
    public void unregisterNotification()
        {
        AtomicLong counter = m_atomicNotifications;
        assert counter != null;
        counter.getAndDecrement();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Post and asynchronous "call later" message to this context. Any exception thrown by the
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
     * Post and asynchronous "call later" message to this context.
     *
     * The caller is responsible for handling any potential exceptions thrown by the called
     * function, which would be provided via the returned CompletableFuture.
     *
     * @param frameCaller  (optional) the caller's frame
     *
     * @return a CompletableFuture for the call or null if the service has terminated
     */
    public CompletableFuture<ObjectHandle> postRequest(
            Frame frameCaller, FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return null;
            }

        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        // TODO: should we reject (throw) if the service overwhelmed?
        addRequest(new CallLaterRequest(frameCaller, hFunction, ahArg, cReturns, future));

        return future;
        }

    /*
     * Send and asynchronous Op-based message to this context with one return value. The caller
     * will be blocked until the asynchronous operation completes.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendOp1Request(Frame frameCaller, Op op, int iReturn)
        {
        assert iReturn != Op.A_IGNORE_ASYNC;

        OpRequest request = new OpRequest(frameCaller, op, iReturn == Op.A_IGNORE ? 0 : 1);

        CompletableFuture<ObjectHandle> future = request.f_future;

        addRequest(request);

        frameCaller.f_fiber.registerRequest(future);

        return frameCaller.assignFutureResult(iReturn, future);
        }

    /*
     * Send and asynchronous "construct service" request to this context. The caller
     * will be blocked until the asynchronous construction completes.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendConstructRequest(Frame frameCaller, MethodStructure constructor, TypeComposition clazz,
                                    ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        Op opConstruct = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                xService service = (xService) clazz.getTemplate();

                return service.constructSync(frame, constructor, clazz, hParent, ahArg, 0);
                }

            public String toString()
                {
                return "ConstructRequest";
                }
            };

        return sendOp1Request(frameCaller, opConstruct, iReturn);
        }

    /**
     * Send and asynchronous "invoke" request with zero or one return value.
     *
     * @param fTuple   if true, the tuple is expected as a result of async execution
     * @param iReturn  a register id ({@link Op#A_IGNORE_ASYNC} for fire and forget)
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendInvoke1Request(Frame frameCaller, FunctionHandle hFunction,
                                  ObjectHandle hTarget, ObjectHandle[] ahArg, boolean fTuple, int iReturn)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frameCaller.raiseException(xException.serviceTerminated(frameCaller, f_sName));
            }

        boolean fAsync;
        int     cReturns;
        switch (iReturn)
            {
            case Op.A_IGNORE_ASYNC:
                assert !fTuple;
                fAsync   = true;
                cReturns = 0;
                break;

            case Op.A_IGNORE:
                assert !fTuple;
                fAsync   = false;
                cReturns = 0;
                break;

            default:
                fAsync   = false;
                cReturns = fTuple ? -1 : 1;
                break;
            }

        Op opCall = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                switch (cReturns)
                    {
                    case -1:
                        return hFunction.callT(frame, hTarget, ahArg, 0);

                    case 0:
                        return hFunction.call1(frame, hTarget, ahArg, A_IGNORE);

                    case 1:
                        return hFunction.call1(frame, hTarget, ahArg, 0);

                    default:
                        throw new IllegalStateException();
                    }
                }

            public String toString()
                {
                return "Invoke1Request";
                }
            };

        OpRequest                       request = new OpRequest(frameCaller, opCall, cReturns);
        CompletableFuture<ObjectHandle> future  = request.f_future;

        boolean fOverwhelmed = addRequest(request);

        Fiber fiber = frameCaller.f_fiber;
        if (fAsync)
            {
            // in the case of an ignored return and underwhelmed queue - fire and forget
            if (!fOverwhelmed)
                {
                if (future.isDone())
                    {
                    return frameCaller.assignFutureResult(iReturn, future);
                    }
                fiber.registerUncapturedRequest(future);
                return Op.R_NEXT;
                }

            // consider not to block the caller if it is *not* the reason of the callee being
            // overwhelmed, which would require some additional knowledge being retained
            }

        fiber.registerRequest(future);

        return frameCaller.assignFutureResult(iReturn, future);
        }

    /**
     * Send and asynchronous "invoke" request with multiple return values.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendInvokeNRequest(Frame frameCaller, FunctionHandle hFunction,
                                  ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frameCaller.raiseException(xException.serviceTerminated(frameCaller, f_sName));
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
                return "InvokeNRequest";
                }
            };

        OpRequest                         request = new OpRequest(frameCaller, opCall, cReturns);
        CompletableFuture<ObjectHandle[]> future  = request.f_future;

        boolean fOverwhelmed = addRequest(request);

        Fiber fiber = frameCaller.f_fiber;
        if (cReturns == 0)
            {
            fiber.registerUncapturedRequest(future);
            return fOverwhelmed || future.isDone()
                ? frameCaller.assignFutureResult(Op.A_IGNORE, (CompletableFuture) future)
                : Op.R_NEXT;
            }

        fiber.registerRequest(future);
        if (cReturns == 1)
            {
            CompletableFuture<ObjectHandle> cfReturn =
                    future.thenApply(ahResult -> ahResult[0]);
            return frameCaller.assignFutureResult(aiReturn[0], cfReturn);
            }

        // TODO replace with: assignFutureResults()
        return frameCaller.call(Utils.createWaitFrame(frameCaller, future, aiReturn));
        }

    /**
     * Send and asynchronous property "read" operation request. The caller will be blocked until the
     * result is returned.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendProperty01Request(Frame frameCaller, ObjectHandle hTarget,
                                     PropertyConstant idProp, int iReturn, PropertyOperation01 op)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frameCaller.raiseException(xException.serviceTerminated(frameCaller, f_sName));
            }

        Op opGet = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                int iResult = op.invoke(frame, hTarget, idProp, 0);

                // don't return a FutureHandle, but wait till it's done
                return idProp.isFutureVar() && iResult == Op.R_NEXT
                    ? ((FutureHandle) frame.f_ahVar[0]).waitAndAssign(frame, 0)
                    : iResult;
                }

            public String toString()
                {
                return "Property01_Op";
                }
            };

        return sendOp1Request(frameCaller, opGet, iReturn);
        }

    /*
     * Send and asynchronous property "update" operation request. The caller will be blocked until
     * the asynchronous operation completes.
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int sendProperty10Request(Frame frameCaller, ObjectHandle hTarget,
                                     PropertyConstant idProp, ObjectHandle hValue, PropertyOperation10 op)
        {
        if (getStatus() == ServiceStatus.Terminated)
            {
            return frameCaller.raiseException(xException.serviceTerminated(frameCaller, f_sName));
            }

        ObjectHandle hPassValue;
        if (hValue.isPassThrough(f_container))
            {
            hPassValue = hValue;
            }
        else
            {
            hPassValue = hValue.getTemplate().createProxyHandle(this, hValue, idProp.getType());
            if (hPassValue == null)
                {
                return frameCaller.raiseException(xException.mutableObject(frameCaller));
                }
            }

        Op opSet = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                return op.invoke(frame, hTarget, idProp, hPassValue);
                }

            public String toString()
                {
                return "Property10_Op";
                }
            };

        return sendOp1Request(frameCaller, opSet, Op.A_IGNORE);
        }

    /*
     * Send and asynchronous "constant initialization" message.
     */
    public CompletableFuture<ObjectHandle> sendConstantRequest(Frame frameCaller,
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

        OpRequest request = new OpRequest(frameCaller, opInit, 1);

        addRequest(request);

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
                    case Op.R_NEXT:
                        Utils.log(frame, "\nUnhandled exception: " +
                            ((StringHandle) frame.popStack()).getStringValue());
                        return Op.R_NEXT;

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(
                            frameCaller ->
                                {
                                Utils.log(frameCaller, "\nUnhandled exception: " +
                                    ((StringHandle) frameCaller.popStack()).getStringValue());
                                return Op.R_NEXT;
                                }
                            );
                        return Op.R_CALL;

                    default:
                        throw new IllegalStateException();
                    }
                });
            }

        // ignore any exception coming out of the handler
        postRequest(null, hFunction, new ObjectHandle[]{hException}, 0);
        }

    /**
     * Send the specified number of return values back to the caller.
     */
    protected static int sendResponse(Fiber fiberCaller, Frame frame,
                                      CompletableFuture future, int cReturns)
        {
        switch (cReturns)
            {
            case 0:
                fiberCaller.f_context.respond(
                        new Response<ObjectHandle>(fiberCaller, xTuple.H_VOID, frame.m_hException, future));
                break;

            case  1:
                {
                ObjectHandle    hReturn    = frame.f_ahVar[0];
                ExceptionHandle hException = frame.m_hException;
                ServiceContext  ctxCaller  = fiberCaller.f_context;

                if (hException == null && !hReturn.isPassThrough(ctxCaller.f_container))
                    {
                    hReturn = hReturn.getTemplate().createProxyHandle(frame.f_context, hReturn, null);
                    if (hReturn == null)
                        {
                        hException = xException.mutableObject(frame);
                        }
                    }
                ctxCaller.respond(new Response<ObjectHandle>(fiberCaller, hReturn, hException, future));
                break;
                }

            case -1: // tuple return
                {
                ObjectHandle[]  ahReturn   = frame.f_ahVar;
                ExceptionHandle hException = frame.m_hException;
                ServiceContext  ctxCaller  = fiberCaller.f_context;
                TupleHandle     hTuple     = null;
                if (hException == null)
                    {
                    hTuple = (TupleHandle) ahReturn[0];
                    if (hTuple == null)
                        {
                        // indicates a "void" return
                        hTuple = xTuple.H_VOID;
                        }
                    else
                        {
                        ahReturn = hTuple.m_ahValue;
                        for (int i = 0, c = ahReturn.length; i < c; i++)
                            {
                            ObjectHandle hReturn = ahReturn[i];
                            if (!hReturn.isPassThrough(ctxCaller.f_container))
                                {
                                hReturn = hReturn.getTemplate().createProxyHandle(frame.f_context, hReturn, null);
                                if (hReturn == null)
                                    {
                                    hException = xException.mutableObject(frame);
                                    hTuple     = null;
                                    break;
                                    }
                                ahReturn[i] = hReturn;
                                }
                            }
                        }
                    }
                ctxCaller.respond(new Response<ObjectHandle>(fiberCaller, hTuple, hException, future));
                break;
                }

            default:
                {
                assert cReturns > 1;
                ObjectHandle[]  ahReturn   = frame.f_ahVar;
                ExceptionHandle hException = frame.m_hException;
                ServiceContext  ctxCaller  = fiberCaller.f_context;
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
                        else if (!hReturn.isPassThrough(ctxCaller.f_container))
                            {
                            hReturn = hReturn.getTemplate().createProxyHandle(frame.f_context, hReturn, null);
                            if (hReturn == null)
                                {
                                hException = xException.mutableObject(frame);
                                ahReturn   = null;
                                break;
                                }
                            ahReturn[i] = hReturn;
                            }
                        }
                    }
                ctxCaller.respond(new Response<ObjectHandle[]>(fiberCaller, ahReturn, hException, future));
                break;
                }
            }
        return Op.R_NEXT;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Service \"")
          .append(f_sName)
          .append("\" (id=")
          .append(f_nId)
          .append(')');

        if (isContended())
            {
            sb.append(" contended");
            }
        if (m_frameCurrent != null)
            {
            sb.append(" @")
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

        final public CompletableFuture f_future;
        }

    /**
     * A cross-service Op based Request.
     */
    public static class OpRequest
            extends Request
        {
        protected OpRequest(Frame frameCaller, Op op, int cReturns)
            {
            super(frameCaller);

            f_op       = op;
            f_cReturns = cReturns;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns, new Op[]{f_op, Return_0.INSTANCE});

            frame0.addContinuation(_null -> sendResponse(f_fiberCaller, frame0, f_future, f_cReturns));
            return frame0;
            }

        private final Op  f_op;
        private final int f_cReturns;
        }

    /**
     * Represents a natural "fire and forget" or a native call request to a service.
     */
    public static class CallLaterRequest
            extends Message
        {
        private final FunctionHandle                  f_hFunction;
        private final ObjectHandle[]                  f_ahArg;
        private final int                             f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;

        public CallLaterRequest(Frame frameCaller, FunctionHandle hFunction, ObjectHandle[] ahArg,
                                int cReturns, CompletableFuture<ObjectHandle> future)
            {
            super(frameCaller);

            assert cReturns <= 1; // multiple returns are not supported for now

            f_hFunction = hFunction;
            f_ahArg     = ahArg;
            f_cReturns  = cReturns;
            f_future    = future;
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
                    return "CallLaterRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                    new Op[] {opCall, Return_0.INSTANCE});

            if (f_fiberCaller == null)
                {
                // since there was no originating fiber, we need to register the request on-the-spot
                frame0.f_fiber.registerRequest(f_future);

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
                        sendResponse(f_fiberCaller, frame0, f_future, f_cReturns));
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
     * The wake up scheduler.
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
                    ensureScheduled();
                    }
                };

            xLocalClock.TIMER.schedule(m_taskCurrent, Math.max(1, ldtWakeUp - ldtNow));
            }

        private long      f_ldtScheduled; // when
        private TimerTask m_taskCurrent;  // what
        }


    // ----- constants and fields ------------------------------------------------------------------

    public final Container        f_container;
    public final TemplateRegistry f_templates;

    /**
     * The queue size threshold at which the caller should pushed back.
     */
    public final static int QUEUE_THRESHOLD = 256;

    /**
     * The container's ConstantPool.
     */
    public final ConstantPool f_pool;

    /**
     * The service id.
     */
    public final int f_nId;

    /**
     * The service name.
     */
    public final String f_sName;

    /**
     * The service handle.
     */
    private ServiceHandle m_hService;

    /**
     * The unhandled exception notification
     */
    public FunctionHandle m_hExceptionHandler;

    /**
     * The counter used to create Frame ids
     */
    protected int m_iFrameCounter;

    /**
     * The current Timeout that will be used by the service when it invokes other services.
     */
    private ObjectHandle m_hTimeout;

    /**
     * The current CriticalSection for the service.
     */
    private ObjectHandle m_hCriticalSection;

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
    private final Queue<Message>  f_queueMsg;

    /**
     * The queue of message responses.
     */
    private final Queue<Response> f_queueResponse;

    /**
     * The set of active fibers. It can be [read] accessed by outside threads.
     */
    private final Set<Fiber> f_setFibers = new ConcurrentSkipListSet<>();

    /**
     * The queue of suspended fibers.
     */
    private final FiberQueue f_queueSuspended = new FiberQueue();

    /**
     * The reentrancy policy. Must be the same names as in natural Service.Reentrancy.
     */
    public enum Reentrancy {Open, Prioritized, Exclusive, Forbidden}

    private Reentrancy m_reentrancy = Reentrancy.Prioritized;

    /**
     * The context scheduling "lock", atomic operations are performed via {@link #SCHEDULING_LOCK_HANDLE}.
     * <p>
     * This lock must be acquired in order to schedule context processing and is not released until
     * the context is not longer scheduled.
     * <p>
     * The lock is implemented as a volatile counter, the thread which transitions from 0 to 1 becomes
     * the lock holder will release the lock by setting back via a getAndSet(0), which if it yields
     * a prior value of something other then 1 indicates lock contention.
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
        Terminated;
        }

    /**
     * The context served by the current thread.
     */
    final static ThreadLocal<ServiceContext[]> s_tloContext = ThreadLocal.withInitial(() -> new ServiceContext[1]);

    /**
     * VarHandle for {@link #m_lLockScheduling}.
     */
    final static VarHandle SCHEDULING_LOCK_HANDLE;
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
     */
    private final Map<Op, EnumMap> f_mapOpInfo = new HashMap<>();

    /**
     * A wake up scheduler to process registered timeouts.
     */
    private final WakeUpScheduler f_wakeUpScheduler = new WakeUpScheduler();
    }
