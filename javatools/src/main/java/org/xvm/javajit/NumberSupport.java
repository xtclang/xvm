package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.registers.MultiSlot;

import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_short;

import static org.xvm.javajit.Builder.CD_JavaMath;
import static org.xvm.javajit.Builder.MD_FP8Binary;
import static org.xvm.javajit.Builder.MD_FloorModI;
import static org.xvm.javajit.Builder.MD_FloorModJ;
import static org.xvm.javajit.Builder.MD_UDivInt;
import static org.xvm.javajit.Builder.MD_UDivLong;
import static org.xvm.javajit.Builder.md;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy numeric types.
 */
public interface NumberSupport
        extends NumberSupportInt128, NumberSupportDec {

    /**
     * The 8-bit FP formats are carried as their encoding in an int, so they share the "I" carrier
     * with the small integer types but share none of their arithmetic. Every primitive operation
     * therefore has to ask the Ecstasy type, not the carrier, before dispatching.
     *
     * @return the jitbridge ClassDesc for an FP8 type, or null if this is not one
     */
    private static ClassDesc fp8Class(TypeConstant type) {
        return switch (type.getSingleUnderlyingClass(false).getName()) {
            case "Float8e4" -> Builder.CD_Float8e4;
            case "Float8e5" -> Builder.CD_Float8e5;
            default         -> null;
        };
    }

    /**
     * Float16 shares the "F" carrier with Float32, so an operation on it is performed at float
     * precision and has to be rounded back into the format afterwards; without this, a Float16
     * result can hold a value Float16 cannot represent. Float32 and Float64 fill their carriers
     * exactly and need nothing.
     */
    private static CodeBuilder narrowFloat(CodeBuilder code, TypeConstant type) {
        return "Float16".equals(type.getSingleUnderlyingClass(false).getName())
                ? code.invokestatic(Builder.CD_JavaFloat, "floatToFloat16", md(CD_short, CD_float))
                      .invokestatic(Builder.CD_JavaFloat, "float16ToFloat", md(CD_float, CD_short))
                : code;
    }

    /**
     * Build the optimized binary operation that will add two primitive types from the stack
     * (T + T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveAdd(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$add", MD_FP8Binary);
            return;
        }
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.iadd();
                Builder.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.ladd();
            case "F" -> narrowFloat(code.fadd(), regTarget.type());
            case "D" -> code.dadd();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive types
     * (T + T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildXvmPrimitiveAdd(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongAdd(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec32"             -> buildDec32Add(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec", "Dec64"      -> buildDec64Add(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec128"            -> buildDec128Add(bctx, code, (MultiSlot) regTarget, nArgId);
            default  -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically AND two primitive types from the
     * stack (T & T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveAnd(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.iand();
                Builder.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.land();
            case "Z" -> code.iand();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will logically AND two XVM primitive types
     * (T & T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildXvmPrimitiveAnd(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongAnd(bctx, code, (MultiSlot) regTarget, nArgId);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized unary operation that will produce the complement of the target value from
     * the top of the stack (~T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveCompl(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I"  -> Builder.adjustIntValue(code.iconst_m1().ixor(), regTarget.type());
            case "Z" -> code.iconst_1().ixor();
            case "J" -> code.ldc(-1L).lxor();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will produce the complement of a XVM primitive
     * (~T -> T).
     * <p>
     * The target register should not have been loaded to the stack.
     *
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildXvmPrimitiveCompl(CodeBuilder  code,
                                        RegisterInfo regTarget) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongCompl(code, (MultiSlot) regTarget);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will divide two primitive types from the stack
     * (T / T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveDiv(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$div", MD_FP8Binary);
            return;
        }
        TypeConstant typeTarget = regTarget.type();
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                boolean fUnsigned = typeTarget.getValueString().charAt(0) == 'U';
                if (fUnsigned) {
                    code.invokestatic(CD_Integer, "divideUnsigned", md(CD_int, CD_int, CD_int));
                } else {
                    code.idiv();
                }
                Builder.adjustIntValue(code, typeTarget);
            }
            case "J" -> {
                boolean fUnsigned = typeTarget.getValueString().charAt(0) == 'U';
                if (fUnsigned) {
                    code.invokestatic(CD_Long, "divideUnsigned", md(CD_long, CD_long, CD_long));
                } else {
                    code.ldiv();
                }
            }
            case "F" -> narrowFloat(code.fdiv(), regTarget.type());
            case "D" -> code.ddiv();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will calculate the remainder from dividing two
     * primitive types.
     * <pre>
     *     remainder = a - (b * quotient)
     * </pre>
     * Nothing should be on the stack, the remainder result wil be on the stack after execution.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveRemainder(BuildContext bctx, CodeBuilder code,
                                         RegisterInfo regTarget, int nArgId, int nQuotientId) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$rem", MD_FP8Binary);
            return;
        }
        regTarget.load(code);
        bctx.loadArgument(code, nArgId);
        bctx.loadArgument(code, nQuotientId);
        // a, b and quotient will be on the stack in that order
        // perform (b * quotient)
        buildPrimitiveMul(bctx, code, regTarget);
        // stack is now a followed by the result of (b * quotient)
        // perform the subtraction
        buildPrimitiveSub(bctx, code, regTarget);
        // remainder is on the stack
    }

    /**
     * Build the optimized binary operation that will divide two XVM primitive types
     * (T / T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveDiv(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongDiv(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec32"             -> buildDec32Div(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec", "Dec64"      -> buildDec64Div(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec128"            -> buildDec128Div(bctx, code, (MultiSlot) regTarget, nArgId);
            default                  ->
                    throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will calculate the remainder from dividing two
     * XVM primitive types.
     * <pre>
     *     remainder = a - (b * quotient)
     * </pre>
     * Nothing should be on the stack, the remainder result wil be on the stack after execution.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildXvmPrimitiveRemainder(BuildContext bctx, CodeBuilder code,
            RegisterInfo regTarget, int nArgId, int nQuotientId) {
        // perform (b * quotient)
        RegisterInfo regArg  = bctx.ensureRegister(code, nArgId);
        buildXvmPrimitiveMul(bctx, code, regArg, nQuotientId);
        // multiply result is on the stack
        TypeConstant type = regTarget.type();
        ClassDesc[]  cds  = JitTypeDesc.getXvmPrimitiveClasses(type);
        // store the result in a temporary register (Op.A_STACK)
        RegisterInfo temp = bctx.pushTempMultiRegister(type, cds);
        temp.store(bctx, code, type);
        // perform the subtraction
        buildXvmPrimitiveSub(bctx, code, regTarget, Op.A_STACK);
        // remainder is on the stack
    }

    /**
     * Build the optimized binary operation that will produce the modulo of two primitive types
     * from the stack (T % T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveMod(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$mod", MD_FP8Binary);
            return;
        }
        ClassDesc cd       = regTarget.cd();
        boolean   unsigned = regTarget.type().getValueString().startsWith("UInt");
        switch (cd.descriptorString()) {
            case "I" -> {
                if (unsigned) {
                    code.invokestatic(CD_Integer, "remainderUnsigned", MD_UDivInt);
                } else {
                    code.invokestatic(CD_JavaMath, "floorMod", MD_FloorModI);
                }
                Builder.adjustIntValue(code, regTarget.type());
            }
            case "J" -> {
                if (unsigned) {
                    code.invokestatic(CD_Long, "remainderUnsigned", MD_UDivLong);
                } else {
                    code.invokestatic(CD_JavaMath, "floorMod", MD_FloorModJ);
                }
            }
            case "F" -> narrowFloat(code.frem(), regTarget.type());
            case "D" -> code.drem();
            default  -> throw new IllegalStateException(
                    "Unsupported carrier: " + cd.descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will produce the modulo of two XVM primitive types
     * (T % T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveMod(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongMod(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec32"             -> buildDec32Mod(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec", "Dec64"      -> buildDec64Mod(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec128"            -> buildDec128Mod(bctx, code, (MultiSlot) regTarget, nArgId);
            default ->
                    throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized unary operation that will produce negative of a primitive type (-T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveNeg(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) != null) {
            // an FP8 value is carried as its encoding, so negation just flips the sign bit
            code.loadConstant(0x80)
                .ixor();
            return;
        }
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.ineg();

                switch (regTarget.type().getSingleUnderlyingClass(false).getName()) {
                    case "Int8"  -> code.i2b();
                    case "Int16" -> code.i2s();
                    case "Int32" -> {}
                    case "UInt8", "UInt16", "UInt32"
                            -> bctx.throwUnsupported(code);
                    default -> throw new IllegalStateException("Unsupported type: "
                            + regTarget.type().getSingleUnderlyingClass(false).getName());
                }
            }
            case "J" -> code.lneg();
            case "F" -> code.fneg();
            case "D" -> code.dneg();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized unary operation that will produce negative of a XVM primitive type
     * (-T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildXvmPrimitiveNeg(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.type().removeAccess().getValueString()) {
            case "Int128"       -> buildLongLongNeg(code, (MultiSlot) regTarget);
            case "UInt128"      -> bctx.throwUnsupported(code);
            case "Dec32"        -> buildDec32Neg(bctx, code, (MultiSlot) regTarget);
            case "Dec", "Dec64" -> buildDec64Neg(bctx, code, (MultiSlot) regTarget);
            case "Dec128"       -> buildDec128Neg(bctx, code, (MultiSlot) regTarget);
            default             ->
                    throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will produce the product of two primitive types
     * from the stack (T * T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveMul(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$mul", MD_FP8Binary);
            return;
        }
        switch (regTarget.cd().descriptorString()) {
            case "I" -> Builder.adjustIntValue(code.imul(), regTarget.type());
            case "J" -> code.lmul();
            case "F" -> narrowFloat(code.fmul(), regTarget.type());
            case "D" -> code.dmul();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will produce the product of two XVM primitive types
     * (T * T -> T).
     *
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveMul(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongMul(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec32"             -> buildDec32Mul(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec", "Dec64"      -> buildDec64Mul(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec128"            -> buildDec128Mul(bctx, code, (MultiSlot) regTarget, nArgId);
            default                  ->
                    throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically OR two primitive types from the
     * stack (T | T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveOr(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.ior();
                Builder.adjustIntValue(code, regTarget.type());
            }
            case "Z" -> code.ior();
            case "J" -> code.lor();
            default  -> throw new IllegalStateException(
                    "Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will logically OR two XVM primitive types
     * (T | T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildXvmPrimitiveOr(BuildContext bctx,
                                     CodeBuilder  code,
                                     RegisterInfo regTarget,
                                     int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongOr(bctx, code, (MultiSlot) regTarget, nArgId);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically shift left a primitive type
     * (T << T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx      the current build context
     * @param code      the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     *
     * @return the result type
     */
    default TypeConstant buildPrimitiveShl(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           int          nArgId) {
        RegisterInfo regLoaded = regTarget.load(code);
        RegisterInfo regArg    = bctx.loadArgument(code, nArgId);
        if (regArg.cd().equals(CD_long)) {
            code.l2i();
        } else if (!regArg.cd().equals(CD_int)) {
            throw new IllegalArgumentException("Expected Int argument for shl operation but is "
                    + regArg.cd().displayName());
        }

        switch (regLoaded.cd().descriptorString()) {
            case "I" -> Builder.adjustIntValue(code.ishl(), regLoaded.type());
            case "J" -> code.lshl();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regLoaded.cd().descriptorString());
        }
        return regLoaded.type();
    }

    /**
     * Build the optimized binary operation that will logically shift left a XVM primitive type
     * (T << T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveShl(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongShl(bctx, code, (MultiSlot) regTarget, nArgId);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically shift right a XVM primitive type
     * (T >> T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveShr(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128"  -> buildLongLongShr(bctx, code, (MultiSlot) regTarget, nArgId, false);
            case "UInt128" -> buildLongLongShr(bctx, code, (MultiSlot) regTarget, nArgId, true);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically unsigned shift right a XVM
     * primitive type (T >>> T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildXvmPrimitiveUnsignedShr(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongShr(bctx, code, (MultiSlot) regTarget, nArgId, true);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically shift right a primitive type
     * (T >> T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildPrimitiveShr(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           int          nArgId) {
        RegisterInfo regLoaded  = regTarget.load(code);
        RegisterInfo regArg     = bctx.loadArgument(code, nArgId);
        TypeConstant typeTarget = regLoaded.type();
        boolean      fUnsigned  = typeTarget.getValueString().charAt(0) == 'U';
        return buildPrimitiveShr(bctx, code, regLoaded, regArg, fUnsigned);
    }

    /**
     * Build the optimized binary operation that will logically perform an unsigned shift right
     * of a primitive type (T >>> T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildPrimitiveUnsignedShr(BuildContext bctx,
                                                   CodeBuilder  code,
                                                   RegisterInfo regTarget,
                                                   int          nArgId) {
        RegisterInfo regLoaded  = regTarget.load(code);
        TypeConstant typeTarget = regTarget.type();

        switch (typeTarget.getSingleUnderlyingClass(false).getName()) {
            case "Int8", "UInt8"   -> code.sipush(0xFF).iand();
            case "Int16", "UInt16" -> code.ldc(0xFFFF).iand();
        }

        RegisterInfo regArg = bctx.loadArgument(code, nArgId);
        return buildPrimitiveShr(bctx, code, regLoaded, regArg, true);
    }

    /**
     * Build the optimized binary operation that will logically shift right a primitive type
     * either (T >> T -> T) for signed or (T >>> T -> T) for unsigned.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param regArg     the register containing the operation argument
     * @param fUnsigned  true if the operation is unsigned, false if signed
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildPrimitiveShr(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           RegisterInfo regArg,
                                           boolean      fUnsigned) {
        TypeConstant typeTarget = regTarget.type();

        switch (regArg.cd().descriptorString()) {
            case "I" -> {}
            case "J" -> code.l2i();
            default ->
                throw new IllegalArgumentException("Expected int argument for shr operation but is "
                        + regTarget.cd().displayName());
        }

        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                if (fUnsigned) {
                    code.iushr();
                } else {
                    code.ishr();
                }
                Builder.adjustIntValue(code, typeTarget);
            }
            case "J" -> {
                if (fUnsigned) {
                    code.lushr();
                } else {
                    code.lshr();
                }
            }
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }

        return typeTarget;
    }

    /**
     * Build the optimized binary operation that will take two primitive types from the stack and
     * subtract one from the other (T - T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveSub(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        if (fp8Class(regTarget.type()) instanceof ClassDesc fp8CD) {
            code.invokestatic(fp8CD, "$sub", MD_FP8Binary);
            return;
        }
        switch (regTarget.cd().descriptorString()) {
            case "I" -> Builder.adjustIntValue(code.isub(), regTarget.type());
            case "J" -> code.lsub();
            case "F" -> narrowFloat(code.fsub(), regTarget.type());
            case "D" -> code.dsub();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will subtract one XVM primitive type from another
     * (T - T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildXvmPrimitiveSub(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongSub(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec32"             -> buildDec32Sub(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec", "Dec64"      -> buildDec64Sub(bctx, code, (MultiSlot) regTarget, nArgId);
            case "Dec128"            -> buildDec128Sub(bctx, code, (MultiSlot) regTarget, nArgId);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will take two primitive types from the stack and
     * subtract one from the other (T ^ T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveXor(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> Builder.adjustIntValue(code.ixor(), regTarget.type());
            case "Z" -> code.ixor();
            case "J" -> code.lxor();
            default  -> throw new IllegalStateException("Unsupported carrier: " + regTarget.cd().descriptorString());
        }
    }

    /**
     * Build the optimized binary operation that will logically XOR two XVM primitive types
     * (T ^ T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildXvmPrimitiveXor(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      int          nArgId) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> buildLongLongXor(bctx, code, (MultiSlot) regTarget, nArgId);
            default -> throw new IllegalStateException("Unsupported type: " + regTarget.type().getValueString());
        }
    }
}
