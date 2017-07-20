package org.xvm.proto;

import org.xvm.proto.op.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;

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

    public static Op[] readOps(DataInput in)
            throws IOException
        {
        ArrayList<Op> list = new ArrayList<>();
        switch (in.readUnsignedByte())
            {
            case OP_NOP:
                list.add(new Nop());
                break;
            case OP_ENTER:
                list.add(new Enter());
                break;
            case OP_EXIT:
                list.add(new Exit());
                break;
            case OP_GUARD:
                list.add(new GuardStart(in));
                break;
            case OP_END_GUARD:
                list.add(new GuardEnd(in));
                break;
            case OP_HANDLER:
                list.add(new HandlerStart());
                break;
            case OP_END_HANDLER:
                list.add(new HandlerEnd(in));
                break;
            case OP_GUARD_ALL:
                list.add(new GuardAll(in));
                break;
            case OP_FINALLY:
                list.add(new FinallyStart());
                break;
            case OP_END_FINALLY:
                list.add(new FinallyEnd());
                break;
            case OP_THROW:
                list.add(new Throw(in));
                break;
            case OP_ADD:
                list.add(new Add(in));
                break;
            case OP_GOTO:
                list.add(new GoTo(in));
                break;
            case OP_JMP:
                list.add(new Jump(in));
                break;
            case OP_JMP_FALSE:
                list.add(new JumpFalse(in));
                break;
            case OP_VAR:
                list.add(new Var(in));
                break;
            case OP_IVAR:
                list.add(new IVar(in));
                break;
            case OP_NVAR:
                list.add(new NVar(in));
                break;
            case OP_INVAR:
                list.add(new INVar(in));
                break;
            case OP_DVAR:
                list.add(new DVar(in));
                break;
            case OP_DNVAR:
                list.add(new DNVar(in));
                break;
            case OP_SVAR:
                list.add(new SVar(in));
                break;
            case OP_TVAR:
                list.add(new TVar(in));
                break;
            case OP_REF:
                list.add(new Ref(in));
                break;
            case OP_MOV:
                list.add(new Move(in));
                break;
            case OP_MOV_REF:
                list.add(new MoveRef(in));
                break;
            case OP_NEG:
                list.add(new Neg(in));
                break;
            case OP_POSTINC:
                list.add(new PostInc(in));
                break;
            case OP_PREINC:
                list.add(new PreInc(in));
                break;
            case OP_P_GET:
                list.add(new PGet(in));
                break;
            case OP_P_SET:
                list.add(new PSet(in));
                break;
            case OP_P_POSTINC:
                list.add(new PPostInc(in));
                break;
            case OP_P_PREINC:
                list.add(new PPreInc(in));
                break;
            case OP_L_GET:
                list.add(new LGet(in));
                break;
            case OP_L_SET:
                list.add(new LSet(in));
                break;
            case OP_CALL_00:
                list.add(new Call_00(in));
                break;
            case OP_CALL_01:
                list.add(new Call_01(in));
                break;
            case OP_CALL_10:
                list.add(new Call_10(in));
                break;
            case OP_CALL_11:
                list.add(new Call_11(in));
                break;
            case OP_CALL_1N:
                list.add(new Call_1N(in));
                break;
            case OP_CALL_1T:
                list.add(new Call_1T(in));
                break;
            case OP_CALL_N0:
                list.add(new Call_N0(in));
                break;
            case OP_CALL_N1:
                list.add(new Call_N1(in));
                break;
            case OP_INVOKE_00:
                list.add(new Invoke_00(in));
                break;
            case OP_INVOKE_01:
                list.add(new Invoke_01(in));
                break;
            case OP_INVOKE_10:
                list.add(new Invoke_10(in));
                break;
            case OP_INVOKE_11:
                list.add(new Invoke_11(in));
                break;
            case OP_INVOKE_1N:
                list.add(new Invoke_1N(in));
                break;
            case OP_INVOKE_N0:
                list.add(new Invoke_N0(in));
                break;
            case OP_INVOKE_N1:
                list.add(new Invoke_N1(in));
                break;
            case OP_INVOKE_NN:
                list.add(new Invoke_NN(in));
                break;
            case OP_INVOKE_T1:
                list.add(new Invoke_T1(in));
                break;
            case OP_I_GET:
                list.add(new IGet(in));
                break;
            case OP_I_SET:
                list.add(new ISet(in));
                break;
            case OP_I_REF:
                list.add(new IRef(in));
                break;
            case OP_NEW_1:
                list.add(new New_1(in));
                break;
            case OP_NEW_N:
                list.add(new New_N(in));
                break;
            case OP_NEW_0G:
                list.add(new New_0G(in));
                break;
            case OP_NEW_1G:
                list.add(new New_1G(in));
                break;
            case OP_NEW_NG:
                list.add(new New_NG(in));
                break;
            case OP_CONSTR_1:
                list.add(new Construct_1(in));
                break;
            case OP_CONSTR_N:
                list.add(new Construct_N(in));
                break;
            case OP_ASSERT:
                list.add(new Assert(in));
                break;
            case OP_ASSERT_T:
                list.add(new AssertT(in));
                break;
            case OP_MBIND:
                list.add(new MBind(in));
                break;
            case OP_FBIND:
                list.add(new FBind(in));
                break;
            case OP_RETURN_0:
                list.add(new Return_0());
                break;
            case OP_RETURN_1:
                list.add(new Return_1(in));
                break;
            case OP_RETURN_N:
                list.add(new Return_N(in));
                break;
            case OP_RETURN_T:
                list.add(new Return_T(in));
                break;
            case OP_IS_ZERO:
                list.add(new IsZero(in));
                break;
            case OP_IS_NZERO:
                list.add(new IsNotZero(in));
                break;
            case OP_IS_NULL:
                list.add(new IsNull(in));
                break;
            case OP_IS_NNULL:
                list.add(new IsNotNull(in));
                break;
            case OP_IS_EQ:
                list.add(new IsEq(in));
                break;
            case OP_IS_GT:
                list.add(new IsGt(in));
                break;
            default:
                throw new UnsupportedOperationException();
            }
        return list.toArray(new Op[list.size()]);
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

    public static final int OP_POSTINC         = 0x40;
    public static final int OP_POSTDEC         = 0x41;
    public static final int OP_PREINC          = 0x42;
    public static final int OP_PREDEC          = 0x43;
    public static final int OP_ADD_ASGN        = 0x44;
    public static final int OP_SUB_ASGN        = 0x45;
    public static final int OP_MUL_ASGN        = 0x46;
    public static final int OP_DIV_ASGN        = 0x47;
    public static final int OP_MOD_ASGN        = 0x48;
    public static final int OP_SHL_ASGN        = 0x49;
    public static final int OP_SHR_ASGN        = 0x4A;
    public static final int OP_USHR_ASGN       = 0x4B;
    public static final int OP_AND_ASGN        = 0x4C;
    public static final int OP_OR_ASGN         = 0x4D;
    public static final int OP_XOR_ASGN        = 0x4E;
    public static final int OP_RESERVED_4F     = 0x4F;

    public static final int OP_P_GET           = 0x50;
    public static final int OP_P_SET           = 0x51;
    public static final int OP_P_REF           = 0x52;
    public static final int OP_P_CREF          = 0x53;
    public static final int OP_P_INC           = 0x54;
    public static final int OP_P_DEC           = 0x55;
    public static final int OP_P_POSTINC       = 0x56;
    public static final int OP_P_POSTDEC       = 0x57;
    public static final int OP_P_PREINC        = 0x58;
    public static final int OP_P_PREDEC        = 0x59;
    public static final int OP_P_ADD_ASGN      = 0x5A;
    public static final int OP_P_SUB_ASGN      = 0x5B;
    public static final int OP_P_MUL_ASGN      = 0x5C;
    public static final int OP_P_DIV_ASGN      = 0x5D;
    public static final int OP_P_MOD_ASGN      = 0x5E;
    public static final int OP_P_SHL_ASGN      = 0x5F;
    public static final int OP_P_SHR_ASGN      = 0x60;
    public static final int OP_P_USHR_ASGN     = 0x61;
    public static final int OP_P_AND_ASGN      = 0x62;
    public static final int OP_P_OR_ASGN       = 0x63;
    public static final int OP_P_XOR_ASGN      = 0x64;
    public static final int OP_RESERVED_65     = 0x65;
    public static final int OP_RESERVED_66     = 0x66;
    public static final int OP_RESERVED_67     = 0x67;
    public static final int OP_L_GET           = 0x68;
    public static final int OP_L_SET           = 0x69;
    public static final int OP_L_REF           = 0x6A;
    public static final int OP_RESERVED_6B     = 0x6B;
    //                      ...                ...
    public static final int OP_RESERVED_6F     = 0x6F;

    public static final int OP_CALL_00         = 0x70;
    public static final int OP_CALL_01         = 0x71;
    public static final int OP_CALL_0N         = 0x72;
    public static final int OP_CALL_0T         = 0x73;
    public static final int OP_CALL_10         = 0x74;
    public static final int OP_CALL_11         = 0x75;
    public static final int OP_CALL_1N         = 0x76;
    public static final int OP_CALL_1T         = 0x77;
    public static final int OP_CALL_N0         = 0x78;
    public static final int OP_CALL_N1         = 0x79;
    public static final int OP_CALL_NN         = 0x7A;
    public static final int OP_CALL_NT         = 0x7B;
    public static final int OP_CALL_T0         = 0x7C;
    public static final int OP_CALL_T1         = 0x7D;
    public static final int OP_CALL_TN         = 0x7E;
    public static final int OP_CALL_TT         = 0x7F;

    public static final int OP_INVOKE_00       = 0x80;
    public static final int OP_INVOKE_01       = 0x81;
    public static final int OP_INVOKE_0N       = 0x82;
    public static final int OP_INVOKE_0T       = 0x83;
    public static final int OP_INVOKE_10       = 0x84;
    public static final int OP_INVOKE_11       = 0x85;
    public static final int OP_INVOKE_1N       = 0x86;
    public static final int OP_INVOKE_1T       = 0x87;
    public static final int OP_INVOKE_N0       = 0x88;
    public static final int OP_INVOKE_N1       = 0x89;
    public static final int OP_INVOKE_NN       = 0x8A;
    public static final int OP_INVOKE_NT       = 0x8B;
    public static final int OP_INVOKE_T0       = 0x8C;
    public static final int OP_INVOKE_T1       = 0x8D;
    public static final int OP_INVOKE_TN       = 0x8E;
    public static final int OP_INVOKE_TT       = 0x8F;

    public static final int OP_I_GET           = 0x90;
    public static final int OP_I_SET           = 0x91;
    public static final int OP_I_REF           = 0x92;
    public static final int OP_I_CREF          = 0x93;
    public static final int OP_I_INC           = 0x94;
    public static final int OP_I_DEC           = 0x95;
    public static final int OP_I_POSTINC       = 0x96;
    public static final int OP_I_POSTDEC       = 0x97;
    public static final int OP_I_PREINC        = 0x98;
    public static final int OP_I_PREDEC        = 0x99;
    public static final int OP_I_ADD_ASGN      = 0x9A;
    public static final int OP_I_SUB_ASGN      = 0x9B;
    public static final int OP_I_MUL_ASGN      = 0x9C;
    public static final int OP_I_DIV_ASGN      = 0x9D;
    public static final int OP_I_MOD_ASGN      = 0x9E;
    public static final int OP_I_SHL_ASGN      = 0x9F;

    public static final int I_SHR_ASGN         = 0x90;
    public static final int I_USHR_ASGN        = 0x91;
    public static final int I_AND_ASGN         = 0x92;
    public static final int I_OR_ASGN          = 0x93;
    public static final int I_XOR_ASGN         = 0x94;
    public static final int RESERVED_95        = 0x9E;
    //                      ...                ...
    public static final int RESERVED_9F        = 0x9F;

    public static final int OP_NEW_0           = 0xA0;
    public static final int OP_NEW_1           = 0xA1;
    public static final int OP_NEW_N           = 0xA2;
    public static final int OP_NEW_T           = 0xA3;
    public static final int OP_NEW_0G          = 0xA4;
    public static final int OP_NEW_1G          = 0xA5;
    public static final int OP_NEW_NG          = 0xA6;
    public static final int OP_NEW_TG          = 0xA7;
    public static final int OP_NEWC_0          = 0xA8;
    public static final int OP_NEWC_1          = 0xA9;
    public static final int OP_NEWC_N          = 0xAA;
    public static final int OP_NEWC_T          = 0xAB;
    public static final int OP_CONSTR_0        = 0xAC;
    public static final int OP_CONSTR_1        = 0xAD;
    public static final int OP_CONSTR_N        = 0xAE;
    public static final int OP_CONSTR_T        = 0xAF;

    public static final int OP_RTYPE_1         = 0xB0;
    public static final int OP_RTYPE_N         = 0xB1;
    public static final int OP_MATCH_2         = 0xB2;
    public static final int OP_MATCH_3         = 0xB3;
    public static final int OP_MATCH_N         = 0xB4;
    public static final int OP_ASSERT          = 0xB5;
    public static final int OP_ASSERT_T        = 0xB6;
    public static final int OP_ASSERT_V        = 0xB7;
    public static final int OP_MBIND           = 0xB8;
    public static final int OP_FBIND           = 0xB9;
    public static final int OP_FBINDN          = 0xBA;
    public static final int OP_RETURN_0        = 0xBB;
    public static final int OP_RETURN_1        = 0xBC;
    public static final int OP_RETURN_N        = 0xBD;
    public static final int OP_RETURN_T        = 0xBE;
    public static final int RESERVED_BF        = 0xBF;

    public static final int OP_IS_ZERO         = 0xC0;
    public static final int OP_IS_NZERO        = 0xC1;
    public static final int OP_IS_NULL         = 0xC2;
    public static final int OP_IS_NNULL        = 0xC3;
    public static final int OP_IS_EQ           = 0xC4;
    public static final int OP_IS_NEQ          = 0xC5;
    public static final int OP_IS_LT           = 0xC6;
    public static final int OP_IS_LTE          = 0xC7;
    public static final int OP_IS_GT           = 0xC8;
    public static final int OP_IS_GTE          = 0xC9;
    public static final int OP_IS_NOT          = 0xCA;
    public static final int OP_IS_TYPE         = 0xCB;
    public static final int OP_IS_NTYPE        = 0xCC;
    public static final int OP_IS_SVC          = 0xCD;
    public static final int OP_IS_CONST        = 0xCE;
    public static final int OP_IS_IMMT         = 0xCF;

    public static final int OP_COND            = 0xD0;
    public static final int OP_NCOND           = 0xD1;
    public static final int OP_ACOND           = 0xD2;
    public static final int OP_NACOND          = 0xD3;
    public static final int OP_TCOND           = 0xD4;
    public static final int OP_NTCOND          = 0xD5;
    public static final int OP_DCOND           = 0xD6;
    public static final int OP_NDCOND          = 0xD7;
    public static final int OP_PCOND           = 0xD8;
    public static final int OP_NPCOND          = 0xD9;
    public static final int OP_VCOND           = 0xDA;
    public static final int OP_NVCOND          = 0xDB;
    public static final int OP_XCOND           = 0xDC;
    public static final int OP_NXCOND          = 0xDD;
    public static final int OP_END_COND        = 0xDE;
    public static final int RESERVED_DF        = 0xDF;
    }