package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate;
import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;


/**
 * Native ClassTemplate implementation.
 */
public class xClassTemplate
        extends ClassTemplate
    {
    public static xClassTemplate INSTANCE;

    public xClassTemplate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public int callEqualsImpl(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return hValue1 instanceof ComponentTemplateHandle &&
                hValue2 instanceof ComponentTemplateHandle
            ? xRTComponentTemplate.INSTANCE.callEquals(frame, clazz, hValue1, hValue2, iReturn)
            : frame.assignValue(iReturn, xBoolean.FALSE);
        }
    }