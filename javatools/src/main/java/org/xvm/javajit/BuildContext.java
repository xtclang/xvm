package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.OpReturn;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.CastTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
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
import org.xvm.asm.op.Nop;

import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_MethodType;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Exception;
import static org.xvm.javajit.Builder.CD_JavaObject;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nException;
import static org.xvm.javajit.Builder.CD_nFunction;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.EXT;
import static org.xvm.javajit.Builder.N_TypeMismatch;
import static org.xvm.javajit.Builder.OPT;
import static org.xvm.javajit.JitFlavor.NullablePrimitive;
import static org.xvm.javajit.TypeSystem.ID_NUM;

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
        this.methodJitName = methodInfo.ensureJitMethodName(typeSystem);
        this.isFunction    = methodInfo.isFunction();
        this.isConstructor = methodInfo.isCtorOrValidator();
        this.isOptimized   = methodDesc.optimizedMD != null;
        this.typeMatrix    = new TypeMatrix(this);
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
        this.methodJitName = isGetter
                ? propInfo.ensureGetterJitMethodName(typeSystem)
                : propInfo.ensureSetterJitMethodName(typeSystem);
        this.isFunction    = propInfo.isConstant();
        this.isConstructor = false;
        this.isOptimized   = methodDesc.optimizedMD != null;
        this.typeMatrix    = new TypeMatrix(this);
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
        this.typeMatrix    = new TypeMatrix(this);
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
        this.typeMatrix    = new TypeMatrix(this);
    }

    public final Builder         builder;
    public final TypeSystem      typeSystem;
    public final String          className;
    public final TypeInfo        typeInfo;
    public final int             callDepth;
    public final MethodBody[]    callChain;
    public final MethodStructure methodStruct;
    public final JitMethodDesc   methodDesc;
    public final String          methodJitName; // standard name
    public final boolean         isOptimized;
    public final boolean         isFunction;
    public final boolean         isConstructor;

    /**
     * The map of {@link RegisterInfo}s indexed by the register id.
     */
    public final Map<Integer, RegisterInfo> registerInfos = new HashMap<>();

    /**
     * The {@link TypeMatrix} for this method.
     */
    public final TypeMatrix typeMatrix;

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
     * we produce the bytecode that look like the following pseudocode:
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
        Scope origScope = scope;
        scope = origScope.startPreprocessing();

        Deque<Integer>       guardStack    = null;
        Deque<List<Integer>> jumpAddrStack = null;
        Deque<List<Integer>> jumpDestStack = null;

        int            guardAddr = -1;     // the address of the last GuardAll op
        int            finAddr   = -1;     // the address of the last FinallyEnd op
        List<Integer>  jumpsAddr  = null;  // the addresses of Jump ops
        List<Integer>  jumpsDest  = null;  // the addresses of jump destinations
        boolean        doReturn   = false; // indicates whether FinallyEnd should generate returns
        for (int iPC = 0, opsCount = ops.length; iPC < opsCount; iPC++) {
            Op op = ops[currOpAddr = iPC];
            switch (op) {
            case GuardAll _:
                if (guardAddr < 0) {
                    guardStack    = new ArrayDeque<>();
                    jumpAddrStack = new ArrayDeque<>();
                    jumpDestStack = new ArrayDeque<>();
                } else {
                    guardStack.push(guardAddr);
                    jumpAddrStack.push(jumpsAddr);
                    jumpDestStack.push(jumpsDest);
                }
                jumpsAddr = new ArrayList<>();
                jumpsDest = new ArrayList<>();
                guardAddr = iPC;
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

                if (finAddr != -1) {
                    // the previous "FinallyEnd" needs to jump here upon a return
                    ((FinallyEnd) ops[finAddr]).registerJump(iPC);
                }
                break;

            case FinallyEnd finallyEnd:
                GuardAll guardAll = (GuardAll) ops[guardAddr];
                guardAddr = guardStack.isEmpty()
                        ? -1
                        : guardStack.pop();

                // only the very top GuardAll allocates the return values
                guardAll.registerJumps(jumpsDest, doReturn && guardAddr == -1);

                jumpsDest = jumpDestStack.isEmpty()
                        ? new ArrayList<>()
                        : jumpDestStack.pop();
                if (guardAddr == -1) {
                    doReturn = false;
                    finAddr  = -1;
                } else {
                    // the next FinallyEnd will tell us where to jump upon a return
                    finAddr = iPC;
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

            // there are scenarios when Exit or Nop are unreachable (e.g. following LoopEnd or throw);
            // also we still need to process all scope changing ops
            if (typeMatrix.isReached(iPC) || op.isEnter() || op.isExit() || op instanceof Nop) {
                op.computeTypes(this);
            } else {
                // TODO: remove
                System.err.println("Dead code: " + Op.toName(op.getOpCode()) + " at " + this +
                    " for " + typeInfo.getType().removeAccess().getValueString());
            }
        }

        // finish the preprocessing phase
        lineNumber = 1;
        scope = origScope;
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
     *
     * @return -1 to proceed to the next op or a positive op address to eliminate all code up to
     *         that address
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
     * Obtain the Constant at the specified index, verifying it is of the expected type.
     *
     * @param argId  the argument id
     * @param type   the expected type of the constant
     * @param <T>    the Constant type
     *
     * @return the constant, cast to the expected type
     *
     * @throws IllegalStateException if the constant is not of the expected type
     */
    public <T extends Constant> T getConstant(int argId, Class<T> type) {
        Constant constant = getConstant(argId);
        try {
            return type.cast(constant);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Expected " + type.getSimpleName() +
                ", found " + (constant == null ? "null" : constant.getClass().getSimpleName()), e);
        }
    }

    /**
     * Get the String value for the specified argument index.
     */
    public String getString(int argId) {
        return getConstant(argId, StringConstant.class).getValue();
    }

    /**
     * Get the type for the specified argument index.
     */
    public TypeConstant getTypeConstant(int argId) {
        return getConstant(argId, TypeConstant.class);
    }

    /**
     * TODO: Resolve the specified type.
     */
    public TypeConstant resolveType(CodeBuilder code, TypeConstant type) {
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
            thisType = thisType.ensureAccess(Access.STRUCT);
            registerInfos.put(Op.A_THIS, new SingleSlot(Op.A_THIS, extraArgs-1, thisType, CD_this, "thi$"));
        } else if (!isFunction) {
            registerInfos.put(Op.A_THIS, new SingleSlot(Op.A_THIS, 0, thisType, CD_this, "this$"));
        }
        typeMatrix.declare(-1, Op.A_THIS, thisType);

        JitParamDesc[] params = isOptimized ? methodDesc.optimizedParams : methodDesc.standardParams;
        for (int i = 0, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = methodStruct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(extraArgs + i); // compensate for implicits

            if (type.containsFormalType(true)) {
                type = type.resolveGenerics(pool(), thisType);
            }

            code.localVariable(slot, name, paramDesc.cd, scope.startLabel, scope.endLabel);
            scope.topReg = Math.max(scope.topReg, varIndex + 1);

            switch (paramDesc.flavor) {
            case Specific, Widened, Primitive, SpecificWithDefault, WidenedWithDefault:
                registerInfos.put(varIndex, new SingleSlot(varIndex, slot, type, paramDesc.cd, name));
                break;

            case NullablePrimitive, PrimitiveWithDefault:
                int extSlot = code.parameterSlot(extraArgs + i + 1);

                registerInfos.put(varIndex,
                    new DoubleSlot(this, varIndex, slot, extSlot, paramDesc.flavor, type, paramDesc.cd, name));
                i++; // already processed
                break;
            }
        typeMatrix.declare(-1, varIndex, type);
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
        scope = scope.enter(currOpAddr);

        if (!scope.preprocess) {
            code.labelBinding(scope.startLabel);
        }
        return scope;
    }

    /**
     * @return the exited scope
     */
    public Scope exitScope(CodeBuilder code) {
        Scope prevScope = scope;
        scope = prevScope.exit();

        boolean isReached = typeMatrix.isReached(currOpAddr);

        // clear up the old scope's entries
        typeMatrix.removeRegisters(isReached ? currOpAddr : currOpAddr + 1, scope.topReg);

        if (!scope.preprocess) {
            for (var it = registerInfos.entrySet().iterator(); it.hasNext();) {
                var entry = it.next();
                if (entry.getKey() >= scope.topReg) {
                    // remove the register
                    it.remove();
                }
                if (entry.getValue() instanceof Narrowed narrowedReg &&
                        narrowedReg.scopeDepth >= scope.depth) {
                    // reset the register type
                    entry.setValue(narrowedReg.origReg());
                }
            }


            // there are scenarios when Exit is unreachable (e.g. following LoopEnd)
            if (isReached) {
                mergeTypes(code, currOpAddr + 1);
            }
        }

        return prevScope;
    }

    /**
     * Merge the types for the specified address.
     */
    public void mergeTypes(CodeBuilder code, int jumpAddr) {
        // collect the registered that need to be "widened"
        for (int widenedId : typeMatrix.follow(currOpAddr, jumpAddr, -1)) {
            if (registerInfos.get(widenedId) instanceof Narrowed narrowedReg) {
                // TODO: if reg.changed
                RegisterInfo reg = narrowedReg.widen(this, code, typeMatrix.getType(widenedId, jumpAddr));
                registerInfos.put(widenedId, reg);
            }
        }
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

        RegisterInfo reg = getRegisterInfo(code, Op.A_THIS);
        return reg.load(code);
    }

    /**
     * Obtain the type of the specified argument.
     */
    public TypeConstant getArgumentType(int argId) {
        if (argId >= 0) {
            return typeMatrix.getType(argId, currOpAddr);
        }

        if (argId <= Op.CONSTANT_OFFSET) {
            TypeConstant type = getConstant(argId).getType();
            if (type.containsFormalType(true)) {
                type = type.resolveGenerics(pool(), typeInfo.getType());
            }

            // if the type is an enum value, widen it to its parent Enumeration
            if (type.isEnumValue()) {
                type = type.getSingleUnderlyingClass(false).getNamespace().getType();
            }
            return type;
        }

        return switch (argId) {
            case Op.A_THIS  -> typeMatrix.getType(argId, currOpAddr);
            case Op.A_SUPER -> {
                TypeConstant typeSuper = callChain[callDepth + 1].getIdentity().getType();
                assert typeSuper.isMethod();
                yield pool().bindMethodTarget(typeSuper);
            }
            default -> throw new UnsupportedOperationException("id=" + argId);
        };
    }

    /**
     * Build the code to load an argument value on the Java stack.
     */
    public RegisterInfo loadArgument(CodeBuilder code, int argId) {
        return argId >= 0
            ? adjustRegister(code, getRegisterInfo(code, argId).load(code), true)
            : argId <= Op.CONSTANT_OFFSET
                ? loadConstant(code, argId)
                : loadPredefineArgument(code, argId);
    }

    /**
     * Build the code to load an argument value on the Java stack. If the argument represents a
     * constant, the corresponding value gets loaded on the Java stack directly, without allocating
     * a Java slot, in which case the RegisterInfo.slot() returns the value of {@link Op#A_STACK}
     * (-1).
     *
     * @param targetDesc  the desired type description
     */
    public RegisterInfo loadArgument(CodeBuilder code, int argId, JitTypeDesc targetDesc) {
        RegisterInfo reg = loadArgument(code, argId);
        if (reg.cd().isPrimitive() && !targetDesc.cd.isPrimitive()) {
            if (reg instanceof DoubleSlot doubleSlot) {
                assert doubleSlot.flavor == NullablePrimitive;
                // loadArgument() has already loaded the value and the boolean

                Label ifTrue = code.newLabel();
                Label endIf  = code.newLabel();

                code.ifne(ifTrue);
                Builder.box(code, reg.type().removeNullable(), reg.cd());
                code.goto_(endIf)
                    .labelBinding(ifTrue);
                Builder.pop(code, doubleSlot.cd);
                Builder.loadNull(code);
                code.labelBinding(endIf);
                reg = new SingleSlot(targetDesc.type, targetDesc.cd, reg.name() + "?");
            } else {
                Builder.box(code, reg.type(), reg.cd());
                reg = new SingleSlot(targetDesc.type, targetDesc.cd, reg.name());
            }
        }
        return reg;
    }

    /**
     * Adjust the register type based on the type matrix type. This only appies to non-primitive
     * or boxed registers.
     *
     * @param loaded  if true, the register value has been loaded on the Java stack
     */
    protected RegisterInfo adjustRegister(CodeBuilder code, RegisterInfo reg, boolean loaded) {
        if (!reg.cd().isPrimitive()) {
            int          regId   = reg.regId();
            TypeConstant regType = reg.type().getCanonicalJitType();
            TypeConstant mtxType = typeMatrix.getType(regId, currOpAddr);

            if (!mtxType.equals(regType)) {
                int depth = scope.depth;
                if (reg instanceof Narrowed narrowedReg) {
                    reg     = narrowedReg.origReg();
                    regType = reg.type();
                    depth   = narrowedReg.scopeDepth;
                    if (mtxType.equals(regType)) {
                        return reg;
                    }
                }

                assert mtxType.isA(regType);
                // narrow, but stay boxed for primitive types
                ClassDesc narrowedCD = mtxType.ensureClassDesc(typeSystem);
                // if already loaded - cast here and don't cast on load
                reg = new Narrowed(regId, reg.slot(), mtxType, narrowedCD, reg.name(), depth, !loaded, reg);
                registerInfos.put(regId, reg);
                if (loaded) {
                    code.checkcast(narrowedCD);
                }
            }
        }
        return reg;
    }

    /**
     * Check if the specified argument has been already assigned a Java slot. If the argument points
     * to a constant, create a temporary Java slot for it. In either case, the corresponding value
     * is **not** loaded on the Java stack.
     */
    public RegisterInfo ensureRegister(CodeBuilder code, int argId) {
        if (argId >= 0) {
            return adjustRegister(code, getRegisterInfo(code, argId), false);
        }
        RegisterInfo reg = argId <= Op.CONSTANT_OFFSET
                ? loadConstant(code, argId)
                : loadPredefineArgument(code, argId);
        if (reg.slot() == 0) {
            return reg; // Op.A_THIS;
        }

        assert reg.isSingle() && reg.slot() == -1; // constant

        int slot = storeTempValue(code, reg.cd());
        return new SingleSlot(argId, slot, reg.type(), reg.cd(), reg.name());
    }

    /**
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(CodeBuilder code, int argId) {
        return loadConstant(code, getConstant(argId));
    }

    /**
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(CodeBuilder code, Constant constant) {
        return builder.loadConstant(this, code, constant);
    }

    /**
     * Build the code to load a value for a predefine constant on the Java stack.
     */
    public RegisterInfo loadPredefineArgument(CodeBuilder code, int argId) {
        switch (argId) {
        case Op.A_STACK:
            // this refers to a synthetic RegInfo created by the pushTempRegister() method
            RegisterInfo reg = tempRegStack.pop();
            return reg.load(code);

        case Op.A_THIS:
            return loadThis(code);

        case Op.A_SUPER: {
            return loadSuper(code);
        }

        default:
            throw new UnsupportedOperationException("id=" + argId);
        }
    }

    /**
     * Build the code to load "super()" function instance on the Java stack.
     */
    private SingleSlot loadSuper(CodeBuilder code) {
        // instantiate a function object (see Builder.loadConstant for MethodConstant)

        int              nDepth      = callDepth + 1;
        MethodBody       bodySuper   = callChain[nDepth];
        MethodConstant   superId     = bodySuper.getIdentity();
        IdentityConstant containerId = superId.getNamespace();
        Component.Format format      = containerId.getComponent().getFormat();
        String           jitName     = MethodInfo.getJitIdentity(callChain).ensureJitMethodName(typeSystem);
        ClassDesc        containerCD;

        if (format == Component.Format.MIXIN) {
            // we need to generate a synthetic super
            containerCD = ClassDesc.of(className);
            jitName += ID_NUM + String.valueOf(nDepth);

            buildSuper(jitName, nDepth);
        } else {
            containerCD = containerId.ensureClassDesc(typeSystem);
        }

        JitMethodDesc               jmd  = bodySuper.getJitDesc(typeSystem, typeInfo.getType());
        DirectMethodHandleDesc.Kind kind = DirectMethodHandleDesc.Kind.SPECIAL;

        DirectMethodHandleDesc stdMD = MethodHandleDesc.ofMethod(kind,
                containerCD, methodJitName, jmd.standardMD);
        DirectMethodHandleDesc optMD = isOptimized
                ? MethodHandleDesc.ofMethod(kind, containerCD, methodJitName+OPT, jmd.optimizedMD)
                : null;

        TypeConstant fnType = bodySuper.getIdentity().getType();
        assert fnType.isMethod();

        fnType = pool().bindMethodTarget(fnType);

        MethodTypeDesc bindDesc = MethodTypeDesc.of(CD_MethodHandle, CD_JavaObject);
        ClassDesc      cd       = CD_nFunction;
        code.new_(cd)
            .dup()
            .aload(code.parameterSlot(0)); // ctx
        Builder.loadTypeConstant(code, typeSystem, fnType);
        code.ldc(stdMD)
            .aload(0)
            .invokevirtual(CD_MethodHandle, "bindTo", bindDesc);
        if (optMD == null) {
            code.aconst_null();
        } else {
            code.ldc(optMD)
                .aload(0)
                .invokevirtual(CD_MethodHandle, "bindTo", bindDesc);
        }
        code.iconst_1() // immutable = true
            .invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                CD_MethodHandle, CD_MethodHandle, CD_boolean));
        return new SingleSlot(fnType, cd, "");
    }

    /**
     * Store one or two values at the Java stack into the Java slot for the specified register.
     */
    public void storeValue(CodeBuilder code, RegisterInfo reg) {
        storeValue(code, reg, null);
    }

    /**
     * Store one or two values at the Java stack into the Java slot for the specified register.
     *
     * @param type  (optional) the known destination type
     */
    public void storeValue(CodeBuilder code, RegisterInfo reg, TypeConstant type) {
        ensureVarScope(code, reg);
        reg.store(this, code, type);
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

        if (type.containsFormalType(true)) {
            type = type.resolveGenerics(pool(), typeInfo.getType());
        }

        if (name.isEmpty()) {
            name = "v$" + regId;
        } else {
            name = name.replace('#', '$').replace('.', '$');
        }

        Label varStart = code.newLabel();

        ClassDesc    cd;
        RegisterInfo reg;
        if ((cd = JitTypeDesc.getNullablePrimitiveClass(type)) != null) {
            int slotPrime = scope.allocateLocal(regId, cd);
            int slotExt   = scope.allocateLocal(regId, TypeKind.BOOLEAN);

            code.localVariable(slotPrime, name, cd, varStart, scope.endLabel);
            code.localVariable(slotExt,   name+EXT, CD_boolean, varStart, scope.endLabel);

            reg = new DoubleSlot(this, regId, slotPrime, slotExt, NullablePrimitive, type, cd, name);
        } else {
            cd = JitParamDesc.getJitClass(typeSystem, type);

            int slotPrime = scope.allocateLocal(regId, cd);
            code.localVariable(slotPrime, name, cd, varStart, scope.endLabel);

            reg = new SingleSlot(regId, slotPrime, type, cd, name);
        }

        registerInfos.put(regId, reg);
        unassignedRegisters.put(reg, varStart);
        return reg;
    }

    /**
     * Ensure the scope for the Java slot associated with the specified register.
     */
    public void ensureVarScope(CodeBuilder code, RegisterInfo reg) {
        Label varStart = unassignedRegisters.remove(reg);
        if (varStart != null) {
            code.labelBinding(varStart);
        }
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
                RegisterInfo reg        = new SingleSlot(regId, slot, resourceType, resourceCD, name);
                code.localVariable(slot, name, resourceCD, varStart, scope.endLabel);

                registerInfos.put(regId, reg);

                loadCtx(code);
                Builder.loadTypeConstant(code, typeSystem, resourceType);
                code.ldc(resourceName)
                    .aconst_null() // opts
                    .invokevirtual(CD_Ctx, "inject", Ctx.MD_inject);
                storeValue(code, reg);
                code.labelBinding(varStart);
                return reg;
            }
        }
        throw new UnsupportedOperationException("name=" + name + "; type=" + type);
    }

    /**
     * Load arguments for a method invocation.
     */
    public void loadCallArguments(CodeBuilder code, JitMethodDesc jmd, int[] anArgValue) {
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
    public RegisterInfo getRegisterInfo(CodeBuilder code, int regId) {
        return registerInfos.get(regId);
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
        return regId == Op.A_IGNORE
            ? new SingleSlot(regId, -2, type, cd, name)
            : registerInfos.computeIfAbsent(regId, ix -> {
                TypeConstant resolvedType = type.containsFormalType(true)
                    ? type.resolveGenerics(pool(), typeInfo.getType())
                    : type;

                return new SingleSlot(regId, scope.allocateLocal(ix, cd), resolvedType, cd, name);
            }
        );
    }

    /**
     * Narrow the type of the specified register for the duration of the current op. This may
     * cause moving data between Java slots.
     *
     * @param reg           the register to narrow
     * @param narrowedType  the narrowed type
     *
     * @return the narrowed register (if applied)
     */
    public RegisterInfo narrowRegister(CodeBuilder code, RegisterInfo origReg,
                                       TypeConstant narrowedType) {
        return narrowRegister(code, origReg, currOpAddr + 1, narrowedType);
    }

    /**
     * Narrow the type of the specified register in the code starting at the "from" op address.
     * Note, that passing the current address **does not** put the narrowed register into the
     * registry.
     * <p>
     * Note, that unlike the dead code elimination below, the narrowing could "stop" at any point an
     * assignment is made to the register.
     *
     * @param origReg      the register to narrow
     * @param fromAddr     the beginning address at which the narrowing should apply
     * @param narrowedType the narrowed type
     *
     * @return the narrowed register (if applied)
     */
    public RegisterInfo narrowRegister(CodeBuilder code, RegisterInfo origReg,
                                       int fromAddr, TypeConstant narrowedType) {
        TypeConstant origType = origReg.type();

        if (origReg.isJavaStack() || narrowedType.getCanonicalJitType().equals(origType)) {
            return origReg;
        }

        if (fromAddr > currOpAddr + 1 && computeEndScopeAddr(fromAddr) == fromAddr) {
            // there is nothing to apply the narrowing to
            return origReg;
        }

        ClassDesc narrowedCD = JitTypeDesc.getJitClass(typeSystem, narrowedType);
        int       narrowedSlot;
        if (narrowedType.isPrimitive() || origType.isPrimitive()) {
            narrowedSlot = scope.allocateJavaSlot(narrowedCD);
        } else {
            narrowedSlot = origReg.slot();
        }

        Narrowed narrowedReg = new Narrowed(origReg.regId(), narrowedSlot, narrowedType, narrowedCD,
                origReg.name(), scope.depth, false, origReg);
        OpAction action = () -> {
            if (fromAddr > currOpAddr) {
                registerInfos.put(narrowedReg.regId, narrowedReg);
            }

            if (origReg.slot() != narrowedSlot || !narrowedCD.equals(CD_nObj)) {
                // even if the narrowed register uses the same slot, we need to let the Java verifier
                // know that
                origReg.load(code);
                if (narrowedCD.isPrimitive() && !origReg.cd().isPrimitive()) {
                    code.checkcast(narrowedType.ensureClassDesc(typeSystem)); // boxed
                    Builder.unbox(code, narrowedReg);
                } else {
                    code.checkcast(narrowedCD);
                }
                Builder.store(code, narrowedCD, narrowedSlot);
            }
            return -1;
        };

        if (fromAddr <= currOpAddr + 1) {
            action.prepare();
        } else {
            addAction(fromAddr, action);
        }
        return narrowedReg;
    }

    /**
     * Reset the narrowed register info.
     */
    public RegisterInfo resetRegister(Narrowed narrowedReg) {
        RegisterInfo origReg = narrowedReg.origReg;
        registerInfos.put(narrowedReg.regId, origReg);
        return origReg;
    }

    /**
     * Mark the ops in the specified range as "unreachable". If "addrTo" is -1, compute the
     * "addrTo" address based on the corresponding scope exit.
     *
     * @param fromAddr  the beginning address of the dead code (inclusive)
     * @param toAddr    the ending address of the dead code (exclusive; could be -1)
     *
     * @return true iff the end of scope was computed and the "skip" action was generated
     */
    public boolean markDeadCode(int fromAddr, int toAddr) {
        int endAdr = toAddr == -1
            ? computeEndScopeAddr(fromAddr)
            : toAddr;

        if (endAdr > fromAddr) {
            addAction(fromAddr, () -> endAdr);
            return true;
        }
        return false;
    }

    /**
     * Get the property value.
     */
    public void buildGetProperty(CodeBuilder code, RegisterInfo targetSlot, int propIdIndex, int retId) {
        if (!targetSlot.isSingle()) {
            throw new UnsupportedOperationException("Multislot P_Get");
        }

        PropertyConstant propId = getConstant(propIdIndex, PropertyConstant.class);
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
        PropertyConstant propId     = getConstant(propIdIndex, PropertyConstant.class);
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
        String  sJitNew    = infoCtor.ensureJitMethodName(typeSystem).replace("construct", Builder.NEW);
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
        loadCallArguments(code, jmdNew, anArgValue);

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
                case NullablePrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, CD_boolean, pdExt.altIndex);

                    if (reg.isSingle()) {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.ifne(ifTrue);
                        Builder.box(code, typeRet, cdRet);
                        code.goto_(endIf)
                            .labelBinding(ifTrue)
                            .pop();
                        Builder.loadNull(code);
                        code.labelBinding(endIf);
                    }
                    storeValue(code, reg, typeRet);
                    break;

                default:
                    // process the natural return
                    storeValue(code, reg, typeRet);
                    break;
                }
            } else {
                switch (pdRet.flavor) {
                case NullablePrimitive:
                    assert isOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, cdRet, pdExt.altIndex);

                    if (reg.isSingle()) {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.iconst_0()
                            .if_icmpeq(ifTrue);
                        Builder.box(code, typeRet, cdRet);
                        code.goto_(endIf);
                        code.labelBinding(ifTrue)
                            .pop();
                        Builder.loadNull(code);
                        code.labelBinding(endIf);
                    }
                    storeValue(code, reg, typeRet);
                    break;

                default:
                    Builder.loadFromContext(code, cdRet, pdRet.altIndex);
                    storeValue(code, reg, typeRet);
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
                MethodTypeDesc.of(CD_nException, CD_Ctx, CD_JavaString))
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
                CD_nException, CD_Ctx, CD_JavaString, CD_Throwable))
            .athrow();
    }

    /**
     * Create a String and store the reference to the String on the stack.
     * <p>
     * The String template is formatted with values from the provided slots.
     * Each occurrence of {@code "\u0001"} is replaced with the value from an entry in the {@code
     * argSlots} array.
     *
     * @param code       the {@link CodeBuilder} to use
     * @param text       the String template to inject values into
     * @param argsTypes  the types of the arguments to inject into the String. The array must
     */
    public void buildString(CodeBuilder  code,
                                   String       text,
                                   ClassDesc... argsTypes) {
        DirectMethodHandleDesc bsm = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.of("java.lang.invoke.StringConcatFactory"),
                "makeConcatWithConstants",
                MethodTypeDesc.of(CD_CallSite, CD_MethodHandles_Lookup, CD_String,
                        CD_MethodType, CD_String, CD_Object.arrayType())
        );
        code.invokedynamic(DynamicCallSiteDesc.of(bsm, "makeConcat",
                MethodTypeDesc.of(CD_String, argsTypes), text));
    }

    /**
     * Create a String and store the reference to the String in a new local variable slot.
     * <p>
     * The String template is formatted with values from the provided slots.
     * Each occurrence of {@code "\u0001"} is replaced with the value from an entry in the {@code
     * argSlots} array.
     *
     * @param code       the {@link CodeBuilder} to use
     * @param text       the String template to inject values into
     * @param argsTypes  the types of the arguments to inject into the String. The array must
     *
     * @return the identifier of the local variable slot that the String has been stored into
     */
    public int buildAndStoreString(CodeBuilder  code,
                                   String       text,
                                   ClassDesc... argsTypes) {
        buildString(code, text, argsTypes);
        return storeTempValue(code, CD_String);
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
            case "Char"   -> {}
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
        RegisterInfo tempReg  = new SingleSlot(Op.A_THIS, tempSlot, type, cd, "");
        tempRegStack.push(tempReg);
        return tempReg;
    }

    /**
     * Store a value for the specified ClassDesc on Java stack onto a temporary slot.
     */
    public int storeTempValue(CodeBuilder code, ClassDesc cd) {
        int slot = scope.allocateJavaSlot(cd);
        Builder.store(code, cd, slot);
        return slot;
    }

    /**
     * Create a "deferred" context to generate a synthetic method representing a "super" method
     * in a call chain that originates from a mixin or annotation.
     */
    public void buildSuper(String jitName, int depth) {
        deferAssembly(new BuildContext(this, jitName, depth));
    }

    /**
     * Create a "deferred" context to generate a synthetic Java method representing a method or
     * function that originates from a mixin, annotation or a lambda.
     */
    public void buildMethod(String jitName, MethodBody body) {
        deferAssembly(new BuildContext(this, jitName, body));
    }

    /**
     * Wrapper around {@link TypeConstant#combine} that considers primitive and formal types.
     */
    public TypeConstant combine(TypeConstant baseType, TypeConstant inferredType) {
        if (baseType.isPrimitive()) {
            assert baseType.isA(inferredType) || inferredType.isFormalType();
            return baseType;
        }
        if (inferredType.isPrimitive()) {
            assert inferredType.isA(baseType) || baseType.isFormalType();
            return inferredType;
        }

        if (baseType.isFormalType() && inferredType.isA(baseType.resolveConstraints())) {
            // use non-formal type
            return inferredType;
        }
        if (inferredType.isFormalType() && baseType.isA(inferredType.resolveConstraints())) {
            return baseType;
        }

        return new CastTypeConstant(pool(), baseType, inferredType);
    }

    /**
     * Wrapper around {@link TypeConstant#andNot} that considers primitive and formal types.
     */
    public TypeConstant andNot(TypeConstant baseType, TypeConstant inferredType) {
        TypeConstant notType = baseType.andNot(pool(), inferredType);
        return notType == null || notType.equals(baseType)
            ? baseType
            : new CastTypeConstant(pool(), baseType, notType);
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
        if (startOp.isEnter()) {
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

    public record SingleSlot(int regId, int slot, TypeConstant type, ClassDesc cd, String name)
            implements RegisterInfo {

        /**
         * Construct the SingleSlot representing a value placed on the Java stack.
         */
        public SingleSlot(TypeConstant type, ClassDesc cd, String name) {
            this(Op.A_STACK, JAVA_STACK, type, cd, name);
        }

        /**
         * Construct the SingleSlot.
         */
        public SingleSlot(int regId, int slot, TypeConstant type, ClassDesc cd, String name) {
            this.regId = regId;
            this.slot  = slot;
            this.type  = type.getCanonicalJitType();
            this.cd    = cd;
            this.name  = name;
        }

        @Override
        public boolean isSingle() {
            return true;
        }
    }

    public record DoubleSlot(BuildContext bctx, int regId, int slot, int extSlot,
                             JitFlavor flavor, TypeConstant type, ClassDesc cd, String name)
            implements RegisterInfo {

        public DoubleSlot(BuildContext bctx, int regId, int slot, int extSlot, JitFlavor flavor,
                             TypeConstant type, ClassDesc cd, String name) {
            this.bctx    = bctx;
            this.regId   = regId;
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

        @Override
        public RegisterInfo load(CodeBuilder code) {
            switch (flavor) {
            case PrimitiveWithDefault:
                Parameter parameter = bctx.methodStruct.getParam(regId);
                assert parameter.hasDefaultValue();

                Label ifTrue = code.newLabel();
                Label endIf  = code.newLabel();

                // if the extension slot is `true`, take the default value
                code.iload(extSlot)
                    .ifne(ifTrue)
                    .loadLocal(Builder.toTypeKind(cd), slot)
                    .goto_(endIf)
                    .labelBinding(ifTrue);
                bctx.builder.loadConstant(bctx, code, parameter.getDefaultValue());
                code.labelBinding(endIf);
                return new SingleSlot(type(), cd, name());

            case NullablePrimitive:
                // load the "extension" boolean flag last
                Builder.load(code, cd, slot);
                Builder.load(code, CD_boolean, extSlot);
                return this;

            default:
                throw new IllegalStateException();
            }
        }

        @Override
        public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
            // store the "extension" boolean flag first
            code.istore(extSlot());

            return RegisterInfo.super.store(bctx, code, type);
        }
    }

    public record Narrowed(int regId, int slot, TypeConstant type, ClassDesc cd, String name,
                           int scopeDepth, boolean castOnLoad, RegisterInfo origReg)
            implements RegisterInfo {

        @Override
        public boolean isSingle() {
            return true;
        }

        @Override
        public RegisterInfo original() {
            return origReg;
        }

        @Override
        public RegisterInfo load(CodeBuilder code) {
            RegisterInfo.super.load(code);
            if (castOnLoad) {
                code.checkcast(cd);
            }
            return this;
        }

        @Override
        public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
            if (type == null) {
                RegisterInfo origReg = bctx.resetRegister(this);

                if (this.slot() != origReg.slot()) {
                    if (cd().isPrimitive()) {
                        if (origReg.cd().isPrimitive()) {
                            assert origReg instanceof DoubleSlot;
                            code.iconst_0() // false
                                .istore(((DoubleSlot) origReg).extSlot);
                        } else {
                            Builder.box(code, type(), cd());
                        }
                    }
                }

                Builder.store(code, origReg.cd(), origReg.slot());
                return origReg;
            }

            if (type.isA(this.type())) {
                return RegisterInfo.super.store(bctx, code, type);
            } else {
                assert type.isA(original().type());
                return bctx.narrowRegister(code, original(), type).store(bctx, code, type);
            }
        }

        /**
         * Widen this register to the specified type. This may require data transfer between Java
         * slots.
         */
        public RegisterInfo widen(BuildContext bctx, CodeBuilder code, TypeConstant wideType) {
            TypeConstant prevType = type();
            RegisterInfo origReg  = original();
            TypeConstant origType = origReg.type();

            assert !prevType.equals(wideType) && wideType.isA(origType);

            if (this.slot() != origReg.slot()) {
                load(code);

                if (cd().isPrimitive()) {
                    if (origReg.cd().isPrimitive()) {
                        assert origReg instanceof DoubleSlot;
                        code.iconst_0() // false
                            .istore(((DoubleSlot) origReg).extSlot);
                    } else {
                        Builder.box(code, prevType, cd());
                    }
                }
                Builder.store(code, origReg.cd(), origReg.slot());
            }

            return wideType.equals(origType)
                ? origReg
                : bctx.narrowRegister(code, origReg, wideType).store(bctx, code, wideType);
        }
    }

    // ----- Actions -------------------------------------------------------------------------------

    @FunctionalInterface
    public interface OpAction {
        /**
         * Prepare for the compilation for a particular op.
         *
         * @return -1 to proceed to the next op or a positive op address to eliminate all code up to
         *         that address
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
        return className + " " + methodStruct.getIdentityConstant().getValueString() +
            ":" + lineNumber + " #" + currOpAddr;
    }
}
