package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.BinaryAST;

import org.xvm.asm.op.Label;


/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Default constructor.
     */
    protected Statement() {
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>No CHILD_FIELDS - base class for statements</li>
     *   <li>All fields shallow copied via Object.clone() bitwise copy</li>
     * </ul>
     *
     * @param original  the Statement to copy from
     */
    protected Statement(@NotNull Statement original) {
        super(Objects.requireNonNull(original));

        // Shallow copy ALL fields to match Object.clone() (super.clone()) semantics
        this.m_labelEnd   = original.m_labelEnd;
        this.m_ctx        = original.m_ctx;
        this.m_listBreaks = original.m_listBreaks;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Subclasses MUST override this method with a covariant return type and use a copy
     * constructor. The default implementation throws to catch any missing override.
     */
    @Override
    public Statement copy() {
        throw new UnsupportedOperationException(
            "Statement subclass " + getClass().getSimpleName() + " must override copy()");
    }


    // ----- accessors -----------------------------------------------------------------------------

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
            // generate a delta of assignment information for the jump
            Map<String, Assignment> mapAsn = new HashMap<>();
            Map<String, Argument>   mapArg = new HashMap<>();

            ctxOrigin.prepareJump(ctxDest, mapAsn, mapArg);

            addBreak(new Break(nodeOrigin, mapAsn, mapArg, label));
        }

        return label;
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
        if (errs.isAbortDesired()) {
            return null;
        }

        // before validating the nested code, associate this statement with the context so that any
        // "break" or "continue" can find the context to apply assignment data to
        m_ctx = ctx;
        Statement stmt = validateImpl(ctx, errs);
        m_ctx = null;

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

    /**
     * Holder for BinaryAST objects as they percolate up the emit() call tree.
     * TODO get rid of this concept and add a getAST as per Expression tree
     */
    public static class AstHolder {
        BinaryAST getAst(Statement stmt) {
            assert stmt != null;

            if (stmt instanceof LabeledStatement stmtLbl) {
                return getAst(stmtLbl.stmt);
            }

            if (stmt instanceof ImportStatement || stmt instanceof ComponentStatement) {
                return null;
            }

            BinaryAST ast = this.ast;
            this.ast = null;
            return ast != null && stmt == this.stmt
                ? ast
                : BinaryAST.POISON;
        }

        void setAst(Statement stmt, BinaryAST ast) {
            assert stmt != null && ast != null;
            this.stmt = stmt;
            this.ast  = ast;
        }

        private Statement stmt;
        private BinaryAST ast;
    }


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