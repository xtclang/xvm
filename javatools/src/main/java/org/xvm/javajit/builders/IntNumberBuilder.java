package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Collection;

import java.util.function.BiConsumer;

import org.xvm.asm.constants.PropertyInfo;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

/**
 * The builder for IntNumber types.
 */
public class IntNumberBuilder extends NumberBuilder {

    public IntNumberBuilder(TypeSystem typeSystem, TypeSystem.Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    protected Collection<PropertyInfo> getProperties() {
        return pool().typeIntNumber().ensureTypeInfo().getProperties().values();
    }

    @Override
    protected BiConsumer<CodeBuilder, JitMethodDesc> getCodeGenerator(String jitName) {
        return switch (jitName) {
            case "leftmostBit"             -> this::generateLeftmostBitGet;
            case "rightmostBit"            -> this::generateRightmostBitGet;
            case "leadingZeroCount"        -> this::generateLeadingZeroCountGet;
            case "trailingZeroCount"       -> this::generateTrailingZeroCountGet;
            default -> super.getCodeGenerator(jitName);
        };
    }

    /**
     * Assemble the static primitive accessor "leftmostBit$get$p" method, for example:
     * <pre>
     *     int leftmostBit$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateLeftmostBitGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    code.iload(slot);
                    switch (thisType.getSingleUnderlyingClass(false).getName()) {
                        case "Nibble"          -> code.ldc(0x0F).iand();
                        case "Int8", "UInt8"   -> code.ldc(0xFF).iand();
                        case "Int16", "UInt16" -> code.ldc(0xFFFF).iand();
                    }
                    code.invokestatic(CD_JavaInteger, "highestOneBit",
                                MethodTypeDesc.of(CD_int, CD_int));
                    Builder.adjustIntValue(code, thisType);
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    break;
                default:
                    throw new IllegalStateException();
            }
            box(code, thisType);
            code.areturn();
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelLow = code.newLabel();

                    // get the high bits first
                    code.lconst_0()
                        .lload(slotHigh)
                        .dup2()
                        .lconst_0()
                        .lcmp()
                        .ifeq(labelLow)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    box(code, thisType);
                    code.areturn();

                    // high value is zero, check the low value
                    code.labelBinding(labelLow)
                        // the long zero and duplicated high bits will be on the stack, pop them
                        .pop2()
                        .pop2()
                        // get and return the highestOneBit for the low value
                        .lload(slotLow)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                            MethodTypeDesc.of(CD_long, CD_long))
                        .lconst_0();
                    // box the result as a 128-bit Int/UInt
                    box(code, thisType);
                    code.areturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble the static primitive accessor "rightmostBit$get$p" method, for example:
     * <pre>
     *     int rightmostBit$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateRightmostBitGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "lowestOneBit",
                                MethodTypeDesc.of(CD_int, CD_int));
                    Builder.adjustIntValue(code, thisType);
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    break;
                default:
                    throw new IllegalStateException();
            }
            box(code, thisType);
            code.areturn();
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelHigh = code.newLabel();

                    // get the low bits first
                    code.lload(slotLow)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long))
                        .dup2()           // duplicate the low result
                        .lconst_0()       // load zero
                        .lcmp()           // compare result to zero
                        .ifeq(labelHigh); // if zero, do the high value

                        code.lconst_0();
                        box(code, thisType);
                        code.areturn();

                    // duplicated low result on the stack is zero, check the high value
                    code.labelBinding(labelHigh)
                        .lload(slotHigh)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    box(code, thisType);
                    code.areturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble the static primitive accessor "leadingZeroCount$get$p" method, for example:
     * <pre>
     *     long leadingZeroCount$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateLeadingZeroCountGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd = JitTypeDesc.getJavaPrimitive(thisType);
            assert cd != null;

            int slot      = code.parameterSlot(0);
            int bitLength = getBitLength();

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    Label labelEnd  = code.newLabel();
                    int   adjust    = 32 - bitLength;
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_int))
                        .dup()
                        .ifeq(labelEnd)
                        .loadConstant(adjust)
                        .isub()
                        .labelBinding(labelEnd)
                        .i2l()
                        .lreturn();
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .i2l()
                        .lreturn();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelLow = code.newLabel();

                    // get the high bits first
                    code.lload(slotHigh)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .dup()                    // duplicate the hig result
                        .loadConstant(64)   // compare result to 64
                        .if_icmpeq(labelLow)      // if result is 64, do the low value
                        .i2l()                    // else convert the result to a long (Int64)
                        .lreturn()                // and return
                        // duplicated high result on the stack is 64, calculate the low value
                        .labelBinding(labelLow)
                        .lload(slotLow)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .iadd()     // high result (64) and low result are on the stack, add them
                        .i2l()      // convert to long and return
                        .lreturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble the static primitive accessor "trailingZeroCount$get$p" method, for example:
     * <pre>
     *     long trailingZeroCount$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateTrailingZeroCountGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            assert cd != null;

            int bitLength = getBitLength();
            int slot      = code.parameterSlot(0);

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    Label labelEnd  = code.newLabel();
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_int))
                        .dup()
                        .loadConstant(32)
                        .if_icmpne(labelEnd)
                        .pop()
                        .loadConstant(bitLength)
                        .labelBinding(labelEnd)
                        .i2l()
                        .lreturn();
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .i2l()
                        .lreturn();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelHigh = code.newLabel();

                    // get the low bits first
                    code.lload(slotLow)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .dup()                    // duplicate the low result
                        .loadConstant(64)   // compare result to 64
                        .if_icmpeq(labelHigh)     // if result is 64, do the high value
                        .i2l()                    // else convert the result to a long (Int64)
                        .lreturn()                // and return
                        // duplicated low result on the stack is 64, calculate the high value
                        .labelBinding(labelHigh)
                        .lload(slotHigh)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .iadd()     // low result (64) and high result are on the stack, add them
                        .i2l()      // convert to long and return
                        .lreturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }
}
