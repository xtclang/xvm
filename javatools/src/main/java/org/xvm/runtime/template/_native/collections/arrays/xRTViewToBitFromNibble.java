package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;


/**
 * The native RTViewToBit<Nibble> implementation.
 */
public class xRTViewToBitFromNibble
        extends LongBasedBitView {
    public xRTViewToBitFromNibble(Container container, ClassStructure structure) {
        super(container, structure, 4);
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeNibble());
    }
}
