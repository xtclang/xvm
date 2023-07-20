package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt16;


/**
 * Native RTDelegate<Int16> implementation.
 */
public class xRTInt16Delegate
        extends LongBasedDelegate
        implements ByteView
    {
    public static xRTInt16Delegate INSTANCE;

    public xRTInt16Delegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 16, true);

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
                pool.typeInt16());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xInt16.INSTANCE.makeJavaLong(lValue);
        }

    /**
     * Pack an array of short values into a long array and create a DelegateHandle.
     */
    public DelegateHandle packHandle(short[] anValue, Mutability mutability)
        {
        int    cValues  = anValue.length;
        long[] alPacked = new long[storage(cValues)];

        for (int i = 0; i < cValues; i++)
            {
            setValue(alPacked, i, anValue[i]);
            }
        return makeHandle(alPacked, cValues, mutability);
        }
    }