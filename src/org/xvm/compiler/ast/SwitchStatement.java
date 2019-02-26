package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 */
public class SwitchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchStatement(Token keyword, List<AstNode> cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isNaturalShortCircuitStatementTarget()
        {
        return true;
        }

    @Override
    public Label ensureContinueLabel(Context ctxOrigin)
        {
        Context ctxDest = getValidationContext();
        assert ctxDest != null;

        if (m_labelContinue == null)
            {
            assert m_listContinues == null;

            int iGroup = m_casemgr.getCaseGroupCount() - 1;
            assert iGroup >= 0;

            m_labelContinue = new Label("fall_through_from_case_group_" + iGroup);
            m_listContinues = new ArrayList<>();
            }

        // record the long-jump that will either fall-through to the next case group or (if this is
        // the last case group) break out of this switch statement by recording its assignment
        // impact (a delta of assignment information)
        m_listContinues.add(ctxOrigin.prepareJump(ctxDest));

        return m_labelContinue;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        // the expressions are in the condition portion
        return true;
        }

    @Override
    protected Label getShortCircuitLabel(Context ctx, Expression exprChild)
        {
        // TODO definite assignment implications
        return getEndLabel();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // validate the switch condition
        m_casemgr = new CaseManager(this);
        fValid   &= m_casemgr.validateCondition(ctx, cond, errs);

        // the case manager enters a new context if the switch condition declares variables
        Context ctxCond = m_casemgr.getSwitchContext();
        if (ctxCond != null)
            {
            ctx = ctxCond;
            }

        if (m_casemgr.usesIfLadder())
            {
            // a switch that uses an "if ladder" may have side-effects of the various case
            // statements that effect assignment, so treat the context containing the case
            // statements as one big branch whose completion represents one possible path
            ctx = ctx.enter();
            ctx.markNonCompleting();
            }

        List<Statement> listStmts = block.stmts;
        int             cStmts    = listStmts.size();
        boolean         fInCase   = false;
        Context         ctxBlock  = null;
        for (int i = 0; i < cStmts; ++i)
            {
            Statement stmt = listStmts.get(i);
            if (stmt instanceof CaseStatement)
                {
                if (ctxBlock != null)
                    {
                    ctxBlock.exit();
                    ctxBlock = null;
                    }

                fValid &= m_casemgr.validateCase(ctx, (CaseStatement) stmt, errs);
                fInCase = true;
                }
            else
                {
                if (fInCase)
                    {
                    // use the statement index "i" as the cookie for the case group, so that we can
                    // easily find the first statement of the case group's implicit statement block
                    m_casemgr.endCaseGroup(i);
                    fInCase = false;

                    assert ctxBlock == null;
                    ctxBlock = ctx.enter();

                    // while not immediately apparent, a case block never completes normally; it
                    // must break, continue (fall through), or otherwise fail to complete (e.g.
                    // throw or return), otherwise an error occurs (detected during emit); by
                    // marking the context as non-completing, we prevent the incorrect leaking of
                    // assignment information from inside the block if the block does not break
                    // unconditionally at its termination
                    ctxBlock.markNonCompleting();

                    // associate any previous "fall through" with this pseudo statement block
                    if (m_labelContinue != null)
                        {
                        stmt.setBeginLabel(m_labelContinue);
                        for (Map<String, Assignment> mapAsn : m_listContinues)
                            {
                            ctxBlock.merge(mapAsn);
                            }

                        m_labelContinue = null;
                        m_listContinues = null;
                        }
                    }

                Statement stmtNew = stmt.validate(ctxBlock, errs);
                if (stmtNew != stmt)
                    {
                    if (stmtNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        block.stmts = listStmts = ensureArrayList(listStmts);
                        listStmts.set(i, stmtNew);
                        }
                    }
                }

            if (errs.isAbortDesired())
                {
                break;
                }
            }

        // close any last block
        if (ctxBlock != null)
            {
            ctx = ctxBlock.exit();
            }

        // close the context used for an "if ladder"
        if (m_casemgr.usesIfLadder())
            {
            ctx = ctx.exit();
            }

        if (m_listContinues != null)
            {
            // the last "continue" donates asn deltas in roughtly the same way that a "break" would
            // REVIEW does this go AFTER validateEnd() i.e. on the original passed-in ctx?
            for (Map<String, Assignment> mapAsn : m_listContinues)
                {
                ctx.merge(mapAsn);
                }
            }

        // notify the case manager that we're finished collecting everything
        fValid &= m_casemgr.validateEnd(ctx, errs);

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletesCond;
        if (m_casemgr.isSwitchConstant())
        else if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtCond = (AssignmentStatement) cond;
            fCompletesCond = stmtCond.completes(ctx, fReachable, code, errs);
            code.add(new JumpFalse(stmtCond.getConditionRegister(), labelElse));
            }
        else
            {
            Expression exprCond = (Expression) cond;
            fCompletesCond = exprCond.isCompletable();
            exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
            }

        boolean fCompletes = fReachable;

        // TODO

        // if the last case group block has a "continue", then it "continues" to the same place that
        // a "break" would "break", i.e. the end
        if (m_labelContinue != null)
            {
            code.add(m_labelContinue);
            }

        return fReachable;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (");

        if (cond != null)
            {
            sb.append(cond.get(0));
            for (int i = 1, c = cond.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(cond.get(i));
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected List<AstNode>  cond;
    protected StatementBlock block;

    private transient CaseManager m_casemgr;

    /**
     * A list of continuation labels, corresponding to the case groups from the CaseManager.
     */
    private transient Label m_labelContinue;

    /**
     * A list of continuation labels, corresponding to the case groups from the CaseManager.
     */
    private transient List<Map<String, Assignment>> m_listContinues;

    /**
     * A list of continuation labels, corresponding to the case groups from the CaseManager.
     */
    // TODO private transient List<Map<String, Assignment>> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
