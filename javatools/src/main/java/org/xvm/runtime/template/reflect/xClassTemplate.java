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
        extends ClassTemplate {
    public xClassTemplate(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure);
    }

    @Override
    public int callEqualsImpl(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        return hValue1 instanceof ComponentTemplateHandle &&
                hValue2 instanceof ComponentTemplateHandle
            ? f_container.nativeTemplate(xRTComponentTemplate.class).callEquals(frame, clazz, hValue1, hValue2, iReturn)
            : frame.assignValue(iReturn, xBoolean.FALSE);
    }
}
