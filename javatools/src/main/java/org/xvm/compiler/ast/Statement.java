package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.BinaryAST;

import org.xvm.asm.op.Label;


/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper()
        {
        for (AstNode node : children())
            {
            if (!(node instanceof ComponentStatement) && node.usesSuper())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel()
        {
        Label label = m_labelEnd;
        if (label == null)
            {
            m_labelEnd = label = new Label();
            }
        return label;
        }

    /**
     * @return true iff a GotoStatement can "naturally" (without a label) refer to this statement
     */
    public boolean isNaturalGotoStatementTarget()
        {
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
    public Label ensureBreakLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        Context ctxDest = ensureValidationContext();
        Label   label   = getEndLabel();

        if (ctxOrigin.isReachable())
            {
            // generate a delta of assignment information for the jump
            Map<String, Assignment> mapAsn = new HashMap<>();
            Map<String, Argument>   mapArg = new HashMap<>();

            ctxOrigin.prepareJump(ctxDest, mapAsn, mapArg);

            addBreak(nodeOrigin, mapAsn, mapArg, label);
            }

        return label;
        }

    protected void addBreak(AstNode nodeOrigin, Map<String, Assignment> mapAsn,
                            Map<String, Argument> mapsArg, Label label)
        {
        // record the jump that landed on this statement by recording its assignment impact
        if (m_listBreaks == null)
            {
            m_listBreaks = new ArrayList<>();
            }
        m_listBreaks.add(new Break(nodeOrigin, mapAsn, mapsArg, label));
        }

    /**
     * Obtain the label to "continue" to.
     *
     * @param nodeOrigin  the "continue" node (the node requesting the label)
     * @param ctxOrigin   the context from the point under this statement of the "continue"
     *
     * @return the label to jump to when a "continue" occurs within (or for) this statement
     */
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        assert isNaturalGotoStatementTarget();
        throw new IllegalStateException();
        }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
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
    protected final Statement validate(Context ctx, ErrorListener errs)
        {
        if (errs.isAbortDesired())
            {
            return null;
            }

        // before validating the nested code, associate this statement with the context so that any
        // "break" or "continue" can find the context to apply assignment data to
        m_ctx = ctx;
        Statement stmt = validateImpl(ctx, errs);
        m_ctx = null;

        if (m_listBreaks != null)
            {
            for (Iterator<Break> iter = m_listBreaks.iterator(); iter.hasNext(); )
                {
                Break breakInfo = iter.next();
                if (breakInfo.node.isDiscarded())
                    {
                    iter.remove();
                    }
                else
                    {
                    ctx.merge(breakInfo.mapAssign, breakInfo.mapNarrow);

                    if (breakInfo.label != null)
                        {
                        breakInfo.label.restoreNarrowed(ctx);
                        }
                    }
                }

            if (!ctx.isReachable())
                {
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
    protected Context ensureValidationContext()
        {
        if (m_ctx == null)
            {
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
     * @return true iff the compilation can proceed
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
    protected final boolean completes(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (fReachable)
            {
            updateLineNumber(code);
            }
        else
            {
            code = code.blackhole();
            }

        boolean fCompletes = fReachable && emit(ctx, fReachable, code, errs);

        if (m_labelEnd != null)
            {
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
     * The break info.
     */
    private static class Break
        {
        Break(AstNode node, Map<String, Assignment> mapAssign, Map<String, Argument> mapNarrow,
              Label label)
            {
            this.node      = node;
            this.mapAssign = mapAssign;
            this.mapNarrow = mapNarrow;
            this.label     = label;
            }
        public final AstNode node;
        public final Map<String, Assignment> mapAssign;
        public final Map<String, Argument> mapNarrow;
        public final Label label;
        }

    /**
     * Holder for BinaryAST objects as they percolate up the emit() call tree.
     * TODO get rid of this concept and add a getAST as per Expression tree
     */
    static class AstHolder
        {
        BinaryAST getAst(Statement stmt)
            {
            assert stmt != null;

            if (stmt instanceof LabeledStatement stmtLbl)
                {
                return getAst(stmtLbl.stmt);
                }

            if (stmt instanceof ImportStatement || stmt instanceof ComponentStatement)
                {
                return null;
                }

            BinaryAST ast = this.ast;
            this.ast = null;
            if (ast != null && stmt == this.stmt)
                {
                return ast;
                }

            throw new UnsupportedOperationException(
                    "BAST for Statement: " + stmt.getClass().getSimpleName());
            }

        void setAst(Statement stmt, BinaryAST ast)
            {
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