package org.xvm.runtime;

import org.xvm.asm.Annotation;
import org.xvm.asm.constants.TypeInfo;

/**
 * An OpSupport implementation for annotated types.
 */
public class AnnotationSupport
        implements OpSupport
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
