package org.xvm.compiler.ast;

import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Token;

/**
 * A looping statement: "for", "while", "do" or the "for each" form of "for". A loop can declare
 * the label variables of an enclosing labeled statement while it is being validated.
 */
public abstract class LoopStatement
        extends ConditionalStatement {
    // ----- constructors --------------------------------------------------------------------------

    protected LoopStatement(Token keyword, List<AstNode> conds) {
        super(keyword, conds);
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected final Statement validateImpl(Context ctx, ErrorListener errs) {
        ValidationScope previous = m_labelVars;
        try {
            return validateLoop(ctx, errs);
        } finally {
            m_labelVars = previous;
        }
    }

    /**
     * Validate the loop. {@link #validateImpl} restores {@link #m_labelVars} on every exit, so an
     * early return or an exception cannot leave a finished attempt's context and listener behind.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return the resulting statement (typically this) or null if the compilation cannot proceed
     */
    protected abstract Statement validateLoop(Context ctx, ErrorListener errs);

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The context and listener that lazily created label variables register against; non-null
     * only while the loop is validating.
     */
    protected transient ValidationScope m_labelVars;
}
