package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Constant;
import org.xvm.asm.Register;

import org.xvm.javajit.BuildContext;

/**
 * VAR_P TYPE; an anonymous variable that can pass an asynchronous invocation result to its caller.
 * The interpreter treats it as an ordinary variable.
 */
public class Var_P extends Var {
    public Var_P(Register reg) {
        super(reg);
    }

    public Var_P(DataInput in, Constant[] constants) throws IOException {
        super(in, constants);
    }

    @Override
    public int getOpCode() {
        return OP_VAR_P;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        bctx.introducePassThrough(code, m_nVar, m_nType);
        return -1;
    }
}
