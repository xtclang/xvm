package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.TemplateRegistry;
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

            LESSER .setField("symbol", xString.makeHandle("<"));
            EQUAL  .setField("symbol", xString.makeHandle("="));
            GREATER.setField("symbol", xString.makeHandle(">"));

            LESSER .setField("reversed", GREATER);
            EQUAL  .setField("reversed", EQUAL);
            GREATER.setField("reversed", LESSER);
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
