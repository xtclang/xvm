package org.xvm.runtime;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;

import org.xvm.asm.op.Return_0;

import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.PropertyOperation;
import org.xvm.runtime.template.xService.PropertyOperation10;
import org.xvm.runtime.template.xService.PropertyOperation01;
import org.xvm.runtime.template.xService.PropertyOperation11;
import org.xvm.runtime.template.xService.ServiceHandle;
import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;


/**
 * The service context.
 */
public class ServiceContext
        implements Runnable
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
        assert m_hService == null;
        m_hService = hService;
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
        EnumMap mapByCategory = m_mapOpInfo.get(op);
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
        m_mapOpInfo.computeIfAbsent(op, (op_) -> new EnumMap(category.getClass()))
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
            if (drainWork())
                {
                // we've completed all processing
                releaseSchedulingLock();
                }
            else
                {
                // continue asynchronously
                f_container.schedule(this);
                }
            }
        // else; already scheduled
        }

    @Override
    public void run()
        {
        if (drainWork())
            {
            releaseSchedulingLock();
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
        return !m_fLockScheduling && SCHEDULING_LOCK_HANDLE.compareAndSet(this, false, true);
        }

    /**
     * Release the context lock.
     */
    protected void releaseSchedulingLock()
        {
        m_fLockScheduling = false;

        // we've released the lock but work may have concurrently slipped in; if so try to re-lock
        // and hand off processing to the runtime's thread-pool
        if (isContended() && tryAcquireSchedulingLock())
            {
            f_container.schedule(this);
            }
        }

    public void addRequest(Message msg)
        {
        f_queueMsg.add(msg);
        ensureScheduled();
        }

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
                // don't allow a new fiber unless it belongs to already existing thread of execution
                return qSuspended.getAssociatedOrYielded();

            case Prioritized:
                // give priority to already existing thread of execution
                Frame frameNext = qSuspended.getAssociatedOrYielded();
                if (frameNext != null)
                    {
                    return frameNext;
                    }
                // fall through

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
        Fiber fiber = frame.f_fiber;
        int iPC = frame.m_iPC;
        int iPCLast = iPC;

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

        Op[] aOp = frame.f_aOp;
        int  nOps = 0;

    nextOp:
        while (true)
            {
            while (iPC >= 0) // main loop
                {
                frame.m_iPC = iPC;

                if (++nOps > 100)
                    {
                    fiber.setStatus(FiberStatus.Paused);
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
                    fiber.setStatus(FiberStatus.Waiting);
                    return frame;

                case Op.R_BLOCK:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Waiting);
                    return frame;

                case Op.R_YIELD:
                    frame.m_iPC = iPCLast + 1;
                    fiber.setStatus(FiberStatus.Yielded);
                    return frame;

                default:
                    throw new IllegalStateException("Invalid code: " + iPC);
                }
            }
        }

    // create a "proto"-frame
    protected Frame createServiceEntryFrame(Message msg, int cReturns, Op[] aopNative)
        {
        // create a pseudo frame that has variables to collect the return values
        ObjectHandle[] ahVar = new ObjectHandle[cReturns];

        Fiber fiber = createFiber(msg);
        Frame frame = new Frame(fiber, msg.f_iCallerPC, aopNative, ahVar, Op.A_IGNORE, null);

        for (int nVar = 0; nVar < cReturns; nVar++)
            {
            frame.f_aInfo[nVar] = frame.new VarInfo(f_pool.typeObject(), Frame.VAR_STANDARD);
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
            fiber.setStatus(FiberStatus.Terminating);
            }
        else
            {
            f_setFibers.remove(fiber);
            }
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
        return m_frameCurrent != null || !f_queueMsg.isEmpty() || !f_queueSuspended.isEmpty();
        }

    /**
     * @return true iff the service is Idle
     */
    public boolean isIdle()
        {
        return getStatus() == ServiceStatus.Idle;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Send and asynchronous "call later" message to this context.
     *
     * Unlike any of the "send*" methods below, there is no "originating" fiber in this case and the
     * future registration is done by the request itself.
     */
    public int callLater(FunctionHandle hFunction, ObjectHandle[] ahArg)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new CallLaterRequest(hFunction, ahArg, future));

        future.whenComplete((r, x) ->
            {
            if (x != null)
                {
                callUnhandledExceptionHandler(
                    ((ExceptionHandle.WrapperException) x).getExceptionHandle());
                }
            });
        return Op.R_NEXT;
        }

    /*
     * Send and asynchronous "construct service" message to this context.
     */
    public CompletableFuture<ServiceHandle> sendConstructRequest(Frame frameCaller,
                MethodStructure constructor, ClassComposition clazz, ObjectHandle hParent, ObjectHandle[] ahArg)
        {
        CompletableFuture<ServiceHandle> future = new CompletableFuture<>();

        addRequest(new ConstructRequest(frameCaller, constructor, clazz, future, hParent, ahArg));

        frameCaller.f_fiber.registerRequest(future);
        return future;
        }

    /**
     * Send and asynchronous "invoke" message with zero or one return value.
     *
     * @param cReturns 1, 0 or -1  for one, zero or tuple return
     */
    public CompletableFuture<ObjectHandle> sendInvoke1Request(Frame frameCaller,
            FunctionHandle hFunction, ObjectHandle hTarget, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new Invoke1Request(frameCaller, hFunction, hTarget, ahArg, cReturns, future));

        Fiber fiber = frameCaller.f_fiber;
        if (cReturns == 0)
            {
            fiber.registerUncapturedRequest(future);
            return null;
            }

        fiber.registerRequest(future);
        return future;
        }

    /**
     * Send and asynchronous "invoke" message with multiple return values.
     */
    public CompletableFuture<ObjectHandle[]> sendInvokeNRequest(Frame frameCaller,
                FunctionHandle hFunction, ObjectHandle hTarget, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle[]> future = new CompletableFuture<>();

        addRequest(new InvokeNRequest(frameCaller, hFunction, hTarget, ahArg, cReturns, future));

        Fiber fiber = frameCaller.f_fiber;
        if (cReturns == 0)
            {
            fiber.registerUncapturedRequest(future);
            return null;
            }

        fiber.registerRequest(future);
        return future;
        }

    /**
     * Send and asynchronous property "read" operation message.
     */
    public CompletableFuture<ObjectHandle> sendProperty01Request(Frame frameCaller,
                                                                 PropertyConstant idProp, PropertyOperation01 op)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new PropertyOpRequest(frameCaller, idProp, null, 1, future, op));

        frameCaller.f_fiber.registerRequest(future);
        return future;
        }

    /*
     * Send and asynchronous property "update" operation message.
     */
    public void sendProperty10Request(Frame frameCaller,
                                      PropertyConstant idProp, ObjectHandle hValue, PropertyOperation10 op)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new PropertyOpRequest(frameCaller, idProp, hValue, 0, future, op));

        frameCaller.f_fiber.registerUncapturedRequest(future);
        }

    /*
     * Send and asynchronous "constant initialization" message.
     */
    public CompletableFuture<ObjectHandle> sendConstantRequest(Frame frameCaller,
                                                               List<SingletonConstant> listConstants)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        addRequest(new ConstantInitializationRequest(frameCaller, listConstants, future));

        return future;
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
                                    ((StringHandle) frame.popStack()).getStringValue());
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
        callLater(hFunction, new ObjectHandle[]{hException});
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

                if (hException == null && hReturn.isMutable() && !hReturn.isService())
                    {
                    hReturn = hReturn.getTemplate().createProxyHandle(frame.f_context, hReturn, null);
                    if (hReturn == null)
                        {
                        hException = xException.mutableObject(frame);
                        }
                    }
                fiberCaller.f_context.respond(
                        new Response<ObjectHandle>(fiberCaller, hReturn, hException, future));
                break;
                }

            case -1: // tuple return
                {
                ObjectHandle[]  ahReturn   = frame.f_ahVar;
                ExceptionHandle hException = frame.m_hException;
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
                            if (hReturn.isMutable() && !hReturn.isService())
                                {
                                hReturn = hReturn.getTemplate().
                                        createProxyHandle(frame.f_context, hReturn, null);
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
                fiberCaller.f_context.respond(
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
                        else if (hReturn.isMutable() && !hReturn.isService())
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
                fiberCaller.f_context.respond(new
                        Response<ObjectHandle[]>(fiberCaller, ahReturn, hException, future));
                break;
                }
            }
        return Op.R_NEXT;
        }

    @Override
    public String toString()
        {
        return "Service \"" + f_sName + "\" (id=" + f_nId + ')';
        }


    // --- inner classes ---------------------------------------------------------------------------

    /**
     * Base class for requests.
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
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class ConstructRequest
            extends Message
        {
        private final MethodStructure                  f_constructor;
        private final ClassComposition                 f_clazz;
        private final ObjectHandle                     f_hParent;
        private final ObjectHandle[]                   f_ahArg;
        private final CompletableFuture<ServiceHandle> f_future;

        public ConstructRequest(Frame frameCaller, MethodStructure constructor, ClassComposition clazz,
                                CompletableFuture<ServiceHandle> future, ObjectHandle hParent, ObjectHandle[] ahArg)
            {
            super(frameCaller);

            f_constructor = constructor;
            f_clazz       = clazz;
            f_hParent     = hParent;
            f_ahArg       = ahArg;
            f_future      = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opConstruct = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    xService service = (xService) f_clazz.getTemplate();

                    return service.constructSync(frame, f_constructor, f_clazz, f_hParent, f_ahArg, 0);
                    }

                public String toString()
                    {
                    return "ConstructRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, 1,
                    new Op[]{opConstruct, Return_0.INSTANCE});

            frame0.addContinuation(_null -> sendResponse(f_fiberCaller, frame0, f_future, 1));
            return frame0;
            }
        }

    /**
     * Represents a "fire and forget" call request onto a service.
     */
    public static class CallLaterRequest
            extends Message
        {
        private final FunctionHandle                  f_hFunction;
        private final ObjectHandle[]                  f_ahArg;
        private final CompletableFuture<ObjectHandle> f_future;

        public CallLaterRequest(FunctionHandle hFunction, ObjectHandle[] ahArg,
                                CompletableFuture<ObjectHandle> future)
            {
            super(null);

            f_hFunction = hFunction;
            f_ahArg     = ahArg;
            f_future    = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return f_hFunction.call1(frame, null, f_ahArg, A_IGNORE);
                    }

                public String toString()
                    {
                    return "CallLaterRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, 0,
                    new Op[] {opCall, Return_0.INSTANCE});

            // since there was no originating fiber, we need to register the request on-the-spot
            frame0.f_fiber.registerRequest(f_future);

            frame0.addContinuation(_null ->
                {
                // "callLater" has returned
                ExceptionHandle hException = frame0.m_hException;
                if (hException == null)
                    {
                    f_future.complete(xTuple.H_VOID);
                    }
                else
                    {
                    f_future.completeExceptionally(hException.getException());
                    }
                return Op.R_NEXT;
                });

            return frame0;
            }
        }

    /**
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class Invoke1Request
            extends Message
        {
        private final FunctionHandle                  f_hFunction;
        private final ObjectHandle                    f_hTarget;
        private final ObjectHandle[]                  f_ahArg;
        private final int                             f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;

        public Invoke1Request(Frame frameCaller, FunctionHandle hFunction,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int cReturns,
                              CompletableFuture<ObjectHandle> future)
            {
            super(frameCaller);

            f_hFunction = hFunction;
            f_hTarget   = hTarget;
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
                    switch (f_cReturns)
                        {
                        case -1:
                            return f_hFunction.callT(frame, f_hTarget, f_ahArg, 0);

                        case 0:
                            return f_hFunction.call1(frame, f_hTarget, f_ahArg, A_IGNORE);

                        case 1:
                            return f_hFunction.call1(frame, f_hTarget, f_ahArg, 0);

                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "Invoke1Request";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, Math.abs(f_cReturns),
                    new Op[] {opCall, Return_0.INSTANCE});

            frame0.addContinuation(_null ->
                sendResponse(f_fiberCaller, frame0, f_future, f_cReturns));

            return frame0;
            }
        }

    /**
     * Represents an invoke request from one service onto another with multiple return values.
     */
    public static class InvokeNRequest
            extends Message
        {
        private final FunctionHandle                    f_hFunction;
        private final ObjectHandle                      f_hTarget;
        private final ObjectHandle[]                    f_ahArg;
        private final int                               f_cReturns;
        private final CompletableFuture<ObjectHandle[]> f_future;

        public InvokeNRequest(Frame frameCaller, FunctionHandle hFunction,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int cReturns,
                              CompletableFuture<ObjectHandle[]> future)
            {
            super(frameCaller);

            f_hFunction = hFunction;
            f_hTarget   = hTarget;
            f_ahArg     = ahArg;
            f_cReturns  = cReturns;
            f_future    = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            // the pseudo-frame's vars are the return values
            int[] aiReturn = new int[f_cReturns];
            for (int i = 0; i < f_cReturns; i++)
                {
                aiReturn[i] = i;
                }

            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return f_hFunction.callN(frame, f_hTarget, f_ahArg, aiReturn);
                    }

                public String toString()
                    {
                    return "InvokeNRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, f_cReturns,
                new Op[] {opCall, Return_0.INSTANCE});

            frame0.addContinuation(_null ->
                sendResponse(f_fiberCaller, frame0, f_future, f_cReturns));

            return frame0;
            }
        }

    /**
     * Represents a property operation request from one service onto another
     * that takes zero or one parameter and returns zero or one value.
     */
    public static class PropertyOpRequest
            extends Message
        {
        private final PropertyConstant                f_idProp;
        private final ObjectHandle                    f_hValue;
        private final int                             f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;
        private final PropertyOperation               f_op;

        public PropertyOpRequest(Frame frameCaller, PropertyConstant idProp,
                                 ObjectHandle hValue, int cReturns,
                                 CompletableFuture<ObjectHandle> future, PropertyOperation op)
            {
            super(frameCaller);

            f_idProp   = idProp;
            f_hValue   = hValue;
            f_cReturns = cReturns;
            f_future   = future;
            f_op       = op;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            int cReturns = f_cReturns;

            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return cReturns == 0
                            ? ((PropertyOperation10) f_op).invoke(frame, context.m_hService, f_idProp, f_hValue)
                       : f_hValue == null
                            ? ((PropertyOperation01) f_op).invoke(frame, context.m_hService, f_idProp, 0)
                            : ((PropertyOperation11) f_op).invoke(frame, context.m_hService, f_idProp, f_hValue, 0);
                    }

                public String toString()
                    {
                    return "PropertyOpRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, cReturns,
                    new Op[]{opCall, Return_0.INSTANCE});

            frame0.addContinuation(_null ->
                sendResponse(f_fiberCaller, frame0, f_future, f_cReturns));

            return frame0;
            }
        }

    /**
     * Represents a constant initialization request sent to the "main" container context.
     */
    public static class ConstantInitializationRequest
            extends Message
        {
        private final List<SingletonConstant>         f_list;
        private final CompletableFuture<ObjectHandle> f_future;

        public ConstantInitializationRequest(Frame frameCaller, List<SingletonConstant> listConstants,
                                             CompletableFuture<ObjectHandle> future)
            {
            super(frameCaller);

            f_list   = listConstants;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    return Utils.initConstants(frame, f_list,
                        frameCaller -> frameCaller.assignValue(0, xNullable.NULL));
                    }

                public String toString()
                    {
                    return "StaticInitializationRequest";
                    }
                };

            Frame frame0 = context.createServiceEntryFrame(this, 1,
                    new Op[]{opCall, Return_0.INSTANCE});

            frame0.addContinuation(_null ->
                sendResponse(f_fiberCaller, frame0, f_future, 1));

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

    // ----- constants and fields ------------------------------------------------------------------

    public final Container        f_container;
    public final TemplateRegistry f_templates;

    /**
     * The container's ConstantPool.
     */
    public final ConstantPool f_pool;

    /**
     * The service id.
     */
    private final int f_nId;

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
    public long m_cTimeoutMillis;

    /**
     * Metrics: the total time (in nanos) this service has been running.
     */
    protected long m_cRuntimeNanos;

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
    public enum Reentrancy {Prioritized, Open, Exclusive, Forbidden}
    public Reentrancy m_reentrancy = Reentrancy.Prioritized;

    /**
     * The context scheduling "lock", atomic operations are performed via {@link #SCHEDULING_LOCK_HANDLE}.
     * <p>
     * This lock must be acquired in order to schedule context processing and is not released until
     * the context is not longer scheduled.
     */
    volatile boolean m_fLockScheduling;

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
     * VarHandle for {@link #m_fLockScheduling}.
     */
    final static VarHandle SCHEDULING_LOCK_HANDLE;

    static
        {
        try
            {
            SCHEDULING_LOCK_HANDLE = MethodHandles.lookup().findVarHandle(ServiceContext.class,
                "m_fLockScheduling", boolean.class);
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
    private Map<Op, EnumMap> m_mapOpInfo = new HashMap<>();
    }
