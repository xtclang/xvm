package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_TypeConstant;


/**
 * Whatever is necessary for the Ops compilation.
 */
public class BuildContext {

    /**
     * Construct {@link BuildContext}.
     */
    public BuildContext(TypeSystem typeSystem, MethodInfo method) {
        this.typeSystem = typeSystem;
        this.method     = method;
        this.jmd        = method.getJitDesc(typeSystem);
        this.optimized  = method.getSignature().getName().endsWith(Builder.OPT);
    }

    public final MethodInfo    method;
    public final TypeSystem    typeSystem;
    public final JitMethodDesc jmd;
    public final boolean       optimized;

    /**
     * The map of {@link Slot}s indexed by the Var index.
     */
    public final Map<Integer, Slot> slots = new HashMap<>();

    /**
     * The current line number.
     */
    public int lineNumber;

    /**
     * @return the ConstantPool used by this {@link BuildContext}.
     */
    public ConstantPool pool() {
        return typeSystem.pool();
    }

    /**
     * Get the constant for the specified argument index.
     */
    public Constant getConstant(int argIndex) {
        assert argIndex <= Op.CONSTANT_OFFSET;
        return method.getHead().getMethodStructure().getLocalConstants()[Op.CONSTANT_OFFSET - argIndex];
    }

    /**
     * Get the String value for the specified argument index.
     */
    public String getString(int argIndex) {
        return ((StringConstant) getConstant(argIndex)).getValue();
    }

    /**
     * Get the type for the specified argument index.
     */
    public TypeConstant getType(int argIndex) {
        return resolveType((TypeConstant) getConstant(argIndex)); // must exist
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

        JitParamDesc[] params = optimized ? jmd.optimizedParams : jmd.standardParams;
        // skip the "Ctx $ctx" at index 0
        for (int i = 1, c = params.length; i < c; i++) {
            JitParamDesc paramDesc = params[i];
            int          varIndex  = paramDesc.index;
            Parameter    param     = struct.getParam(varIndex);
            String       name      = param.getName();
            TypeConstant type      = param.getType();
            int          slot      = code.parameterSlot(i);

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
     * Build the code to load the Ctx instance on stack.
     */
    public CodeBuilder loadCtx(CodeBuilder code) {
        code.aload(code.parameterSlot(0));
        return code;
    }

    /**
     * Build the code to load an invocation/property target on the stack.
     */
    public Slot loadArgument(CodeBuilder code, int iArg) {
        if (iArg >= 0) {
            Slot slot = getSlot(iArg);
            assert slot != null;
            ClassDesc cd = slot.cd();
            code.loadLocal(Builder.toTypeKind(cd), slot.slot());
            return slot;
        }
        return iArg <= Op.CONSTANT_OFFSET
                ? loadConstant(code, iArg)
                : loadPredefineArgument(code, iArg);

    }

    /**
     * Build the code to load a value for a constant on the stack.
     *
     * We **always** load a primitive value if possible.
     */
    public Slot loadConstant(CodeBuilder code, int iArg) {
        return loadConstant(code, getConstant(iArg));
    }

    /**
     * Build the code to load a value for a constant on the stack.
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
            return new SingleSlot(-1, constant.getType(), CD_String, "");
        }
        if (constant instanceof IntConstant intConstant) {
            // TODO: support all Int/UInt types
            code
                .ldc(intConstant.getValue().getLong());
            return new SingleSlot(-1, constant.getType(), CD_long, "");
        }
        throw new UnsupportedOperationException();
        // return code;
    }

    /**
     * Build the code to load a value for a predefine constant on the stack.
     */
    public Slot loadPredefineArgument(CodeBuilder code, int iArg) {
        switch (iArg) {
            case Op.A_STACK:
                return null;

            default:
                throw new UnsupportedOperationException();
        }
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

    public interface Slot {
        int          slot();
        TypeConstant type();
        ClassDesc    cd();
        String       name();

        default boolean isStack() {
            return slot() == -1;
        }
    }

    record SingleSlot(int slot, TypeConstant type, ClassDesc cd, String name) implements Slot {}

    record DoubleSlot(int slot, int extSlot, TypeConstant type, ClassDesc cd, String name) implements Slot {}
}
