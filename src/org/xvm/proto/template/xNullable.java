package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Component;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.Collections;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNullable
        extends xEnum
    {
    public static NullHandle NULL;

    public xNullable(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);
        }

    @Override
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            NULL = new NullHandle(f_clazzCanonical);

            m_listNames = Collections.singletonList("Null");
            m_listHandles = Collections.singletonList(NULL);
            }
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
