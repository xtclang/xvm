package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import java.util.function.Consumer;

// Opcodes
public enum Op {

  NOP         /*0x00*/ (Op::todo),
  LINE_1      /*0x01*/ (X -> line_n(X,1)),
  LINE_2      /*0x02*/ (X -> line_n(X,2)),
  LINE_3      /*0x03*/ (X -> line_n(X,3)),
  LINE_N      /*0x04*/ (X -> line_n(X,X.u31())),
  ENTER       /*0x05*/ (Op::enter),
  EXIT        /*0x06*/ (Op::todo),
  GUARD       /*0x07*/ (Op::todo),
  GUARD_END   /*0x08*/ (Op::todo),
  CATCH       /*0x09*/ (Op::todo),
  CATCH_END   /*0x0A*/ (Op::todo),
  GUARD_ALL   /*0x0B*/ (Op::todo),
  FINALLY     /*0x0C*/ (Op::todo),
  FINALLY_END /*0x0D*/ (Op::todo),
  THROW       /*0x0E*/ (Op::todo),
  RSVD_0F     /*0x0F*/ (Op::todo),
  CALL_00     /*0x10*/ (Op::todo),
  CALL_01     /*0x11*/ (Op::todo),
  CALL_0N     /*0x12*/ (Op::todo),
  CALL_0T     /*0x13*/ (Op::todo),
  CALL_10     /*0x14*/ (Op::todo),
  CALL_11     /*0x15*/ (Op::todo),
  CALL_1N     /*0x16*/ (Op::todo),
  CALL_1T     /*0x17*/ (Op::todo),
  CALL_N0     /*0x18*/ (Op::todo),
  CALL_N1     /*0x19*/ (Op::todo),
  CALL_NN     /*0x1A*/ (Op::todo),
  CALL_NT     /*0x1B*/ (Op::todo),
  CALL_T0     /*0x1C*/ (Op::todo),
  CALL_T1     /*0x1D*/ (Op::todo),
  CALL_TN     /*0x1E*/ (Op::todo),
  CALL_TT     /*0x1F*/ (Op::todo),
  NVOK_00     /*0x20*/ (Op::todo),
  NVOK_01     /*0x21*/ (Op::todo),
  NVOK_0N     /*0x22*/ (Op::todo),
  NVOK_0T     /*0x23*/ (Op::todo),
  NVOK_10     /*0x24*/ (Op::todo),
  NVOK_11     /*0x25*/ (Op::todo),
  NVOK_1N     /*0x26*/ (Op::todo),
  NVOK_1T     /*0x27*/ (Op::todo),
  NVOK_N0     /*0x28*/ (Op::todo),
  NVOK_N1     /*0x29*/ (Op::todo),
  NVOK_NN     /*0x2A*/ (Op::todo),
  NVOK_NT     /*0x2B*/ (Op::todo),
  NVOK_T0     /*0x2C*/ (Op::todo),
  NVOK_T1     /*0x2D*/ (Op::todo),
  NVOK_TN     /*0x2E*/ (Op::todo),
  NVOK_TT     /*0x2F*/ (Op::todo),
  MBIND       /*0x30*/ (Op::todo),
  FBIND       /*0x31*/ (Op::todo),
  RSVD_32     /*0x32*/ (Op::todo),
  SYN_INIT    /*0x33*/ (Op::todo),
  CONSTR_0    /*0x34*/ (Op::todo),
  CONSTR_1    /*0x35*/ (Op::todo),
  CONSTR_N    /*0x36*/ (Op::todo),
  CONSTR_T    /*0x37*/ (Op::todo),
  NEW_0       /*0x38*/ (Op::todo),
  NEW_1       /*0x39*/ (Op::todo),
  NEW_N       /*0x3A*/ (Op::todo),
  NEW_T       /*0x3B*/ (Op::todo),
  NEWG_0      /*0x3C*/ (Op::todo),
  NEWG_1      /*0x3D*/ (Op::todo),
  NEWG_N      /*0x3E*/ (Op::todo),
  NEWG_T      /*0x3F*/ (Op::todo),
  NEWC_0      /*0x40*/ (Op::todo),
  NEWC_1      /*0x41*/ (Op::todo),
  NEWC_N      /*0x42*/ (Op::todo),
  NEWC_T      /*0x43*/ (Op::todo),
  NEWCG_0     /*0x44*/ (Op::todo),
  NEWCG_1     /*0x45*/ (Op::todo),
  NEWCG_N     /*0x46*/ (Op::todo),
  NEWCG_T     /*0x47*/ (Op::todo),
  NEWV_0      /*0x48*/ (Op::todo),
  NEWV_1      /*0x49*/ (Op::todo),
  NEWV_N      /*0x4A*/ (Op::todo),
  NEWV_T      /*0x4B*/ (Op::todo),
  RETURN_0    /*0x4C*/ (X -> return_n(X,0)),
  RETURN_1    /*0x4D*/ (Op::todo),
  RETURN_N    /*0x4E*/ (Op::todo),
  RETURN_T    /*0x4F*/ (Op::todo),
  VAR         /*0x50*/ (Op::todo),
  VAR_I       /*0x51*/ (Op::todo),
  VAR_N       /*0x52*/ (Op::var_n),
  VAR_IN      /*0x53*/ (Op::var_in),
  VAR_D       /*0x54*/ (Op::todo),
  VAR_DN      /*0x55*/ (Op::var_dn),
  VAR_C       /*0x56*/ (Op::todo),
  VAR_CN      /*0x57*/ (Op::todo),
  VAR_S       /*0x58*/ (Op::todo),
  VAR_SN      /*0x59*/ (Op::todo),
  VAR_T       /*0x5A*/ (Op::todo),
  VAR_TN      /*0x5B*/ (Op::todo),
  VAR_M       /*0x5C*/ (Op::todo),
  VAR_MN      /*0x5D*/ (Op::todo),
  RSVD_5E     /*0x5E*/ (Op::todo),
  RSVD_5F     /*0x5F*/ (Op::todo),
  MOV         /*0x60*/ (Op::todo),
  MOV_VAR     /*0x61*/ (Op::todo),
  MOV_REF     /*0x62*/ (Op::todo),
  MOV_THIS    /*0x63*/ (Op::todo),
  MOV_THIS_A  /*0x64*/ (Op::todo),
  MOV_TYPE    /*0x65*/ (Op::todo),
  CAST        /*0x66*/ (Op::todo),
  RSVD_67     /*0x67*/ (Op::todo),
  CMP         /*0x68*/ (Op::todo),
  IS_ZERO     /*0x69*/ (Op::todo),
  IS_NZERO    /*0x6A*/ (Op::todo),
  IS_NULL     /*0x6B*/ (Op::todo),
  IS_NNULL    /*0x6C*/ (Op::todo),
  IS_EQ       /*0x6D*/ (Op::is_eq),
  IS_NEQ      /*0x6E*/ (Op::todo),
  IS_LT       /*0x6F*/ (Op::todo),
  IS_LTE      /*0x70*/ (Op::todo),
  IS_GT       /*0x71*/ (Op::todo),
  IS_GTE      /*0x72*/ (Op::todo),
  IS_NOT      /*0x73*/ (Op::todo),
  IS_TYPE     /*0x74*/ (Op::todo),
  IS_NTYPE    /*0x75*/ (Op::todo),
  RSVD_76     /*0x76*/ (Op::todo),
  RSVD_77     /*0x77*/ (Op::todo),
  RSVD_78     /*0x78*/ (Op::todo),
  JMP         /*0x79*/ (Op::todo),
  JMP_TRUE    /*0x7A*/ (Op::todo),
  JMP_FALSE   /*0x7B*/ (Op::todo),
  JMP_ZERO    /*0x7C*/ (Op::todo),
  JMP_NZERO   /*0x7D*/ (Op::todo),
  JMP_NULL    /*0x7E*/ (Op::todo),
  JMP_NNULL   /*0x7F*/ (Op::todo),
  JMP_EQ      /*0x80*/ (Op::todo),
  JMP_NEQ     /*0x81*/ (Op::todo),
  JMP_LT      /*0x82*/ (Op::todo),
  JMP_LTE     /*0x83*/ (Op::todo),
  JMP_GT      /*0x84*/ (Op::todo),
  JMP_GTE     /*0x85*/ (Op::todo),
  JMP_TYPE    /*0x86*/ (Op::todo),
  JMP_NTYPE   /*0x87*/ (Op::todo),
  JMP_COND    /*0x88*/ (Op::todo),
  JMP_NCOND   /*0x89*/ (Op::todo),
  JMP_NFIRST  /*0x8A*/ (Op::todo),
  JMP_NSAMPLE /*0x8B*/ (Op::todo),
  JMP_INT     /*0x8C*/ (Op::todo),
  JMP_VAL     /*0x8D*/ (Op::todo),
  JMP_ISA     /*0x8E*/ (Op::todo),
  JMP_VAL_N   /*0x8F*/ (Op::todo),
  ASSERT      /*0x90*/ (Op::todo),
  ASSERT_M    /*0x91*/ (Op::todo),
  ASSERT_V    /*0x92*/ (Op::todo),
  GP_ADD      /*0x93*/ (Op::todo),
  GP_SUB      /*0x94*/ (Op::todo),
  GP_MUL      /*0x95*/ (Op::todo),
  GP_DIV      /*0x96*/ (Op::todo),
  GP_MOD      /*0x97*/ (Op::todo),
  GP_SHL      /*0x98*/ (Op::todo),
  GP_SHR      /*0x99*/ (Op::todo),
  GP_USHR     /*0x9A*/ (Op::todo),
  GP_AND      /*0x9B*/ (Op::todo),
  GP_OR       /*0x9C*/ (Op::todo),
  GP_XOR      /*0x9D*/ (Op::todo),
  GP_DIVREM   /*0x9E*/ (Op::todo),
  GP_IRANGEI  /*0x9F*/ (Op::todo),
  GP_ERANGEI  /*0xA0*/ (Op::todo),
  GP_IRANGEE  /*0xA1*/ (Op::todo),
  GP_ERANGEE  /*0xA2*/ (Op::todo),
  GP_NEG      /*0xA3*/ (Op::todo),
  GP_COMPL    /*0xA4*/ (Op::todo),
  L_GET       /*0xA5*/ (Op::todo),
  L_SET       /*0xA6*/ (Op::todo),
  P_GET       /*0xA7*/ (Op::todo),
  P_SET       /*0xA8*/ (Op::todo),
  P_VAR       /*0xA9*/ (Op::todo),
  P_REF       /*0xAA*/ (Op::todo),
  IP_INC      /*0xAB*/ (Op::todo),
  IP_DEC      /*0xAC*/ (Op::todo),
  IP_INCA     /*0xAD*/ (Op::todo),
  IP_DECA     /*0xAE*/ (Op::todo),
  IP_INCB     /*0xAF*/ (Op::todo),
  IP_DECB     /*0xB0*/ (Op::todo),
  IP_ADD      /*0xB1*/ (Op::todo),
  IP_SUB      /*0xB2*/ (Op::todo),
  IP_MUL      /*0xB3*/ (Op::todo),
  IP_DIV      /*0xB4*/ (Op::todo),
  IP_MOD      /*0xB5*/ (Op::todo),
  IP_SHL      /*0xB6*/ (Op::todo),
  IP_SHR      /*0xB7*/ (Op::todo),
  IP_USHR     /*0xB8*/ (Op::todo),
  IP_AND      /*0xB9*/ (Op::todo),
  IP_OR       /*0xBA*/ (Op::todo),
  IP_XOR      /*0xBB*/ (Op::todo),
  PIP_INC     /*0xBC*/ (Op::todo),
  PIP_DEC     /*0xBD*/ (Op::todo),
  PIP_INCA    /*0xBE*/ (Op::todo),
  PIP_DECA    /*0xBF*/ (Op::todo),
  PIP_INCB    /*0xC0*/ (Op::todo),
  PIP_DECB    /*0xC1*/ (Op::todo),
  PIP_ADD     /*0xC2*/ (Op::todo),
  PIP_SUB     /*0xC3*/ (Op::todo),
  PIP_MUL     /*0xC4*/ (Op::todo),
  PIP_DIV     /*0xC5*/ (Op::todo),
  PIP_MOD     /*0xC6*/ (Op::todo),
  PIP_SHL     /*0xC7*/ (Op::todo),
  PIP_SHR     /*0xC8*/ (Op::todo),
  PIP_USHR    /*0xC9*/ (Op::todo),
  PIP_AND     /*0xCA*/ (Op::todo),
  PIP_OR      /*0xCB*/ (Op::todo),
  PIP_XOR     /*0xCC*/ (Op::todo),
  I_GET       /*0xCD*/ (Op::todo),
  I_SET       /*0xCE*/ (Op::todo),
  IIP_INC     /*0xCF*/ (Op::todo),
  IIP_DEC     /*0xD0*/ (Op::todo),
  IIP_INCA    /*0xD1*/ (Op::todo),
  IIP_DECA    /*0xD2*/ (Op::todo),
  IIP_INCB    /*0xD3*/ (Op::todo),
  IIP_DECB    /*0xD4*/ (Op::todo),
  IIP_ADD     /*0xD5*/ (Op::todo),
  IIP_SUB     /*0xD6*/ (Op::todo),
  IIP_MUL     /*0xD7*/ (Op::todo),
  IIP_DIV     /*0xD8*/ (Op::todo),
  IIP_MOD     /*0xD9*/ (Op::todo),
  IIP_SHL     /*0xDA*/ (Op::todo),
  IIP_SHR     /*0xDB*/ (Op::todo),
  IIP_USHR    /*0xDC*/ (Op::todo),
  IIP_AND     /*0xDD*/ (Op::todo),
  IIP_OR      /*0xDE*/ (Op::todo),
  IIP_XOR     /*0xDF*/ (Op::todo),
  M_GET       /*0xE0*/ (Op::todo),
  M_SET       /*0xE1*/ (Op::todo),
  M_VAR       /*0xE2*/ (Op::todo),
  M_REF       /*0xE3*/ (Op::todo),
  MIP_INC     /*0xE4*/ (Op::todo),
  MIP_DEC     /*0xE5*/ (Op::todo),
  MIP_INCA    /*0xE6*/ (Op::todo),
  MIP_DECA    /*0xE7*/ (Op::todo),
  MIP_INCB    /*0xE8*/ (Op::todo),
  MIP_DECB    /*0xE9*/ (Op::todo),
  MIP_ADD     /*0xEA*/ (Op::todo),
  MIP_SUB     /*0xEB*/ (Op::todo),
  MIP_MUL     /*0xEC*/ (Op::todo),
  MIP_DIV     /*0xED*/ (Op::todo),
  MIP_MOD     /*0xEE*/ (Op::todo),
  MIP_SHL     /*0xEF*/ (Op::todo),
  MIP_SHR     /*0xF0*/ (Op::todo),
  MIP_USHR    /*0xF1*/ (Op::todo),
  MIP_AND     /*0xF2*/ (Op::todo),
  MIP_OR      /*0xF3*/ (Op::todo),
  MIP_XOR     /*0xF4*/ (Op::todo),
  RSVD_F5     /*0xF5*/ (Op::todo),
  RSVD_F6     /*0xF6*/ (Op::todo),
  RSVD_F7     /*0xF7*/ (Op::todo),
  RSVD_F8     /*0xF8*/ (Op::todo),
  RSVD_F9     /*0xF9*/ (Op::todo),
  RSVD_FA     /*0xFA*/ (Op::todo),
  RSVD_FB     /*0xFB*/ (Op::todo),
  RSVD_FC     /*0xFC*/ (Op::todo),
  RSVD_FD     /*0xFD*/ (Op::todo),
  RSVD_FE     /*0xFE*/ (Op::todo),
  RSVD_FF     /*0xFF*/ (Op::todo),
  ;

