package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.Op.OP_IIP_DEC;
import static org.xvm.asm.Op.OP_IIP_DECA;
import static org.xvm.asm.Op.OP_IIP_DECB;
import static org.xvm.asm.Op.OP_IIP_INC;
import static org.xvm.asm.Op.OP_IIP_INCA;
import static org.xvm.asm.Op.OP_IIP_INCB;
import static org.xvm.asm.Op.OP_IP_DEC;
import static org.xvm.asm.Op.OP_IP_DECA;
import static org.xvm.asm.Op.OP_IP_DECB;
import static org.xvm.asm.Op.OP_IP_INC;
import static org.xvm.asm.Op.OP_IP_INCA;
import static org.xvm.asm.Op.OP_IP_INCB;

/**
 * An interface of default utility methods for implementing in-place operations.
 */
public interface InPlaceSupport
        extends NumberSupportInt128 {

    /**
     * @return the byte value that identifies the op-code, in the range 0-255
     */
    int getOpCode();

    /**
     * Build the primitive local ops.
     * <p>
     * In:  nothing on the Java stack
     * Out: the result on Java stack
     *
     * @param bctx  the current {@link BuildContext}
     * @param code  the {@link CodeBuilder} to generate byte codes
     * @param reg   the {@link RegisterInfo} for the register to perform the operation on
     */
    default void buildPrimitiveLocal(BuildContext bctx, CodeBuilder code, RegisterInfo reg) {
        int    opCode  = getOpCode();
        Label  labelOK = code.newLabel();
        String msg;

        if ("Char".equals(reg.type().getSingleUnderlyingClass(true).getName())) {
            // we need to code a range check for Char inc/dec ops
            code.iload(reg.slot());
            switch (opCode) {
                case OP_IP_DEC, OP_IIP_DEC, OP_IP_DECA, OP_IIP_DECA, OP_IP_DECB, OP_IIP_DECB:
                    // decrementing
                    code.loadConstant(0);
                    msg = "OutOfBounds: text.Char underflow";
                    break;
                case OP_IP_INC, OP_IIP_INC, OP_IP_INCA, OP_IIP_INCA, OP_IP_INCB, OP_IIP_INCB:
                    // incrementing
                    code.loadConstant(0x10FFFF);
                    msg = "OutOfBounds: text.Char overflow";
                    break;
                default:
                    throw new IllegalStateException();
            }
            code.if_icmpne(labelOK);
            Builder.throwOutOfBounds(code, msg);
            code.labelBinding(labelOK);
        }

        switch (reg.cd().descriptorString()) {
            case "I", "S", "B", "C", "Z":
                switch (opCode) {
                    case OP_IP_DEC, OP_IIP_DEC:
                        code.iinc(reg.slot(), -1);
                        break;

                    case OP_IP_INC, OP_IIP_INC:
                        code.iinc(reg.slot(), +1);
                        break;

                    case OP_IP_DECA, OP_IIP_DECA:
                        code.iload(reg.slot())
                                .iinc(reg.slot(), -1); // leaves the old value on Java stack
                        break;

                    case OP_IP_INCA, OP_IIP_INCA:
                        code.iload(reg.slot())
                                .iinc(reg.slot(), +1);
                        break;

                    case OP_IP_DECB, OP_IIP_DECB:
                        code.iinc(reg.slot(), -1)
                                .iload(reg.slot());
                        break;

                    case OP_IP_INCB, OP_IIP_INCB:
                        code.iinc(reg.slot(), +1)
                                .iload(reg.slot());
                        break;

                    default:
                        throw new IllegalStateException();
                }
                break;

            case "J":
                code.lload(reg.slot());
                switch (opCode) {
                    case OP_IP_DEC, OP_IIP_DEC:
                        code.lconst_1()
                                .lsub();
                        break;

                    case OP_IP_INC, OP_IIP_INC:
                        code.lconst_1()
                                .ladd();
                        break;

                    case OP_IP_DECA, OP_IIP_DECA:
                        code.dup2()
                                .lconst_1()
                                .lsub();
                        break;

                    case OP_IP_INCA, OP_IIP_INCA:
                        code.dup2()
                                .lconst_1()
                                .ladd();
                        break;

                    case OP_IP_DECB, OP_IIP_DECB:
                        code.lconst_1()
                                .lsub()
                                .dup2();
                        break;

                    case OP_IP_INCB, OP_IIP_INCB:
                        code.lconst_1()
                                .ladd()
                                .dup2();
                        break;

                    default:
                        throw new IllegalStateException();
                }
                code.lstore(reg.slot());
                break;

            case "F":
                code.fload(reg.slot());
                switch (opCode) {
                    case OP_IP_DEC, OP_IIP_DEC:
                        code.fconst_1()
                                .fsub();
                        break;

                    case OP_IP_INC, OP_IIP_INC:
                        code.fconst_1()
                                .fadd();
                        break;

                    case OP_IP_DECA, OP_IIP_DECA:
                        code.dup2()
                                .fconst_1()
                                .fsub();
                        break;

                    case OP_IP_INCA, OP_IIP_INCA:
                        code.dup2()
                                .fconst_1()
                                .fadd();
                        break;

                    case OP_IP_DECB, OP_IIP_DECB:
                        code.fconst_1()
                                .fsub()
                                .dup2();
                        break;

                    case OP_IP_INCB, OP_IIP_INCB:
                        code.fconst_1()
                                .fadd()
                                .dup2();
                        break;

                    default:
                        throw new IllegalStateException();
                }
                code.fstore(reg.slot());
                break;

            case "D":
                code.dload(reg.slot());
                switch (opCode) {
                    case OP_IP_DEC, OP_IIP_DEC:
                        code.dconst_1()
                                .dsub();
                        break;

                    case OP_IP_INC, OP_IIP_INC:
                        code.dconst_1()
                                .dadd();
                        break;

                    case OP_IP_DECA, OP_IIP_DECA:
                        code.dup2()
                                .dconst_1()
                                .dsub();
                        break;

                    case OP_IP_INCA, OP_IIP_INCA:
                        code.dup2()
                                .dconst_1()
                                .dadd();
                        break;

                    case OP_IP_DECB, OP_IIP_DECB:
                        code.dconst_1()
                                .dsub()
                                .dup2();
                        break;

                    case OP_IP_INCB, OP_IIP_INCB:
                        code.dconst_1()
                                .dadd()
                                .dup2();
                        break;

                    default:
                        throw new IllegalStateException();
                }
                code.dstore(reg.slot());
                break;

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Build the XVM primitive local ops.
     * <p>
     * Nothing is on the Java stack before this method executes. The result will be on the Java
     * stack when the method completes.
     *
     * @param bctx  the current BuildContext
     * @param code  the CodeBuilder to use to generate the operation byte codes
     * @param reg   the register containing the XVM prmitive value the operation is performed on
     */
    default void buildXvmPrimitiveLocal(BuildContext bctx, CodeBuilder code, RegisterInfo reg) {
        TypeConstant baseType = reg.type().removeNullable();
        String       typeName = baseType.getSingleUnderlyingClass(false).getName();
        int          op       = getOpCode();
        switch (typeName) {
            case "Int128", "UInt128":
                int[] slots = reg.slots();
                switch (getOpCode()) {
                    case OP_IP_DEC, OP_IIP_DEC:
                        buildLongLongSub(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                            .lstore(slots[0]);
                        break;

                    case OP_IP_INC, OP_IIP_INC:
                        buildLongLongAdd(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                            .lstore(slots[0]);
                        break;

                    case OP_IP_DECA, OP_IIP_DECA:
                        code.lload(slots[0])
                            .lload(slots[1]);
                        buildLongLongSub(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                            .lstore(slots[0]);
                        break;

                    case OP_IP_INCA, OP_IIP_INCA:
                        code.lload(slots[0])
                            .lload(slots[1]);
                        buildLongLongAdd(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                            .lstore(slots[0]);
                        break;

                    case OP_IP_DECB, OP_IIP_DECB:
                        buildLongLongSub(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                            .lstore(slots[0])
                            .lload(slots[0])
                            .lload(slots[1]);
                        break;

                    case OP_IP_INCB, OP_IIP_INCB:
                        buildLongLongAdd(bctx, code, slots[0], slots[1], 1L, 0L);
                        code.lstore(slots[1])
                                .lstore(slots[0])
                                .lload(slots[0])
                                .lload(slots[1]);
                        break;

                    default:
                        throw new IllegalStateException("Unsupported XVM primitive op " + op
                                + " on type: " + typeName);
                }
                break;

            default:
                throw new IllegalStateException("Unsupported XVM primitive type: " + typeName);
        }
    }
}
