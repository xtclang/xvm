package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;


/**
 * Native Nullable.
 */
public class xNullable
        extends xEnum
    {
    public static EnumHandle NULL;

    public xNullable(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
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
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal)
        {
        return new EnumHandle(clz, 0);
        }
    }