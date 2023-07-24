package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int64 support.
 */
public class xCheckedInt64
        extends xCheckedConstrainedInt
    {
    public static xCheckedInt64 INSTANCE;

    public xCheckedInt64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedUInt64.INSTANCE;
        }
    }