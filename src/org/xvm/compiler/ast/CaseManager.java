package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.ValueConstant;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;

import org.xvm.util.ListMap;
import org.xvm.util.ListSet;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


/**
 * The CaseManager is shared compilation logic used by both the "switch" statement and  expression.
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
     * @return the constant pool to use
     */
    public ConstantPool pool()
        {
        return getSwitch().pool();
        }

    /**
     * @return the node that created this manager
     */
    public AstNode getSwitch()
        {
        return m_nodeSwitch;
        }

    /**
     * @return true if the condition value require a scope to be created for the switch
     */
    public boolean hasDeclarations()
        {
        return m_ctxSwitch != null;
        }

    /**
     * @return a nesting context created automatically if the switch condition declares any
     *         variables
     */
    public Context getSwitchContext()
        {
        return m_ctxSwitch;
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
        return m_mapWild != null;
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
     * @return true iff the JMP_INT op is going to be used
     */
    public boolean usesJmpInt()
        {
        return !usesIfLadder() && getCaseLabels() != null && getCaseConstants() == null;
        }

    /**
     * @return the int offset for the JMP_INT op
     */
    public PackedInteger getJmpIntOffset()
        {
        return m_pintMin;
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

    /**
     * @return the array of labels
     */
    public Label[] getCaseLabels()
        {
        return m_alabelCase;
        }

    /**
     * @return the array of constants associated with the labels, or null if either an if-ladder or
     *         JMP_INT is used
     */
    public Constant[] getCaseConstants()
        {
        return m_aconstCase;
        }

    /**
     * @return the label for the "default:" (or fully wild-card "case:") statement; may be null
     */
    public Label getDefaultLabel()
        {
        return m_labelDefault;
        }

    /**
     * @return the cookie for the specified label
     */
    public Object getCookie(Label label)
        {
        return m_mapLabels.get(label);
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
                    if (m_ctxSwitch == null && stmtCond.hasDeclarations())
                        {
                        m_ctxSwitch = ctx = ctx.enter();
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

        // the context must match
        if (m_ctxSwitch != null)
            {
            assert ctx == m_ctxSwitch;
            }

        // each contiguous group of "case:"/"default:" statements shares a single label
        if (m_labelCurrent == null)
            {
            m_labelCurrent = new Label("case_" + (m_mapLabels.size() + 1));
            }

        // each case statement is marked with the label that it will jump to
        stmtCase.setLabel(m_labelCurrent);
        m_caseCurrent = stmtCase;

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

        // validate each separate value in the case label
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
                        if (collides(constCase, lIgnore, lRange))
                            {
                            // collision
                            stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DUPLICATE,
                                    constCase.getValueString());
                            fValid = false;
                            }
                        else
                            {
                            m_listsetCase.add(constCase);
                            m_listLabels.add(m_labelCurrent);
                            m_mapLabels.put(m_labelCurrent, null);

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
     *
     * @param cookie  whatever the caller wants to associate with the just-finished case group
     */
    public void endCaseGroup(Object cookie)
        {
        if (m_labelCurrent == null)
            {
            throw new IllegalStateException();
            }

        if (cookie != null)
            {
            m_mapLabels.put(m_labelCurrent, cookie);
            }

        m_labelCurrent = null;
        m_caseCurrent  = null;
        }

    /**
     * Finish the switch validation.
     *
     * @param ctx     the validation context
     * @param errs    the error list to log to
     *
     * @return true iff the validation succeeded
     */
    public boolean validateEnd(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // the context must match
        if (m_ctxSwitch != null)
            {
            assert ctx == m_ctxSwitch;
            }

        if (m_labelCurrent != null)
            {
            m_caseCurrent.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DANGLING);
            fValid = false;
            }
        else if (m_aconstCond != null && m_labelConstant == null && m_fAllConsts && m_labelDefault != null)
            {
            m_labelConstant = m_labelDefault;
            }
        else if (m_labelDefault == null && (m_nodeSwitch instanceof Expression)
                && (!isCardinal() || m_listsetCase.size() < m_typeCase.getIntCardinality()))
            {
            // this means that the switch expression would "short circuit" (not result in a value),
            // which is not allowed
            m_nodeSwitch.log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_REQUIRED);
            fValid = false;
            }

        if (m_ctxSwitch != null)
            {
            m_ctxSwitch.exit();
            }

        // store the resulting information
        if (!usesIfLadder())
            {
            // check if the JumpInt optimization can be used
            // (enums are no longer supported, since cross-module dependencies without
            // re-compilation would cause the values to fail to "line up", so it's better to
            // use the JumpVal option instead of the JumpInt option)
            int     cVals       = m_listsetCase.size();
            int     cRange      = 0;
            boolean fUseJumpInt = false;
            if (fValid && m_pintMin != null && !usesJmpValN())
                {
                PackedInteger pintRange = m_pintMax.sub(m_pintMin);
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

            Constant[] aConstCase = m_listsetCase.toArray(Constant.NO_CONSTS);
            Label[]    aLabelCase = m_listLabels.toArray(new Label[0]);
            if (fUseJumpInt)
                {
                Label[] aLabels = new Label[cRange];
                for (int i = 0; i < cVals; ++i)
                    {
                    Label    label     = aLabelCase[i];
                    Constant constCase = aConstCase[i];
                    int      nIndex    = constCase.getIntValue().sub(m_pintMin).getInt();

                    aLabels[nIndex] = label;
                    }

                // fill in any gaps
                Label labelMiss = m_labelDefault;
                if (labelMiss == null && m_nodeSwitch instanceof Statement)
                    {
                    // statement "defaults" to skipping out of the statement altogether
                    labelMiss = ((Statement) m_nodeSwitch).getEndLabel();
                    }
                for (int i = 0; i < cRange; ++i)
                    {
                    if (aLabels[i] == null)
                        {
                        assert labelMiss != null || !fValid;
                        aLabels[i] = labelMiss;
                        }
                    }

                m_alabelCase = aLabels;
                }
            else
                {
                m_alabelCase = aLabelCase;
                m_aconstCase = aConstCase;
                }
            }

        return fValid;
        }

    /**
     * Check if the specified case constant is covered by any previously encountered case constants.
     *
     * @param constCase  the case constant to test
     * @param lIgnore    the bitmask indicating which fields are match-any
     * @param lRange     the bitmask indicating which fields are intervals
     *
     * @return true if the passed case constant collides with any previous encountered case
     *         constants
     */
    private boolean collides(Constant constCase, long lIgnore, long lRange)
        {
        if (m_listsetCase.contains(constCase))
            {
            return true;
            }

        if (m_mapWild != null)
            {
            for (Entry<Constant, Long> entry : m_mapWild.entrySet())
                {
                Constant constWild   = entry.getKey();
                long     lWildRanges = entry.getValue();
                if (covers(constWild, lWildRanges, constCase, lRange))
                    {
                    return true;
                    }
                }
            }

        if ((lIgnore | lRange) != 0L)
            {
            if (m_mapWild == null)
                {
                m_mapWild = new ListMap<>();
                }
            m_mapWild.put(constCase, lRange);
            }

        return false;
        }

    /**
     * Determine if "this constant" covers "that constant", applying rules including wild-cards and
     * ranges.
     *
     * @param constThis  a case constant
     * @param constThat  another case constant
     *
     * @return true iff constThis provably covers constThat
     */
    private boolean covers(Constant constThis, long lRangeThis, Constant constThat, long lRangeThat)
        {
        if (constThis.equals(constThat))
            {
            return true;
            }

        int cConds = getConditionCount();
        if (cConds == 1)
            {
            return covers(constThis, (lRangeThis & 1) != 0,
                          constThat, (lRangeThat & 1) != 0);
            }
        else
            {
            assert cConds > 1;
            if (constThis.getFormat() == Format.Tuple &&
                constThat.getFormat() == Format.Tuple)
                {
                Constant[] aconstThis = ((ArrayConstant) constThis).getValue();
                Constant[] aconstThat = ((ArrayConstant) constThat).getValue();
                for (int i = 0; i < cConds; ++i)
                    {
                    if (!covers(aconstThis[i], (lRangeThis & (1L << i)) != 0,
                                aconstThat[i], (lRangeThat & (1L << i)) != 0))
                        {
                        return false;
                        }
                    }
                return true;
                }
            }

        return false;
        }

    private boolean covers(Constant constThis, boolean fRangeThis, Constant constThat, boolean fRangeThat)
        {
        if (constThis.equals(constThat) || constThis instanceof MatchAnyConstant)
            {
            return true;
            }

        if (constThat instanceof MatchAnyConstant
                || !fRangeThis && !fRangeThat
                || !(constThis instanceof ValueConstant)
                || !(constThat instanceof ValueConstant))
            {
            // technically, some types do have a known range, so it is possible to define a range
            // that is equivalent to a wild-card; that implementation can be deferred until the
            // compiler is re-built from the ground up in Ecstasy; similarly, no handling is
            // provided for non-value constants (since they don't have a comparable value)
            return false;
            }

        Object oThisLo, oThisHi;
        if (fRangeThis)
            {
            if (constThis instanceof IntervalConstant)
                {
                IntervalConstant interval = (IntervalConstant) constThis;
                oThisLo = interval.getFirst();
                oThisHi = interval.getLast();
                if (oThisLo instanceof ValueConstant && oThisHi instanceof ValueConstant)
                    {
                    oThisLo = ((ValueConstant) oThisLo).getValue();
                    oThisHi = ((ValueConstant) oThisHi).getValue();
                    }
                else
                    {
                    return false;
                    }
                }
            else
                {
                return false;
                }
            }
        else
            {
            oThisLo = oThisHi = ((ValueConstant) constThis).getValue();
            }
        if (!(  oThisLo instanceof Comparable &&
                oThisHi instanceof Comparable))
            {
            return false;
            }

        Object oThatLo, oThatHi;
        if (fRangeThat)
            {
            if (constThat instanceof IntervalConstant)
                {
                IntervalConstant interval = (IntervalConstant) constThat;
                oThatLo = interval.getFirst();
                oThatHi = interval.getLast();
                if (oThatLo instanceof ValueConstant && oThatHi instanceof ValueConstant)
                    {
                    oThatLo = ((ValueConstant) oThatLo).getValue();
                    oThatHi = ((ValueConstant) oThatHi).getValue();
                    }
                else
                    {
                    return false;
                    }
                }
            else
                {
                return false;
                }
            }
        else
            {
            oThatLo = oThatHi = ((ValueConstant) constThat).getValue();
            }
        if (!(  oThatLo instanceof Comparable &&
                oThatHi instanceof Comparable))
            {
            return false;
            }

        // TODO this will work for types that have a corresponding Java type, like Int, but not enums etc.
        Comparable cmpThisLo = (Comparable) oThisLo;
        Comparable cmpThisHi = (Comparable) oThisHi;
        Comparable cmpThatLo = (Comparable) oThatLo;
        Comparable cmpThatHi = (Comparable) oThatHi;
        try
            {
            if (cmpThisLo.compareTo(cmpThisHi) > 0)
                {
                Comparable cmpOops = cmpThisLo;
                cmpThisLo = cmpThisHi;
                cmpThisHi = cmpOops;
                }
            if (cmpThatLo.compareTo(cmpThatHi) > 0)
                {
                Comparable cmpOops = cmpThatLo;
                cmpThatLo = cmpThatHi;
                cmpThatHi = cmpOops;
                }

            return  cmpThisLo.compareTo(cmpThatLo) <= 0 &&
                    cmpThisLo.compareTo(cmpThatHi) <= 0 &&
                    cmpThisHi.compareTo(cmpThatLo) >= 0 &&
                    cmpThisHi.compareTo(cmpThatHi) >= 0;
            }
        catch (Exception e)
            {
            return false;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The switch statement or expression.
     */
    private AstNode m_nodeSwitch;

    /**
     * If the switch condition requires a scope, this creates a context.
     */
    private Context m_ctxSwitch;

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
    private CaseStatement m_caseCurrent;

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
     * A list of case labels.
     */
    private List<Label> m_listLabels = new ArrayList<>();

    /**
     * Set to false when any non-constant case values are encountered.
     */
    private boolean m_fAllConsts = true;

    /**
     * A list-map of labels corresponding to the list of case values; associated value is a cookie.
     */
    private ListMap<Label, Object> m_mapLabels = new ListMap<>();

    /**
     * The (lazily created) list of all case values that have wildcards or intervals.
     */
    private ListMap<Constant, Long> m_mapWild;

    /**
     * After the validateEnd(), this holds all of the constant values for the cases, if JMP_VAL is
     * used.
     */
    private Constant[] m_aconstCase;

    /**
     * After the validateEnd(), this holds all of the labels for the cases, unless an if-ladder is
     * used.
     */
    private Label[] m_alabelCase;

    /**
     * Used to calculate the range of cardinal case values to determine if JMP_INT can be used.
     */
    private PackedInteger m_pintMin;

    /**
     * Used to calculate the range of cardinal case values to determine if JMP_INT can be used.
     */
    private PackedInteger m_pintMax;
    }
