package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Object functionality implementation.
 */
public class xObject
        extends ClassTemplate
    {
    public static xObject INSTANCE;
    public static TypeComposition CLASS;

    public xObject(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            CLASS = getCanonicalClass();
            }
        }

    @Override
    public void initDeclared()
        {
        if (this == INSTANCE)
            {
            markNativeMethod("toString", VOID, STRING);
            markNativeMethod("equals", null, null);
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "meta":
                return frame.assignValue(iReturn, hTarget);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }
    }
