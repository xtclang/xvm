package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Common base for CALL_ ops.
 */
public abstract class OpCallable extends Op {
    /**
     * Construct an op based on the passed argument.
     *
     * @param argFunction  the function Argument
     */
    protected OpCallable(Argument argFunction) {
        m_argFunction = argFunction;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpCallable(DataInput in, Constant[] aconst)
            throws IOException {
        m_nFunctionId = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argFunction != null) {
            m_nFunctionId = encodeArgument(m_argFunction, registry);
        }

        writePackedLong(out, m_nFunctionId);
    }

    @Override
    public boolean usesSuper() {
        return m_nFunctionId == A_SUPER;
    }

    /**
     * A "virtual constant" indicating whether or not this op has multiple return values.
     *
     * @return true iff the op has multiple return values.
     */
    protected boolean isMultiReturn() {
        return false;
    }

    @Override
    public void resetSimulation() {
        if (isMultiReturn()) {
            resetRegisters(m_aArgReturn);
        } else {
            resetRegister(m_argReturn);
        }
    }

    @Override
    public void simulate(Scope scope) {
        if (isMultiReturn()) {
            checkNextRegisters(scope, m_aArgReturn, m_anRetValue);
        } else {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argFunction = registerArgument(m_argFunction, registry);

        if (isMultiReturn()) {
            registerArguments(m_aArgReturn, registry);
        } else {
            m_argReturn = registerArgument(m_argReturn, registry);
        }
    }

    @Override
    public String toString() {
        return super.toString() + ' ' + getFunctionString() + '(' + getParamsString() + ") -> " + getReturnsString();
    }
    protected String getFunctionString() {
        return Argument.toIdString(m_argFunction, m_nFunctionId);
    }
    protected String getParamsString() {
        return "";
    }
    protected static String getParamsString(int[] anArgValue, Argument[] aArgValue) {
        StringBuilder sb = new StringBuilder();
        int cArgNums = anArgValue == null ? 0 : anArgValue.length;
        int cArgRefs = aArgValue == null ? 0 : aArgValue.length;
        for (int i = 0, c = Math.max(cArgNums, cArgRefs); i < c; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Argument.toIdString(i < cArgRefs ? aArgValue[i] : null,
                    i < cArgNums ? anArgValue[i] : Register.UNKNOWN));
        }
        return sb.toString();
    }
    protected String getReturnsString() {
        if (m_anRetValue != null || m_aArgReturn != null) {
            // multi-return
            StringBuilder sb = new StringBuilder();
            int cArgNums = m_anRetValue == null ? 0 : m_anRetValue.length;
            int cArgRefs = m_aArgReturn == null ? 0 : m_aArgReturn.length;
            for (int i = 0, c = Math.max(cArgNums, cArgRefs); i < c; ++i) {
                sb.append(i == 0 ? "(" : ", ")
                        .append(Argument.toIdString(i < cArgRefs ? m_aArgReturn[i] : null,
                                i < cArgNums ? m_anRetValue[i] : Register.UNKNOWN));
            }
            return sb.append(')').toString();
        }

        if (m_nRetValue != A_IGNORE || m_argReturn != null) {
            return Argument.toIdString(m_argReturn, m_nRetValue);
        }

        return "void";
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * This Op holds a constant for the constructor of a child of the compile-time parent.
     * The run-time type of the parent could extend the compile-time type and that parent
     * may have a corresponding child extension.
     *
     * @return a child constructor for the specified parent; null if it cannot be found
     */
    protected MethodStructure getChildConstructor(Frame frame, ObjectHandle hParent) {
        // suffix "C" indicates the compile-time constants; "R" - the run-time
        IdentityConstant idParentR   = hParent.getTemplate().getClassConstant();
        ServiceContext   context     = frame.f_context;
        MethodStructure  constructor = (MethodStructure) context.getOpInfo(this, Category.Constructor);
        if (constructor != null) {
            IdentityConstant idParent = (IdentityConstant) context.getOpInfo(this, Category.TargetClass);
            if (idParent.equals(idParentR)) {
                // cached constructor fits the parent's class
                return constructor;
            }
        }

        constructor = getMethodStructure(frame);
        if (constructor == null) {
            return null;
        }

        ClassStructure clzTargetC = (ClassStructure) constructor.getParent().getParent();
        if (clzTargetC.isVirtualChild()) {
            ClassStructure clzParentC = (ClassStructure) clzTargetC.getParent();

            // if the parent is an annotation part of the virtual child type - no virtualization
            if (clzParentC.getFormat() != Format.ANNOTATION &&
                    !idParentR.equals(clzParentC.getIdentityConstant())) {
                // find the run-time target's constructor;
                // note that we don't need to resolve the actual types
                ClassStructure clzParentR  = (ClassStructure) idParentR.getComponent();
                ClassStructure clzChild    = clzParentR.getVirtualChild(clzTargetC.getSimpleName());
                if (clzChild == null) {
                    return null;
                }
                TypeInfo infoTarget = clzChild.getFormalType().
                        ensureAccess(Access.PROTECTED).ensureTypeInfo();
                MethodInfo infoConstr = infoTarget.getMethodBySignature(
                        constructor.getIdentityConstant().getSignature(), true);
                if (infoConstr == null) {
                    return null;
                }
                constructor = infoConstr.getTopmostMethodStructure(infoTarget);
            }
        }

        context.setOpInfo(this, Category.TargetClass, idParentR);
        context.setOpInfo(this, Category.Constructor, constructor);
        return constructor;
    }

    /**
     * This Op holds a constant for the constructor of the compile-time target.
     * The run-time type of the target could extend the compile-time type and that target
     * must have the corresponding constructor
     *
     * @return a constructor for the specified type
     */
    protected MethodStructure getTypeConstructor(Frame frame, TypeHandle hType) {
        TypeConstant     typeR       = hType.getDataType();
        IdentityConstant idTargetR   = typeR.getSingleUnderlyingClass(false);
        ServiceContext   context     = frame.f_context;
        MethodStructure  constructor = (MethodStructure) context.getOpInfo(this, Category.Constructor);
        if (constructor != null) {
            IdentityConstant idTarget = (IdentityConstant) context.getOpInfo(this, Category.TargetClass);
            if (idTarget.equals(idTargetR)) {
                // cached constructor fits the parent's class
                return constructor;
            }
        }

        constructor = getMethodStructure(frame);
        if (constructor == null) {
            return null;
        }

        ClassStructure   clzTargetC = (ClassStructure) constructor.getParent().getParent();
        IdentityConstant idTargetC  = clzTargetC.getIdentityConstant();

        if (!idTargetR.equals(idTargetC)) {
            TypeInfo infoTarget = typeR.ensureTypeInfo();

            MethodInfo info = infoTarget.getMethodBySignature(
                                constructor.getIdentityConstant().getSignature(), true);
            if (info == null) {
                return null;
            }
            constructor = info.getTopmostMethodStructure(infoTarget);
        }

        context.setOpInfo(this, Category.TargetClass, idTargetR);
        context.setOpInfo(this, Category.Constructor, constructor);
        return constructor;
    }

    /**
     * @return R_EXCEPTION
     */
    protected int reportMissingConstructor(Frame frame, ObjectHandle hParent) {
        MethodStructure constructor = getMethodStructure(frame);
        if (constructor == null) {
            // getMethodStructure() must've already created an exception
            return R_EXCEPTION;
        }

        IdentityConstant idParent = hParent instanceof TypeHandle
                ? ((TypeHandle) hParent).getDataType().getSingleUnderlyingClass(false)
                : hParent.getType().getSingleUnderlyingClass(false);

        return frame.raiseException(
                "Missing constructor \"" + constructor.getIdentityConstant().getPathString() +
                "\" at class " + idParent.getValueString());
    }

    /**
     * @return R_EXCEPTION
     */
    protected int reportNonExtendable(Frame frame, MethodStructure constructor) {
        return frame.raiseException(xException.unsupported(frame,
            "Class \"" + constructor.getContainingClass().getIdentityConstant().getPathString() +
            "\" is not extendable"));
    }

    /**
     * Retrieve the constructor to be used by this Construct_* op code.
     *
     * @return the method structure or null if cannot be found, in which case an exception
     *         has been raised on the frame
     */
    protected MethodStructure getConstructor(Frame frame) {
        assert frame.f_function.isConstructor();

        ServiceContext   context     = frame.f_context;
        MethodStructure  constructor = (MethodStructure) context.getOpInfo(this, Category.Function);
        IdentityConstant idPrev      = (IdentityConstant) context.getOpInfo(this, Category.TargetClass);
        IdentityConstant idThis      = frame.getThis().getTemplate().getClassConstant();

        if (constructor != null && idPrev.equals(idThis)) {
            return constructor;
        }

        ConstantPool        pool       = frame.poolContext();
        GenericTypeResolver resolver   = frame.getGenericsResolver(false);
        MethodConstant      idCtor     = (MethodConstant) frame.getConstant(m_nFunctionId);
        IdentityConstant    idTarget   = idCtor.getNamespace();
        TypeConstant        typeTarget = idTarget.getFormalType().resolveGenerics(pool, resolver);

        Virtual:
        if (typeTarget.isVirtualChild()) {
            TypeConstant typeThis = frame.getThis().getType();
            if (!typeThis.isVirtualChild()) {
                frame.raiseException("Not a virtual child: \"" + typeTarget.getValueString() + '"');
                return null;
            }

            String sNameCurrent = frame.f_function.getIdentityConstant().getNamespace().getName();
            String sNameTarget  = idTarget.getName();
            if (sNameCurrent.equals(sNameTarget)) {
                break Virtual;
            }

            TypeConstant typeVirtTarget =
                    typeTarget.ensureVirtualParent(typeThis.getParentType(), true);
            if (typeVirtTarget == typeTarget) {
                break Virtual;
            }

            constructor = pool.ensureAccessTypeConstant(typeVirtTarget, Access.PROTECTED).
                    findCallable(idCtor.getSignature().resolveGenericTypes(pool, resolver));
        }

        if (constructor == null) {
            constructor = (MethodStructure) idCtor.getComponent();
            if (constructor == null) {
                constructor = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE).
                    findCallable(idCtor.getSignature().resolveGenericTypes(pool, resolver));
            }

            if (constructor == null) {
                frame.raiseException("Unresolvable constructor \"" +
                    idCtor.getValueString() + "\" for " + typeTarget.getValueString());
                return null;
            }
        }

        context.setOpInfo(this, Category.Function, constructor);
        context.setOpInfo(this, Category.TargetClass, idThis);

        return constructor;
    }

