package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
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

        ensurePropertyTemplate("serviceName", "x:String").makeAtomicRef();
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

        frame.f_context.setService(hService);

        setFieldValue(hService, getPropertyTemplate("serviceName"), xString.makeHandle(f_sName));

        return hService;
        }

    public int asyncConstruct(Frame frame, ConstructTemplate constructor,
                                          TypeComposition clazz, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext contextCur = frame.f_context;
        ServiceContext contextNew = contextCur.f_container.createServiceContext();

        ExceptionHandle hException = contextNew.start(f_sName);

        if (hException != null)
            {
            frame.m_hException = hException;
            return Op.R_EXCEPTION;
            }

        CompletableFuture cfService =
                contextCur.sendConstructRequest(contextNew, constructor, clazz, ahArg);
        frame.forceValue(iReturn, xFutureRef.makeSyntheticHandle(cfService));
        return Op.R_NEXT;
        }

    public int asyncInvoke1(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
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

        cfResult.whenComplete((r, x) ->
            {
            if (x != null)
                {
                // TODO: call UnhandledExceptionNotification handler
                Utils.log("\nUnhandled exception " + x + "\n  by " + hService);
                }
            });
        return Op.R_NEXT;
        }

    public int asyncInvokeN(Frame frame, ServiceHandle hService, FunctionHandle hFunction,
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

                if (frame.assignValue(aiReturn[i], xFutureRef.makeSyntheticHandle(cfReturn)) < 0)
                    {
                    return Op.R_EXCEPTION;
                    }
                }
            }
        else
            {
            cfResult.whenComplete((r, x) ->
                {
                if (x != null)
                    {
                    // TODO: call UnhandledExceptionNotification handler
                    Utils.log("\nUnhandled exception " + x + "\n  by " + hService);
                    }
                });
            }

        return Op.R_NEXT;
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
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
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
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
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
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
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.getFieldValue(frame, hTarget, property, iReturn);
            }
        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
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
                Utils.log("\nUnhandled exception " + x + "\n  by " + hService);
                }
            });

        return Op.R_NEXT;
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

    // a tag interface for property operations
    public interface PropertyOperation
        {
        }

    // an operation against a property that takes no parameters and returns one value
    @FunctionalInterface
    public interface PropertyOperation01
            extends PropertyOperation
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn);
        }

    // an operation against a property that takes one parameter and returns zero values
    @FunctionalInterface
    public interface PropertyOperation10
            extends PropertyOperation
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
                   ObjectHandle hValue);
        }

    // an operation against a property that takes one parameter and returns one value
    @FunctionalInterface
    public interface PropertyOperation11
            extends PropertyOperation
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyTemplate property,
                   ObjectHandle hValue, int iReturn);
        }
    }
