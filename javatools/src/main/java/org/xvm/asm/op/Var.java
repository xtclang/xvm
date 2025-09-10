package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;


/**
 * VAR TYPE ; (next register is an uninitialized anonymous variable)
 */
public class Var
        extends OpVar {
    /**
     * Construct a VAR op for the specified register.
     *
     * @param reg  the register
     */
    public Var(Register reg) {
        super(reg);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_VAR;
    }

    @Override
    public int process(Frame frame, int iPC) {
        frame.introduceVar(m_nVar, convertId(m_nType), 0, Frame.VAR_STANDARD, null);

        return iPC + 1;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        bctx.introduceVar(code, m_nVar, m_nType, 0);
    }
}