    /**
     * Retrieve the method structure for this op-code and cache the parent's template
     * to be used by {@link #getNativeTemplate}.
     *
     * @return the method structure or null if cannot be found, in which case an exception
     *         has been raised on the frame
     */
    protected MethodStructure getMethodStructure(Frame frame) {
        ServiceContext   context    = frame.f_context;
        MethodConstant   idFunction = (MethodConstant) frame.getConstant(m_nFunctionId);
        MethodStructure  function   = (MethodStructure) context.getOpInfo(this, Category.Function);
        IdentityConstant idTarget   = idFunction.getNamespace();

        switch (idTarget.getFormat()) {
        case Module:
        case Package:
        case Class: {
            if (function == null) {
                ConstantPool        pool     = frame.poolContext();
                GenericTypeResolver resolver = frame.getGenericsResolver(false);

                TypeConstant typeTarget = idTarget.getFormalType().resolveGenerics(pool, resolver);

                function = (MethodStructure) idFunction.getComponent();
                if (function == null) {
                    function = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE).
                        findCallable(idFunction.getSignature().resolveGenericTypes(pool, resolver));
                }

                if (function == null) {
                    frame.raiseException("Unresolvable or ambiguous function \"" +
                        idFunction.getValueString() + "\" for " + typeTarget.getValueString());
                    return null;
                }

                context.setOpInfo(this, Category.Function, function);
                context.setOpInfo(this, Category.Template,
                        context.f_container.getTemplate(typeTarget));
            }
            break;
        }

        case FormalTypeChild:
        case Property:
        case TypeParameter:
        case DynamicFormal: {
            GenericTypeResolver resolver   = frame.getGenericsResolver(true);
            TypeConstant        typeTarget = ((FormalConstant) idTarget).resolve(resolver);
            TypeConstant        typePrev   = (TypeConstant) context.getOpInfo(this, Category.TargetType);
            if (function == null || !typeTarget.equals(typePrev)) {
                function = typeTarget.findCallable(idFunction.getSignature());
                if (function == null) {
                    frame.raiseException("Unresolvable or ambiguous function \"" +
                        idFunction.getValueString() + "\" for " + typeTarget.getValueString());
                    return null;
                }

                context.setOpInfo(this, Category.Function, function);
                context.setOpInfo(this, Category.TargetType, typeTarget);
                context.setOpInfo(this, Category.Template,
                    typeTarget.isSingleDefiningConstant()
                        ? context.f_container.getTemplate(typeTarget)
                        : context.f_container.getTemplate(
                                function.getContainingClass().getIdentityConstant()));
            }
            break;
        }

        case Method: {
            if (function == null) {
                function = (MethodStructure) idFunction.getComponent();
                assert !function.isNative();

                // since the function is never native, no need to save the template
                context.setOpInfo(this, Category.Function, function);
            }
            break;
        }

        default:
            throw new IllegalStateException();
        }

