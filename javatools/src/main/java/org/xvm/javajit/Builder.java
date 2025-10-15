package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.EnumValueConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext.SingleSlot;
import org.xvm.javajit.BuildContext.Slot;
import org.xvm.javajit.TypeSystem.ClassfileShape;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.JitFlavor.Specific;

/**
 * Base class for JIT class builders.
 */
public abstract class Builder {
    public Builder(TypeSystem typeSystem) {
        this.typeSystem = typeSystem;
    }

    public final TypeSystem typeSystem;

    /**
     * Assemble the java class for the "impl" shape.
     */
    public void assembleImpl(String className, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException();
    }

    /**
     * Assemble the java class for the "pure" shape.
     */
    public void assemblePure(String className, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Compute a MethodTypeDesc for a Java method with the specified parameter and return types.
     */
    public MethodTypeDesc computeMethodDesc(TypeConstant[] paramTypes,
                                            TypeConstant[] returnTypes) {
        int         paramCount = paramTypes.length;
        ClassDesc[] paramCDs   = new ClassDesc[paramCount + 1];

        paramCDs[0] = ClassDesc.of(Ctx.class.getName());

        for (int i = 0; i < paramCount; i++)
            {
            paramCDs[i+1] = paramTypes[i].ensureClassDesc(typeSystem);
            }
        return MethodTypeDesc.of(returnTypes.length == 0 ? CD_void :
                returnTypes[0].ensureClassDesc(typeSystem), paramCDs);
        }

    /**
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public Slot loadConstant(CodeBuilder code, Constant constant) {
        // see NativeContainer#getConstType()

        switch (constant) {
        case StringConstant stringConst:
            MethodTypeDesc MD_of = MethodTypeDesc.of(CD_String, CD_Ctx, CD_JavaString);
            // String.of(s)
            code.aload(code.parameterSlot(0)) // $ctx
                .ldc(stringConst.getValue())
                .invokestatic(CD_String, "of", MD_of);
            return new SingleSlot(Op.A_STACK, constant.getType(), CD_String, "");

        case IntConstant intConstant:// TODO: support all Int/UInt types
            code.ldc(intConstant.getValue().getLong());
            return new SingleSlot(Op.A_STACK, constant.getType(), CD_long, "");

        case ByteConstant byteConstant:
            code.ldc(byteConstant.getValue().intValue());
            return new SingleSlot(Op.A_STACK, constant.getType(), CD_int, "");

        case LiteralConstant litConstant:
            switch (litConstant.getFormat()) {
            case IntLiteral:
                // TODO: delegate to IntN
                break;
            case FPLiteral:
                // TODO: delegate to FloatN
                break;
            }
            break;

        case SingletonConstant singleton:
            if (singleton instanceof EnumValueConstant enumConstant) {
                ConstantPool pool = constant.getConstantPool();
                if (enumConstant.getType().isOnlyNullable()) {
                    Builder.loadNull(code);
                    return new SingleSlot(Op.A_STACK, pool.typeNullable(), CD_Nullable, "");
                }
                else if (enumConstant.getType().isA(pool.typeBoolean())) {
                    if (enumConstant.getIntValue().getInt() == 0) {
                        code.iconst_0();
                    }
                    else {
                        code.iconst_1();
                    }
                    return new SingleSlot(Op.A_STACK, pool.typeBoolean(), CD_boolean, "");
                }
            }

            TypeConstant type = singleton.getType();
            JitTypeDesc  jtd  = type.getJitDesc(typeSystem);
            assert jtd.flavor == JitFlavor.Specific;

            // retrieve from Singleton.$INSTANCE (see CommonBuilder.assembleStaticInitializer)
            ClassDesc cd = jtd.cd;
            code.getstatic(cd, Instance, cd);
            return new SingleSlot(Op.A_STACK, type, cd, "");

        case NamedCondition cond:
            code.loadConstant(cond.getName());
            return new SingleSlot(Op.A_STACK, cond.getConstantPool().typeString(), CD_String, "");

        default:
            break;
        }

        throw new UnsupportedOperationException(constant.toString());
    }

    /**
     * Generate a value "load" for the specified Java class.
     */
    public static void load(CodeBuilder code, ClassDesc cd, int slot) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.iload(slot);
                break;
            case "J":
                code.lload(slot);
                break;
            case "F":
                code.fload(slot);
                break;
            case "D":
                code.dload(slot);
                break;
            default:
                throw new IllegalStateException();
            }
        } else {
            code.aload(slot);
        }
    }

    /**
     * Generate a value "store" for the specified Java class.
     */
    public static void store(CodeBuilder code, ClassDesc cd, int slot) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.istore(slot);
                break;
            case "J":
                code.lstore(slot);
                break;
            case "F":
                code.fstore(slot);
                break;
            case "D":
                code.dstore(slot);
                break;
            default:
                throw new IllegalStateException();
            }
        } else {
            code.astore(slot);
        }
    }

    /**
     * Generate a default value "load" for the specified Java class.
     */
    public static void defaultLoad(CodeBuilder code, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.iconst_0();
                break;
            case "J":
                code.lconst_0();
                break;
            case "F":
                code.fconst_0();
                break;
            case "D":
                code.dconst_0();
                break;
            case "V":
                break;
            }
        } else {
            code.aconst_null();
        }
    }

    /**
     * Generate a "load" for the XTC `Null` value.
     */
    public static void loadNull(CodeBuilder code) {
        code.getstatic(CD_Nullable, "Null", CD_Nullable);
    }

    /**
     * Generate a "load" for the specified TypeConstant.
     * In:  Ctx
     * Out: TypeConstant
     */
    public static void loadTypeConstant(CodeBuilder code, TypeSystem ts, TypeConstant type) {
        code.loadConstant(ts.registerConstant(type))
            .invokevirtual(CD_Ctx, "getConstant", Ctx.MD_getConstant) // <- const
            .checkcast(CD_TypeConstant);                              // <- type
    }

    /**
     * Generate a "get" for an xType.
     * In:  Ctx
     * Out: xType instance
     */
    public static void loadType(CodeBuilder code, TypeSystem ts, TypeConstant type) {
        code.dup(); // ctx
        loadTypeConstant(code, ts, type);
        code.swap() // [ctx, type] -> [type, ctx]
            .getfield(CD_Ctx, "container", CD_Container)
            .invokevirtual(CD_TypeConstant, "ensureXType",
                    MethodTypeDesc.of(CD_JavaObject, CD_Container))
            .checkcast(CD_xType);
    }

    /**
     * Generate a default return for the specified Java class assuming the corresponding value
     * is already on java stack.
     */
    public static void addReturn(CodeBuilder code, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.ireturn();
                break;
            case "J":
                code.lreturn();
                break;
            case "F":
                code.freturn();
                break;
            case "D":
                code.dreturn();
                break;
            case "V":
                code.return_();
                break;
            default:
                throw new IllegalStateException();
            }
        } else {
            code.areturn();
        }
    }

    /**
     * Generate a "pop()" opcode for Java class assuming the corresponding value is already on java
     * stack.
     */
    public static void pop(CodeBuilder code, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "J", "D":
                code.pop2();
                break;
            default:
                code.pop();
            }
        } else {
            code.pop();
        }
    }

    /**
     * Generate unboxing opcodes for a wrapper reference on the stack and the specified primitive
     * class.
     *
     * In: the boxed reference
     * Out: unboxed primitive value
     *
     * @param type the primitive type
     * @param cd   the corresponding ClassDesc
     */
    public void unbox(CodeBuilder code, TypeConstant type, ClassDesc cd) {
        assert cd.isPrimitive() && type.isPrimitive();

        switch (cd.descriptorString()) {
        case "Z": // boolean
            assert type.equals(typeSystem.pool().typeBoolean());
            code.getfield(CD_Boolean, "$value", cd);
            break;

        case "J": // long
            switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Int64"  -> code.getfield(CD_Int64,  "$value", cd);
                case "UInt64" -> code.getfield(CD_UInt32, "$value", cd);
                default       -> throw new IllegalStateException();
            }
            break;

        case "I": // int
            switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Char"   -> code.getfield(CD_Char, "codepoint", cd);
                case "Int8"   -> code.getfield(CD_Int8,   "$value", cd);
                case "Int16"  -> code.getfield(CD_Int16,  "$value", cd);
                case "Int32"  -> code.getfield(CD_Int32,  "$value", cd);
                case "UInt8"  -> code.getfield(CD_UInt8,  "$value", cd);
                case "UInt16" -> code.getfield(CD_UInt16, "$value", cd);
                case "UInt32" -> code.getfield(CD_UInt32, "$value", cd);
                default       -> throw new IllegalStateException();
            }
            break;

        default:
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Generate boxing opcodes for a primitive value of the specified primitive class on the stack.
     *
     * In: unboxed primitive value
     * Out: the boxed reference
     */
    public void box(CodeBuilder code, TypeConstant type, ClassDesc cd) {
        assert cd.isPrimitive() && type.isPrimitive();

        switch (cd.descriptorString()) {
        case "Z": // boolean
            assert type.equals(typeSystem.pool().typeBoolean());
            code.invokestatic(CD_Boolean, "$box", MD_Boolean_box);
            break;

        case "J": // long
            switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Int64"  -> code.invokestatic(CD_Int64,  "$box", MD_Int64_box);
                case "UInt64" -> code.invokestatic(CD_UInt64, "$box", MD_UInt64_box);
                default       -> throw new IllegalStateException();
            }
            break;

        case "I": // int
            switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Char"   -> code.invokestatic(CD_Char,   "$box", MD_Char_box);
                case "Int8"   -> code.invokestatic(CD_Int8,   "$box", MD_Int8_box);
                case "Int16"  -> code.invokestatic(CD_Int16,  "$box", MD_Int16_box);
                case "Int32"  -> code.invokestatic(CD_Int32,  "$box", MD_Int32_box);
                case "UInt8"  -> code.invokestatic(CD_UInt8,  "$box", MD_UInt8_box);
                case "UInt16" -> code.invokestatic(CD_UInt16, "$box", MD_UInt16_box);
                case "UInt32" -> code.invokestatic(CD_UInt32, "$box", MD_UInt32_box);
                default       -> throw new IllegalStateException();
            }
            break;

        default:
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Compute the TypeKind based on the ClassDesc.
     */
    public static TypeKind toTypeKind(ClassDesc cd) {
        return switch (cd.descriptorString()) {
            case "B" -> TypeKind.BYTE;
            case "C" -> TypeKind.CHAR;
            case "D" -> TypeKind.DOUBLE;
            case "F" -> TypeKind.FLOAT;
            case "I" -> TypeKind.INT;
            case "J" -> TypeKind.LONG;
            case "S" -> TypeKind.SHORT;
            case "Z" -> TypeKind.BOOLEAN;
            case "V" -> TypeKind.VOID;
            default  -> TypeKind.REFERENCE;
        };
    }

    /**
     * Generate a "load the return value from the context" for the specified Java class. Out: The
     * loaded value is at the java stack top.
     *
     * @param returnIndex the index of the value in the Ctx object
     */
    public static void loadFromContext(CodeBuilder code, ClassDesc cd, int returnIndex) {
        assert returnIndex >= 0;

        code.aload(code.parameterSlot(0));

        if (cd.isPrimitive()) {
            if (returnIndex < 8) {
                code // r = $ctx.i"returnIndex"
                    .getfield(CD_Ctx, "i" + (returnIndex), CD_long);
            } else {
                code // r = $ctx.iN[returnIndex-8]
                    .getfield(CD_Ctx, "iN", CD_long.arrayType())
                    .loadConstant(returnIndex-8)
                    .aaload();
            }

            // convert the long to the corresponding Java primitive
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.l2i();
                break;
            case "J":
                break;
            case "F":
                code.l2f();
                break;
            case "D":
                code.l2d();
                break;
            default:
                throw new IllegalStateException();
            }
        } else {
            if (returnIndex < 8) {
                code // r = $ctx.o"returnIndex"
                    .getfield(CD_Ctx, "o" + (returnIndex-1), CD_Object);
            } else {
                code // r = $ctx.oN[returnIndex-8]
                    .getfield(CD_Ctx, "oN", CD_Object.arrayType())
                    .loadConstant(returnIndex-8)
                    .aaload();
            }
        }
    }

    /**
     * Generate a "store the return value to the context" for the specified Java class.
     * In: The value to store is at the java stack top.
     */
    public static void storeToContext(CodeBuilder code, ClassDesc cd, int returnIndex) {
        assert returnIndex >= 0;

        code.aload(code.parameterSlot(0));
        if (cd.isPrimitive() && cd.descriptorString().equals("J")) {
             // the value is a "long" that occupies two slots
             // stack (lvalue, lvalue2, $ctx) -> ($ctx, lvalue, lvalue2)
            code.dup_x2().pop();
        } else {
            // stack (value, $ctx) -> ($ctx, value)
            code.swap();
        }

        if (cd.isPrimitive()) {
            // all primitives are stored into "long" fields; convert
            switch (cd.descriptorString()) {
            case "I", "S", "B", "C", "Z":
                code.i2l();
                break;
            case "J":
                break;
            case "F":
                code.f2l();
                break;
            case "D":
                code.d2l();
                break;
            default:
                throw new IllegalStateException();
            }

            if (returnIndex < 8) {
                code // $ctx.i"returnIndex" = r
                    .putfield(CD_Ctx, "i" + returnIndex, CD_long);
            } else {
                // TODO: replace with a helper "Ctx.storeLong(i-8, value)"
                code // $ctx.iN[returnIndex-8] = r
                    .getfield(CD_Ctx, "iN", CD_long.arrayType())
                    .loadConstant(returnIndex-8)
                    .aastore();
            }
        } else {
            if (returnIndex < 8) {
                code // $ctx.o"returnIndex" = r
                    .putfield(CD_Ctx, "o" + (returnIndex), CD_Object);
            } else {
                // TODO: replace with a helper "Ctx.storeRef(i-8, value)"
                code // $ctx.oN[returnIndex-8] = r
                    .getfield(CD_Ctx, "oN", CD_Object.arrayType())
                    .loadConstant(returnIndex-8)
                    .aastore();
            }
        }
    }

    /**
     * Convert the "void construct$0(...)" to "This new$0(...)"
     */
    public static JitMethodDesc convertConstructToNew(TypeInfo typeInfo, String className,
                                                      JitMethodDesc jmdCtor) {
        JitParamDesc retDesc = new JitParamDesc(typeInfo.getType(), Specific,
                ClassDesc.of(className), 0, -1, false);

        JitParamDesc[] standardReturns  = new JitParamDesc[] {retDesc};
        JitParamDesc[] optimizedReturns = jmdCtor.isOptimized ? standardReturns : null;
        return new JitMethodDesc(standardReturns,  jmdCtor.standardParams,
                                 optimizedReturns, jmdCtor.optimizedParams);
    }

    /**
     * For a given class name create a name for an associated class of the specified shape.
     */
    public static String getShapeName(String className, ClassfileShape shape) {
        int simpleOffset = className.lastIndexOf('.');
        assert simpleOffset > 0;
        String simpleName = className.substring(simpleOffset + 1);
        return className.substring(0, simpleOffset + 1) + shape.prefix + simpleName;
    }

    /**
     * For a given class name create a ClassDesc for an associated class of the specified shape.
     */
    public static ClassDesc getShapeDesc(String className, ClassfileShape shape) {
        return ClassDesc.of(getShapeName(className, shape));
    }

    // ----- TEMPORARY: debugging support ----------------------------------------------------------

    /**
     * Adds a log message generation (this also allows to break in the debugger).
     */
    public static void addLog(CodeBuilder code, String message) {
        code.invokestatic(CD_Ctx, "get", MethodTypeDesc.of(CD_Ctx))
            .loadConstant(message)
            .invokevirtual(Builder.CD_Ctx, "log", MethodTypeDesc.of(CD_void, CD_JavaString));
    }

    // ----- native class names --------------------------------------------------------------------

    public static final String N_Boolean      = "org.xtclang.ecstasy.Boolean";
    public static final String N_Char         = "org.xtclang.ecstasy.text.Char";
    public static final String N_Exception    = "org.xtclang.ecstasy.Exception";
    public static final String N_Int8         = "org.xtclang.ecstasy.numbers.Int8";
    public static final String N_Int16        = "org.xtclang.ecstasy.numbers.Int16";
    public static final String N_Int32        = "org.xtclang.ecstasy.numbers.Int32";
    public static final String N_Int64        = "org.xtclang.ecstasy.numbers.Int64";
    public static final String N_Nullable     = "org.xtclang.ecstasy.Nullable";
    public static final String N_Object       = "org.xtclang.ecstasy.Object";
    public static final String N_Ordered      = "org.xtclang.ecstasy.Ordered";
    public static final String N_String       = "org.xtclang.ecstasy.text.String";
    public static final String N_UInt8        = "org.xtclang.ecstasy.numbers.UInt8";
    public static final String N_UInt16       = "org.xtclang.ecstasy.numbers.UInt16";
    public static final String N_UInt32       = "org.xtclang.ecstasy.numbers.UInt32";
    public static final String N_UInt64       = "org.xtclang.ecstasy.numbers.UInt64";

    public static final String N_xClass       = "org.xtclang.ecstasy.xClass";
    public static final String N_xConst       = "org.xtclang.ecstasy.xConst";
    public static final String N_xEnum        = "org.xtclang.ecstasy.xEnum";
    public static final String N_xEnumeration = "org.xtclang.ecstasy.reflect.Enumeration";
    public static final String N_xException   = "org.xtclang.ecstasy.xException";
    public static final String N_xFunction    = "org.xtclang.ecstasy.xFunction";
    public static final String N_xModule      = "org.xtclang.ecstasy.xModule";
    public static final String N_xObj         = "org.xtclang.ecstasy.xObj";
    public static final String N_xService     = "org.xtclang.ecstasy.xService";
    public static final String N_xType        = "org.xtclang.ecstasy.xType";

    // ----- well-known suffixes -------------------------------------------------------------------

    public static final String EXT         = "$ext";          // a multi-slot extension field of a primitive field
    public static final String ENUMERATION = "$Enumeration";  // a class that represents an Enumeration class
    public static final String INIT        = "$init";         // the singleton initialization instance method
    public static final String MODULE      = "$module";       // the main module class name
    public static final String NEW         = "$new";          // the instance creation static method
    public static final String OPT         = "$p";            // methods that contains primitive types

    // ----- well-known class descriptors ----------------------------------------------------------

    public static final ClassDesc CD_Exception     = ClassDesc.of(N_Exception);
    public static final ClassDesc CD_xFunction     = ClassDesc.of(N_xFunction);
    public static final ClassDesc CD_xModule       = ClassDesc.of(N_xModule);

    public static final ClassDesc CD_xEnum         = ClassDesc.of(N_xEnum);
    public static final ClassDesc CD_xEnumeration  = ClassDesc.of(N_xEnumeration);
    public static final ClassDesc CD_xException    = ClassDesc.of(N_xException);
    public static final ClassDesc CD_xObj          = ClassDesc.of(N_xObj);
    public static final ClassDesc CD_xType         = ClassDesc.of(N_xType);

    public static final ClassDesc CD_Boolean       = ClassDesc.of(N_Boolean);
    public static final ClassDesc CD_Char          = ClassDesc.of(N_Char);
    public static final ClassDesc CD_Int8          = ClassDesc.of(N_Int8);
    public static final ClassDesc CD_Int16         = ClassDesc.of(N_Int16);
    public static final ClassDesc CD_Int32         = ClassDesc.of(N_Int32);
    public static final ClassDesc CD_Int64         = ClassDesc.of(N_Int64);
    public static final ClassDesc CD_Nullable      = ClassDesc.of(N_Nullable);
    public static final ClassDesc CD_Object        = ClassDesc.of(N_Object);
    public static final ClassDesc CD_Ordered       = ClassDesc.of(N_Ordered);
    public static final ClassDesc CD_String        = ClassDesc.of(N_String);
    public static final ClassDesc CD_UInt8         = ClassDesc.of(N_UInt8);
    public static final ClassDesc CD_UInt16        = ClassDesc.of(N_UInt16);
    public static final ClassDesc CD_UInt32        = ClassDesc.of(N_UInt32);
    public static final ClassDesc CD_UInt64        = ClassDesc.of(N_UInt64);

    public static final ClassDesc CD_xClass        = ClassDesc.of(N_xClass);
    public static final ClassDesc CD_xConst        = ClassDesc.of(N_xConst);
    public static final ClassDesc CD_Container     = ClassDesc.of(Container.class.getName());
    public static final ClassDesc CD_Ctx           = ClassDesc.of(Ctx.class.getName());
    public static final ClassDesc CD_CtorCtx       = ClassDesc.of(Ctx.CtorCtx.class.getName());
    public static final ClassDesc CD_TypeConstant  = ClassDesc.of(TypeConstant.class.getName());
    public static final ClassDesc CD_TypeSystem    = ClassDesc.of(TypeSystem.class.getName());

    public static final ClassDesc CD_JavaString    = ClassDesc.of(java.lang.String.class.getName());
    public static final ClassDesc CD_JavaObject    = ClassDesc.of(java.lang.Object.class.getName());

    // ----- well-known methods --------------------------------------------------------------------

    public static final String         Instance       = "$INSTANCE";
    public static final MethodTypeDesc MD_Boolean_box = MethodTypeDesc.of(CD_Boolean, CD_boolean);
    public static final MethodTypeDesc MD_Char_box    = MethodTypeDesc.of(CD_Char,   CD_int);
    public static final MethodTypeDesc MD_Int8_box    = MethodTypeDesc.of(CD_Int8,   CD_int);
    public static final MethodTypeDesc MD_Int16_box   = MethodTypeDesc.of(CD_Int16,  CD_int);
    public static final MethodTypeDesc MD_Int32_box   = MethodTypeDesc.of(CD_Int32,  CD_int);
    public static final MethodTypeDesc MD_Int64_box   = MethodTypeDesc.of(CD_Int64,  CD_long);
    public static final MethodTypeDesc MD_UInt8_box   = MethodTypeDesc.of(CD_UInt8,  CD_int);
    public static final MethodTypeDesc MD_UInt16_box  = MethodTypeDesc.of(CD_UInt16, CD_int);
    public static final MethodTypeDesc MD_UInt32_box  = MethodTypeDesc.of(CD_UInt32, CD_int);
    public static final MethodTypeDesc MD_UInt64_box  = MethodTypeDesc.of(CD_UInt64, CD_long);
    public static final MethodTypeDesc MD_Initializer = MethodTypeDesc.of(CD_void,   CD_Ctx);
    public static final MethodTypeDesc MD_StringOf    = MethodTypeDesc.of(CD_String, CD_Ctx, CD_JavaString);
}
