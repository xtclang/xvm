package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.RegisterInfo.JAVA_STACK;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy 128-bit integer types.
 */
public interface NumberSupportInt128 {
    /**
     * The {@link ClassDesc} for {@code java.lang.Math}.
     */
    ClassDesc CD_Math = ClassDesc.of("java.lang.Math");

    /**
     * The name of the {@code multiplyHigh} method in {@code java.lang.Math}.
     */
    String Math_MultiplyHigh = "multiplyHigh";

    /**
     * The {@link MethodTypeDesc} for {@code java.lang.Math.multiplyHigh(long, long)}.
     */
    MethodTypeDesc MD_MultiplyHigh = MethodTypeDesc.of(CD_long, CD_long, CD_long);

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
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value and below that will be the low long value.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildLongLongAdd(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);
        buildLongLongAdd(bctx, code, slotL1, slotH1, slotL2, slotH2);
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
     * the stack will be the high long value, and below that will be the low long value.
     *
     * @param code       the code builder to add the op codes to
     * @param bctx       the current build context
     * @param slotL1     the slot containing the low long for the value to add to
     * @param slotH1     the slot containing the high long for the value to add to
     * @param valueLow   the long value to add to the low value
     * @param valueHigh  the long value to add to the high value
     */
    default void buildLongLongAdd(BuildContext bctx,
                                  CodeBuilder  code,
                                  int          slotL1,
                                  int          slotH1,
                                  long         valueLow,
                                  long         valueHigh) {
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
    default void buildLongLongAnd(BuildContext bctx,
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
    }

    /**
     * Build the optimized binary operation that will produce the complement of a XVM primitive
     * that is represented by two long Java primitive values (~T -> T).
     * <p>
     * The target register should not have been loaded to the stack.
     *
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildLongLongCompl(CodeBuilder code, MultipleSlot regTarget) {

        int slotLow  = regTarget.slot(0);
        int slotHigh = regTarget.slot(1);
        Builder.load(code, CD_long, slotLow);
        code.ldc(-1L).lxor();
        Builder.load(code, CD_long, slotHigh);
        code.ldc(-1L).lxor();
    }

    /**
     * Build the optimized binary operation that will divide two XVM primitive types each
     * represented by two Java long primitives
     * (T / T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildLongLongDiv(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Build the optimized binary operation that will produce the modulo of two XVM primitive types
     * each represented by two Java long primitives
     * (T % T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildLongLongMod(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Build the optimized binary operation that will multiply two XVM 128 bit integer primitives
     * that are each represented by two long Java primitive values.
     * (T * T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the identifier of the register containing the operation argument
     */
    default void buildLongLongMul(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgId) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgId);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);

        // 1. Calculate low long result (l1 * l2)
        code.lload(slotL1) // Load l1
            .lload(slotL2) // Load l2
            .lmul();       // low long result (stack: [low, low_2])

        // 2. Calculate high long result
        code.lload(slotH1) // Load h1
            .lload(slotL2) // Load l2
            .invokestatic(CD_Math,Math_MultiplyHigh, MD_MultiplyHigh) // High bits of l1*l2
            .lload(slotL1) // Load l1
            .lload(slotH2) // Load h2
            .lmul()        // Low bits of l1*h2
            .ladd()        // accumulate
            .lload(slotH1) // Load h1
            .lload(slotL2) // Load l2
            .lmul()        // low bits of h1*l2
            .ladd();       // high long result (stack: [low, low_2, high, high_2])
    }

    /**
     * Build the optimized unary operation that will produce negative of a XVM primitive type
     * that is stored as two Java long values
     * (-T -> T).
     * <p>
     * The target should not have been loaded to the stack.
     *
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     */
    default void buildLongLongNeg(CodeBuilder  code, MultipleSlot regTarget) {

        int   slotLow      = regTarget.slot(0);
        int   slotHigh     = regTarget.slot(1);
        Label labelNotZero = code.newLabel();
        Label labelDone    = code.newLabel();

        // 1. Calculate resLow
        code.lload(slotLow) // load the low long value
            .lneg();        //  low result is -low (stack: [low, low_2])

        // 2. Prepare for high calculation
        code.lload(slotLow)      // Load low again for comparison
            .lconst_0()
            .lcmp()              // Compare low to 0
            .ifne(labelNotZero); // If low != 0, jump to high bit inversion

        // 3. Case: low == 0
        code.lload(slotHigh)
            .lneg()
            .goto_(labelDone);

        // 4. Case: low != 0
        code.labelBinding(labelNotZero)
            .lload(slotHigh)          // Load high
            .loadConstant(-1L)  // Load -1 (all ones)
            .lxor();                  // high result = ~high

        // Done
        code.labelBinding(labelDone); // the stack is [low, low_2, high, high_2]
    }

    /**
     * Build the optimized binary operation that will logically OR two XVM primitives that are
     * each represented by two long Java primitive values.
     * (T | T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildLongLongOr(BuildContext bctx,
                                 CodeBuilder  code,
                                 MultipleSlot regTarget,
                                 int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);

        // or the low long values
        Builder.load(code, CD_long, slotL1);
        Builder.load(code, CD_long, slotL2);
        code.lor(); // store the result on the stack
        // or the high long values
        Builder.load(code, CD_long, slotH1);
        Builder.load(code, CD_long, slotH2);
        code.lor();  // store the result on the stack
    }

    /**
     * Build the optimized binary operation that will logically shift left a XVM primitive that
     * is represented by two long Java primitive values.
     * (T << T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The argument register should be a Java {@code int} primitive value
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value, and below that will be the low long value.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     */
    default void buildLongLongShl(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgId) {

        RegisterInfo regArg    = bctx.ensureRegister(code, nArgId);
        int          slotLow   = regTarget.slot(0);
        int          slotHigh  = regTarget.slot(1);
        Label        labelLt64 = code.newLabel();
        Label        labelZero = code.newLabel();
        Label        labelEnd  = code.newLabel();

        // load the argument, which will also do a conversion to an int masked with 0x7F
        loadInt128ShiftArg(regArg, code);
        // store in a register so we do not have to repeat the conversion
        int slotArg = bctx.storeTempValue(code, CD_int);

        // load the argument and compare to zero
        code.iload(slotArg)
                .iconst_0()
                .if_icmpeq(labelZero);

        // load the argument and compare to 64
        code.iload(slotArg);
        code.bipush(64);
        code.if_icmplt(labelLt64);

        // Case: arg >= 64, we shift the low which becomes the high, the new low will be zero
        code.lconst_0()           // the new low long will be zero, so load zero to the stack
            .lload(slotLow)       // load the low long value
            .iload(slotArg)       // load the arg
            .bipush(64).isub() // arg - 64
            .lshl()               // low << (n - 64)
            .goto_(labelEnd);     // done, stack is [new_low, new_low2, new_high, new_high2]

        // Case: 0 < n < 64
        code.labelBinding(labelLt64)
            .lload(slotLow)   // Low part calculation
            .iload(slotArg)   // shift the low long value by the arg
            .lshl()           // the new low is on the stack [new_low, new_low2]
            .lload(slotHigh)  // High part calculation
            .iload(slotArg)
            .lshl()           // left shift the high long value
            .lload(slotLow)   // Load low
            .bipush(64)    // load int 64
            .iload(slotArg)   // load the arg
            .isub()           // top of stack is (64 - arg)
            .lushr()          // low unsigned right shift by (64 - arg)
            .lor()            // new shifted high value OR'ed by right shifted low value
            .goto_(labelEnd); // the stack is [new_low, new_low2, new_high, new_high2]

        // Case n == 0 (effectively a no-op shift)
        code.labelBinding(labelZero)
            .lload(slotLow)
            .lload(slotHigh);

        // done
        code.labelBinding(labelEnd);
    }

    /**
     * Build the optimized binary operation that will logically shift right a XVM primitive that
     * is represented by two long Java primitive values.
     * (T >> T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     * <p>
     * The argument register should be a Java {@code int} primitive value
     * <p>
     * The result will be represented by two Java long primitive values on the stack. The top of
     * the stack will be the high long value, and below that will be the low long value.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgId     the register containing the operation argument
     * @param fUnsigned  true if the shift is unsigned, false if signed
     */
    default void buildLongLongShr(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgId,
                                  boolean      fUnsigned) {

        RegisterInfo regArg    = bctx.ensureRegister(code, nArgId);
        int          slotLow   = regTarget.slot(0);
        int          slotHigh  = regTarget.slot(1);
        Label        labelLt64 = code.newLabel();
        Label        labelZero = code.newLabel();
        Label        labelEnd  = code.newLabel();

        // load the argument, which will also do a conversion to an int masked with 0x7F
        loadInt128ShiftArg(regArg, code);
        // store in a register so we do not have to repeat the conversion
        int slotArg = bctx.storeTempValue(code, CD_int);

        // load the argument and compare to zero
        code.iload(slotArg)
            .iconst_0()
            .if_icmpeq(labelZero);

        // load the argument and compare to 64
        code.iload(slotArg);
        code.bipush(64);
        code.if_icmplt(labelLt64);

        if (fUnsigned) {
            // Case: arg >= 64, we shift the high which becomes the low, the new high will be zero
            code.lload(slotHigh)      // load the high long value
                .iload(slotArg)       // load the arg
                .bipush(64).isub() // arg - 64
                .lushr()              // high >>> (n - 64) - stack now has [new_low, new_low2]
                .lconst_0()           // the new high is zero for an unsigned shift
                .goto_(labelEnd);     // done, stack is [new_low, new_low2, new_high, new_high2]
        } else {
            // Case: arg >= 64, we shift the high which becomes the low,
            // the new high will be sign extended
            code.lload(slotHigh)      // load the high long value
                .iload(slotArg)       // load the arg
                .bipush(64).isub() // arg - 64
                .lshr()               // high >> (n - 64) - stack now has [new_low, new_low2]
                .lload(slotHigh)      // load the high long value
                .bipush(63).lshr() // high >> 63 results in all 0s or all 1s depending on sign bit
                .goto_(labelEnd);     // done, stack is [new_low, new_low2, new_high, new_high2]
        }

        // Case: 0 < n < 64
        code.labelBinding(labelLt64)
            .lload(slotLow)   // Low part calculation
            .iload(slotArg)
            .lushr()          // shift the low long value by the arg
            .lload(slotHigh)  // load the high long value
            .bipush(64)    // load 64
            .iload(slotArg)   // load the rgument
            .isub()           // top of stack is (64 - arg)
            .lshl().lor()     // or the shifted low by the shifted high
            // the new low is on the stack [new_low, new_low2]
            .lload(slotHigh)  // High part calculation
            .iload(slotArg);

        if (fUnsigned) {
            code.lushr();     // unsigned right shift of the high part
        } else {
            code.lshr();      // signed right shift of the high part
        }

        code.goto_(labelEnd); // the stack is [new_low, new_low2, new_high, new_high2]

        // Case n == 0 (effectively a no-op shift)
        code.labelBinding(labelZero)
            .lload(slotLow)
            .lload(slotHigh);

        // done
        code.labelBinding(labelEnd);
    }

    /**
     * Load the shift argument for an Int128 or UInt128 shift left operation.
     * <p>
     * To remain consistent with other Java shift operations, the shift argument is masked with the
     * maximum number of bits that can be shifted (in this case seven bits, 0x7F).
     *
     * @param regArg  the register containing the shift argument
     * @param code    the code builder to use
     */
    private void loadInt128ShiftArg(RegisterInfo regArg, CodeBuilder code) {
        regArg.load(code);
        if (regArg.cd().equals(CD_long)) {
            code.l2i();
        } else if (!regArg.cd().equals(CD_int)) {
            throw new IllegalArgumentException("Expected Int argument for shl operation but is "
                    + regArg.cd().displayName());
        }
        code.bipush(0x7F).iand(); // arg && 7F to work like Java does for left shift
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
    default void buildLongLongSub(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);
        buildLongLongSub(bctx, code, slotL1, slotH1, slotL2, slotH2);
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
                                  CodeBuilder  code,
                                  int          slotL1,
                                  int          slotH1,
                                  int          slotL2,
                                  int          slotH2) {
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
                                  CodeBuilder  code,
                                  int          slotL1,
                                  int          slotH1,
                                  long         valueLow,
                                  long         valueHigh) {
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
                                  CodeBuilder  code,
                                  int          slotL1,
                                  int          slotH1,
                                  int          slotL2,
                                  int          slotH2,
                                  long         valueLow,
                                  long         valueHigh) {

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
     * Build the optimized binary operation that will logically XOR two XVM primitives that are
     * each represented by two long Java primitive values.
     * (T ^ T -> T).
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     */
    default void buildLongLongXor(BuildContext bctx,
                                  CodeBuilder  code,
                                  MultipleSlot regTarget,
                                  int          nArgValue) {

        MultipleSlot regArg = (MultipleSlot) bctx.ensureRegister(code, nArgValue);

        int slotL1 = regTarget.slot(0);
        int slotH1 = regTarget.slot(1);
        int slotL2 = regArg.slot(0);
        int slotH2 = regArg.slot(1);

        // xor the low long values
        Builder.load(code, CD_long, slotL1);
        Builder.load(code, CD_long, slotL2);
        code.lxor(); // store the result on the stack
        // xor the high long values
        Builder.load(code, CD_long, slotH1);
        Builder.load(code, CD_long, slotH2);
        code.lxor();  // store the result on the stack
    }
}
