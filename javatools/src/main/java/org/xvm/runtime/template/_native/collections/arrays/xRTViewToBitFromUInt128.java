package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;


/**
 * The native RTViewToBit<UInt128> implementation.
 */
public class xRTViewToBitFromUInt128
        extends LongBasedBitView
    {
    public static xRTViewToBitFromUInt128 INSTANCE;

    public xRTViewToBitFromUInt128(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 128);

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
                getInceptionClassConstant().getType(), pool.typeUInt128());
        }
    }