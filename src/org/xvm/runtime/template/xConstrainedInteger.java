package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Abstract base class for constrained integers that fit into 64 bits
 * (Int8, UInt16, @Unchecked Int32, ...)
 */
public abstract class xConstrainedInteger
        extends xConst
    {
    protected xConstrainedInteger(TemplateRegistry templates, ClassStructure structure,
                                  long cMinValue, long cMaxValue,
                                  int cNumBits, boolean fUnsigned, boolean fChecked)
        {
        super(templates, structure, false);

        f_cMinValue = cMinValue;
        f_cMaxValue = cMaxValue;
        f_cNumBits  = cNumBits;
        f_fChecked  = fChecked;
        f_fSigned   = !fUnsigned;

        f_cAddCheckShift = 64 - cNumBits;
        f_cMulCheckShift = fUnsigned ? (cNumBits / 2) : (cNumBits / 2 - 1);
        }

    @Override
    public void initDeclared()
        {
        String sName = f_struct.getName();

        markNativeProperty("magnitude");
        markNativeProperty("digitCount");
        markNativeProperty("bitCount");
        markNativeProperty("leftmostBit");
        markNativeProperty("rightmostBit");
        markNativeProperty("leadingZeroCount");
        markNativeProperty("trailingZeroCount");

        markNativeMethod("to", VOID, sName.equals("Int64")  ? THIS : new String[]{"Int64"});
        markNativeMethod("to", VOID, sName.equals("Int32")  ? THIS : new String[]{"Int32"});
        markNativeMethod("to", VOID, sName.equals("Int16")  ? THIS : new String[]{"Int16"});
        markNativeMethod("to", VOID, sName.equals("Int8")   ? THIS : new String[]{"Int8"});
        markNativeMethod("to", VOID, sName.equals("UInt64") ? THIS : new String[]{"UInt64"});
        markNativeMethod("to", VOID, sName.equals("UInt32") ? THIS : new String[]{"UInt32"});
        markNativeMethod("to", VOID, sName.equals("UInt16") ? THIS : new String[]{"UInt16"});
        markNativeMethod("to", VOID, sName.equals("UInt8")  ? THIS : new String[]{"UInt8"});

        markNativeMethod("to", VOID, new String[]{"Int128"});
        markNativeMethod("to", VOID, new String[]{"UInt128"});
        markNativeMethod("to", VOID, new String[]{"VarInt"});
        markNativeMethod("to", VOID, new String[]{"VarUInt"});
        markNativeMethod("to", VOID, new String[]{"VarFloat"});
        markNativeMethod("to", VOID, new String[]{"VarDec"});
        markNativeMethod("to", VOID, new String[]{"Char"});
        markNativeMethod("to", VOID, new String[]{"collections.Array<Boolean>"});

        markNativeMethod("rotateLeft"   , INT , THIS);
        markNativeMethod("rotateRight"  , INT , THIS);
        markNativeMethod("truncate"     , INT , THIS);
        markNativeMethod("reverseBits"  , VOID, THIS);
        markNativeMethod("reverseBytes" , VOID, THIS);
        markNativeMethod("stepsTo"      , THIS, INT );

        // @Op methods
        markNativeMethod("abs"          , VOID, THIS);
        markNativeMethod("add"          , THIS, THIS);
        markNativeMethod("sub"          , THIS, THIS);
        markNativeMethod("mul"          , THIS, THIS);
        markNativeMethod("div"          , THIS, THIS);
        markNativeMethod("mod"          , THIS, THIS);
        markNativeMethod("neg"          , VOID, THIS);
        markNativeMethod("and"          , THIS, THIS);
        markNativeMethod("or"           , THIS, THIS);
        markNativeMethod("xor"          , THIS, THIS);
        markNativeMethod("not"          , VOID, THIS);
        markNativeMethod("shiftLeft"    , INT, THIS);
        markNativeMethod("shiftRight"   , INT, THIS);
        markNativeMethod("shiftAllRight", INT, THIS);
        }

    /**
     * @return a complimentary template (signed for unsigned and vice versa)
     */
    abstract protected xConstrainedInteger getComplimentaryTemplate();

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntConstant)
            {
            frame.pushStack(new JavaLong(getCanonicalClass(),
                    (((IntConstant) constant).getValue().getLong())));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "hash":
                return buildHashCode(frame, hTarget, iReturn);

            case "magnitude":
                {
                if (f_fSigned)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    hTarget = getComplimentaryTemplate().makeJavaLong(l < 0 ? -l : l);
                    }
                return frame.assignValue(iReturn, hTarget);
                }

            case "digitCount":
                {
                long l = ((JavaLong) hTarget).getValue();

                if (l < 0)
                    {
                    l = -l;
                    }

                int cDigits = 19;
                if (l >= 0)
                    {
                    long n = 10;
                    for (cDigits = 1; cDigits < 19; ++cDigits)
                        {
                        if (l < n)
                            {
                            break;
                            }
                        n *= 10;
                        }
                    }

                return frame.assignValue(iReturn, makeInt(cDigits));
                }

            case "bitCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeInt(Long.bitCount(l)));
                }

            case "leftmostBit":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.highestOneBit(l)));
                }

            case "rightmostBit":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.lowestOneBit(l)));
                }

            case "leadingZeroCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.numberOfLeadingZeros(l)));
                }

            case "trailingZeroCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.numberOfTrailingZeros(l)));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "add":
                return invokeAdd(frame, hTarget, hArg, iReturn);

            case "sub":
                return invokeSub(frame, hTarget, hArg, iReturn);

            case "mul":
                return invokeMul(frame, hTarget, hArg, iReturn);

            case "div":
                return invokeDiv(frame, hTarget, hArg, iReturn);

            case "mod":
                return invokeMod(frame, hTarget, hArg, iReturn);

            case "and":
                return invokeAnd(frame, hTarget, hArg, iReturn);

            case "or":
                return invokeOr(frame, hTarget, hArg, iReturn);

            case "xor":
                return invokeXor(frame, hTarget, hArg, iReturn);

            case "not":
                return invokeCompl(frame, hTarget, iReturn);

            case "shiftLeft":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftRight":
                return invokeShr(frame, hTarget, hArg, iReturn);

            case "shiftAllRight":
                return invokeShrAll(frame, hTarget, hArg, iReturn);

            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "abs":
                {
                if (f_fSigned)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    return frame.assignValue(iReturn, l >= 0 ? hTarget : makeJavaLong(-l));
                    }
                return frame.assignValue(iReturn, hTarget);
                }

            case "to":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = getTemplateByType(typeRet);

                if (template == this)
                    {
                    return frame.assignValue(iReturn, hTarget);
                    }

                if (template instanceof xConstrainedInteger)
                    {
                    xConstrainedInteger templateTo = (xConstrainedInteger) template;
                    long                lValue     = ((JavaLong) hTarget).getValue();

                    // there is one overflow case that needs to be handled here: UInt64 -> Int*
                    if (lValue < 0 && this instanceof xUInt64)
                        {
                        return templateTo.overflow(frame);
                        }

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof xBaseInt128)
                    {
                    xBaseInt128 templateTo = (xBaseInt128) template;
                    long        lValue     = ((JavaLong) hTarget).getValue();

                    if (f_fSigned && lValue < 0 && !templateTo.f_fSigned)
                        {
                        // cannot assign negative value to the unsigned type
                        return overflow(frame);
                        }

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof xChar)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    if (l > 0x10_FFFF)
                        {
                        l &= 0x0F_FFFF;
                        }
                    return frame.assignValue(iReturn, xChar.makeHandle(l));
                    }

                if (template instanceof xBoolean)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    return frame.assignValue(iReturn, l == 0 ? xBoolean.FALSE : xBoolean.TRUE);
                    }

                break;
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);

            case "rotateLeft":
            case "rotateRight":
            case "reverseBits":
            case "reverseBytes":
                throw new UnsupportedOperationException("subclass implementation required for " + method.getName());

            case "truncate":
                {
                long lValue = ((JavaLong) hTarget ).getValue();
                long cBits  = ((JavaLong) ahArg[0]).getValue();
                if (cBits < 0 || cBits > f_cNumBits)
                    {
                    return frame.raiseException(xException.outOfRange(cBits, f_cNumBits));
                    }

                if (cBits == 0)
                    {
                    lValue = 0;
                    }
                else if (cBits != f_cNumBits)
                    {
                    lValue = lValue & (0xFFFFFFFFFFFFFFFFL >>> (64-cBits));
                    }

                return frame.assignValue(iReturn, makeJavaLong(lValue));
                }

            case "stepsTo":
                {
                long lFrom = ((JavaLong) hTarget ).getValue();
                long lTo   = ((JavaLong) ahArg[0]).getValue();
                return frame.assignValue(iReturn, makeJavaLong(lTo - lFrom));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (f_fChecked && (((l1 ^ lr) & (l2 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (f_fChecked && (((l1 ^ l2) & (l1 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 * l2;

        if (f_fChecked)
            {
            long a1 = Math.abs(l1);
            long a2 = Math.abs(l2);
            if ((a1 | a2) >>> f_cMulCheckShift != 0)
                {
                // see Math.multiplyExact()
                if (((l2 != 0) && ((lr & f_cMaxValue) / l2 != l1)) ||
                        (l1 == f_cMinValue && l2 == -1))
                    {
                    return overflow(frame);
                    }
                }
            }


        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && (!f_fSigned || l == f_cMinValue))
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && l == f_cMinValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && l == f_cMaxValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l + 1));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeJavaLong(l1 / l2));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        long lMod = l1 % l2;
        if (lMod < 0)
            {
            lMod += (l2 < 0 ? -l2 : l2);
            }

        return frame.assignValue(iReturn, makeJavaLong(lMod));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 << l2));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 >> l2));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 >>> l2));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 & l2));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 | l2));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 ^ l2));
        }

    @Override
    public int invokeDivMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        long lMod = l1 % l2;
        if (lMod < 0)
            {
            lMod += (l2 < 0 ? -l2 : l2);
            }

        return frame.assignValues(aiReturn, makeJavaLong(l1 / l2), makeJavaLong(lMod));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeJavaLong(~l));
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget);
        }

    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(Long.compare(h1.getValue(), h2.getValue())));
        }

    /**
     * Convert a long value into a handle for the type represented by this template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int convertLong(Frame frame, long lValue, int iReturn)
        {
        if (lValue < f_cMinValue || lValue > f_cMaxValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(String.valueOf(l)));
        }

    /**
     * Raise an overflow exception.
     *
     * @return {@link Op#R_EXCEPTION}
     */
    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.makeHandle(f_struct.getName() + " overflow"));
        }

    public JavaLong makeInt(long lValue)
        {
        return xInt64.makeHandle(lValue);
        }

    public JavaLong makeJavaLong(long lValue)
        {
        // TODO: cache frequently used values
        return new JavaLong(getCanonicalClass(), lValue);
        }


    // ----- helpers -----

    /**
     * @param type  a type to get a template for
     *
     * @return a class template instance corresponding to the specified name
     */
    public static ClassTemplate getTemplateByType(TypeConstant type)
        {
        Constant constant = type.getDefiningConstant();
        if (constant instanceof ThisClassConstant)
            {
            constant = ((ThisClassConstant) constant).getDeclarationLevelClass();
            }
        String sName = ((ClassConstant) constant).getPathElementString();

        switch (sName)
            {
            case "Int8":
                return xInt8.INSTANCE;
            case "Int16":
                return xInt16.INSTANCE;
            case "Int32":
                return xInt32.INSTANCE;
            case "Int64":
                return xInt64.INSTANCE;
            case "Int128":
                return xInt128.INSTANCE;
            case "UInt8":
                return xUInt8.INSTANCE;
            case "UInt16":
                return xUInt16.INSTANCE;
            case "UInt32":
                return xUInt32.INSTANCE;
            case "UInt64":
                return xUInt64.INSTANCE;
            case "UInt128":
                return xUInt128.INSTANCE;
            default:
                return null;
            }
        }


    // ----- fields -----

    protected final long f_cMinValue;
    protected final long f_cMaxValue;
    protected final int  f_cNumBits;
    protected final int  f_cAddCheckShift;
    protected final int  f_cMulCheckShift;

    protected final boolean f_fChecked;
    protected final boolean f_fSigned;
    }
