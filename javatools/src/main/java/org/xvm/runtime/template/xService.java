package org.xvm.runtime.template;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.ServiceContext.Reentrancy;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.xRTServiceControl;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native Service implementation.
 */
public class xService
        extends ClassTemplate
    {
    public static xService INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public xService(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
        new InterfaceProxy(f_templates); // this initializes the InterfaceProxy.INSTANCE reference
        }

    @Override
    public void initNative()
        {
        REENTRANCY = (xEnum) f_templates.getTemplate("Service.Reentrancy");

        // since Service is an interface, we cannot annotate the properties naturally and need to do
        // an ad-hoc check (the list is to be updated)
        Set<String> setAtomic = new HashSet<>();
        setAtomic.add("serviceName");
        setAtomic.add("serviceControl");
        setAtomic.add("timeout");
        s_setAtomicProperties = setAtomic;
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return this == INSTANCE ? INCEPTION_CLASS : (ClassConstant) super.getInceptionClassConstant();
        }

    public boolean isService()
        {
        return true;
        }

    public int constructSync(Frame frame, MethodStructure constructor, ClassComposition clazz,
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
                    frame.f_context.setService(hService);
                    return frame.assignValue(iReturn, hService);
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext contextNew = frame.f_context.f_container.createServiceContext(f_sName);

        return contextNew.sendConstructRequest(frame, constructor, clazz, hParent, ahArg, iReturn);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, ClassComposition clazz)
        {
        // called via constructSync()
        ServiceContext context = frame.f_context;
        ServiceHandle  hStruct = new ServiceHandle(clazz.ensureAccess(Access.STRUCT), context);
        context.setService(hStruct);
        return hStruct;
        }

    @Override
    protected int makeImmutable(Frame frame, ObjectHandle hTarget)
        {
        return frame.raiseException(xException.unsupportedOperation(frame, "makeImmutable"));
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invoke1(frame, chain, hTarget, ahVar, iReturn) :
            xRTFunction.makeAsyncHandle(chain).call1(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invokeT(frame, chain, hTarget, ahVar, iReturn) :
            xRTFunction.makeAsyncHandle(chain).callT(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).f_context || chain.isAtomic() ?
            super.invokeN(frame, chain, hTarget, ahVar, aiReturn) :
            xRTFunction.makeAsyncHandle(chain).callN(frame, hTarget, ahVar, aiReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (method.getName())
            {
            case "callLater":
                {
                return hService.f_context.callLater((FunctionHandle) hArg, Utils.OBJECTS_NONE, false);
                }

            case "registerTimeout":
                if (frame.f_context == hService.f_context)
                    {
                    frame.f_fiber.setTimeoutHandle(hArg);
                    }
                else
                    {
                    hService.f_context.setTimeoutHandle(hArg);
                    }
                return Op.R_NEXT;

            case "registerAsyncSection":
                if (frame.f_context != hService.f_context)
                    {
                    return frame.raiseException("Call out of context");
                    }
                return frame.f_fiber.registerAsyncSection(frame, hArg);

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

            case "yield":
                return frame.f_context == hService.f_context
                    ? Op.R_YIELD
                    : Op.R_NEXT;
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
                // the service -- within the context of the container (and container == typesystem)
                assert frame.f_context == hService.f_context;
                return frame.f_context.f_container.ensureTypeSystemHandle(frame, iReturn);

            case "serviceName":
                return frame.assignValue(iReturn, xString.makeHandle(hService.f_context.f_sName));

            case "serviceControl":
                return frame.assignValue(iReturn, xRTServiceControl.makeHandle(hService.f_context));

            case "timeout":
                {
                ObjectHandle  hTimeout = frame.f_context == hService.f_context
                    ? frame.f_fiber.getTimeoutHandle()
                    : hService.f_context.getTimeoutHandle();
                return frame.assignValue(iReturn, hTimeout);
                }

            case "reentrancy":
                {
                EnumHandle hReentrancy = REENTRANCY.getEnumByName(
                        hService.f_context.m_reentrancy.name());
                return Utils.assignInitializedEnum(frame, hReentrancy, iReturn);
                }

            case "asyncSection":
                return frame.assignValue(iReturn, frame.f_fiber.getAsyncSection());
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        switch (sPropName)
            {
            case "reentrancy":
                {
                EnumHandle hReentrancy = (EnumHandle) hValue;

                hService.f_context.m_reentrancy = Reentrancy.valueOf(hReentrancy.getName());
                return Op.R_NEXT;
                }
            }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePreInc(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, idProp, iReturn, this::invokePreInc);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePostInc(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, idProp, iReturn, this::invokePostInc);
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePreDec(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, idProp, iReturn, this::invokePreDec);
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePostDec(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, idProp, iReturn, this::invokePostDec);
        }

    @Override
    public int invokePropertyAdd(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePropertyAdd(frame, hTarget, idProp, hArg);
            }

        return hService.f_context.sendProperty10Request(frame, idProp, hArg, this::invokePropertyAdd);
        }

    @Override
    public int invokePropertySub(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.invokePropertySub(frame, hTarget, idProp, hArg);
            }

        return hService.f_context.sendProperty10Request(frame, idProp, hArg, this::invokePropertySub);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.f_context || hService.isAtomic(idProp))
            {
            return super.getPropertyValue(frame, hTarget, idProp, iReturn);
            }

        return hService.f_context.sendProperty01Request(frame, idProp, iReturn, this::getPropertyValue);
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

        return hService.f_context.sendProperty10Request(frame, idProp, hValue, this::setPropertyValue);
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

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;
        return frame.assignValue(iReturn, xString.makeHandle(hService.f_context.toString()));
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
        public boolean isAtomic(PropertyConstant idProp)
            {
            return s_setAtomicProperties.contains(idProp.getName()) || super.isAtomic(idProp);
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
        int invoke(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn);
        }

    // an operation against a property that takes one parameter and returns zero values
    @FunctionalInterface
    public interface PropertyOperation10
            extends PropertyOperation
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue);
        }

    // an operation against a property that takes one parameter and returns one value
    @FunctionalInterface
    public interface PropertyOperation11
            extends PropertyOperation
        {
        int invoke(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                   ObjectHandle hValue, int iReturn);
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
    protected static xEnum REENTRANCY;

    /**
     * Names of atomic properties.
     */
    private static Set<String> s_setAtomicProperties;
    }
