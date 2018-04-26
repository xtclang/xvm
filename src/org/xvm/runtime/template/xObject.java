package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
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
            markNativeMethod("to", VOID, STRING);
            getCanonicalType().ensureTypeInfo().findEqualsFunction().setNative(true);
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return true;
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
