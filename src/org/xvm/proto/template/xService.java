package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.ServiceContext;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.Utils;
import org.xvm.proto.template.xFunction.FunctionHandle;

import java.util.concurrent.CompletableFuture;

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
        ServiceHandle hService = new ServiceHandle(
                f_clazzCanonical, f_clazzCanonical.ensureStructType(), null);

        hService.createFields();

        setField(hService, getPropertyTemplate("serviceName"), xString.makeHandle(f_sName));

        return hService;
        }

    public ExceptionHandle start(ServiceHandle hService)
        {
        hService.m_context = ServiceContext.getCurrentContext().f_container.createContext();

        return hService.m_context.start(hService, f_sName);
        }

    // return an exception
    public ExceptionHandle asyncInvoke1(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
                                        ObjectHandle[] ahArg, int iReturn)
        {
        // TODO: validate that all the arguments are immutable or ImmutableAble
        int cReturns = iReturn < 0 ? 0 : 1;

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendInvoke1Request(
                hService.m_context, hFunction, ahArg, cReturns);

        if (cReturns == 1)
            {
            return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
            }
        else
            {
            cfResult.whenComplete((r, x) ->
                {
                if (x != null)
                    {
                    // TODO: call UnhandledExceptionNotification handler
                    Utils.log("unhandled exception " + x + "\n  by " + hService);
                    }
                });
            }

        return null;
        }

    // return an exception
    public ExceptionHandle asyncInvokeN(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
                                        ObjectHandle[] ahArg, int[] aiReturn)
        {
        // TODO: validate that all the arguments are immutable or ImmutableAble

        CompletableFuture<ObjectHandle[]> cfResult = frame.f_context.sendInvokeNRequest(
                hService.m_context, hFunction, ahArg, aiReturn.length);

        int cReturns = aiReturn.length;
        if (cReturns > 0)
            {
            for (int i = 0; i < cReturns; i++)
                {
                final int iRet = i;

                CompletableFuture<ObjectHandle> cfReturn =
                        cfResult.thenApply(ahResult -> ahResult[iRet]);

                frame.assignValue(aiReturn[i], xFutureRef.makeSyntheticHandle(cfReturn));
                }
            }
        else
            {
            cfResult.whenComplete((r, x) ->
                {
                if (x != null)
                    {
                    // TODO: call UnhandledExceptionNotification handler
                    Utils.log("unhandled exception " + x + "\n  by " + hService);
                    }
                });
            }

        return null;
        }

    @Override
    public ExceptionHandle getProperty(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
                                       int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.getProperty(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendGetRequest(
                hService.m_context, property);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
        }

    @Override
    public ExceptionHandle getField(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.getField(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendGetRequest(
                hService.m_context, property);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
        }

    @Override
    public ExceptionHandle setProperty(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
                                       ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.setProperty(frame, hTarget, property, hValue);
            }

        CompletableFuture<Void> cfResult = frame.f_context.sendSetRequest(
                hService.m_context, property, hValue);

        cfResult.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // TODO: call UnhandledExceptionNotification handler
                Utils.log("unhandled exception " + x + "\n  by " + hService);
                }
            });

        return null;
        }

    @Override
    public ExceptionHandle setField(ObjectHandle hTarget, PropertyTemplate property, ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        ServiceContext context = hService.m_context;
        ServiceContext contextCurrent = ServiceContext.getCurrentContext();

        if (context == null || context == contextCurrent || property.isAtomic())
            {
            return super.setField(hTarget, property, hValue);
            }

        CompletableFuture<Void> cfResult = contextCurrent.sendSetRequest(context, property, hValue);

        cfResult.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // TODO: call UnhandledExceptionNotification handler
                Utils.log("unhandled exception " + x + "\n  by " + hService);
                }
            });

        return null;
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
