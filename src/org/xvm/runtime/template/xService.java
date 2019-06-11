package org.xvm.runtime.template;


import java.util.concurrent.CompletableFuture;

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
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.annotations.xFutureVar;
import org.xvm.runtime.template.xFunction.FunctionHandle;


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

            new InterfaceProxy(templates); // this initializes the InterfaceProxy.INSTANCE reference
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return this == INSTANCE ? INCEPTION_CLASS : super.getInceptionClassConstant();
        }

    public boolean isService()
        {
        return true;
        }

    public int constructSync(Frame frame, MethodStructure constructor,
                             ClassComposition clazz, ObjectHandle[] ahArg, int iReturn)
        {
        return super.construct(frame, constructor, clazz, null, ahArg, iReturn);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceContext contextNew = frame.f_context.createContext(f_sName);

        CompletableFuture cfService = contextNew.sendConstructRequest(frame, constructor, clazz, ahArg);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfService), true);
        }

    @Override
    protected ObjectHandle createStruct(Frame frame, ClassComposition clazz)
        {
        // called via constructSync()
        return new ServiceHandle(clazz.ensureAccess(Access.STRUCT), frame.f_context);
        }

    @Override
    protected ExceptionHandle makeImmutable(ObjectHandle hTarget)
        {
        return xException.unsupportedOperation();
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).m_context ?
            super.invoke1(frame, chain, hTarget, ahVar, iReturn) :
            xFunction.makeAsyncHandle(chain).call1(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).m_context ?
            super.invokeT(frame, chain, hTarget, ahVar, iReturn) :
            xFunction.makeAsyncHandle(chain).callT(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.f_context == ((ServiceHandle) hTarget).m_context ?
            super.invokeN(frame, chain, hTarget, ahVar, aiReturn) :
            xFunction.makeAsyncHandle(chain).callN(frame, hTarget, ahVar, aiReturn);
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
                return hService.m_context.callLater((FunctionHandle) hArg, Utils.OBJECTS_NONE);
                }

            case "registerTimeout":
                {
                long cDelayMillis = ((JavaLong) hArg).getValue();
                if (frame.f_context == hService.m_context)
                    {
                    frame.f_fiber.m_ldtTimeout = cDelayMillis <= 0 ? 0 :
                        System.currentTimeMillis() + cDelayMillis;
                    }
                else
                    {
                    hService.m_context.m_cTimeoutMillis = Math.max(0, cDelayMillis);
                    }
                return Op.R_NEXT;
                }

            case "registerAsyncSection":
                if (frame.f_context != hService.m_context)
                    {
                    return frame.raiseException(xException.makeHandle("Call out of context"));
                    }
                return frame.f_fiber.registerAsyncSection(frame, hArg);

            case "registerUnhandledExceptionNotification":
                hService.m_context.m_hExceptionHandler = (FunctionHandle) hArg;
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
            case "yield":
                return frame.f_context == hService.m_context ? Op.R_YIELD : Op.R_NEXT;
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "serviceName":
                return frame.assignValue(iReturn,
                    xString.makeHandle(hTarget.getComposition().getTemplate().f_sName));

            case "asyncSection":
                return frame.assignValue(iReturn, frame.f_fiber.getAsyncSection());
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePreInc(frame, hTarget, idProp, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, idProp, this::invokePreInc);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePostInc(frame, hTarget, idProp, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, idProp, this::invokePostInc);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePreDec(frame, hTarget, idProp, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, idProp, this::invokePreDec);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePostDec(frame, hTarget, idProp, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, idProp, this::invokePostDec);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
        }

    @Override
    public int invokePropertyAdd(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePropertyAdd(frame, hTarget, idProp, hArg);
            }

        hService.m_context.sendProperty10Request(frame, idProp, hArg, this::invokePropertyAdd);
        return Op.R_NEXT;
        }

    @Override
    public int invokePropertySub(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.invokePropertySub(frame, hTarget, idProp, hArg);
            }

        hService.m_context.sendProperty10Request(frame, idProp, hArg, this::invokePropertySub);
        return Op.R_NEXT;
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.getPropertyValue(frame, hTarget, idProp, iReturn);
            }

        CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendProperty01Request(
                frame, idProp, this::getPropertyValue);

        return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
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

        if (frame.f_context == hService.m_context || hService.isAtomic(idProp))
            {
            return super.setPropertyValue(frame, hTarget, idProp, hValue);
            }

        hService.m_context.sendProperty10Request(frame, idProp, hValue, this::setPropertyValue);

        return Op.R_NEXT;
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        ServiceContext context = hService.m_context;
        ServiceContext contextCurrent = ServiceContext.getCurrentContext();

        if (context == null || context == contextCurrent || hService.isAtomic(idProp))
            {
            return super.setFieldValue(frame, hTarget, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;
        return frame.assignValue(iReturn, xString.makeHandle(hService.m_context.toString()));
        }


    // ----- ObjectHandle -----

    public static ServiceHandle makeHandle(ServiceContext context,
                                           TypeComposition clz, TypeConstant type)
        {
        ServiceHandle hService = new ServiceHandle(clz.maskAs(type), context);
        context.setService(hService);
        return hService;
        }

    public static class ServiceHandle
            extends GenericHandle
        {
        public ServiceContext m_context;

        public ServiceHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz);

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
    }
