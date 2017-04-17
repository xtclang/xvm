package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xString.StringHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xService
        extends TypeCompositionTemplate
    {
    public xService(TypeSet types)
        {
        super(types, "x:Service", "x:Object", Shape.Interface);
        }

    // subclassing
    protected xService(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    @atomic String serviceName;
        //    enum StatusIndicator {Idle, Busy, ShuttingDown, Terminated};
        //    @ro @atomic StatusIndicator statusIndicator;
        //    @ro @atomic CriticalSection? criticalSection;
        //    enum Reentrancy {Prioritized, Open, Exclusive, Forbidden};
        //    @atomic Reentrancy reentrancy;
        //    @ro @atomic Timeout? incomingTimeout;
        //    @ro @atomic Timeout? timeout;
        //    @ro @atomic Duration upTime;
        //    @ro @atomic Duration cpuTime;
        //    @ro @atomic Boolean contended;
        //    @ro @atomic Int backlogDepth;
        //    Void yield();
        //    Void invokeLater(function Void doLater());
        //    @ro @atomic Int bytesReserved;
        //    @ro @atomic Int bytesAllocated;
        //    Void gc();
        //    Void shutdown();
        //    Void kill();
        //    Void registerTimeout(Timeout? timeout);
        //    Void registerCriticalSection(CriticalSection? criticalSection);
        //    Void registerShuttingDownNotification(function Void notify());
        //    Void registerUnhandledExceptionNotification(function Void notify(Exception));

        PropertyTemplate pt;

        pt = ensurePropertyTemplate("serviceName", "x:String");
        pt.makeAtomic();
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ServiceHandle(clazz);
        }

    @Override
    public ObjectHandle createStruct()
        {
        return createService(null);
        }

    public ServiceHandle createService(String sName)
        {
        ServiceContext contextCurr = ServiceContext.getCurrentContext();

        ServiceContext context = contextCurr.f_container.createContext();
        ServiceHandle hService = new ServiceHandle(f_clazzCanonical, context);

        hService.createFields();

        if (sName == null)
            {
            sName = getClass().getSimpleName();
            }

        setProperty(hService, "serviceName", xString.makeHandle(sName));
        return hService;
        }

    public ExceptionHandle start(ServiceHandle hService)
        {
        ObjectHandle[] ahName = new ObjectHandle[1];
        getProperty(hService, "serviceName", ahName);

        StringHandle hName = (StringHandle) ahName[0];
        return hService.m_context.start(hService, hName.getValue());
        }

    // return an exception
    public ExceptionHandle invokeAsync(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
                                    ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
        {
        // TODO: validate that all the arguments are immutable or ImmutableAble

        CompletableFuture<ObjectHandle[]> cfResult = frame.f_context.sendRequest(
                hService.m_context, hFunction, ahArg, ahReturn.length);

        InvocationTemplate function = hFunction.m_invoke;
        TypeComposition clzService = hService.f_clazz;

        for (int i = 0, c = ahReturn.length; i < c; i++)
            {
            final int iRet = i;

            CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahReturn[iRet] = ahResult[iRet]);

            ahReturn[i] = new ProxyHandle(function.getReturnType(i, clzService), cfReturn);
            }

        return null;
        }

    @Override
    public ExceptionHandle invokeNative01(Frame frame, ObjectHandle hTarget, MethodTemplate method, ObjectHandle[] ahReturn)
        {
        ServiceHandle hThis;
        try
            {
            hThis = hTarget.as(ServiceHandle.class);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return e.getExceptionHandle();
            }

        throw new IllegalStateException("Unknown method: " + method);
        }

    public static class ServiceHandle
            extends GenericHandle
        {
        public ServiceContext m_context;

        public ServiceHandle(TypeComposition clazz)
            {
            super(clazz);
            }
        public ServiceHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz);

            m_context = context;
            }
        }

    // a dynamic proxy handle automatically convertible to a FutureHandle or a "real" handle
    public static class ProxyHandle
            extends ObjectHandle
        {
        protected Type m_type;
        protected CompletableFuture<ObjectHandle> m_future;

        public ProxyHandle(Type type, CompletableFuture<ObjectHandle> future)
            {
            super(null, type);

            m_type = type;
            m_future = future;
            }

        @Override
        public <T extends ObjectHandle> T as(Class<T> clz)
                throws ExceptionHandle.WrapperException
            {
            if (clz == FutureHandle.class)
                {
                return (T) xFutureRef.INSTANCE.makeHandle(m_type, m_future);
                }
            return get().as(clz);
            }

        @Override
        public <T extends ObjectHandle> T as(TypeComposition clazz)
                throws ExceptionHandle.WrapperException
            {
            if (clazz.extends_(xFutureRef.INSTANCE.f_clazzCanonical))
                {
                return (T) xFutureRef.INSTANCE.makeHandle(m_type, m_future);
                }
            return get().as(clazz);
            }

        protected ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            try
                {
                // TODO: use the timeout defined on the service
                while (!m_future.isDone())
                    {
                    ServiceContext.getCurrentContext().yield();
                    }
                return m_future.get();
                }
            catch (InterruptedException e)
                {
                throw new UnsupportedOperationException("TODO");
                }
            catch (ExecutionException e)
                {
                Throwable eOrig = e.getCause();
                if (eOrig instanceof ExceptionHandle.WrapperException)
                    {
                    throw (ExceptionHandle.WrapperException) eOrig;
                    }
                throw new UnsupportedOperationException(e);
                }
            }
        }
    }
