package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBit;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xUInt8;


/**
 * Native Array<Bit> implementation.
 */
public class xBitArray
        extends BitBasedArray
    {
    public static xBitArray INSTANCE;

    public xBitArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        ClassStructure mixin = f_templates.getClassStructure("collections.Array.BitArray");

        mixin.findMethod("toByte", 0).setNative(true);
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.ensureEcstasyTypeConstant("Bit"));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toByte":
                {
                BitArrayHandle hBits = (BitArrayHandle) hTarget;
                return hBits.m_cSize > 8
                    ? frame.raiseException(xException.outOfBounds(frame, "Array is too big: " + hBits.m_cSize))
                    : frame.assignValue(iReturn, xUInt8.INSTANCE.makeJavaLong(hBits.m_abValue[0]));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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

    /**
     * Create a bit array handle.
     *
     * @param abValue     the underlying bytes
     * @param cBits       the array arity
     * @param mutability  the mutability
     *
     * @return the array handle
     */
    public static BitArrayHandle makeHandle(byte[] abValue, int cBits, Mutability mutability)
        {
        return new BitArrayHandle(INSTANCE.getCanonicalClass(), abValue, cBits, mutability);
        }
    }
