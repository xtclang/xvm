package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.MethodTypeDesc;

import org.xvm.javajit.registers.MultiSlot;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Ctx;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy Decimal types.
 */
public interface NumberSupportDec {

    /**
     * The name of the method to invoke for Decimal addition.
     */
    String METHOD_NAME_DEC_ADD = "$add";

    /**
     * The name of the method to invoke for Decimal division.
     */
    String METHOD_NAME_DEC_DIV = "$div";

    /**
     * The name of the method to invoke for Decimal modulus.
     */
    String METHOD_NAME_DEC_MOD = "$mod";

    /**
     * The name of the method to invoke for Decimal multiplication.
     */
    String METHOD_NAME_DEC_MUL = "$mul";

    /**
     * The name of the method to invoke for Decimal negation.
     */
    String METHOD_NAME_DEC_NEG = "$neg";

    /**
     * The name of the method to invoke for Decimal subtraction.
     */
    String METHOD_NAME_DEC_SUB = "$sub";

    /**
     * The method type descriptor for a method with two Dec32 arguments, returning a Dec32 result.
     */
    MethodTypeDesc MD_BinaryOp_Dec32 = MethodTypeDesc.of(CD_int, CD_Ctx, CD_int, CD_int);

    /**
     * The method type descriptor for a method with a Dec32 argument, returning a Dec32 result.
     */
    MethodTypeDesc MD_UnaryOp_Dec32 = MethodTypeDesc.of(CD_int, CD_Ctx, CD_int);

    /**
     * The method type descriptor for Dec64 addition.
     */
    MethodTypeDesc MD_BinaryOp_Dec64 = MethodTypeDesc.of(CD_long, CD_Ctx, CD_long, CD_long);

    /**
     * The method type descriptor for Dec64 addition.
     */
    MethodTypeDesc MD_UnaryOp_Dec64 = MethodTypeDesc.of(CD_long, CD_Ctx, CD_long);

    /**
     * The method type descriptor for Dec128 addition.
     */
    MethodTypeDesc MD_BinaryOp_Dec128 = MethodTypeDesc.of(CD_long, CD_Ctx, CD_long, CD_long, CD_long, CD_long);

    /**
     * The method type descriptor for Dec128 addition.
     */
    MethodTypeDesc MD_UnaryOp_Dec128 = MethodTypeDesc.of(CD_long, CD_Ctx, CD_long, CD_long);

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec32 types
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code int} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec32Add(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalAdd(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec32);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec64 types
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code long} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec64Add(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalAdd(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec64);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec128 types
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented two Java {@code long} primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec128Add(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                int nArgValue) {
        buildDecimalAdd(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Decimal types that are
     * (T + T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by one or more Java primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     * @param method     the method type descriptor for the operation
     */
    default void buildDecimalAdd(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                 int nArgValue, MethodTypeDesc method) {

        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_ADD, method);
    }

    /**
     * Build the optimized binary operation that will divide two XVM primitive Dec32 types
     * (T / T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code int} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec32Div(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalDiv(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec32);
    }

    /**
     * Build the optimized binary operation that will divide two XVM primitive Dec64 types
     * (T / T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code long} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec64Div(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalDiv(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec64);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec128 types
     * (T / T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented two Java {@code long} primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec128Div(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                int nArgValue) {
        buildDecimalDiv(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized binary operation that will divide two XVM primitive Decimal types
     * (T / T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by one or more Java primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     * @param method     the method type descriptor for the operation
     */
    default void buildDecimalDiv(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                 int nArgValue, MethodTypeDesc method) {
        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_DIV, method);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Dec32 types
     * (T % T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code int} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec32Mod(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalMod(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec32);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Dec64 types
     * (T % T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code long} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec64Mod(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalMod(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec64);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec128 types
     * (T % T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented two Java {@code long} primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec128Mod(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                int nArgValue) {
        buildDecimalMod(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Decimal types
     * (T % T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by one or more Java primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     * @param method     the method type descriptor for the operation
     */
    default void buildDecimalMod(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                 int nArgValue, MethodTypeDesc method) {

        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_MOD, method);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Dec32 types
     * (T * T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code int} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec32Mul(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalMul(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec32);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Dec64 types
     * (T * T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code long} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec64Mul(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalMul(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec64);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec128 types
     * (T * T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented two Java {@code long} primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec128Mul(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                int nArgValue) {
        buildDecimalMul(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized binary operation that will multiply two XVM primitive Decimal types
     * (T * T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by one or more Java primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     * @param method     the method type descriptor for the operation
     */
    default void buildDecimalMul(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                 int nArgValue, MethodTypeDesc method) {
        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_MUL, method);
    }

    /**
     * Build the optimized unary operation that will produce the negative of a XVM primitive Dec32
     * type (-T -> T).
     * <p>
     * The target should not have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildDec32Neg(BuildContext bctx, CodeBuilder code, MultiSlot regTarget) {
        buildDecNeg(bctx, code, regTarget, MD_UnaryOp_Dec32);
    }

    /**
     * Build the optimized unary operation that will produce the negative of a XVM primitive Dec64
     * type (-T -> T).
     * <p>
     * The target should not have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildDec64Neg(BuildContext bctx, CodeBuilder code, MultiSlot regTarget) {
        buildDecNeg(bctx, code, regTarget, MD_UnaryOp_Dec64);
    }

    /**
     * Build the optimized unary operation that will produce the negative of a XVM primitive Dec128
     * type (-T -> T).
     * <p>
     * The target should not have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildDec128Neg(BuildContext bctx, CodeBuilder code, MultiSlot regTarget) {
        buildDecNeg(bctx, code, regTarget, MD_UnaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized unary operation that will produce the negative of a XVM primitive Dec32
     * type (-T -> T).
     * <p>
     * The target should not have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param method     the method type descriptor for the operation
     */
    default void buildDecNeg(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                             MethodTypeDesc method) {
        bctx.loadCtx(code);
        regTarget.load(code);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_NEG, method);
    }

    /**
     * Build the optimized binary operation that will subtract two XVM primitive Dec32 types
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code int} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec32Sub(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalSub(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec32);
    }

    /**
     * Build the optimized binary operation that will subtract two XVM primitive Dec64 types
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented a Java {@code long} primitive value on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec64Sub(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                               int nArgValue) {
        buildDecimalSub(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec64);
    }

    /**
     * Build the optimized binary operation that will add two XVM primitive Dec128 types
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented two Java {@code long} primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildDec128Sub(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                int nArgValue) {
        buildDecimalSub(bctx, code, regTarget, nArgValue, MD_BinaryOp_Dec128);
        Builder.loadFromContext(code, CD_long, 0);
    }

    /**
     * Build the optimized binary operation that will subtract two XVM primitive Decimal types
     * (T - T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The result will be represented by one or more Java primitive values on the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     * @param method     the method type descriptor for the operation
     */
    default void buildDecimalSub(BuildContext bctx, CodeBuilder code, MultiSlot regTarget,
                                 int nArgValue, MethodTypeDesc method) {
        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(regTarget.cd(), METHOD_NAME_DEC_SUB, method);
    }
}
