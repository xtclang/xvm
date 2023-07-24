package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int16 support.
 */
public class xCheckedInt16
        extends xCheckedConstrainedInt
    {
    public static xCheckedInt16 INSTANCE;

    public xCheckedInt16(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedUInt16.INSTANCE;
        }
    }