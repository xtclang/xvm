package org.xvm.runtime;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.template.xRefImpl;
import org.xvm.runtime.template.xRefImpl.RefHandle;
import org.xvm.runtime.template.xVarImpl;

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

        // if the annotation itself is native, it overrides the base type template (support)
        if (supportAnno instanceof xRefImpl) // TODO: isNative()?
            {
            f_support = supportAnno.getTemplate(typeBase);
            f_fNative = true;
            }
        else if (typeBase.isSingleDefiningConstant())
            {
            Constant constIdBase = typeBase.getDefiningConstant();

            ConstantPool pool = typeBase.getConstantPool();
            if (constIdBase.equals(pool.clzVar()))
                {
                f_support = xVarImpl.INSTANCE;
                f_fNative = true;
                }
            else if (constIdBase.equals(pool.clzRef()))
                {
                f_support = xRefImpl.INSTANCE;
                f_fNative = true;
                }
            else
                {
                f_support = typeBase.getOpSupport(registry);
                f_fNative = false;
                }
            }
        else
            {
            f_support = typeBase.getOpSupport(registry);
            f_fNative = false;
            }

        f_typeAnno = type.getAnnotation().getAnnotationType();
        f_registry = registry;
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
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return ensureVarSupport().createRefHandle(clazz, sName);
        }

    @Override
    public int get(Frame frame, RefHandle hTarget, int iReturn)
        {
        CallChain chain = getOpChain("get");
        return chain == null
            ? ensureVarSupport().get(frame, hTarget, iReturn)
            : chain.invoke(frame, hTarget, iReturn);
        }

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
    public int set(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        CallChain chain = getOpChain("set");
        return chain == null
            ? ensureVarSupport().set(frame, hTarget, hValue)
            : chain.invoke(frame, hTarget, hValue, Frame.RET_UNUSED);
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
        if (!f_fNative)
            {
            TypeInfo info = f_typeAnno.ensureTypeInfo();
            // TODO: use the TypeInfo to get the chain

            // temporary very silly implementation
            for (MethodConstant constMethod: info.getMethods().keySet())
                {
                if (constMethod.getName().equals(sOp))
                    {
                    TypeComposition clzAnno = f_registry.resolveClass(f_typeAnno);
                    CallChain chain = clzAnno.
                        getMethodCallChain(constMethod.getSignature(), Constants.Access.PUBLIC);
                    assert !chain.isNative();
                    return chain;
                    }
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
    private final TypeConstant f_typeAnno;

    /**
     * The template registry.
     */
    private final TemplateRegistry f_registry;
    }
