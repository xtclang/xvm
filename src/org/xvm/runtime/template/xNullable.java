package org.xvm.runtime.template;


import java.util.Collections;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xNullable
        extends xEnum
    {
    public static NullHandle NULL;

    public xNullable(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            ConstantPool pool = f_struct.getConstantPool();

            f_templates.registerNativeTemplate(pool.typeNull(), this);

            super.initDeclared();

            NULL = (NullHandle) m_listHandles.get(0);
            }
        else
            {
            getSuper(); // this will initialize all the handles
            }
        }

    @Override
    protected EnumHandle makeEnumHandle(int iOrdinal)
        {
        return new NullHandle(getCanonicalClass());
        }

    private static class NullHandle
                extends EnumHandle
        {
        NullHandle(TypeComposition clz)
            {
            super(clz, 0);
            }
        }
    }
