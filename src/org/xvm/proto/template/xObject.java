package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xObject
        extends ClassTemplate
    {
    public static xObject INSTANCE;
    public static TypeComposition CLASS;
    public static Type TYPE;
    public static MethodConstant TO_STRING; // TODO: should be MethodIdConst

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
        TO_STRING = f_types.f_adapter.getMethod(this, "to", VOID, STRING).getIdentityConstant();
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
