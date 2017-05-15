package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNullable
        extends TypeCompositionTemplate
    {
    public xNullable(TypeSet types)
        {
        super(types, "x:Nullable", "x:Object", Shape.Enum);
        }

    // subclassing
    protected xNullable(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        if (!f_sName.equals("x:Nullable")) return; // avoid recursion

        // in-place declaration for True and False
        // in-place generation of Hashable
        f_types.addTemplate(new xNullable(f_types, "x:Nullable$Null", "x:Nullable", Shape.Enum));

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
