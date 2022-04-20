package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native unchecked Int8 support.
 */
public class xUncheckedInt8
        extends xUncheckedSignedInt
    {
    public static xUncheckedInt8 INSTANCE;

    public xUncheckedInt8(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt8.INSTANCE;
        }
    }