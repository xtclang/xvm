package org.xvm.runtime.template;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;

import org.xvm.runtime.template.reflect.xClass;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.xRTServiceControl;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template._native.temporal.xNanosTimer;


/**
 * Native Service implementation.
 */
public class xService
        extends ClassTemplate
    {
    public static xService INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public xService(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        new Proxy(f_container); // this initializes the Proxy.INSTANCE reference
        }

    @Override
    public void initNative()
        {
        if (this == INSTANCE)
            {
            SYNCHRONICITY = (xEnum) f_container.getTemplate("Service.Synchronicity");

            // since Service is an interface, we cannot annotate the properties naturally and need to do
            // an ad-hoc check (the list is to be updated)
            Set<String> setAtomic = new HashSet<>();
            setAtomic.add("serviceName");
            setAtomic.add("serviceControl");
            setAtomic.add("timeout");
            s_setAtomicProperties = setAtomic;

            IdentityConstant idTimeout  = pool().getImplicitlyImportedIdentity("Timeout");
            ClassStructure   clzTimeout = (ClassStructure) idTimeout.getComponent();
            REMAINING_TIME = (PropertyConstant) clzTimeout.getChild("remainingTime").getIdentityConstant();
            }
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return this == INSTANCE ? INCEPTION_CLASS : (ClassConstant) super.getInceptionClassConstant();
        }

    /**
     * The part of the construction logic that is executed on the constructed service's context.
     */
    public int constructSync(Frame frame, MethodStructure constructor, TypeComposition clazz,
                             ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        switch (super.construct(frame, constructor, clazz, hParent, ahArg, Op.A_STACK))
            {
            case Op.R_NEXT:
                {
                ServiceHandle hService = (ServiceHandle) frame.popStack();
                frame.f_context.setService(hService);
                return frame.assignValue(iReturn, hService);
                }

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    ServiceHandle hService = (ServiceHandle) frameCaller.popStack();
                    frameCaller.f_context.setService(hService);
                    return frameCaller.assignValue(iReturn, hService);
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * The part of the struct allocation logic that is executed on the allocated service's context.
     * Note, that the clazz could be a virtual child of the parent's service.
     */
    public int allocateSync(Frame frame, TypeComposition clazz, ObjectHandle hParent, int iReturn)
        {
        ObjectHandle hStruct = clazz.getTemplate().createStruct(frame, clazz);

        switch (xClass.completeStructAllocation(frame, hStruct, hParent,
                            new int[] {Op.A_IGNORE, Op.A_STACK}))
            {
            case Op.R_NEXT:
                return frame.assignValue(iReturn, hStruct);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(iReturn, frameCaller.popStack()));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext context    = frame.f_context;
        ServiceContext contextNew = context.f_container.createServiceContext(f_sName);

        switch (context.validatePassThrough(frame, contextNew, constructor.getParamTypes(), ahArg))
            {
            case Op.R_NEXT:
                return contextNew.sendConstructRequest(
                        frame, clazz, constructor, hParent, ahArg, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    contextNew.sendConstructRequest(
                            frameCaller, clazz, constructor, hParent, ahArg, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        // called via constructSync() or allocateSync()
        ServiceContext context = frame.f_context;
        ServiceHandle  hStruct = new ServiceHandle(clazz.ensureAccess(Access.STRUCT), context);

        // prime the context with the service "struct" handle; it will be replaced with a "public"
        // one by constructSync() above
        context.setService(hStruct);
        return hStruct;
        }

    @Override
    protected boolean makeImmutable(ObjectHandle hTarget)
        {
        return false;
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invoke1(frame, chain, hTarget, ahVar, iReturn) :
            xRTFunction.makeAsyncHandle(frame, chain).call1(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invokeT(frame, chain, hTarget, ahVar, iReturn) :
            xRTFunction.makeAsyncHandle(frame, chain).callT(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invokeN(frame, chain, hTarget, ahVar, aiReturn) :
            xRTFunction.makeAsyncHandle(frame, chain).callN(frame, hTarget, ahVar, aiReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (method.getName())
            {
            case "callLater":
                return hService.f_context.callLater((FunctionHandle) hArg, Utils.OBJECTS_NONE) == null
                        ? frame.raiseException(xException.serviceTerminated(frame, f_sName))
                        : Op.R_NEXT;

            case "registerContextToken":
                return frame.f_context == hService.f_context
                        ? frame.raiseException("Not implemented")
                        : frame.raiseException("Call out of context");

            case "registerTimeout":
                if (frame.f_context != hService.f_context)
                    {
                    return frame.raiseException("Call out of context");
                    }

                if (hArg == xNullable.NULL)
                    {
                    frame.f_fiber.setTimeoutHandle(hArg, 0L);
                    return Op.R_NEXT;
                    }

                switch (hArg.getTemplate().getPropertyValue(frame, hArg, REMAINING_TIME, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        {
                        long cRemains = xNanosTimer.millisFromDuration(frame.popStack());
                        frame.f_fiber.setTimeoutHandle(hArg,
                                System.currentTimeMillis() + cRemains);
                        return Op.R_NEXT;
                        }
                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(frameCaller ->
                            {
                            long cRemains = xNanosTimer.millisFromDuration(frameCaller.popStack());
                            frameCaller.f_fiber.setTimeoutHandle(hArg,
                                    System.currentTimeMillis() + cRemains);
                            return Op.R_NEXT;
                            });
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "registerSynchronizedSection":
                return frame.f_context == hService.f_context
                        ? frame.f_context.setSynchronizedSection(frame, hArg)
                        : frame.raiseException("Call out of context");

            case "registerShuttingDownNotification":
                return frame.raiseException("Not implemented");

            case "registerAsyncSection":
                return frame.f_context == hService.f_context
                        ? frame.f_fiber.registerAsyncSection(frame, hArg)
                        : frame.raiseException("Call out of context");

            case "registerUnhandledExceptionNotification":
                hService.f_context.m_hExceptionHandler = (FunctionHandle) hArg;
                return Op.R_NEXT;
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (method.getName())
            {
            case "shutdown":
                // this method is called by the ServiceControl; it doesn't even exist on the Service
                assert frame.f_context == hService.f_context;
                return hService.f_context.shutdown(frame);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (sPropName)
            {
            case "typeSystem":
                // since typeSystem is NOT atomic, this code always executes within the context of
                // the service -- within the context of the container (and container == typeSystem)
                assert frame.f_context == hService.f_context;
                return frame.f_context.f_container.ensureTypeSystemHandle(frame, iReturn);

            case "serviceName":
                return frame.assignValue(iReturn, xString.makeHandle(hService.f_context.f_sName));

            case "serviceControl":
                return frame.assignValue(iReturn, xRTServiceControl.makeHandle(hService.f_context));

            case "timeout":
                return frame.f_context == hService.f_context
                        ? frame.assignValue(iReturn, frame.f_fiber.getTimeoutHandle())
                        : frame.raiseException("Call out of context");

            case "asyncSection":
                return frame.assignValue(iReturn, frame.f_fiber.getAsyncSection());

            case "synchronizedSection":
                ObjectHandle hCriticalSection = hService.f_context.getSynchronizedSection();
                return frame.assignValue(iReturn, hCriticalSection == null ? xNullable.NULL : hCriticalSection);

            case "synchronicity":
                {
                if (frame.f_context != hService.f_context)
                    {
                    return frame.raiseException("Call out of context");
                    }
                EnumHandle hSynchronicity = SYNCHRONICITY.getEnumByName(
                        frame.getSynchronicity().name());
                return Utils.assignInitializedEnum(frame, hSynchronicity, iReturn);
                }
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePreInc(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, hService, idProp, iReturn, this::invokePreInc);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePostInc(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, hService, idProp, iReturn, this::invokePostInc);
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePreDec(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, hService, idProp, iReturn, this::invokePreDec);
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePostDec(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, hService, idProp, iReturn, this::invokePostDec);
        }

    @Override
    public int invokePropertyAdd(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePropertyAdd(frame, hTarget, idProp, hArg);
            }

        return hService.f_context.sendProperty10Request(frame, hService, idProp, hArg, this::invokePropertyAdd);
        }

    @Override
    public int invokePropertySub(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePropertySub(frame, hTarget, idProp, hArg);
            }

        return hService.f_context.sendProperty10Request(frame, hService, idProp, hArg, this::invokePropertySub);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.getPropertyValue(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, hService, idProp, iReturn, this::getPropertyValue);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.getFieldValue(frame, hTarget, idProp, iReturn);
            }
        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.setPropertyValue(frame, hTarget, idProp, hValue);
            }

        return hService.f_context.sendProperty10Request(frame, hService, idProp, hValue, this::setPropertyValue);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (hService.f_context == frame.f_context || hService.isAtomic(idProp))
            {
            return super.setFieldValue(frame, hTarget, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Create a service handle.
     *
     * @param context   the service context
     * @param clz       the original class composition
     * @param typeMask  the type to mask the service as
     *
     * @return  the service handle
     */
    public ServiceHandle createServiceHandle(ServiceContext context,
                                             ClassComposition clz, TypeConstant typeMask)
        {
        ServiceHandle hService = new ServiceHandle(clz.maskAs(typeMask), context);
        context.setService(hService);
        return hService;
        }

    public static class ServiceHandle
            extends GenericHandle
        {
        public final ServiceContext f_context;

        public ServiceHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz);

            f_context = context;
            m_owner   = context.f_container;
            }

        @Override
        public boolean makeImmutable()
            {
            return false;
            }

        @Override
        public boolean isService()
            {
            return true;
            }

        @Override
        public ServiceHandle getService()
            {
            return this;
            }

        @Override
        public boolean isAtomic(PropertyConstant idProp)
            {
            return s_setAtomicProperties.contains(idProp.getName()) || super.isAtomic(idProp);
            }
        }

    // an operation against a property that takes no parameters and returns one value
    @FunctionalInterface
    public interface PropertyOperation01
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn);
        }

    // an operation against a property that takes one parameter and returns zero values
    @FunctionalInterface
    public interface PropertyOperation10
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue);
        }

    // native function adapters
    @FunctionalInterface
    public interface NativeOperation
        {
        int invoke(Frame frame, ObjectHandle[] ahArg, int iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Enum used by the native properties.
     */
    protected static xEnum SYNCHRONICITY;

    /**
     * Names of atomic properties.
     */
    private static Set<String> s_setAtomicProperties;

    /**
     * Property constant for "Timeout.remainingTime".
     */
    private static PropertyConstant REMAINING_TIME;
    }