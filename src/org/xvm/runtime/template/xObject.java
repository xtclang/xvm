package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeSet;


/**
 * TODO:
 */
public class xObject
        extends ClassTemplate
    {
    public static xObject INSTANCE;
    public static TypeComposition CLASS;
    public static Type TYPE;

    public xObject(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            CLASS = f_clazzCanonical;
            TYPE = CLASS.ensurePublicType();
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, STRING);
        markCalculated("meta");
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        switch (property.getName())
            {
            case "meta":
                return frame.assignValue(iReturn, hTarget);
            }

        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }
    }
