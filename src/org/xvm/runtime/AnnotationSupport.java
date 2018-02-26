package org.xvm.runtime;

import org.xvm.asm.Annotation;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xRef.RefHandle;

/**
 * An OpSupport implementation for annotated types.
 */
public class AnnotationSupport
        implements OpSupport, VarSupport
    {
    public AnnotationSupport(OpSupport support, Annotation annotation)
        {
        f_support = support;
        f_annotation = annotation;
        }

    // ----- OpSupport implementation --------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate()
        {
        return f_support.getTemplate();
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("+");
        return chain == null
            ? f_support.invokeAdd(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("-");
        return chain == null
            ? f_support.invokeSub(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("*");
        return chain == null
            ? f_support.invokeMul(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("/");
        return chain == null
            ? f_support.invokeDiv(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("%");
        return chain == null
            ? f_support.invokeMod(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("neg");
        return chain == null
            ? f_support.invokeNeg(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("next");
        return chain == null
            ? f_support.invokeNext(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("prev");
        return chain == null
            ? f_support.invokePrev(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }


    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("preInc");
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postInc");
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("preDec");
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postDec");
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("+=");
        return chain == null
            ? ensureVarSupport().invokeVarAdd(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("-=");
        return chain == null
            ? ensureVarSupport().invokeVarSub(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("*=");
        return chain == null
            ? ensureVarSupport().invokeVarMul(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("/=");
        return chain == null
            ? ensureVarSupport().invokeVarDiv(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("%=");
        return chain == null
            ? ensureVarSupport().invokeVarMod(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Frame.RET_UNUSED);
        }


    // ----- helpers--------------------------------------------------------------------------------

    /**
     * @return a call chain for the specified op or null if non exists
     */
    protected CallChain getOpChain(String sOp)
        {
        TypeInfo info = f_annotation.getAnnotationType().ensureTypeInfo();
        // TODO: use the TypeInfo to get the chain
        return null;
        }

    protected VarSupport ensureVarSupport()
        {
        try
            {
            return (VarSupport) f_support;
            }
        catch (ClassCastException e)
            {
            throw new IllegalStateException("Not a VarSupport: " + f_support);
            }
        }

    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The underlying {@link OpSupport} object.
     */
    private final OpSupport f_support;

    /**
     * The underlying annotation.
     */
    private final Annotation f_annotation;
    }
