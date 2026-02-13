package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_JavaMath;
import static org.xvm.javajit.Builder.MD_FloorModI;
import static org.xvm.javajit.Builder.MD_FloorModJ;
import static org.xvm.javajit.Builder.MD_UDivInt;
import static org.xvm.javajit.Builder.MD_UDivLong;
import static org.xvm.javajit.RegisterInfo.JAVA_STACK;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy numeric types.
 */
public interface NumberSupport {
    /**
     * Build the optimized binary operation that will add two primitive types from the stack
     * (T + T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildPrimitiveAdd(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.iadd();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.ladd();
            case "F" -> code.fadd();
            case "D" -> code.dadd();
            default  -> throw new IllegalStateException();
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
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildXvmPrimitiveAdd(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> {
                return buildLongLongAdd(bctx, code, (MultipleSlot) regTarget, nArgValue);
            }
            default  -> throw new IllegalStateException("Unsupported type: "
                    + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive types that are each
     * represented by two Java long primitive values.
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildLongLongAdd(BuildContext bctx,
                                          CodeBuilder  code,
                                          MultipleSlot regTarget,
                                          int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);
        buildLongLongAdd(bctx, code, slotL1, slotH1, slotL2, slotH2);
        return regTarget.type();
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive types that are each
     * represented by two Java long primitive values.
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the first value
     * @param slotH1     the slot containing the high long for the first value
     * @param slotL2     the slot containing the low long for the second value
     * @param slotH2     the slot containing the high long for the second value
     */
    default void buildLongLongAdd(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  int slotL2,
                                  int slotH2) {
        buildLongLongAdd(bctx, code, slotL1, slotH1, slotL2, slotH2, 0L, 0L);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive types that are each
     * represented by two Java long primitive values.
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The first XVM primitive is stored in slots, the XVM primitive to add is represented by the
     * two long parameters.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the value to add to
     * @param slotH1     the slot containing the high long for the value to add to
     * @param valueLow   the long value to add to the low value
     * @param valueHigh  the long value to add to the high value
     */
    default void buildLongLongAdd(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  long valueLow,
                                  long valueHigh) {
        buildLongLongAdd(bctx, code, slotL1, slotH1, JAVA_STACK, JAVA_STACK, valueLow, valueHigh);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive types that are each
     * represented by two Java long primitive values.
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the first value
     * @param slotH1     the slot containing the high long for the first value
     * @param slotL2     the slot containing the low long for the second value
     * @param slotH2     the slot containing the high long for the second value
     * @param valueLow   the long value to add to the low value if {@code slotL2} is
     *                   {@link RegisterInfo#JAVA_STACK}
     * @param valueHigh  the long value to add to the high value if {@code slotH2} is
     *                   {@link RegisterInfo#JAVA_STACK}
     */
    default void buildLongLongAdd(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  int slotL2,
                                  int slotH2,
                                  long valueLow,
                                  long valueHigh) {

        Label labelEnd = code.newLabel();

        // add the low long values
        Builder.load(code, CD_long, slotL1);
        loadSlotOrConstant(code, slotL2, valueLow);
        code.ladd();
        // store the result
        int slotSumLow = bctx.storeTempValue(code, CD_long);
        // add the high long values
        Builder.load(code, CD_long, slotH1);
        loadSlotOrConstant(code, slotH2, valueHigh);
        code.ladd();
        // store the result
        int slotSumHigh = bctx.storeTempValue(code, CD_long);

        // check for overflow
        // equivalent to  if (((l1L & l2L) | ((l1L | l2L) & ~lrL)) < 0) {
        Builder.load(code, CD_long, slotL1);
        loadSlotOrConstant(code, slotL2, valueLow);
        code.land();
        Builder.load(code, CD_long, slotL1);
        loadSlotOrConstant(code, slotL2, valueLow);
        code.lor();
        Builder.load(code, CD_long, slotSumLow);
        code.ldc(-1L).lxor().land().lor()
                .lconst_0().lcmp().ifge(labelEnd);
        // overflowed the low part so increment the high part
        Builder.load(code, CD_long, slotSumHigh);
        code.lconst_1().ladd();
        // store the incremented high part
        Builder.store(code, CD_long, slotSumHigh);
        code.labelBinding(labelEnd);
        // store the low and high result on the stack
        Builder.load(code, CD_long, slotSumLow);
        Builder.load(code, CD_long, slotSumHigh);
    }

    private void loadSlotOrConstant(CodeBuilder code, int slot, long value) {
        if (slot == RegisterInfo.JAVA_STACK) {
            code.loadConstant(value);
        } else {
            Builder.load(code, CD_long, slot);
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
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.land();
            case "Z" -> code.iand();
            default  -> throw new IllegalStateException();
        }
    }

    /**
     * Build the optimized binary operation that will logically AND two XVM primitive types
     * (T + T -> T).
     * <p>
     * Each type may be represented by one or more Java primitive types stored on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildXvmPrimitiveAnd(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> {
                return buildLongLongAnd(bctx, code, (MultipleSlot) regTarget, nArgValue);
            }
            default -> throw new IllegalStateException("Unsupported type: "
                    + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will logically AND two XVM primitives that are
     * each represented by two long Java primitive values.
     * (T & T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildLongLongAnd(BuildContext bctx,
                                          CodeBuilder  code,
                                          MultipleSlot regTarget,
                                          int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);

        // and the low long values
        Builder.load(code, CD_long, slotL1);
        Builder.load(code, CD_long, slotL2);
        code.land(); // store the result on the stack
        // and the high long values
        Builder.load(code, CD_long, slotH1);
        Builder.load(code, CD_long, slotH2);
        code.land();  // store the result on the stack

        return regTarget.type();
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
            case "I"  -> {
                code.iconst_m1().ixor();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "Z" -> code.iconst_m1().ixor();
            case "J" -> code.ldc(-1L).lxor();
            default  -> throw new IllegalStateException();
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
        TypeConstant typeTarget = regTarget.type();
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                boolean fUnsigned = typeTarget.getValueString().charAt(0) == 'U';
                if (fUnsigned) {
                    code.invokestatic(CD_Integer,"divideUnsigned",
                            MethodTypeDesc.of(CD_int, CD_int, CD_int));
                } else {
                    code.idiv();
                }
                bctx.adjustIntValue(code, typeTarget);
            }
            case "J" -> {
                boolean fUnsigned = typeTarget.getValueString().charAt(0) == 'U';
                if (fUnsigned) {
                    code.invokestatic(CD_Long,"divideUnsigned",
                            MethodTypeDesc.of(CD_long, CD_long, CD_long));
                } else {
                    code.ldiv();
                }
            }
            case "F" -> code.fdiv();
            case "D" -> code.ddiv();
            default  -> throw new IllegalStateException();
        }
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
        ClassDesc cd       = regTarget.cd();
        boolean   unsigned = regTarget.type().getValueString().startsWith("UInt");
        switch (cd.descriptorString()) {
            case "I" -> {
                if (unsigned) {
                    code.invokestatic(CD_Integer, "remainderUnsigned", MD_UDivInt);
                } else {
                    code.invokestatic(CD_JavaMath, "floorMod", MD_FloorModI);
                }
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> {
                if (unsigned) {
                    code.invokestatic(CD_Long, "remainderUnsigned", MD_UDivLong);
                } else {
                    code.invokestatic(CD_JavaMath, "floorMod", MD_FloorModJ);
                }
            }
            case "F" -> code.frem();
            case "D" -> code.drem();
            default  -> throw new IllegalStateException();
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
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.ineg();

                switch (regTarget.type().getSingleUnderlyingClass(false).getName()) {
                    case "Int8"  -> code.i2b();
                    case "Int16" -> code.i2s();
                    case "Int32" -> {}
                    case "UInt8", "UInt16", "UInt32"
                            -> bctx.throwUnsupported(code);
                    default -> throw new IllegalStateException();
                }
            }
            case "J" -> code.lneg();
            case "F" -> code.fneg();
            case "D" -> code.dneg();
            default  -> throw new IllegalStateException();
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
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.imul();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.lmul();
            case "F" -> code.fmul();
            case "D" -> code.dmul();
            default  -> throw new IllegalStateException();
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
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "Z" -> code.ior();
            case "J" -> code.lor();
            default  -> throw new IllegalStateException();
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
        regTarget.load(code);
        RegisterInfo regArg = bctx.loadArgument(code, nArgId);
        if (regArg.cd().equals(CD_long)) {
            code.l2i();
        } else if (!regArg.cd().equals(CD_int)) {
            throw new IllegalArgumentException("Expected Int argument for shl operation but is "
                    + regArg.cd().displayName());
        }

        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.ishl();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.lshl();
            default  -> throw new IllegalStateException();
        }
        return regTarget.type();
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
        regTarget.load(code);
        RegisterInfo regArg     = bctx.loadArgument(code, nArgId);
        TypeConstant typeTarget = regTarget.type();
        boolean      fUnsigned  = typeTarget.getValueString().charAt(0) == 'U';
        return buildPrimitiveShr(bctx, code, regTarget, regArg, fUnsigned);
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
    default TypeConstant buildPrimitiveShrAll(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgId) {
        regTarget.load(code);
        TypeConstant typeTarget = regTarget.type();

        switch (typeTarget.getSingleUnderlyingClass(false).getName()) {
            case "Int8", "UInt8"   -> code.sipush(0xFF).iand();
            case "Int16", "UInt16" -> code.ldc(0xFFFF).iand();
        }

        RegisterInfo regArg = bctx.loadArgument(code, nArgId);
        return buildPrimitiveShr(bctx, code, regTarget, regArg, true);
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
                bctx.adjustIntValue(code, typeTarget);
            }
            case "J" -> {
                if (fUnsigned) {
                    code.lushr();
                } else {
                    code.lshr();
                }
            }
            default  -> throw new IllegalStateException();
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
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.isub();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.lsub();
            case "F" -> code.fsub();
            case "D" -> code.dsub();
            default  -> throw new IllegalStateException();
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
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildXvmPrimitiveSub(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        switch (regTarget.type().getValueString()) {
            case "Int128", "UInt128" -> {
                return buildLongLongSub(bctx, code, (MultipleSlot) regTarget, nArgValue);
            }
            default  -> throw new IllegalStateException("Unsupported type: "
                    + regTarget.type().getValueString());
        }
    }

    /**
     * Build the optimized binary operation that will subtract one XVM primitive type from another
     * where each is represented by two Java long primitive values.
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default TypeConstant buildLongLongSub(BuildContext bctx,
                                          CodeBuilder  code,
                                          MultipleSlot regTarget,
                                          int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);
        buildLongLongSub(bctx, code, slotL1, slotH1, slotL2, slotH2);
        return regTarget.type();
    }

    /**
     * Build the optimized binary operation that will subtract one XVM primitive type from another
     * where each is represented by two Java long primitive values.
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the first value
     * @param slotH1     the slot containing the high long for the first value
     * @param slotL2     the slot containing the low long for the second value
     * @param slotH2     the slot containing the high long for the second value
     */
    default void buildLongLongSub(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  int slotL2,
                                  int slotH2) {
        buildLongLongSub(bctx, code, slotL1, slotH1, slotL2, slotH2, 0L, 0L);
    }

    /**
     * Build the optimized binary operation that will subtract one XVM primitive type from another
     * where each is represented by two Java long primitive values.
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The first XVM primitive is stored in slots, the XVM primitive to subtract is represented by
     * the two long parameters.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the value to subtract from
     * @param slotH1     the slot containing the high long for the value to subtract from
     * @param valueLow   the long value to subtract from the low value
     * @param valueHigh  the long value to subtract from the high value
     */
    default void buildLongLongSub(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  long valueLow,
                                  long valueHigh) {
        buildLongLongSub(bctx, code, slotL1, slotH1, JAVA_STACK, JAVA_STACK, valueLow, valueHigh);
    }

    /**
     * Build the optimized binary operation that will subtract one XVM primitive type from another
     * where each is represented by two Java long primitive values.
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the first value
     * @param slotH1     the slot containing the high long for the first value
     * @param slotL2     the slot containing the low long for the second value
     * @param slotH2     the slot containing the high long for the second value
     * @param valueLow   the long value to subtract from the low value if {@code slotL2} is
     *                   {@link RegisterInfo#JAVA_STACK}
     * @param valueHigh  the long value to subtract from the high value if {@code slotH2} is
     *                   {@link RegisterInfo#JAVA_STACK}
     */
    default void buildLongLongSub(BuildContext bctx,
                                  CodeBuilder code,
                                  int slotL1,
                                  int slotH1,
                                  int slotL2,
                                  int slotH2,
                                  long valueLow,
                                  long valueHigh) {

        Label labelEnd = code.newLabel();

        // subtract the low long values
        Builder.load(code, CD_long, slotL1);
        loadSlotOrConstant(code, slotL2, valueLow);
        code.lsub();
        // store the result
        int slotResultLow = bctx.storeTempValue(code, CD_long);
        // subtract the high long values
        Builder.load(code, CD_long, slotH1);
        loadSlotOrConstant(code, slotH2, valueHigh);
        code.lsub();
        // store the result
        int slotResultHigh = bctx.storeTempValue(code, CD_long);

        // check for borrow, which occurs if l1L <u l2L (less as unsigned)
        // which is equivalent to (l1L + MIN_VALUE) < (l2L + MIN_VALUE)
        Builder.load(code, CD_long, slotL1);
        code.ldc(Long.MIN_VALUE).ladd();
        loadSlotOrConstant(code, slotL2, valueLow);
        code.ldc(Long.MIN_VALUE).ladd();
        code.lcmp().ifge(labelEnd);

        // borrowed from the low part so decrement the high part
        Builder.load(code, CD_long, slotResultHigh);
        code.lconst_1().lsub();
        // store the decremented high part
        Builder.store(code, CD_long, slotResultHigh);
        code.labelBinding(labelEnd);
        // store the low and high result on the stack
        Builder.load(code, CD_long, slotResultLow);
        Builder.load(code, CD_long, slotResultHigh);
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
            case "I" -> {
                code.ixor();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "Z" -> code.ixor();
            case "J" -> code.lxor();
            default  -> throw new IllegalStateException();
        }
    }
}
