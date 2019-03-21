package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * @return the label corresponding to the beginning of the Statement
     */
    public Label getBeginLabel()
        {
        Label label = m_labelBegin;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelBegin = label = new Label();
            }
        return label;
        }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel()
        {
        Label label = m_labelEnd;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelEnd = label = new Label();
            }
        return label;
        }

    /**
     * @return true iff a "continue" statement can apply to this statement
     */
    public boolean isNaturalShortCircuitStatementTarget()
        {
        return false;
        }

    /**
     * Obtain the label to "break" to.
     *
     * @param ctxOrigin  the context from the point under this statement of the "break"
     *
     * @return the label to jump to when a "break" occurs within (or for) this statement
     */
    public Label ensureBreakLabel(Context ctxOrigin)
        {
        Context ctxDest = getValidationContext();
        assert ctxDest != null;

        // generate a delta of assignment information for the long-jump
        Map<String, Assignment> mapAsn = ctxOrigin.prepareJump(ctxDest);

        // record the long-jump that landed on this statement by recording its assignment impact
        if (m_listBreaks == null)
            {
            m_listBreaks = new ArrayList<>();
            }
        m_listBreaks.add(mapAsn);

        return getEndLabel();
        }

    /**
     * Obtain the label to "continue" to.
     *
     * @param ctxOrigin  the context from the point under this statement of the "continue"
     *
     * @return the label to jump to when a "continue" occurs within (or for) this statement
     */
    public Label ensureContinueLabel(Context ctxOrigin)
        {
        throw new IllegalStateException();
        }

    // REVIEW need to think through "any statement allows expression to short circuit by default" decision
    // TODO how to clean up the stack (A_STACK register) when short-circuit occurs?

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        return true;
        }

    @Override
    protected Label getShortCircuitLabel(Context ctx, Expression exprChild)
        {
        assert allowsShortCircuit(exprChild);
        return getEndLabel();
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
        // before validating the nested code, associate this statement with the context so that any
        // "break" or "continue" can find the context to apply assignment data to
        m_ctx = ctx;
        Statement stmt = validateImpl(ctx, errs);
        m_ctx = null;

        List<Map<String, Assignment>> listBreaks = m_listBreaks;
        if (listBreaks != null)
            {
            for (Map<String, Assignment> mapAsn : listBreaks)
                {
                ctx.merge(mapAsn);
                }
            }

        return stmt;
        }

    /**
     * @return the Context being used for validation of this statement, if this statement is
     *         currently being validated
     */
    protected Context getValidationContext()
        {
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
    protected Statement validateImpl(Context ctx, ErrorListener errs) // TODO make abstract
        {
        throw notImplemented();
        }

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

        boolean fBeginLabel = m_labelBegin != null;
        if (fBeginLabel)
            {
            code.add(m_labelBegin);
            }

        boolean fCompletes = fReachable & emit(ctx, fReachable, code, errs);

        // a begin label should not have been requested during the emit stage unless it had been
        // requested previously (since it's too late to add it now!)
        assert fBeginLabel == (m_labelBegin != null);

        if (m_labelEnd != null)
            {
            code.add(m_labelEnd);
            }

        m_fEmitted = true;
        return fCompletes || fReachable && m_listBreaks != null;
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
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) // TODO make abstract
        {
        throw notImplemented();
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fEmitted;

    /**
     * The Context that contains this statement as it validates.
     */
    private transient Context m_ctx;
    /**
     * Generally null, unless there is a break that long-jumps to this statement's exit label.
     */
    private transient List<Map<String, Assignment>> m_listBreaks;
    }
