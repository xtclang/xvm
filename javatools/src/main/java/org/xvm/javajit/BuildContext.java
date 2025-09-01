package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.EnumValueConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Boolean;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_Nullable;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_xObj;
import static org.xvm.javajit.Builder.EXT;

/**
 * Whatever is necessary for the Ops compilation.
 */
public class BuildContext {

    /**
     * Construct {@link BuildContext} for a method.
     */
    public BuildContext(TypeSystem typeSystem, TypeInfo typeInfo, MethodInfo methodInfo) {
        this.typeSystem   = typeSystem;
        this.typeInfo     = typeInfo;
        this.callChain    = methodInfo.getChain();
        this.methodStruct = callChain[0].getMethodStructure();
        this.jmd          = methodInfo.getJitDesc(typeSystem);
        this.isStatic     = methodInfo.isFunction();
        this.isOptimized  = jmd.optimizedMD != null;
    }

    /**
     * Construct {@link BuildContext} for a property accessor.
     */
    public BuildContext(TypeSystem typeSystem, TypeInfo typeInfo, PropertyInfo propInfo,
                        boolean isGetter) {
        this.typeSystem   = typeSystem;
        this.typeInfo     = typeInfo;
        this.callChain    = isGetter
                ? propInfo.ensureOptimizedGetChain(typeInfo, null)
                : propInfo.ensureOptimizedSetChain(typeInfo, null);
        this.methodStruct = callChain[0].getMethodStructure();
        this.jmd          = isGetter
                ? propInfo.getGetterJitDesc(typeSystem)
                : propInfo.getSetterJitDesc(typeSystem);
        this.isStatic     = propInfo.isConstant();
        this.isOptimized  = jmd.optimizedMD != null;
    }

    public final TypeInfo        typeInfo;
    public final MethodBody[]    callChain;
    public final MethodStructure methodStruct;
    public final TypeSystem      typeSystem;
    public final JitMethodDesc   jmd;
    public final boolean         isOptimized;
    public final boolean         isStatic;

    /**
     * The map of {@link Slot}s indexed by the Var index.
     */
    public final Map<Integer, Slot> slots = new HashMap<>();

    /**
     * The stack of {@link Slot}s.
     */
    private final Deque<Slot> stack = new ArrayDeque<>();

    /**
     * The current line number.
     */
    public int lineNumber;

    /**
     * The start-of-method label.
     */
    public Label startScope;

    /**
     * The end-of-method label.
     */
    public Label endScope;

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

        if (!isStatic && type.containsAutoNarrowing(true)) {
            // TODO: how to resolve?
        }

