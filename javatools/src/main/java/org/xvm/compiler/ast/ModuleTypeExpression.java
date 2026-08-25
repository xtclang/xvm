package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Token;


/**
 * A type expression specifies a module name.
 */
public final class ModuleTypeExpression
    extends NamedTypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ModuleTypeExpression.
     *
     * @param names  the qualified name of the module
     */
    public ModuleTypeExpression(List<Token> names) {
        super(null, names, null, null, null, names.get(names.size()-1).getEndPosition());
    }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
    }

    // ----- copy support --------------------------------------------------------------------------

    /**
     * Shallow copy constructor for {@link AstNode#deepCopy()}: every declared field of this
     * tier is carried over verbatim (children are re-copied and re-adopted by the walk); the
     * field parity assertion in deepCopy() fails loudly if a field is added but not copied.
     */
    protected ModuleTypeExpression(ModuleTypeExpression that) {
        super(that);
    }

    @Override
    protected AstNode shallowCopy() {
        return new ModuleTypeExpression(this);
    }
}
