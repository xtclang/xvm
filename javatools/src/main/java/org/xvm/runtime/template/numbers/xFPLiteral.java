package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native FPLiteral implementation.
 */
public class xFPLiteral
        extends xConst
    {
    public static xFPLiteral INSTANCE;

    public xFPLiteral(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("construct", STRING, VOID);

        markNativeMethod("toString", VOID, STRING);

        markNativeMethod("toFloatN"  , VOID, new String[]{"numbers.FloatN"});
        markNativeMethod("toFloat16"   , VOID, new String[]{"numbers.Float16"});
        markNativeMethod("toFloat32"   , VOID, new String[]{"numbers.Float32"});
        markNativeMethod("toFloat64"   , VOID, new String[]{"numbers.Float64"});
        markNativeMethod("toFloat128"  , VOID, new String[]{"numbers.Float128"});
        markNativeMethod("toDecN"    , VOID, new String[]{"numbers.DecN"});
        markNativeMethod("toDec32"     , VOID, new String[]{"numbers.Dec32"});
        markNativeMethod("toDec64"     , VOID, new String[]{"numbers.Dec64"});
        markNativeMethod("toDec128"    , VOID, new String[]{"numbers.Dec128"});

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        LiteralConstant constVal    = (LiteralConstant) constant;
        StringHandle    hText       = (StringHandle) frame.getConstHandle(constVal.getStringConstant());
        FPNHandle     hFPLiteral  = makeFPLiteral(constVal.getBigDecimal(), hText);

        return frame.pushStack(hFPLiteral);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        StringHandle hText = (StringHandle) ahVar[0];
        try
            {
            return frame.assignValue(iReturn,
                makeFPLiteral(new BigDecimal(hText.getStringValue()), hText));
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + hText.getStringValue() + "\""));
            }
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        switch (idProp.getName())
            {
            case "text":
                return frame.assignValue(iReturn, ((FPNHandle) hTarget).getText());
            }
        return frame.raiseException("not supported field: " + idProp.getName());
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal dec1 = ((FPNHandle) hTarget).getValue();
        BigDecimal dec2 = ((FPNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFPLiteral(dec1.add(dec2)));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal dec1 = ((FPNHandle) hTarget).getValue();
        BigDecimal dec2 = ((FPNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFPLiteral(dec1.subtract(dec2)));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal dec1 = ((FPNHandle) hTarget).getValue();
        BigDecimal dec2 = ((FPNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFPLiteral(dec1.multiply(dec2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal dec1 = ((FPNHandle) hTarget).getValue();
        BigDecimal dec2 = ((FPNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFPLiteral(dec1.divide(dec2)));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        FPNHandle hLiteral = (FPNHandle) hTarget;
        switch (method.getName())
            {
            case "toFloat16":
            case "toFloat32":
            case "toFloat64":
                {
                TypeConstant typeRet  = method.getReturn(0).getType();
                BaseBinaryFP template = (BaseBinaryFP) f_templates.getTemplate(typeRet);
                return frame.assignValue(iReturn,
                        template.makeHandle(hLiteral.getValue().doubleValue()));
                }

            case "toDec32":
                return frame.assignValue(iReturn,
                        xDec32.INSTANCE.makeHandle(hLiteral.getValue().doubleValue()));

            case "toDec64":
                return frame.assignValue(iReturn,
                        xDec64.INSTANCE.makeHandle(hLiteral.getValue().doubleValue()));

            case "toFloat128":
            case "toFloatN":
            case "toDecN":
            case "toDec128":
                throw new UnsupportedOperationException(); // TODO
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected FPNHandle makeFPLiteral(BigDecimal decValue)
        {
        return new FPNHandle(getCanonicalClass(), decValue, null);
        }

    protected FPNHandle makeFPLiteral(BigDecimal decValue, StringHandle hText)
        {
        return new FPNHandle(getCanonicalClass(), decValue, hText);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        FPNHandle hLiteral = (FPNHandle) hTarget;
        return frame.assignValue(iReturn, hLiteral.getText());
        }

    /**
     * This handle type is used by IntN, UIntN as well as IntLiteral.
     */
    public static class FPNHandle
            extends ObjectHandle
        {
        public FPNHandle(TypeComposition clazz, BigDecimal decValue, StringHandle hText)
            {
            super(clazz);

            assert decValue != null;

            m_decValue = decValue;
            }

        public StringHandle getText()
            {
            StringHandle hText = m_hText;
            if (hText == null)
                {
                m_hText = hText = xString.makeHandle(m_decValue.toString());
                }
            return hText;
            }

        public BigDecimal getValue()
            {
            return m_decValue;
            }

        @Override
        public int hashCode() { return m_decValue.hashCode(); }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof FPNHandle && m_decValue.equals(((FPNHandle) obj).m_decValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_decValue.toString();
            }

        protected BigDecimal    m_decValue;
        protected StringHandle  m_hText; // (optional) cached text handle
        }
    }
