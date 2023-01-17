package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * Native RTDelegate<Boolean> implementation.
 */
public class xRTBooleanDelegate
        extends BitBasedDelegate
    {
    public static xRTBooleanDelegate INSTANCE;

    public xRTBooleanDelegate(Container container, ClassStructure structure, boolean fInstance)
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
                pool.typeBoolean());
        }

    @Override
    protected boolean isSet(ObjectHandle hValue)
        {
        return ((BooleanHandle) hValue).get();
        }

    @Override
    protected ObjectHandle makeBitHandle(boolean f)
        {
        return xBoolean.makeHandle(f);
        }

    @Override
    public BitArrayHandle makeHandle(byte[] ab, long cSize, Mutability mutability)
        {
        return new BitArrayHandle(getCanonicalClass(), ab, cSize, mutability);
        }

    /**
     * Create a byte array representing a native boolean array.
     */
    public static byte[] toBytes(boolean[] af)
        {
        int    cBits = af.length;
        byte[] ab    = new byte[(cBits + 7) >>> 3];
        for (int i = 0; i < cBits; i++)
            {
            setBit(ab, i, af[i]);
            }
        return ab;
        }

    }