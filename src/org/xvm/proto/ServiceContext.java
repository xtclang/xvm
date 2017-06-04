package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFutureRef;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xService;
import org.xvm.proto.template.xService.PropertyOperation;
import org.xvm.proto.template.xService.PropertyOperation10;
import org.xvm.proto.template.xService.PropertyOperation01;
import org.xvm.proto.template.xService.PropertyOperation11;
import org.xvm.proto.template.xService.ServiceHandle;

import java.util.LinkedList;
import java.util.Queue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Supplier;

/**
 * The service context.
 *
 * @author gg 2017.02.15
 */
public class ServiceContext
    {
    public final Container f_container;
    public final TypeSet f_types;
    public final ObjectHeap f_heapGlobal;
    public final ConstantPoolAdapter f_constantPool;

    private final Queue<Message> f_queueMsg;

    final int f_nId; // the service id
    final String f_sName; // the service name
    ServiceHandle m_hService;

    Frame m_frameCurrent;
    Queue<Frame> f_queueSuspended = new LinkedList<>(); // suspended fibers

    // fiber id producer
    AtomicInteger f_idProducer = new AtomicInteger();

    enum Status {Idle, Busy, ShuttingDown, Terminated}
    volatile Status m_status = Status.Idle;

    final static ThreadLocal<ServiceContext> s_tloContext = new ThreadLocal<>();

    ServiceContext(Container container, String sName, int nId)
        {
        f_container = container;
        f_sName = sName;
        f_nId = nId;

        f_heapGlobal = container.f_heapGlobal;
        f_types = container.f_types;
        f_constantPool = container.f_constantPoolAdapter;
        f_queueMsg = new ConcurrentLinkedQueue<>();
        }

    public Frame getCurrentFrame()
        {
        return m_frameCurrent;
        }

    public static ServiceContext getCurrentContext()
        {
        return s_tloContext.get();
        }

    public void setService(ServiceHandle hService)
        {
        assert m_hService == null;
        m_hService = hService;
        }

    public void addMessage(Message msg)
        {
        f_queueMsg.add(msg);
        }

    // get a next frame ready for execution
    public Frame nextFiber()
        {
        // new messages should be processed first
        Message message = f_queueMsg.poll();
        while (message != null)
            {
            s_tloContext.set(this);
            Frame frame = message.createFrame(this);
            if (frame != null)
                {
                return frame;
                }
            message = f_queueMsg.poll();
            }

        return f_queueSuspended.isEmpty() ? null : f_queueSuspended.poll();
        }

    public void suspendFiber(Frame frame)
        {
        f_queueSuspended.add(frame);
        }

    // return null iff there the context popped up all frames
    public Frame execute(Frame frame)
        {
        int iPC = frame.m_iPC;
        int iPCLast = iPC;

        if (frame.m_fBlocked)
            {
            switch (frame.checkWaitingRegisters())
                {
                case Op.R_BLOCK:
                    // there are still some "waiting" registers
                    return frame;

                case Op.R_EXCEPTION:
                    iPC = Op.R_EXCEPTION;
                    break;

                case Op.R_NEXT:
                    // proceed as is
                    break;
                }
            }

        m_frameCurrent = frame;
        s_tloContext.set(this);

        Op[] abOp = frame.f_aOp;

        int nOps = 0;
        while (true)
            {
            while (iPC >= 0) // main loop
                {
                if (++nOps > 10)
                    {
                    // same as yield
                    frame.m_iPC = iPC;
                    m_frameCurrent = null;
                    return frame;
                    }

                iPC = abOp[iPC].process(frame, iPCLast = iPC);
                }

            switch (iPC)
                {
                case Op.R_NEXT:
                    iPC = iPCLast + 1;
                    break;

                case Op.R_CALL:
                    m_frameCurrent = frame.m_frameNext;
                    frame.m_iPC = iPCLast + 1;
                    frame.m_frameNext = null;
                    frame = m_frameCurrent;
                    abOp = frame.f_aOp;
                    iPC = 0;
                    break;

                case Op.R_RETURN:
                    Supplier<Frame> continuation = frame.m_continuation;
                    if (continuation != null)
                        {
                        Frame frameNext = continuation.get();
                        if (frameNext == null)
                            {
                            // continuation is allowed to "throw"
                            if (frame.m_hException != null)
                                {
                                // throw the exception
                                iPC = Op.R_EXCEPTION;
                                break;
                                }
                            }
                        else
                            {
                            frame = m_frameCurrent = frameNext;
                            abOp = frame.f_aOp;
                            iPC = frame.m_iPC;
                            break;
                            }
                        }

                    frame = m_frameCurrent = frame.f_framePrev;
                    if (frame == null)
                        {
                        // all done
                        return null;
                        }
                    abOp = frame.f_aOp;
                    iPC = frame.m_iPC;
                    break;

                case Op.R_EXCEPTION:
                    ExceptionHandle hException = frame.m_hException;
                    assert hException != null;

                    while (true)
                        {
                        iPC = frame.findGuard(hException);
                        if (iPC >= 0)
                            {
                            // handled exception; go to the handler
                            m_frameCurrent = frame;
                            abOp = frame.f_aOp;
                            break;
                            }

                        // not handled by this frame
                        Frame framePrev = frame.f_framePrev;
                        if (framePrev == null)
                            {
                            // the frame is a synthetic "proto" frame;
                            // it should process the exception
                            if (frame.m_continuation == null)
                                {
                                // TODO: this should never happen
                                Utils.log("\nProto-frame is missing the continuation: " + hException);
                                }
                            else
                                {
                                frame.m_hException = hException;
                                Frame frameNext = frame.m_continuation.get();
                                if (frameNext != null)
                                    {
                                    frame = m_frameCurrent = frameNext;
                                    abOp = frame.f_aOp;
                                    iPC = frame.m_iPC;
                                    break;
                                    }
                                }

                            m_frameCurrent = null;
                            return null;
                            }
                        frame = framePrev;
                        }
                    break;

                case Op.R_REPEAT:
                    frame.m_iPC = iPCLast;
                    m_frameCurrent = null;
                    return frame;

                case Op.R_BLOCK:
                    frame.m_iPC = iPCLast + 1;
                    frame.m_fBlocked = true;
                    m_frameCurrent = null;
                    return frame;

                case Op.R_YIELD:
                    frame.m_iPC = iPCLast + 1;
                    m_frameCurrent = null;
                    return frame;
                }
            }
        }

    // create a new frame that returns zero or one value into the specified slot
    public Frame createFrame1(Frame framePrev, InvocationTemplate template,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, framePrev, template, hTarget, ahVar,
                iReturn == Frame.R_UNUSED ? Utils.ARGS_NONE : new int[] {iReturn});
        }

    public Frame createFrameN(Frame framePrev, InvocationTemplate template,
                             ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return new Frame(this, framePrev, template, hTarget, ahVar, aiReturn);
        }

    // this method is used by the ServiceContext
    public Frame createServiceEntryFrame(int cReturns)
        {
        // TODO: collect the caller's frame proxy

        // create a pseudo frame that has variables to collect
        // the return values
        ObjectHandle[] ahVar = new ObjectHandle[cReturns];

        Frame frame = createFrame1(null, null, null, ahVar, Frame.R_UNUSED);

        for (int iVar = 0; iVar < cReturns; iVar++)
            {
            // create a "dynamic ref" for all return values
            // (see DVar op-code)
            FutureHandle hRef = xFutureRef.makeHandle(new CompletableFuture<>());

            frame.introduceVar(iVar, hRef.f_clazz, null, Frame.VAR_DYNAMIC_REF, hRef);
            }
        return frame;
        }

    // ----- x:Service methods -----

    public boolean isContended()
        {
        return !f_queueMsg.isEmpty() || !f_queueSuspended.isEmpty() || m_frameCurrent != null;
        }

    // send and asynchronous "construct service" message
    public CompletableFuture<ServiceHandle> sendConstructRequest(ServiceContext context,
                ConstructTemplate constructor, TypeComposition clazz, ObjectHandle[] ahArg)
        {
        CompletableFuture<ServiceHandle> future = new CompletableFuture<>();

        context.addMessage(new ConstructRequest(this, constructor, clazz, future, ahArg));

        return future.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // the construction failed; we need to kill the service
                f_container.removeServiceContext(context);
                }
            });
        }

    // send and asynchronous "invoke" message with zero or one return value
    public CompletableFuture<ObjectHandle> sendInvoke1Request(
            ServiceContext context, FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle> future = cReturns == 0 ? null : new CompletableFuture<>();

        context.addMessage(new Invoke1Request(this, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous "invoke" message with multiple return values
    public CompletableFuture<ObjectHandle[]> sendInvokeNRequest(ServiceContext context,
                FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle[]> future = new CompletableFuture<>();

        context.addMessage(new InvokeNRequest(this, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous property operation message
    public CompletableFuture<ObjectHandle> sendProperty01Request(ServiceContext context,
            PropertyTemplate property, PropertyOperation01 op)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        context.addMessage(new PropertyOpRequest(this, property, null, 1, future, op));

        return future;
        }

    // send and asynchronous property operation message
    public void sendProperty10Request(ServiceContext context,
            PropertyTemplate property, ObjectHandle hValue, PropertyOperation10 op)
        {
        context.addMessage(new PropertyOpRequest(this, property, hValue, 0, null, op));
        }

    public int callLater(FunctionHandle hFunction, ObjectHandle[] ahArg)
        {
        sendInvoke1Request(this, hFunction, ahArg, 0);
        return Op.R_NEXT;
        }

    public Frame callUnhandledExceptionHandler(Frame frame)
        {
        // TODO:
        Utils.log("Unhandled exception: " + frame.m_hException);
        return null;
        }

    // ----- helpers ------

    // return an asynchronous call result
    public static <T> void sendResponse(ServiceContext context, ExceptionHandle hException,
                                 T returnValue, CompletableFuture<T> future)
        {
        context.addMessage(new Response(hException, returnValue, future));
        }

    // send zero or one results back to the caller
    protected static void sendResponse1(ServiceContext contextCaller,
                                        Frame frame, CompletableFuture future)
        {
        if (frame.m_hException == null)
            {
            CompletableFuture<ObjectHandle> cf =
                    ((FutureHandle) frame.f_ahVar[0]).m_future;

            if (cf.isDone() && !cf.isCompletedExceptionally())
                {
                // common path
                try
                    {
                    sendResponse(contextCaller, null, cf.get(), future);
                    }
                catch (Exception e) {}
                }
            else // has not finished yet, or completed exceptionally
                {
                cf.whenComplete((hR, x) ->
                    {
                    if (x == null)
                        {
                        sendResponse(contextCaller, null, hR, future);
                        }
                    else
                        {
                        ExceptionHandle hX = ((ExceptionHandle.WrapperException) x).
                                getExceptionHandle();
                        sendResponse(contextCaller, hX, null, future);
                        }
                    });
                }
            }
        else
            {
            sendResponse(contextCaller, frame.m_hException, null, future);
            }
        }

    @Override
    public String toString()
        {
        return "Service(" + f_sName + ')';
        }

    public interface Message
        {
        Frame createFrame(ServiceContext context);
        }

    /**
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class ConstructRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final ConstructTemplate f_constructor;
        private final TypeComposition f_clazz;
        private final ObjectHandle[] f_ahArg;
        private final CompletableFuture<ServiceHandle> f_future;

        public ConstructRequest(ServiceContext contextCaller, ConstructTemplate constructor,
                                TypeComposition clazz, CompletableFuture<ServiceHandle> future, ObjectHandle[] ahArg)
            {
            f_contextCaller = contextCaller;
            f_constructor = constructor;
            f_clazz = clazz;
            f_ahArg = ahArg;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Frame frame0 = context.createServiceEntryFrame(1);

            xService service = (xService) f_constructor.getClazzTemplate();

            int nResult = service.constructSync(frame0, f_constructor, f_clazz, f_ahArg, 0);
            if (nResult == Op.R_CALL)
                {
                frame0.m_continuation = () ->
                    {
                    sendResponse1(f_contextCaller, frame0, f_future);
                    return null;
                    };
                return frame0.m_frameNext;
                }

            // can only be an exception
            sendResponse1(f_contextCaller, frame0, f_future);
            return null;
            }
        }

    /**
     * Represents an invoke request from one service onto another with zero or one return value.
     */
    public static class Invoke1Request
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;

        public Invoke1Request(ServiceContext contextCaller, FunctionHandle hFunction,
                              ObjectHandle[] ahArg, int cReturns,
                              CompletableFuture<ObjectHandle> future)
            {
            f_contextCaller = contextCaller;
            f_hFunction = hFunction;
            f_ahArg = ahArg;
            f_cReturns = cReturns;
            f_future = future;
            }


        @Override
        public Frame createFrame(ServiceContext context)
            {
            Frame frame0 = context.createServiceEntryFrame(f_hFunction.getReturnCount());

            f_hFunction.call1(frame0, f_ahArg, f_cReturns - 1);

            frame0.m_continuation = () ->
                {
                if (f_cReturns == 0)
                    {
                    if (frame0.m_hException != null)
                        {
                        return context.callUnhandledExceptionHandler(frame0);
                        }
                    }
                else
                    {
                    sendResponse1(f_contextCaller, frame0, f_future);
                    }
                return null;
                };

            return frame0.m_frameNext;
            }
        }

    /**
     * Represents an invoke request from one service onto another with multiple return values.
     */
    public static class InvokeNRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle[]> f_future;

        public InvokeNRequest(ServiceContext contextCaller, FunctionHandle hFunction,
                             ObjectHandle[] ahArg, int cReturns,
                             CompletableFuture<ObjectHandle[]> future)
            {
            f_contextCaller = contextCaller;
            f_hFunction = hFunction;
            f_ahArg = ahArg;
            f_cReturns = cReturns;
            f_future = future;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            Frame frame0 = context.createServiceEntryFrame(f_hFunction.getReturnCount());

            // the pseudo-frame's vars are the return values
            int[] aiReturn = new int[f_cReturns];
            for (int i = 0; i < f_cReturns; i++)
                {
                aiReturn[i] = i;
                }

            f_hFunction.callN(frame0, f_ahArg, aiReturn);

            Frame frameNext = frame0.m_frameNext;

            frameNext.m_continuation = () ->
                {
                ExceptionHandle hException = frame0.m_hException;
                if (hException == null)
                    {
                    // TODO: optimized for a case when all futures are complete
                    CompletableFuture<Void>[] acf = new CompletableFuture[f_cReturns];
                    for (int i = 0; i < f_cReturns; i++)
                        {
                        int iRet = i;

                        acf[i] = ((FutureHandle) frame0.f_ahVar[i]).m_future.thenAccept(
                                h -> frame0.f_ahVar[iRet] = h);
                        }

                    CompletableFuture.allOf(acf).whenComplete((none, x) ->
                        {
                        if (x == null)
                            {
                            sendResponse(f_contextCaller, null, frame0.f_ahVar, f_future);
                            }
                        else
                            {
                            ExceptionHandle hX = ((ExceptionHandle.WrapperException) x).
                                    getExceptionHandle();
                            sendResponse(f_contextCaller, hX, null, f_future);
                            }
                        });
                    }
                else
                    {
                    sendResponse(f_contextCaller, hException, null, f_future);
                    }
                return null;
                };

            return frameNext;
            }
        }

    /**
     * Represents a property operation request from one service onto another
     * that takes zero or one parameter and returns zero or one value.
     */
    public static class PropertyOpRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final PropertyTemplate f_property;
        private final ObjectHandle f_hValue;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle> f_future;
        private final PropertyOperation f_op;

        public PropertyOpRequest(ServiceContext contextCaller, PropertyTemplate property,
                                 ObjectHandle hValue, int cReturns,
                                 CompletableFuture<ObjectHandle> future, PropertyOperation op)
            {
            f_contextCaller = contextCaller;
            f_property = property;
            f_hValue = hValue;
            f_cReturns = cReturns;
            f_future = future;
            f_op = op;
            }

        @Override
        public Frame createFrame(ServiceContext context)
            {
            int cReturns = f_cReturns;

            Frame frame0 = context.createServiceEntryFrame(cReturns);

            if (cReturns == 0)
                {
                int nResult =
                        ((PropertyOperation10) f_op).invoke(frame0, context.m_hService, f_property, f_hValue);
                if (nResult == Op.R_CALL)
                    {
                    return frame0.m_frameNext;
                    }
                if (nResult == Op.R_EXCEPTION)
                    {
                    return context.callUnhandledExceptionHandler(frame0);
                    }
                }
            else
                {
                int nResult = f_hValue == null
                        ? ((PropertyOperation01) f_op).invoke(frame0, context.m_hService, f_property, 0)
                        : ((PropertyOperation11) f_op).invoke(frame0, context.m_hService, f_property, f_hValue, 0);

                if (nResult == Op.R_CALL)
                    {
                    frame0.m_continuation = () ->
                        {
                        sendResponse1(f_contextCaller, frame0, f_future);
                        return null;
                        };
                    return frame0.m_frameNext;
                    }

                sendResponse1(f_contextCaller, frame0, f_future);
                }
            return null;
            }
        }

    /**
     * Represents a call return.
     */
    public static class Response<T>
            implements Message
        {
        private final ExceptionHandle f_hException;
        private final T f_return;
        private final CompletableFuture<T> f_future;

        public Response(ExceptionHandle hException, T returnValue, CompletableFuture<T> future)
            {
            f_hException = hException;
            f_return = returnValue;
            f_future = future;
            }


        @Override
        public Frame createFrame(ServiceContext context)
            {
            if (f_hException == null)
                {
                f_future.complete(f_return);
                }
            else
                {
                f_future.completeExceptionally(f_hException.getException());
                }
            return null;
            }
        }
    }
