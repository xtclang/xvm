package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;

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
    public static xService INSTANCE;

    public xService(TypeSet types)
        {
        super(types, "x:Service", "x:Object", Shape.Interface);

        INSTANCE = this;
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

        f_types.ensureTemplate("x:FutureRef");

        ensurePropertyTemplate("serviceName", "x:String").makeAtomic();
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ServiceHandle(clazz);
        }

    @Override
    public ObjectHandle createStruct()
        {
        ServiceContext contextCurr = ServiceContext.getCurrentContext();

        ServiceContext context = contextCurr.f_container.createContext();
        ServiceHandle hService = new ServiceHandle(
                f_clazzCanonical, f_clazzCanonical.ensureStructType(), context);

        hService.createFields();

        setField(hService, "serviceName", xString.makeHandle(f_sName));
        return hService;
        }

    public ExceptionHandle start(ServiceHandle hService)
        {
        return hService.m_context.start(hService, f_sName);
        }

    // return an exception
    public ExceptionHandle asyncInvoke(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
                                       ObjectHandle[] ahArg, ObjectHandle[] ahReturn)
        {
        // TODO: validate that all the arguments are immutable or ImmutableAble

        CompletableFuture<ObjectHandle[]> cfResult = frame.f_context.sendInvokeRequest(
                hService.m_context, hFunction, ahArg, ahReturn.length);

        InvocationTemplate function = hFunction.m_invoke;
        TypeComposition clzService = hService.f_clazz;

        int cReturns = ahReturn.length;
        if (cReturns > 0)
            {
            for (int i = 0; i < cReturns; i++)
                {
                final int iRet = i;

                CompletableFuture<ObjectHandle> cfReturn =
                        cfResult.thenApply(ahResult -> ahResult[iRet]);

                ahReturn[i] = xFutureRef.makeHandle(function.getReturnType(i, clzService), cfReturn);
                }
            }
        else
            {
            cfResult.whenComplete((r, x) ->
                {
                if (x != null)
                    {
                    // TODO: call UnhandledExceptionNotification handler
                    System.out.println(ServiceContext.getCurrentContext() + ": unhandled exception " + x);
                    }
                });
            }

        return null;
        }

    @Override
    public ExceptionHandle getProperty(PropertyTemplate property, MethodTemplate method,
                                       Frame frame, ObjectHandle hTarget, ObjectHandle[] ahReturn, int iRet)
        {
        ServiceContext context = ServiceContext.getCurrentContext();
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (context == hService.m_context || property.isAtomic())
            {
            return super.getProperty(property, method, frame, hTarget, ahReturn, iRet);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendGetRequest(
                hService.m_context, property);

        TypeComposition clzService = hService.f_clazz;

        ObjectHandle hValue = xFutureRef.makeHandle(property.getType(clzService), cfResult);

        if (ahReturn == null)
            {
            return frame.assignValue(iRet, hValue);
            }

        ahReturn[iRet] = hValue;
        return null;
        }

    @Override
    public ExceptionHandle setProperty(PropertyTemplate property, MethodTemplate method,
                                       Frame frame, ObjectHandle hTarget, ObjectHandle hValue)
        {
        ServiceContext context = ServiceContext.getCurrentContext();
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (context == hService.m_context || property.isAtomic())
            {
            return super.setProperty(property, method, frame, hTarget, hValue);
            }

        CompletableFuture<Void> cfResult = frame.f_context.sendSetRequest(
                hService.m_context, property, hValue);

        cfResult.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // TODO: call UnhandledExceptionNotification handler
                System.out.println(ServiceContext.getCurrentContext() + ": unhandled exception " + x);
                }
            });

        return null;
        }

    @Override
    public ExceptionHandle invokeNative01(Frame frame, ObjectHandle hTarget, MethodTemplate method,
                                          ObjectHandle[] ahReturn, int iRet)
        {
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

        public ServiceHandle(TypeComposition clazz, Type type, ServiceContext context)
            {
            super(clazz, type);

            m_context = context;
            }
        }
    }
