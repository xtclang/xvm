package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.text.xString;


/**
 * Native Ordered.
 */
public class xOrdered
        extends xEnum
    {
    public static EnumHandle LESSER;
    public static EnumHandle EQUAL;
    public static EnumHandle GREATER;

    public xOrdered(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
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

            LESSER .setField(null, "symbol", xString.makeHandle("<"));
            EQUAL  .setField(null, "symbol", xString.makeHandle("="));
            GREATER.setField(null, "symbol", xString.makeHandle(">"));

            LESSER .setField(null, "reversed", GREATER);
            EQUAL  .setField(null, "reversed", EQUAL);
            GREATER.setField(null, "reversed", LESSER);
            }
        }

    @Override
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal)
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