package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xInt8;


/**
 * Native RTDelegate<Int8> implementation.
 */
public class xRTInt8Delegate
        extends ByteBasedDelegate
        implements ByteView
    {
    public static xRTInt8Delegate INSTANCE;

    public xRTInt8Delegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

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
                pool.typeInt8());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xInt8.INSTANCE.makeJavaLong(lValue);
        }
    }