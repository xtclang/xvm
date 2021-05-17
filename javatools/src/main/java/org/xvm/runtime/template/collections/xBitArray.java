package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xBit;
import org.xvm.runtime.template.numbers.xUInt8;


/**
 * Native BitArray<Bit> implementation.
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
    public void initNative()
        {
        ClassTemplate mixin = f_templates.getTemplate("collections.arrays.BitArray");

        mixin.markNativeMethod("toUInt8", VOID, null);
        mixin.markNativeMethod("toByteArray", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureArrayType(pool.typeBit());
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toByteArray":
                {
                BitArrayHandle hBits = (BitArrayHandle) hTarget;
                int cBits = hBits.m_cSize;
                if (cBits % 8 != 0)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + cBits));
                    }

                int    cBytes = cBits >>> 3;
                byte[] aBytes = hBits.m_abValue;

                Mutability mutability = hArg == ObjectHandle.DEFAULT
                    ? Mutability.Constant
                    : xArray.Mutability.values()[((xEnum.EnumHandle) hArg).getOrdinal()];

                // TODO: do we always need a copy?
                aBytes = Arrays.copyOfRange(aBytes, 0, cBytes);

                return frame.assignValue(iReturn,
                        xByteArray.makeHandle(aBytes, mutability));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toUInt8":
                {
                BitArrayHandle hBits = (BitArrayHandle) hTarget;
                int            cBits = hBits.m_cSize;
                switch (cBits)
                    {
                    case 0:
                        return frame.assignValue(iReturn, xUInt8.INSTANCE.makeJavaLong(0));

                    case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                        return frame.assignValue(iReturn, xUInt8.INSTANCE.makeJavaLong(
                            (hBits.m_abValue[0] & 0xFF) >>> (8 - cBits)));

                    case 8:
                        return frame.assignValue(iReturn, xUInt8.INSTANCE.makeJavaLong(
                                hBits.m_abValue[0]));

                    default:
                        return frame.raiseException(xException.outOfBounds(
                                frame, "Array is too big: " + cBits));
                    }
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
