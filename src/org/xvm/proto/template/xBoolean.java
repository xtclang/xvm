package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.ClassConstant;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBoolean
        extends xEnum
    {
    public static BooleanHandle TRUE;
    public static BooleanHandle FALSE;
    public static TypeConstant TYPE;
    public static TypeConstant[] TYPES;

    public xBoolean(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            TYPE = ((ClassConstant) f_struct.getIdentityConstant()).asTypeConstant();
            TYPES = new TypeConstant[] {TYPE};
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, STRING);

        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            FALSE = new BooleanHandle(f_clazzCanonical, false);
            TRUE = new BooleanHandle(f_clazzCanonical, true);
            }
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (f_struct.getFormat() == Component.Format.ENUMVALUE)
            {
            xEnum template = (xEnum) getSuper();
            return template.createConstHandle(constant, heap);
            }

        if (constant instanceof ClassConstant)
            {
            ClassConstant constClass = (ClassConstant) constant;
            switch (constClass.getName())
                {
                case "False":
                    return FALSE;

                case "True":
                    return TRUE;
                }
            }
        return null;
        }

    @Override
    public ObjectHandle.ExceptionHandle buildStringValue(ObjectHandle hTarget, StringBuilder sb)
        {
        sb.append(hTarget == FALSE ? "False" : "True");
        return null;
        }

    public static BooleanHandle makeHandle(boolean f)
        {
        return f ? TRUE : FALSE;
        }

    public static class BooleanHandle
                extends EnumHandle
        {
        BooleanHandle(TypeComposition clz, boolean f)
            {
            super(clz, f ? 1 : 0);
            }

        public boolean get()
            {
            return m_lValue != 0;
            }

        @Override
        public String toString()
            {
            return m_lValue == 0 ? "false" : "true";
            }
        }
    }
