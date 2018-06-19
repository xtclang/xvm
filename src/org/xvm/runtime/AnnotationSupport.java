package org.xvm.runtime;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xRef;
import org.xvm.runtime.template.xRef.RefHandle;

/**
 * An OpSupport and VarSupport implementation for annotated types.
 */
public class AnnotationSupport
        implements OpSupport, VarSupport
    {
    public AnnotationSupport(AnnotatedTypeConstant type, TemplateRegistry registry)
        {
        TypeConstant typeBase = type.getUnderlyingType();

        IdentityConstant constIdAnno = (IdentityConstant) type.getAnnotation().getAnnotationClass();
        OpSupport supportAnno = registry.getTemplate(constIdAnno);

        // if the annotation itself is native, it overrides the base type template (support);
        // for now all native Ref implementations extend xRef
        if (supportAnno instanceof xRef)
            {
            f_support = supportAnno.getTemplate(typeBase);
            f_fNative = true;
            }

        else
            {
            f_support = typeBase.getOpSupport(registry);
            f_fNative = false;
            }

        if (!f_fNative)
            {
            ConstantPool pool = type.getConstantPool();
            TypeConstant typeAnno = type.getAnnotationType();
            TypeComposition clzAnno = registry.resolveClass(typeAnno);

            TypeInfo info = typeAnno.ensureTypeInfo();

            TypeConstant[] atypeRef =
                new TypeConstant[]{info.getTypeParams().get("RefType").getActualType()};

            m_sigGet = new SignatureConstant(pool, "get", ConstantPool.NO_TYPES, atypeRef);
            m_sigSet = new SignatureConstant(pool, "set", atypeRef, ConstantPool.NO_TYPES);

            m_typeAnno = typeAnno;
            m_clzAnno = clzAnno;
            }
        }

    // ----- OpSupport implementation --------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return f_support.getTemplate(type);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("+", 2);
        return chain == null
            ? f_support.invokeAdd(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("-", 2);
        return chain == null
            ? f_support.invokeSub(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("*", 2);
        return chain == null
            ? f_support.invokeMul(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("/", 2);
        return chain == null
            ? f_support.invokeDiv(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("%", 2);
        return chain == null
            ? f_support.invokeMod(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("<<", 2);
        return chain == null
            ? f_support.invokeShl(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain(">>", 2);
        return chain == null
            ? f_support.invokeShr(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain(">>>", 2);
        return chain == null
            ? f_support.invokeShrAll(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("&", 2);
        return chain == null
            ? f_support.invokeAnd(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("|", 2);
        return chain == null
            ? f_support.invokeOr(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("^", 2);
        return chain == null
            ? f_support.invokeXor(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDivMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        CallChain chain = getOpChain("/%", 2);
        return chain == null
            ? f_support.invokeDivMod(frame, hTarget, hArg, aiReturn)
            : chain.invoke(frame, hTarget, hArg, aiReturn);
        }

    @Override
    public int invokeDotDot(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = getOpChain("..", 2);
        return chain == null
            ? f_support.invokeDotDot(frame, hTarget, hArg, iReturn)
            : chain.invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("!", 1);
        return chain == null
            ? f_support.invokeNeg(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("~", 1);
        return chain == null
            ? f_support.invokeCompl(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("next", 1);
        return chain == null
            ? f_support.invokeNext(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("prev", 1);
        return chain == null
            ? f_support.invokePrev(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }


    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return ensureVarSupport().createRefHandle(clazz, sName);
        }

    @Override
    public int get(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getGetChain();
        return chain == null
            ? ensureVarSupport().get(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("preInc", 1);
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postInc", 1);
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("preDec", 1);
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("postDec", 1);
        return chain == null
            ? ensureVarSupport().invokeVarPreInc(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

    @Override
    public int set(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        CallChain chain = getSetChain();
        return chain == null
            ? ensureVarSupport().set(frame, hTarget, hValue)
            : chain.invoke(frame, hTarget, hValue, Op.A_IGNORE);
        }

    @Override
    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("+=", 1);
        return chain == null
            ? ensureVarSupport().invokeVarAdd(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("-=", 1);
        return chain == null
            ? ensureVarSupport().invokeVarSub(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("*=", 1);
        return chain == null
            ? ensureVarSupport().invokeVarMul(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("/=", 1);
        return chain == null
            ? ensureVarSupport().invokeVarDiv(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getOpChain("%=", 1);
        return chain == null
            ? ensureVarSupport().invokeVarMod(frame, hTarget, hArg)
            : chain.invoke(frame, hTarget, hArg, Op.A_IGNORE);
        }


    // ----- helpers--------------------------------------------------------------------------------

    /**
     * @return a call chain for the specified op or null if non exists
     */
    protected CallChain getOpChain(String sOp, int cArgs)
        {
        if (!f_fNative)
            {
            TypeInfo info = m_typeAnno.ensureTypeInfo();

            // TODO: what if there is more than one valid method?

            for (MethodConstant constMethod: info.findOpMethods(sOp, sOp, cArgs))
                {
                CallChain chain = m_clzAnno.getMethodCallChain(constMethod.getSignature());
                if (chain.getDepth() > 0)
                    {
                    return chain;
                    }
                }
            }
        return null;
        }

    /**
     * @return a call chain for the "get" method
     */
    protected CallChain getGetChain()
        {
        if (!f_fNative)
            {
            CallChain chain = m_clzAnno.getMethodCallChain(m_sigGet);
            if (chain.isExplicit())
                {
                return chain;
                }
            }
        return null;
        }

    /**
     * @return a call chain for the "set" method
     */
    protected CallChain getSetChain()
        {
        if (!f_fNative)
            {
            CallChain chain = m_clzAnno.getMethodCallChain(m_sigSet);
            if (chain.isExplicit())
                {
                return chain;
                }
            }
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
     * Indicates that the annotation support is native and all calls should be routed directly to it.
     */
    private final boolean f_fNative;

    /**
     * The underlying annotated type.
     */
    private TypeConstant m_typeAnno;

    /**
     * The underlying annotated class.
     */
    private TypeComposition m_clzAnno;

    /**
     * Cached "get" signature.
     */
    private SignatureConstant m_sigGet;

    /**
     * Cached "set" signature.
     */
    private SignatureConstant m_sigSet;
    }
