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

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_USHR rvalue1, rvalue2, lvalue ; T >>> T -> T
 */
public class GP_ShrAll
        extends OpGeneral
        implements NumberSupport {
    /**
     * Construct a GP_USHR op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_ShrAll(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_ShrAll(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_USHR;
    }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeShrAll(frame, hTarget, hArg, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected TypeConstant buildOptimizedBinary(BuildContext bctx,
                                                CodeBuilder  code,
                                                RegisterInfo regTarget,
                                                int          nArgValue) {
        return buildPrimitiveShrAll(bctx, code, regTarget, nArgValue);
    }
}
