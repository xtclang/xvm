package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TextSupport;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_SUB rvalue1, rvalue2, lvalue ; T - T -> T
 */
public class GP_Sub
        extends OpGeneral
        implements NumberSupport, TextSupport {
    /**
     * Construct a GP_SUB op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_Sub(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Sub(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_SUB;
    }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeSub(frame, hTarget, hArg, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected TypeConstant buildOptimizedBinary(BuildContext bctx,
                                                CodeBuilder  code,
                                                RegisterInfo regTarget,
                                                int          nArgValue) {
        if (regTarget.type().getValueString().equals("Char")) {
            return buildSubFromChar(bctx, code, regTarget, nArgValue);
        }
        return super.buildOptimizedBinary(bctx, code, regTarget, nArgValue);
    }

    @Override
    protected void buildOptimizedBinary(BuildContext bctx,
                                        CodeBuilder  code,
                                        RegisterInfo regTarget,
                                        RegisterInfo regArg) {
        buildPrimitiveSub(bctx, code, regTarget);
    }

    @Override
    protected RegisterInfo buildXvmOptimizedBinary(BuildContext bctx,
                                                   CodeBuilder  code,
                                                   RegisterInfo regTarget,
                                                   int          nArgValue) {
        buildXvmPrimitiveSub(bctx, code, regTarget, nArgValue);
        return regTarget;
    }
}
