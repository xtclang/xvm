package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.OpReturn;
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
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.FinallyEnd;
import org.xvm.asm.op.FinallyStart;
import org.xvm.asm.op.GuardAll;
import org.xvm.asm.op.Guarded;
import org.xvm.asm.op.Jump;

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
 * Whatever is necessary for the method bytecode production.
 */
public class BuildContext {
    /**
     * Construct {@link BuildContext} for a "top" method in the call chain.
     */
    public BuildContext(Builder builder, String className, TypeInfo typeInfo, MethodInfo methodInfo) {
        this.builder       = builder;
        this.typeSystem    = builder.typeSystem;
        this.className     = className;
        this.typeInfo      = typeInfo;
        this.callChain     = methodInfo.getChain();
        this.methodStruct  = callChain[0].getMethodStructure();
        this.callDepth     = 0;
        this.methodDesc    = methodInfo.getJitDesc(typeSystem, typeInfo.getType());
        this.methodJitName = null;
        this.isFunction    = methodInfo.isFunction();
        this.isConstructor = methodInfo.isConstructor();
        this.isOptimized   = methodDesc.optimizedMD != null;
    }

    /**
     * Construct {@link BuildContext} for a property accessor.
     */
    public BuildContext(Builder builder, String className, TypeInfo typeInfo, PropertyInfo propInfo,
                        boolean isGetter) {
        this.builder       = builder;
        this.typeSystem    = builder.typeSystem;
        this.className     = className;
        this.typeInfo      = typeInfo;
        this.callDepth     = 0;
        this.callChain     = isGetter
                ? propInfo.ensureOptimizedGetChain(typeInfo, null)
                : propInfo.ensureOptimizedSetChain(typeInfo, null);
        this.methodStruct  = callChain[0].getMethodStructure();
        this.methodDesc = isGetter
                ? propInfo.getGetterJitDesc(typeSystem)
                : propInfo.getSetterJitDesc(typeSystem);
        this.methodJitName = null;
        this.isFunction    = propInfo.isConstant();
        this.isConstructor = false;
        this.isOptimized   = methodDesc.optimizedMD != null;
    }

    /**
     * Construct {@link BuildContext} for a synthetic method in the call chain.
     */
    private BuildContext(BuildContext bctx, String jitName, int callDepth) {
        MethodBody body = bctx.callChain[callDepth];

        this.builder       = bctx.builder;
        this.typeSystem    = bctx.builder.typeSystem;
        this.className     = bctx.className;
        this.typeInfo      = bctx.typeInfo;
        this.callChain     = bctx.callChain;
        this.methodStruct  = body.getMethodStructure();
        this.callDepth     = callDepth;
        this.methodDesc    = body.getJitDesc(typeSystem, typeInfo.getType());
        this.methodJitName = jitName;
        this.isFunction    = bctx.isFunction;
        this.isConstructor = bctx.isConstructor;
        this.isOptimized   = methodDesc.optimizedMD != null;
    }

    /**
     * Construct {@link BuildContext} for a synthetic function.
     */
    private BuildContext(BuildContext bctx, String jitName, MethodBody body) {
        this.builder       = bctx.builder;
        this.typeSystem    = bctx.builder.typeSystem;
        this.className     = bctx.className;
        this.typeInfo      = bctx.typeInfo;
        this.callChain     = bctx.callChain;
        this.methodStruct  = body.getMethodStructure();
        this.callDepth     = 0;
        this.methodDesc    = body.getJitDesc(typeSystem, typeInfo.getType());
        this.methodJitName = jitName;
        this.isFunction    = bctx.isFunction;
        this.isConstructor = bctx.isConstructor;
        this.isOptimized   = methodDesc.optimizedMD != null;
    }

    public final Builder         builder;
    public final TypeSystem      typeSystem;
    public final String          className;
    public final TypeInfo        typeInfo;
    public final int             callDepth;
    public final MethodBody[]    callChain;
    public final MethodStructure methodStruct;
    public final JitMethodDesc   methodDesc;
    public final String          methodJitName; // used only for deferred
    public final boolean         isOptimized;
    public final boolean         isFunction;
    public final boolean         isConstructor;

