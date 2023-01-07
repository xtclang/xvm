package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;

import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.IntBasedDelegate.IntArrayHandle;


/**
 * The native RTViewToBit<Int> implementation.
 */
public class xRTViewToBitFromInt
        extends LongBasedBitView
    {
    public static xRTViewToBitFromInt INSTANCE;

    public xRTViewToBitFromInt(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, -1);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), pool.typeInt());
        }

    @Override
    protected int getBitsPerValue(LongArrayHandle hArray)
        {
        return 64 * ((IntArrayHandle) hArray).getValueSize();
        }
    }