package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

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
     * @param regTarget  that final result type
     */
    default void buildPrimitiveAdd(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.iadd();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> {
                code.ladd();
            }
            case "F" -> code.fadd();
            case "D" -> code.dadd();
            default  -> throw new IllegalStateException();
        }
    }

    /**
     * Build the optimized binary operation that will logically AND two primitive types from the
     * stack (T & T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
     */
    default void buildPrimitiveAnd(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.iand();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.land();
            default  -> throw new IllegalStateException();
        }
    }

    /**
     * Build the optimized unary operation that will produce the complement of the target value from
     * the top of the stack (~T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
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
     * @param regTarget  that final result type
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
     * @param regTarget  that final result type
     */
    default void buildPrimitiveMod(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        // TODO: convert remainder to a modulo
        switch (regTarget.cd().descriptorString()) {
            case "I" -> {
                code.irem();
                bctx.adjustIntValue(code, regTarget.type());
            }
            case "J" -> code.lrem();
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
     * @param regTarget  that final result type
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
     * @param regTarget  that final result type
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
     * @param regTarget  that final result type
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
     * @param regTarget that final result type
     * @param nArgValue the register containing the operation argument
     *
     * @return the result type
     */
    default TypeConstant buildPrimitiveShl(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           int          nArgValue) {
        regTarget.load(code);
        RegisterInfo regArg = bctx.loadArgument(code, nArgValue);
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
     * @param nArgValue  the operation argument id
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildPrimitiveShr(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           int          nArgValue) {
        regTarget.load(code);
        RegisterInfo regArg     = bctx.loadArgument(code, nArgValue);
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
     * @param nArgValue  the operation argument id
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildPrimitiveShrAll(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        regTarget.load(code);
        TypeConstant typeTarget = regTarget.type();

        switch (typeTarget.getSingleUnderlyingClass(false).getName()) {
            case "Int8", "UInt8"   -> code.sipush(0xFF).iand();
            case "Int16", "UInt16" -> code.ldc(0xFFFF).iand();
        }

        RegisterInfo regArg     = bctx.loadArgument(code, nArgValue);
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
     * @param regTarget  that final result type
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
     * Build the optimized binary operation that will take two primitive types from the stack and
     * subtract one from the other (T ^ T -> T).
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
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