    /**
     * The map of {@link RegisterInfo}s indexed by the register id.
     */
    public final Map<Integer, RegisterInfo> registerInfos = new HashMap<>();

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
     * The current op address.
     */
    private int currOpAddr;

    /**
     * The Map of not-yet-assigned registers.
     */
    private final Map<RegisterInfo, Label> unassignedRegisters = new IdentityHashMap<>();

    /**
     * The stack of synthetic {@link RegisterInfo}s that are used internally.
     */
    private final Deque<RegisterInfo> tempRegStack = new ArrayDeque<>();

    /**
     * The map of {@link OpAction}s indexed by the register id.
     */
    private final Map<Integer, OpAction> actions = new HashMap<>();

    /**
     * Deferred compilation context.
     */
    private BuildContext deferred;

    /**
     * @return the ConstantPool used by this {@link BuildContext}.
     */
    public ConstantPool pool() {
        return typeSystem.pool();
    }

    /**
     * Generate the method code.
     */
    public void assembleCode(CodeBuilder code) {
        Op[] ops = methodStruct.getOps();

        enterMethod(code);
        preprocess(code, ops);
        process(code, ops);

        exitMethod(code);
    }

    /**
     * Preprocess the ops and collect all necessary information to produce the code for "finally"
     * blocks.
     * <p>
     * For every GUARD_ALL - FINALLY - E_FINALLY block we create synthetic variables to generate
     * conditional jumps as necessary. As an example, for a block:
     * <pre><code>
     *  Loop:
     *    while (True) {
     *      try {
     *          return foo();
     *      } catch (Exception1 e1) {
     *          bar1();
     *          continue;
     *      } catch (ExceptionN eN) {
     *          barN();
     *          break;
     *      } finally {
     *          fin();
     *      }
     *   }
     * </code></pre>
     * <p>
     * we produce the bytecode that look like the following pseudo code:
     *
     * <pre><code>
     *     Throwable $rethrow  = null;
     *     boolean   $jump1    = false;
     *     boolean   $jump2    = false;
     *     boolean   $doReturn = false;
     *     R1        $r1;
     *     try {
     *         try {
     *             $r1 = foo();
     *             $doReturn = true;
     *             GOTO Fin:
     *         } catch (Exception1 e1) {
     *             $rethrow = e1;
     *             bar1();
     *             $jump1 = true;
     *             GOTO Fin:
     *         } catch (ExceptionN eN) {
     *             $rethrow = eN;
     *             barn();
     *             $jump2 = true;
     *             GOTO Fin:
     *         }
     *     } catch (Throwable $t) {
     *         $rethrow = $t;
     *     }
     *
     *  Fin:
     *     fin();
     *
     *     if ($rethrow != null) throw $rethrow
     *     if ($jump1) GOTO Loop.Continue
     *     if ($jump2) GOTO Loop.Exit
     *     if ($doReturn) return $r1
     * </code></pre>
     *
     * @param code
     * @param ops
     */
    public void preprocess(CodeBuilder code, Op[] ops) {
        Deque<Integer>       guardStack    = null;
        Deque<List<Integer>> jumpAddrStack = null;
        Deque<List<Integer>> jumpDestStack = null;

        int            guard      = -1;
        List<Integer>  jumpsAddr  = null; // the addresses of Jump ops
        List<Integer>  jumpsDest  = null; // the addresses of jump destinations
        boolean        doReturn   = false;
        for (int iPC = 0, c = ops.length; iPC < c; iPC++) {
            switch (ops[iPC]) {
            case GuardAll _:
                if (guard < 0) {
                    guardStack    = new ArrayDeque<>();
                    jumpAddrStack = new ArrayDeque<>();
                    jumpDestStack = new ArrayDeque<>();
                } else {
                    guardStack.push(guard);
                    jumpAddrStack.push(jumpsAddr);
                    jumpDestStack.push(jumpsDest);
                }
                jumpsAddr = new ArrayList<>();
                jumpsDest = new ArrayList<>();
                guard     = iPC;
                break;

            case Jump jump:
                if (jump.shouldCallFinally()) {
                    jumpsAddr.add(iPC);
                }
                break;

            case OpReturn ret:
                if (ret.shouldCallFinally()) {
                    jumpsAddr.add(iPC);
                }
                break;

            case FinallyStart _:
                if (!jumpsAddr.isEmpty()) {
                    for (int jumpAddr : jumpsAddr) {
                        switch (ops[jumpAddr]) {
                        case Jump jump:
                            jumpsDest.add(jump.exchangeJump(this, code, iPC));
                            break;
                        case OpReturn ret:
                            ret.registerJump(iPC);
                            doReturn = true;
                            break;
                        default:
                             throw new IllegalStateException();
                        }
                    }
                // all the jump ops within the finally block are handled by the parent "finally"
                jumpsAddr = jumpAddrStack.isEmpty()
                        ? new ArrayList<>()
                        : jumpAddrStack.pop();
                }
                break;

            case FinallyEnd finallyEnd:
                GuardAll guardAll = (GuardAll) ops[guard];
                guard = guardStack.isEmpty()
                        ? -1
                        : guardStack.pop();

                // only the very top GuardAll allocates the return values
                guardAll.registerJumps(jumpsDest, doReturn && guard == -1);

                jumpsDest = jumpDestStack.isEmpty()
                        ? new ArrayList<>()
                        : jumpDestStack.pop();
                if (guard == -1) {
                    doReturn = false;
                }

                if (iPC == ops.length - 1 || !ops[iPC + 1].isReachable()) {
                    finallyEnd.setCompletes(false);
                }
                break;

            case org.xvm.asm.op.Label label:
                // there is a chance we are compiling the same ops multiple times (for different
                // formal types)
                label.setLabel(null);
                break;

            default:
                break;
            }
        }
    }

