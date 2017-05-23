package org.xvm.proto;


import org.xvm.proto.op.Return_0;

/**
 * The ops.
 *
 * @author gg 2017.02.21
 */
public abstract class Op
    {
    // offsets for the execution indexes
    public static final int I_SCOPE = 0;
    public static final int I_GUARD = 1;

    // the maximum value for the constants in the const pool
    public static final int MAX_CONST_ID = 2_000_000_000;

    // indexes for pre-defined arguments
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

    // return values from the process()
    public static final int R_NEXT = -1;
    public static final int R_RETURN = -2;
    public static final int R_EXCEPTION = -3;
    public static final int R_CALL = -4;
    public static final int R_WAIT = -5;

    // an stub for an op-code
    public static final Op[] STUB = new Op[] {Return_0.INSTANCE};

    // returns a positive iPC or a negative RETURN_*
    public abstract int process(Frame frame, int iPC);
    }
