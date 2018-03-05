package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlaceVarBinary;
import org.xvm.runtime.Utils.InPlaceVarUnary;
import org.xvm.runtime.Utils.UnaryAction;

import org.xvm.runtime.VarSupport;


/**
 * TODO:
 */
public class xVar
        extends xRef
        implements VarSupport
    {
    public static xVar INSTANCE;

    public xVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("set", new String[]{"RefType"}, VOID);
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
        CallChain chain = getOpChain("preInc");
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postInc");
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, true, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("preDec");
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.DEC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postDec");
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
                RefHandle hDelegate = (RefHandle) hTarget.m_hDelegate;
                return hDelegate.getVarSupport().set(frame, hDelegate, hValue);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hDelegate = hTarget.m_hDelegate;
                return hDelegate.getTemplate().setPropertyValue(
                    frame, hDelegate, hTarget.m_sName, hTarget);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle hArray = hTarget.m_hDelegate;
                IndexSupport template = (IndexSupport) hArray.getTemplate();

                ExceptionHandle hException =
                    template.assignArrayValue(hArray, hIndexedRef.f_lIndex, hValue);
                return hException == null ? Op.R_NEXT : frame.raiseException(hException);
                }

            default:
                assert hTarget.m_iVar >= 0;
                frame.f_ahVar[hTarget.m_iVar] = hValue;
                return Op.R_NEXT;
            }
        }

    protected int setInternal(Frame frame, RefHandle hRef, ObjectHandle hValue)
        {
        hRef.m_hDelegate = hValue;
        return Op.R_NEXT;
        }

    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("+=");
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.ADD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("-=");
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SUB, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("*=");
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MUL, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("/=");
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.DIV, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("%=");
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MOD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }
    }