    /**
     * Process the ops - build the corresponding bytecode.
     *
     * @param code
     * @param ops
     */
    public void process(CodeBuilder code, Op[] ops) {
        for (int iPC = 0, c = ops.length; iPC < c; iPC++) {
            try {
                while (true) {
                    int skipTo = prepareOp(code, iPC);
                    if (skipTo < 0) {
                        break;
                    }
                    assert skipTo > iPC;
                    iPC = skipTo;
                }
                ops[iPC].build(this, code);
            } catch (Throwable e) {
                MethodStructure struct = methodStruct;
                StringBuilder sb = new StringBuilder();
                sb.append(className)
                    .append('.')
                    .append(struct.getIdentityConstant().getName())
                    .append('(')
                    .append(struct.getContainingClass().getSourceFileName());

                int nLine = struct.calculateLineNumber(iPC);
                if (nLine > 0) {
                    sb.append(':').append(nLine);
                } else {
                    sb.append(" iPC=")
                        .append(iPC);
                }
                sb.append(')');
                throw new RuntimeException("Failed to generate code for " +
                    "op=" + Op.toName(ops[iPC].getOpCode()) + "\n" + sb, e);
            }
        }
    }


    /**
     * Add a deferred compilation context.
     */
    public void deferAssembly(BuildContext bctx) {
        if (this.deferred != null) {
            bctx.deferred = this.deferred;
        }
        this.deferred = bctx;
    }

    /**
     * @return (optional) deferred compilation context
     */
    public BuildContext getDeferred() {
        return deferred;
    }

    /**
     * Add an action for the specified op address.
     */
    public void addAction(int opAddr, OpAction action) {
        assert opAddr > currOpAddr;

        OpAction currAct = actions.get(opAddr);
        if (currAct == null) {
            actions.put(opAddr, action);
        } else {
            actions.put(opAddr, new ActionChain(action, currAct));
        }
    }

