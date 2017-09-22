package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.op.*;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for all XVM machine code instructions, each of which is called an "op".
 */
public abstract class Op
    {
    // ----- Op interface ----------------------------------------------------------------------------------------------

    /**
     * @return the byte value that identifies the op-code, in the range 0-255
     */
    public abstract int getOpCode();

    /**
     * Process this op.
     * 
     * @param frame  current execution frame
     * @param iPC    instruction pointer
     * 
     * @return a positive iPC or a negative {@code R_}* value
     */
    public abstract int process(Frame frame, int iPC);

    /**
     * Write the op-code.
     * 
     * @param out  the DataOutput to write to
     * 
     * @throws IOException  if an error occurs while writing the op
     */
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(getOpCode());
        }

    // TODO stack depth and variable count impact calc


    // ----- static helpers --------------------------------------------------------------------------------------------

    /**
     * Read the ops for a particular method.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     *
     * @return an array of ops
     *
     * @throws IOException  if an error occurs reading the ops
     */
    public static Op[] readOps(DataInput in, Constant[] aconst)
            throws IOException
        {
        int  cOps = readMagnitude(in);
        Op[] aop  = new Op[cOps];
        for (int i = 0; i < cOps; ++i)
            {
            aop[i] = instantiate(in.readUnsignedByte(), in, aconst);;
            }

        return aop;
        }

    /**
     * Instantiate an Op object for the specified op code.
     *
     * @param nOp     the op-code
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     *
     * @return the new op instance
     */
    public static Op instantiate(int nOp, DataInput in, Constant[] aconst)
            throws IOException
        {
        switch (nOp)
            {
            case OP_NOP:
                return new Nop(in, aconst);
                break;
            case OP_LINE_1:
                return new Line_1(in, aconst);
                break;
            case OP_LINE_N:
                return new Line_N(in, aconst);
                break;
            case OP_BREAK:
                return new Break(in, aconst);
                break;
            case OP_ENTER:
                return new Enter(in, aconst);
                break;
            case OP_EXIT:
                return new Exit(in, aconst);
                break;
            case OP_GUARD:
                return new GuardStart(in, aconst);
                break;
            case OP_END_GUARD:
                return new GuardEnd(in, aconst);
                break;
            case OP_HANDLER:
                return new HandlerStart(in, aconst);
                break;
            case OP_END_HANDLER:
                return new HandlerEnd(in, aconst);
                break;
            case OP_GUARD_ALL:
                return new GuardAll(in, aconst);
                break;
            case OP_FINALLY:
                return new FinallyStart(in, aconst);
                break;
            case OP_END_FINALLY:
                return new FinallyEnd(in, aconst);
                break;
            case OP_THROW:
                return new Throw(in, aconst);
                break;
            case OP_ADD:
                return new Add(in, aconst);
                break;
            case OP_GOTO:
                return new GoTo(in, aconst);
                break;
            case OP_JMP:
                return new Jump(in, aconst);
                break;
            case OP_JMP_TRUE:
                return new JumpTrue(in, aconst);
                break;
            case OP_JMP_FALSE:
                return new JumpFalse(in, aconst);
                break;
            case OP_JMP_ZERO:
                return new JumpZero(in, aconst);
                break;
            case OP_JMP_NZERO:
                return new JumpNotZero(in, aconst);
                break;
            case OP_JMP_NULL:
                return new JumpNull(in, aconst);
                break;
            case OP_JMP_NNULL:
                return new JumpNotNull(in, aconst);
                break;
            case OP_JMP_EQ:
                return new JumpEq(in, aconst);
                break;
            case OP_JMP_NEQ:
                return new JumpNotEq(in, aconst);
                break;
            case OP_JMP_LT:
                return new JumpLt(in, aconst);
                break;
            case OP_JMP_LTE:
                return new JumpLte(in, aconst);
                break;
            case OP_JMP_GT:
                return new JumpGt(in, aconst);
                break;
            case OP_JMP_GTE:
                return new JumpGte(in, aconst);
                break;
            case OP_VAR:
                return new Var(in, aconst);
                break;
            case OP_IVAR:
                return new IVar(in, aconst);
                break;
            case OP_NVAR:
                return new NVar(in, aconst);
                break;
            case OP_INVAR:
                return new INVar(in, aconst);
                break;
            case OP_DVAR:
                return new DVar(in, aconst);
                break;
            case OP_DNVAR:
                return new DNVar(in, aconst);
                break;
            case OP_SVAR:
                return new SVar(in, aconst);
                break;
            case OP_TVAR:
                return new TVar(in, aconst);
                break;
            case OP_REF:
                return new Ref(in, aconst);
                break;
            case OP_MOV:
                return new Move(in, aconst);
                break;
            case OP_MOV_REF:
                return new MoveRef(in, aconst);
                break;
            case OP_NEG:
                return new Neg(in, aconst);
                break;
            case OP_INC:
                return new Inc(in, aconst);
                break;
            case OP_POSTINC:
                return new PostInc(in, aconst);
                break;
            case OP_PREINC:
                return new PreInc(in, aconst);
                break;
            case OP_P_GET:
                return new PGet(in, aconst);
                break;
            case OP_P_SET:
                return new PSet(in, aconst);
                break;
            case OP_P_POSTINC:
                return new PPostInc(in, aconst);
                break;
            case OP_P_PREINC:
                return new PPreInc(in, aconst);
                break;
            case OP_L_GET:
                return new LGet(in, aconst);
                break;
            case OP_L_SET:
                return new LSet(in, aconst);
                break;
            case OP_CALL_00:
                return new Call_00(in, aconst);
                break;
            case OP_CALL_01:
                return new Call_01(in, aconst);
                break;
            case OP_CALL_0N:
                return new Call_0N(in, aconst);
                break;
            case OP_CALL_0T:
                return new Call_0T(in, aconst);
                break;
            case OP_CALL_10:
                return new Call_10(in, aconst);
                break;
            case OP_CALL_11:
                return new Call_11(in, aconst);
                break;
            case OP_CALL_1N:
                return new Call_1N(in, aconst);
                break;
            case OP_CALL_1T:
                return new Call_1T(in, aconst);
                break;
            case OP_CALL_N0:
                return new Call_N0(in, aconst);
                break;
            case OP_CALL_N1:
                return new Call_N1(in, aconst);
                break;
            case OP_CALL_NN:
                return new Call_NN(in, aconst);
                break;
            case OP_CALL_NT:
                return new Call_NT(in, aconst);
                break;
            case OP_CALL_T0:
                return new Call_T0(in, aconst);
                break;
            case OP_CALL_T1:
                return new Call_T1(in, aconst);
                break;
            case OP_CALL_TN:
                return new Call_TN(in, aconst);
                break;
            case OP_CALL_TT:
                return new Call_TT(in, aconst);
                break;
            case OP_INVOKE_00:
                return new Invoke_00(in, aconst);
                break;
            case OP_INVOKE_01:
                return new Invoke_01(in, aconst);
                break;
            case OP_INVOKE_0N:
                return new Invoke_0N(in, aconst);
                break;
            case OP_INVOKE_0T:
                return new Invoke_0N(in, aconst);
                break;
            case OP_INVOKE_10:
                return new Invoke_10(in, aconst);
                break;
            case OP_INVOKE_11:
                return new Invoke_11(in, aconst);
                break;
            case OP_INVOKE_1N:
                return new Invoke_1N(in, aconst);
                break;
            case OP_INVOKE_1T:
                return new Invoke_1T(in, aconst);
                break;
            case OP_INVOKE_N0:
                return new Invoke_N0(in, aconst);
                break;
            case OP_INVOKE_N1:
                return new Invoke_N1(in, aconst);
                break;
            case OP_INVOKE_NN:
                return new Invoke_NN(in, aconst);
                break;
            case OP_INVOKE_NT:
                return new Invoke_NT(in, aconst);
                break;
            case OP_INVOKE_T0:
                return new Invoke_T0(in, aconst);
                break;
            case OP_INVOKE_T1:
                return new Invoke_T1(in, aconst);
                break;
            case OP_INVOKE_TN:
                return new Invoke_TN(in, aconst);
                break;
            case OP_INVOKE_TT:
                return new Invoke_TT(in, aconst);
                break;
            case OP_I_GET:
                return new IGet(in, aconst);
                break;
            case OP_I_SET:
                return new ISet(in, aconst);
                break;
            case OP_I_REF:
                return new IRef(in, aconst);
                break;
            case OP_NEW_1:
                return new New_1(in, aconst);
                break;
            case OP_NEW_N:
                return new New_N(in, aconst);
                break;
            case OP_NEW_0G:
                return new New_0G(in, aconst);
                break;
            case OP_NEW_1G:
                return new New_1G(in, aconst);
                break;
            case OP_NEW_NG:
                return new New_NG(in, aconst);
                break;
            case OP_CONSTR_1:
                return new Construct_1(in, aconst);
                break;
            case OP_CONSTR_N:
                return new Construct_N(in, aconst);
                break;
            case OP_ASSERT:
                return new Assert(in, aconst);
                break;
            case OP_ASSERT_T:
                return new AssertT(in, aconst);
                break;
            case OP_MBIND:
                return new MBind(in, aconst);
                break;
            case OP_FBIND:
                return new FBind(in, aconst);
                break;
            case OP_RETURN_0:
                return new Return_0(in, aconst);
                break;
            case OP_RETURN_1:
                return new Return_1(in, aconst);
                break;
            case OP_RETURN_N:
                return new Return_N(in, aconst);
                break;
            case OP_RETURN_T:
                return new Return_T(in, aconst);
                break;
            case OP_IS_ZERO:
                return new IsZero(in, aconst);
                break;
            case OP_IS_NZERO:
                return new IsNotZero(in, aconst);
                break;
            case OP_IS_NULL:
                return new IsNull(in, aconst);
                break;
            case OP_IS_NNULL:
                return new IsNotNull(in, aconst);
                break;
            case OP_IS_EQ:
                return new IsEq(in, aconst);
                break;
            case OP_IS_NEQ:
                return new IsNotEq(in, aconst);
                break;
            case OP_IS_LT:
                return new IsLt(in, aconst);
                break;
            case OP_IS_LTE:
                return new IsLte(in, aconst);
                break;
            case OP_IS_GT:
                return new IsGt(in, aconst);
                break;
            case OP_IS_GTE:
                return new IsGte(in, aconst);
                break;
            case OP_IS_NOT:
                return new IsNot(in, aconst);
                break;
            default:
                throw new IllegalStateException("op=" + byteToHexString(nOp));
            }
        }

    /**
     * Read an array of packed integers from the provided stream.
     *
     * @param in  the DataInput to read from
     *
     * @return an array of integers
     *
     * @throws IOException  if an error occurs reading the array
     */
    public static int[] readIntArray(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);

        int[] ai = new int[c];
        for (int i = 0; i < c; ++i)
            {
            ai[i] = readPackedInt(in);
            }
        return ai;
        }

    /**
     * Write an array of integers to the provided stream in a packed format.
     *
     * @param out  the DataOutput to write to
     * @param ai   the array of integers to write
     *
     * @throws IOException  if an error occurs writing the array
     */
    public static void writeIntArray(DataOutput out, int[] ai)
            throws IOException
        {
        int c = ai.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, ai[i]);        
            }
        }

    
    // ----- op-codes --------------------------------------------------------------------------------------------------

    public static final int OP_NOP             = 0x00;
    public static final int OP_LINE_1          = 0x01;
    public static final int OP_LINE_N          = 0x02;
    public static final int OP_BREAK           = 0x03;
    public static final int OP_ENTER           = 0x04;
    public static final int OP_EXIT            = 0x05;
    public static final int OP_GUARD           = 0x06;
    public static final int OP_END_GUARD       = 0x07;
    public static final int OP_HANDLER         = 0x08;
    public static final int OP_END_HANDLER     = 0x09;
    public static final int OP_GUARD_ALL       = 0x0A;
    public static final int OP_FINALLY         = 0x0B;
    public static final int OP_END_FINALLY     = 0x0C;
    public static final int OP_THROW           = 0x0D;
    public static final int OP_RESERVED_0E     = 0x0E;
    public static final int OP_RESERVED_0F     = 0x0F;

    public static final int OP_GOTO            = 0x10;
    public static final int OP_JMP             = 0x11;
    public static final int OP_JMP_TRUE        = 0x12;
    public static final int OP_JMP_FALSE       = 0x13;
    public static final int OP_JMP_ZERO        = 0x14;
    public static final int OP_JMP_NZERO       = 0x15;
    public static final int OP_JMP_NULL        = 0x16;
    public static final int OP_JMP_NNULL       = 0x17;
    public static final int OP_JMP_EQ          = 0x18;
    public static final int OP_JMP_NEQ         = 0x19;
    public static final int OP_JMP_LT          = 0x1A;
    public static final int OP_JMP_LTE         = 0x1B;
    public static final int OP_JMP_GT          = 0x1C;
    public static final int OP_JMP_GTE         = 0x1D;
    public static final int OP_JMP_TYPE        = 0x1E;
    public static final int OP_JMP_NTYPE       = 0x1F;

    public static final int OP_VAR             = 0x20;
    public static final int OP_IVAR            = 0x21;
    public static final int OP_NVAR            = 0x22;
    public static final int OP_INVAR           = 0x23;
    public static final int OP_DVAR            = 0x24;
    public static final int OP_DNVAR           = 0x25;
    public static final int OP_SVAR            = 0x26;
    public static final int OP_TVAR            = 0x27;
    public static final int OP_REF             = 0x28;
    public static final int OP_CREF            = 0x29;
    public static final int OP_CAST            = 0x2A;
    public static final int OP_MOV             = 0x2B;
    public static final int OP_MOV_REF         = 0x2C;
    public static final int OP_MOV_CREF        = 0x2D;
    public static final int OP_MOV_CAST        = 0x2E;
    public static final int OP_SWAP            = 0x2F;

    public static final int OP_ADD             = 0x30;
    public static final int OP_SUB             = 0x31;
    public static final int OP_MUL             = 0x32;
    public static final int OP_DIV             = 0x33;
    public static final int OP_MOD             = 0x34;
    public static final int OP_SHL             = 0x35;
    public static final int OP_SHR             = 0x36;
    public static final int OP_USHR            = 0x37;
    public static final int OP_AND             = 0x38;
    public static final int OP_OR              = 0x39;
    public static final int OP_XOR             = 0x3A;
    public static final int OP_DIVMOD          = 0x3B;
    public static final int OP_POS             = 0x3C;
    public static final int OP_NEG             = 0x3D;
    public static final int OP_COMPL           = 0x3E;
    public static final int OP_RESERVED_3F     = 0x3F;

    public static final int OP_INC             = 0x40;
    public static final int OP_DEC             = 0x41;
    public static final int OP_POSTINC         = 0x42;
    public static final int OP_POSTDEC         = 0x43;
    public static final int OP_PREINC          = 0x44;
    public static final int OP_PREDEC          = 0x45;
    public static final int OP_ADD_ASGN        = 0x46;
    public static final int OP_SUB_ASGN        = 0x47;
    public static final int OP_MUL_ASGN        = 0x48;
    public static final int OP_DIV_ASGN        = 0x49;
    public static final int OP_MOD_ASGN        = 0x4A;
    public static final int OP_SHL_ASGN        = 0x4B;
    public static final int OP_SHR_ASGN        = 0x4C;
    public static final int OP_USHR_ASGN       = 0x4D;
    public static final int OP_AND_ASGN        = 0x4E;
    public static final int OP_OR_ASGN         = 0x4F;
    public static final int OP_XOR_ASGN        = 0x50;
    public static final int OP_RESERVED_51     = 0x51;
    // ...
    public static final int OP_RESERVED_5F     = 0x5F;

    public static final int OP_P_GET           = 0x60;
    public static final int OP_P_SET           = 0x61;
    public static final int OP_P_REF           = 0x62;
    public static final int OP_P_CREF          = 0x63;
    public static final int OP_P_INC           = 0x64;
    public static final int OP_P_DEC           = 0x65;
    public static final int OP_P_POSTINC       = 0x66;
    public static final int OP_P_POSTDEC       = 0x67;
    public static final int OP_P_PREINC        = 0x68;
    public static final int OP_P_PREDEC        = 0x69;
    public static final int OP_P_ADD_ASGN      = 0x6A;
    public static final int OP_P_SUB_ASGN      = 0x6B;
    public static final int OP_P_MUL_ASGN      = 0x6C;
    public static final int OP_P_DIV_ASGN      = 0x6D;
    public static final int OP_P_MOD_ASGN      = 0x6E;
    public static final int OP_P_SHL_ASGN      = 0x6F;
    public static final int OP_P_SHR_ASGN      = 0x70;
    public static final int OP_P_USHR_ASGN     = 0x71;
    public static final int OP_P_AND_ASGN      = 0x72;
    public static final int OP_P_OR_ASGN       = 0x73;
    public static final int OP_P_XOR_ASGN      = 0x74;
    public static final int OP_RESERVED_65     = 0x75;
    public static final int OP_RESERVED_66     = 0x76;
    public static final int OP_RESERVED_67     = 0x77;
    public static final int OP_L_GET           = 0x78;
    public static final int OP_L_SET           = 0x79;
    public static final int OP_L_REF           = 0x7A;
    public static final int OP_RESERVED_6B     = 0x7B;
    //                      ...                ...
    public static final int OP_RESERVED_6F     = 0x7F;

    public static final int OP_CALL_00         = 0x80;
    public static final int OP_CALL_01         = 0x81;
    public static final int OP_CALL_0N         = 0x82;
    public static final int OP_CALL_0T         = 0x83;
    public static final int OP_CALL_10         = 0x84;
    public static final int OP_CALL_11         = 0x85;
    public static final int OP_CALL_1N         = 0x86;
    public static final int OP_CALL_1T         = 0x87;
    public static final int OP_CALL_N0         = 0x88;
    public static final int OP_CALL_N1         = 0x89;
    public static final int OP_CALL_NN         = 0x8A;
    public static final int OP_CALL_NT         = 0x8B;
    public static final int OP_CALL_T0         = 0x8C;
    public static final int OP_CALL_T1         = 0x8D;
    public static final int OP_CALL_TN         = 0x8E;
    public static final int OP_CALL_TT         = 0x8F;

    public static final int OP_INVOKE_00       = 0x90;
    public static final int OP_INVOKE_01       = 0x91;
    public static final int OP_INVOKE_0N       = 0x92;
    public static final int OP_INVOKE_0T       = 0x93;
    public static final int OP_INVOKE_10       = 0x94;
    public static final int OP_INVOKE_11       = 0x95;
    public static final int OP_INVOKE_1N       = 0x96;
    public static final int OP_INVOKE_1T       = 0x97;
    public static final int OP_INVOKE_N0       = 0x98;
    public static final int OP_INVOKE_N1       = 0x99;
    public static final int OP_INVOKE_NN       = 0x9A;
    public static final int OP_INVOKE_NT       = 0x9B;
    public static final int OP_INVOKE_T0       = 0x9C;
    public static final int OP_INVOKE_T1       = 0x9D;
    public static final int OP_INVOKE_TN       = 0x9E;
    public static final int OP_INVOKE_TT       = 0x9F;

    public static final int OP_I_GET           = 0xA0;
    public static final int OP_I_SET           = 0xA1;
    public static final int OP_I_REF           = 0xA2;
    public static final int OP_I_CREF          = 0xA3;
    public static final int OP_I_INC           = 0xA4;
    public static final int OP_I_DEC           = 0xA5;
    public static final int OP_I_POSTINC       = 0xA6;
    public static final int OP_I_POSTDEC       = 0xA7;
    public static final int OP_I_PREINC        = 0xA8;
    public static final int OP_I_PREDEC        = 0xA9;
    public static final int OP_I_ADD_ASGN      = 0xAA;
    public static final int OP_I_SUB_ASGN      = 0xAB;
    public static final int OP_I_MUL_ASGN      = 0xAC;
    public static final int OP_I_DIV_ASGN      = 0xAD;
    public static final int OP_I_MOD_ASGN      = 0xAE;
    public static final int OP_I_SHL_ASGN      = 0xAF;

    public static final int I_SHR_ASGN         = 0xA0;
    public static final int I_USHR_ASGN        = 0xA1;
    public static final int I_AND_ASGN         = 0xA2;
    public static final int I_OR_ASGN          = 0xA3;
    public static final int I_XOR_ASGN         = 0xA4;
    public static final int RESERVED_95        = 0xAE;
    //                      ...                ...
    public static final int RESERVED_9F        = 0xAF;

    public static final int OP_NEW_0           = 0xB0;
    public static final int OP_NEW_1           = 0xB1;
    public static final int OP_NEW_N           = 0xB2;
    public static final int OP_NEW_T           = 0xB3;
    public static final int OP_NEW_0G          = 0xB4;
    public static final int OP_NEW_1G          = 0xB5;
    public static final int OP_NEW_NG          = 0xB6;
    public static final int OP_NEW_TG          = 0xB7;
    public static final int OP_NEWC_0          = 0xB8;
    public static final int OP_NEWC_1          = 0xB9;
    public static final int OP_NEWC_N          = 0xBA;
    public static final int OP_NEWC_T          = 0xBB;
    public static final int OP_CONSTR_0        = 0xBC;
    public static final int OP_CONSTR_1        = 0xBD;
    public static final int OP_CONSTR_N        = 0xBE;
    public static final int OP_CONSTR_T        = 0xBF;

    public static final int OP_RTYPE_1         = 0xC0;
    public static final int OP_RTYPE_N         = 0xC1;
    public static final int OP_MATCH_2         = 0xC2;
    public static final int OP_MATCH_3         = 0xC3;
    public static final int OP_MATCH_N         = 0xC4;
    public static final int OP_ASSERT          = 0xC5;
    public static final int OP_ASSERT_T        = 0xC6;
    public static final int OP_ASSERT_V        = 0xC7;
    public static final int OP_MBIND           = 0xC8;
    public static final int OP_FBIND           = 0xC9;
    public static final int OP_FBINDN          = 0xCA;
    public static final int OP_RETURN_0        = 0xCB;
    public static final int OP_RETURN_1        = 0xCC;
    public static final int OP_RETURN_N        = 0xCD;
    public static final int OP_RETURN_T        = 0xCE;
    public static final int RESERVED_BF        = 0xCF;

    public static final int OP_IS_ZERO         = 0xD0;
    public static final int OP_IS_NZERO        = 0xD1;
    public static final int OP_IS_NULL         = 0xD2;
    public static final int OP_IS_NNULL        = 0xD3;
    public static final int OP_IS_EQ           = 0xD4;
    public static final int OP_IS_NEQ          = 0xD5;
    public static final int OP_IS_LT           = 0xD6;
    public static final int OP_IS_LTE          = 0xD7;
    public static final int OP_IS_GT           = 0xD8;
    public static final int OP_IS_GTE          = 0xD9;
    public static final int OP_IS_NOT          = 0xDA;
    public static final int OP_IS_TYPE         = 0xDB;
    public static final int OP_IS_NTYPE        = 0xDC;
    public static final int OP_IS_SVC          = 0xDD;
    public static final int OP_IS_CONST        = 0xDE;
    public static final int OP_IS_IMMT         = 0xDF;

    public static final int OP_COND            = 0xE0;
    public static final int OP_NCOND           = 0xE1;
    public static final int OP_ACOND           = 0xE2;
    public static final int OP_NACOND          = 0xE3;
    public static final int OP_TCOND           = 0xE4;
    public static final int OP_NTCOND          = 0xE5;
    public static final int OP_DCOND           = 0xE6;
    public static final int OP_NDCOND          = 0xE7;
    public static final int OP_PCOND           = 0xE8;
    public static final int OP_NPCOND          = 0xE9;
    public static final int OP_VCOND           = 0xEA;
    public static final int OP_NVCOND          = 0xEB;
    public static final int OP_XCOND           = 0xEC;
    public static final int OP_NXCOND          = 0xED;
    public static final int OP_END_COND        = 0xEE;
    public static final int RESERVED_DF        = 0xEF;
    
    
    // ----- pre-defined arguments -------------------------------------------------------------------------------------

    /**
     * Pre-defined argument mask. Pre-defined arguments are in the range -1 to -16, with any unused values in that
     * range reserved for use in a future revision of the XVM.
     */
    public static final int A_MASK = 0xFFFFFFF0;

    /**
     * Pre-defined argument: {@code frame.getFrameLocal()}
     */
    public static final int A_LOCAL = -1;

    /**
     * Pre-defined argument: {@code this:target}
     */
    public static final int A_TARGET = -2;

    /**
     * Pre-defined argument: {@code this:public}
     */
    public static final int A_PUBLIC = -3;

    /**
     * Pre-defined argument: {@code this:protected}
     */
    public static final int A_PROTECTED = -4;

    /**
     * Pre-defined argument: {@code this:private}
     */
    public static final int A_PRIVATE = -5;

    /**
     * Pre-defined argument: {@code this:struct}
     */
    public static final int A_STRUCT = -6;

    /**
     * Pre-defined argument: {@code this:frame}
     */
    public static final int A_FRAME = -7;

    /**
     * Pre-defined argument: {@code this:service}
     */
    public static final int A_SERVICE = -8;

    /**
     * Pre-defined argument: {@code this:module}
     */
    public static final int A_MODULE = -9;

    /**
     * Pre-defined argument: {@code this:type}
     */
    public static final int A_TYPE = -10;

    /**
     * Pre-defined argument: {@code super} (function).
     */
    public static final int A_SUPER = -11;


    // ----- return values from the Op.process() method ----------------------------------------------------------------

    /**
     * Result from process() method: execute the next op-code.
     */
    public static final int R_NEXT = -1;

    /**
     * Result from process() method: resume the previous frame execution.
     */
    public static final int R_RETURN = -2;

    /**
     * Result from process() method: process the exception placed in frame.m_hException.
     */
    public static final int R_EXCEPTION = -3;

    /**
     * Result from process() method: process the exception raised during return.
     */
    public static final int R_RETURN_EXCEPTION = -4;

    /**
     * Result from process() method: call the frame placed in frame.m_frameNext.
     */
    public static final int R_CALL = -5;

    /**
     * Result from process() method: some registers are not ready for a read; yield and repeat the same op-code.
     */
    public static final int R_REPEAT = -6;

    /**
     * Result from process() method: some assignments were deferred; yield and check the "waiting" registers before
     * executing the next op-code.
     */
    public static final int R_BLOCK = -7;

    /**
     * Result from process() method: some assignments were deferred; yield and check the "waiting" registers before
     * returning.
     */
    public static final int R_BLOCK_RETURN = -8;

    /**
     * Result from process() method: yield before executing the next op-code.
     */
    public static final int R_YIELD = -9;


    // ----- other constants -------------------------------------------------------------------------------------------

    /**
     * The first constant, constant #0, is at this index (which is a negative). For a constant whose index is {@code i},
     * it is encoded as: {@code CONSTANT_OFFSET - i}
     */
    public static final int CONSTANT_OFFSET = -17;

    /**
     * A stub for an op-code.
     */
    public static final Op[] STUB = new Op[] {Return_0.INSTANCE};
    }