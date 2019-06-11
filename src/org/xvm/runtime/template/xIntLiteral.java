package org.xvm.runtime.template;


import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.util.PackedInteger;


/**
 * TODO:
 */
public class xIntLiteral
        extends ClassTemplate
    {
    public static xIntLiteral INSTANCE;

    public xIntLiteral(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("construct", STRING, VOID);

        markNativeMethod("toString", VOID, STRING);

        markNativeMethod("to", VOID, new String[]{"Int128"});
        markNativeMethod("to", VOID, new String[]{"Int64"});
        markNativeMethod("to", VOID, new String[]{"Int32"});
        markNativeMethod("to", VOID, new String[]{"Int16"});
        markNativeMethod("to", VOID, new String[]{"Int8"});

        markNativeMethod("to", VOID, new String[]{"UInt128"});
        markNativeMethod("to", VOID, new String[]{"UInt64"});
        markNativeMethod("to", VOID, new String[]{"UInt32"});
        markNativeMethod("to", VOID, new String[]{"UInt16"});
        markNativeMethod("to", VOID, new String[]{"UInt8"});

        markNativeMethod("to", VOID, new String[]{"VarInt"});
        markNativeMethod("to", VOID, new String[]{"VarUInt"});
        markNativeMethod("to", VOID, new String[]{"VarFloat"});
        markNativeMethod("to", VOID, new String[]{"VarDec"});
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        LiteralConstant constVal = (LiteralConstant) constant;
        VarIntHandle hIntLiteral = makeIntLiteral(constVal.getPackedInteger());

        return frame.assignValue(Op.A_STACK, hIntLiteral);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        String sText = ((StringHandle) ahVar[0]).getStringValue();

        // TODO: large numbers
        try
            {
            long lValue = Long.parseLong(sText);

            return frame.assignValue(iReturn,
                makeIntLiteral(new PackedInteger(lValue)));
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.makeHandle("Invalid number \"" + sText + "\""));
            }
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.add(pi2)));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.sub(pi2)));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mul(pi2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.div(pi2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mod(pi2)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.and(pi2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.or(pi2)));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        VarIntHandle hLiteral = (VarIntHandle) hTarget;
        switch (method.getName())
            {
            case "to":
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_templates.getTemplate(typeRet);
                PackedInteger piValue  = hLiteral.getValue();

                if (template instanceof xConstrainedInteger)
                    {
                    if (piValue.isBig())
                        {
                        return ((xConstrainedInteger) template).overflow(frame);
                        }
                    return ((xConstrainedInteger) template).
                        convertLong(frame, piValue.getLong(), iReturn);
                    }
                if (template instanceof xBaseInt128)
                    {
                    xBaseInt128 template128 = (xBaseInt128) template;
                    BigInteger  biValue     = piValue.getBigInteger();
                    LongLong    llValue     = LongLong.fromBigInteger(biValue);

                    if (!template128.f_fSigned && llValue.signum() < 0)
                        {
                        return template128.overflow(frame);
                        }
                    return frame.assignValue(iReturn, template128.makeLongLong(llValue));
                    }
                break;
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected VarIntHandle makeIntLiteral(PackedInteger piValue)
        {
        return new VarIntHandle(getCanonicalClass(), piValue);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        VarIntHandle hLiteral = (VarIntHandle) hTarget;
        return frame.assignValue(iReturn, xString.makeHandle(hLiteral.getValue().toString()));
        }

    /**
     * This handle type is used by VarInt, VarUInt as well as IntLiteral.
     */
    public static class VarIntHandle
            extends ObjectHandle
        {
        public VarIntHandle(TypeComposition clazz, PackedInteger piValue)
            {
            super(clazz);

            assert piValue != null;

            m_piValue = piValue;
            }

        public PackedInteger getValue()
            {
            return m_piValue;
            }

        @Override
        public int hashCode() { return m_piValue.hashCode(); }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof VarIntHandle && m_piValue.equals(((VarIntHandle) obj).m_piValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_piValue.toString();
            }

        protected PackedInteger m_piValue;
        }
    }
