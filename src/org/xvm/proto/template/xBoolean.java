package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBoolean
        extends ClassTemplate
    {
    public xBoolean(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        TypeComposition clzTrue = f_types.getTemplate("x:Boolean$True").f_clazzCanonical;
        TypeComposition clzFalse = f_types.getTemplate("x:Boolean$True").f_clazzCanonical;

        TRUE = new BooleanHandle(clzTrue, true);
        FALSE = new BooleanHandle(clzFalse, false);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;
            String sName = constClass.getClassConstant().getName();
            if (sName.equals("True"))
                {
                return TRUE;
                }
            if (sName.equals("False"))
                {
                return FALSE;
                }
            }
        return null;
        }

    public static BooleanHandle TRUE;
    public static BooleanHandle FALSE;

    public static class BooleanHandle
                extends JavaLong
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
            return m_lValue > 0 ? "true" : "false";
            }
        }
    }
