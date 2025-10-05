package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_NEG rvalue, lvalue   ; -T -> T
 */
public class GP_Neg
        extends OpGeneral {
    /**
     * Construct a GP_NEG op for the passed arguments.
     *
     * @param argValue  the Argument to negate
     * @param argResult  the Argument to store the result in
     */
    public GP_Neg(Argument argValue, Argument argResult) {
        super(argValue, argResult);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Neg(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_NEG;
    }

    @Override
    protected boolean isBinaryOp() {
        return false;
    }

    @Override
    protected int completeUnary(Frame frame, ObjectHandle hTarget) {
        return hTarget.getOpSupport().invokeNeg(frame, hTarget, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected void buildOptimizedUnary(BuildContext bctx, CodeBuilder code, ClassDesc cdTarget) {
        switch (cdTarget.descriptorString()) {
            case "I", "S", "B", "C", "Z"
                     -> code.ineg();
            case "J" -> code.lneg();
            case "F" -> code.fneg();
            case "D" -> code.dneg();
            default  -> throw new IllegalStateException();
        }
    }
}
