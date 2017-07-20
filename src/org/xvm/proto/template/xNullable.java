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
public class xNullable
        extends xEnum
    {
    public xNullable(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, fInstance);
        }

    @Override
    public void initDeclared()
        {
        NULL = new NullHandle(f_clazzCanonical);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;
            if (constClass.getClassConstant().getName().equals("Null"))
                {
                return NULL;
                }
            }
        return null;
        }

    public static NullHandle NULL;

    private static class NullHandle
                extends ObjectHandle
        {
        NullHandle(TypeComposition clz)
            {
            super(clz);
            }

        @Override
        public String toString()
            {
            return "null";
            }
        }
    }
