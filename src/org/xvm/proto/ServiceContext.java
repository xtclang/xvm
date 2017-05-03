package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFutureRef;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xService;
import org.xvm.proto.template.xService.ServiceHandle;

import java.util.concurrent.CompletableFuture;

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

    ServiceHandle m_hService;
    Frame m_frameCurrent;

    ServiceDaemon m_daemon;

    ServiceContext(Container container)
        {
        f_container = container;
        f_heapGlobal = container.f_heapGlobal;
        f_types = container.f_types;
        f_constantPool = container.f_constantPoolAdapter;
        }

    public Frame getCurrentFrame()
        {
        return m_frameCurrent;
        }

    public static ServiceContext getCurrentContext()
        {
        return ServiceDaemon.s_tloContext.get();
        }

    public void setService(ServiceHandle hService)
        {
        assert m_hService == null;
        m_hService = hService;
        }

    @Override
    public String toString()
        {
        return "Service(" + m_daemon.getThread().getName() + ')';
        }

    // create a new frame that returns zero or one value into the specified slot
    public Frame createFrame1(Frame framePrev, InvocationTemplate template,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, framePrev, template, hTarget, ahVar,
                iReturn < 0 ? Utils.ARGS_NONE : new int[] {iReturn});
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

        for (int i = 0; i < cReturns; i++)
            {
            // create a "dynamic ref" for all return values
            // (see DVar op-code)
            FutureHandle hRef = xFutureRef.makeHandle(new CompletableFuture<>());

            Frame.VarInfo info = new Frame.VarInfo(hRef.f_clazz);
            info.m_fDynamicRef = true;

            frame.f_aInfo[i] = info;
            frame.f_ahVar[i] = hRef;
            }
        return frame;
        }

    public ExceptionHandle start(String sServiceName)
        {
        String sThreadName = f_container.m_constModule.getQualifiedName() + "/" + sServiceName;

        // TODO: we need to be able to share native threads across services
        ServiceDaemon daemon = m_daemon = new ServiceDaemon(sThreadName, this);
        daemon.start();

        while (!daemon.isStarted())
            {
            // TODO: timeout
            yield();
            }

        return null;
        }

    // ----- x:Service methods -----

    // wait for any messages coming in
    public void yield()
        {
        m_daemon.dispatch(100l);
        }

    public boolean isContended()
        {
        return m_daemon.isContended();
        }

    // send and asynchronous "construct service" message
    public CompletableFuture<ServiceHandle> sendConstructRequest(ServiceContext context,
                ConstructTemplate constructor, TypeComposition clazz, ObjectHandle[] ahArg)
        {
        CompletableFuture<ServiceHandle> future = new CompletableFuture<>();

        context.m_daemon.add(new ConstructRequest(this, constructor, clazz, future, ahArg));

        return future.whenComplete((r, x) ->
            {
            if (x != null)
                {
                context.m_daemon.kill();
                }
            });
        }

    // send and asynchronous "invoke" message with zero or one return value
    public CompletableFuture<ObjectHandle> sendInvoke1Request(
            ServiceContext context, FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        context.m_daemon.add(new Invoke1Request(this, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous "invoke" message with multiple return values
    public CompletableFuture<ObjectHandle[]> sendInvokeNRequest(ServiceContext context,
                FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle[]> future = new CompletableFuture<>();

        context.m_daemon.add(new InvokeNRequest(this, hFunction, ahArg, cReturns, future));

        return future;
        }

    // send and asynchronous "get" message
    public CompletableFuture<ObjectHandle> sendGetRequest(ServiceContext context,
                PropertyTemplate property)
        {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();

        context.m_daemon.add(new GetRequest(this, property, future));

        return future;
        }

    // send and asynchronous "set" message
    public CompletableFuture<Void> sendSetRequest(ServiceContext context,
                PropertyTemplate property, ObjectHandle hValue)
        {
        CompletableFuture<Void> future = new CompletableFuture<>();

        context.m_daemon.add(new SetRequest(this, property, hValue, future));

        return future;
        }

    // ----- helpers ------

    // return an asynchronous call result
    public static <T> void sendResponse(ServiceContext context, ExceptionHandle hException,
                                 T returnValue, CompletableFuture<T> future)
        {
        context.m_daemon.add(new Response(hException, returnValue, future));
        }

    // send zero or one results back to the caller
    protected static void sendResponse1(ServiceContext contextCaller, ExceptionHandle hException,
                                        Frame frame, int cReturns, CompletableFuture future)
        {
        if (hException == null)
            {
            if (cReturns == 0)
                {
                sendResponse(contextCaller, null, null, future);
                }
            else
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
                else
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
            }
        else
            {
            sendResponse(contextCaller, hException, null, future);
            }
        }

    public interface Message
        {
        void process(ServiceContext context);
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
        public void process(ServiceContext context)
            {
            Frame frame = context.createServiceEntryFrame(1);

            xService template = (xService) f_constructor.getClazzTemplate();

            ExceptionHandle hException = template.construct(frame, f_constructor, f_clazz, f_ahArg, 0);

            sendResponse1(f_contextCaller, hException, frame, 1, f_future);
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
        public void process(ServiceContext context)
            {
            Frame frame = context.createServiceEntryFrame(f_hFunction.getReturnCount());

            ExceptionHandle hException = f_hFunction.call1(frame, f_ahArg, f_cReturns - 1);

            sendResponse1(f_contextCaller, hException, frame, f_cReturns, f_future);
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
        public void process(ServiceContext context)
            {
            Frame frame = context.createServiceEntryFrame(f_hFunction.getReturnCount());

            // the pseudo-frame's vars are the return values
            int[] aiReturn = new int[f_cReturns];
            for (int i = 0; i < f_cReturns; i++)
                {
                aiReturn[i] = i;
                }

            ExceptionHandle hException = f_hFunction.callN(frame, f_ahArg, aiReturn);

            if (hException == null)
                {
                // TODO: optimized for a case when all futures are complete
                CompletableFuture<Void>[] acf = new CompletableFuture[f_cReturns];
                for (int i = 0; i < f_cReturns; i++)
                    {
                    int iRet = i;

                    acf[i] = ((FutureHandle) frame.f_ahVar[i]).m_future.thenAccept(
                            h -> frame.f_ahVar[iRet] = h);
                    }

                CompletableFuture.allOf(acf).whenComplete((none, x) ->
                    {
                    if (x == null)
                        {
                        sendResponse(f_contextCaller, null, frame.f_ahVar, f_future);
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
            }
        }

    /**
     * Represents a property Get request from one service onto another.
     */
    public static class GetRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final PropertyTemplate f_property;
        private final CompletableFuture<ObjectHandle> f_future;

        public GetRequest(ServiceContext contextCaller, PropertyTemplate property,
                             CompletableFuture<ObjectHandle> future)
            {
            f_contextCaller = contextCaller;
            f_property = property;
            f_future = future;
            }

        @Override
        public void process(ServiceContext context)
            {
            Frame frame = context.createServiceEntryFrame(1);

            ExceptionHandle hException = f_property.getClazzTemplate().getPropertyValue(
                    frame, context.m_hService, f_property, 0);

            sendResponse1(f_contextCaller, hException, frame, 1, f_future);
            }
        }

    /**
     * Represents a property Set request from one service onto another.
     */
    public static class SetRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final PropertyTemplate f_property;
        private final ObjectHandle f_hValue;
        private final CompletableFuture<Void> f_future;

        public SetRequest(ServiceContext contextCaller, PropertyTemplate property,
                          ObjectHandle hValue, CompletableFuture<Void> future)
            {
            f_contextCaller = contextCaller;
            f_property = property;
            f_hValue = hValue;
            f_future = future;
            }

        @Override
        public void process(ServiceContext context)
            {
            Frame frame = context.createServiceEntryFrame(0);

            ExceptionHandle hException = f_property.getClazzTemplate().setPropertyValue(
                    frame, context.m_hService, f_property, f_hValue);

            sendResponse1(f_contextCaller, hException, frame, 0, f_future);
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
        public void process(ServiceContext context)
            {
            if (f_hException == null)
                {
                f_future.complete(f_return);
                }
            else
                {
                f_future.completeExceptionally(f_hException.m_exception);
                }
            }
        }
    }
