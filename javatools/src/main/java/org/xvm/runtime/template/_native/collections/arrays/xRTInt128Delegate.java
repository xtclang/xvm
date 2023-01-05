package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt128;


/**
 * Native RTDelegate<Int128> implementation.
 */
public class xRTInt128Delegate
        extends LongLongDelegate
    {
    public static xRTInt128Delegate INSTANCE;

    public xRTInt128Delegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, true);

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
                getInceptionClassConstant().getType(),
                pool.typeCInt128());
        }

    @Override
    protected ObjectHandle makeElementHandle(LongLong ll)
        {
        return xInt128.INSTANCE.makeHandle(ll);
        }
   }