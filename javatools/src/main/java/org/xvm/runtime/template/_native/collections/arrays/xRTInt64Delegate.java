package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native RTDelegate<Int> implementation.
 */
public class xRTInt64Delegate
        extends LongDelegate
    {
    public static xRTInt64Delegate INSTANCE;

    public xRTInt64Delegate(Container container, ClassStructure structure, boolean fInstance)
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
                pool.typeInt64());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xInt64.INSTANCE.makeJavaLong(lValue);
        }
   }