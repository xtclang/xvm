package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

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
        boolean fValid  = true;
        Context ctxOrig = ctx;

        // validate the switch condition
        m_casemgr = new CaseManager<CaseGroup>(this);
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
        CaseGroup       group     = null;
        for (int i = 0; i < cStmts; ++i)
            {
            Statement stmt = listStmts.get(i);
            if (stmt instanceof CaseStatement)
                {
                if (ctxBlock != null)
                    {
                    assert group != null;
                    group.fScope          = ctxBlock.isAnyVarDeclaredInThisScope();
                    group.labelContinueTo = m_labelContinue;
                    group = null;

                    ctxBlock.exit();
                    ctxBlock = null;
                    }

                if (!fInCase)
                    {
                    fInCase = true;

                    assert group == null;
                    group            = new CaseGroup();
                    group.iGroup     = m_listGroups.size();
                    group.iFirstCase = i;
                    m_listGroups.add(group);
                    }

                fValid &= m_casemgr.validateCase(ctx, (CaseStatement) stmt, errs);
                }
            else
                {
                if (fInCase)
                    {
                    assert group != null && group.iFirstStmt < 0;
                    group.iFirstStmt = i;
                    m_casemgr.endCaseGroup(group);
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
            assert group != null;
            group.fScope          = ctxBlock.isAnyVarDeclaredInThisScope();
            group.labelContinueTo = m_labelContinue;

            ctxBlock.exit();
            }

        // close the context used for an "if ladder"
        if (m_casemgr.usesIfLadder())
            {
            ctx = ctx.exit();
            }

        // notify the case manager that we're finished collecting everything
        fValid &= m_casemgr.validateEnd(ctx, errs);

        if (m_listContinues != null)
            {
            // the last "continue" donates asn deltas in roughtly the same way that a "break" would
            for (Map<String, Assignment> mapAsn : m_listContinues)
                {
                ctxOrig.merge(mapAsn);
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // check for the extremely rare possibility that the switch condition is a constant and we
        // can tell which branch to use (discarding the rest of the possible case branches)
        if (m_casemgr.isSwitchConstant())
            {
            // skip the condition and just spit out the reachable code inside the case group(s)
            CaseGroup groupStart = m_casemgr.getCookie(m_casemgr.getSwitchConstantLabel());
            for (int iGroup = groupStart.iGroup, cGroups = m_listGroups.size(); iGroup < cGroups; ++iGroup)
                {
                emitCaseGroup(ctx, fReachable, code, iGroup, errs);
                if (m_listGroups.get(iGroup).labelContinueTo == null)
                    {
                    // if there was no "continue", then we're done
                    break;
                    }
                }

            // switch never completes normally
            return false;
            }

        if (m_casemgr.hasDeclarations())
            {
            code.add(new Enter());
            }

        // TODO
//        if (cond instanceof AssignmentStatement)
//            {
//            AssignmentStatement stmtCond = (AssignmentStatement) cond;
//            fCompletesCond = stmtCond.completes(ctx, fReachable, code, errs);
//            code.add(new JumpFalse(stmtCond.getConditionRegister(), labelElse));
//            }
//        else
//            {
//            Expression exprCond = (Expression) cond;
//            fCompletesCond = exprCond.isCompletable();
//            exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
//            }

        if (m_casemgr.hasDeclarations())
            {
            code.add(new Exit());
            }

        // if the last case group block has a "continue", then it "continues" to the same place that
        // a "break" would "break", i.e. the end
        if (m_labelContinue != null)
            {
            code.add(m_labelContinue);
            }

        return fReachable;
        }

    private void emitCaseGroup(Context ctx, boolean fReachable, Code code, int iGroup, ErrorListener errs)
        {
        boolean   fCompletes = fReachable;
        CaseGroup group      = m_listGroups.get(iGroup);

        // the label for any "continue" from the last group
        if (iGroup > 0)
            {
            Label labelContinueFrom = m_listGroups.get(iGroup - 1).labelContinueTo;
            if (labelContinueFrom != null)
                {
                code.add(labelContinueFrom);
                }
            }

        // the label assigned to the case group
        code.add(m_casemgr.getCaseLabels()[iGroup]);

        if (group.fScope)
            {
            code.add(new Enter());
            }

        List<Statement> listStmts = block.stmts;
        for (int iStmt = group.iFirstStmt, cStmts = listStmts.size(); iStmt < cStmts; ++iStmt)
            {
            Statement stmt = listStmts.get(iStmt);

            if (stmt instanceof CaseStatement)
                {
                if (fReachable && fCompletes)
                    {
                    stmt.log(errs, Severity.ERROR, Compiler.SWITCH_BREAK_OR_CONTINUE_EXPECTED);
                    }
                break;
                }

            if (fReachable && !fCompletes)
                {
                // this statement is the first statement that cannot be reached;
                // the only thing that is allowed is an inner class definition
                fReachable = false;
                if (!(stmt instanceof TypeCompositionStatement))
                    {
                    stmt.log(errs, Severity.ERROR, Compiler.NOT_REACHABLE);
                    }
                }

            fCompletes = stmt.completes(ctx, fCompletes, code, errs);
            }

        if (group.fScope)
            {
            code.add(new Exit());
            }
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


    // ----- inner class: CaseGroup ----------------------------------------------------------------

    /**
     * Holds information about a case group.
     */
    class CaseGroup
        {
        /**
         * What group number is this? Zero-based.
         */
        int iGroup     = -1;
        /**
         * What is the index of the first case statement of this group? Index into "block".
         */
        int iFirstCase = -1;
        /**
         * What is the index of the first non-case statement of this group? Index into "block".
         */
        int iFirstStmt = -1;
        /**
         * Does this group need an enter/exit?
         */
        boolean fScope;
        /**
         * Does this group ever "continue"? If so, this is the label that it continues to.
         */
        Label   labelContinueTo;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected List<AstNode>  cond;
    protected StatementBlock block;

    /**
     * The manager that collects all of the case information.
     */
    private transient CaseManager<CaseGroup> m_casemgr;

    /**
     * Information about each group that begins with one or more CaseStatements and is followed by
     * other statements.
     */
    private transient List<CaseGroup> m_listGroups = new ArrayList<>();

    /**
     * A list of continuation labels, corresponding to the case groups from the CaseManager.
     */
    private transient Label m_labelContinue;

    /**
     * A list of continuation labels, corresponding to the case groups from the CaseManager.
     */
    private transient List<Map<String, Assignment>> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
