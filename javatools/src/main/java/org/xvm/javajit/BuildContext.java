package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.FinallyStart;
import org.xvm.asm.op.Guarded;

import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Exception;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_xException;
import static org.xvm.javajit.Builder.EXT;
import static org.xvm.javajit.Builder.N_TypeMismatch;

import static org.xvm.javajit.JitFlavor.MultiSlotPrimitive;

/**
 * Whatever is necessary for the Ops compilation.
 */
public class BuildContext {
    /**
     * Construct {@link BuildContext} for a method.
     */
    public BuildContext(Builder builder, TypeInfo typeInfo, MethodInfo methodInfo) {
        this.builder       = builder;
        this.typeSystem    = builder.typeSystem;
        this.typeInfo      = typeInfo;
        this.callChain     = methodInfo.getChain();
        this.methodStruct  = callChain[0].getMethodStructure();
        this.jmd           = methodInfo.getJitDesc(typeSystem, typeInfo.getType());
        this.isFunction    = methodInfo.isFunction();
        this.isConstructor = methodInfo.isConstructor();
        this.isOptimized   = jmd.optimizedMD != null;
    }

    /**
     * Construct {@link BuildContext} for a property accessor.
     */
    public BuildContext(Builder builder, TypeInfo typeInfo, PropertyInfo propInfo,
                        boolean isGetter) {
        this.builder       = builder;
        this.typeSystem    = builder.typeSystem;
        this.typeInfo      = typeInfo;
        this.callChain     = isGetter
                ? propInfo.ensureOptimizedGetChain(typeInfo, null)
                : propInfo.ensureOptimizedSetChain(typeInfo, null);
        this.methodStruct  = callChain[0].getMethodStructure();
        this.jmd           = isGetter
                ? propInfo.getGetterJitDesc(typeSystem)
                : propInfo.getSetterJitDesc(typeSystem);
        this.isFunction    = propInfo.isConstant();
        this.isConstructor = false;
        this.isOptimized   = jmd.optimizedMD != null;
    }

    public final Builder         builder;
    public final TypeSystem      typeSystem;
    public final TypeInfo        typeInfo;
    public final MethodBody[]    callChain;
    public final MethodStructure methodStruct;
    public final JitMethodDesc   jmd;
    public final boolean         isOptimized;
    public final boolean         isFunction;
    public final boolean         isConstructor;

    /**
     * The map of {@link Slot}s indexed by the Var index.
     */
    public final Map<Integer, Slot> slots = new HashMap<>();

    /**
     * The current line number.
     */
    public int lineNumber;

    /**
     * The top index of the Java stack for this method.
     */
    public int maxLocal;

    /**
     * The current scope.
     */
    public Scope scope;

    /**
     * The Java slot past the last one used by the numbered XTC registers. The slots from this point
     * up are used for un-numbered XTC registers {@link Op#A_STACK}
     */
    private int tailSlot;

    /**
     * The stack of {@link Slot}s that are kept below the {@link #tailSlot}.
     */
    private final Deque<Slot> tailStack = new ArrayDeque<>();

    /**
     * The Map of not-yet-assigned slots.
     */
    private final Map<Slot, Label> unassignedSlots = new IdentityHashMap<>();

    /**
     * @return the ConstantPool used by this {@link BuildContext}.
     */
    public ConstantPool pool() {
        return typeSystem.pool();
    }

    /**
     * Get the constant for the specified argument index.
     */
    public Constant getConstant(int argId) {
        assert argId <= Op.CONSTANT_OFFSET;
        return methodStruct.getLocalConstants()[Op.CONSTANT_OFFSET - argId];
    }

    /**
     * Get the String value for the specified argument index.
     */
    public String getString(int argId) {
        return ((StringConstant) getConstant(argId)).getValue();
    }

    /**
     * Get the type for the specified argument index.
     */
    public TypeConstant getType(int argId) {
        return resolveType((TypeConstant) getConstant(argId)); // must exist
    }

    /**
     * Resolve the specified type.
     */
    public TypeConstant resolveType(TypeConstant type) {
        if (type.containsFormalType(true)) {
            // TODO: how to resolve?
            if (type.containsFormalType(true)) {
                // soft assertion
                System.err.println("ERROR: Unresolved type " + type);
            }
        }

        if (!isFunction && type.containsAutoNarrowing(true)) {
            // TODO: how to resolve?
        }

        return type;
        }

