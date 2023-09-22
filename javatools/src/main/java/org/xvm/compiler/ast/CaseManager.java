package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.MultiExprAST;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ValueConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpInt;
import org.xvm.asm.op.JumpIsA;
import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.JumpVal_N;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

import org.xvm.util.BitCube;
import org.xvm.util.ListMap;
import org.xvm.util.ListSet;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


/**
 * The CaseManager is shared compilation logic used by both the "switch" statement and  expression.
 */
public class CaseManager<CookieType>
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
        return m_fHasDecl;
        }

    /**
     * @return true if the switch contains any Type based conditions
     */
    public boolean hasTypeConditions()
        {
        return m_lTypeExpr != 0;
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
     * @return true iff the switch has exactly one expression value to switch on, and the comparison
     *         is for equality
     */
    public boolean isSingleEquals()
        {
        return m_cCondVals == 1 && m_afIsSwitch == 0;
        }

    /**
     * @return the number of case groups encountered thus far
     */
    public int getCaseGroupCount()
        {
        return m_mapLabels.size();
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
     * @return true iff the switch can fall through without going through a case group because there
     *         is no default and not all of the possible cases are covered
     */
    public boolean isCompletable()
        {
        return m_fCompletes;
        }

    /**
     * @return true iff any of the case expressions use a range value match
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
     * @return true iff the JMP_INT op is going to be used
     */
    public boolean usesJmpInt()
        {
        return m_fUseJumpInt;
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
     * @return the array of constants associated with the labels
     */
    public Constant[] getCaseConstants()
        {
        return m_aconstCase;
        }

    /**
     * @return the number of case constants (including default)
     */
    public int getCaseCount()
        {
        return m_aconstCase.length + (hasDefaultCase() ? 1 : 0);
        }

    /**
     * @return the label associated with the "default:" statement, or null
     */
    public Label getDefaultLabel()
        {
        return m_labelDefault;
        }

    /**
     * @return True iff there is an explicit default label
     */
    public boolean hasDefaultCase()
        {
        return !(m_labelDefault == null || m_nodeSwitch instanceof SwitchStatement stmt
                                        && stmt.getEndLabel() == m_labelDefault);
        }

    /**
     * @return the cookie for the specified label
     */
    public CookieType getCookie(Label label)
        {
        return m_mapLabels.get(label);
        }

    /**
     * @return the binary AST for the condition portion of the switch
     */
    public ExprAST getConditionBAST()
        {
        return m_bastCond;
        }

    /**
     * @return a bitmask indicating which conditions are "is a" tests
     */
    public long getConditionIsA()
        {
        return m_afIsSwitch;
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Compute the arity of the switch (the number of values the condition is expected to produce).
     *
     * @param listNodes  all the nodes of the switch block
     * @param errs       the error list to log to
     *
     * @return a positive arity or zero if an error has been reported
     */
    protected int computeArity(List<? extends AstNode> listNodes, ErrorListener errs)
        {
        int nArity = 0;
        for (AstNode node : listNodes)
            {
            if (node instanceof CaseStatement stmtCase)
                {
                if (stmtCase.isDefault())
                    {
                    continue;
                    }

                for (Expression expr : stmtCase.getExpressions())
                    {
                    int n = expr instanceof TupleExpression exprTuple
                            ? exprTuple.getExpressions().size()
                            : 1;
                    if (nArity == 0)
                        {
                        nArity = n;
                        }
                    else if (nArity != n)
                        {
                        expr.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_ILLEGAL_ARITY);
                        return 0;
                        }
                    }
                }
            }

        // if there was not a single non-default CaseStatement, assume the arity of one
        return nArity == 0 ? 1 : nArity;
        }

    /**
     * Validate the condition value expressions.
     *
     * @param ctx       the validation context
     * @param listCond  the list of condition value expressions (mutable)
     * @param nArity    the number of values the condition is expected to produce
     * @param errs      the error list to log to
     *
     * @return true iff the validation succeeded
     */
    protected boolean validateCondition(Context ctx, List<AstNode> listCond, int nArity,
                                        ErrorListener errs)
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
            m_afIsSwitch = 0;
            m_aconstCond = new Constant[] {pool.valTrue()};
            }
        else
            {
            List<TypeConstant> listTypes  = new ArrayList<>();
            List<Constant>     listConsts = new ArrayList<>();
            long               afIsSwitch = 0L;
            boolean            fAllConst  = true;
            for (int i = 0, c = listCond.size(); i < c; ++i)
                {
                AstNode        node      = listCond.get(i);
                TypeConstant[] atype     = null;
                boolean        fIsSwitch = false;
                Constant[]     aconst    = null;
                AstNode        nodeNew;
                if (node instanceof AssignmentStatement stmtCond)
                    {
                    // assignment represents a side-effect, so disable the constant optimizations
                    fAllConst   = false;
                    m_fHasDecl |= stmtCond.hasDeclarations();

                    nodeNew = stmtCond.validate(ctx, errs);
                    if (nodeNew != null)
                        {
                        AstNode nodeLVal = ((AssignmentStatement) nodeNew).getLValue();

                        atype = nodeLVal instanceof VariableDeclarationStatement stmtVarDecl
                                    ? new TypeConstant[]{stmtVarDecl.getType()}
                              : nodeLVal instanceof MultipleLValueStatement stmtMulti
                                    ? stmtMulti.getTypes()
                                    : nodeLVal.getLValueExpression().getTypes();
                        }
                    }
                else if (node instanceof IsExpression exprIs
                        && exprIs.getExpression2() instanceof KeywordTypeExpression exprType
                        && exprType.getKeyword().getId() == Token.Id.ANY)
                    {
                    // replace "x.is(_)" with "x", and this node's test becomes an "is switch"
                    nodeNew = exprIs.getExpression1().validate(ctx, null, errs);
                    if (nodeNew != null)
                        {
                        nodeNew.setParent(exprIs.getParent());
                        }
                    atype     = new TypeConstant[] {pool.typeType()};
                    fIsSwitch = true;
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
                    // every condition could produce multiple results; ignore ones that exceeded
                    // the switch arity
                    for (int j = 0, iType = i; j < atype.length && iType < nArity; j++, iType++)
                        {
                        TypeConstant type = atype[j];

                        // allow switching on formal types as soon as constraints are satisfied
                        if (type.isTypeOfType() && type.containsFormalType(true))
                            {
                            type = type.resolveConstraints();
                            }

                        listTypes.add(type);
                        if (fIsSwitch)
                            {
                            if (iType >= 63)
                                {
                                node.log(errs, Severity.ERROR, Compiler.SWITCH_OVERFLOW);
                                fValid = false;
                                }
                            afIsSwitch |= 1L << iType;
                            }
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

            m_cCondVals  = listTypes.size();
            m_atypeCond  = listTypes.toArray(TypeConstant.NO_TYPES);
            m_afIsSwitch = afIsSwitch;
            if (fValid)
                {
                if (fAllConst)
                    {
                    m_aconstCond = listConsts.toArray(Constant.NO_CONSTS);
                    }
                else
                    {
                    // check if any of the conditions are Type based, in which case we can do the
                    // type inference during validation
                    for (int i = 0, c = m_cCondVals; i < c; ++i)
                        {
                        if (m_atypeCond[i].isTypeOfType())
                            {
                            m_lTypeExpr |= 1L << i;
                            }
                        }
                    }
                }
            }

        m_listCond = listCond;
        if (m_atypeCond.length == 1)
            {
            m_typeCase = m_atypeCond[0].freeze();
            }
        else
            {
            m_typeCase = pool.ensureImmutableTypeConstant(pool.ensureTupleType(m_atypeCond));
            }

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
            m_labelCurrent = new Label("case_" + (m_mapLabels.size() + 1));
            m_mapLabels.put(m_labelCurrent, null);
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

        // allow case values to infer based on the expected type from the switch condition
        ctx = ctx.enterInferring(m_typeCase);

        // validate each separate value in the case label
        ConstantPool pool       = pool();
        boolean      fIfSwitch  = usesIfLadder();
        boolean      fIntConsts = isCardinal() && m_typeCase.getExplicitClassFormat() != Component.Format.ENUM;
        int          cPrevCases = m_listsetCase.size();
        for (int iExpr = 0, cExprs = listExprs.size(); iExpr < cExprs; ++iExpr)
            {
            Expression     exprCase  = listExprs.get(iExpr);
            TypeConstant   typeMatch = m_typeCase;
            long           lIgnore   = 0L;
            long           lRange    = 0L;
            int            cFields   = 1;
            TypeConstant[] atypeAlt  = null;
            if (exprCase instanceof TupleExpression exprTuple)
                {
                List<Expression> listFields = exprTuple.exprs;
                cFields = listFields.size();
                for (int i = 0; i < cFields; ++i)
                    {
                    Expression exprField = listFields.get(i);
                    if (exprField instanceof IgnoredNameExpression)
                        {
                        lIgnore |= 1L << i;
                        }
                    else if (!exprField.testFit(ctx, m_atypeCond[i], false, null).isFit())
                        {
                        TypeConstant typeRange = pool.ensureRangeType(m_atypeCond[i]);
                        if (exprField.testFit(ctx, typeRange, false, null).isFit())
                            {
                            lRange |= 1L << i;

                            if (atypeAlt == null)
                                {
                                atypeAlt = m_atypeCond.clone();
                                }
                            atypeAlt[i] = typeRange;
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
                else if (!exprCase.testFit(ctx, m_typeCase, false, null).isFit())
                    {
                    TypeConstant typeRange = pool.ensureRangeType(m_typeCase);
                    if (exprCase.testFit(ctx, typeRange, false, null).isFit())
                        {
                        lRange    = 1;
                        typeMatch = typeRange;
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

                // this wildcard case becomes the "default:", need to validate it before continuing
                Expression exprNew = exprCase.validate(ctx, typeMatch, errs);
                assert exprNew == exprCase;
                continue;
                }

            // if the validation type changes for this particular case expression
            // (because of a range match), then create the type that will apply to
            // this particular case expression
            if (atypeAlt != null)
                {
                typeMatch = pool.ensureImmutableTypeConstant(pool.ensureTupleType(atypeAlt));
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
                    if (m_fAllConsts && m_aconstCond != null && m_labelConstant == null)
                        {
                        Constant constCond = m_aconstCond.length == 1
                                ? m_aconstCond[0]
                                : pool.ensureArrayConstant(m_typeCase, m_aconstCond);
                        if (covers(constCase, lRange, constCond, 0))
                            {
                            m_labelConstant = m_labelCurrent;
                            }
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

                            if (fIntConsts)
                                {
                                if (constCase instanceof MatchAnyConstant)
                                    {
                                    // treated as "default:", so ignore
                                    }
                                else if (constCase instanceof RangeConstant constRange)
                                    {
                                    incorporateInt(constRange.getFirst().getIntValue());
                                    incorporateInt(constRange.getLast() .getIntValue());
                                    }
                                else
                                    {
                                    incorporateInt(constCase.getIntValue());
                                    }
                                }

                            // validate "is(_)" type constants
                            if (getConditionCount() == 1)
                                {
                                if ((m_afIsSwitch & 1L) != 0 &&
                                        m_listCond.get(0) instanceof Expression exprTest)
                                    {
                                    TypeConstant typeCase = (TypeConstant) constCase;
                                    assert typeCase.isTypeOfType();

                                    typeCase = typeCase.getParamType(0);

                                    TypeConstant typeTest = exprTest.getImplicitType(ctx);
                                    if (typeTest != null && typeTest.isTypeOfType() &&
                                            !typeCase.isTypeOfType())
                                        {
                                        exprNew.log(errs, Severity.ERROR, Compiler.NOT_TYPE_OF_TYPE,
                                            exprTest.toString(), typeCase.getValueString());
                                        fValid = false;
                                        }
                                    else
                                        {
                                        // check for reachability, since is(_) switch is basically
                                        // a ladder of consecutive "isA()" tests
                                        Iterator<Constant> iter = m_listsetCase.iterator();
                                        for (int i = 0; i < cPrevCases; i++)
                                            {
                                            TypeConstant typePrev = (TypeConstant) iter.next();
                                            assert typePrev.isTypeOfType();
                                            typePrev = typePrev.getParamType(0);
                                            if (typeCase.isA(typePrev))
                                                {
                                                stmtCase.log(errs, Severity.ERROR, Compiler.NOT_REACHABLE);
                                                fValid = false;
                                                break;
                                                }
                                            }
                                        }
                                    }
                                }
                            else if (m_afIsSwitch != 0 && constCase instanceof ArrayConstant constArray)
                                {
                                Constant[] aConstCase = constArray.getValue();
                                for (int i = 0, c = 64 - Long.numberOfLeadingZeros(m_afIsSwitch); i < c; i++)
                                    {
                                    if ((m_afIsSwitch & (1L << i)) != 0 &&
                                            m_listCond.get(i) instanceof Expression exprTest)
                                        {
                                        TypeConstant typeCase = (TypeConstant) aConstCase[i];
                                        assert typeCase.isTypeOfType();

                                        typeCase = typeCase.getParamType(0);

                                        TypeConstant typeTest = exprTest.getImplicitType(ctx);
                                        if (typeTest != null && typeTest.isTypeOfType() &&
                                                !typeCase.isTypeOfType())
                                            {
                                            exprNew.log(errs, Severity.ERROR, Compiler.NOT_TYPE_OF_TYPE,
                                                exprTest.toString(), typeCase.getValueString());
                                            fValid = false;
                                            }
                                        }
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

        // exit the inferring context
        ctx.exit();

        return fValid;
        }

    /**
     * Add any existing type inference information to the specified context.
     *
     * @param ctx       the context to use
     * @param stmtCase  the corresponding case statement (used for error reporting only)
     * @param errs      the error list to log to
     *
     * @return true iff the switch contains any type inferring conditions
     */
    protected boolean addTypeInference(Context ctx, CaseStatement stmtCase, ErrorListener errs)
        {
        if (m_lTypeExpr == 0L)
            {
            return false;
            }

        ConstantPool pool      = pool();
        Constant     constCase = m_listsetCase.last();
        if (getConditionCount() == 1)
            {
            if ((m_lTypeExpr & 1L) != 0L &&
                    m_listCond.get(0) instanceof NameExpression exprTest)
                {
                TypeConstant typeCase = (TypeConstant) constCase;
                assert typeCase.isTypeOfType();

                if ((m_afIsSwitch & 1L) != 0L)
                    {
                    typeCase = typeCase.getParamType(0);

                    TypeConstant typeTest = exprTest.getImplicitType(ctx);
                    if (typeTest != null)
                        {
                        typeCase = IsExpression.computeInferredType(pool, typeTest, typeCase);
                        }
                    }
                exprTest.narrowType(ctx, Branch.Always, typeCase);
                }
            }
        else if (constCase instanceof ArrayConstant constArray)
            {
            Constant[] aConstCase = constArray.getValue();
            for (int i = 0, c = 64 - Long.numberOfLeadingZeros(m_lTypeExpr); i < c; i++)
                {
                long lPos = 1L << i;
                if ((m_lTypeExpr & lPos) != 0L &&
                        m_listCond.get(i) instanceof NameExpression exprTest)
                    {
                    TypeConstant typeCase = (TypeConstant) aConstCase[i];
                    assert typeCase.isTypeOfType();

                    if ((m_afIsSwitch & lPos) != 0L)
                        {
                        typeCase = typeCase.getParamType(0);

                        TypeConstant typeTest = exprTest.getImplicitType(ctx);
                        if (typeTest != null)
                            {
                            typeCase = IsExpression.computeInferredType(pool, typeTest, typeCase);
                            }
                        }
                    exprTest.narrowType(ctx, Branch.Always, typeCase);
                    }
                }
            }
        return true;
        }

    private void incorporateInt(PackedInteger pint)
        {
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

    /**
     * Mark the end of a series of contiguous "case:" / "default:" statements
     *
     * @param cookie  whatever the caller wants to associate with the just-finished case group
     */
    public void endCaseGroup(CookieType cookie)
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

        if (m_labelCurrent != null)
            {
            m_caseCurrent.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DANGLING);
            fValid = false;
            }
        else if (m_aconstCond != null && m_labelConstant == null && m_fAllConsts && m_labelDefault != null)
            {
            m_labelConstant = m_labelDefault;
            }
        else if (m_labelDefault == null && !areAllCasesCovered())
            {
            m_fCompletes = true;
            if (m_nodeSwitch instanceof Statement stmtSwitch)
                {
                m_labelDefault = stmtSwitch.getEndLabel();
                }
            else
                {
                // this means that the switch expression would "short circuit" (not result in a value),
                // which is not allowed
                m_nodeSwitch.log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_REQUIRED);
                fValid = false;
                }
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
            if (fValid && m_pintMin != null && getConditionCount() == 1)
                {
                PackedInteger pintRange = m_pintMax.sub(m_pintMin);
                if (!pintRange.isBig())
                    {
                    // the idea is that if we have ints from 100,000 to 100,100, and there are
                    // 25+ of them, then we should go ahead and use the jump table optimization
                    if (m_listsetCase.size() >= (pintRange.getLong() >>> 2))
                        {
                        cRange      = pintRange.getInt() + 1;
                        m_fUseJumpInt = true;
                        }
                    }
                }

            Constant[] aConstCase = m_listsetCase.toArray(Constant.NO_CONSTS);
            Label[]    aLabelCase = m_listLabels.toArray(new Label[0]);
            if (m_fUseJumpInt)
                {
                Label[] aLabels = new Label[cRange];
                for (int i = 0; i < cVals; ++i)
                    {
                    Label    label     = aLabelCase[i];
                    Constant constCase = aConstCase[i];
                    if (constCase instanceof RangeConstant constRange)
                        {
                        int     iFirst   = constRange.getFirst().getIntValue().sub(m_pintMin).getInt();
                        int     iLast    = constRange.getLast().getIntValue().sub(m_pintMin).getInt();
                        boolean fFirstEx = constRange.isFirstExcluded();
                        boolean fLastEx  = constRange.isLastExcluded();
                        if (iFirst > iLast)
                            {
                            int iTemp = iFirst;
                            iFirst = fLastEx  ? iLast+1 : iLast;
                            iLast  = fFirstEx ? iTemp-1 : iTemp;
                            }
                        else
                            {
                            if (fFirstEx)
                                {
                                iFirst++;
                                }
                            if (fLastEx)
                                {
                                iLast--;
                                }
                            }

                        for (int iVal = iFirst; iVal <= iLast; ++iVal)
                            {
                            if (aLabels[iVal] == null)
                                {
                                aLabels[iVal] = label;
                                }
                            }
                        }
                    else
                        {
                        aLabels[constCase.getIntValue().sub(m_pintMin).getInt()] = label;
                        }
                    }

                // fill in any gaps
                Label labelMiss = m_labelDefault;
                if (labelMiss == null && m_nodeSwitch instanceof Statement stmtSwitch)
                    {
                    // statement "defaults" to skipping out of the statement altogether
                    labelMiss = stmtSwitch.getEndLabel();
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
                }
            m_aconstCase = aConstCase;
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

        if (usesNonExactMatching())
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

                // Important note: we intentionally don't check already existing values for being
                // "covered" by the new range, allowing exact matches to be processed first and
                // ranges with potential unreachable values later without generating compilation
                // errors; for example:
                //  switch (i)
                //    {
                //    case 7:     {...}
                //    case 1..10: {...} // any value between 1 and 10 except 7
                //    }
                //
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

    private static boolean covers(Constant constThis, boolean fRangeThis,
                                  Constant constThat, boolean fRangeThat)
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

        Object  oThisLo, oThisHi;
        boolean fThisLoEx, fThisHiEx;
        if (fRangeThis)
            {
            if (constThis instanceof RangeConstant constRange)
                {
                oThisLo = constRange.getFirst();
                oThisHi = constRange.getLast();
                if (oThisLo instanceof ValueConstant valLo &&
                    oThisHi instanceof ValueConstant valHi)
                    {
                    oThisLo   = valLo.getValue();
                    oThisHi   = valHi.getValue();
                    fThisLoEx = constRange.isFirstExcluded();
                    fThisHiEx = constRange.isLastExcluded();
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
            oThisLo   = oThisHi   = ((ValueConstant) constThis).getValue();
            fThisLoEx = fThisHiEx = false;
            }

        if (!(oThisLo instanceof Comparable cmpThisLo &&
              oThisHi instanceof Comparable cmpThisHi))
            {
            return false;
            }

        Object  oThatLo, oThatHi;
        boolean fThatLoEx, fThatHiEx;
        if (fRangeThat)
            {
            if (constThat instanceof RangeConstant constRange)
                {
                oThatLo = constRange.getFirst();
                oThatHi = constRange.getLast();
                if (oThatLo instanceof ValueConstant valueLo &&
                    oThatHi instanceof ValueConstant valueHi)
                    {
                    oThatLo   = valueLo.getValue();
                    oThatHi   = valueHi.getValue();
                    fThatLoEx = constRange.isFirstExcluded();
                    fThatHiEx = constRange.isLastExcluded();
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
            oThatLo   = oThatHi   = ((ValueConstant) constThat).getValue();
            fThatLoEx = fThatHiEx = false;
            }
        if (!(  oThatLo instanceof Comparable cmpThatLo &&
                oThatHi instanceof Comparable cmpThatHi))
            {
            return false;
            }

        // this automatically works for ValueConstant types that have a corresponding Java type,
        // like Int, String, etc., while enums use their ordinal values (see EnumValueConstant).
        try
            {
            if (cmpThisLo.compareTo(cmpThisHi) > 0)
                {
                Comparable cmpOops = cmpThisLo;
                cmpThisLo = cmpThisHi;
                cmpThisHi = cmpOops;

                boolean fOops = fThisHiEx;
                fThisHiEx = fThisLoEx;
                fThisLoEx = fOops;
                }
            if (cmpThatLo.compareTo(cmpThatHi) > 0)
                {
                Comparable cmpOops = cmpThatLo;
                cmpThatLo = cmpThatHi;
                cmpThatHi = cmpOops;

                boolean fOops = fThatHiEx;
                fThatHiEx = fThatLoEx;
                fThatLoEx = fOops;
                }

            // this range covers that range if "this lo" <= "that lo" and "this hi" >= "that hi"

            int nCmpLo = cmpThisLo.compareTo(cmpThatLo);
            int nCmpHi = cmpThisHi.compareTo(cmpThatHi);

            boolean fCoverLo = !fThisLoEx || fThatLoEx ? nCmpLo <= 0 : nCmpLo < 0;
            boolean fCoverHi = !fThisHiEx || fThatHiEx ? nCmpHi >= 0 : nCmpHi > 0;

            return  fCoverLo && fCoverHi;
            }
        catch (Exception e)
            {
            return false;
            }
        }

    /**
     * Check if all the possible values of the condition are covered. This method is called only
     * after all the cases are processed (validated) and the default label is not present.
     *
     * @return true iff all the possible values are covered and the "default" is not necessary
     */
    private boolean areAllCasesCovered()
        {
        if (usesIfLadder())
            {
            return false;
            }

        int cCases = m_listsetCase.size();
        int cConds = getConditionCount();
        if (cConds == 1)
            {
            TypeConstant typeCase = m_typeCase;
            if (!typeCase.isIntConvertible())
                {
                return false;
                }

            int cCardinality = typeCase.getIntCardinality();
            if (cCardinality == Integer.MAX_VALUE)
                {
                // the value domain doesn't support the full coverage
                return false;
                }

            int cCovered = 0;
            if (usesNonExactMatching())
                {
                // we know that the ranges don't intersect, but there is a possibility that some
                // former values intersect with later ranges; so we need to account for those
                for (Constant constCase : m_listsetCase)
                    {
                    if (constCase instanceof MatchAnyConstant)
                        {
                        return true;
                        }

                    if (constCase instanceof RangeConstant constRange)
                        {
                        cCovered += constRange.size();

                        // compensate for all previous intersections with this range
                        for (Constant constPrev : m_listsetCase)
                            {
                            if (constPrev == constCase)
                                {
                                break;
                                }

                            if (!(constPrev instanceof RangeConstant) &&
                                    covers(constCase, true, constPrev, false))
                                {
                                cCovered--;
                                }
                            }
                        }
                    else
                        {
                        cCovered++;
                        }
                    }
                }
            else
                {
                // without a wildcard or ranges (since we know that the simple cases don't
                // intersect) the answer is simple
                cCovered = cCases;
                }

            assert cCardinality >= cCovered;
            return cCardinality == cCovered;
            }

        // there are multiple columns; be reasonable on the number of possible combinations
        TypeConstant[] atype = m_typeCase.getParamTypesArray();
        assert atype.length == cConds;

        int[]      aiCardinals = new int[cConds];
        Constant[] aconstBase  = new Constant[cConds];
        int        cTotal      = 1;
        for (int i = 0; i < cConds; i++)
            {
            TypeConstant typeCase = atype[i];
            if (!typeCase.isIntConvertible())
                {
                return false;
                }

            int cCardinality = typeCase.getIntCardinality();
            if (cCardinality == Integer.MAX_VALUE)
                {
                return false;
                }

            Constant constBase = typeCase.getCardinalBase();
            if (constBase == null)
                {
                return false;
                }

            cTotal *= cCardinality;
            if (cTotal >= 32000)
                {
                // too many; get out
                return false;
                }
            aiCardinals[i] = cCardinality;
            aconstBase[i]  = constBase;
            }

        BitCube cube    = new BitCube(aiCardinals);
        int[]   anPoint = new int[cConds];

        Iterator<Constant> iter = m_listsetCase.iterator();
        for (int iCase = 0; iCase < cCases; iCase++)
            {
            Constant constTuple = iter.next();
            if (constTuple.getFormat() != Format.Tuple)
                {
                continue;
                }

            Constant[] aconstCase = ((ArrayConstant) constTuple).getValue();
            assert aconstCase.length == cConds;

            processCondition(cube, 0, anPoint, cConds, aconstCase, aconstBase);
            }
        return cube.isFull();
        }

    /**
     * Recursively process conditions starting at index "iCond".
     */
    private static void processCondition(BitCube cube, int iCond, int[] anPoint,
                                         int cCondVals, Constant[] aconstCase, Constant[] aconstBase)
        {
        if (iCond == cCondVals)
            {
            cube.set(anPoint);
            return;
            }

        Constant constCase = aconstCase[iCond];

        if (constCase instanceof MatchAnyConstant)
            {
            for (int i = 0, c = cube.getSize(iCond); i < c; i++)
                {
                anPoint[iCond] = i;
                processCondition(cube, iCond + 1, anPoint, cCondVals, aconstCase, aconstBase);
                }
            }
        else if (constCase instanceof RangeConstant constRange)
            {
            Constant constFirst = constRange.getFirst();
            Constant constLast  = constRange.getLast();
            boolean  fReverse   = constRange.isReverse();
            Constant constBase  = aconstBase[iCond];

            IntConstant constMin = (IntConstant)
                (fReverse ? constLast : constFirst).apply(Token.Id.SUB, constBase);
            IntConstant constMax = (IntConstant)
                (fReverse ? constFirst : constLast).apply(Token.Id.SUB, constBase);
            int nMin = constMin.getValue().getInt();
            int nMax = constMax.getValue().getInt();

            for (int i = nMin; i <= nMax; i++)
                {
                anPoint[iCond] = i;
                processCondition(cube, iCond + 1, anPoint, cCondVals, aconstCase, aconstBase);
                }
            }
        else
            {
            IntConstant constOrdinal = (IntConstant) constCase.apply(Token.Id.SUB, aconstBase[iCond]);
            anPoint[iCond] = constOrdinal.getValue().getInt();
            processCondition(cube, iCond + 1, anPoint, cCondVals, aconstCase, aconstBase);
            }
        }

    /**
     * Generate the arguments that result from the evaluation of the switch condition.
     *
     * @param ctx     the emitting context
     * @param code    the code to emit to
     * @param errs    the error list to log to
     *
     * @return an array of arguments
     */
    public Argument[] generateConditionArguments(Context ctx, Code code, ErrorListener errs)
        {
        assert m_listCond != null && !m_listCond.isEmpty();

        Argument[] aArgVal  = new Argument[getConditionCount()];
        int        ofArgVal = 0;
        ExprAST[]  abast    = new ExprAST[m_listCond.size()];
        int        ibast    = 0;
        for (AstNode node : m_listCond)
            {
            Expression exprCond;
            Expression exprBast = null;
            if (node instanceof AssignmentStatement stmt)
                {
                if (!stmt.completes(ctx, true, code, errs))
                    {
                    m_fCondAborts = true;
                    }
                exprCond = stmt.getLValue().getLValueExpression();
                // review GG - was there some issue where getLValueExpression() was destructive? should this get moved up one line?
                abast[ibast++] = (ExprAST) ctx.getHolder().getAst(stmt);
                }
            else
                {
                exprCond = (Expression) node;
                exprBast = exprCond;
                }

            if (usesJmpInt() && (!getJmpIntOffset().equals(PackedInteger.ZERO)
                    || !exprCond.getType().isA(pool().typeInt64())))
                {
                // either the offset is non-zero of the type is non-int; either way, convert it to a
                // zero-based Int
                exprCond = new ToIntExpression(exprCond, getJmpIntOffset(), errs);
                }

            // don't use stack
            Argument[] aArgsAdd = exprCond.generateArguments(ctx, code, true, false, errs);

            // ignore unused return values
            int cArgsAdd = Math.min(aArgsAdd.length, aArgVal.length - ofArgVal);
            System.arraycopy(aArgsAdd, 0, aArgVal, ofArgVal, cArgsAdd);
            ofArgVal += cArgsAdd;

            if (exprBast != null)
                {
                abast[ibast++] = exprBast.getExprAST();
                }
            }
        assert ofArgVal == aArgVal.length;

        assert ibast == abast.length && Arrays.stream(abast).allMatch(Objects::nonNull);
        m_bastCond = abast.length == 1 ? abast[0] : new MultiExprAST(abast);

        return aArgVal;
        }

    /**
     * After generating the condition arguments, determine if the condition is aborting.
     *
     * @return true iff the condition is known to always abort
     */
    public boolean isConditionAborting()
        {
        return m_fCondAborts;
        }

    /**
     * Generate the code for "jump table".
     *
     * @param ctx     the emitting context
     * @param code    the code to emit to
     * @param errs    the error list to log to
     */
    public void generateJumpTable(Context ctx, Code code, ErrorListener errs)
        {
        boolean fDefaultAssert = false;
        Label   labelDefault   = m_labelDefault;
        if (labelDefault == null)
            {
            labelDefault   = new Label("default_assert");
            fDefaultAssert = true;
            }

        Argument[] aArgVal    = generateConditionArguments(ctx, code, errs);
        Label[]    alabelCase = m_alabelCase;
        if (usesJmpInt())
            {
            assert getConditionCount() == 1 && aArgVal.length == 1;
            code.add(new JumpInt(aArgVal[0], alabelCase, labelDefault));
            }
        else if (isSingleEquals())
            {
            code.add(new JumpVal(aArgVal[0], m_aconstCase, alabelCase, labelDefault));
            }
        else if (getConditionCount() == 1)
            {
            assert m_afIsSwitch == 1;
            code.add(new JumpIsA(aArgVal[0], m_aconstCase, alabelCase, labelDefault));
            }
        else
            {
            code.add(new JumpVal_N(aArgVal, m_afIsSwitch, m_aconstCase, alabelCase, labelDefault));
            }

        if (fDefaultAssert)
            {
            // default is an assertion
            code.add(labelDefault);
            code.add(new Assert(pool().valFalse(),
                    AssertStatement.findExceptionConstructor(pool(), "IllegalArgument", errs)));
            }
        }

    /**
     * Generate the code for "jump table emulation" via an if-ladder.
     *
     * @param ctx     the emitting context
     * @param code    the code to emit to
     * @param aNodes  the statements/expressions inside the switch statement / expression (only the
     *                CaseStatement instances are used by this method)
     * @param errs    the error list to log to
     */
    public void generateIfLadder(Context ctx, Code code, List<? extends AstNode> aNodes, ErrorListener errs)
        {
        int cNodes = aNodes.size();
        for (int i = 0; i < cNodes; ++i)
            {
            AstNode node = aNodes.get(i);
            if (node instanceof CaseStatement stmt)
                {
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

        Label labelDefault = m_labelDefault;
        assert labelDefault != null;
        code.add(new Jump(labelDefault));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The switch statement or expression.
     */
    private final AstNode m_nodeSwitch;

    /**
     * Set to true if a scope to be created for the switch.
     */
    private boolean m_fHasDecl;

    /**
     * The actual number of condition values.
     */
    private int m_cCondVals = -1;

    /**
     * The condition (or list of conditions).
     */
    private List<AstNode> m_listCond;

    /**
     * Set to true iff the condition is determined to abort.
     */
    private boolean m_fCondAborts;

    /**
     * Set to true if the switch can fall through without going through a case group because there
     * is no default and not all of the possible cases are covered.
     */
    private boolean m_fCompletes;

    /**
     * The type of each condition expression / tuple field of case statements.
     */
    private TypeConstant[] m_atypeCond;

    /**
     * "Bit set" (encoded as a long) for each condition expression / tuple field of case statement
     * that is an "is switch" (an ".is()" instead of an equals test).
     */
    private long m_afIsSwitch;

    /**
     * If the condition evaluates to a constant, then this holds that value.
     */
    private Constant[] m_aconstCond;

    /**
     * The BAST expression for the condition.
     */
    private ExprAST m_bastCond;

    /**
     * The type of the condition.
     */
    private TypeConstant m_typeCase;

    /**
     * If the switch contains any Type conditions, this value is the bitmask indicating which fields
     * hold corresponding expressions.
     */
    private long m_lTypeExpr;

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
    private final ListSet<Constant> m_listsetCase = new ListSet<>();

    /**
     * A list of case labels.
     */
    private final List<Label> m_listLabels = new ArrayList<>();

    /**
     * Set to false when any non-constant case values are encountered.
     */
    private boolean m_fAllConsts = true;

    /**
     * A list-map of labels corresponding to the list of case values; associated value is a cookie.
     */
    private final ListMap<Label, CookieType> m_mapLabels = new ListMap<>();

    /**
     * The (lazily created) list of all case values that have wildcards or intervals.
     */
    private ListMap<Constant, Long> m_mapWild;

    /**
     * After the validateEnd(), this holds all of the constant values for the cases.
     */
    private Constant[] m_aconstCase;

    /**
     * After the validateEnd(), this holds all of the labels for the cases, unless an if-ladder is
     * used.
     */
    private Label[] m_alabelCase;

    /**
     * Use the "jump offset" byte code optimization.
     */
    private boolean m_fUseJumpInt = false;

    /**
     * Used to calculate the range of cardinal case values to determine if JMP_INT can be used.
     */
    private PackedInteger m_pintMin;

    /**
     * Used to calculate the range of cardinal case values to determine if JMP_INT can be used.
     */
    private PackedInteger m_pintMax;
    }