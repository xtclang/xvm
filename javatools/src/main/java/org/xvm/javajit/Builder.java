package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import java.math.BigInteger;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.*;

import org.xvm.javajit.TypeSystem.ClassfileShape;

import org.xvm.javajit.registers.ExtendedSlot;
import org.xvm.javajit.registers.MultiSlot;
import org.xvm.javajit.registers.SingleSlot;

import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;

import org.xvm.util.PackedInteger;

import static java.lang.constant.ConstantDescs.CD_Double;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.asm.Constant.Format.Int128;

import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.XvmPrimitive;
import static org.xvm.javajit.JitFlavor.NullablePrimitive;
import static org.xvm.javajit.JitFlavor.Primitive;
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

    /**
     * Generate a "load" for the specified TypeConstant.
     * Out: TypeConstant on Java stack
     */
    protected void loadTypeConstant(CodeBuilder code, String className, TypeConstant type) {
        throw new UnsupportedOperationException();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return the ConstantPool used by this {@link Builder}.
     */
    public ConstantPool pool() {
        return typeSystem.pool();
    }

    /**
     * Ensure a unique ClassDesc for the specified type.
     */
    public ClassDesc ensureClassDesc(TypeConstant type) {
        return type.ensureClassDesc(typeSystem);
    }

    /**
     * Ensure a unique Java class name for the specified type.
     */
    public String ensureJitClassName(TypeConstant type) {
        return type.ensureJitClassName(typeSystem);
    }

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
            return new SingleSlot(stringConst.getType(), Specific, CD_String, "");

        case IntConstant intConstant:
            return switch (intConstant.getFormat()) {
                case Int16, UInt16, Int32 -> {
                    code.ldc(intConstant.getValue().getInt());
                    yield new SingleSlot(constant.getType(), Primitive, CD_int, "");
                }
                case UInt32 -> {
                    code.ldc(intConstant.getValue().getLong())
                        .ldc(0xFFFFFFFFL)
                        .land()
                        .l2i();
                    yield new SingleSlot(constant.getType(), Primitive, CD_int, "");
                }
                case Int64, UInt64 -> {
                    code.ldc(intConstant.getValue().getLong());
                    yield new SingleSlot(constant.getType(), Primitive, CD_long, "");
                }
                case Int128, UInt128 -> {
                    TypeConstant  type   = intConstant.getType();
                    PackedInteger packed = intConstant.getValue();
                    if (packed.isBig()) {
                        BigInteger big = packed.getBigInteger();
                        code.ldc(big.longValue());
                        code.ldc(big.shiftRight(64).longValue());
                    } else {
                        long n = packed.getLong();
                        code.ldc(n);
                        code.ldc(n < 0 ? -1L : 0L);
                    }
                    yield intConstant.getFormat() == Int128
                            ? new MultiSlot(bctx, XvmPrimitive, type, CD_Int128, CDs_LongLong)
                            : new MultiSlot(bctx, XvmPrimitive, type, CD_UInt128, CDs_LongLong);
                }
                default ->
                    throw new IllegalStateException("Unsupported IntConstant type "
                            + intConstant.getFormat());
            };

        case DecimalConstant decConstant:
            return switch (decConstant.getFormat()) {
                case Dec32 -> {
                    TypeConstant type = decConstant.getType();
                    Decimal32    dec  = (Decimal32) decConstant.getValue();
                    code.ldc(dec.toIntBits());
                    yield new MultiSlot(bctx, XvmPrimitive, type, CD_Dec32, CDs_Int);
                }
                case Dec64 -> {
                    TypeConstant type = decConstant.getType();
                    Decimal64    dec  = (Decimal64) decConstant.getValue();
                    code.ldc(dec.toLongBits());
                    yield new MultiSlot(bctx, XvmPrimitive, type, CD_Dec64, CDs_Long);
                }
                case Dec128 -> {
                    TypeConstant type = decConstant.getType();
                    Decimal128   dec  = (Decimal128) decConstant.getValue();
                    code.ldc(dec.getLowBits());
                    code.ldc(dec.getHighBits());
                    yield new MultiSlot(bctx, XvmPrimitive, type, CD_Dec128, CDs_LongLong);
                }
                default ->
                    throw new IllegalStateException("Unsupported IntConstant type "
                            + decConstant.getFormat());
            };

        case FloatConstant floatConstant:
            return switch (floatConstant.getFormat()) {
                case Float32 -> {
                    code.loadConstant(floatConstant.getValue().floatValue());
                    yield new SingleSlot(constant.getType(), Primitive, CD_float, "");
                }
                default ->
                        throw new IllegalStateException("Unsupported FloatConstant type "
                                + floatConstant.getFormat());
            };

        case Float64Constant float64Constant:
            return switch (float64Constant.getFormat()) {
                case Float64 -> {
                    code.loadConstant(float64Constant.getValue().doubleValue());
                    yield new SingleSlot(constant.getType(), Primitive, CD_double, "");
                }
                default ->
                        throw new IllegalStateException("Unsupported Float64Constant type "
                                + float64Constant.getFormat());
            };

        case ByteConstant byteConstant:
            code.ldc(byteConstant.getValue());
            return new SingleSlot(constant.getType(), Primitive, CD_int, "");

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
                    return new SingleSlot(pool.typeNullable(), Specific, CD_Nullable, "");
                }
                else if (enumConstant.getType().isA(pool.typeBoolean())) {
                    if (enumConstant.getIntValue().getInt() == 0) {
                        code.iconst_0();
                    }
                    else {
                        code.iconst_1();
                    }
                    return new SingleSlot(pool.typeBoolean(), Primitive, CD_boolean, "");
                }
            }

            TypeConstant type = singleton.getType();
            JitTypeDesc  jtd  = type.getJitDesc(this);
            assert jtd.flavor == Specific;

            // retrieve from Singleton.$INSTANCE (see CommonBuilder.assembleStaticInitializer)
            ClassDesc cd = jtd.cd;
            code.getstatic(cd, Instance, cd);
            return new SingleSlot(type, Specific, cd, "");
        }

        case CharConstant ch:
            code.loadConstant(ch.getValue());
            return new SingleSlot(constant.getConstantPool().typeChar(), Primitive, CD_int, "");

        case TypeConstant type:
            assert type.isTypeOfType();
            return bctx.loadType(code, type);

        case ClassConstant _:
        case DecoratedClassConstant _:
            IdentityConstant constId = (IdentityConstant) constant;
            throw new UnsupportedOperationException("Load class " + constId.getValueType(bctx.pool(), null));

        case PropertyConstant propId: {
            // support for the "local property" mode
            code.aload(0);
            JitMethodDesc jmd = loadProperty(code, getThisType(), propId);

            TypeConstant type = jmd.isOptimized
                    ? jmd.optimizedReturns[0].type
                    : jmd.standardReturns[0].type;
            JitTypeDesc jtd = type.getJitDesc(this);
            if (jtd.flavor == NullablePrimitive) {
                throw new UnsupportedOperationException("TODO multislot property");
            }
            if (jtd.flavor == NullableXvmPrimitive) {
                throw new UnsupportedOperationException("TODO nullable XVM primitive property");
            }
            return new SingleSlot(type, jtd.flavor, jtd.cd, "");
        }

        case MethodConstant methodId: {
            if (bctx == null) {
                throw new IllegalStateException("Context is missing");
            }

            // 1) ensure the method exists
            String     jitName;
            MethodBody body;
            if (methodId.isLambda()) {
                // generate the method itself
                MethodStructure lambda = (MethodStructure) methodId.getComponent();
                jitName = methodId.ensureJitMethodName(typeSystem).replace("->", LAMBDA);
                body    = new MethodBody(lambda);
                bctx.buildMethod(jitName, body);
            } else {
                MethodInfo method = bctx.typeInfo.getMethodById(methodId);
                jitName = method.ensureJitMethodName(typeSystem);
                body    = method.getHead();
                if (body.getIdentity().getNestedDepth() > 2) {
                    // methods nested inside properties or methods are not visible otherwise
                    // and need to built on-the-spot
                    bctx.buildMethod(jitName, body);
                }
            }

            // 2) create the MethodHandle(s)
            TypeConstant  containerType = bctx.typeInfo.getType();
            ClassDesc     containerCD   = ClassDesc.of(bctx.className);
            JitMethodDesc jmd           = body.getJitDesc(this, containerType);
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
                bctx.loadTypeConstant(code, type);
                code.ldc(stdMD);
                if (optMD == null) {
                    code.aconst_null();
                } else {
                    code.ldc(optMD);
                }
                code.iconst_1() // immutable = true
                    .invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                        CD_MethodHandle, CD_MethodHandle, CD_boolean));
                return new SingleSlot(type, Specific, cd, "");
            } else {
                // 3) instantiate an nMethod object
                //      new nMethod(ctx, type, stdHandle, optHandle);

                assert type.isMethod();

                ClassDesc cd = CD_nMethod;
                code.new_(cd)
                    .dup()
                    .aload(code.parameterSlot(0)); // ctx
                bctx.loadTypeConstant(code, type);
                code.ldc(stdMD);
                if (optMD == null) {
                    code.aconst_null();
                } else {
                    code.ldc(optMD);
                }
                code.invokespecial(cd, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                        CD_MethodHandle, CD_MethodHandle));
                return new SingleSlot(type, Specific, cd, "");
            }
        }

        case ArrayConstant arrayConst: {
            TypeConstant arrayType = arrayConst.getType();
            Constant[]   values    = arrayConst.getValue();
            return loadArray(bctx, code, arrayType, values);
        }

        case MapConstant mapConstant: {
            ConstantPool pool     = pool();
            TypeConstant mapType  = mapConstant.getType();
            TypeConstant keyType  = mapType.getParamType(0);
            TypeConstant valType  = mapType.getParamType(1);
            TypeConstant keysType = pool.ensureArrayType(keyType);
            TypeConstant valsType = pool.ensureArrayType(valType);
            var          constMap = mapConstant.getValue();

            // actual implementation is the ListMap
            mapType = pool.ensureParameterizedTypeConstant(
                pool.ensureEcstasyTypeConstant("maps.ListMap"), keyType, valType);

            TypeInfo       typeInfo = mapType.ensureTypeInfo();
            MethodConstant ctorId   = typeInfo.findConstructor(keysType, valsType.freeze(), pool.typeBoolean());
            MethodInfo     ctorInfo = typeInfo.getMethodById(ctorId);
            JitCtorDesc    ctorJmd  = (JitCtorDesc) ctorInfo.getJitDesc(this, mapType);
            String         ctorName = ctorInfo.ensureJitMethodName(typeSystem).replace("construct", NEW);
            String         clzName  = ensureJitClassName(mapType);
            ClassDesc      mapCd    = ClassDesc.of(clzName);
            JitMethodDesc  newJmd   = convertConstructToNew(typeInfo, mapCd, ctorJmd);

            assert newJmd.isOptimized;

            // call construct(Key[] keys, Value[]? vals, Boolean inPlace = False)
            loadCtx(code);
            loadTypeConstant(code, clzName, mapType);
            loadArray(bctx, code, keysType, constMap.keySet().toArray(Constant.NO_CONSTS));
            loadArray(bctx, code, valsType, constMap.values().toArray(Constant.NO_CONSTS));

            code.iconst_1() // inPlace = True
                .iconst_0() // dflt = False
                .invokestatic(mapCd, ctorName+OPT, newJmd.optimizedMD);

            return new SingleSlot(mapType, Specific, mapCd, "");
        }

        case RangeConstant rangeConst: {
            TypeConstant   rangeType = rangeConst.getType();
            TypeConstant   elType    = rangeType.getParamType(0);
            Constant[]     values    = rangeConst.getValue();
            MethodTypeDesc mdNew;
            ClassDesc      cdRange;
            String         className;

            // TODO: how/where to cache the result ??
            if (elType.isJitPrimitive()) {
                switch (elType.getSingleUnderlyingClass(false).getName()) {
                    case "Int64":
                        cdRange   = CD_nRangeInt64;
                        className = N_nRangeInt64;
                        mdNew     = MethodTypeDesc.of(cdRange, CD_Ctx, CD_TypeConstant, CD_long,
                                        CD_long, CD_boolean, CD_boolean, CD_boolean, CD_boolean);
                        break;

                    default:
                        throw new UnsupportedOperationException("TODO");
                    }
            } else {
                // non-primitive range - must be Orderable and also maybe Sequential
                throw new UnsupportedOperationException("TODO");
            }

            loadCtx(code);
            loadTypeConstant(code, className, rangeType);
            loadConstant(bctx, code, values[0]);
            loadConstant(bctx, code, values[1]);
            code.loadConstant(rangeConst.isFirstExcluded() ? 1 : 0);
            code.iconst_0();
            code.loadConstant(rangeConst.isLastExcluded() ? 1 : 0);
            code.iconst_0();
            code.invokestatic(cdRange, "$new$p", mdNew);

            return new SingleSlot(rangeType, Specific, ensureClassDesc(rangeType), "");
        }

        default:
            break;
        }

        throw new UnsupportedOperationException(constant.toString());
    }

    private SingleSlot loadArray(BuildContext bctx, CodeBuilder code, TypeConstant arrayType,
                                 Constant[] values) {
        TypeConstant   elType = arrayType.getParamType(0);
        ClassDesc      cdArray;
        String         className;
        String         addMethod;
        MethodTypeDesc mdAdd;

        // TODO: how/where to cache the result
        if (elType.isJitPrimitive()) {
            addMethod = "add$p";

            switch (elType.getSingleUnderlyingClass(false).getName()) {
            case "Bit":
                // ArrayᐸBitᐳ array = ArrayᐸBitᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayBit;
                className = N_ArrayBit;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Char":
                // ArrayᐸCharᐳ array = ArrayᐸCharᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayChar;
                className = N_ArrayChar;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Dec32":
                // ArrayᐸDec32ᐳ array = ArrayᐸDec32ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayDec32;
                className = N_ArrayDec32;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Dec64":
                // ArrayᐸDec64ᐳ array = ArrayᐸDec64ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayDec64;
                className = N_ArrayDec64;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long);
                break;

            case "Dec128":
                // ArrayᐸDec128ᐳ array = ArrayᐸDec128ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayDec128;
                className = N_ArrayDec128;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long, CD_long);
                break;

            case "Float32":
                // ArrayᐸFloat32ᐳ array = ArrayᐸFloat32ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayFloat32;
                className = N_ArrayFloat32;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_float);
                break;

            case "Float64":
                // ArrayᐸFloat64ᐳ array = ArrayᐸFloat64ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayFloat64;
                className = N_ArrayFloat64;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_double);
                break;

            case "Int8":
                // ArrayᐸInt8ᐳ array = ArrayᐸInt8ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayInt8;
                className = N_ArrayInt8;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Int16":
                // ArrayᐸInt16ᐳ array = ArrayᐸInt16ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayInt16;
                className = N_ArrayInt16;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Int32":
                // ArrayᐸInt32ᐳ array = ArrayᐸInt32ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayInt32;
                className = N_ArrayInt32;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "Int64":
                // ArrayᐸIntᐳ array = ArrayᐸIntᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayInt64;
                className = N_ArrayInt64;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long);
                break;

            case "Int128":
                // ArrayᐸInt128ᐳ array = ArrayᐸInt128ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayInt128;
                className = N_ArrayInt128;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long, CD_long);
                break;

            case "Nibble":
                // ArrayᐸNibbleᐳ array = ArrayᐸNibbleᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayNibble;
                className = N_ArrayNibble;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "UInt8":
                // ArrayᐸUInt8ᐳ array = ArrayᐸUInt8ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayUInt8;
                className = N_ArrayUInt8;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "UInt16":
                // ArrayᐸUInt16ᐳ array = ArrayᐸUInt16ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayUInt16;
                className = N_ArrayUInt16;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "UInt32":
                // ArrayᐸUInt32ᐳ array = ArrayᐸUInt32ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayUInt32;
                className = N_ArrayUInt32;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_int);
                break;

            case "UInt64":
                // ArrayᐸUIntᐳ array = ArrayᐸUIntᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayUInt64;
                className = N_ArrayUInt64;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long);
                break;

            case "UInt128":
                // ArrayᐸUInt128ᐳ array = ArrayᐸUInt128ᐳ.$new$p(ctx, type, capacity, false);
                cdArray   = CD_ArrayUInt128;
                className = N_ArrayUInt128;
                mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_long, CD_long);
                break;

            default:
                throw new UnsupportedOperationException("TODO");
            }
        } else {
            // ArrayᐸObjectᐳ array = ArrayᐸObjectᐳ.$new$p(ctx, type, capacity, false);
            cdArray   = CD_ArrayObj;
            className = N_ArrayObj;
            addMethod = "add";
            mdAdd     = MethodTypeDesc.of(cdArray, CD_Ctx, CD_nObj);
        }

        // Note: we remove the immutability here; it will be added back upon "$makeImmut"
        loadCtx(code);
        loadTypeConstant(code, className, arrayType.removeImmutable());
        code.loadConstant((long) values.length)
            .iconst_0()
            .invokestatic(cdArray, "$new$p",
                    MethodTypeDesc.of(cdArray, CD_Ctx, CD_TypeConstant, CD_long, CD_boolean));

        for (Constant value : values) {
            // array.add(ctx, loadConstant(constValue));
            code.dup()
                .aload(code.parameterSlot(0));
            loadConstant(bctx, code, value);
            code.invokevirtual(cdArray, addMethod, mdAdd)
                .pop();
        }

        // array.makeImmutable();
        code.dup()
            .aload(code.parameterSlot(0))
            .invokevirtual(cdArray, "$makeImmut", MethodTypeDesc.of(CD_void, CD_Ctx));
        return new SingleSlot(arrayType, Specific, ensureClassDesc(arrayType), "");
    }

    /**
     * Build the code to load an XTC String value on the Java stack.
     */
    public static void loadString(CodeBuilder code, String value) {
        loadCtx(code);
        code.ldc(value)
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
        return loadProperty(code, typeContainer, propId, true);
    }

    /**
     * Build the code to load a local property on the Java stack.
     *
     * This method assumes the "owner" ref is loaded on Java stack.
     *
     * @param allowUnboxing  if true, allow property access optimization
     *
     * @return the JitMethodDesc for the "get" method
     */
    public JitMethodDesc loadProperty(CodeBuilder code, TypeConstant typeContainer,
                                      PropertyConstant propId, boolean allowUnboxing) {
        PropertyInfo  xvmInfo    = propId.getPropertyInfo(typeContainer);
        PropertyInfo  jitInfo    = propId.getPropertyInfo(typeContainer.getCanonicalJitType());
        TypeConstant  typeOwner  = jitInfo.getOwnerType(this, typeContainer);
        JitMethodDesc jmdGet     = jitInfo.getGetterJitDesc(this);
        String        getterName = jitInfo.ensureGetterJitMethodName(typeSystem);

        MethodTypeDesc md;
        if (jmdGet.isOptimized && allowUnboxing) {
            md         = jmdGet.optimizedMD;
            getterName += Builder.OPT;
        } else {
            md = jmdGet.standardMD;
        }

        loadCtx(code);
        if (!jitInfo.isNative() && typeOwner.isJitInterface()) {
            code.invokeinterface(ensureClassDesc(typeOwner), getterName, md);
        } else {
            code.invokevirtual(ensureClassDesc(typeOwner), getterName, md);
        }

        if (!xvmInfo.getType().equals(jitInfo.getType())) {
            code.checkcast(ensureClassDesc(xvmInfo.getType()));
        }
        return jmdGet;
    }

    /**
     * Generate a value "load" for the specified Java class.
     */
    public static void load(CodeBuilder code, ClassDesc cd, int slot) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "Z":
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
            case "I", "S", "B", "Z":
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
            case "I", "S", "B", "Z":
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
     * @param code     the {@link CodeBuilder} to use
     * @param reg      the register containing the vale to check
     * @param lblNull  the label to jump to if the register is "Null".
     */
    public static void checkNull(CodeBuilder code, RegisterInfo reg, Label lblNull) {
        if (reg instanceof ExtendedSlot extSlot) {
            assert reg.cd().isPrimitive();

            code.iload(extSlot.extSlot())
                .ifne(lblNull);
        } else if (reg instanceof MultiSlot multiSlot) {
            assert reg.type().removeNullable().isXvmPrimitive();

            code.iload(multiSlot.extSlot())
                .ifne(lblNull);
        } else {
            assert !reg.cd().isPrimitive();

            reg.load(code);
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
        if (reg instanceof ExtendedSlot extSlot) {
            assert reg.cd().isPrimitive();

            code.iload(extSlot.extSlot())
                .ifeq(lblNotNull);
        } else if (reg instanceof MultiSlot multiSlot) {
            assert reg.flavor() == NullableXvmPrimitive;

            code.iload(multiSlot.extSlot())
                .ifeq(lblNotNull);
        } else {
            assert !reg.cd().isPrimitive();

            reg.load(code);
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
     * Generate a "load" for a boolean value
     */
    public static void loadBoolean(CodeBuilder code, boolean value) {
        if (value) {
            code.iconst_1();
        } else {
            code.iconst_0();
        }
    }

    /**
     * Generate a default return for the specified Java class assuming the corresponding value
     * is already on java stack.
     */
    public static void addReturn(CodeBuilder code, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
            case "I", "S", "B", "Z":
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
     * Generate a "pop()" opcode for a type, assuming the corresponding value is already on the Java
     * stack.
     */
    public static void pop(CodeBuilder code, Builder builder, TypeConstant type) {
        TypeConstant baseType = type.removeNullable();
        if (baseType.isXvmPrimitive()) {
            for (ClassDesc cd : JitTypeDesc.getXvmPrimitiveClasses(baseType)) {
                pop(code, cd);
            }
        } else {
            pop(code, JitTypeDesc.getJitClass(builder, baseType));
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
     * Generate unboxing opcodes for a wrapper reference on the Java stack.
     *
     * In: a boxed Java reference
     * Out: the unboxed primitive value
     *
     * @param reg  the RegisterInfo for the unboxed value
     */
    public static void unbox(CodeBuilder code, RegisterInfo reg) {
        unbox(code, reg.type());
    }

    /**
     * Generate unboxing opcodes for a wrapper reference on the Java stack.
     *
     * In: a boxed Java reference
     * Out: the unboxed primitive value
     *
     * @param type  the primitive type for the boxed value
     */
    public static void unbox(CodeBuilder code, TypeConstant type) {
        String name = type.removeNullable()
                .getSingleUnderlyingClass(false)
                .getName();

        switch (name) {
            case "Bit"     -> code.getfield(CD_Bit,     "$value", CD_int);
            case "Boolean" -> code.getfield(CD_Boolean, "$value", CD_boolean);
            case "Char"    -> code.getfield(CD_Char,    "$value", CD_int);
            case "Dec32"   -> code.getfield(CD_Dec32,   "$bits",  CD_int);
            case "Dec64"   -> code.getfield(CD_Dec64,   "$bits",  CD_long);
            case "Dec128"  -> {
                // stack is Dec128
                code.dup();
                // stack is Dec128 Dec128
                code.getfield(CD_Dec128, "$lowBits", CD_long);
                // stack is Dec128 long long_2
                code.dup2_x1().pop2();
                // stack is long long_2 Dec128
                code.getfield(CD_Dec128, "$highBits", CD_long);
                // stack is long long_2 long long_2
            }
            case "Float16" -> code.getfield(CD_Float16, "$value", CD_float);
            case "Float32" -> code.getfield(CD_Float32, "$value", CD_float);
            case "Float64" -> code.getfield(CD_Float64, "$value", CD_double);
            case "Int8"    -> code.getfield(CD_Int8,    "$value", CD_int);
            case "Int16"   -> code.getfield(CD_Int16,   "$value", CD_int);
            case "Int32"   -> code.getfield(CD_Int32,   "$value", CD_int);
            case "Int64"   -> code.getfield(CD_Int64,   "$value", CD_long);
            case "Int128"  -> {
                // stack is Int128
                code.dup();
                // stack is Int128 Int128
                code.getfield(CD_Int128, "$lowValue", CD_long);
                // stack is Int128 long
                code.dup2_x1().pop2();
                // stack is long Int128
                code.getfield(CD_Int128, "$highValue", CD_long);
                // stack is long long_2
            }
            case "Nibble"  -> code.getfield(CD_Nibble, "$value", CD_int);
            case "UInt8"   -> code.getfield(CD_UInt8,  "$value", CD_int);
            case "UInt16"  -> code.getfield(CD_UInt16, "$value", CD_int);
            case "UInt32"  -> code.getfield(CD_UInt32, "$value", CD_int);
            case "UInt64"  -> code.getfield(CD_UInt64, "$value", CD_long);
            case "UInt128" -> {
                // stack is UInt128
                code.dup();
                // stack is UInt128, UInt128
                code.getfield(CD_UInt128, "$lowValue", CD_long);
                // stack is UInt128, long
                code.dup2_x1().pop2();
                // stack is long, UInt128
                code.getfield(CD_UInt128, "$highValue", CD_long);
                // stack is long, long_2
            }
            default -> throw new UnsupportedOperationException("Cannot unbox " + name);
        }
    }

    /**
     * Generate boxing opcodes for a primitive value of the specified primitive class on the stack.
     *
     * In: an unboxed primitive value
     * Out: the boxed Java reference
     *
     * @param reg  the RegisterInfo for the unboxed value
     */
    public static void box(CodeBuilder code, RegisterInfo reg) {
        box(code, reg.type());
    }

    /**
     * Generate boxing opcodes to box one or more values from the stack into a Java or XVM
     * primitive type.
     *
     * In: an unboxed primitive value
     * Out: the boxed Java reference
     *
     * @param code  the {@link CodeBuilder} to use to generate byte codes
     * @param type  the type to box the values from the stack into
     */
    public static void box(CodeBuilder code, TypeConstant type) {
        String name = type.removeNullable()
                          .getSingleUnderlyingClass(false)
                          .getName();

        switch (name) {
            case "Bit"     -> code.invokestatic(CD_Bit,     "$box", MD_Bit_box);
            case "Boolean" -> code.invokestatic(CD_Boolean, "$box", MD_Boolean_box);
            case "Char"    -> code.invokestatic(CD_Char,    "$box", MD_Char_box);
            case "Dec32"   -> code.invokestatic(CD_Dec32,   "$box", MD_Dec32_box);
            case "Dec64"   -> code.invokestatic(CD_Dec64,   "$box", MD_Dec64_box);
            case "Dec128"  -> code.invokestatic(CD_Dec128,  "$box", MD_Dec128_box);
            case "Float16" -> code.invokestatic(CD_Float16, "$box", MD_Float16_box);
            case "Float32" -> code.invokestatic(CD_Float32, "$box", MD_Float32_box);
            case "Float64" -> code.invokestatic(CD_Float64, "$box", MD_Float64_box);
            case "Int8"    -> code.invokestatic(CD_Int8,    "$box", MD_Int8_box);
            case "Int16"   -> code.invokestatic(CD_Int16,   "$box", MD_Int16_box);
            case "Int32"   -> code.invokestatic(CD_Int32,   "$box", MD_Int32_box);
            case "Int64"   -> code.invokestatic(CD_Int64,   "$box", MD_Int64_box);
            case "Int128"  -> code.invokestatic(CD_Int128,  "$box", MD_Int128_box);
            case "Nibble"  -> code.invokestatic(CD_Nibble,  "$box", MD_Nibble_box);
            case "UInt8"   -> code.invokestatic(CD_UInt8,   "$box", MD_UInt8_box);
            case "UInt16"  -> code.invokestatic(CD_UInt16,  "$box", MD_UInt16_box);
            case "UInt32"  -> code.invokestatic(CD_UInt32,  "$box", MD_UInt32_box);
            case "UInt64"  -> code.invokestatic(CD_UInt64,  "$box", MD_UInt64_box);
            case "UInt128" -> code.invokestatic(CD_UInt128, "$box", MD_UInt128_box);
            default        -> throw new UnsupportedOperationException("Cannot box " + name);
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

        case "F": // float
            code.invokestatic(CD_JavaFloat, "valueOf", MethodTypeDesc.of(CD_JavaFloat, CD_float));
            break;

        case "D": // double
            code.invokestatic(CD_JavaDouble, "valueOf", MethodTypeDesc.of(CD_JavaDouble, CD_Double));
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
     * Build the code to load the Ctx instance on the Java stack.
     */
    public static CodeBuilder loadCtx(CodeBuilder code) {
        code.aload(code.parameterSlot(0));
        return code;
    }

    /**
     * Generate a "load the return value from the context" for the specified Java class. Out: The
     * loaded value is at the java stack top.
     *
     * @param returnIndex the index of the value in the Ctx object
     */
    public static void loadFromContext(CodeBuilder code, ClassDesc cd, int returnIndex) {
        assert returnIndex >= 0;

        loadCtx(code);
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
            case "I", "S", "B", "Z":
                code.l2i();
                break;
            case "J":
                break;
            case "F":
                code.l2i().invokestatic(CD_JavaFloat, "intBitsToFloat", MD_I2F);
                break;
            case "D":
                code.invokestatic(CD_JavaDouble, "longBitsToDouble", MD_L2D);
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

        loadCtx(code);

        String descriptor = cd.descriptorString();
        if (cd.isPrimitive() && (descriptor.equals("J") || descriptor.equals("D"))) {
             // the value is a "long" or "double" that occupies two slots
             // stack (lvalue, lvalue2, $ctx) -> ($ctx, lvalue, lvalue2)
            code.dup_x2().pop();
        } else {
            // stack (value, $ctx) -> ($ctx, value)
            code.swap();
        }

        if (cd.isPrimitive()) {
            // all primitives are stored into "long" fields; convert
            switch (descriptor) {
            case "I", "S", "B", "Z":
                code.i2l();
                break;
            case "J":
                break;
            case "F":
                code.invokestatic(CD_JavaFloat, "floatToRawIntBits", MD_F2I).i2l();
                break;
            case "D":
                code.invokestatic(CD_JavaDouble, "doubleToRawLongBits", MD_D2L);
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
     * Generate a "checkcast" that transforms a potential CCE into a TypeMismatch.
     */
    public void generateCheckCast(CodeBuilder code, TypeConstant typeTo) {
        Label startLabel   = code.newLabel();
        Label endLabel     = code.newLabel();
        Label successLabel = code.newLabel();

        ClassDesc cd = ensureClassDesc(typeTo);
        code.labelBinding(startLabel)
            .checkcast(cd)
            .goto_(successLabel)
            .labelBinding(endLabel);
        Builder.throwTypeMismatch(code, cd.descriptorString());

        code.labelBinding(successLabel)
            .exceptionCatch(startLabel, endLabel, endLabel,
                ClassDesc.of("java.lang.ClassCastException"));
    }

    /**
     * Add the code to throw an Ecstasy exception. The code we produce is equivalent to:
     * {@code throw new Exception(ctx).$init(ctx, text, null);}
     *
     * @param code
     * @param exCD         the ClassDesc for the Ecstasy exception (e.g. TypeMismatch)
     * @param text         the exception text
     * @param buildContext
     */
    public static void throwException(CodeBuilder code, ClassDesc exCD, String text) {
        invokeDefaultConstructor(code, exCD);
        loadCtx(code);
        code.loadConstant(text)
            .aconst_null()
            .invokevirtual(exCD, "$init", MethodTypeDesc.of(
                CD_nException, CD_Ctx, CD_JavaString, CD_Throwable))
            .athrow();
    }

    /**
     * Add the code to throw a "TypeMismatch" exception.
     */
    public static void throwTypeMismatch(CodeBuilder code, String text) {
        throwException(code, ClassDesc.of(N_TypeMismatch), text);
    }

    /**
     * Add the code to throw an "OutOfBounds" exception.
     */
    public static void throwOutOfBounds(CodeBuilder code, String text) {
        throwException(code, ClassDesc.of(N_OutOfBounds), text);
    }

    /**
     * Convert the "void construct$17(...)" to "This new$17(...)"
     */
    public static JitMethodDesc convertConstructToNew(TypeInfo typeInfo, ClassDesc cd,
                                                      JitCtorDesc jmdCtor) {
        JitParamDesc retDesc = new JitParamDesc(typeInfo.getType(), Specific, cd, 0, -1, false);

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
    public static final String N_ArrayBit     = "org.xtclang.ecstasy.collections.ArrayᐸBitᐳ";
    public static final String N_ArrayChar    = "org.xtclang.ecstasy.collections.ArrayᐸCharᐳ";
    public static final String N_ArrayDec32   = "org.xtclang.ecstasy.collections.ArrayᐸDec32ᐳ";
    public static final String N_ArrayDec64   = "org.xtclang.ecstasy.collections.ArrayᐸDec64ᐳ";
    public static final String N_ArrayDec128  = "org.xtclang.ecstasy.collections.ArrayᐸDec128ᐳ";
    public static final String N_ArrayFloat32 = "org.xtclang.ecstasy.collections.ArrayᐸFloat32ᐳ";
    public static final String N_ArrayFloat64 = "org.xtclang.ecstasy.collections.ArrayᐸFloat64ᐳ";
    public static final String N_ArrayNibble  = "org.xtclang.ecstasy.collections.ArrayᐸNibbleᐳ";
    public static final String N_ArrayInt8    = "org.xtclang.ecstasy.collections.ArrayᐸInt8ᐳ";
    public static final String N_ArrayInt16   = "org.xtclang.ecstasy.collections.ArrayᐸInt16ᐳ";
    public static final String N_ArrayInt32   = "org.xtclang.ecstasy.collections.ArrayᐸInt32ᐳ";
    public static final String N_ArrayInt64   = "org.xtclang.ecstasy.collections.ArrayᐸInt64ᐳ";
    public static final String N_ArrayInt128  = "org.xtclang.ecstasy.collections.ArrayᐸInt128ᐳ";
    public static final String N_ArrayUInt8   = "org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ";
    public static final String N_ArrayUInt16  = "org.xtclang.ecstasy.collections.ArrayᐸUInt16ᐳ";
    public static final String N_ArrayUInt32  = "org.xtclang.ecstasy.collections.ArrayᐸUInt32ᐳ";
    public static final String N_ArrayUInt64  = "org.xtclang.ecstasy.collections.ArrayᐸUInt64ᐳ";
    public static final String N_ArrayUInt128 = "org.xtclang.ecstasy.collections.ArrayᐸUInt128ᐳ";
    public static final String N_ArrayObj     = "org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ";
    public static final String N_Bit          = "org.xtclang.ecstasy.numbers.Bit";
    public static final String N_Boolean      = "org.xtclang.ecstasy.Boolean";
    public static final String N_Char         = "org.xtclang.ecstasy.text.Char";
    public static final String N_Class        = "org.xtclang.ecstasy.reflect.Class";
    public static final String N_Comparable   = "org.xtclang.ecstasy.Comparable";
    public static final String N_Dec32        = "org.xtclang.ecstasy.numbers.Dec32";
    public static final String N_Dec64        = "org.xtclang.ecstasy.numbers.Dec64";
    public static final String N_Dec128       = "org.xtclang.ecstasy.numbers.Dec128";
    public static final String N_Enumeration  = "org.xtclang.ecstasy.reflect.Enumeration";
    public static final String N_Exception    = "org.xtclang.ecstasy.Exception";
    public static final String N_Float16      = "org.xtclang.ecstasy.numbers.Float16";
    public static final String N_Float32      = "org.xtclang.ecstasy.numbers.Float32";
    public static final String N_Float64      = "org.xtclang.ecstasy.numbers.Float64";
    public static final String N_Int8         = "org.xtclang.ecstasy.numbers.Int8";
    public static final String N_Int16        = "org.xtclang.ecstasy.numbers.Int16";
    public static final String N_Int32        = "org.xtclang.ecstasy.numbers.Int32";
    public static final String N_Int64        = "org.xtclang.ecstasy.numbers.Int64";
    public static final String N_Int128       = "org.xtclang.ecstasy.numbers.Int128";
    public static final String N_Nibble       = "org.xtclang.ecstasy.numbers.Nibble";
    public static final String N_Nullable     = "org.xtclang.ecstasy.Nullable";
    public static final String N_Object       = "org.xtclang.ecstasy.Object";
    public static final String N_Orderable    = "org.xtclang.ecstasy.Orderable";
    public static final String N_Ordered      = "org.xtclang.ecstasy.Ordered";
    public static final String N_OutOfBounds  = "org.xtclang.ecstasy.OutOfBounds";
    public static final String N_String       = "org.xtclang.ecstasy.text.String";
    public static final String N_TypeMismatch = "org.xtclang.ecstasy.TypeMismatch";
    public static final String N_UInt8        = "org.xtclang.ecstasy.numbers.UInt8";
    public static final String N_UInt16       = "org.xtclang.ecstasy.numbers.UInt16";
    public static final String N_UInt32       = "org.xtclang.ecstasy.numbers.UInt32";
    public static final String N_UInt64       = "org.xtclang.ecstasy.numbers.UInt64";
    public static final String N_UInt128      = "org.xtclang.ecstasy.numbers.UInt128";

    public static final String N_nConst       = "org.xtclang.ecstasy.nConst";
    public static final String N_nEnum        = "org.xtclang.ecstasy.nEnum";
    public static final String N_nException   = "org.xtclang.ecstasy.nException";
    public static final String N_nFunction    = "org.xtclang.ecstasy.nFunction";
    public static final String N_nMethod      = "org.xtclang.ecstasy.nMethod";
    public static final String N_nModule      = "org.xtclang.ecstasy.nModule";
    public static final String N_nObj         = "org.xtclang.ecstasy.nObj";
    public static final String N_nPackage     = "org.xtclang.ecstasy.nPackage";
    public static final String N_nRef         = "org.xtclang.ecstasy.reflect.nRef";
    public static final String N_nRangeInt64  = "org.xtclang.ecstasy.nRangeᐸInt64ᐳ";
    public static final String N_nService     = "org.xtclang.ecstasy.nService";
    public static final String N_nType        = "org.xtclang.ecstasy.nType";

    // ----- well-known suffixes -------------------------------------------------------------------

    public static final String MODULE         = "¤module"; // the main module class name
    public static final String LAMBDA         = "lambda¤"; // the base of the lambda function name
    public static final String EXT            = "$ext";    // a multi-slot extension field of a primitive field
    public static final String INIT           = "$init";   // the singleton initialization instance method
    public static final String NEW            = "$new";    // the instance creation static method
    public static final String OPT            = "$p";      // methods that contains primitive types
    public static final String DELEGATE       = "$d";      // methods that delegates to an underlying property

    // ----- well-known class descriptors ----------------------------------------------------------

    public static final ClassDesc CD_Array         = ClassDesc.of(N_Array);
    public static final ClassDesc CD_ArrayBit      = ClassDesc.of(N_ArrayBit  );
    public static final ClassDesc CD_ArrayChar     = ClassDesc.of(N_ArrayChar);
    public static final ClassDesc CD_ArrayDec32    = ClassDesc.of(N_ArrayDec32);
    public static final ClassDesc CD_ArrayDec64    = ClassDesc.of(N_ArrayDec64);
    public static final ClassDesc CD_ArrayDec128   = ClassDesc.of(N_ArrayDec128);
    public static final ClassDesc CD_ArrayFloat32  = ClassDesc.of(N_ArrayFloat32);
    public static final ClassDesc CD_ArrayFloat64  = ClassDesc.of(N_ArrayFloat64);
    public static final ClassDesc CD_ArrayInt8     = ClassDesc.of(N_ArrayInt8);
    public static final ClassDesc CD_ArrayInt16    = ClassDesc.of(N_ArrayInt16);
    public static final ClassDesc CD_ArrayInt32    = ClassDesc.of(N_ArrayInt32);
    public static final ClassDesc CD_ArrayInt64    = ClassDesc.of(N_ArrayInt64);
    public static final ClassDesc CD_ArrayInt128   = ClassDesc.of(N_ArrayInt128);
    public static final ClassDesc CD_ArrayNibble   = ClassDesc.of(N_ArrayNibble);
    public static final ClassDesc CD_ArrayUInt8    = ClassDesc.of(N_ArrayUInt8);
    public static final ClassDesc CD_ArrayUInt16   = ClassDesc.of(N_ArrayUInt16);
    public static final ClassDesc CD_ArrayUInt32   = ClassDesc.of(N_ArrayUInt32);
    public static final ClassDesc CD_ArrayUInt64   = ClassDesc.of(N_ArrayUInt64);
    public static final ClassDesc CD_ArrayUInt128  = ClassDesc.of(N_ArrayUInt128);
    public static final ClassDesc CD_ArrayObj      = ClassDesc.of(N_ArrayObj);
    public static final ClassDesc CD_Class         = ClassDesc.of(N_Class);
    public static final ClassDesc CD_Comparable    = ClassDesc.of(N_Comparable);
    public static final ClassDesc CD_Enumeration   = ClassDesc.of(N_Enumeration);
    public static final ClassDesc CD_Exception     = ClassDesc.of(N_Exception);
    public static final ClassDesc CD_nFunction     = ClassDesc.of(N_nFunction);
    public static final ClassDesc CD_nMethod       = ClassDesc.of(N_nMethod);
    public static final ClassDesc CD_nModule       = ClassDesc.of(N_nModule);
    public static final ClassDesc CD_nPackage      = ClassDesc.of(N_nPackage);
    public static final ClassDesc CD_nRangeInt64   = ClassDesc.of(N_nRangeInt64);

    public static final ClassDesc CD_nConst        = ClassDesc.of(N_nConst);
    public static final ClassDesc CD_nEnum         = ClassDesc.of(N_nEnum);
    public static final ClassDesc CD_nException    = ClassDesc.of(N_nException);
    public static final ClassDesc CD_nObj          = ClassDesc.of(N_nObj);
    public static final ClassDesc CD_nRef          = ClassDesc.of(N_nRef);
    public static final ClassDesc CD_nType         = ClassDesc.of(N_nType);

    public static final ClassDesc CD_Bit           = ClassDesc.of(N_Bit);
    public static final ClassDesc CD_Boolean       = ClassDesc.of(N_Boolean);
    public static final ClassDesc CD_Char          = ClassDesc.of(N_Char);
    public static final ClassDesc CD_Dec32         = ClassDesc.of(N_Dec32);
    public static final ClassDesc CD_Dec64         = ClassDesc.of(N_Dec64);
    public static final ClassDesc CD_Dec128        = ClassDesc.of(N_Dec128);
    public static final ClassDesc CD_Float16       = ClassDesc.of(N_Float16);
    public static final ClassDesc CD_Float32       = ClassDesc.of(N_Float32);
    public static final ClassDesc CD_Float64       = ClassDesc.of(N_Float64);
    public static final ClassDesc CD_Int8          = ClassDesc.of(N_Int8);
    public static final ClassDesc CD_Int16         = ClassDesc.of(N_Int16);
    public static final ClassDesc CD_Int32         = ClassDesc.of(N_Int32);
    public static final ClassDesc CD_Int64         = ClassDesc.of(N_Int64);
    public static final ClassDesc CD_Int128        = ClassDesc.of(N_Int128);
    public static final ClassDesc CD_Nibble        = ClassDesc.of(N_Nibble);
    public static final ClassDesc CD_Nullable      = ClassDesc.of(N_Nullable);
    public static final ClassDesc CD_Object        = ClassDesc.of(N_Object);
    public static final ClassDesc CD_Orderable     = ClassDesc.of(N_Orderable);
    public static final ClassDesc CD_Ordered       = ClassDesc.of(N_Ordered);
    public static final ClassDesc CD_String        = ClassDesc.of(N_String);
    public static final ClassDesc CD_UInt8         = ClassDesc.of(N_UInt8);
    public static final ClassDesc CD_UInt16        = ClassDesc.of(N_UInt16);
    public static final ClassDesc CD_UInt32        = ClassDesc.of(N_UInt32);
    public static final ClassDesc CD_UInt64        = ClassDesc.of(N_UInt64);
    public static final ClassDesc CD_UInt128       = ClassDesc.of(N_UInt128);

    public static final ClassDesc CD_Container     = ClassDesc.of(Container.class.getName());
    public static final ClassDesc CD_Ctx           = ClassDesc.of(Ctx.class.getName());
    public static final ClassDesc CD_CtorCtx       = ClassDesc.of(Ctx.CtorCtx.class.getName());
    public static final ClassDesc CD_TypeConstant  = ClassDesc.of(TypeConstant.class.getName());
    public static final ClassDesc CD_TypeSystem    = ClassDesc.of(TypeSystem.class.getName());

    public static final ClassDesc CD_JavaBoolean   = ClassDesc.of(java.lang.Boolean.class.getName());
    public static final ClassDesc CD_JavaDouble    = ClassDesc.of(java.lang.Double.class.getName());
    public static final ClassDesc CD_JavaFloat    = ClassDesc.of(java.lang.Float.class.getName());
    public static final ClassDesc CD_JavaInteger   = ClassDesc.of(java.lang.Integer.class.getName());
    public static final ClassDesc CD_JavaLong      = ClassDesc.of(java.lang.Long.class.getName());
    public static final ClassDesc CD_JavaMath      = ClassDesc.of(java.lang.Math.class.getName());
    public static final ClassDesc CD_JavaObject    = ClassDesc.of(java.lang.Object.class.getName());
    public static final ClassDesc CD_JavaString    = ClassDesc.of(java.lang.String.class.getName());

    // ----- well-known custom primitives ----------------------------------------------------------

    public static final ClassDesc[] CDs_Int      = new ClassDesc[]{CD_int};
    public static final ClassDesc[] CDs_Long     = new ClassDesc[]{CD_long};
    public static final ClassDesc[] CDs_LongLong = new ClassDesc[]{CD_long, CD_long};

    // ----- well-known methods --------------------------------------------------------------------

    /**
     * The name of the static field holding an instance reference for singleton types.
     */
    public static final String Instance = "$INSTANCE";

    /**
     * The name of the field on nType object holding the underlying TypeConstant.
     */
    public static final String DataType = "$dataType";

    // various commonly used MethodDesc constants
    public static final MethodTypeDesc MD_Bit_box     = MethodTypeDesc.of(CD_Bit,     CD_int);
    public static final MethodTypeDesc MD_Boolean_box = MethodTypeDesc.of(CD_Boolean, CD_boolean);
    public static final MethodTypeDesc MD_Char_box    = MethodTypeDesc.of(CD_Char,    CD_int);
    public static final MethodTypeDesc MD_Char_addInt = MethodTypeDesc.of(CD_int,     CD_Ctx, CD_int, CD_long);
    public static final MethodTypeDesc MD_Char_subInt = MethodTypeDesc.of(CD_int,     CD_Ctx, CD_int, CD_long);
    public static final MethodTypeDesc MD_Dec32_box   = MethodTypeDesc.of(CD_Dec32,   CD_int);
    public static final MethodTypeDesc MD_Dec64_box   = MethodTypeDesc.of(CD_Dec64,   CD_long);
    public static final MethodTypeDesc MD_Dec128_box  = MethodTypeDesc.of(CD_Dec128,  CD_long, CD_long);
    public static final MethodTypeDesc MD_Float16_box = MethodTypeDesc.of(CD_Float16, CD_float);
    public static final MethodTypeDesc MD_Float32_box = MethodTypeDesc.of(CD_Float32, CD_float);
    public static final MethodTypeDesc MD_Float64_box = MethodTypeDesc.of(CD_Float64, CD_double);
    public static final MethodTypeDesc MD_Nibble_box  = MethodTypeDesc.of(CD_Nibble,  CD_int);
    public static final MethodTypeDesc MD_Int8_box    = MethodTypeDesc.of(CD_Int8,    CD_int);
    public static final MethodTypeDesc MD_Int16_box   = MethodTypeDesc.of(CD_Int16,   CD_int);
    public static final MethodTypeDesc MD_Int32_box   = MethodTypeDesc.of(CD_Int32,   CD_int);
    public static final MethodTypeDesc MD_Int64_box   = MethodTypeDesc.of(CD_Int64,   CD_long);
    public static final MethodTypeDesc MD_Int128_box  = MethodTypeDesc.of(CD_Int128,  CD_long, CD_long);
    public static final MethodTypeDesc MD_UInt8_box   = MethodTypeDesc.of(CD_UInt8,   CD_int);
    public static final MethodTypeDesc MD_UInt16_box  = MethodTypeDesc.of(CD_UInt16,  CD_int);
    public static final MethodTypeDesc MD_UInt32_box  = MethodTypeDesc.of(CD_UInt32,  CD_int);
    public static final MethodTypeDesc MD_UInt64_box  = MethodTypeDesc.of(CD_UInt64,  CD_long);
    public static final MethodTypeDesc MD_UInt128_box = MethodTypeDesc.of(CD_UInt128, CD_long, CD_long);
    public static final MethodTypeDesc MD_Initializer = MethodTypeDesc.of(CD_void,    CD_Ctx);
    public static final MethodTypeDesc MD_StringOf    = MethodTypeDesc.of(CD_String,  CD_Ctx, CD_JavaString);
    public static final MethodTypeDesc MD_xvmType     = MethodTypeDesc.of(CD_TypeConstant, CD_Ctx);
    public static final MethodTypeDesc MD_TypeIsA     = MethodTypeDesc.of(CD_boolean, CD_TypeConstant);
    public static final MethodTypeDesc MD_FloorModI   = MethodTypeDesc.of(CD_int, CD_int, CD_int);
    public static final MethodTypeDesc MD_FloorModJ   = MethodTypeDesc.of(CD_long, CD_long, CD_long);
    public static final MethodTypeDesc MD_UDivInt     = MethodTypeDesc.of(CD_int, CD_int, CD_int);
    public static final MethodTypeDesc MD_UDivLong    = MethodTypeDesc.of(CD_long, CD_long, CD_long);
    public static final MethodTypeDesc MD_D2L         = MethodTypeDesc.of(CD_long, CD_double);
    public static final MethodTypeDesc MD_L2D         = MethodTypeDesc.of(CD_double, CD_long);
    public static final MethodTypeDesc MD_F2I         = MethodTypeDesc.of(CD_int, CD_float);
    public static final MethodTypeDesc MD_I2F         = MethodTypeDesc.of(CD_float, CD_int);
}
