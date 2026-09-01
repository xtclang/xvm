package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorList;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OperatorBinding;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.PackedInteger;


/**
 * Native IntLiteral implementation.
 */
public class xIntLiteral
        extends xConst {
    public xIntLiteral(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        bindOp(OperatorBinding.Op.ADD, IntNHandle.class, IntNHandle.class, this::opAdd);
        bindOp(OperatorBinding.Op.SUB, IntNHandle.class, IntNHandle.class, this::opSub);
        bindOp(OperatorBinding.Op.MUL, IntNHandle.class, IntNHandle.class, this::opMul);
        bindOp(OperatorBinding.Op.DIV, IntNHandle.class, IntNHandle.class, this::opDiv);
        bindOp(OperatorBinding.Op.MOD, IntNHandle.class, IntNHandle.class, this::opMod);
        bindOp(OperatorBinding.Op.SHL, IntNHandle.class, JavaLong.class, this::opShl);
        bindOp(OperatorBinding.Op.SHR, IntNHandle.class, JavaLong.class, this::opShr);
        bindOp(OperatorBinding.Op.SHR_ALL, IntNHandle.class, JavaLong.class, this::opShrAll);
        bindOp(OperatorBinding.Op.AND, IntNHandle.class, IntNHandle.class, this::opAnd);
        bindOp(OperatorBinding.Op.OR, IntNHandle.class, IntNHandle.class, this::opOr);
        bindOp(OperatorBinding.Op.XOR, IntNHandle.class, IntNHandle.class, this::opXor);
        bindOp(OperatorBinding.Op.NEG, IntNHandle.class, this::opNeg);
        bindOp(OperatorBinding.Op.COMPL, IntNHandle.class, this::opCompl);

        markNativeMethod("construct", STRING, VOID);

        markNativeMethod("and",           THIS, THIS);
        markNativeMethod("or",            THIS, THIS);
        markNativeMethod("xor",           THIS, THIS);
        markNativeMethod("shiftLeft",     INT,  THIS);
        markNativeMethod("shiftRight",    INT,  THIS);
        markNativeMethod("shiftAllRight", INT,  THIS);
        markNativeMethod("add",           THIS, THIS);
        markNativeMethod("sub",           THIS, THIS);
        markNativeMethod("mul",           THIS, THIS);
        markNativeMethod("div",           THIS, THIS);
        markNativeMethod("mod",           THIS, THIS);
        markNativeMethod("not",           VOID, THIS);

        markNativeMethod("toString", VOID, STRING);

        markNativeMethod("toInt8"   , null, new String[]{"numbers.Int8"});
        markNativeMethod("toInt16"  , null, new String[]{"numbers.Int16"});
        markNativeMethod("toInt32"  , null, new String[]{"numbers.Int32"});
        markNativeMethod("toInt64"  , null, new String[]{"numbers.Int64"});
        markNativeMethod("toInt128" , null, new String[]{"numbers.Int128"});

        markNativeMethod("toUInt8"  , null, new String[]{"numbers.UInt8"});
        markNativeMethod("toUInt16" , null, new String[]{"numbers.UInt16"});
        markNativeMethod("toUInt32" , null, new String[]{"numbers.UInt32"});
        markNativeMethod("toUInt64" , null, new String[]{"numbers.UInt64"});
        markNativeMethod("toUInt128", null, new String[]{"numbers.UInt128"});

        markNativeMethod("toIntN"   , null, new String[]{"numbers.IntN"});
        markNativeMethod("toUIntN"  , null, new String[]{"numbers.UIntN"});
        markNativeMethod("toFloatN" , null, new String[]{"numbers.FloatN"});
        markNativeMethod("toDecN"   , null, new String[]{"numbers.DecN"});

        invalidateTypeInfo();
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        LiteralConstant constVal    = (LiteralConstant) constant;
        StringHandle    hText       = (StringHandle) frame.getConstHandle(constVal.getStringConstant());
        IntNHandle      hIntLiteral = makeIntLiteral(constVal.getPackedInteger(), hText);

        return frame.pushStack(hIntLiteral);
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        StringHandle hText = (StringHandle) ahVar[0];
        String       sText = hText.getStringValue();

        PackedInteger pi;
        try {
            pi = parsePackedInteger(sText);
        } catch (NumberFormatException e) {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + sText + "\""));
        }

        return frame.assignValue(iReturn, makeIntLiteral(pi, hText));
    }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn) {
        switch (idProp.getName()) {
        case "text":
            return frame.assignValue(iReturn, ((IntNHandle) hTarget).getText());
        }
        return frame.raiseException("not supported field: " + idProp.getName());
    }














    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "and":
            return invokeAnd(frame, hTarget, hArg, iReturn);

        case "or":
            return invokeOr(frame, hTarget, hArg, iReturn);

        case "xor":
            return invokeXor(frame, hTarget, hArg, iReturn);

        case "shiftLeft":
            return invokeShl(frame, hTarget, hArg, iReturn);

        case "shiftRight":
            return invokeShr(frame, hTarget, hArg, iReturn);

        case "shiftAllRight":
            return invokeShrAll(frame, hTarget, hArg, iReturn);

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
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "toInt8":
        case "toInt16":
        case "toInt32":
        case "toInt64":
        case "toInt128":
        case "toUInt8":
        case "toUInt16":
        case "toUInt32":
        case "toUInt64":
        case "toUInt128":
        case "toIntN":
        case "toUIntN":
        case "toFloatN":
        case "toDecN":
            TypeConstant  typeRet  = method.getReturn(0).getType();
            ClassTemplate template = f_container.getTemplate(typeRet);
            IntNHandle    hLiteral = (IntNHandle) hTarget;
            PackedInteger piValue  = hLiteral.getValue();

            if (template instanceof xConstrainedInteger templateTo) {
                return templateTo.convertLong(frame, piValue, true, iReturn);
            }

            if (template instanceof BaseInt128 templateTo) {
                BigInteger biValue = piValue.getBigInteger();
                if (biValue.bitLength() > 128) {
                    return templateTo.overflow(frame);
                }

                LongLong llValue = LongLong.fromBigInteger(biValue);
                return !templateTo.f_fSigned && llValue.signum() < 0
                        ? templateTo.overflow(frame)
                        : frame.assignValue(iReturn, templateTo.makeHandle(llValue));
            }

            if (template instanceof xUnconstrainedInteger templateTo) {
                return piValue.isNegative() && !templateTo.f_fSigned
                        ? templateTo.overflow(frame)
                        : frame.assignValue(iReturn, templateTo.makeInt(piValue));
            }
            break;

        case "not":
            return invokeCompl(frame, hTarget, iReturn);
        }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    protected IntNHandle makeIntLiteral(PackedInteger piValue) {
        return new IntNHandle(getCanonicalClass(), piValue, null);
    }

    protected IntNHandle makeIntLiteral(PackedInteger piValue, StringHandle hText) {
        return new IntNHandle(getCanonicalClass(), piValue, hText);
    }

    @Override
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        IntNHandle hLiteral = (IntNHandle) hTarget;
        return frame.assignValue(iReturn, xInt64.makeHandle(frame, hLiteral.getValue().hashCode()));
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        IntNHandle hLiteral = (IntNHandle) hTarget;
        return frame.assignValue(iReturn, hLiteral.getText());
    }


    private int opAdd(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.add(pi2)));
    }

    private int opSub(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.sub(pi2)));
    }

    private int opMul(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mul(pi2)));
    }

    private int opDiv(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.div(pi2)));
    }

    private int opMod(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mod(pi2)));
    }

    private int opShl(Frame frame, IntNHandle hTarget, JavaLong hArg, int iReturn) {
        PackedInteger pi    = hTarget.getValue();
        long          count = hArg.getValue();

        if (count > Integer.MAX_VALUE) {
            return overflow(frame);
        }
        return frame.assignValue(iReturn, makeIntLiteral(pi.shl((int) count)));
    }

    private int opShr(Frame frame, IntNHandle hTarget, JavaLong hArg, int iReturn) {
        PackedInteger pi    = hTarget.getValue();
        long          count = hArg.getValue();

        if (count > Integer.MAX_VALUE) {
            return overflow(frame);
        }
        return frame.assignValue(iReturn, makeIntLiteral(pi.shr((int) count)));
    }

    private int opShrAll(Frame frame, IntNHandle hTarget, JavaLong hArg, int iReturn) {
        PackedInteger pi    = hTarget.getValue();
        long          count = hArg.getValue();

        if (count > Integer.MAX_VALUE) {
            return overflow(frame);
        }
        return frame.assignValue(iReturn, makeIntLiteral(pi.ushr((int) count)));
    }

    private int opAnd(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.and(pi2)));
    }

    private int opOr(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.or(pi2)));
    }

    private int opXor(Frame frame, IntNHandle hTarget, IntNHandle hArg, int iReturn) {
        PackedInteger pi1 = hTarget.getValue();
        PackedInteger pi2 = hArg.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.xor(pi2)));
    }

    private int opNeg(Frame frame, IntNHandle hTarget, int iReturn) {
        PackedInteger pi = hTarget.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi.negate()));
    }

    private int opCompl(Frame frame, IntNHandle hTarget, int iReturn) {
        PackedInteger pi = hTarget.getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi.complement()));
    }

    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        IntNHandle h1 = (IntNHandle) hValue1;
        IntNHandle h2 = (IntNHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, h1.getValue().equals(h2.getValue())));
    }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        IntNHandle h1 = (IntNHandle) hValue1;
        IntNHandle h2 = (IntNHandle) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(frame, h1.getValue().cmp(h2.getValue())));
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Parse the specified text into a BigInteger value.
     *
     * @throws NumberFormatException  if parsing failed
     */
    public static BigInteger parseBigInteger(String sText) {
        try {
            return new BigInteger(sText, 10);
        } catch (NumberFormatException e) {
            ErrorList errs  = ErrorList.firstError();
            Lexer     lexer = new Lexer(new Source(sText), errs);

            if (!lexer.hasNext()) {
                throw e;
            }

            Token tokLit = lexer.next();
            if (errs.hasSeriousErrors() || tokLit.getId() != Token.Id.LIT_INT) {
                throw e;
            }

            return ((PackedInteger) tokLit.getValue()).getBigInteger();
        }
    }

    /**
     * Parse the specified text into a PackedInteger value.
     *
     * @throws NumberFormatException  if parsing failed
     */
    public static PackedInteger parsePackedInteger(String sText) {
        try {
            return new PackedInteger(Long.parseLong(sText));
        } catch (NumberFormatException e) {
            if (sText.isEmpty()) {
                throw e;
            }
            boolean   fNeg   = false;
            ErrorList errs   = ErrorList.firstError();
            Lexer     lexer  = new Lexer(new Source(sText), errs);
            Token     tokLit = lexer.next();
            if (tokLit.getId() == Id.SUB) {
                fNeg = true;
                tokLit = lexer.next();
            }

            if (errs.hasSeriousErrors() || tokLit.getId() != Token.Id.LIT_INT) {
                throw e;
            }

            PackedInteger pi = (PackedInteger) tokLit.getValue();
            return fNeg
                    ? pi.negate()
                    : pi;
        }
    }

    /**
     * This handle type is used by IntN, UIntN as well as IntLiteral.
     */
    public static class IntNHandle
            extends ObjectHandle {
        public IntNHandle(TypeComposition clazz, PackedInteger piValue, StringHandle hText) {
            super(clazz);

            assert piValue != null;

            m_piValue = piValue;
            m_hText    = hText;
        }

        public StringHandle getText() {
            StringHandle hText = m_hText;
            if (hText == null) {
                m_hText = hText = xString.makeHandle(this, m_piValue.toString());
            }
            return hText;
        }

        public PackedInteger getValue() {
            return m_piValue;
        }

        @Override
        public int hashCode() { return m_piValue.hashCode(); }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IntNHandle that && m_piValue.equals(that.m_piValue);
        }

        @Override
        public String toString() {
            return super.toString() + m_piValue.toString();
        }

        protected PackedInteger m_piValue;
        protected StringHandle  m_hText; // (optional) cached text handle
    }
}
