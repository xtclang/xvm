package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xUInt128;


/**
 * Native RTDelegate<UInt128> implementation.
 */
public class xRTUInt128Delegate
        extends LongLongDelegate {
    public xRTUInt128Delegate(Container container, ClassStructure structure) {
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
                pool.typeUInt128());
    }

    @Override
    protected ObjectHandle makeElementHandle(LongLong ll) {
        return f_container.nativeTemplates().uint128().makeHandle(ll);
    }
   }
