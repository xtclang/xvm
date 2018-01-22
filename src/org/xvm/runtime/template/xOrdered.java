package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

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

    public static TypeConstant[] TYPES;

    public xOrdered(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            TYPES = new TypeConstant[] {getTypeConstant()};
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        LESSER = m_listHandles.get(0);
        EQUAL = m_listHandles.get(1);
        GREATER = m_listHandles.get(2);
        }

    public static EnumHandle makeHandle(long i)
        {
        return i < 0 ? LESSER :
               i > 0 ? GREATER :
                       EQUAL;
        }
    }
