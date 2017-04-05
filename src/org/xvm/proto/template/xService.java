package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.GenericHandle;

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

    public void start(ServiceHandle hService)
        {
        StringHandle hName = getProperty(hService, "serviceName").as(StringHandle.class);
        hService.m_context.startServiceDaemon(hName.getValue());
        }

    public ObjectHandle invokeAsync(Frame frame, FunctionHandle hFunction, ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
        {
        throw new UnsupportedOperationException("TODO");
        }

    @Override
    public ObjectHandle invokeNative01(Frame frame, ObjectHandle hTarget, MethodTemplate method, ObjectHandle[] ahReturn)
        {
        ServiceHandle hThis = hTarget.as(ServiceHandle.class);

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
        protected CompletableFuture<ObjectHandle> m_future;
        protected ServiceHandle m_hService;

        public ProxyHandle(TypeComposition clazz, CompletableFuture<ObjectHandle> future, ServiceHandle hService)
            {
            super(clazz);

            m_future = future;
            m_hService = hService;
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
            {
            if (clz == FutureHandle.class)
                {
                return (T) xFutureRef.INSTANCE.makeHandle(m_future);
                }
            return get().as(clz);
            }

        protected ObjectHandle get()
            {
            try
                {
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
