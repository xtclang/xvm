package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.Access;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;
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

    @Override
    public String toString()
        {
        return "Service(" + m_daemon.getThread().getName() + ')';
        }

    public Frame createFrame(Frame framePrev, InvocationTemplate template,
                             ObjectHandle hTarget, ObjectHandle[] ahVar)
        {
        return new Frame(this, framePrev, template, hTarget, ahVar);
        }

    public ExceptionHandle start(ServiceHandle hService, String sServiceName)
        {
        m_hService = (ServiceHandle) hService.f_clazz.ensureAccess(hService, Access.Public);

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

    // send and asynchronous "invoke" message
    public CompletableFuture<ObjectHandle[]> sendInvokeRequest(ServiceContext context,
                FunctionHandle hFunction, ObjectHandle[] ahArg, int cReturns)
        {
        CompletableFuture<ObjectHandle[]> future = new CompletableFuture<>();

        context.m_daemon.add(new InvokeRequest(this, hFunction, ahArg, cReturns, future));

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

    // return an asynchronous call result
    public <T> void sendResponse(ServiceContext context, ExceptionHandle hException,
                                 T returnValue, CompletableFuture<T> future)
        {
        context.m_daemon.add(new Response(hException, returnValue, future));
        }

    public interface Message
        {
        void process(ServiceContext context);
        }

    /**
     * Represents an invoke request from one service onto another.
     */
    public static class InvokeRequest
            implements Message
        {
        private final ServiceContext f_contextCaller;
        private final FunctionHandle f_hFunction;
        private final ObjectHandle[] f_ahArg;
        private final int f_cReturns;
        private final CompletableFuture<ObjectHandle[]> f_future;

        public InvokeRequest(ServiceContext contextCaller, FunctionHandle hFunction,
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
            ObjectHandle[] ahReturn = f_cReturns == 0 ? Utils.OBJECTS_NONE : new ObjectHandle[f_cReturns];

            ExceptionHandle hException = f_hFunction.invoke(context, null,
                    f_ahArg[0], f_hFunction.prepareVars(f_ahArg), ahReturn);

            context.sendResponse(f_contextCaller, hException, ahReturn, f_future);
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
            ObjectHandle[] ahReturn = new ObjectHandle[1];
            ExceptionHandle hException = f_property.getClazzTemplate().getProperty(
                    f_property, f_property.m_templateGet, null, context.m_hService, ahReturn, 0);

            context.sendResponse(f_contextCaller, hException, ahReturn[0], f_future);
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
            ExceptionHandle hException = f_property.getClazzTemplate().setProperty(
                    f_property, f_property.m_templateSet, null, context.m_hService, f_hValue);

            context.sendResponse(f_contextCaller, hException, null, f_future);
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
// try {System.out.println(this + " completing");Thread.sleep(10000);}catch (Throwable e) {}
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
