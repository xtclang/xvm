package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

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
    public ObjectHandle createConstHandle(Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            ClassConstant constClass = (ClassConstant) constant;
            if (constClass.getName().equals("Null"))
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
