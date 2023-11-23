package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native Number support.
 */
public abstract class xNumber
        extends xConst
    {
    public xNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        String[] NAMES =
                {
                "Int8",  "Int16",  "Int32",  "Int64",  "Int128",  "IntN",
                "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",

                "Float16", "Float32", "Float64", "FloatN",

                "Dec32", "Dec64", "Dec128", "DecN"
                };

        for (String sName : NAMES)
            {
            String sNameQ = "numbers." + sName;
            if (!sNameQ.equals(f_sName))
                {
                markNativeMethod("to" + sName, null, new String[]{sNameQ});
                }
            }

        markNativeProperty("bits");

        ClassStructure structure = getStructure();
        structure.findMethodDeep("equals",   m -> m.getParamCount() == 3).markNative();
        structure.findMethodDeep("compare",  m -> m.getParamCount() == 3).markNative();
        structure.findMethodDeep("hashCode", m -> m.getParamCount() == 2).markNative();
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        SignatureConstant sig = constructor.getIdentityConstant().getSignature();
        if (sig.getParamCount() == 1)
            {
            TypeConstant typeParam = sig.getRawParams()[0];
            if (typeParam.equals(pool().typeString()))
                {
                StringHandle hText = (StringHandle) ahVar[0];
                return constructFromString(frame, hText.getStringValue(), iReturn);
                }

            TypeConstant typeElement = typeParam.getParamType(0);
            if (typeElement.equals(pool().typeByte()))
                {
                // construct(Byte[] bytes)
                ArrayHandle hArray = (ArrayHandle) ahVar[0];
                byte[]      abVal  = xByteArray.getBytes(hArray);
                int         cBytes  = (int) hArray.m_hDelegate.m_cSize;

                return constructFromBytes(frame, abVal, cBytes, iReturn);
                }

            if (typeElement.equals(pool().typeBit()))
                {
                // construct(Bit[] bits)
                ArrayHandle hArray = (ArrayHandle) ahVar[0];
                byte[]      abBits = xBitArray.getBits(hArray);
                int         cBits  = (int) hArray.m_hDelegate.m_cSize;

                return constructFromBits(frame, abBits, cBits, iReturn);
                }
            }
        return frame.raiseException(xException.unsupportedOperation(frame));
        }

    /**
     * Construct a number from the specified string and place it into the specified register.
     */
    protected abstract int constructFromString(Frame frame, String sText, int iReturn);

    /**
     * Construct a number from the specified byte array and place it into the specified register.
     */
    protected abstract int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn);

    /**
     * Construct a number from the specified bit array and place it into the specified register.
     */
    protected abstract int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn);

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // all numbers are consts with identity being the value
        return frame.assignValue(iReturn,
                xBoolean.makeHandle(compareIdentity(hValue1, hValue2)));
        }

    @Override
    public abstract boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2);
    }