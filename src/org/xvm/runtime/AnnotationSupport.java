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
            : invokeNatural1(frame, chain, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("-");
        return chain == null
            ? f_support.invokeSub(frame, hTarget, hArg, iReturn)
            : invokeNatural1(frame, chain, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("*");
        return chain == null
            ? f_support.invokeMul(frame, hTarget, hArg, iReturn)
            : invokeNatural1(frame, chain, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("/");
        return chain == null
            ? f_support.invokeDiv(frame, hTarget, hArg, iReturn)
            : invokeNatural1(frame, chain, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("%");
        return chain == null
            ? f_support.invokeMod(frame, hTarget, hArg, iReturn)
            : invokeNatural1(frame, chain, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("neg");
        return chain == null
            ? f_support.invokeNeg(frame, hTarget, iReturn)
            : invokeNatural0(frame, chain, hTarget, iReturn);
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

    // natural invocation with zero args
    protected int invokeNatural0(Frame frame, CallChain chain, ObjectHandle hTarget, int iReturn)
        {
        assert !chain.isNative();

        int cVars = chain.getTop().getMaxVars();
        ObjectHandle[] ahVar = new ObjectHandle[cVars];

        return getTemplate().invoke1(frame, chain, hTarget, ahVar, iReturn);
        }

    // natural invocation with one arg
    protected int invokeNatural1(Frame frame, CallChain chain, ObjectHandle hTarget,
                                 ObjectHandle hArg, int iReturn)
        {
        assert !chain.isNative();

        int cVars = chain.getTop().getMaxVars();
        ObjectHandle[] ahVar = new ObjectHandle[cVars];
        ahVar[0] = hArg;

        return getTemplate().invoke1(frame, chain, hTarget, ahVar, iReturn);
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
