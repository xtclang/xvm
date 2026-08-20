package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt32;


/**
 * Native RTDelegate<Int32> implementation.
 */
public class xRTInt32Delegate
        extends LongBasedDelegate
        implements ByteView {
    public xRTInt32Delegate(Container container, ClassStructure structure) {
        super(container, structure, 32, true);
    }

    @Override
    public void initNative() {
    }

    @Override
    public TypeConstant getCanonicalType() {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeInt32());
    }

    @Override
    protected ObjectHandle makeElementHandle(long lValue) {
        return f_container.nativeTemplates().int32().makeJavaLong(lValue);
    }

    /**
     * Pack an array of short values into a long array and create a DelegateHandle.
     */
    public DelegateHandle packHandle(int[] anValue, Mutability mutability) {
        int    cValues  = anValue.length;
        long[] alPacked = new long[storage(cValues)];

        for (int i = 0; i < cValues; i++) {
            setValue(alPacked, i, anValue[i]);
        }
        return makeHandle(alPacked, cValues, mutability);
    }
}
