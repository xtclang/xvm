package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.Map.Entry;
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
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchStatement(Token keyword, List<AstNode> conds, StatementBlock block)
        {
        super(keyword, conds);
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isNaturalGotoStatementTarget()
        {
        return true;
        }

    @Override
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        Context ctxDest = ensureValidationContext();

        if (m_labelContinue == null)
            {
            assert m_listContinues == null;

            int iGroup = m_casemgr.getCaseGroupCount() - 1;
            assert iGroup >= 0;

            m_labelContinue = new Label("fall_through_from_case_group_" + iGroup);
            m_listContinues = new ArrayList<>();
            }

        if (ctxOrigin.isReachable())
            {
            // record the jump that will either fall-through to the next case group or (if this is
            // the last case group) break out of this switch statement by recording its assignment
            // impact (a delta of assignment information)
            m_listContinues.add(new SimpleEntry<>(nodeOrigin, ctxOrigin.prepareJump(ctxDest)));
            }

        return m_labelContinue;
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid  = true;
        Context ctxOrig = ctx;

        // validate the switch condition
        m_listGroups = new ArrayList<>();
        m_casemgr    = new CaseManager<CaseGroup>(this);

        // create an new context in case there are short-circuiting conditions that result in
        // narrowing inferences; for example:
        //   Int[]? args = ...;
        //   switch (args?.size > 0)
        //       {
        //       case 1: // "args" is know to be an array of one Int value
        //       }

        ctx = ctx.enter();

        fValid &= m_casemgr.validateCondition(ctx, conds, errs);

        // the case manager enters a new context if the switch condition declares variables
        Context ctxCond = m_casemgr.getSwitchContext();
        if (ctxCond != null)
            {
            ctx = ctxCond;
            }

        // TODO this is probably all wrong now; needs REVIEW CP
        if (m_casemgr.usesIfLadder())
            {
            // a switch that uses an "if ladder" may have side-effects of the various case
            // statements that effect assignment, so treat the context containing the case
            // statements as one big branch whose completion represents one possible path
            ctx = ctx.enter();
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

                    // while not immediately apparent, a case block never completes normally; it
                    // must break, continue (fall through), or otherwise fail to complete (e.g.
                    // throw or return), otherwise an error occurs (detected during emit); instead
                    // of exiting the context, it is simply discarded at this point
                    ctxBlock.discard();
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

                    int cCases = 0;
                    for (int iCase = group.iFirstCase; iCase < group.iFirstStmt; iCase++)
                        {
                        cCases += ((CaseStatement) listStmts.get(iCase)).getExpressionCount();
                        }

                    ctxBlock = m_casemgr.enterBlock(ctx, cCases, fValid);

                    // associate any previous "fall through" with this pseudo statement block
                    if (m_labelContinue != null)
                        {
                        for (Entry<AstNode, Map<String, Assignment>> entry : m_listContinues)
                            {
                            if (!entry.getKey().isDiscarded())
                                {
                                ctxBlock.merge(entry.getValue());
                                }
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

            if (ctxBlock.isReachable())
                {
                errs.log(Severity.ERROR, Compiler.SWITCH_BREAK_OR_CONTINUE_EXPECTED, null,
                        getSource(), getEndPosition(), getEndPosition());
                fValid = false;
                }

            ctxBlock.discard();
            ctxBlock = null;
            }

        // close the context used for an "if ladder"
        // REVIEW (see corresponding section above)
        if (m_casemgr.usesIfLadder())
            {
            ctx = ctx.exit();
            }

        // notify the case manager that we're finished collecting everything
        fValid &= m_casemgr.validateEnd(ctx, errs);

        ctx = ctx.exit();

        // if a switch statement covers all of the possible values, or has a default label, then the
        // switch statement does not complete normally
        if (!m_casemgr.isCompletable())
            {
            ctxOrig.setReachable(false);
            }

        if (m_listContinues != null)
            {
            // the last "continue" is translated as a "break"
            for (Entry<AstNode, Map<String, Assignment>> entry : m_listContinues)
                {
                if (!entry.getKey().isDiscarded())
                    {
                    addBreak(entry.getKey(), entry.getValue(), m_labelContinue);
                    }
                }
            m_listContinues = null;
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

        if (m_casemgr.usesIfLadder())
            {
            m_casemgr.generateIfLadder(ctx, code, block.stmts, errs);
            }
        else
            {
            m_casemgr.generateJumpTable(ctx, code, errs);
            }

        for (int iGroup = 0, cGroups = m_listGroups.size(); iGroup < cGroups; ++iGroup)
            {
            emitCaseGroup(ctx, fReachable, code, iGroup, errs);
            }

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

        return m_casemgr.isCompletable();
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
        List<Statement> listStmts = block.stmts;
        code.add(((CaseStatement) listStmts.get(group.iFirstCase)).getLabel());

        if (group.fScope)
            {
            code.add(new Enter());
            }

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

        if (conds != null)
            {
            sb.append(conds.get(0));
            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
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

    protected StatementBlock block;

    /**
     * The manager that collects all of the case information.
     */
    private transient CaseManager<CaseGroup> m_casemgr;

    /**
     * Information about each group that begins with one or more CaseStatements and is followed by
     * other statements.
     */
    private transient List<CaseGroup> m_listGroups;

    /**
     * For a given case group, this is the label that each "continue" statement within that group
     * will jump to; it's null until the first continue statement is encountered in the group.
     */
    private transient Label m_labelContinue;

    /**
     * For a given case group, this collects the assignment information that comes from each
     * "continue" statement within that group; it's null until the first continue statement is
     * encountered in the group.
     */
    private transient List<Map.Entry<AstNode, Map<String, Assignment>>> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "conds", "block");
    }
