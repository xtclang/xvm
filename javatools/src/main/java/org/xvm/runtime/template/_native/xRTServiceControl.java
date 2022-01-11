package org.xvm.runtime.template._native;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;

import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;


/**
 * Native implementation of _native.RTServiceControl class.
 */
public class xRTServiceControl
        extends ClassTemplate
    {
    public static xRTServiceControl INSTANCE;

    public xRTServiceControl(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
        TypeConstant typeControl = pool().ensureEcstasyTypeConstant("Service.ServiceControl");

        m_clzControl = ensureClass(getCanonicalType(), typeControl);

        SERVICE_STATUS = (xEnum) f_templates.getTemplate("Service.ServiceStatus");

        markNativeProperty("statusIndicator");
        markNativeProperty("upTime");
        markNativeProperty("cpuTime");
        markNativeProperty("contended");

        markNativeMethod("gc", VOID, VOID);
        markNativeMethod("shutdown", VOID, VOID);
        markNativeMethod("kill", VOID, VOID);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ControlHandle hControl = (ControlHandle) hTarget;

        switch (method.getName())
            {
            case "shutdown":
                {
                ServiceContext context  = hControl.getContext();
                ServiceHandle  hService = context.getService();
                if (hService == null)
                    {
                    // already shut down
                    return Op.R_NEXT;
                    }
                return frame.f_context == context
                    ? context.shutdown(frame)
                    : xRTFunction.makeAsyncNativeHandle(method).call1(frame, hService, ahArg, iReturn);
                }

            case "kill":
                // TODO GG
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ControlHandle hControl = (ControlHandle) hTarget;

        switch (sPropName)
            {
            case "contended":
                return frame.assignValue(iReturn,
                        xBoolean.makeHandle(hControl.getContext().isContended()));

            case "statusIndicator":
                {
                xEnum.EnumHandle hStatus = SERVICE_STATUS.getEnumByName(
                        hControl.getContext().getStatus().name());
                return Utils.assignInitializedEnum(frame, hStatus, iReturn);
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static ObjectHandle makeHandle(ServiceContext context)
        {
        return new ControlHandle(INSTANCE.m_clzControl, context);
        }

    protected static class ControlHandle
            extends ObjectHandle
        {
        protected ControlHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz);

            f_context = context;
            }

        /**
         * @return  the ServiceContext this ControlHandle instance is responsible for managing
         */
        public ServiceContext getContext()
            {
            return f_context;
            }

        /**
         * The ServiceContext this control is managing.
         */
        protected final ServiceContext f_context;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Enum used by the native properties.
     */
    protected static xEnum SERVICE_STATUS;

    private TypeComposition m_clzControl;
    }
