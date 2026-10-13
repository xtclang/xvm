package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode {
    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Statement copyTree() {
        return (Statement) super.copyTree();
    }

    @Override
    protected boolean usesSuper() {
        for (AstNode node : children()) {
            if (!(node instanceof ComponentStatement) && node.usesSuper()) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel() {
        Label label = m_labelEnd;
        if (label == null) {
            m_labelEnd = label = new Label(getCodeContainerCounter());
        }
        return label;
    }

    /**
     * @return true iff a GotoStatement can "naturally" (without a label) refer to this statement
     */
    public boolean isNaturalGotoStatementTarget() {
        return false;
    }

    /**
     * Obtain the label to "break" to.
     *
     * @param nodeOrigin  the "break" node (the node requesting the label)
     * @param ctxOrigin   the context from the point under this statement of the "break"
     *
     * @return the label to jump to when a "break" occurs within (or for) this statement
     */
    public Label ensureBreakLabel(AstNode nodeOrigin, Context ctxOrigin) {
        Context ctxDest = ensureValidationContext();
        Label   label   = getEndLabel();

        if (ctxOrigin.isReachable()) {
            addBreak(prepareJump(nodeOrigin, ctxOrigin, ctxDest, label));
        }

        return label;
    }

    /** Capture the assignment and narrowing changes carried by a control-flow jump. */
    protected static Break prepareJump(AstNode origin, Context from, Context to, Label label) {
        Map<String, Assignment> assignments = new HashMap<>();
        Map<String, Argument>   arguments   = new HashMap<>();
        from.prepareJump(to, assignments, arguments);
        return new Break(origin, assignments, arguments, label);
    }

    /** Merge live incoming jumps and restore reachability if at least one reaches this point. */
    protected static void mergeJumps(Context ctx, List<Break> jumps) {
        boolean reaches = false;
        for (Break jump : jumps) {
            if (!jump.node().isDiscarded()) {
                ctx.merge(jump.mapAssign(), jump.mapNarrow());
                reaches = true;
            }
        }
        if (reaches) {
            ctx.setReachable(true);
        }
    }

    /** Validate a Boolean expression or conditional assignment, including its negated context. */
    protected AstNode validateCondition(Context ctx, AstNode condition, ErrorListener errs) {
        if (condition instanceof AssignmentStatement assignment) {
            if (assignment.isNegated()) {
                Context negated = ctx.enterNot();
                AstNode validated = assignment.validate(negated, errs);
                negated.exit();
                return validated;
            }
            return assignment.validate(ctx, errs);
        }
        return ((Expression) condition).validate(ctx, pool().typeBoolean(), errs);
    }

    /** Emit one condition's false exit, preserving conditional assignments and negation labels. */
    protected static boolean emitCondition(Context ctx, AstNode condition, boolean reachable,
                                           Code code, Label exit, ErrorListener errs) {
        if (condition instanceof AssignmentStatement assignment) {
            boolean negated = assignment.isNegated();
            Label   failed  = negated ? new Label("negated") : exit;
            assignment.setConditionFalseLabel(failed);
            boolean completes = assignment.completes(ctx, reachable, code, errs);
            assert assignment.isConditionFalseLabelTaken();
            if (negated) {
                code.add(new Jump(exit));
                code.add(failed);
            }
            return completes;
        }
        Expression expression = (Expression) condition;
        expression.generateConditionalJump(ctx, code, exit, false, errs);
        return expression.isCompletable();
    }

    protected void addBreak(Break breakInfo) {
        // record the jump that landed on this statement by recording its assignment impact
        if (m_listBreaks == null) {
            m_listBreaks = new ArrayList<>();
        }
        m_listBreaks.add(breakInfo);
    }

    /**
     * @return true iff this statement has any breaks that jump to this statement's exit label
     */
    protected boolean hasBreaks() {
        return m_listBreaks != null;
    }

    /**
     * Obtain the label to "continue" to.
     *
     * @param nodeOrigin  the "continue" node (the node requesting the label)
     * @param ctxOrigin   the context from the point under this statement of the "continue"
     *
     * @return the label to jump to when a "continue" occurs within (or for) this statement
     */
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin) {
        assert isNaturalGotoStatementTarget();
        throw new IllegalStateException();
    }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin) {
        AstNode nodeChild = findChild(nodeOrigin);
        assert nodeChild != null;
        assert allowsShortCircuit(nodeChild);

        // this needs to be overridden by any statement that doesn't short-circuit to the end label
        return ensureBreakLabel(nodeOrigin, ctxOrigin);
    }

    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return the resulting statement (typically this) or null if the compilation cannot proceed
     */
    protected final Statement validate(Context ctx, ErrorListener errs) {
        return validate(ctx, errs, () -> validateImpl(ctx, errs));
    }

    /** Preserve the statement validation scope while supplying an explicit value-context hint. */
    protected final Statement validate(Context ctx, ErrorListener errs, Supplier<Statement> validation) {
        if (errs.isAbortDesired()) {
            return null;
        }

        // before validating the nested code, associate this statement with the context so that any
        // "break" or "continue" can find the context to apply assignment data to
        Context   ctxPrevious = m_ctx;
        Statement stmt;
        m_ctx = ctx;
        try {
            stmt = validation.get();
        } finally {
            m_ctx = ctxPrevious;
        }

        if (m_listBreaks != null) {
            for (Iterator<Break> iter = m_listBreaks.iterator(); iter.hasNext(); ) {
                Break breakInfo = iter.next();
                if (breakInfo.node.isDiscarded()) {
                    iter.remove();
                } else {
                    ctx.merge(breakInfo.mapAssign(), breakInfo.mapNarrow());

                    if (breakInfo.label != null) {
                        breakInfo.label.restoreNarrowed(ctx);
                    }
                }
            }

            if (!ctx.isReachable()) {
                // since we do have reachable breaks, the statement is completable
                ctx.setReachable(true);
            }
        }

        return stmt;
    }

    /**
     * @return the Context being used for validation of this statement, if this statement is
     *         currently being validated
     *
     * @throws IllegalStateException  if this statement is not currently being validated
     */
    protected Context ensureValidationContext() {
        if (m_ctx == null) {
            throw new IllegalStateException();
        }

        return m_ctx;
    }

    /**
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return the resulting statement (typically this) or null if the compilation cannot proceed
     */
    protected abstract Statement validateImpl(Context ctx, ErrorListener errs);

    /**
     * Generate the generic assembly code that wraps the contents of any statement.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected final boolean completes(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        if (fReachable) {
            updateLineNumber(code);
        } else {
            code = code.blackhole();
        }

        boolean fCompletes = fReachable && emit(ctx, fReachable, code, errs);

        if (m_labelEnd != null && !errs.hasSeriousErrors()) {
            code.add(m_labelEnd);
        }

        return fCompletes || fReachable && m_listBreaks != null && !m_listBreaks.isEmpty();
    }

    /**
     * Generate the statement-specific assembly code.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected abstract boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs);

    /**
     * The "break" info (also used for "continue" and "short circuit" info).
     */
    public record Break(AstNode node, Map<String, Assignment> mapAssign,
                         Map<String, Argument> mapNarrow, Label label) {}

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The label representing the end of the statement, or null if it has not been requested.
     */
    private Label m_labelEnd;

    /**
     * The Context that contains this statement temporarily during validation.
     */
    private transient Context m_ctx;

    /**
     * Generally null, unless there is a break that jumps to this statement's exit label.
     */
    private transient List<Break> m_listBreaks;
}
