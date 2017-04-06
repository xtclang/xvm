package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;

import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xString.StringHandle;

import java.util.concurrent.CompletableFuture;

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
    public ObjectHandle createStruct(Frame frame)
        {
        ServiceContext context = frame.f_context.f_container.createContext();
        ServiceHandle hService = new ServiceHandle(f_clazzCanonical, context);

        hService.createFields();

        setProperty(hService, "serviceName",
                xString.makeHandle(getClass().getSimpleName()));
        return hService;
        }

    public ExceptionHandle start(ServiceHandle hService)
        {
        ObjectHandle[] ahRet = new ObjectHandle[1];
        getProperty(hService, "serviceName", ahRet);

        try
            {
            StringHandle hName = ahRet[0].as(StringHandle.class);
            return hService.m_context.start(hService, hName.getValue());
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return e.getExceptionHandle();
            }
        }

    // return an exception
    public ExceptionHandle invokeAsync(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
                                    ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
        {
        // TODO: validate that all the arguments are immutable or ImmutableAble

        CompletableFuture<ObjectHandle[]> cfResult = frame.f_context.sendRequest(
                hService.m_context, hFunction, ahArg, ahReturn.length);

        for (int i = 0, c = ahReturn.length; i < c; i++)
            {
            final int iRet = i;

            CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahReturn[iRet] = ahResult[iRet]);

            ahReturn[i] = new ProxyHandle(hService, cfReturn);
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
        protected ServiceContext m_context;
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
        protected ServiceHandle m_hService;
        protected CompletableFuture<ObjectHandle> m_future;

        public ProxyHandle(ServiceHandle hService, CompletableFuture<ObjectHandle> future)
            {
            // TODO: actually, the ProxyHandle has nothing to do with the xService template
            super(hService.f_clazz);

            m_hService = hService;
            m_future = future;
            }

        @Override
        public <T extends ObjectHandle> boolean isAssignableTo(Class<T> clz)
            {
            if (clz == FutureHandle.class)
                {
                return true;
                }
            return get().isAssignableTo(clz);
            }

        @Override
        public <T extends ObjectHandle> T as(Class<T> clz)
                throws ExceptionHandle.WrapperException
            {
            if (clz == FutureHandle.class)
                {
                return (T) xFutureRef.INSTANCE.makeHandle(m_hService, m_future);
                }
            return get().as(clz);
            }

        protected ObjectHandle get()
            {
            try
                {
                // TODO: use the timeout defined on the service
                while (!m_future.isDone())
                    {
                    m_hService.m_context.yield();
                    }
                return m_future.get();
                }
            catch (Exception e )
                {
                // pass it onto the service handle
                throw new UnsupportedOperationException();
                }
            }
        }
    }
