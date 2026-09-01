package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;


/**
 * The native RTViewToBit<UInt64> implementation.
 */
public class xRTViewToBitFromUInt64
        extends LongBasedBitView {
    public xRTViewToBitFromUInt64(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, 64);
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeUInt64());
    }
}
