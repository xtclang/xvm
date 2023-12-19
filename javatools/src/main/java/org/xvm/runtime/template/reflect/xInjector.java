package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Injector implementation.
 */
public class xInjector
        extends xService
    {
    public static xInjector INSTANCE;

    public xInjector(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("inject", null, null);
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureInjector(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hInjector = m_hInjector;
        if (hInjector == null)
            {
            m_hInjector = hInjector = createServiceHandle(
                    f_container.createServiceContext("Injector"),
                        getCanonicalClass(), getCanonicalType());
            }

        return hInjector;
        }


    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "inject":
                {
                TypeHandle   hType = (TypeHandle)   ahArg[1];
                StringHandle hName = (StringHandle) ahArg[2];
                ObjectHandle hOpts = ahArg[3];

                if (hOpts == ObjectHandle.DEFAULT)
                    {
                    hOpts = xNullable.NULL;
                    }
                ObjectHandle hValue = frame.getInjected(hName.getStringValue(), hType.getDataType(), hOpts);
                if (hValue == null)
                    {
                    return frame.raiseException("Unknown injectable resource \"" +
                            hType.getDataType().getValueString() + ' ' + hName.getStringValue() + '"');
                    }

                return Op.isDeferred(hValue)
                        ? hValue.proceed(frame, frameCaller ->
                            frameCaller.assignValue(iReturn, frameCaller.popStack()))
                        : frame.assignValue(iReturn, hValue);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Cached Injector handle.
     */
    private ObjectHandle m_hInjector;
    }