package org.xvm.compiler.ast;


import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

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
        if (ctxOrigin.isReachable())
            {
            // generate a delta of assignment information for the jump
            addBreak(nodeOrigin, ctxOrigin.prepareJump(ctxDest));
            }

        return getEndLabel();
        }

    protected void addBreak(AstNode nodeOrigin, Map<String, Assignment> mapAsn)
        {
        // record the jump that landed on this statement by recording its assignment impact
        if (m_listBreaks == null)
            {
            m_listBreaks = new ArrayList<>();
            }
        m_listBreaks.add(new SimpleEntry<>(nodeOrigin, mapAsn));
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
     * @return true iff the compilation can proceed
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
            for (Iterator<Entry<AstNode, Map<String, Assignment>>> iter = m_listBreaks.iterator();
                    iter.hasNext(); )
                {
                Map.Entry<AstNode, Map<String, Assignment>> entry = iter.next();
                if (entry.getKey().isDiscarded())
                    {
                    iter.remove();
                    }
                else
                    {
                    ctx.merge(entry.getValue());
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

        boolean fCompletes = fReachable & emit(ctx, fReachable, code, errs);

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
    private transient List<Map.Entry<AstNode, Map<String, Assignment>>> m_listBreaks;
    }
