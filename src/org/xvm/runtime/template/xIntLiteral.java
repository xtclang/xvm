package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

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
        markNativeMethod("to", VOID, INT);
        markNativeMethod("to", VOID, STRING);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        LiteralConstant constVal = (LiteralConstant) constant;
        VarIntHandle hIntLiteral = makeIntLiteral(constVal.getPackedInteger());

        return frame.assignValue(Op.A_STACK, hIntLiteral);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi = ((VarIntHandle) hTarget).getValue(); // TODO: make this actually add
        return frame.assignValue(iReturn, makeIntLiteral(pi));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        VarIntHandle hLiteral = (VarIntHandle) hTarget;
        switch (method.getName())
            {
            case "to":
                TypeConstant        typeRet  = method.getReturn(0).getType();
                xConstrainedInteger template = xConstrainedInteger.getTemplateByType(typeRet);
                if (template != null)
                    {
                    return template.convertIntegerType(frame, hLiteral.getValue().getLong(), iReturn);
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
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
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