    /**
     * Prepare compiling the specified op address.
     */
    public int prepareOp(CodeBuilder code, int opAddr) {
        OpAction currAct = actions.remove(currOpAddr = opAddr);
        return currAct == null
                ? -1
                : currAct.prepare();
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
    public void enterMethod(CodeBuilder code) {
        lineNumber = 1; // XVM ops are 0 based; Java is 1-based
        scope      = new Scope(this, code);
        code
            .lineNumber(methodStruct.getSourceLineNumber() + 1)
            .labelBinding(scope.startLabel)
            .localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, scope.startLabel, scope.endLabel)
            ;

        int          extraArgs = methodDesc.getImplicitParamCount(); // account for $ctx, $cctx, thi$
        TypeConstant thisType  = typeInfo.getType();
        ClassDesc    CD_this   = thisType.ensureClassDesc(typeSystem);
        if (isConstructor) {
            registerInfos.put(Op.A_THIS, new SingleSlot(extraArgs-1, thisType.ensureAccess(Access.STRUCT),
                CD_this, "thi$"));
        } else if (!isFunction) {
            registerInfos.put(Op.A_THIS, new SingleSlot(0, thisType, CD_this, "this$"));
        }

        JitParamDesc[] params = isOptimized ? methodDesc.optimizedParams : methodDesc.standardParams;
        for (int i = 0, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = methodStruct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(extraArgs + i); // compensate for implicits

            code.localVariable(slot, name, paramDesc.cd, scope.startLabel, scope.endLabel);
            scope.topReg = Math.max(scope.topReg, varIndex + 1);

            switch (paramDesc.flavor) {
            case Specific, Widened, Primitive, SpecificWithDefault, WidenedWithDefault:
                registerInfos.put(varIndex, new SingleSlot(slot, type, paramDesc.cd, name));
                break;

            case MultiSlotPrimitive, PrimitiveWithDefault:
                int extSlot = code.parameterSlot(extraArgs + i + 1);

                registerInfos.put(varIndex,
                    new DoubleSlot(slot, extSlot, paramDesc.flavor, type, paramDesc.cd, name));
                i++; // already processed
                break;
            }
        }
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
        registerInfos.entrySet().removeIf(entry -> entry.getKey() >= scope.topReg);
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
    public RegisterInfo loadThis(CodeBuilder code) {
        assert isConstructor || !isFunction;

        RegisterInfo reg = getRegisterInfo(Op.A_THIS);
        code.aload(reg.slot());
        return reg;
    }

    /**
     * Obtain the type of the specified argument.
     */
    public TypeConstant getArgumentType(int argId) {
        if (argId >= 0) {
            RegisterInfo reg = getRegisterInfo(argId);
            assert reg != null;
            return reg.type();
        }

        if (argId <= Op.CONSTANT_OFFSET) {
            TypeConstant type = getConstant(argId).getType();
            if (type.containsFormalType(true)) {
                type = type.resolveGenerics(pool(), typeInfo.getType());
            }
            return type;
        }

        return switch (argId) {
            case Op.A_THIS  -> getRegisterInfo(Op.A_THIS).type();
            default         -> throw new UnsupportedOperationException("id=" + argId);
        };
    }

    /**
     * Build the code to load an argument value on the Java stack.
     */
    public RegisterInfo loadArgument(CodeBuilder code, int argId) {
        if (argId >= 0) {
            RegisterInfo reg = getRegisterInfo(argId);
            assert reg != null;
            ClassDesc cd = reg.cd();
            if (reg instanceof DoubleSlot doubleSlot) {
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
                    return new SingleSlot(Op.A_STACK, reg.type(), cd, reg.name());

                case MultiSlotPrimitive:
                    code.loadLocal(Builder.toTypeKind(cd), doubleSlot.slot);
                    code.loadLocal(TypeKind.BOOLEAN, doubleSlot.extSlot);
                    return reg;
                }
            }
            code.loadLocal(Builder.toTypeKind(cd), reg.slot());
            return reg;
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
    public RegisterInfo loadArgument(CodeBuilder code, int argId, JitTypeDesc targetDesc) {
        RegisterInfo reg = loadArgument(code, argId);
        if (reg.cd().isPrimitive() && !targetDesc.cd.isPrimitive()) {
            if (reg instanceof DoubleSlot doubleSlot) {
                assert doubleSlot.flavor == MultiSlotPrimitive;
                // loadArgument() has already loaded the value and the boolean

                Label ifTrue = code.newLabel();
                Label endIf  = code.newLabel();

                code.ifne(ifTrue);
                builder.box(code, reg.type().removeNullable(), reg.cd());
                code.goto_(endIf)
                    .labelBinding(ifTrue);
                    Builder.pop(code, doubleSlot.cd);
                    Builder.loadNull(code);
                code.labelBinding(endIf);
                reg = new SingleSlot(Op.A_STACK, targetDesc.type, targetDesc.cd, reg.name() + "?");
            } else {
                builder.box(code, reg.type(), reg.cd());
                reg = new SingleSlot(Op.A_STACK, targetDesc.type, targetDesc.cd, reg.name());
            }
        }
        return reg;
    }

    /**
     * Build the code to load a value for a constant on the stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(CodeBuilder code, int argId) {
        return loadConstant(code, getConstant(argId));
    }

    /**
     * Build the code to load a value for a constant on the stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(CodeBuilder code, Constant constant) {
        return builder.loadConstant(code, constant);
    }

    /**
     * Build the code to load a value for a predefine constant on the Java stack.
     */
    public RegisterInfo loadPredefineArgument(CodeBuilder code, int argId) {
        switch (argId) {
        case Op.A_STACK:
            // this refers to a synthetic RegInfo created by the pushTempRegister() method
            RegisterInfo reg = tempRegStack.pop();
            if (reg instanceof DoubleSlot doubleSlot) {
                code.iload(doubleSlot.extSlot()); // load the boolean flag
            }
            Builder.load(code, reg.cd(), reg.slot());
            return reg;

        case Op.A_THIS:
            return loadThis(code);

        default:
            throw new UnsupportedOperationException("id=" + argId);
        }
    }

    /**
     * Store one or two values at the Java stack into the Java slot for the specified register.
     */
    public void storeValue(CodeBuilder code, RegisterInfo reg) {
        if (reg instanceof DoubleSlot doubleSlot) {
            code.istore(doubleSlot.extSlot()); // store the boolean flag
        }

        if (reg.isIgnore()) {
            Builder.pop(code, reg.cd());
        } else {
            Builder.store(code, reg.cd(), reg.slot());
        }

        Label varStart = unassignedRegisters.remove(reg);
        if (varStart != null) {
            code.labelBinding(varStart);
        }
    }

    /**
     * Introduce a new variable for the specified type id, name id style and an optional value.
     *
     * @param regId   the XVM register id
     * @param typeId  a "relative" (negative) number representing the TypeConstant representing
     *                the type
     * @param nameId  a "relative" (negative) number representing the StringConstant for the name
     *                or zero for unnamed vars
     */
    public RegisterInfo introduceVar(CodeBuilder code, int regId, int typeId, int nameId) {
        TypeConstant type = (TypeConstant) getConstant(typeId);
        String       name = nameId == 0 ? "" : ((StringConstant) getConstant(nameId)).getValue();

        if (type.containsFormalType(true)) {
            type = type.resolveGenerics(pool(), typeInfo.getType());
        }

        return introduceVar(code, regId, type, name);
    }

    /**
     * Introduce a new variable for the specified type and name.
     *
     * @param regId  the XVM register id
     * @param type   the variable type
     * @param name   the variable name
     */
    public RegisterInfo introduceVar(CodeBuilder code, int regId, TypeConstant type, String name) {
        if (regId < 0) {
            throw new IllegalArgumentException("Invalid var index: " + regId);
        }
        if (name.isEmpty()) {
            name = "v$" + regId;
        } else {
            name = name.replace('#', '$').replace('.', '$');
        }

        Label varStart = code.newLabel();

        ClassDesc    cd;
        RegisterInfo reg;
        if ((cd = JitTypeDesc.getMultiSlotPrimitiveClass(type)) != null) {
            int slotPrime = scope.allocateLocal(regId, cd);
            int slotExt   = scope.allocateLocal(regId, TypeKind.BOOLEAN);

            code.localVariable(slotPrime, name, cd, varStart, scope.endLabel);
            code.localVariable(slotExt,   name+EXT, CD_boolean, varStart, scope.endLabel);

            reg = new DoubleSlot(slotPrime, slotExt, MultiSlotPrimitive, type, cd, name);
        } else {
            cd = JitParamDesc.getJitClass(typeSystem, type);

            int slotPrime = scope.allocateLocal(regId, cd);
            code.localVariable(slotPrime, name, cd, varStart, scope.endLabel);

            reg = new SingleSlot(slotPrime, type, cd, name);
        }

        registerInfos.put(regId, reg);
        unassignedRegisters.put(reg, varStart);
        return reg;
    }

    /**
     * Build the code that creates a `Ref` object for the specified type and name and stores it in
     * the Java slot associated with the specified register id.
     */
    public RegisterInfo introduceRef(CodeBuilder code, String name, TypeConstant type, int regId) {
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

                Label        varStart   = code.newLabel();
                ClassDesc    resourceCD = resourceType.ensureClassDesc(typeSystem);
                int          slot       = scope.allocateLocal(regId, TypeKind.REFERENCE);
                RegisterInfo reg        = new SingleSlot(slot, resourceType, resourceCD, name);
                code.localVariable(slot, name, resourceCD, varStart, scope.endLabel);

                registerInfos.put(regId, reg);

                loadCtx(code);
                Builder.loadTypeConstant(code, typeSystem, resourceType);
                code.ldc(resourceName)
                    .aconst_null() // opts
                    .invokevirtual(CD_Ctx, "inject", Ctx.MD_inject)
                    .astore(reg.slot())
                    .labelBinding(varStart);
                return reg;
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
     * Get a {@link RegisterInfo} for the specified register id.
     */
    public RegisterInfo getRegisterInfo(int regId) {
        RegisterInfo reg = registerInfos.get(regId);
        if (reg instanceof Narrowed narrowed && currOpAddr > narrowed.lastOp) {
            reg = resetRegister(regId, narrowed);
        }
        return reg;
    }

    /**
     * Ensure an unnamed {@link RegisterInfo? for the specified register id and an optimized
     * ClassDesc for the specified type.
     */
    public RegisterInfo ensureRegInfo(int regId, TypeConstant type) {
        return ensureRegInfo(regId, type, JitTypeDesc.getJitClass(typeSystem, type), "");
    }

    /**
     * Ensure a {@link RegisterInfo} for the specified register id.
     */
    public RegisterInfo ensureRegInfo(int regId, TypeConstant type, ClassDesc cd, String name) {
        return registerInfos.computeIfAbsent(regId, ix -> new SingleSlot(
                scope.allocateLocal(ix, cd), type, cd, name));
    }

    /**
     * Narrow the type of the specified register in the code region between the "from" op address
     * and the "to" address. If "addrTo" is -1, compute the "addrTo" address based on the
     * corresponding scope exit.
     *
     * Note, that unlike the dead code elimination below, the narrowing could "stop" at any point
     * an assignment is made to the register.
     *
     * @param regId         the register id
     * @param fromAddr      the beginning address at which the narrowing should apply (inclusive)
     * @param toAddr        the address after which the narrowing should not apply (exclusive;
     *                      could be -1)
     * @param narrowedType  the narrowed type
     */
    public void narrowTarget(CodeBuilder code, int regId, int fromAddr, int toAddr,
                             TypeConstant narrowedType) {
        RegisterInfo origReg = getRegisterInfo(regId);
        assert origReg != null;

        if (toAddr == -1) {
            int endAddr = computeEndScopeAddr(fromAddr);
            if (endAddr == fromAddr) {
                // there is nothing to apply narrowing to
                return;
            }
            toAddr = endAddr;
        }

        ClassDesc narrowedCD   = JitTypeDesc.getJitClass(typeSystem, narrowedType);
        int       narrowedSlot = scope.allocateJavaSlot(narrowedCD);

        Narrowed narrowedReg = new Narrowed(narrowedSlot, narrowedType, narrowedCD,
            origReg.name(), fromAddr, toAddr, origReg);
        addAction(fromAddr, () -> {
            registerInfos.put(regId, narrowedReg);
            Builder.load(code, origReg.cd(), origReg.slot());
            if (narrowedCD.isPrimitive() && !origReg.cd().isPrimitive()) {
                code.checkcast(narrowedType.ensureClassDesc(typeSystem)); // boxed
                builder.unbox(code, narrowedType, narrowedCD);
            } else {
                code.checkcast(narrowedCD);
            }
            Builder.store(code, narrowedCD, narrowedSlot);
            return -1;
        });
    }

    /**
     * Reset the narrowed register info for the specified register id.
     */
    public RegisterInfo resetRegister(int regId, Narrowed narrowedReg) {
        RegisterInfo origReg = narrowedReg.origReg;
        registerInfos.put(regId, origReg);
        return origReg;
    }

    /**
     * Mark the ops in the specified range as "unreachable". If "addrTo" is -1, compute the
     * "addrTo" address based on the corresponding scope exit.
     *
     * @param fromAddr  the beginning address of the dead code (inclusive)
     * @param toAddr    the ending address of the dead code (exclusive; could be -1)
     */
    public void markDeadCode(int fromAddr, int toAddr) {
        int endAdr = toAddr == -1
            ? computeEndScopeAddr(fromAddr)
            : toAddr;

        if (endAdr > fromAddr) {
            addAction(fromAddr, () -> endAdr);
        }
    }

    /**
     * Get the property value.
     */
    public void buildGetProperty(CodeBuilder code, RegisterInfo targetSlot, int propIdIndex, int retId) {
        if (!targetSlot.isSingle()) {
            throw new UnsupportedOperationException("Multislot P_Get");
        }

        PropertyConstant propId = (PropertyConstant) getConstant(propIdIndex);
        JitMethodDesc    jmdGet = builder.loadProperty(code, targetSlot.type(), propId);

        assignReturns(code, jmdGet, 1, new int[] {retId});
    }

    /**
     * Set the property value.
     */
    public void buildSetProperty(CodeBuilder code, RegisterInfo targetSlot, int propIdIndex, int regId) {
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
        RegisterInfo reg = loadArgument(code, regId);
        if (!reg.isSingle()) {
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
            TypeConstant typeRet = pdRet.type;
            ClassDesc    cdRet   = pdRet.cd;
            int          regId   = anVar[i];
            RegisterInfo reg     = ensureRegInfo(regId, typeRet, cdRet, "");

            if (i == 0) {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, CD_boolean, pdExt.altIndex);

                    if (reg.isSingle()) {
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
                    storeValue(code, reg);
                    break;

                default:
                    // process the natural return
                    storeValue(code, reg);
                    break;
                }
            } else {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, cdRet, pdExt.altIndex);

                    if (reg.isSingle()) {
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
                    storeValue(code, reg);
                    break;

                default:
                    Builder.loadFromContext(code, cdRet, pdRet.altIndex);
                    storeValue(code, reg);
                    break;
                }
            }
        }
    }

    /**
     * Add the code to throw an "Unsupported" exception.
     */
    public void throwUnsupported(CodeBuilder code) {
        loadCtx(code);
        code.aconst_null()
            .invokestatic(CD_Exception, "$unsupported",
                MethodTypeDesc.of(CD_xException, CD_Ctx, CD_JavaString))
            .athrow();
    }

    /**
     * Add the code to throw a "TypeMismatch" exception.
     */
    public void throwTypeMismatch(CodeBuilder code, String text) {
        throwException(code, ClassDesc.of(N_TypeMismatch), text);
    }

    /**
     * Add the code to throw an Ecstasy exception. The code we produce is equivalent to:
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
     * Create a temporary {@link RegisterInfo} for internal "use once within a scope" scenarios.
     * This register can only be addressed via the synthetic register -1, at which point it will be
     * "popped".
     */
    public RegisterInfo pushTempRegister(TypeConstant type, ClassDesc cd) {
        int          tempSlot = scope.allocateJavaSlot(cd);
        RegisterInfo tempReg  = new SingleSlot(tempSlot, type, cd, "");
        tempRegStack.push(tempReg);
        return tempReg;
    }

    /**
     * Create a "deferred" context to generate a synthetic method representing a "super" method
     * in a call chain that originates from a mixin or annotation.
     */
    public void buildSuper(String jitName, int depth) {
        deferAssembly(new BuildContext(this, jitName, depth));
    }

    /**
     * Create a "deferred" context to generate a synthetic function representing a function
     * that originates from a mixin or annotation.
     */
    public void buildFunction(String jitName, MethodBody body) {
        deferAssembly(new BuildContext(this, jitName, body));
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return the last address (exclusive) to which the action should apply
     */
    private int computeEndScopeAddr(int fromAddr) {
        Op[] ops     = methodStruct.getOps();
        Op   startOp = ops[fromAddr].ensureOp();
        if (startOp instanceof OpReturn) {
            return fromAddr;
        }
        if (startOp instanceof Enter) {
            int depth = 1;
            int addr  = fromAddr + 1;
            while (true) {
                switch (ops[addr++].ensureOp()) {
                    case Enter _ -> depth++;
                    case Exit _  -> {if (--depth == 0) {return addr;}}
                    default      -> {}
                }
            }
        }
        return fromAddr;
    }

    // ----- RegisterInfo implementations ----------------------------------------------------------

    public record SingleSlot(int slot, TypeConstant type, ClassDesc cd, String name)
            implements RegisterInfo {

        public SingleSlot(int slot, TypeConstant type, ClassDesc cd, String name) {
            this.slot = slot;
            this.type = type.getCanonicalJitType();
            this.cd   = cd;
            this.name = name;
        }

        @Override
        public boolean isSingle() {
            return true;
        }
    }

    public record DoubleSlot(int slot, int extSlot, JitFlavor flavor,
                             TypeConstant type, ClassDesc cd, String name)
            implements RegisterInfo {

        public DoubleSlot(int slot, int extSlot, JitFlavor flavor,
                             TypeConstant type, ClassDesc cd, String name) {
            this.slot    = slot;
            this.extSlot = extSlot;
            this.flavor  = flavor;
            this.type    = type.getCanonicalJitType();
            this.cd      = cd;
            this.name    = name;
        }

        @Override
        public boolean isSingle() {
            return false;
        }
    }

    public record Narrowed(int slot, TypeConstant type, ClassDesc cd, String name,
                           int firstOp, int lastOp, RegisterInfo origReg)
            implements RegisterInfo {

        @Override
        public boolean isSingle() {
            return true;
        }
    }

    // ----- Actions -------------------------------------------------------------------------------

    @FunctionalInterface
    public interface OpAction {
        /**
         * Prepare for the compilation for a particular op.
         *
         * @return -1 to proceed to the next op or positive number to eliminate all code up to the
         *         specified address
         */
        int prepare();
    }

    /**
     * Chain two actions together.
     */
    public record ActionChain(OpAction act1, OpAction act2)
            implements OpAction {
        @Override
        public int prepare() {
            int next = act1.prepare();
            if (next > 0) {
                return next;
            }
            return act2.prepare();
        }
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString() {
        return className + " " + methodStruct.getIdentityConstant().getValueString();
    }
}