    /**
     * Prepare the compilation.
     */
    public void enterMethod(CodeBuilder code, int synthSlots) {
        lineNumber = 1; // XVM ops are 0 based; Java is 1-based
        scope      = new Scope(this, code);
        code
            .lineNumber(methodStruct.getSourceLineNumber() + 1)
            .labelBinding(scope.startLabel)
            .localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, scope.startLabel, scope.endLabel)
            ;

        int          extraArgs = jmd.getImplicitParamCount(); // account for $ctx, $cctx, thi$
        TypeConstant thisType  = typeInfo.getType();
        ClassDesc    CD_this   = thisType.ensureClassDesc(typeSystem);
        if (isConstructor) {
            slots.put(Op.A_THIS, new SingleSlot(extraArgs-1, thisType.ensureAccess(Access.STRUCT),
                CD_this, "thi$"));
        } else if (!isFunction) {
            slots.put(Op.A_THIS, new SingleSlot(0, thisType, CD_this, "this$"));
        }

        JitParamDesc[] params = isOptimized ? jmd.optimizedParams : jmd.standardParams;
        for (int i = 0, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = methodStruct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(extraArgs + i); // compensate for implicits

            code.localVariable(slot, name, paramDesc.cd, scope.startLabel, scope.endLabel);
            scope.topVar = Math.max(scope.topVar, varIndex + 1);

            switch (paramDesc.flavor) {
            case Specific, Widened, Primitive, SpecificWithDefault, WidenedWithDefault:
                slots.put(varIndex, new SingleSlot(slot, type, paramDesc.cd, name));
                break;

            case MultiSlotPrimitive, PrimitiveWithDefault:
                int extSlot = code.parameterSlot(extraArgs + i + 1);

                slots.put(varIndex,
                    new DoubleSlot(slot, extSlot, paramDesc.flavor, type, paramDesc.cd, name));
                i++; // already processed
                break;
            }
        }