        return function;
    }

    /**
     * @return the ClassTemplate that defines a native implementation for the specified function
     *         using the information collected by {@link #getMethodStructure}
     */
    protected ClassTemplate getNativeTemplate(Frame frame, MethodStructure function) {
        assert function == frame.f_context.getOpInfo(this, Category.Function);
        return (ClassTemplate) frame.f_context.getOpInfo(this, Category.Template);
    }

    /**
     * Call a constructor for the virtual or inner child class.
     */
    protected int constructChild(Frame frame, MethodStructure constructor,
                                 ObjectHandle hParent, TypeConstant typeChild, ObjectHandle[] ahVar) {
        ConstantPool    pool        = frame.poolContext();
        ClassStructure  structChild = (ClassStructure) constructor.getParent().getParent();
        TypeConstant    typeParent  = hParent.getComposition().getInceptionType().removeAccess();
        TypeConstant    typeTarget;

        if (structChild.isVirtualChild()) {
            typeTarget = pool.ensureVirtualChildTypeConstant(typeParent, structChild.getName());
            if (typeChild != null) {
                // transfer the type parameters
                if (typeChild.isParamsSpecified()) {
                    typeTarget = pool.ensureParameterizedTypeConstant(typeTarget,
                                        typeChild.getParamTypesArray());
                }

                // transfer the annotations
                if (typeChild.isAnnotated()) {
                    typeTarget = typeTarget.adoptAnnotations(pool, typeChild);
                }
            }
        } else if (typeChild == null) {
            typeTarget = structChild.isInnerChild()
                    ? pool.ensureInnerChildTypeConstant(typeParent,
                        (ClassConstant) structChild.getIdentityConstant())
                    : structChild.getCanonicalType();
        } else {
            typeTarget = typeChild;
        }

        TypeComposition clzTarget = typeTarget.ensureClass(frame);
        if (frame.isNextRegister(m_nRetValue)) {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
        }

        return clzTarget.getTemplate().construct(
                frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);
    }

    protected int constructChild(Frame frame, MethodStructure constructor,
                                 ObjectHandle hParent, ObjectHandle[] ahVar) {
        return constructChild(frame, constructor, hParent, null, ahVar);
    }

    /**
     * Allocate a register for the return value if necessary.
     */
    protected void checkReturnRegister(Frame frame, MethodStructure method) {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue)) {
            int nMethodId = m_nFunctionId;
            if (nMethodId == Op.A_SUPER) {
                // the position should refer to the frame's context pool
                nMethodId = frame.poolContext().getConstant(
                                method.getIdentityConstant()).getPosition();
            }
            frame.introduceMethodReturnVar(m_nRetValue, nMethodId, 0);
        }
    }

    /**
     * Allocate a register for the return Tuple value if necessary.
     */
    protected void checkReturnTupleRegister(Frame frame, MethodStructure method) {
        assert !isMultiReturn();

        if (frame.isNextRegister(m_nRetValue)) {
            int nMethodId = m_nFunctionId;
            if (nMethodId == Op.A_SUPER) {
                nMethodId = frame.poolContext().getConstant(
                                method.getIdentityConstant()).getPosition();
            }
            frame.introduceMethodReturnVar(m_nRetValue, nMethodId, 0);
        }
    }

    /**
     * Allocate registers for the return values if necessary.
     */
    protected void checkReturnRegisters(Frame frame, MethodStructure method) {
        assert isMultiReturn();

        int nMethodId = m_nFunctionId;

        int[] anRet = m_anRetValue;
        for (int i = 0, c = anRet.length; i < c; i++) {
            if (frame.isNextRegister(anRet[i])) {
                if (nMethodId == Op.A_SUPER) {
                    frame.introduceResolvedVar(anRet[i],
                        frame.resolveType(method.getReturn(i).getType()));
                } else {
                    frame.introduceMethodReturnVar(anRet[i], nMethodId, i);
                }
            }
        }
    }

    // ----- JIT support ---------------------------------------------------------------------------

    /**
     * Support for CALL_ ops.
     */
    protected void buildCall(BuildContext bctx, CodeBuilder code, int[] anArgValue) {
        TypeSystem ts = bctx.typeSystem;

        ClassDesc      cdTarget;
        String         sJitName;
        JitMethodDesc  jmdCall;
        int            cReturns;
        boolean        fSpecial;

        if (m_nFunctionId == A_SUPER) {
            MethodBody bodyHead  = bctx.callChain[0];
            int        nDepth    = bctx.callDepth + 1;
            MethodBody bodySuper = bctx.callChain[nDepth];

            MethodConstant   idSuper  = bodySuper.getIdentity();
            IdentityConstant idCallee = idSuper.getNamespace();
            Format           format   = idCallee.getComponent().getFormat();

            if (format == Format.MIXIN) {
                // we need to generate a synthetic super
                cdTarget = ClassDesc.of(bctx.className);
                sJitName = bodyHead.getIdentity().ensureJitMethodName(ts) + "ꖛ" + nDepth;

                bctx.buildSuper(sJitName, nDepth);
            } else {
                cdTarget = idCallee.ensureClassDesc(ts);
                sJitName = idSuper.ensureJitMethodName(ts);
            }
            jmdCall  = bodySuper.getJitDesc(ts, bctx.typeInfo.getType());
            cReturns = idSuper.getSignature().getReturnCount();
            fSpecial = true;
            code.aload(0); // super() can only be on "this"
        } else if (m_nFunctionId <= CONSTANT_OFFSET) {
            MethodConstant   idMethod   = (MethodConstant) bctx.getConstant(m_nFunctionId);
            IdentityConstant idTarget   = idMethod.getClassIdentity();
            TypeConstant     typeTarget = idTarget.getType();
            MethodInfo       infoMethod = typeTarget.ensureTypeInfo().getMethodById(idMethod);

            cdTarget = idTarget.ensureClassDesc(ts); // function; no formal types applicable
            sJitName = idMethod.ensureJitMethodName(ts);
            jmdCall  = infoMethod.getJitDesc(ts, typeTarget);
            cReturns = idMethod.getSignature().getReturnCount();
            fSpecial = false;
        } else {
            RegisterInfo regFn = bctx.getRegisterInfo(m_nFunctionId);
            // TODO: call "invoke(Ctx ctx, Tuple args)"
            throw new UnsupportedOperationException("function call " + regFn.type());
        }

        MethodTypeDesc mdCall;
        if (jmdCall.isOptimized) {
            mdCall  = jmdCall.optimizedMD;
            sJitName += Builder.OPT;
        }
        else {
            mdCall = jmdCall.standardMD;
        }

        bctx.loadCtx(code);
        bctx.loadArguments(code, jmdCall, anArgValue);

        if (fSpecial) {
            code.invokespecial(cdTarget, sJitName, mdCall);
        } else {
            code.invokestatic(cdTarget, sJitName, mdCall);
        }

        if (cReturns > 0) {
            int[] anVar = isMultiReturn() ? m_anRetValue : new int[] {m_nRetValue};
            bctx.assignReturns(code, jmdCall, cReturns, anVar);
        }
    }

    /**
     * Support for NEW_ ops.
     */
    protected void buildNew(BuildContext bctx, CodeBuilder code, int[] anArgValue) {
        MethodConstant idCtor     = (MethodConstant) bctx.getConstant(m_nFunctionId);
        TypeConstant   typeTarget = idCtor.getNamespace().getType();
        ClassDesc      cdTarget   = bctx.buildNew(code, typeTarget, idCtor, anArgValue);

        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, typeTarget, cdTarget, ""));
    }

    /**
     * Support for NEW_G ops.
     */
    protected void buildNewG(BuildContext bctx, CodeBuilder code, int nTypeArg, int[] anArgValue) {
        TypeConstant typeTarget;
        if (nTypeArg <= CONSTANT_OFFSET) {
            typeTarget = bctx.getType(nTypeArg);
            if (typeTarget.containsFormalType(true)) {
                // resolve the type against "this.$type()"
                throw new UnsupportedOperationException("ToDo resolve formal type");
            }
        } else {
            assert nTypeArg >= 0;
            RegisterInfo regXType = bctx.loadArgument(code, nTypeArg);
            assert regXType.type().isTypeOfType();
            throw new UnsupportedOperationException("ToDo dynamic type");
        }

        MethodConstant idCtor   = (MethodConstant) bctx.getConstant(m_nFunctionId);
        ClassDesc      cdTarget = bctx.buildNew(code, typeTarget, idCtor, anArgValue);

        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, typeTarget, cdTarget, ""));
    }

    /**
     * Support for CONSTR_ ops.
     */
    protected void buildConstruct(BuildContext bctx, CodeBuilder code, int[] anArgValue) {
        TypeSystem       ts         = bctx.typeSystem;
        MethodConstant   idCtor     = (MethodConstant) bctx.getConstant(m_nFunctionId);
        IdentityConstant idTarget   = idCtor.getNamespace();
        TypeConstant     typeTarget = idTarget.getType();
        TypeInfo         infoTarget = typeTarget.ensureAccess(Access.PRIVATE).ensureTypeInfo();
        MethodInfo       infoCtor   = infoTarget.getMethodById(idCtor);

        if (infoCtor == null) {
            throw new RuntimeException("Unresolvable constructor \"" +
                idCtor.getValueString() + "\" for " + typeTarget.getValueString());
        }

        ClassDesc cdTarget;
        String    sJitCtor;
        if (infoTarget.getFormat() == Format.MIXIN) {
            cdTarget = ClassDesc.of(bctx.className);
            sJitCtor = idTarget.getName() + "$" + idCtor.ensureJitMethodName(ts);

            bctx.buildFunction(sJitCtor, infoCtor.getHead());
        } else {
            cdTarget = typeTarget.ensureClassDesc(ts);
            sJitCtor = idCtor.ensureJitMethodName(ts);
        }

        JitMethodDesc jmdCtor = infoCtor.getJitDesc(ts, typeTarget);

        boolean fOptimized = jmdCtor.isOptimized;
        MethodTypeDesc md;
        if (fOptimized) {
            md       = jmdCtor.optimizedMD;
            sJitCtor += Builder.OPT;
        }
        else {
            md = jmdCtor.standardMD;
        }

        bctx.loadCtx(code);
        bctx.loadCtorCtx(code);
        bctx.loadThis(code);
        bctx.loadArguments(code, jmdCtor, anArgValue);
        code.invokestatic(cdTarget, sJitCtor, md);
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int   m_nFunctionId;
    protected int   m_nRetValue = A_IGNORE;
    protected int[] m_anRetValue;

    protected Argument   m_argFunction;
    protected Argument   m_argReturn;  // optional
    protected Argument[] m_aArgReturn; // optional

    // categories for cached info
    enum Category {Function, Template, TargetClass, TargetType, Constructor}
}