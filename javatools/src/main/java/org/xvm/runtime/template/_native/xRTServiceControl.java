package org.xvm.runtime.template._native;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;

import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.util.Lazy;


/**
 * Native implementation of _native.RTServiceControl class.
 */
public class xRTServiceControl
        extends ClassTemplate {

    public static xRTServiceControl getInstance(Frame frame) {
        return NativeTemplates.get(frame).serviceControl();
    }

    public static xRTServiceControl getInstance(Container container) {
        return NativeTemplates.get(container).serviceControl();
    }

    public xRTServiceControl(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public void initNative() {
        f_clzControl.get(this);
        f_templateServiceStatus.get(this);

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
                             ObjectHandle[] ahArg, int iReturn) {
        ControlHandle hControl = (ControlHandle) hTarget;

        switch (method.getName()) {
        case "shutdown": {
            ServiceContext context  = hControl.getContext();
            ServiceHandle  hService = context.getService();
            if (hService == null) {
                // already shut down
                return Op.R_NEXT;
            }
            return frame.f_context == context
                ? context.shutdown(frame)
                : xRTFunction.makeAsyncNativeHandle(frame, method).call1(frame, hService, ahArg, iReturn);
        }

        case "kill":
            // TODO GG
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ControlHandle hControl = (ControlHandle) hTarget;

        switch (sPropName) {
        case "contended":
            return frame.assignValue(iReturn,
                    xBoolean.makeHandle(frame, hControl.getContext().isContended()));

        case "statusIndicator": {
            return frame.assignDeferredValue(iReturn,
                    f_templateServiceStatus.get(this).ensureEnumByName(
                            frame, hControl.getContext().getStatus().name()));
        }
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static ObjectHandle makeHandle(ServiceContext context) {
        xRTServiceControl template = NativeTemplates.get(context.f_container).serviceControl();
        return new ControlHandle(template.f_clzControl.get(template), context);
    }

    protected static class ControlHandle
            extends ObjectHandle {
        protected ControlHandle(TypeComposition clazz, ServiceContext context) {
            super(clazz);

            f_context = context;
        }

        /**
         * @return  the ServiceContext this ControlHandle instance is responsible for managing
         */
        public ServiceContext getContext() {
            return f_context;
        }

        /**
         * The ServiceContext this control is managing.
         */
        protected final ServiceContext f_context;
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Lazily resolved ServiceControl composition owned by this template's container.
     */
    private final Lazy.Owner<xRTServiceControl, TypeComposition> f_clzControl = Lazy.ofOwner(owner -> {
        TypeConstant typeMask = owner.pool().ensureEcstasyTypeConstant("Service.ServiceControl");
        return owner.ensureClass(owner.container(), owner.getCanonicalType(), typeMask);
    });

    /**
     * Lazily resolved Service.ServiceStatus enum template owned by this template's container.
     */
    private final Lazy.Owner<xRTServiceControl, xEnum> f_templateServiceStatus =
            Lazy.ofOwner(owner -> owner.container().getEnumTemplate("Service.ServiceStatus"));
}
