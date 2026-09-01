package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.numbers.xNibble;


/**
 * Native RTDelegate<Nibble> implementation.
 */
public class xRTNibbleDelegate
        extends LongBasedDelegate
        implements ByteView {
    public xRTNibbleDelegate(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 4, false);
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

    @Override
    protected ObjectHandle makeElementHandle(long lValue) {
        return f_container.nativeTemplate(xNibble.class).makeJavaLong(lValue);
    }

    /**
     * Pack an array of byte values into a long array and create a DelegateHandle.
     */
    public DelegateHandle packHandle(byte[] anValue, xArray.Mutability mutability) {
        int    cValues  = anValue.length;
        long[] alPacked = new long[storage(cValues)];

        for (int i = 0; i < cValues; i++) {
            setValue(alPacked, i, anValue[i]);
        }
        return makeHandle(alPacked, cValues, mutability);
    }
}