        // compute the tailSlot; we don't mind to overshoot by assuming the worst case - two slots
        // per var plus ("this", "$ctx", ...) and synthetics
        tailSlot = (isFunction ? 0 : 1) + extraArgs + methodStruct.getMaxVars() * 2 + synthSlots;
    }

    /**
     * Finish the compilation.
     */
    public void exitMethod(CodeBuilder code) {
        code.labelBinding(scope.endLabel);
    }

    /**
     * @return the newly entered scope
     */
    public Scope enterScope(CodeBuilder code) {
        scope = scope.enter();
        code.labelBinding(scope.startLabel);
        return scope;
    }

    /**
     * @return the exited scope
     */
    public Scope exitScope(CodeBuilder code) {
        Scope prevScope = scope;
        scope = prevScope.exit();

        // clear up the old scope's entries
        slots.entrySet().removeIf(entry -> entry.getKey() >= scope.topVar);
        return prevScope;
    }

    /**
     * Ensure there is a Java label associated with the specified Op address.
     */
    public java.lang.classfile.Label ensureLabel(CodeBuilder code, int opAddress) {
        Op[] ops = methodStruct.getOps();
        Op   op  = ops[opAddress];
        if (op instanceof org.xvm.asm.op.Label label) {
            java.lang.classfile.Label javaLabel = label.getLabel();
            if (javaLabel == null) {
                label.setLabel(javaLabel = code.newLabel());
            }
            return javaLabel;
        }
        // replace the original op with a Label op
        java.lang.classfile.Label javaLabel = code.newLabel();
        org.xvm.asm.op.Label      xvmLabel  = new org.xvm.asm.op.Label(opAddress);

        xvmLabel.setLabel(javaLabel);
        xvmLabel.append(op);
        ops[opAddress] = xvmLabel;
        return javaLabel;
    }

    /**
     * Add a {@link Guarded} label to the specified {@link CatchStart} or {@link FinallyStart} op.
     */
    public void ensureGuarded(int opAddress) {
        Op[] ops = methodStruct.getOps();
        Op   op  = ops[opAddress];
        if (op instanceof Guarded) {
            throw new IllegalStateException("Already guarded");
        }

        if (op instanceof CatchStart catchOp) {
            Guarded xvmLabel = new Guarded(scope);
            xvmLabel.append(catchOp);
            ops[opAddress] = xvmLabel;
        }
        else {
            throw new IllegalStateException("Only CatchStart can be guarded");
        }
    }

    /**
     * @return ClassDesc for "super" class
     */
    public ClassDesc getSuperCD() {
        return typeInfo.getExtends().ensureClassDesc(typeSystem);
    }

    /**
     * Build the code to load the Ctx instance on the Java stack.
     */
    public CodeBuilder loadCtx(CodeBuilder code) {
        code.aload(code.parameterSlot(0));
        return code;
    }

    /**
     * Build the code to load the CtorCtx instance on the Java stack.
     */
    public CodeBuilder loadCtorCtx(CodeBuilder code) {
        assert isConstructor;
        code.aload(code.parameterSlot(1));
        return code;
    }

    /**
     * Build the code to load "this" instance on the Java stack.
     */
    public Slot loadThis(CodeBuilder code) {
        assert isConstructor || !isFunction;

        Slot slot = slots.get(Op.A_THIS);
        code.aload(slot.slot());
        return slot;
    }

    /**
     * Obtain the type of the specified argument.
     */
    public TypeConstant getArgumentType(int argId) {
        if (argId >= 0) {
            Slot slot = getSlot(argId);
            assert slot != null;
            return slot.type();
        }

        if (argId <= Op.CONSTANT_OFFSET) {
            TypeConstant type = getConstant(argId).getType();
            if (type.containsFormalType(true)) {
                type = type.resolveGenerics(pool(), typeInfo.getType());
            }
            return type;
        }

        return switch (argId) {
            case Op.A_STACK -> tailStack.peek().type();
            case Op.A_THIS  -> slots.get(Op.A_THIS).type();
            default -> throw new UnsupportedOperationException("id=" + argId);
        };
    }

    /**
     * Build the code to load an argument value on the Java stack.
     */
    public Slot loadArgument(CodeBuilder code, int argId) {
        if (argId >= 0) {
            Slot slot = getSlot(argId);
            assert slot != null;
            ClassDesc cd = slot.cd();
            if (slot instanceof DoubleSlot doubleSlot) {
                switch (doubleSlot.flavor) {
                case PrimitiveWithDefault:
                    Parameter parameter = methodStruct.getParam(argId);
                    assert parameter.hasDefaultValue();

                    Label ifTrue = code.newLabel();
                    Label endIf  = code.newLabel();

                    // if the extension slot is `true`, take the default value
                    code.iload(doubleSlot.extSlot)
                        .ifne(ifTrue)
                        .loadLocal(Builder.toTypeKind(cd), doubleSlot.slot)
                        .goto_(endIf)
                        .labelBinding(ifTrue);
                    builder.loadConstant(code, parameter.getDefaultValue());
                    code.labelBinding(endIf);
                    return new SingleSlot(Op.A_STACK, slot.type(), cd, slot.name());

                case MultiSlotPrimitive:
                    code.loadLocal(Builder.toTypeKind(cd), doubleSlot.slot);
                    code.loadLocal(TypeKind.BOOLEAN, doubleSlot.extSlot);
                    return slot;
                }
            }
            code.loadLocal(Builder.toTypeKind(cd), slot.slot());
            return slot;
        }
        return argId <= Op.CONSTANT_OFFSET
                ? loadConstant(code, argId)
                : loadPredefineArgument(code, argId);
    }

    /**
     * Build the code to load an argument value on the Java stack.
     *
     * @param targetDesc  the desired type description
     */
    public Slot loadArgument(CodeBuilder code, int argId, JitTypeDesc targetDesc) {
        Slot slot = loadArgument(code, argId);
        if (slot.cd().isPrimitive() && !targetDesc.cd.isPrimitive()) {
            if (slot instanceof DoubleSlot doubleSlot) {
                assert doubleSlot.flavor == MultiSlotPrimitive;
                // loadArgument() has already loaded the value and the boolean

                Label ifTrue = code.newLabel();
                Label endIf  = code.newLabel();

                code.ifne(ifTrue);
                builder.box(code, slot.type().removeNullable(), slot.cd());
                code.goto_(endIf)
                    .labelBinding(ifTrue);
                    Builder.pop(code, doubleSlot.cd);
                    Builder.loadNull(code);
                code.labelBinding(endIf);
                slot = new SingleSlot(Op.A_STACK, targetDesc.type, targetDesc.cd, slot.name() + "?");
            } else {
                builder.box(code, slot.type(), slot.cd());
                slot = new SingleSlot(Op.A_STACK, targetDesc.type, targetDesc.cd, slot.name());
            }
        }
        return slot;
    }

    /**
     * Build the code to load a value for a constant on the stack.
     *
     * We **always** load a primitive value if possible.
     */
    public Slot loadConstant(CodeBuilder code, int argId) {
        return loadConstant(code, getConstant(argId));
    }

    /**
     * Build the code to load a value for a constant on the stack.
     *
     * We **always** load a primitive value if possible.
     */
    public Slot loadConstant(CodeBuilder code, Constant constant) {
        return builder.loadConstant(code, constant);
    }

    /**
     * Build the code to load a value for a predefine constant on the Java stack.
     */
    public Slot loadPredefineArgument(CodeBuilder code, int argId) {
        switch (argId) {
        case Op.A_STACK:
            Slot slot = popSlot();
            loadValue(code, slot);
            return slot;

        case Op.A_THIS:
            return loadThis(code);

        default:
            throw new UnsupportedOperationException("id=" + argId);
        }
    }

    /**
     * Load one or two values at the specified slot onto the Java stack.
     */
    public void loadValue(CodeBuilder code, Slot slot) {
        if (slot.isIgnore()) {
            throw new IllegalStateException();
        }

        if (slot instanceof DoubleSlot doubleSlot) {
            code.iload(doubleSlot.extSlot()); // load the boolean flag
        }

        Builder.load(code, slot.cd(), slot.slot());
    }

    /**
     * Store one or two values at the Java stack into the specified slot.
     */
    public void storeValue(CodeBuilder code, Slot slot) {
        if (slot instanceof DoubleSlot doubleSlot) {
            code.istore(doubleSlot.extSlot()); // store the boolean flag
        }

        if (slot.isIgnore()) {
            Builder.pop(code, slot.cd());
        } else {
            Builder.store(code, slot.cd(), slot.slot());
        }

        Label varStart = unassignedSlots.remove(slot);
        if (varStart != null) {
            code.labelBinding(varStart);
        }
    }

    /**
     * Introduce a new variable for the specified type id, name id style and an optional value.
     *
     * @param varIndex  the variable index
     * @param typeId    a "relative" (negative) number representing the TypeConstant representing
     *                  the type
     * @param nameId    a "relative" (negative) number representing the StringConstant for the name
     *                  or zero for unnamed vars
     */
    public Slot introduceVar(CodeBuilder code, int varIndex, int typeId, int nameId) {
        TypeConstant type = (TypeConstant) getConstant(typeId);
        String       name = nameId == 0 ? "" : ((StringConstant) getConstant(nameId)).getValue();

        return introduceVar(code, varIndex, type, name);
    }

    /**
     * Introduce a new variable for the specified type and name.
     *
     * @param varIndex  the variable index
     * @param type      the variable type
     * @param name      the variable name
     */
    public Slot introduceVar(CodeBuilder code, int varIndex, TypeConstant type, String name) {
        if (varIndex < 0) {
            throw new IllegalArgumentException("Invalid var index: " + varIndex);
        }
        if (name.isEmpty()) {
            name = "v$" + varIndex;
        } else {
            name = name.replace('#', '$').replace('.', '$');
        }

        Label varStart = code.newLabel();

        ClassDesc cd;
        Slot      slot;
        if ((cd = JitTypeDesc.getMultiSlotPrimitiveClass(type)) != null) {
            int slotIndex = scope.allocateLocal(varIndex, Builder.toTypeKind(cd));
            int slotExt   = scope.allocateLocal(varIndex, TypeKind.BOOLEAN);

            code.localVariable(slotIndex, name, cd, varStart, scope.endLabel);
            code.localVariable(slotExt,   name+EXT, CD_boolean, varStart, scope.endLabel);

            slot = new DoubleSlot(slotIndex, slotExt, MultiSlotPrimitive, type, cd, name);
        } else {
            cd = JitParamDesc.getJitClass(typeSystem, type);

            int slotIndex = scope.allocateLocal(varIndex, Builder.toTypeKind(cd));
            code.localVariable(slotIndex, name, cd, varStart, scope.endLabel);

            slot = new SingleSlot(slotIndex, type, cd, name);
        }

        slots.put(varIndex, slot);
        unassignedSlots.put(slot, varStart);
        return slot;
    }

    /**
     * Build the code that creates a `Ref` object for the specified type and name and stores it in
     * the slot associated with the specified var index.
     */
    public Slot introduceRef(CodeBuilder code, String name, TypeConstant type, int varIndex) {
        if (type instanceof AnnotatedTypeConstant annoType) {
            type = type.getUnderlyingType();

            Annotation anno = annoType.getAnnotation();
            if (anno.getAnnotationClass().equals(pool().clzInject())) {
                TypeConstant resourceType = type.getParamType(0);
                Constant[]   params       = anno.getParams();
                int          paramCount   = params.length;

                Constant nameConst = paramCount > 0 ? params[0] : null;
                Constant optsConst = paramCount > 1 ? params[1] : null;
                String   resourceName = nameConst instanceof StringConstant stringConst
                                ? stringConst.getValue()
                                : name;
                if (optsConst != null && !(optsConst instanceof RegisterConstant regConst &&
                                           regConst.getRegisterIndex() == Op.A_DEFAULT)) {
                    throw new UnsupportedOperationException("retrieve opts");
                }

                Label     varStart   = code.newLabel();
                ClassDesc resourceCD = resourceType.ensureClassDesc(typeSystem);
                int       slotIndex  = scope.allocateLocal(varIndex, TypeKind.REFERENCE);
                Slot      slot       = new SingleSlot(slotIndex, resourceType, resourceCD, name);
                code.localVariable(slotIndex, name, resourceCD, varStart, scope.endLabel);

                slots.put(varIndex, slot);

                loadCtx(code);
                Builder.loadTypeConstant(code, typeSystem, resourceType);
                code.ldc(resourceName)
                    .aconst_null() // opts
                    .invokevirtual(CD_Ctx, "inject", Ctx.MD_inject)
                    .astore(slot.slot())
                    .labelBinding(varStart);
                return slot;
            }
        }
        throw new UnsupportedOperationException("name=" + name + "; type=" + type);
    }

    /**
     * Load arguments for a method invocation.
     */
    public void loadArguments(CodeBuilder code, JitMethodDesc jmd, int[] anArgValue) {
        boolean isOptimized = jmd.isOptimized;
        int     argCount    = anArgValue.length;

        for (int i = 0, c = jmd.standardParams.length; i < c; i++ ) {
            int          iArg = i < argCount ? anArgValue[i] : Op.A_DEFAULT;
            JitParamDesc pd   = isOptimized ? jmd.getOptimizedParam(i) : jmd.standardParams[i];
            switch (pd.flavor) {
            case SpecificWithDefault, WidenedWithDefault:
                if (iArg == Op.A_DEFAULT) {
                    code.aconst_null();
                    continue;
                }
                break;

            case PrimitiveWithDefault:
                if (iArg == Op.A_DEFAULT) {
                    assert isOptimized;
                    // default primitive with an additional `true`
                    Builder.defaultLoad(code, pd.cd);
                    code.iconst_1();
                } else {
                    // actual primitive with an additional `false`
                    loadArgument(code, iArg);
                    code.iconst_0();
                }
                continue;

            default:
                assert iArg != Op.A_DEFAULT;
                break;
            }
            loadArgument(code, iArg, pd);
        }
    }

    /**
     * Get the property value.
     */
    public void buildGetProperty(CodeBuilder code, Slot targetSlot, int propIdIndex, int retIndex) {
        if (!targetSlot.isSingle()) {
            throw new UnsupportedOperationException("Multislot P_Get");
        }

        PropertyConstant propId = (PropertyConstant) getConstant(propIdIndex);
        JitMethodDesc    jmdGet = builder.loadProperty(code, targetSlot.type(), propId);

        assignReturns(code, jmdGet, 1, new int[] {retIndex});
    }

    /**
     * Set the property value.
     */
    public void buildSetProperty(CodeBuilder code, Slot targetSlot, int propIdIndex, int valIndex) {
        if (!targetSlot.isSingle()) {
            throw new UnsupportedOperationException("Multislot P_Set");
        }
        PropertyConstant propId     = (PropertyConstant) getConstant(propIdIndex);
        PropertyInfo     propInfo   = propId.getPropertyInfo(targetSlot.type());
        JitMethodDesc    jmd        = propInfo.getSetterJitDesc(typeSystem);
        String           setterName = propInfo.ensureSetterJitMethodName(typeSystem);

        MethodTypeDesc md;
        if (jmd.isOptimized) {
            md         = jmd.optimizedMD;
            setterName += Builder.OPT;
        } else {
            md = jmd.standardMD;
        }

        loadCtx(code);
        Slot valueSlot = loadArgument(code, valIndex);
        if (!valueSlot.isSingle()) {
            throw new UnsupportedOperationException("Multislot L_Set");
        }
        code.invokevirtual(targetSlot.cd(), setterName, md);
    }

    /**
     * Call the "new$" [static] method.
     */
    public ClassDesc buildNew(CodeBuilder code, TypeConstant typeTarget,
                              MethodConstant idCtor, int[] anArgValue) {
        TypeInfo   infoTarget = typeTarget.ensureAccess(Access.PRIVATE).ensureTypeInfo();
        MethodInfo infoCtor   = infoTarget.getMethodById(idCtor);

        if (infoCtor == null) {
            throw new RuntimeException("Unresolvable constructor \"" +
                idCtor.getValueString() + "\" for " + typeTarget.getValueString());
        }

        String        sJitTarget = typeTarget.ensureJitClassName(typeSystem);
        ClassDesc     cdTarget   = ClassDesc.of(sJitTarget);
        JitMethodDesc jmdNew     = Builder.convertConstructToNew(infoTarget, sJitTarget,
                                    (JitCtorDesc) infoCtor.getJitDesc(typeSystem, typeTarget));

        boolean fOptimized = jmdNew.isOptimized;
        String  sJitNew    = idCtor.ensureJitMethodName(typeSystem).replace("construct", Builder.NEW);
        MethodTypeDesc md;
        if (fOptimized) {
            md       = jmdNew.optimizedMD;
            sJitNew += Builder.OPT;
        }
        else {
            md = jmdNew.standardMD;
        }

        loadCtx(code);
        if (infoTarget.hasGenericTypes()) {
            Builder.loadTypeConstant(code, typeSystem, infoTarget.getType());
        }
        loadArguments(code, jmdNew, anArgValue);

        code.invokestatic(cdTarget, sJitNew, md);
        return cdTarget;
    }

    /**
     * Assign return values from a method invocation.
     */
    public void assignReturns(CodeBuilder code, JitMethodDesc jmd, int cReturns, int[] anVar) {
        boolean isOptimized = jmd.isOptimized;

        for (int i = 0; i < cReturns; i++) {
            int          iOpt    = isOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
            JitParamDesc pdRet   = isOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
            int          nVar    = anVar[i];
            TypeConstant typeRet = pdRet.type;
            ClassDesc    cdRet   = pdRet.cd;
            Slot         slot    = ensureSlot(nVar, typeRet, cdRet, "");

            if (i == 0) {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, CD_boolean, pdExt.altIndex);

                    if (slot.isSingle()) {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.ifne(ifTrue);
                        builder.box(code, typeRet, cdRet);
                        code.goto_(endIf)
                            .labelBinding(ifTrue)
                            .pop();
                        Builder.loadNull(code);
                        code.labelBinding(endIf);
                    }
                    storeValue(code, slot);
                    break;

                default:
                    // process the natural return
                    storeValue(code, slot);
                    break;
                }
            } else {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, cdRet, pdExt.altIndex);

                    if (slot.isSingle()) {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.iconst_0()
                            .if_icmpeq(ifTrue);
                        builder.box(code, typeRet, cdRet);
                        code.goto_(endIf);
                        code.labelBinding(ifTrue)
                            .pop();
                        Builder.loadNull(code);
                        code.labelBinding(endIf);
                    }
                    storeValue(code, slot);
                    break;

                default:
                    Builder.loadFromContext(code, cdRet, pdRet.altIndex);
                    storeValue(code, slot);
                    break;
                }
            }
        }
    }

    /**
     * Throw an "Unsupported" exception.
     */
    public void throwUnsupported(CodeBuilder code) {
        loadCtx(code);
        code.aconst_null()
            .invokestatic(CD_Exception, "$unsupported",
                MethodTypeDesc.of(CD_xException, CD_Ctx, CD_JavaString))
            .athrow();
    }

    /**
     * Throw a "TypeMismatch" exception.
     */
    public void throwTypeMismatch(CodeBuilder code, String text) {
        throwException(code, ClassDesc.of(N_TypeMismatch), text);
    }

    /**
     * Throw an Ecstasy exception. The code we produce is equivalent to:
     * {@code throw new Exception(ctx).$init(ctx, text, null);}
     *
     * @param exCD  the ClassDesc for the Ecstasy exception (e.g. TypeMismatch)
     * @param text  the exception text
     */
    public void throwException(CodeBuilder code, ClassDesc exCD, String text) {
        Builder.invokeDefaultConstructor(code, exCD);
        loadCtx(code);
        code.loadConstant(text);
        code.aconst_null()
            .invokevirtual(exCD, "$init", MethodTypeDesc.of(
                        CD_xException, CD_Ctx, CD_JavaString, CD_Throwable))
            .athrow();
    }

    /**
     * Adjust the int value on the stack according to its type.
     */
    public void adjustIntValue(CodeBuilder code, TypeConstant type) {
        switch (type.getSingleUnderlyingClass(false).getName()) {
            case "Int8"   -> code.i2b();
            case "Int16"  -> code.i2s();
            case "Int32"  -> {}
            case "UInt8"  -> code.ldc(0xFF).iand();
            case "UInt16" -> code.ldc(0xFFFF).iand();
            case "UInt32" -> {}
            default       -> throw new IllegalStateException();
        }
    }

    /**
     * Get a Slot for the specified var index.
     */
    public Slot getSlot(int varIndex) {
        return slots.get(varIndex);
    }

    /**
     * Ensure an unnamed Slot the specified var index and an optimized ClassDesc for the specified type.
     */
    public Slot ensureSlot(int varIndex, TypeConstant type) {
        return ensureSlot(varIndex, type, JitTypeDesc.getJitClass(typeSystem, type), "name");
    }

    /**
     * Ensure a Slot the specified var index.
     */
    public Slot ensureSlot(int varIndex, TypeConstant type, ClassDesc cd, String name) {
        return varIndex == Op.A_STACK
            ? pushSlot(type, cd, name)
            : slots.computeIfAbsent(varIndex, ix -> new SingleSlot(
                    scope.allocateLocal(ix, Builder.toTypeKind(cd)), type, cd, name));
    }

    /**
     * Push a Slot onto the tail stack.
     */
    public Slot pushSlot(TypeConstant type, ClassDesc cd, String name) {
        Slot slot = new SingleSlot(tailSlot, type, cd, name);
        tailSlot += Builder.toTypeKind(cd).slotSize();
        tailStack.push(slot);
        return slot;
    }

    /**
     * Push an extended Slot onto the tail stack.
     */
    public Slot pushExtSlot(TypeConstant type, ClassDesc cd, JitFlavor flavor, String name) {
        int slotIndex = tailSlot;
        tailSlot += Builder.toTypeKind(cd).slotSize();
        int slotExt = tailSlot++;

        Slot slot = new DoubleSlot(slotIndex, slotExt, flavor, type, cd, name);
        tailStack.push(slot);
        return slot;
    }

    /**
     * Pop a Slot from the tail stack.
     */
    public Slot popSlot() {
        Slot slot = tailStack.pop();
        tailSlot -= Builder.toTypeKind(slot.cd()).slotSize();
        if (slot instanceof DoubleSlot) {
            tailSlot--;
        }
        return slot;
    }

    /**
     * Load a value from the tail slot onto the Java stack.
     */
    public void popTempVar(CodeBuilder code) {
        Slot slot = popSlot();
        code.loadLocal(Builder.toTypeKind(slot.cd()), slot.slot());
    }

    /**
     * Store a value on the Java stack to the tail slot.
     */
    public void pushTempVar(CodeBuilder code, TypeConstant type, ClassDesc cd) {
        Slot slot = pushSlot(type, cd, "");
        code.storeLocal(Builder.toTypeKind(cd), slot.slot());
    }

    // ----- Slot classes --------------------------------------------------------------------------

    public interface Slot {
        int          slot(); // Java slot index
        TypeConstant type();
        ClassDesc    cd();
        String       name();
        boolean      isSingle();

        default boolean isIgnore() {
            return slot() == Op.A_IGNORE;
        }
        default boolean isThis() {
            return slot() == Op.A_THIS;
        }
    }

    public record SingleSlot(int slot, TypeConstant type, ClassDesc cd, String name)
            implements Slot {
        @Override
        public boolean isSingle() {
            return true;
        }
    }

    public record DoubleSlot(int slot, int extSlot, JitFlavor flavor,
                      TypeConstant type, ClassDesc cd, String name)
            implements Slot {
        @Override
        public boolean isSingle() {
            return false;
        }
    }
}
