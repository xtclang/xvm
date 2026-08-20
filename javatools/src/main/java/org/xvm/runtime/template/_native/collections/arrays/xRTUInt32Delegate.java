package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xUInt32;


/**
 * Native RTDelegate<UInt32> implementation.
 */
public class xRTUInt32Delegate
        extends LongBasedDelegate
        implements ByteView {
    public xRTUInt32Delegate(Container container, ClassStructure structure) {
        super(container, structure, 32, false);
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeUInt32());
    }

    @Override
    protected ObjectHandle makeElementHandle(long lValue) {
        return f_container.nativeTemplates().uint32().makeJavaLong(lValue);
    }
}
