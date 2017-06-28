package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNullable
        extends ClassTemplate
    {
    public xNullable(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        // in-place declaration for True and False
        // in-place generation of Hashable
        ClassTemplate template = f_types.getTemplate("x:Nullable$Null");

        NULL = new NullHandle(template.f_clazzCanonical);
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
