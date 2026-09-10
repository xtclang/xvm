package org.xvm.runtime.template._native.collections.arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;

/**
 * The native RTViewToBit<Byte> implementation.
 */
public class xRTViewToBitFromUInt8
        extends ByteBasedBitView {
    public xRTViewToBitFromUInt8(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure);
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), pool.typeByte());
    }
}
