package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.VarSupport;

import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlaceVarBinary;
import org.xvm.runtime.Utils.InPlaceVarUnary;
import org.xvm.runtime.Utils.UnaryAction;


/**
 * TODO:
 */
public class xVar
        extends xRef
        implements VarSupport
    {
    public static xVar INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public xVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return INCEPTION_CLASS;
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "set":
                return set(frame, hThis, hArg);
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "preInc", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "postInc", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, true, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "preDec", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.DEC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "postDec", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.DEC, hTarget, true, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int set(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        switch (hTarget.m_iVar)
            {
            case RefHandle.REF_REFERENT:
                return setInternal(frame, hTarget, hValue);

            case RefHandle.REF_REF:
                {
                RefHandle hDelegate = (RefHandle) hTarget.getValue();
                return hTarget.getVarSupport().set(frame, hDelegate, hValue);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hDelegate = hTarget.getValue();
                return hDelegate.getTemplate().setPropertyValue(
                    frame, hDelegate, hTarget.getPropertyId(), hTarget);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle hArray = hTarget.getValue();
                IndexSupport template = (IndexSupport) hArray.getTemplate();

                return template.assignArrayValue(frame, hArray, hIndexedRef.f_lIndex, hValue);
                }

            default:
                {
                Frame frameRef = hTarget.m_frame;
                int   nVar     = hTarget.m_iVar;
                assert frameRef != null && nVar >= 0;

                frameRef.f_ahVar[nVar] = hValue;
                return Op.R_NEXT;
                }
            }
        }

    protected int setInternal(Frame frame, RefHandle hRef, ObjectHandle hValue)
        {
        hRef.setValue(hValue);
        return Op.R_NEXT;
        }

    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "+=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.ADD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "-=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SUB, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "*=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MUL, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "/=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.DIV, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "%=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MOD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShl(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "<<=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SHL, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, ">>=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SHR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShrAll(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, ">>>=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.USHR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarAnd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "&=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.AND, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarOr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "|=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.OR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarXor(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "^=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.XOR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }
    }
