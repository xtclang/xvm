package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native unchecked Int32 support.
 */
public class xUncheckedInt32
        extends xUncheckedSignedInt
    {
    public static xUncheckedInt32 INSTANCE;

    public xUncheckedInt32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt32.INSTANCE;
        }
    }