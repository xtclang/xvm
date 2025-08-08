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
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Boolean;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_Nullable;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_TypeConstant;

/**
 * Whatever is necessary for the Ops compilation.
 */
public class BuildContext {

    /**
     * Construct {@link BuildContext}.
     */
    public BuildContext(TypeSystem typeSystem, TypeInfo typeInfo, MethodInfo method) {
        this.typeSystem = typeSystem;
        this.typeInfo   = typeInfo;
        this.method     = method;
        this.jmd        = method.getJitDesc(typeSystem);
        this.optimized  = jmd.optimizedMD != null;
    }

    public final TypeInfo      typeInfo;
    public final MethodInfo    method;
    public final TypeSystem    typeSystem;
    public final JitMethodDesc jmd;
    public final boolean       optimized;

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
        return method.getHead().getMethodStructure().getLocalConstants()[Op.CONSTANT_OFFSET - argId];
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

        if (!method.isFunction() && type.containsAutoNarrowing(true)) {
            // TODO: how to resolve?
        }

        return type;
        }

    /**
     * Prepare the compilation.
     */
    public void enterMethod(CodeBuilder code) {
        MethodStructure struct = method.getHead().getMethodStructure();

        lineNumber = struct.getSourceLineNumber();
        startScope = code.newLabel();
        endScope   = code.newLabel();

        code
            .labelBinding(startScope)
            .localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, startScope, endScope)
            ;

        if (!method.isFunction()) {
            TypeConstant thisType = typeInfo.getType();
            slots.put(Op.A_THIS, new SingleSlot(Op.A_THIS, thisType,
                ClassDesc.of(thisType.ensureJitClassName(typeSystem)), "thi$"));
        }

        JitParamDesc[] params = optimized ? jmd.optimizedParams : jmd.standardParams;
        for (int i = 0, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = struct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(i+1); // compensate for $ctx

            switch (paramDesc.flavor) {
                case Specific, Widened, Primitive, SpecificWithDefault, WidenedWithDefault:
                    slots.put(varIndex, new SingleSlot(slot, type, paramDesc.cd, name));
                    break;

                case MultiSlotPrimitive, PrimitiveWithDefault:
                    int extSlot = code.parameterSlot(i+1);

                    slots.put(varIndex, new DoubleSlot(slot, extSlot, type, paramDesc.cd, name));
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
     * Build the code to load the Ctx instance on the java stack.
     */
    public CodeBuilder loadCtx(CodeBuilder code) {
        code.aload(code.parameterSlot(0));
        return code;
    }

    /**
     * Build the code to load "this" instance on the java stack.
     */
    public Slot loadThis(CodeBuilder code) {
        assert !method.isFunction();
        code.aload(0);
        return slots.get(Op.A_THIS);
    }

    /**
     * Build the code to load an argument value on the java stack.
     */
    public Slot loadArgument(CodeBuilder code, int argId) {
        if (argId >= 0) {
            Slot slot = getSlot(argId);
            assert slot != null;
            ClassDesc cd = slot.cd();
            code.loadLocal(Builder.toTypeKind(cd), slot.slot());
            return slot;
        }
        return argId <= Op.CONSTANT_OFFSET
                ? loadConstant(code, argId)
                : loadPredefineArgument(code, argId);

    }

    /**
     * Build the code to load an argument value on the java stack.
     *
     * @param targetDesc  the desired type description
     */
    public Slot loadArgument(CodeBuilder code, int argId, JitTypeDesc targetDesc) {
        Slot slot = loadArgument(code, argId);
        if (slot.cd().isPrimitive() && !targetDesc.cd.isPrimitive()) {
            Builder.box(code, typeSystem, slot.type(), slot.cd());
            slot = new SingleSlot(slot.slot(), targetDesc.type, targetDesc.cd, slot.name());
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
     * Build the code to load a value for a constant on the java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public Slot loadConstant(CodeBuilder code, Constant constant) {
        // see NativeContainer#getConstType()

        if (constant instanceof StringConstant stringConst) {
            MethodTypeDesc MD_of = MethodTypeDesc.of(CD_String, CD_Ctx, CD_JavaString);
            // String.of(s)
            loadCtx(code)
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
            ConstantPool pool = pool();
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
     * Build the code to load a value for a predefine constant on the java stack.
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
     * Store a value at the specified slot.
     */
    public void storeValue(CodeBuilder code, Slot slot) {
        assert !slot.isStack();

        ClassDesc cd = slot.cd();
        if (cd.isPrimitive()) {
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

        ClassDesc cd = type.isPrimitive()
            ? JitParamDesc.getPrimitiveClass(type)
            : ClassDesc.of(type.ensureJitClassName(typeSystem));

        int slotIndex = code.allocateLocal(Builder.toTypeKind(cd));
        code.localVariable(slotIndex, name, cd, startScope, endScope);

        Slot slot = new SingleSlot(slotIndex, type, cd, name);
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
                    ClassDesc resourceCD = ClassDesc.of(resourceType.ensureJitClassName(typeSystem));
                    Slot      slot       = new SingleSlot(
                        code.allocateLocal(TypeKind.REFERENCE), resourceType, resourceCD, name);
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
            assert slot.type().equals(type);
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
        int          slot(); // java slot index
        TypeConstant type();
        ClassDesc    cd();
        String       name();

        default boolean isStack() {
            return slot() == Op.A_STACK;
        }
        default boolean isThis() {
            return slot() == Op.A_THIS;
        }
    }

    record SingleSlot(int slot, TypeConstant type, ClassDesc cd, String name) implements Slot {}

    record DoubleSlot(int slot, int extSlot, TypeConstant type, ClassDesc cd, String name)
        implements Slot {}
}
