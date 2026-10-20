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
    // ----- fields --------------------------------------------------------------------------------

    /**
     * The context and listener that lazily created label variables register against; non-null
     * only while the loop is validating.
     */
    protected transient ValidationScope labelVars;

    protected transient Register firstRegister;
    protected transient Register countRegister;

    /**
     * Generally null, unless there is a "continue" that jumps to this statement.
     */
    protected transient List<Break> continues;

    // ----- constructors --------------------------------------------------------------------------

    protected LoopStatement(Token keyword, List<AstNode> conds) {
        super(keyword, conds);
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Label ensureContinueLabel(AstNode origin, Context originContext) {
        Context destination = ensureValidationContext();
        Label   label       = getContinueLabel();

        if (originContext.isReachable()) {
            // record the jump that landed on this statement by recording its assignment impact
            if (continues == null) {
                continues = new ArrayList<>();
            }
            continues.add(prepareJump(origin, originContext, destination, label));
        }

        return label;
    }

    /**
     * @return the continue label for this statement
     */
    public abstract Label getContinueLabel();

    // ----- LabelAble methods ---------------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String name) {
        return "first".equals(name) || "count".equals(name);
    }

    @Override
    public Register getLabelVar(Context ctx, String name) {
        assert hasLabelVar(name);

        boolean first = "first".equals(name);

        Register reg = first ? firstRegister : countRegister;
        if (reg == null) {
            // this occurs only during validate()
            assert labelVars != null;

            String labelName    = ((LabeledStatement) getParent()).getName();
            String registerName = labelName + '.' + name;
            Token  tok          = new Token(keyword.getStartPosition(), keyword.getEndPosition(), Id.IDENTIFIER, registerName);

            reg = ctx.createRegister(first ? pool().typeBoolean() : pool().typeInt64(), registerName);
            labelVars.ctx().registerVar(tok, reg, labelVars.errs());

            if (first) {
                firstRegister = reg;
            } else {
                countRegister = reg;
            }
        }

        return reg;
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected final Statement validateImpl(Context ctx, ErrorListener errs) {
        ValidationScope previous = labelVars;
        try {
            return validateLoop(ctx, errs);
        } finally {
            labelVars = previous;
        }
    }

    /**
     * Validate the loop. {@link #validateImpl} restores {@link #labelVars} on every exit, so an
     * early return or an exception cannot leave a finished attempt's context and listener behind.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return the resulting statement (typically this) or null if the compilation cannot proceed
     */
    protected abstract Statement validateLoop(Context ctx, ErrorListener errs);
}
