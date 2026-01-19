package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_AND rvalue1, rvalue2, lvalue ; T & T -> T
 */
public class GP_And
        extends OpGeneral
        implements NumberSupport {
    /**
     * Construct a GP_AND op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_And(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_And(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_AND;
    }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeAnd(frame, hTarget, hArg, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected void buildOptimizedBinary(BuildContext bctx,
                                        CodeBuilder  code,
                                        RegisterInfo regTarget,
                                        RegisterInfo regArg) {
        buildPrimitiveAnd(bctx, code, regTarget);
    }
}
