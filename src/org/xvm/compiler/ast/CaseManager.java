package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpInt;
import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.JumpVal_N;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;

import org.xvm.util.ListMap;
import org.xvm.util.ListSet;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


/**
 * A "switch" expression.
 */
public class CaseManager
    {
    // ----- constructors --------------------------------------------------------------------------

    public CaseManager(AstNode nodeSwitch)
        {
        m_nodeSwitch = nodeSwitch;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the node that created this manager
     */
    public AstNode getSwitch()
        {
        return m_nodeSwitch;
        }

    /**
     * @return the constant pool to use
     */
    public ConstantPool pool()
        {
        return getSwitch().pool();
        }

    /**
     * @return the type of the condition (which is the basis for the type of each case expression)
     */
    public TypeConstant getConditionType()
        {
        return m_typeCase;
        }

    /**
     * @return the number of condition values for this switch
     */
    public int getConditionCount()
        {
        return m_cCondVals;
        }

    /**
     * @return true if the condition value require a scope to be created for the switch
     */
    public boolean hasDeclarations()
        {
        return m_fScope;
        }

    /**
     * @return true if the switch is operating with a cardinal set of values (allowing an offset
     *         jump, for example)
     */
    public boolean isCardinal()
        {
        return getConditionCount() == 1 && getConditionType().isIntConvertible();
        }

    /**
     * @return true iff any of the case expressions use an interval or range value match
     */
    public boolean usesNonExactMatching()
        {
        return m_listWild != null;
        }

    /**
     * @return true iff the switch has no conditions, which implies that each case expression is an
     *         "if" condition, with the flow of control transferring to the first one that evaluates
     *         to true
     */
    public boolean usesIfLadder()
        {
        return getConditionCount() == 0;
        }

    /**
     * @return true iff the JMP_VAL_N op is required, because the condition is multiple, or because
     *         wildcards or intervals are used
     */
    public boolean usesJmpValN()
        {
        return getConditionCount() > 1 || usesNonExactMatching();
        }

    /**
     * @return true if the switch condition is constant and as a result the case to use is known at
     *         compile time; this value is available after validateEnd()
     */
    public boolean isSwitchConstant()
        {
        return m_labelConstant != null;
        }

    /**
     * @return the case label to use if the switch condition is constant and as a result the case to
     *         use is known at compile time; this value is available after validateEnd()
     */
    public Label getSwitchConstantLabel()
        {
        return m_labelConstant;
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Validate the condition value expressions.
     *
     * @param ctx       the validation context
     * @param listCond  the list of condition value expressions (mutable)
     * @param errs      the error list to log to
     *
     * @return true iff the validation succeeded
     */
    protected boolean validateCondition(Context ctx, List<AstNode> listCond, ErrorListener errs)
        {
        boolean      fValid     = true;
        ConstantPool pool       = pool();
        boolean      fIfSwitch  = listCond == null;
        if (fIfSwitch)
            {
            // there is no condition, so all of the case statements must evaluate to boolean, and
            // the first one that matches "True" will be used
            m_cCondVals  = 0;
            m_atypeCond  = new TypeConstant[] {pool.typeBoolean()};
            m_aconstCond = new Constant[] {pool.valTrue()};
            }
        else
            {
            List<TypeConstant> listTypes  = new ArrayList<>();
            List<Constant>     listConsts = new ArrayList<>();
            boolean            fAllConst  = true;
            for (int i = 0, c = listCond.size(); i < c; ++i)
                {
                AstNode        node    = listCond.get(i);
                AstNode        nodeNew = null;
                TypeConstant[] atype   = null;
                Constant[]     aconst  = null;
                if (node instanceof AssignmentStatement)
                    {
                    // assignment represents a side-effect, so disable the constant optimizations
                    fAllConst = false;

                    AssignmentStatement stmtCond = (AssignmentStatement) node;
                    if (!m_fScope && stmtCond.hasDeclarations())
                        {
                        ctx      = ctx.enter();
                        m_fScope = true;
                        }

                    nodeNew = stmtCond.validate(ctx, errs);
                    if (nodeNew != null)
                        {
                        atype = ((AssignmentStatement) nodeNew).getLValue().getLValueExpression().getTypes();
                        }
                    }
                else
                    {
                    nodeNew = ((Expression) node).validate(ctx, null, errs);
                    if (nodeNew != null)
                        {
                        Expression expr = (Expression) nodeNew;
                        atype  = expr.getTypes();
                        aconst = expr.toConstants();
                        }
                    }

                if (nodeNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (nodeNew != node)
                        {
                        listCond.set(i, nodeNew);
                        }
                    }

                if (atype == null)
                    {
                    fValid = false;
                    listTypes.add(pool.typeObject()); // avoids having no types on failure
                    }
                else
                    {
                    for (TypeConstant type : atype)
                        {
                        listTypes.add(type);
                        }
                    }

                if (fAllConst)
                    {
                    if (aconst == null)
                        {
                        fAllConst = false;
                        }
                    else
                        {
                        assert aconst.length == atype.length;
                        for (Constant constant : aconst)
                            {
                            assert constant != null;
                            listConsts.add(constant);
                            }
                        assert listConsts.size() == listTypes.size();
                        }
                    }
                }

            m_cCondVals = listTypes.size();
            m_atypeCond = listTypes.toArray(TypeConstant.NO_TYPES);
            if (fValid && fAllConst)
                {
                m_aconstCond = listConsts.toArray(Constant.NO_CONSTS);
                }
            }

        m_typeCase = pool.ensureImmutableTypeConstant(m_atypeCond.length == 1
                ? m_atypeCond[0]
                : pool.ensureParameterizedTypeConstant(pool.typeTuple(), m_atypeCond));

        return fValid;
        }

    /**
     * Add a "case:" / "default:" statement.
     *
     * @param ctx       the validation context
     * @param stmtCase  the case statement
     * @param errs      the error list to log to
     *
     * @return true iff the validation succeeded
     */
    public boolean validateCase(Context ctx, CaseStatement stmtCase, ErrorListener errs)
        {
        boolean fValid = true;

        // each contiguous group of "case:"/"default:" statements shares a single label
        if (m_labelCurrent == null)
            {
            m_labelCurrent = new Label("case_" + (m_listLabel.size() + 1));
            }

        // each case statement is marked with the label that it will jump to
        stmtCase.setLabel(m_labelCurrent);

        // a case statement has any number of case values, any one of which can match the switch
        // condition; a case statement with no values indicates the "default:" statement
        List<Expression> listExprs = stmtCase.exprs;
        if (listExprs == null)
            {
            if (m_labelDefault == null)
                {
                m_labelDefault = m_labelCurrent;
                }
            else
                {
                stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_DUPLICATE);
                fValid = false;
                }

            return fValid;
            }

        // validate each seperate value in the case label
        ConstantPool pool       = pool();
        boolean      fIfSwitch  = usesIfLadder();
        boolean      fIntConsts = isCardinal() && m_typeCase.getExplicitClassFormat() != Component.Format.ENUM;
        for (int iExpr = 0, cExprs = listExprs.size(); iExpr < cExprs; ++iExpr)
            {
            Expression     exprCase  = listExprs.get(iExpr);
            TypeConstant   typeMatch = m_typeCase;
            long           lIgnore   = 0L;
            long           lRange    = 0L;
            int            cFields   = 1;
            TypeConstant[] atypeAlt  = null;
            if (exprCase instanceof TupleExpression)
                {
                List<Expression> listFields = ((TupleExpression) exprCase).exprs;
                cFields = listFields.size();
                for (int i = 0; i < cFields; ++i)
                    {
                    Expression exprField = listFields.get(i);
                    if (exprField instanceof IgnoredNameExpression)
                        {
                        lIgnore |= 1 << i;
                        }
                    else if (!exprField.testFit(ctx, m_atypeCond[i]).isFit())
                        {
                        TypeConstant typeInterval = pool.ensureParameterizedTypeConstant(
                                pool.typeInterval(), m_atypeCond[i]);
                        if (exprField.testFit(ctx, typeInterval).isFit())
                            {
                            lRange |= 1 << i;

                            if (atypeAlt == null)
                                {
                                atypeAlt = m_atypeCond.clone();
                                }
                            atypeAlt[i] = typeInterval;
                            }
                        }
                    }
                }
            else if (getConditionCount() == 1)
                {
                if (exprCase instanceof IgnoredNameExpression)
                    {
                    lIgnore = 1;
                    }
                else if (!exprCase.testFit(ctx, m_typeCase).isFit())
                    {
                    TypeConstant typeInterval = pool.ensureParameterizedTypeConstant(
                            pool.typeInterval(), m_typeCase);
                    if (exprCase.testFit(ctx, typeInterval).isFit())
                        {
                        lRange    = 1;
                        typeMatch = typeInterval;
                        }
                    }
                }
            else if (exprCase instanceof IgnoredNameExpression)
                {
                // cannot use a single "_" when there are multiple condition values
                exprCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_ILLEGAL_ARITY);
                fValid = false;
                }

            // if everything is a wildcard, then this is the same as a "default:"
            if (Long.bitCount(lIgnore) == cFields)
                {
                if (m_labelDefault == null)
                    {
                    m_labelDefault = m_labelCurrent;
                    }
                else
                    {
                    stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_DUPLICATE);
                    fValid = false;
                    }
                continue;
                }

            // if the validation type changes for this particular case expression
            // (because of an interval match), then create the type that will apply to
            // this particular case expression
            if (atypeAlt != null)
                {
                typeMatch = pool.ensureImmutableTypeConstant(
                        pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeAlt));
                }

            // validate the expression (that contains all of the values for a possible match with
            // the switch condition)
            Expression exprNew = exprCase.validate(ctx, typeMatch, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                if (exprNew != exprCase)
                    {
                    listExprs.set(iExpr, exprCase = exprNew);
                    }

                if (exprCase.isConstant()) // TODO resolve the difference with isRuntimeConstant()
                    {
                    Constant constCase = exprCase.toConstant();
                    if (m_fAllConsts && m_aconstCond != null && m_aconstCond.equals(constCase) // TODO covers (not equals)
                            && m_labelConstant == null)
                        {
                        m_labelConstant = m_labelCurrent;
                        }

                    if (!fIfSwitch)
                        {
                        if (collides(constCase, getConditionCount()==1, m_listsetCase, m_mapWild, lIgnore, lRange))
                            {
                            // collision
                            stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DUPLICATE,
                                    constCase.getValueString());
                            fValid = false;
                            }
                        else
                            {
                            m_listsetCase.add(constCase);
                            m_listLabel.add(m_labelCurrent);

                            if (fIntConsts)
                                {
                                PackedInteger pint = constCase.getIntValue();
                                if (m_pintMin == null)
                                    {
                                    m_pintMin = m_pintMax = pint;
                                    }
                                else if (pint.compareTo(m_pintMax) > 0)
                                    {
                                    m_pintMax = pint;
                                    }
                                else if (pint.compareTo(m_pintMin) < 0)
                                    {
                                    m_pintMin = pint;
                                    }
                                }
                            }
                        }
                    }
                else
                    {
                    m_fAllConsts = false;
                    if (!fIfSwitch)
                        {
                        stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_CONSTANT_REQUIRED);
                        fValid = false;
                        }
                    }
                }
            }

        return fValid;
        }

    /**
     * Mark the end of a series of contiguous "case:" / "default:" statements
     */
    public void endCaseGroup()
        {
        if (m_labelCurrent == null)
            {
            throw new IllegalStateException();
            }

        m_labelCurrent = null;
        }

    /**
     * Finish the switch validation.
     *
     * @param ctx       the validation context
     * @param errs      the error list to log to
     *
     * @return true iff the validation succeeded
     */
    public boolean validateEnd(Context ctx, ErrorListener errs)
        {
        if (m_labelCurrent != null)
            {
            listNodes.get(listNodes.size()-1).log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DANGLING);
            fValid = false;
            }
        else if (m_aconstCond != null && aconstVal == null && m_fAllConsts && aconstDefault != null)
            {
            aconstVal = aconstDefault;
            }
        else if (m_labelDefault == null && (!fCardinals || m_listsetCase.size() < typeCase.getIntCardinality()))
            {
            // this means that the switch would "short circuit", which is not allowed
            log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_REQUIRED);
            fValid = false;
            }

        if (m_fScope)
            {
            ctx = ctx.exit();
            }

        // store the resulting information
        if (!fIfSwitch)
            {
            int cVals = m_listsetCase.size();
            assert m_listLabel.size() == cVals;

            // check if the JumpInt optimization can be used
            // (enums are no longer supported, since cross-module dependencies without
            // re-compilation would cause the values to fail to "line up", so it's better to
            // use the JumpVal option instead of the JumpInt option)
            boolean fUseJumpInt = false;
            int     cRange      = 0;
            if (fValid && pintMin != null)
                {
                PackedInteger pintRange = pintMax.sub(pintMin);
                if (!pintRange.isBig())
                    {
                    // the idea is that if we have ints from 100,000 to 100,100, and there are
                    // 25+ of them, then we should go ahead and use the jump table optimization
                    if (m_listsetCase.size() >= (pintRange.getLong() >>> 2))
                        {
                        cRange      = pintRange.getInt() + 1;
                        fUseJumpInt = true;
                        }
                    }
                }

            if (fUseJumpInt)
                {
                Label[] aLabels = new Label[cRange];
                for (int i = 0; i < cVals; ++i)
                    {
                    Label    label     = m_listLabel.get(i);
                    Constant constCase = m_listsetCase.get(i);
                    int      nIndex    = constCase.getIntValue().sub(pintMin).getInt();

                    aLabels[nIndex] = label;
                    }

                // fill in any gaps
                for (int i = 0; i < cRange; ++i)
                    {
                    if (aLabels[i] == null)
                        {
                        assert m_labelDefault != null;
                        aLabels[i] = m_labelDefault;
                        }
                    }

                m_alabelCase = aLabels;
                m_pintOffset = pintMin;
                }
            else
                {
                m_aconstCase = m_listsetCase.toArray(new Constant[cVals]);
                m_alabelCase = m_listLabel.toArray(new Label   [cVals]);
                }
            }

        m_labelDefault = m_labelDefault;

        TypeConstant[] atypeActual = collector.inferMulti(atypeRequired);
        return finishValidations(atypeRequired, atypeActual, fValid ? TypeFit.Fit : TypeFit.NoFit, aconstVal, errs);
        }

    private boolean collides(Constant constCase, boolean fSingleCond,
            Set<Constant> setCase, ListMap<Constant, Long> mapWild, long lIgnore, long lRange)
        {
        if (!setCase.add(constCase))
            {
            return true;
            }

        for (Entry<Constant, Long> entry : mapWild.entrySet())
            {
            // TODO check wildcards .. _
            }

        if ((lIgnore | lRange) != 0L)
            {
            mapWild.put(constCase, lRange);
            }

        return false;
        }

    private void generateIfSwitch(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        List<AstNode> aNodes = contents;
        int           cNodes = aNodes.size();
        for (int i = 0; i < cNodes; ++i)
            {
            AstNode node = aNodes.get(i);
            if (node instanceof CaseStatement)
                {
                CaseStatement stmt = ((CaseStatement) node);
                if (!stmt.isDefault())
                    {
                    stmt.updateLineNumber(code);
                    Label labelCur = stmt.getLabel();
                    for (Expression expr : stmt.getExpressions())
                        {
                        expr.generateConditionalJump(ctx, code, labelCur, true, errs);
                        }
                    }
                }
            }
        code.add(new Jump(m_labelDefault));

        Label labelCur  = null;
        Label labelExit = new Label("switch_end");
        for (int i = 0; i < cNodes; ++i)
            {
            AstNode node = aNodes.get(i);
            if (node instanceof CaseStatement)
                {
                labelCur = ((CaseStatement) node).getLabel();
                }
            else
                {
                Expression expr = (Expression) node;

                expr.updateLineNumber(code);
                code.add(labelCur);
                expr.generateAssignments(ctx, code, aLVal, errs);
                code.add(new Jump(labelExit));
                }
            }
        code.add(labelExit);
        }

    private void generateJumpSwitch(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        assert cond != null && !cond.isEmpty();

        Label labelDefault = this.m_labelDefault;
        if (labelDefault == null)
            {
            labelDefault = new Label("default_assert");
            }

        Argument[] aArgVal  = new Argument[m_atypeCond.length];
        int        ofArgVal = 0;
        for (AstNode node : cond)
            {
            Expression exprCond;
            if (node instanceof AssignmentStatement)
                {
                AssignmentStatement stmt = (AssignmentStatement) node;
                boolean fCompletes = stmt.completes(ctx, true, code, errs);
                if (!fCompletes)
                    {
                    m_fAborting = true;
                    }
                exprCond = stmt.getLValue().getLValueExpression();
                }
            else
                {
                exprCond = (Expression) node;
                }

            if (m_aconstCase == null && (m_pintOffset != null || !exprCond.getType().isA(pool().typeInt())))
                {
                exprCond = new ToIntExpression(exprCond, m_pintOffset, errs);
                }

            Argument[] aArgsAdd = exprCond.generateArguments(ctx, code, true, true, errs);
            int        cArgsAdd = aArgsAdd.length;
            System.arraycopy(aArgsAdd, 0, aArgVal, ofArgVal, cArgsAdd);
            ofArgVal += cArgsAdd;
            }
        assert ofArgVal == aArgVal.length;

        if (m_aconstCase == null)
            {
            assert cond.size() == 1 && aArgVal.length == 1;
            code.add(new JumpInt(aArgVal[0], m_alabelCase, labelDefault));
            }
        else
            {
            code.add(cond.size() == 1 // TODO && no wildcards && no intervals
                    ? new JumpVal(aArgVal[0], m_aconstCase, m_alabelCase, labelDefault)
                    : new JumpVal_N(aArgVal, m_aconstCase, m_alabelCase, labelDefault));
            }

        Label         labelCur  = null;
        Label         labelExit = new Label("switch_end");
        List<AstNode> aNodes    = contents;
        for (int i = 0, c = aNodes.size(); i < c; ++i)
            {
            AstNode node = aNodes.get(i);
            if (node instanceof CaseStatement)
                {
                Label labelNew = ((CaseStatement) node).getLabel();
                if (labelNew != labelCur)
                    {
                    code.add(labelNew);
                    labelCur = labelNew;
                    }
                }
            else
                {
                node.updateLineNumber(code);
                ((Expression) node).generateAssignments(ctx, code, aLVal, errs);
                code.add(new Jump(labelExit));
                }
            }

        if (labelDefault != this.m_labelDefault)
            {
            // default is an assertion
            code.add(labelDefault);
            code.add(new Assert(pool().valFalse()));
            }

        code.add(labelExit);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The switch statement or expression.
     */
    private AstNode m_nodeSwitch;

    /**
     * If the switch condition requires a scope.
     */
    private boolean m_fScope;

    /**
     * The actual number of condition values.
     */
    private int m_cCondVals = -1;

    /**
     * The type of each condition expression / tuple field of case statements.
     */
    private TypeConstant[] m_atypeCond;

    /**
     * If the condition evaluates to a constant, then this holds that value.
     */
    private Constant[] m_aconstCond;

    /**
     * The type of the condition.
     */
    private TypeConstant m_typeCase;

    /**
     * The label of the currently active case group.
     */
    private Label m_labelCurrent;

    /**
     * The label to jump to for the "default:" branch.
     */
    private Label m_labelDefault;

    /**
     * The label that the constant value of the condition matches to; otherwise, null.
     */
    private Label m_labelConstant;

    /**
     * A list of case values.
     */
    private ListSet<Constant> m_listsetCase = new ListSet<>();

    /**
     * Set to false when any non-constant case values are encountered.
     */
    private boolean m_fAllConsts = true;

    /**
     * A list of labels corresponding to the list of case values.
     */
    private List<Label> m_listLabel = new ArrayList<>();

    /**
     * The (lazily created) list of all case values that have wildcards or intervals.
     */
    private List<Constant> m_listWild;

    private transient PackedInteger m_pintOffset;
    private transient PackedInteger m_pintMin;
    private transient PackedInteger m_pintMax;
    }
