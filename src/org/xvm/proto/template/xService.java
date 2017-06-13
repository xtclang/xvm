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
        // +  @atomic String serviceName;
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
        // +  Void yield();
        // +  Void invokeLater(function Void doLater());
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

        ensureMethodTemplate("yield", VOID, VOID).markNative();
        ensureMethodTemplate("invokeLater", new String[] {"x:Function"}, VOID).markNative();
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        ServiceContext context = frame.f_context;

        ServiceHandle hService = new ServiceHandle(clazz, clazz.ensureStructType(), context);

        context.setService(hService);

        setFieldValue(hService, getPropertyTemplate("serviceName"), xString.makeHandle(f_sName));

        return hService;
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (method.f_sName)
            {
            case "invokeLater":
                {
                return hService.m_context.callLater((FunctionHandle) hArg, Utils.OBJECTS_NONE);
                }
            }

        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (method.f_sName)
            {
            case "yield":
                {
                return frame.f_context == hService.m_context ? Op.R_YIELD : Op.R_NEXT;
                }
            }

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || property.isAtomic())
            {
            return super.invokePreInc(frame, hTarget, property, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, property, this::invokePreInc);

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

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, property, this::invokePostInc);

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

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, property, this::getPropertyValue);

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

        hService.m_context.sendProperty10Request(
                frame.f_fiber, property, hValue, this::setPropertyValue);

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

    @Override
    public int construct(Frame frame, ConstructTemplate constructor,
                         TypeComposition clazz, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext contextNew = frame.f_context.f_container.createServiceContext(f_sName);

        CompletableFuture cfService = contextNew.sendConstructRequest(frame, constructor, clazz, ahArg);

        return frame.assignValue(iReturn, xFutureRef.makeSyntheticHandle(cfService));
        }

    // ----- Service API -----

    public int constructSync(Frame frame, ConstructTemplate constructor,
                             TypeComposition clazz, ObjectHandle[] ahArg, int iReturn)
        {
        return super.construct(frame, constructor, clazz, ahArg, iReturn);
        }


    // ----- ObjectHandle -----

    public static ServiceHandle makeHandle(ServiceContext context)
        {
        TypeComposition clz = INSTANCE.f_clazzCanonical;

        return new ServiceHandle(clz, clz.ensurePublicType(), context);
        }

    public static class ServiceHandle
            extends GenericHandle
        {
        public ServiceContext m_context;

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
