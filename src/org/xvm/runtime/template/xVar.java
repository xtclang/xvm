package org.xvm.runtime.template;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.TypeConstant;

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
    public static TypeConstant INCEPTION_TYPE;

    public xVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_TYPE = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant()).asTypeConstant();
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    protected TypeConstant getInceptionType()
        {
        if (this == INSTANCE)
            {
            return INCEPTION_TYPE;
            }

        // there are no natural classes that extend Var, but there is a number of native templates
        // that represent Var mixins that extend xVar

        TypeConstant type = m_typeInception;
        if (type == null)
            {
            type = m_typeInception = f_struct.getConstantPool().ensureAnnotatedTypeConstant(
                new Annotation(f_struct.getIdentityConstant(), null), INCEPTION_TYPE);
            }
        return type;
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
                RefHandle hDelegate = (RefHandle) hTarget.getValue();
                return hDelegate.getVarSupport().set(frame, hDelegate, hValue);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hDelegate = hTarget.getValue();
                return hDelegate.getTemplate().setPropertyValue(
                    frame, hDelegate, hTarget.getName(), hTarget);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle hArray = hTarget.getValue();
                IndexSupport template = (IndexSupport) hArray.getTemplate();

                return template.assignArrayValue(frame, hArray, hIndexedRef.f_lIndex, hValue);
                }

            default:
                assert hTarget.m_iVar >= 0;
                frame.f_ahVar[hTarget.m_iVar] = hValue;
                return Op.R_NEXT;
            }
        }

    protected int setInternal(Frame frame, RefHandle hRef, ObjectHandle hValue)
        {
        hRef.setValue(hValue);
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
