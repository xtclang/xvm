package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.ParameterizedTypeConstant;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xOrdered
        extends xEnum
    {
    public static EnumHandle LESSER;
    public static EnumHandle EQUAL;
    public static EnumHandle GREATER;

    public static ParameterizedTypeConstant[] TYPES;

    public xOrdered(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            TYPES = new ParameterizedTypeConstant[] {getTypeConstant()};
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
