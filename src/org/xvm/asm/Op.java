package org.xvm.asm;

import org.xvm.asm.op.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.proto.Frame;

import static org.xvm.util.Handy.readMagnitude;


/**
 * The ops.
 *
 * @author gg 2017.02.21
 */
public abstract class Op
    {
    // the maximum value for the constants in the const pool
    public static final int MAX_CONST_ID = 2_000_000_000;

    // indexes for pre-defined arguments
    public static final int A_LOCAL     = -MAX_CONST_ID;       // frame.getFrameLocal()
    public static final int A_TARGET    = -MAX_CONST_ID - 1;   // this:target
    public static final int A_PUBLIC    = -MAX_CONST_ID - 2;   // this:public
    public static final int A_PROTECTED = -MAX_CONST_ID - 3;   // this:protected
    public static final int A_PRIVATE   = -MAX_CONST_ID - 4;   // this:private
    public static final int A_STRUCT    = -MAX_CONST_ID - 5;   // this:struct
    public static final int A_FRAME     = -MAX_CONST_ID - 6;   // this:frame
    public static final int A_SERVICE   = -MAX_CONST_ID - 7;   // this:service
    public static final int A_MODULE    = -MAX_CONST_ID - 8;   // this:module
    public static final int A_TYPE      = -MAX_CONST_ID - 9;   // this:type
    public static final int A_SUPER     = -MAX_CONST_ID - 10;  // super (function)

    // ----- return values from the Op.process() method -----

    // execute the next op-code
    public static final int R_NEXT = -1;

    // resume the previous frame execution
    public static final int R_RETURN = -2;

    // process the exception placed in frame.m_hException
    public static final int R_EXCEPTION = -3;

    // process the exception raised during return
    public static final int R_RETURN_EXCEPTION = -4;

    // call the frame placed in frame.m_frameNext
    public static final int R_CALL = -5;

    // some registers are not ready for a read; yield and repeat the same op-code
    public static final int R_REPEAT = -6;

    // some assignments were deferred; yield and check the "waiting" registers
    // before executing the next op-code
    public static final int R_BLOCK = -7;

    // some assignments were deferred; yield and check the "waiting" registers
    // before returning
    public static final int R_BLOCK_RETURN = -8;

    // yield before executing the next op-code
    public static final int R_YIELD = -9;

    // an stub for an op-code
    public static final Op[] STUB = new Op[] {Return_0.INSTANCE};

    // returns a positive iPC or a negative R_*
    public abstract int process(Frame frame, int iPC);

    // write the op-code
    public void write(DataOutput out)
            throws IOException
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Read the ops for a particular method.
     * 
     * @param in  the DataInput to read from
     *
     * @return an array of ops
     * 
     * @throws IOException  if an error occurs reading the ops
     */
    public static Op[] readOps(DataInput in)
            throws IOException
        {
        int  cOps = readMagnitude(in);
        Op[] aop  = new Op[cOps];
        for (int i = 0; i < cOps; ++i)
            {
            Op op;
            switch (in.readUnsignedByte())
                {
                case OP_NOP:
                    op =  new Nop();
                    break;
                case OP_ENTER:
                    op =  new Enter();
                    break;
                case OP_EXIT:
                    op =  new Exit();
                    break;
                case OP_GUARD:
                    op =  new GuardStart(in);
                    break;
                case OP_END_GUARD:
                    op =  new GuardEnd(in);
                    break;
                case OP_HANDLER:
                    op =  new HandlerStart();
                    break;
                case OP_END_HANDLER:
                    op =  new HandlerEnd(in);
                    break;
                case OP_GUARD_ALL:
                    op =  new GuardAll(in);
                    break;
                case OP_FINALLY:
                    op =  new FinallyStart();
                    break;
                case OP_END_FINALLY:
                    op =  new FinallyEnd();
                    break;
                case OP_THROW:
                    op =  new Throw(in);
                    break;
                case OP_ADD:
                    op =  new Add(in);
                    break;
                case OP_GOTO:
                    op =  new GoTo(in);
                    break;
                case OP_JMP:
                    op =  new Jump(in);
                    break;
                case OP_JMP_TRUE:
                    op =  new JumpTrue(in);
                    break;
                case OP_JMP_FALSE:
                    op =  new JumpFalse(in);
                    break;
                case OP_JMP_ZERO:
                    op =  new JumpZero(in);
                    break;
                case OP_JMP_NZERO:
                    op =  new JumpNotZero(in);
                    break;
                case OP_JMP_NULL:
                    op =  new JumpNull(in);
                    break;
                case OP_JMP_NNULL:
                    op =  new JumpNotNull(in);
                    break;
                case OP_JMP_EQ:
                    op =  new JumpEq(in);
                    break;
                case OP_JMP_NEQ:
                    op =  new JumpNotEq(in);
                    break;
                case OP_JMP_LT:
                    op =  new JumpLt(in);
                    break;
                case OP_JMP_LTE:
                    op =  new JumpLte(in);
                    break;
                case OP_JMP_GT:
                    op =  new JumpGt(in);
                    break;
                case OP_JMP_GTE:
                    op =  new JumpGte(in);
                    break;
                case OP_VAR:
                    op =  new Var(in);
                    break;
                case OP_IVAR:
                    op =  new IVar(in);
                    break;
                case OP_NVAR:
                    op =  new NVar(in);
                    break;
                case OP_INVAR:
                    op =  new INVar(in);
                    break;
                case OP_DVAR:
                    op =  new DVar(in);
                    break;
                case OP_DNVAR:
                    op =  new DNVar(in);
                    break;
                case OP_SVAR:
                    op =  new SVar(in);
                    break;
                case OP_TVAR:
                    op =  new TVar(in);
                    break;
                case OP_REF:
                    op =  new Ref(in);
                    break;
                case OP_MOV:
                    op =  new Move(in);
                    break;
                case OP_MOV_REF:
                    op =  new MoveRef(in);
                    break;
                case OP_NEG:
                    op =  new Neg(in);
                    break;
                case OP_INC:
                    op =  new Inc(in);
                    break;
                case OP_POSTINC:
                    op =  new PostInc(in);
                    break;
                case OP_PREINC:
                    op =  new PreInc(in);
                    break;
                case OP_P_GET:
                    op =  new PGet(in);
                    break;
                case OP_P_SET:
                    op =  new PSet(in);
                    break;
                case OP_P_POSTINC:
                    op =  new PPostInc(in);
                    break;
                case OP_P_PREINC:
                    op =  new PPreInc(in);
                    break;
                case OP_L_GET:
                    op =  new LGet(in);
                    break;
                case OP_L_SET:
                    op =  new LSet(in);
                    break;
                case OP_CALL_00:
                    op =  new Call_00(in);
                    break;
                case OP_CALL_01:
                    op =  new Call_01(in);
                    break;
                case OP_CALL_0N:
                    op =  new Call_0N(in);
                    break;
                case OP_CALL_0T:
                    op =  new Call_0T(in);
                    break;
                case OP_CALL_10:
                    op =  new Call_10(in);
                    break;
                case OP_CALL_11:
                    op =  new Call_11(in);
                    break;
                case OP_CALL_1N:
                    op =  new Call_1N(in);
                    break;
                case OP_CALL_1T:
                    op =  new Call_1T(in);
                    break;
                case OP_CALL_N0:
                    op =  new Call_N0(in);
                    break;
                case OP_CALL_N1:
                    op =  new Call_N1(in);
                    break;
                case OP_CALL_NN:
                    op =  new Call_NN(in);
                    break;
                case OP_CALL_NT:
                    op =  new Call_NT(in);
                    break;
                case OP_CALL_T0:
                    op =  new Call_T0(in);
                    break;
                case OP_CALL_T1:
                    op =  new Call_T1(in);
                    break;
                case OP_CALL_TN:
                    op =  new Call_TN(in);
                    break;
                case OP_CALL_TT:
                    op =  new Call_TT(in);
                    break;
                case OP_INVOKE_00:
                    op =  new Invoke_00(in);
                    break;
                case OP_INVOKE_01:
                    op =  new Invoke_01(in);
                    break;
                case OP_INVOKE_0N:
                    op =  new Invoke_0N(in);
                    break;
                case OP_INVOKE_0T:
                    op =  new Invoke_0N(in);
                    break;
                case OP_INVOKE_10:
                    op =  new Invoke_10(in);
                    break;
                case OP_INVOKE_11:
                    op =  new Invoke_11(in);
                    break;
                case OP_INVOKE_1N:
                    op =  new Invoke_1N(in);
                    break;
                case OP_INVOKE_1T:
                    op =  new Invoke_1T(in);
                    break;
                case OP_INVOKE_N0:
                    op =  new Invoke_N0(in);
                    break;
                case OP_INVOKE_N1:
                    op =  new Invoke_N1(in);
                    break;
                case OP_INVOKE_NN:
                    op =  new Invoke_NN(in);
                    break;
                case OP_INVOKE_NT:
                    op =  new Invoke_NT(in);
                    break;
                case OP_INVOKE_T0:
                    op =  new Invoke_T0(in);
                    break;
                case OP_INVOKE_T1:
                    op =  new Invoke_T1(in);
                    break;
                case OP_INVOKE_TN:
                    op =  new Invoke_TN(in);
                    break;
                case OP_INVOKE_TT:
                    op =  new Invoke_TT(in);
                    break;
                case OP_I_GET:
                    op =  new IGet(in);
                    break;
                case OP_I_SET:
                    op =  new ISet(in);
                    break;
                case OP_I_REF:
                    op =  new IRef(in);
                    break;
                case OP_NEW_1:
                    op =  new New_1(in);
                    break;
                case OP_NEW_N:
                    op =  new New_N(in);
                    break;
                case OP_NEW_0G:
                    op =  new New_0G(in);
                    break;
                case OP_NEW_1G:
                    op =  new New_1G(in);
                    break;
                case OP_NEW_NG:
                    op =  new New_NG(in);
                    break;
                case OP_CONSTR_1:
                    op =  new Construct_1(in);
                    break;
                case OP_CONSTR_N:
                    op =  new Construct_N(in);
                    break;
                case OP_ASSERT:
                    op =  new Assert(in);
                    break;
                case OP_ASSERT_T:
                    op =  new AssertT(in);
                    break;
                case OP_MBIND:
                    op =  new MBind(in);
                    break;
                case OP_FBIND:
                    op =  new FBind(in);
                    break;
                case OP_RETURN_0:
                    op =  new Return_0();
                    break;
                case OP_RETURN_1:
                    op =  new Return_1(in);
                    break;
                case OP_RETURN_N:
                    op =  new Return_N(in);
                    break;
                case OP_RETURN_T:
                    op =  new Return_T(in);
                    break;
                case OP_IS_ZERO:
                    op =  new IsZero(in);
                    break;
                case OP_IS_NZERO:
                    op =  new IsNotZero(in);
                    break;
                case OP_IS_NULL:
                    op =  new IsNull(in);
                    break;
                case OP_IS_NNULL:
                    op =  new IsNotNull(in);
                    break;
                case OP_IS_EQ:
                    op =  new IsEq(in);
                    break;
                case OP_IS_NEQ:
                    op =  new IsNotEq(in);
                    break;
                case OP_IS_LT:
                    op =  new IsLt(in);
                    break;
                case OP_IS_LTE:
                    op =  new IsLte(in);
                    break;
                case OP_IS_GT:
                    op =  new IsGt(in);
                    break;
                case OP_IS_GTE:
                    op =  new IsGte(in);
                    break;
                case OP_IS_NOT:
                    op =  new IsNot(in);
                    break;
                default:
                    throw new UnsupportedOperationException();
                }
            aop[i] = op;
            }

        return aop;
        }

    // ----- helpers -----

    protected static int[] readIntArray(DataInput in)
            throws IOException
        {
        int c = in.readUnsignedByte();

        int[] ai = new int[c];
        for (int i = 0; i < c; i++)
            {
            ai[i] = in.readInt();
            }
        return ai;
        }

    protected static void writeIntArray(DataOutput out, int[] ai)
            throws IOException
        {
        int c = ai.length;
        out.write(c);

        for (int i = 0; i < c; i++)
            {
            out.writeInt(ai[i]);
            }
        }

    // ----- op-codes -----

    public static final int OP_NOP             = 0x0;
    public static final int OP_LINE_1          = 0x1;
    public static final int OP_LINE_N          = 0x2;
    public static final int OP_BREAK           = 0x3;
    public static final int OP_ENTER           = 0x4;
    public static final int OP_EXIT            = 0x5;
    public static final int OP_GUARD           = 0x6;
    public static final int OP_END_GUARD       = 0x7;
    public static final int OP_HANDLER         = 0x8;
    public static final int OP_END_HANDLER     = 0x9;
    public static final int OP_GUARD_ALL       = 0xA;
    public static final int OP_FINALLY         = 0xB;
    public static final int OP_END_FINALLY     = 0xC;
    public static final int OP_THROW           = 0xD;
    public static final int OP_RESERVED_0E     = 0xE;
    public static final int OP_RESERVED_0F     = 0xF;

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
    }