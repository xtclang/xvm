package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.SignatureConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlaceVarBinary;
import org.xvm.runtime.Utils.InPlaceVarUnary;
import org.xvm.runtime.Utils.UnaryAction;
import org.xvm.runtime.VarSupport;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xException;


/**
 * Native Var implementation.
 */
public class xVar
        extends xRef
        implements VarSupport
    {
    public static xVar INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public xVar(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void initNative()
        {
        s_sigSet = getStructure().findMethod("set", 1).getIdentityConstant().getSignature();
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
                return setReferentImpl(frame, hThis, true, hArg);
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "preIncrement", "++#", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "postIncrement", "#++", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.INC, hTarget, true, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "preDecrement", "--#", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.DEC, hTarget, false, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = findOpChain(hTarget, "postDecrement", "#--", null);
        return chain == null
            ? new InPlaceVarUnary(UnaryAction.DEC, hTarget, true, iReturn).doNext(frame)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int setReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return setReferentImpl(frame, hTarget, false, hValue);
        }

    /**
     * Set the Var's referent natively (without making a natural "set" call).
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_EXCEPTION}
     */
    public int setNativeReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return setReferentImpl(frame, hTarget, true, hValue);
        }

    protected int setReferentImpl(Frame frame, RefHandle hRef, boolean fNative, ObjectHandle hValue)
        {
        if (!hRef.isMutable())
            {
            return frame.raiseException(xException.readOnly(frame, "Immutable Var"));
            }

        switch (hRef.m_iVar)
            {
            case RefHandle.REF_REFERENT:
                {
                if (fNative)
                    {
                    hRef.setReferent(hValue);
                    return Op.R_NEXT;
                    }
                return invokeSetReferent(frame, hRef, hValue);
                }

            case RefHandle.REF_REF:
                {
                RefHandle hDelegate = (RefHandle) hRef.getReferentHolder();
                return hRef.getVarSupport().setReferent(frame, hDelegate, hValue);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hDelegate = hRef.getReferentHolder();
                return hDelegate.getTemplate().setPropertyValue(
                    frame, hDelegate, hRef.getPropertyId(), hRef);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hRef;
                ObjectHandle     hArray      = hRef.getReferentHolder();
                IndexSupport     template    = (IndexSupport) hArray.getTemplate();

                return template.assignArrayValue(frame, hArray, hIndexedRef.f_lIndex, hValue);
                }

            default:
                {
                Frame frameRef = hRef.m_frame;
                int   nVar     = hRef.m_iVar;
                assert frameRef != null && nVar >= 0;

                frameRef.f_ahVar[nVar] = hValue;
                return Op.R_NEXT;
                }
            }
        }

    /**
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    protected int invokeSetReferent(Frame frame, RefHandle hRef, ObjectHandle hValue)
        {
        CallChain chain = hRef.getComposition().getMethodCallChain(s_sigSet);
        return chain.isExplicit()
            ? chain.invoke(frame, hRef, hValue, Op.A_IGNORE)
            : setReferentImpl(frame, hRef, true, hValue);
        }

    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "addAssign", "+=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.ADD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "subAssign", "-=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SUB, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "mulAssign", "*=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MUL, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "divAssign", "/=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.DIV, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "modAssign", "%=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.MOD, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShl(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "shiftLeftAssign", "<<=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SHL, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "shiftRightAssign", ">>=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.SHR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarShrAll(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "shiftAllRightAssign", ">>>=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.USHR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarAnd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "andAssign", "&=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.AND, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarOr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "orAssign", "|=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.OR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarXor(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, "xorAssign", "^=", hArg);
        return chain == null
            ? new InPlaceVarBinary(BinaryAction.XOR, hTarget, hArg).doNext(frame)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    // ----- constants -----------------------------------------------------------------------------

    protected static SignatureConstant s_sigSet;
    }