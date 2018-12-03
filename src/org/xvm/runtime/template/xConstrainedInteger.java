package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;


/**
 * Abstract base class for constrained integers (Int8, UInt16, @Unchecked Int32, ...)
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
        f_cNumBits = cNumBits;
        f_fChecked = fChecked;
        f_fUnsigned = fUnsigned;

        f_cAddCheckShift = 64 - cNumBits;
        f_cMulCheckShift = fUnsigned ? (cNumBits / 2) : (cNumBits / 2 - 1);
        }

    @Override
    public void initDeclared()
        {
        String sName = f_struct.getName();

        markNativeMethod("to", VOID, sName.equals("Int64")  ? THIS : new String[]{"Int64"});
        markNativeMethod("to", VOID, sName.equals("Int32")  ? THIS : new String[]{"Int32"});
        markNativeMethod("to", VOID, sName.equals("Int16")  ? THIS : new String[]{"Int16"});
        markNativeMethod("to", VOID, sName.equals("Int8")   ? THIS : new String[]{"Int8"});
        markNativeMethod("to", VOID, sName.equals("UInt64") ? THIS : new String[]{"UInt64"});
        markNativeMethod("to", VOID, sName.equals("UInt32") ? THIS : new String[]{"UInt32"});
        markNativeMethod("to", VOID, sName.equals("UInt16") ? THIS : new String[]{"UInt16"});
        markNativeMethod("to", VOID, sName.equals("UInt8")  ? THIS : new String[]{"UInt8"});

        if (!f_fUnsigned)
            {
            markNativeMethod("abs", VOID, THIS);
            }

        // @Op methods
        markNativeMethod("add", THIS, THIS);
        markNativeMethod("sub", THIS, THIS);
        markNativeMethod("mul", THIS, THIS);
        markNativeMethod("div", THIS, THIS);
        markNativeMethod("mod", THIS, THIS);
        markNativeMethod("neg", VOID, THIS);
        }

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
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, l >= 0 ? hTarget : makeJavaLong(-l));
                }

            case "to":
                {
                TypeConstant        typeRet  = method.getReturn(0).getType();
                xConstrainedInteger template = getTemplateByType(typeRet);
                if (template != null)
                    {
                    return template.convertIntegerType(frame, ((JavaLong) hTarget).getValue(), iReturn);
                    }
                break;
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);
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

        if (f_fChecked && l == f_cMinValue)
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
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        switch (sPropName)
            {
            case "hash":
                return frame.assignValue(iReturn, hThis);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget);
        }

    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(h1.getValue() - h2.getValue()));
        }

    /**
     * Converts an object of "this" integer type to the type represented by the template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    protected int convertIntegerType(Frame frame, long lValue, int iReturn)
        {
        if (lValue < f_cMinValue || lValue > f_cMaxValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
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

    public JavaLong makeJavaLong(long lValue)
        {
        return new JavaLong(getCanonicalClass(), lValue);
        }


    // ----- helpers -----

    /**
     * @param type  a type to get a template for
     *
     * @return a class template instance corresponding to the specified name
     */
    public static xConstrainedInteger getTemplateByType(TypeConstant type)
        {
        String sName = ((ClassConstant) type.getDefiningConstant()).getPathElementString();

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
            case "UInt8":
                return xUInt8.INSTANCE;
            case "UInt16":
                return xUInt16.INSTANCE;
            case "UInt32":
                return xUInt32.INSTANCE;
            case "UInt64":
                return xUInt64.INSTANCE;
            default:
                return null;
            }
        }


    // ----- fields -----

    protected final long f_cMinValue;
    protected final long f_cMaxValue;
    protected final int f_cNumBits;
    protected final int f_cAddCheckShift;
    protected final int f_cMulCheckShift;

    protected final boolean f_fChecked;
    protected final boolean f_fUnsigned;
    }
