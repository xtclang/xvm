package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.ClassTypeConstant;

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

    public xBoolean(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, fInstance);
        }

    @Override
    public void initDeclared()
        {
        FALSE = new BooleanHandle(f_clazzCanonical, false);
        TRUE = new BooleanHandle(f_clazzCanonical, true);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;
            String sName = constClass.getClassConstant().getName();
            if (sName.equals("False"))
                {
                return FALSE;
                }
            if (sName.equals("True"))
                {
                return TRUE;
                }
            }
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
