package org.xvm.runtime.template._native.mgmt;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.xRTServiceControl;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native implementation of _native.mgmt.ContainerControl class.
 */
public class xContainerControl
        extends xRTServiceControl
    {
    public static xContainerControl INSTANCE;

    public xContainerControl(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public void initNative()
        {
        ConstantPool pool     = pool();
        TypeConstant typeMask = pool.ensureEcstasyTypeConstant("mgmt.Container.Control");

        m_clzControl = ensureClass(f_container, getCanonicalType(), typeMask);

        markNativeProperty("mainService");
        markNativeProperty("innerTypeSystem");

        markNativeMethod("invoke", null, null);
        markNativeMethod("kill",   VOID, VOID);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        Container container = ((ControlHandle) hTarget).f_container;
        switch (sPropName)
            {
            case "mainService":
                {
                ServiceContext ctx = container.getServiceContext();
                return frame.assignValue(iReturn, ctx == null ? xNullable.NULL : ctx.getService());
                }

            case "innerTypeSystem":
                return getPropertyTypeSystem(frame, container, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "invoke":
                return invokeInvoke(frame, (ControlHandle) hTarget,
                        (StringHandle) ahArg[0], (TupleHandle) ahArg[1], ahArg[2], iReturn);

            case "kill":
                return invokeKill(frame, (ControlHandle) hTarget, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation: "Tuple invoke(String methodName, Tuple args = Tuple:(),
     *                         Service? runWithin = Null)".
     */
    protected int invokeInvoke(Frame frame, ControlHandle hCtrl, StringHandle hName,
                               TupleHandle hTupleArg, ObjectHandle hRunWithin, int iReturn)
        {
        Container container = hCtrl.f_container;

        try (var ignore = ConstantPool.withPool(container.getConstantPool()))
            {
            ServiceContext ctxContainer = container.ensureServiceContext();

            ObjectHandle[] ahArg    = hTupleArg.m_ahValue;
            String         sMethod  = hName.getStringValue();
            ModuleConstant idModule = container.getModule();
            MethodConstant idMethod = container.findModuleMethod(sMethod, ahArg);

            if (idMethod == null)
                {
                return frame.raiseException("Missing " + sMethod +
                    " method for " + idModule.getValueString());
                }

            ServiceHandle hService = hRunWithin == ObjectHandle.DEFAULT ||
                                     hRunWithin == xNullable.NULL
                    ? ctxContainer.getService()
                    : (ServiceHandle) hRunWithin;

            if (hService.f_context.f_container != container)
                {
                return frame.raiseException("Out of context \"runWithin\" service");
                }

            TypeComposition clzModule = container.resolveClass(idModule.getType());
            CallChain       chain     = clzModule.getMethodCallChain(idMethod.getSignature());
            FunctionHandle  hFunction = new xRTFunction.AsyncHandle(container, chain)
                {
                @Override
                protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                    {
                    return frame.getConstHandle(idModule);
                    }
                };
            return hFunction.callT(frame, hService, ahArg, iReturn);
            }
        }

    /**
     * Native implementation: "void kill()".
     */
    protected int invokeKill(Frame frame, ControlHandle hCtrl, int iReturn)
        {
        Container container = hCtrl.f_container;
        assert container instanceof NestedContainer;

        // TODO GG: clean up

        // Note: the caller is async; we must return the Tuple()
        return frame.assignValue(iReturn, xTuple.H_VOID);
        }

    /**
     * Native implementation of "innerTypeSystem.get()"
     */
    protected int getPropertyTypeSystem(Frame frame, Container container, int iReturn)
        {
        // the "ensureTypeSystemHandle" call should be made on the new container's context
        Op opCall = new Op()
            {
            public int process(Frame frame, int iPC)
                {
                return container.ensureTypeSystemHandle(frame, 0);
                }

            public String toString()
                {
                return "CreateTypeSystem";
                }
            };

        return container.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public ObjectHandle makeHandle(Container container)
        {
        return new ControlHandle(m_clzControl, container);
        }

    protected static class ControlHandle
            extends xRTServiceControl.ControlHandle
        {
        protected ControlHandle(TypeComposition clazz, Container container)
            {
            super(clazz, container.getServiceContext());

            f_container = container;
            }

        @Override
        public ServiceContext getContext()
            {
            return f_container.getServiceContext();
            }

        /**
         * The container this ControlHandle instance is responsible for managing.
         */
        protected final Container f_container;
        }

    private TypeComposition m_clzControl;
    }