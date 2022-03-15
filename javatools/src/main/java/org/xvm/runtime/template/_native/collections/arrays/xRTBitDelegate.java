package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xBit;


/**
 * Native RTDelegate<Bit> implementation.
 */
public class xRTBitDelegate
        extends BitBasedDelegate
    {
    public static xRTBitDelegate INSTANCE;

    public xRTBitDelegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
                pool.typeBit());
        }

    @Override
    protected boolean isSet(ObjectHandle hValue)
        {
        return ((JavaLong) hValue).getValue() != 0;
        }

    @Override
    protected ObjectHandle makeBitHandle(boolean f)
        {
        return xBit.makeHandle(f);
        }

    @Override
    public BitArrayHandle makeHandle(byte[] abValue, long cBits, Mutability mutability)
        {
        return new BitArrayHandle(getCanonicalClass(), abValue, cBits, mutability);
        }
    }
