package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
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
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            super.initDeclared();

            LESSER = m_listHandles.get(0);
            EQUAL = m_listHandles.get(1);
            GREATER = m_listHandles.get(2);
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
