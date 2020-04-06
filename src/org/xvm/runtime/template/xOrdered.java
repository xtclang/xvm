package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Ordered.
 */
public class xOrdered
        extends xEnum
    {
    public static EnumHandle LESSER;
    public static EnumHandle EQUAL;
    public static EnumHandle GREATER;

    public xOrdered(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        if (getStructure().getFormat() == Format.ENUM)
            {
            super.initNative();

            LESSER  = getEnumByOrdinal(0);
            EQUAL   = getEnumByOrdinal(1);
            GREATER = getEnumByOrdinal(2);
            }
        }

    @Override
    protected EnumHandle makeEnumHandle(ClassComposition clz, int iOrdinal)
        {
        return new EnumHandle(clz, iOrdinal);
        }

    /**
     * Trivial helper.
     */
    public static EnumHandle makeHandle(long i)
        {
        return i < 0 ? LESSER :
               i > 0 ? GREATER :
                       EQUAL;
        }
    }
