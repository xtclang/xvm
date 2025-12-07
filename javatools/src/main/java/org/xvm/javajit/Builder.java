package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.EnumValueConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext.SingleSlot;
import org.xvm.javajit.BuildContext.DoubleSlot;
import org.xvm.javajit.TypeSystem.ClassfileShape;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.JitFlavor.MultiSlotPrimitive;
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

    /**
     * @return TypeConstant for "this" type
     */
    protected TypeConstant getThisType() {
        throw new UnsupportedOperationException();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(CodeBuilder code, Constant constant) {
        return loadConstant(null, code, constant);
    }

    /**
     * Build the code to load a value for a constant on the Java stack.
     *
     * We **always** load a primitive value if possible.
     */
    public RegisterInfo loadConstant(BuildContext bctx, CodeBuilder code, Constant constant) {
        // see NativeContainer#getConstType()

        switch (constant) {
        case StringConstant stringConst:
            loadString(code, stringConst.getValue());
            return new SingleSlot(stringConst.getType(), CD_String, "");

        case IntConstant intConstant:// TODO: support all Int/UInt types
            code.ldc(intConstant.getValue().getLong());
            return new SingleSlot(constant.getType(), CD_long, "");

        case ByteConstant byteConstant:
            code.ldc(byteConstant.getValue().intValue());
            return new SingleSlot(constant.getType(), CD_int, "");

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

        case SingletonConstant singleton: {
            if (singleton instanceof EnumValueConstant enumConstant) {
                ConstantPool pool = constant.getConstantPool();
                if (enumConstant.getType().isOnlyNullable()) {
                    Builder.loadNull(code);
                    return new SingleSlot(pool.typeNullable(), CD_Nullable, "");
                }
                else if (enumConstant.getType().isA(pool.typeBoolean())) {
                    if (enumConstant.getIntValue().getInt() == 0) {
                        code.iconst_0();
                    }
                    else {
                        code.iconst_1();
                    }
                    return new SingleSlot(pool.typeBoolean(), CD_boolean, "");
                }
            }

            TypeConstant type = singleton.getType();
            JitTypeDesc  jtd  = type.getJitDesc(typeSystem);
            assert jtd.flavor == Specific;

            // retrieve from Singleton.$INSTANCE (see CommonBuilder.assembleStaticInitializer)
            ClassDesc cd = jtd.cd;
            code.getstatic(cd, Instance, cd);
            return new SingleSlot(type, cd, "");
        }

        case CharConstant ch:
            code.loadConstant(ch.getValue());
            return new SingleSlot(constant.getConstantPool().typeChar(), CD_char, "");

        case NamedCondition cond:
            code.loadConstant(cond.getName());
            return new SingleSlot(cond.getConstantPool().typeString(), CD_String, "");

        case TypeConstant type:
            Builder.loadTypeConstant(code, typeSystem, type);
            return new SingleSlot(type.getType(), CD_TypeConstant, "");

        case PropertyConstant propId: {
            // support for the "local property" mode
            code.aload(0);
            JitMethodDesc jmd = loadProperty(code, getThisType(), propId);

            TypeConstant type = jmd.isOptimized
                    ? jmd.optimizedReturns[0].type
                    : jmd.standardReturns[0].type;
            JitTypeDesc jtd = type.getJitDesc(typeSystem);
            if (jtd.flavor == MultiSlotPrimitive) {
                throw new UnsupportedOperationException("TODO multislot property");
            }
            return new SingleSlot(type, jtd.cd, "");
        }

        case MethodConstant methodId: {
            if (bctx == null) {
                throw new IllegalStateException("Context is missing");
            }

            // 1) ensure the method exists
            String     jitName = methodId.ensureJitMethodName(typeSystem);
            MethodBody body;
            if (methodId.isLambda()) {
                // generate the method itself
                MethodStructure lambda = (MethodStructure) methodId.getComponent();
                jitName = jitName.replace("->", LAMBDA);
                body    = new MethodBody(lambda);
                bctx.buildMethod(jitName, body);
            } else {
                MethodInfo method = bctx.typeInfo.getMethodById(methodId);
                body = method.getHead();
                if (body.getIdentity().getNestedDepth() > 2) {
                    // methods nested inside properties or methods are not visible otherwise
                    // and need to built on-the-spot
                    bctx.buildMethod(jitName, body);
                }
            }

            // 2) create the MethodHandle(s)
            TypeConstant  containerType = bctx.typeInfo.getType();
            ClassDesc     containerCD   = ClassDesc.of(bctx.className);
            JitMethodDesc jmd           = body.getJitDesc(typeSystem, containerType);
            boolean       isFunction    = body.getMethodStructure().isFunction();

            DirectMethodHandleDesc.Kind kind = isFunction
                    ? DirectMethodHandleDesc.Kind.STATIC
                    : DirectMethodHandleDesc.Kind.VIRTUAL;

            DirectMethodHandleDesc stdMD = MethodHandleDesc.ofMethod(kind,
                    containerCD, jitName, jmd.standardMD);

            DirectMethodHandleDesc optMD = jmd.isOptimized
                    ? MethodHandleDesc.ofMethod(kind, containerCD, jitName+OPT, jmd.optimizedMD)
                    : null;

            TypeConstant type = body.getIdentity().getType();
            if (isFunction) {
                // 3) instantiate a function object
                //      new FunctionN(ctx, stdHandle, optHandle, immutable);
                assert type.isFunction();

                ClassDesc cd = CD_nFunction;
                code.new_(cd)
                    .dup()
                    .aload(code.parameterSlot(0)); // ctx
                loadTypeConstant(code, typeSystem, type);
                code.ldc(stdMD);
                if (optMD == null) {
                    code.aconst_null();
                } else {
                    code.ldc(optMD);
                }
                code.iconst_1() // immutable = true
                    .invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                        CD_MethodHandle, CD_MethodHandle, CD_boolean));
                return new SingleSlot(type, cd, "");
            } else {
                // 3) instantiate an nMethod object
                //      new nMethod(ctx, type, stdHandle, optHandle);

                assert type.isMethod();

                ClassDesc cd = CD_nMethod;
                code.new_(cd)
                    .dup()
                    .aload(code.parameterSlot(0)); // ctx
                loadTypeConstant(code, typeSystem, type);
                code.ldc(stdMD);
                if (optMD == null) {
                    code.aconst_null();
                } else {
                    code.ldc(optMD);
                }
                code.invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                        CD_MethodHandle, CD_MethodHandle));
                return new SingleSlot(type, cd, "");
            }
        }

        default:
            break;
        }

        throw new UnsupportedOperationException(constant.toString());
    }

    /**
     * Build the code to load an XTC String value on the Java stack.
     */
    public static void loadString(CodeBuilder code, String value) {
        code.aload(code.parameterSlot(0)) // $ctx
            .ldc(value)
            .invokestatic(CD_String, "of", MD_StringOf);
    }

    /**
     * Build the code to load a local property on the Java stack.
     *
     * This method assumes the "owner" ref is loaded on Java stack.
     *
     * @return the JitMethodDesc for the "get" method
     */
    public JitMethodDesc loadProperty(CodeBuilder code, TypeConstant typeContainer,
                                      PropertyConstant propId) {
        PropertyInfo     propInfo   = propId.getPropertyInfo(typeContainer);
        ClassDesc        cdOwner    = propInfo.getOwnerClassDesc(typeSystem, typeContainer);
        JitMethodDesc    jmdGet     = propInfo.getGetterJitDesc(typeSystem);
        String           getterName = propInfo.ensureGetterJitMethodName(typeSystem);

        MethodTypeDesc md;
        if (jmdGet.isOptimized) {
            md         = jmdGet.optimizedMD;
            getterName += Builder.OPT;
        } else {
            md = jmdGet.standardMD;
        }

        code.aload(code.parameterSlot(0)); // $ctx
        code.invokevirtual(cdOwner, getterName, md);
        return jmdGet;
    }

    /**
     * Generate a value "load" for the specified register. If the register is a {@link DoubleSlot},
     * load the "extension" boolean flag first.
     */
    public static void load(CodeBuilder code, RegisterInfo reg) {
        if (reg instanceof DoubleSlot doubleSlot) {
            code.iload(doubleSlot.extSlot());
        }
        load(code, reg.cd(), reg.slot());
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
     * Generate a value "store" for the specified register.  If the register is a {@link DoubleSlot},
     * store the "extension" boolean flag first.
     */
    public static void store(CodeBuilder code, RegisterInfo reg) {
        if (reg instanceof DoubleSlot doubleSlot) {
            code.istore(doubleSlot.extSlot());
        }
        store(code, reg.cd(), reg.slot());
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
     * Generate a "Null" check for the specified register.
     *
     * @param lblNotNull  the label to jump to if the register is "Null" .
     */
    public static void checkNull(CodeBuilder code, RegisterInfo reg, Label lblNull) {
        if (reg instanceof DoubleSlot doubleSlot) {
            assert reg.cd().isPrimitive();

            code.iload(doubleSlot.extSlot())
                .if_icmpne(lblNull);
        } else {
            assert !reg.cd().isPrimitive();

            code.aload(reg.slot());
            loadNull(code);
            code.if_acmpeq(lblNull);
        }
    }

    /**
     * Generate a "not Null" check for the specified register.
     *
     * @param lblNotNull  the label to jump to if the register is "not Null".
     */
    public static void checkNotNull(CodeBuilder code, RegisterInfo reg, Label lblNotNull) {
        if (reg instanceof DoubleSlot doubleSlot) {
            assert reg.cd().isPrimitive();

            code.iload(doubleSlot.extSlot())
                .if_icmpeq(lblNotNull);
        } else {
            assert !reg.cd().isPrimitive();

            code.aload(reg.slot());
            loadNull(code);
            code.if_acmpne(lblNotNull);
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
     * Out: TypeConstant
     */
    public static void loadTypeConstant(CodeBuilder code, TypeSystem ts, TypeConstant type) {
        code.aload(code.parameterSlot(0)) // $ctx
            .loadConstant(ts.registerConstant(type))
            .invokevirtual(CD_Ctx, "getConstant", Ctx.MD_getConstant) // <- const
            .checkcast(CD_TypeConstant);                              // <- type
    }

    /**
     * Generate a "load" for an nType object for the specified TypeConstant.
     * Out: xType instance
     */
    public static void loadType(CodeBuilder code, TypeSystem ts, TypeConstant type) {
        code.aload(code.parameterSlot(0)); // ctx
        loadTypeConstant(code, ts, type);
        code.invokestatic(CD_nType, "$ensureType",
                          MethodTypeDesc.of(CD_nType, CD_Ctx, CD_TypeConstant));
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
     * Call the default constructor for the target class.
     *
     * @param cd the target ClassDesc
     */
    public static void invokeDefaultConstructor(CodeBuilder code, ClassDesc cd) {
        code.new_(cd)
            .dup()
            .aload(code.parameterSlot(0)) // ctx
            .invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx));
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
     * Generate unboxing opcodes for a wrapper reference on the Java stack.
     *
     * In: a boxed XVM reference
     * Out: the unboxed primitive value
     *
     * @param reg  the RegisterInfo for the unboxed value
     */
    public static void unbox(CodeBuilder code, RegisterInfo reg) {
        unbox(code, reg.type(), reg.cd());
    }

    /**
     * Generate unboxing opcodes for a wrapper reference on the Java stack.
     *
     * In: a boxed XVM reference
     * Out: the unboxed primitive value
     *
     * @param type  the primitive type for the boxed value
     * @param cd    the corresponding ClassDesc to unbox to
     */
    public static void unbox(CodeBuilder code, TypeConstant type, ClassDesc cd) {
        assert cd.isPrimitive() && type.isPrimitive();

        switch (cd.descriptorString()) {
        case "Z": // boolean
            assert type.equals(type.getConstantPool().typeBoolean());
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
                case "Char"   -> code.getfield(CD_Char,   "$value", cd);
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
     * In: an unboxed primitive value
     * Out: the boxed XVM reference
     */
    public static void box(CodeBuilder code, TypeConstant type, ClassDesc cd) {
        assert cd.isPrimitive() && type.isPrimitive();

        switch (cd.descriptorString()) {
        case "Z": // boolean
            assert type.equals(type.getConstantPool().typeBoolean());
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
     * Generate Java boxing opcodes for a primitive value of the specified primitive class on the
     * stack.
     *
     * In: an unboxed primitive value
     * Out: the boxed Java reference
     */
    public static void boxJava(CodeBuilder code, ClassDesc cd) {
        assert cd.isPrimitive();

        switch (cd.descriptorString()) {
        case "Z": // boolean
            code.invokestatic(CD_JavaBoolean, "valueOf", MethodTypeDesc.of(CD_JavaBoolean, CD_boolean));
            break;

        case "J": // long
            code.invokestatic(CD_JavaLong, "valueOf", MethodTypeDesc.of(CD_JavaLong, CD_long));
            break;

        case "I": // int
            code.invokestatic(CD_JavaInteger, "valueOf", MethodTypeDesc.of(CD_JavaInteger, CD_int));
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

        code.aload(code.parameterSlot(0)); // $ctx

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

        code.aload(code.parameterSlot(0)); // $ctx
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
     * Convert the "void construct$17(...)" to "This new$17(...)"
     */
    public static JitMethodDesc convertConstructToNew(TypeInfo typeInfo, String className,
                                                      JitCtorDesc jmdCtor) {
        JitParamDesc retDesc = new JitParamDesc(typeInfo.getType(), Specific,
                ClassDesc.of(className), 0, -1, false);

        JitParamDesc[] standardReturns  = new JitParamDesc[] {retDesc};
        JitParamDesc[] optimizedReturns = jmdCtor.isOptimized ? standardReturns : null;
        return typeInfo.hasGenericTypes()
            ? new JitCtorDesc(null, /*addCtorCtx*/ false, /*addType*/ true,
                    standardReturns,  jmdCtor.standardParams,
                    optimizedReturns, jmdCtor.optimizedParams)
            : new JitMethodDesc(
                    standardReturns,  jmdCtor.standardParams,
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

    public static final String N_Array        = "org.xtclang.ecstasy.collections.Array";
    public static final String N_Boolean      = "org.xtclang.ecstasy.Boolean";
    public static final String N_Char         = "org.xtclang.ecstasy.text.Char";
    public static final String N_Class        = "org.xtclang.ecstasy.reflect.Class";
    public static final String N_Enumeration  = "org.xtclang.ecstasy.reflect.Enumeration";
    public static final String N_Exception    = "org.xtclang.ecstasy.Exception";
    public static final String N_Int8         = "org.xtclang.ecstasy.numbers.Int8";
    public static final String N_Int16        = "org.xtclang.ecstasy.numbers.Int16";
    public static final String N_Int32        = "org.xtclang.ecstasy.numbers.Int32";
    public static final String N_Int64        = "org.xtclang.ecstasy.numbers.Int64";
    public static final String N_Nullable     = "org.xtclang.ecstasy.Nullable";
    public static final String N_Object       = "org.xtclang.ecstasy.Object";
    public static final String N_Ordered      = "org.xtclang.ecstasy.Ordered";
    public static final String N_String       = "org.xtclang.ecstasy.text.String";
    public static final String N_TypeMismatch = "org.xtclang.ecstasy.TypeMismatch";
    public static final String N_UInt8        = "org.xtclang.ecstasy.numbers.UInt8";
    public static final String N_UInt16       = "org.xtclang.ecstasy.numbers.UInt16";
    public static final String N_UInt32       = "org.xtclang.ecstasy.numbers.UInt32";
    public static final String N_UInt64       = "org.xtclang.ecstasy.numbers.UInt64";

    public static final String N_nArrayChar   = "org.xtclang.ecstasy.collections.nArrayᐸCharᐳ";
    public static final String N_nArrayObj    = "org.xtclang.ecstasy.collections.nArrayᐸObjectᐳ";
    public static final String N_nConst       = "org.xtclang.ecstasy.nConst";
    public static final String N_nEnum        = "org.xtclang.ecstasy.nEnum";
    public static final String N_nException   = "org.xtclang.ecstasy.nException";
    public static final String N_nFunction    = "org.xtclang.ecstasy.nFunction";
    public static final String N_nMethod      = "org.xtclang.ecstasy.nMethod";
    public static final String N_nModule      = "org.xtclang.ecstasy.nModule";
    public static final String N_nObj         = "org.xtclang.ecstasy.nObj";
    public static final String N_nService     = "org.xtclang.ecstasy.nService";
    public static final String N_nType        = "org.xtclang.ecstasy.nType";

    // ----- well-known suffixes -------------------------------------------------------------------

    public static final String MODULE         = "¤module"; // the main module class name
    public static final String LAMBDA         = "lambda¤"; // the base of the lambda function name
    public static final String EXT            = "$ext";    // a multi-slot extension field of a primitive field
    public static final String INIT           = "$init";   // the singleton initialization instance method
    public static final String NEW            = "$new";    // the instance creation static method
    public static final String OPT            = "$p";      // methods that contains primitive types

    // ----- well-known class descriptors ----------------------------------------------------------

    public static final ClassDesc CD_Array         = ClassDesc.of(N_Array);
    public static final ClassDesc CD_Class         = ClassDesc.of(N_Class);
    public static final ClassDesc CD_Enumeration   = ClassDesc.of(N_Enumeration);
    public static final ClassDesc CD_Exception     = ClassDesc.of(N_Exception);
    public static final ClassDesc CD_nFunction     = ClassDesc.of(N_nFunction);
    public static final ClassDesc CD_nMethod       = ClassDesc.of(N_nMethod);
    public static final ClassDesc CD_nModule       = ClassDesc.of(N_nModule);

    public static final ClassDesc CD_nArrayChar    = ClassDesc.of(N_nArrayChar);
    public static final ClassDesc CD_nArrayObj     = ClassDesc.of(N_nArrayObj);
    public static final ClassDesc CD_nEnum         = ClassDesc.of(N_nEnum);
    public static final ClassDesc CD_nException    = ClassDesc.of(N_nException);
    public static final ClassDesc CD_nObj          = ClassDesc.of(N_nObj);
    public static final ClassDesc CD_nType         = ClassDesc.of(N_nType);

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

    public static final ClassDesc CD_nConst        = ClassDesc.of(N_nConst);
    public static final ClassDesc CD_Container     = ClassDesc.of(Container.class.getName());
    public static final ClassDesc CD_Ctx           = ClassDesc.of(Ctx.class.getName());
    public static final ClassDesc CD_CtorCtx       = ClassDesc.of(Ctx.CtorCtx.class.getName());
    public static final ClassDesc CD_TypeConstant  = ClassDesc.of(TypeConstant.class.getName());
    public static final ClassDesc CD_TypeSystem    = ClassDesc.of(TypeSystem.class.getName());

    public static final ClassDesc CD_JavaBoolean   = ClassDesc.of(java.lang.Boolean.class.getName());
    public static final ClassDesc CD_JavaInteger   = ClassDesc.of(java.lang.Integer.class.getName());
    public static final ClassDesc CD_JavaLong      = ClassDesc.of(java.lang.Long.class.getName());
    public static final ClassDesc CD_JavaObject    = ClassDesc.of(java.lang.Object.class.getName());
    public static final ClassDesc CD_JavaString    = ClassDesc.of(java.lang.String.class.getName());

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
    public static final MethodTypeDesc MD_xvmType     = MethodTypeDesc.of(CD_TypeConstant, CD_Ctx);
    public static final MethodTypeDesc MD_TypeIsA     = MethodTypeDesc.of(CD_boolean, CD_TypeConstant);
}
