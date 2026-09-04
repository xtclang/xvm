package org.xvm.runtime.template._native.collections.arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xUInt64;

/**
 * Native RTDelegate<UInt64> implementation.
 */
public class xRTUInt64Delegate
        extends LongDelegate {
    public xRTUInt64Delegate(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, false);
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

    @Override
    protected ObjectHandle makeElementHandle(long lValue) {
        return f_container.nativeTemplate(xUInt64.class).makeJavaLong(lValue);
    }
   }
