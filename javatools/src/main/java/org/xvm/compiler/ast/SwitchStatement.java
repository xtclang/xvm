package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

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
        boolean                fValid    = true;
        CaseManager<CaseGroup> mgr       = new CaseManager<>(this);
        List<Statement>        listStmts = block.stmts;
        int                    nArity    = mgr.computeArity(listStmts, errs);

        if (nArity == 0)
            {
            return null; // an error must've been reported
            }

        m_listGroups = new ArrayList<>();
        m_casemgr    = mgr;

        // create a new context in case there are short-circuiting conditions that result in
        // narrowing inferences; for example:
        //   Int[]? args = ...;
        //   switch (args?.size > 0)
        //       {
        //       case 1: // "args" is know to be an array of one Int value
        //       }
        // this context will also be used to keep variables declared by the switch condition and
        // compute inferences if the switch statement doesn't complete (all possible branches are
        // covered or there is a default)
        SwitchContext ctxSwitch = new SwitchContext(ctx, mgr);

        fValid &= mgr.validateCondition(ctxSwitch, conds, nArity, errs);

        int              cStmts    = listStmts.size();
        boolean          fInCase   = false;
        CaseBlockContext ctxBlock  = null;
        CaseGroup        group     = null;
        for (int i = 0; i < cStmts; ++i)
            {
            Statement stmt = listStmts.get(i);
            if (stmt instanceof CaseStatement stmtCase)
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

                fValid &= mgr.validateCase(ctxSwitch, stmtCase, errs);
                }
            else
                {
                if (fInCase)
                    {
                    assert group != null && group.iFirstStmt < 0;
                    group.iFirstStmt = i;
                    mgr.endCaseGroup(group);
                    fInCase = false;

                    assert ctxBlock == null;

                    ctxBlock = ctxSwitch.enterBlock();
                    if (fValid && mgr.hasTypeConditions())
                        {
                        // for now, we only infer a type from a single-case blocks
                        int           cCases   = 0;
                        CaseStatement stmtCase = null;
                        for (int iCase = group.iFirstCase; iCase < group.iFirstStmt; iCase++)
                            {
                            stmtCase = (CaseStatement) listStmts.get(iCase);
                            cCases  += stmtCase.getExpressionCount();
                            }
                        if (cCases == 1)
                            {
                            mgr.addTypeInference(ctxBlock, stmtCase, errs);
                            }
                        }

                    // associate any previous "fall through" with this pseudo statement block
                    if (m_labelContinue != null)
                        {
                        boolean fContinues = false;
                        for (Entry<AstNode, Map<String, Assignment>> entry : m_listContinues)
                            {
                            if (!entry.getKey().isDiscarded())
                                {
                                ctxBlock.merge(entry.getValue());
                                fContinues = true;
                                }
                            }

                        if (fContinues)
                            {
                            ctx.setReachable(true);
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

            ctxBlock.exit();
            }

        // notify the case manager that we're finished collecting everything
        fValid &= mgr.validateEnd(ctxSwitch, errs);

        if (m_listContinues != null)
            {
            // the last "continue" is translated as a "break"
            for (Entry<AstNode, Map<String, Assignment>> entry : m_listContinues)
                {
                if (!entry.getKey().isDiscarded())
                    {
                    addBreak(entry.getKey(), entry.getValue(), Collections.EMPTY_MAP, m_labelContinue);
                    }
                }
            m_listContinues = null;
            }

        if (!m_listBreaks.isEmpty())
            {
            ctxSwitch.mergeBreaks(m_listBreaks);
            m_listBreaks.clear();
            }

        ctx = ctxSwitch.exit();

        return fValid ? this : null;
        }

    @Override
    protected void addBreak(AstNode nodeOrigin, Map<String, Assignment> mapAsn,
                            Map<String, Argument> mapsArg, Label label)
        {
        // we will process the assignments ourselves; see SwitchContext.mergeBreaks()
        m_listBreaks.add(mapAsn);

        super.addBreak(nodeOrigin, Collections.EMPTY_MAP, Collections.EMPTY_MAP, label);
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        CaseManager<CaseGroup> mgr = m_casemgr;

        // check for the extremely rare possibility that the switch condition is a constant, and we
        // can tell which branch to use (discarding the rest of the possible case branches)
        if (mgr.isSwitchConstant())
            {
            // skip the condition and just spit out the reachable code inside the case group(s)
            CaseGroup groupStart = mgr.getCookie(mgr.getSwitchConstantLabel());
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

        if (mgr.hasDeclarations())
            {
            code.add(new Enter());
            }

        if (mgr.usesIfLadder())
            {
            mgr.generateIfLadder(ctx, code, block.stmts, errs);
            }
        else
            {
            mgr.generateJumpTable(ctx, code, errs);
            }

        for (int iGroup = 0, cGroups = m_listGroups.size(); iGroup < cGroups; ++iGroup)
            {
            emitCaseGroup(ctx, fReachable, code, iGroup, errs);
            }

        if (mgr.hasDeclarations())
            {
            code.add(new Exit());
            }

        // if the last case group block has a "continue", then it "continues" to the same place that
        // a "break" would "break", i.e. the end
        if (m_labelContinue != null)
            {
            code.add(m_labelContinue);
            }

        return mgr.isCompletable();
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


    // ----- inner classes -------------------------------------------------------------------------

    /**
     * The context for the SwitchStatement that is used to compute inferences if the switch
     * {@link CaseManager#isCompletable() doesn't complete}.
     */
    protected static class SwitchContext
            extends Context
        {
        protected SwitchContext(Context ctxOuter, CaseManager mgr)
            {
            super(ctxOuter, true);

            f_mgr = mgr;
            }

        /**
         * @return a nested "case block" of this context.
         */
        protected CaseBlockContext enterBlock()
            {
            CaseBlockContext ctxBlock = new CaseBlockContext(this);
            f_listBlocks.add(ctxBlock);
            return ctxBlock;
            }

        @Override
        public Context exit()
            {
            Context ctxOuter = getOuterContext();

            boolean fCompletes = f_mgr.isCompletable();

            promoteAssignments(ctxOuter);

            // if a switch statement covers all of the possible values, or has a default label,
            // then the switch statement does not complete normally and may be able to promote
            // narrowed types to the outer context
            if (!fCompletes)
                {
                promoteNarrowedTypes();
                ctxOuter.setReachable(false);
                }
            return ctxOuter;
            }

        @Override
        protected void promoteNarrowedTypes()
            {
            // to be able to promote a narrowed type, that type should satisfy *all* blocks;
            // any block that doesn't explicitly do it causes any narrowing type to be discarded
            // TODO: this logic may cause a false positive (error) for blocks ending with "continue";
            //       need to add some information to the CasBlockContext to handle that scenario
            List<CaseBlockContext> listBlocks = f_listBlocks;
            int                    cBlocks    = listBlocks.size();

            if (cBlocks == 0)
                {
                // there's already been an error logged to get to this point
                return;
                }

            if (cBlocks == 1)
                {
                // degenerated case - just take all the only block has
                listBlocks.get(0).promoteNarrowedTypes();
                return;
                }

            // collect all narrowed names, counting the occurrences
            Map<String, Integer> mapAllNames = new HashMap<>();
            for (CaseBlockContext ctxBlock : listBlocks)
                {
                for (String sName : ctxBlock.getNameMap().keySet())
                    {
                    if (!ctxBlock.isVarDeclaredInThisScope(sName))
                        {
                        mapAllNames.compute(sName, (k, v) -> v == null ? 1 : v + 1);
                        }
                    }
                }

            // now only process arguments that were narrowed by *every* block
            Map<String, Argument> mapThis = ensureNameMap();
            CaseBlockContext      ctx0    = listBlocks.get(0);
            for (Entry<String, Integer> entry : mapAllNames.entrySet())
                {
                String sName = entry.getKey();
                if (entry.getValue() != cBlocks)
                    {
                    // there is no consensus across the case blocks - restore the original type
                    Argument argOrig = getVar(sName);
                    if (argOrig instanceof Register reg && !reg.isInPlace())
                        {
                        mapThis.put(sName, reg.restoreType());
                        }
                    continue;
                    }

                Argument argPrev = ctx0.getNameMap().get(sName);
                for (int i = 1; i < cBlocks; i++)
                    {
                    Argument argNext = listBlocks.get(i).getNameMap().get(sName);

                    TypeConstant typePrev = argPrev.getType();
                    TypeConstant typeNext = argNext.getType();

                    if (typePrev.isA(typeNext))
                        {
                        mapThis.put(sName, argNext);
                        argPrev = argNext;
                        }
                    else if (typeNext.isA(typePrev))
                        {
                        mapThis.put(sName, argPrev);
                        }
                    else
                        {
                        // no consensus; don't promote anything (though, theoretically speaking we
                        // could find an intermediate type "in between" the original and the widest
                        // of all case blocks)
                        mapThis.remove(sName);
                        break;
                        }
                    }
                }

            if (!mapThis.isEmpty())
                {
                super.promoteNarrowedTypes();
                }
            }

        /**
         * Merge a list of previously prepared set of variable assignment information into this
         * context by collecting assignments that were defined by *every* block.
         *
         * @param listAdd a list of maps of assignments from a previous calls to {@link Statement#addBreak)}
         */
        protected void mergeBreaks(List<Map<String, Assignment>> listAdd)
            {
            // collect all assigned names, counting the occurrences and collect the current assignments
            Map<String, Integer>    mapAllNames = new HashMap<>();
            Map<String, Assignment> mapThis     = new HashMap<>();
            for (Map<String, Assignment> mapAdd : listAdd)
                {
                for (String sName : mapAdd.keySet())
                    {
                    mapThis.computeIfAbsent(sName, this::getVarAssignment);
                    mapAllNames.compute(sName, (k, v) -> v == null ? 1 : v + 1);
                    }
                }

            boolean fCompletes = f_mgr.isCompletable();
            int     cBlocks    = listAdd.size();
            for (Map<String, Assignment> mapAdd : listAdd)
                {
                for (Iterator<Entry<String, Assignment>> iter = mapAdd.entrySet().iterator();
                        iter.hasNext();)
                    {
                    Entry<String, Assignment> entry = iter.next();

                    String     sName   = entry.getKey();
                    Assignment asnThat = entry.getValue();
                    Assignment asnThis = mapThis.get(sName);

                    if (mapAllNames.get(sName) == cBlocks && !fCompletes)
                        {
                        // every block assigns this var and the entire value domain is covered -
                        //
                        if (asnThis.isDefinitelyAssigned())
                            {
                            mapThis.put(sName, asnThis.join(asnThat));
                            }
                        else
                            {
                            mapThis.put(sName, asnThat);
                            }
                        }
                    else
                        {
                        // not all blocks assign this var or there is no "default" -
                        // unassigned should stay unassigned, otherwise - simply join
                        if (!asnThis.isDefinitelyUnassigned())
                            {
                            mapThis.put(sName, asnThis.join(asnThat));
                            }
                        }
                    }
                }

            ensureDefiniteAssignments().putAll(mapThis);
            }

        @Override
        protected void unlink(Context ctxDiscarded)
            {
            List<CaseBlockContext> list = f_listBlocks;
            for (int i = list.size() - 1; i >= 0; i--)
                {
                if (list.get(i) == ctxDiscarded)
                    {
                    list.remove(i);
                    }
                }
            }

        private final CaseManager            f_mgr;
        private final List<CaseBlockContext> f_listBlocks = new ArrayList<>();
        }

    /**
     * While not immediately apparent, a case block never completes normally; it must break,
     * continue (fall through), or otherwise fail to complete (e.g. throw or return), otherwise an
     * error occurs (detected during emit). However, it cannot be simply discarded (instead of
     * exiting), since it needs to pass up all collected assignment information while discarding all
     * other data.
     */
    protected static class CaseBlockContext
            extends Context
        {
        protected CaseBlockContext(SwitchContext ctxOuter)
            {
            super(ctxOuter, true);
            }

        @Override
        public Context exit()
            {
            // don't contribute; the break statement has already registered the "Statement.Break"
            // info that will be processed at the end of Statement.validate() logic
            return getOuterContext();
            }
        }

    /**
     * Holds information about a case group.
     */
    protected static class CaseGroup
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
    private transient List<Entry<AstNode, Map<String, Assignment>>> m_listContinues;

    /**
     * This collects the assignment information that comes from each "break" statement.
     */
    private transient final List<Map<String, Assignment>> m_listBreaks = new ArrayList<>();

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "conds", "block");
    }