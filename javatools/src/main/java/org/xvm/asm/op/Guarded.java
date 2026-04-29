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
     * Construct a Guarded prefix for the specified scope
     */
    public Guarded(Scope scope) {
        m_scope = scope;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        getNextOp().computeTypes(bctx);
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        CatchStart op = (CatchStart) getNextOp();
        op.build(bctx, code, m_scope);
        return -1;
    }

    /**
     * Set a scope associated with this Guarded prefix.
     */
    public void setScope(Scope scope) {
        assert scope != null && m_scope == null;
        m_scope = scope;
    }

    /**
     * Clear all JIT related state.
     */
    public void clear() {
        m_scope = null;
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The JIT scope guarded by the underlying {@link CatchStart}.
     */
    private Scope m_scope;
}