package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Nullable.
 */
public class xNullable
        extends xEnum
    {
    public static EnumHandle NULL;

    public xNullable(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        if (getStructure().getFormat() == Format.ENUM)
            {
            super.initNative();

            NULL = getEnumByOrdinal(0);
            }
        }

    @Override
    protected EnumHandle makeEnumHandle(ClassComposition clz, int iOrdinal)
        {
        return new EnumHandle(clz, 0);
        }
    }
