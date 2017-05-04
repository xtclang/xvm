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
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        ServiceHandle hService = new ServiceHandle(
                f_clazzCanonical, f_clazzCanonical.ensureStructType(), frame.f_context);

        hService.createFields();

        setFieldValue(hService, getPropertyTemplate("serviceName"), xString.makeHandle(f_sName));

        frame.f_context.setService(hService);

        return hService;
        }

    public ExceptionHandle asyncConstruct(Frame frame, ConstructTemplate constructor,
                                          TypeComposition clazz, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext contextCur = frame.f_context;
        ServiceContext contextNew = contextCur.f_container.createContext();

        ExceptionHandle hException = contextNew.start(f_sName);

        if (hException == null)
            {
            CompletableFuture cfService =
                    contextCur.sendConstructRequest(contextNew, constructor, clazz, ahArg);
            hException = frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfService));
            }
        return hException;
        }

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
    public ExceptionHandle invokePreInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.invokePreInc(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendProperty01Request(
                hService.m_context, property, this::invokePreInc);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
        }

    @Override
    public ExceptionHandle invokePostInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.invokePostInc(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendProperty01Request(
                hService.m_context, property, this::invokePostInc);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
        }

    @Override
    public ExceptionHandle getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.getPropertyValue(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = frame.f_context.sendProperty01Request(
                hService.m_context, property, this::getPropertyValue);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfResult));
        }

    @Override
    public ExceptionHandle getFieldValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.getFieldValue(frame, hTarget, property, iReturn);
            }
        throw new IllegalStateException("Invalid context");
        }

    @Override
    public ExceptionHandle setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
                                            ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.setPropertyValue(frame, hTarget, property, hValue);
            }

        CompletableFuture<Void> cfResult = frame.f_context.sendProperty10Request(
                hService.m_context, property, hValue, this::setPropertyValue);

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
    public ExceptionHandle setFieldValue(ObjectHandle hTarget, PropertyTemplate property, ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        ServiceContext context = hService.m_context;
        ServiceContext contextCurrent = ServiceContext.getCurrentContext();

        if (context == null || context == contextCurrent || property.isAtomic())
            {
            return super.setFieldValue(hTarget, property, hValue);
            }

        throw new IllegalStateException("Invalid context");
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

    // an operation against a property that takes no parameters and returns one value
    @FunctionalInterface
    public interface PropertyOperation01
        {
        ExceptionHandle invoke(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn);
        }

    // an operation against a property that takes one parameter and returns zero values
    @FunctionalInterface
    public interface PropertyOperation10
        {
        ExceptionHandle invoke(Frame frame, ObjectHandle hTarget, PropertyTemplate property, ObjectHandle hValue);
        }
    }
