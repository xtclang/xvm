package org.xvm.asm.op;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Op;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Scope;

/**
 * A "label" of sorts that allows every {@link CatchStart} op refer to the corresponding guarded
 * {@link org.xvm.javajit.Scope}.
 */
public class Guarded
        extends Op.Prefix {

    /**
     * Construct a Guard prefix
     */
    public Guarded(Scope scope) {
        f_scope = scope;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        CatchStart op = (CatchStart) getNextOp();
        op.build(bctx, code, f_scope);
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The JIT scope guarded by the underlying {@link CatchStart}.
     */
    private final Scope f_scope;
}