  Op( Consumer<XClzBuilder> emit ) { _emit=emit; }
  final Consumer<XClzBuilder> _emit;
  static final Op[] OPS = Op.values();
  static {
    assert OPS[0x00]==NOP;
    assert OPS[0x80]==JMP_EQ;
    assert OPS[0xFF]==RSVD_FF;
  }

  // --------------------------------------------------------
  // Emit Java per opcode
  static void todo( XClzBuilder X ) { throw XEC.TODO(); }

  // 0x01 - 0x04: Track line numbers
  static void line_n( XClzBuilder X, int n ) { }

  // 0x05: ENTER
  static void enter( XClzBuilder X ) {
    // I am assuming these basically enter/exit a Java lexical scope
    if( X._lexical_depth++ > 0 ) // Not needed at the outermost scope, where the method scope has the same effect
      throw XEC.TODO();
  }

  
  // 0x4C - 0x4E: RETURN_0, RETURN_1, RETURN_N
  static void return_n( XClzBuilder X, int n ) {
    if( n!=0 ) throw XEC.TODO();  // Multi-return
    X._sb.ip("return;").nl();
  }

  // 0x52: VAR_N
  static void var_n( XClzBuilder X ) {
    // Destination is read first and is typeaware, so read the destination type.
    String jtype = X.jtype_methcon();
    // Read the XTC variable name.  Must not collide with any other names
    String name = X.jname_methcon();
    // One-liner to emit var def
    X._sb.ip(jtype).p(" ").p(name).p(";").nl();
  }

