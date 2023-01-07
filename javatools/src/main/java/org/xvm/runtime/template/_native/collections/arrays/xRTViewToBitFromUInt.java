package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;

import org.xvm.runtime.template._native.collections.arrays.IntBasedDelegate.IntArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;


/**
 * The native RTViewToBit<UInt> implementation.
 */
public class xRTViewToBitFromUInt
        extends LongBasedBitView
    {
    public static xRTViewToBitFromUInt INSTANCE;

    public xRTViewToBitFromUInt(Container container, ClassStructure structure, boolean fInstance)
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
                getInceptionClassConstant().getType(), pool.typeUInt());
        }

    @Override
    protected int getBitsPerValue(LongArrayHandle hArray)
        {
        return 64 * ((IntArrayHandle) hArray).getValueSize();
        }
    }