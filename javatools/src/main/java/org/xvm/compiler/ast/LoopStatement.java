package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.Register;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

/**
 * A looping statement: "for", "while", "do" or the "for each" form of "for". A loop can declare
 * the label variables of an enclosing labeled statement while it is being validated; by default
 * these are "first" and "count".
 */
public abstract class LoopStatement
        extends ConditionalStatement
        implements LabelAble {
    // ----- constructors --------------------------------------------------------------------------

    protected LoopStatement(Token keyword, List<AstNode> conds) {
        super(keyword, conds);
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin) {
        Context ctxDest = ensureValidationContext();
        Label   label   = getContinueLabel();

        if (ctxOrigin.isReachable()) {
            // record the jump that landed on this statement by recording its assignment impact
            if (m_listContinues == null) {
                m_listContinues = new ArrayList<>();
            }
            m_listContinues.add(prepareJump(nodeOrigin, ctxOrigin, ctxDest, label));
        }

        return label;
    }

    /**
     * @return the continue label for this statement
     */
    public abstract Label getContinueLabel();

    // ----- LabelAble methods ---------------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String sName) {
        return "first".equals(sName) || "count".equals(sName);
    }

    @Override
    public Register getLabelVar(Context ctx, String sName) {
        assert hasLabelVar(sName);

        boolean fFirst = "first".equals(sName);

        Register reg = fFirst ? m_regFirst : m_regCount;
        if (reg == null) {
            // this occurs only during validate()
            assert m_labelVars != null;

            String sLabel   = ((LabeledStatement) getParent()).getName();
            String sRegName = sLabel + '.' + sName;
            Token  tok      = new Token(keyword.getStartPosition(), keyword.getEndPosition(), Id.IDENTIFIER, sRegName);

            reg = ctx.createRegister(fFirst ? pool().typeBoolean() : pool().typeInt64(), sRegName);
            m_labelVars.ctx().registerVar(tok, reg, m_labelVars.errs());

            if (fFirst) {
                m_regFirst = reg;
            } else {
                m_regCount = reg;
            }
        }

        return reg;
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

    protected transient Register m_regFirst;
    protected transient Register m_regCount;

    /**
     * Generally null, unless there is a "continue" that jumps to this statement.
     */
    protected transient List<Break> m_listContinues;
}