  // 0x53: VAR_IN
  static void var_in( XClzBuilder X ) {
    // Destination is read first and is typeaware, so read the destination type.
    String jtype = X.jtype_methcon();
    // Read the XTC variable name.  Must not collide with any other names
    String name = X.jname_methcon();
    // RHS
    String jval = X.jvalue_tcon((TCon)X.methcon());
    // One-liner to emit var def
    X._sb.ip(jtype).p(" ").p(name).p(" = ").p(jval).p(";").nl();
  }
  
  // 0x55: VAR_DN
  static void var_dn( XClzBuilder X ) {
    // Destination is read first and is typeaware, so read the destination type.
    AnnotTCon anno = (AnnotTCon)X.methcon();
    // Read the XTC variable name.  Must not collide with any other names
    String name = X.jname_methcon();

    // TODO: Handle other kinds of typed args
    TermTCon ttc = anno.con().is_generic();
    if( ttc==null ) throw XEC.TODO();
    String jtype = X.jtype_ttcon(ttc);
    String jval  = X.jvalue_ttcon(ttc);
    
    // One-liner to emit special assignment
    X._sb.ip(jtype).p(" ").p(name).p(" = ").p(jval).p(";").nl();
  }

  // 0x6D: IS_EQ
  static void is_eq( XClzBuilder X ) {
    throw XEC.TODO();
  }
  
}