        return type;
        }

    /**
     * Prepare the compilation.
     */
    public void enterMethod(CodeBuilder code) {
        lineNumber = methodStruct.getSourceLineNumber();
        startScope = code.newLabel();
        endScope   = code.newLabel();

        code
            .lineNumber(lineNumber)
            .labelBinding(startScope)
            .localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, startScope, endScope)
            ;

        if (!isStatic) {
            TypeConstant thisType = typeInfo.getType();
            slots.put(Op.A_THIS, new SingleSlot(Op.A_THIS, thisType,
                thisType.ensureClassDesc(typeSystem), "thi$"));
        }

        JitParamDesc[] params = isOptimized ? jmd.optimizedParams : jmd.standardParams;
        for (int i = 0, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = methodStruct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(i+1); // compensate for $ctx

            code.localVariable(slot, name, paramDesc.cd, startScope, endScope);

            switch (paramDesc.flavor) {
            case Specific, Widened, Primitive, SpecificWithDefault, WidenedWithDefault:
                slots.put(varIndex, new SingleSlot(slot, type, paramDesc.cd, name));
                break;

            case MultiSlotPrimitive, PrimitiveWithDefault:
                int extSlot = code.parameterSlot(i+2);

                slots.put(varIndex,
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
        code.labelBinding(endScope);
    }

    /**
     * Build the code to load the Ctx instance on the Java stack.
     */
    public CodeBuilder loadCtx(CodeBuilder code) {
        code.aload(code.parameterSlot(0));
        return code;
    }

    /**
     * Build the code to load "this" instance on the Java stack.
     */
    public Slot loadThis(CodeBuilder code) {
        assert !isStatic;
        code.aload(0);
        return slots.get(Op.A_THIS);
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
                        .iconst_0()
                        .if_icmpne(ifTrue)
                        .loadLocal(Builder.toTypeKind(cd), doubleSlot.slot)
                    .goto_(endIf)
                    .labelBinding(ifTrue);
                        loadConstant(code, parameter.getDefaultValue());
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
                assert doubleSlot.flavor == JitFlavor.MultiSlotPrimitive;
                // loadArgument() has already loaded the value and the boolean

                Label ifTrue     = code.newLabel();
                Label endIf      = code.newLabel();
                code
                    .iconst_0()
                    .if_icmpne(ifTrue);
                    Builder.box(code, typeSystem, slot.type().removeNullable(), slot.cd());
                code.goto_(endIf)
                    .labelBinding(ifTrue);
                Builder.pop(code, doubleSlot.cd);
                code.getstatic(CD_Nullable, "Null", CD_Nullable)
                    .labelBinding(endIf);
                slot = new SingleSlot(Op.A_STACK, targetDesc.type, targetDesc.cd, slot.name() + "?");
            } else {
                Builder.box(code, typeSystem, slot.type(), slot.cd());
                slot = new SingleSlot(slot.slot(), targetDesc.type, targetDesc.cd, slot.name());
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
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public static Slot loadConstant(CodeBuilder code, Constant constant) {
        // see NativeContainer#getConstType()

        if (constant instanceof StringConstant stringConst) {
            MethodTypeDesc MD_of = MethodTypeDesc.of(CD_String, CD_Ctx, CD_JavaString);
            // String.of(s)
            code.aload(code.parameterSlot(0)) // $ctx
                .ldc(stringConst.getValue())
                .invokestatic(CD_String, "of", MD_of);
            return new SingleSlot(Op.A_STACK, constant.getType(), CD_String, "");
        }
        if (constant instanceof IntConstant intConstant) {
            // TODO: support all Int/UInt types
            code
                .ldc(intConstant.getValue().getLong());
            return new SingleSlot(Op.A_STACK, constant.getType(), CD_long, "");
        }
        if (constant instanceof EnumValueConstant enumConstant) {
            ConstantPool pool = constant.getConstantPool();
            if (enumConstant.getType().isOnlyNullable()) {
                code.getstatic(CD_Nullable, "Null", CD_Nullable);
                return new SingleSlot(-1, pool.typeNullable(), CD_Nullable, "");
            } else if (enumConstant.getType().isA(pool.typeBoolean())) {
                if (enumConstant.getIntValue().getInt() == 0) {
                    code.iconst_0();
                } else {
                    code.iconst_1();
                }
            return new SingleSlot(-1, pool.typeBoolean(), CD_Boolean, "");
            }
        }
        if (constant instanceof LiteralConstant litConstant) {
            switch (litConstant.getFormat()) {
            case IntLiteral:
                // TODO: delegate to IntN
                break;
            case FPLiteral:
                // TODO: delegate to FloatN
                break;
            }
        }
        throw new UnsupportedOperationException(constant.toString());
        // return code;
    }

    /**
     * Build the code to load a value for a predefine constant on the Java stack.
     */
    public Slot loadPredefineArgument(CodeBuilder code, int argId) {
        switch (argId) {
        case Op.A_STACK:
            return popSlot();

        case Op.A_THIS:
            return loadThis(code);

        default:
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Store one or two values at the Java stack into the specified slot.
     */
    public void storeValue(CodeBuilder code, Slot slot) {
        if (slot instanceof DoubleSlot doubleSlot) {
            code.istore(doubleSlot.extSlot()); // store the boolean flag
        }

        if (slot.isStack()) {
            // the value(s) is already on Java stack; keep it there
            return;
        }

        ClassDesc cd = slot.cd();
        if (slot.isIgnore()) {
            Builder.pop(code, cd);
        } else if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.istore(slot.slot());
                break;
            case "J":
                code.lstore(slot.slot());
                break;
            case "F":
                code.fstore(slot.slot());
                break;
            case "D":
                code.dstore(slot.slot());
                break;
            default:
                throw new IllegalStateException();
            }
        } else {
            code.astore(slot.slot());
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

        ClassDesc cd;
        Slot      slot;
        if ((cd = JitTypeDesc.getMultiSlotPrimitiveClass(type)) != null) {

            Label varStartScope = code.newLabel();
            code.labelBinding(varStartScope);

            int slotIndex = code.allocateLocal(Builder.toTypeKind(cd));
            int slotExt   = code.allocateLocal(TypeKind.BOOLEAN);

            code.localVariable(slotIndex, name, cd, varStartScope, endScope);
            code.localVariable(slotExt,   name+EXT, CD_boolean, varStartScope, endScope);

            slot = new DoubleSlot(slotIndex, slotExt, JitFlavor.MultiSlotPrimitive, type, cd, name);
        } else {
            cd = type.isPrimitive()
                ? JitParamDesc.getPrimitiveClass(type)
                : type.isSingleUnderlyingClass(true)
                    ? type.ensureClassDesc(typeSystem)
                    : CD_xObj;

            Label varStartScope = code.newLabel();
            code.labelBinding(varStartScope);

            int slotIndex = code.allocateLocal(Builder.toTypeKind(cd));
            code.localVariable(slotIndex, name, cd, varStartScope, endScope);

            slot = new SingleSlot(slotIndex, type, cd, name);
            }
        slots.put(varIndex, slot);
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

                Constant constant     = paramCount == 0 ? null : params[0];
                String   resourceName = constant instanceof StringConstant stringConst
                                ? stringConst.getValue()
                                : name;
                if (paramCount < 2) {
                    // opts are not specified; the ref can be trivially initialized on-the-spot
                    Label varStartScope = code.newLabel();
                    code.labelBinding(varStartScope);

                    ClassDesc resourceCD = resourceType.ensureClassDesc(typeSystem);
                    int       slotIndex  = code.allocateLocal(TypeKind.REFERENCE);
                    Slot      slot       = new SingleSlot(slotIndex, resourceType, resourceCD, name);
                    code.localVariable(slotIndex, name, resourceCD, varStartScope, endScope);

                    slots.put(varIndex, slot);

                    int typeIndex = typeSystem.registerConstant(resourceType);
                    loadCtx(code)
                        .dup()
                        .loadConstant(typeIndex)
                        .invokevirtual(CD_Ctx, "getConstant", Ctx.MD_getConstant) // <- const
                        .checkcast(CD_TypeConstant)                               // <- type
                        .ldc(resourceName)
                        .aconst_null()                                            // opts
                        .invokevirtual(CD_Ctx, "inject", Ctx.MD_inject)
                        .astore(slot.slot());
                    return slot;
                } else {
                    throw new UnsupportedOperationException("use opts");
                }
            }
        }
        throw new UnsupportedOperationException("name=" + name + "; type=" + type);
    }

    /**
     * Assign return values from a method invocation.
     */
    public void assignReturns(CodeBuilder code, JitMethodDesc jmd,
                              int cReturns, int[] anVar, boolean fOptimized) {
        for (int i = 0; i < cReturns; i++) {
            int          iOpt    = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
            JitParamDesc pdRet   = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
            int          nVar    = anVar[i];
            TypeConstant typeRet = pdRet.type;
            ClassDesc    cdRet   = pdRet.cd;
            Slot         slot    = ensureSlot(nVar, typeRet, cdRet, "");

            if (i == 0) {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert fOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, CD_boolean, pdExt.altIndex);

                    if (slot instanceof DoubleSlot doubleSlot) {
                        storeValue(code, slot);
                    } else {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.iconst_0()
                            .if_icmpne(ifTrue);
                        Builder.box(code, typeSystem, typeRet, cdRet);
                        code.goto_(endIf)
                            .labelBinding(ifTrue)
                            .pop()
                            .getstatic(CD_Nullable, "Null", CD_Nullable)
                            .labelBinding(endIf);
                        storeValue(code, slot);
                    }
                    break;

                default:
                    // process the natural return
                    storeValue(code, slot);
                    break;
                }
            } else {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert fOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];

                    // if the value is `True`, then the return value is Ecstasy `Null`
                    Builder.loadFromContext(code, cdRet, pdExt.altIndex);

                    if (slot instanceof DoubleSlot doubleSlot) {
                        storeValue(code, slot);
                    } else {
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.iconst_0()
                            .if_icmpeq(ifTrue);
                        Builder.box(code, typeSystem, typeRet, cdRet);
                        code.goto_(endIf);
                        code.labelBinding(ifTrue)
                            .pop()
                            .getstatic(CD_Nullable, "Null", CD_Nullable)
                            .labelBinding(endIf);
                        storeValue(code, slot);
                    }
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
     * Get a Slot for the specified var index.
     */
    public Slot getSlot(int varIndex) {
        return slots.get(varIndex);
    }

    /**
     * Ensure a Slot the specified var index.
     */
    public Slot ensureSlot(int varIndex, TypeConstant type, ClassDesc cd, String name) {
        if (varIndex == Op.A_STACK) {
            return pushSlot(new SingleSlot(Op.A_STACK, type, cd, name));
        } else {
            Slot slot = getSlot(varIndex);
            assert slot.cd().equals(cd);
            return slot;
        }
    }

    /**
     * Pop a Slot from the context stack.
     */
    public Slot popSlot() {
        return stack.pop();
    }

    /**
     * Push a Slot onto the context stack.
     */
    public Slot pushSlot(Slot slot) {
        assert slot.slot() == Op.A_STACK;
        stack.push(slot);
        return slot;
    }

    public interface Slot {
        int          slot(); // Java slot index
        TypeConstant type();
        ClassDesc    cd();
        String       name();
        boolean      isSingle();

        default boolean isStack() {
            return slot() == Op.A_STACK;
        }
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
