package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpInt;
import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" expression.
 */
public class SwitchExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchExpression(Token keyword, List<AstNode> cond, List<AstNode> contents, long lEndPos)
        {
        this.keyword  = keyword;
        this.cond     = cond;
        this.contents = contents;
        this.lEndPos  = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean hasSingleValueImpl()
        {
        return false;
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        TypeCollector collector = new TypeCollector(pool());
        for (AstNode node : contents)
            {
            if (node instanceof Expression)
                {
                collector.add(((Expression) node).getImplicitTypes(ctx));
                }
            }
        return collector.inferMulti(null);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
        {
        boolean fAny = false;
        TypeFit fit  = TypeFit.Fit;
        for (AstNode node : contents)
            {
            if (node instanceof Expression)
                {
                fit.combineWith(((Expression) node).testFitMulti(ctx, atypeRequired));
                if (!fit.isFit())
                    {
                    return fit;
                    }
                }
            }

        return fit;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        boolean        fValid     = true;
        ConstantPool   pool       = pool();
        Constant[]     aconstCond = null;
        boolean        fIfSwitch  = cond == null;
        if (fIfSwitch)
            {
            // there is no condition, so all of the case statements must evaluate to boolean, and
            // the first one that matches "True" will be used
            m_atypeCond = new TypeConstant[] {pool.typeBoolean()};
            aconstCond  = new Constant[] {pool.valTrue()};
            }
        else
            {
            List<TypeConstant> listTypes  = new ArrayList<>();
            List<Constant>     listConsts = new ArrayList<>();
            boolean            fAllConst  = true;
            for (int i = 0, c = cond.size(); i < c; ++i)
                {
                AstNode        node    = cond.get(i);
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
                        cond.set(i, nodeNew);
                        }
                    }

                if (atype == null)
                    {
                    fValid = false;
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

            m_atypeCond = listTypes.toArray(TypeConstant.NO_TYPES);
            if (fValid && fAllConst)
                {
                aconstCond = listConsts.toArray(Constant.NO_CONSTS);
                }
            }

        // determine the type to request from each "result expression" (i.e. the result of this
        // switch expression, each of which comes after some "case:" label)
        TypeConstant[] atypeRequest = atypeRequired == null
                ? getImplicitTypes(ctx)
                : atypeRequired;

        Constant[]     aconstVal     = null;
        List<Constant> listVals      = fIfSwitch ? null : new ArrayList<>();
        Set<Constant>  setCase       = fIfSwitch ? null : new HashSet<>();
        boolean        fSingleCond   = m_atypeCond.length == 1;
        TypeConstant   typeCase      = pool.ensureImmutableTypeConstant(fSingleCond ? m_atypeCond[0] :
                                       pool.ensureParameterizedTypeConstant(pool.typeTuple(), m_atypeCond));
        boolean        fAllConsts    = true;
        boolean        fCardinals    = !fIfSwitch && fSingleCond && typeCase.isIntConvertible();
        boolean        fIntConsts    = fCardinals && typeCase.getExplicitClassFormat() != Component.Format.ENUM;
        PackedInteger  pintMin       = null;
        PackedInteger  pintMax       = null;
        boolean        fGrabNext     = false;
        List<Label>    listLabels    = fIfSwitch ? null : new ArrayList<>();
        Label          labelCurrent  = null;
        Label          labelDefault  = null;
        Constant[]     aconstDefault = null;
        int            cLabels       = 0;
        TypeCollector  collector     = new TypeCollector(pool);
        List<AstNode>  listNodes     = contents;
        for (int iNode = 0, cNodes = listNodes.size(); iNode < cNodes; ++iNode)
            {
            AstNode node = listNodes.get(iNode);
            if (node instanceof CaseStatement)
                {
                if (labelCurrent == null)
                    {
                    labelCurrent = new Label("case_" + (++cLabels));
                    }

                CaseStatement stmtCase = (CaseStatement) node;
                stmtCase.setLabel(labelCurrent);

                List<Expression> listExprs = stmtCase.exprs;
                if (listExprs == null) // no expressions on a case means "default:"
                    {
                    if (labelDefault == null)
                        {
                        labelDefault = labelCurrent;
                        }
                    else
                        {
                        stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_DUPLICATE);
                        fValid = false;
                        }
                    }
                else
                    {
                    // validate the expressions in the case label
                    for (int iExpr = 0, cExprs = listExprs.size(); iExpr < cExprs; ++iExpr)
                        {
                        Expression exprCase = listExprs.get(iExpr);
                        boolean    fNormal  = true;
                        long       lIgnore  = 0L;
                        long       lRange   = 0L;

                        if (exprCase instanceof TupleExpression)
                            {
                            List<Expression> listFields = ((TupleExpression) exprCase).exprs;
                            for (int i = 0, c = listFields.size(); i < c; ++i)
                                {
                                Expression exprField = listFields.get(i);
                                if (exprField instanceof IgnoredNameExpression)
                                    {
                                    lIgnore |= 1 << i;
                                    fNormal  = false;
                                    }
                                }
                            }

                        TypeConstant typeMatch = typeCase;
                        if (!exprCase.testFit(ctx, typeMatch).isFit())
                            {
                            // figure out if any non-ignored field could be a range instead of a
                            // single value
                            TypeConstant[] atypeCond = m_atypeCond;
                            int            cFields   = atypeCond.length;
                            int            cValues   = cFields - Long.bitCount(lIgnore);
                            if (cValues > 0)
                                {
                                TypeConstant[] atypeTry = m_atypeCond;
                                for (int iTry = 1, cTries = 1 << cFields-1; iTry <= cTries; ++iTry)
                                    {

                                    }
                                fNormal = false;
                                // TODO testFit .. _
                                }
                            }

                        Expression exprNew  = exprCase.validate(ctx, typeCase, errs);
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
                                if (fAllConsts && aconstCond != null && aconstCond.equals(constCase))
                                    {
                                    fGrabNext = true;
                                    }

                                if (!fIfSwitch)
                                    {
                                    if (setCase.add(constCase))
                                        {
                                        listVals.add(constCase);
                                        listLabels.add(labelCurrent);

                                        if (fIntConsts)
                                            {
                                            PackedInteger pint = constCase.getIntValue();
                                            if (pintMin == null)
                                                {
                                                pintMin = pintMax = pint;
                                                }
                                            else if (pint.compareTo(pintMax) > 0)
                                                {
                                                pintMax = pint;
                                                }
                                            else if (pint.compareTo(pintMin) < 0)
                                                {
                                                pintMin = pint;
                                                }
                                            }
                                        }
                                    else
                                        {
                                        // collision
                                        stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DUPLICATE,
                                                constCase.getValueString());
                                        fValid = false;
                                        }
                                    }
                                }
                            else
                                {
                                fAllConsts = false;
                                if (!fIfSwitch)
                                    {
                                    stmtCase.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_CONSTANT_REQUIRED);
                                    fValid = false;
                                    }
                                }
                            }
                        }
                    }
                }
            else // it's an expression value
                {
                if (labelCurrent == null)
                    {
                    node.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_EXPECTED);
                    fValid = false;
                    }

                Expression exprOld = (Expression) node;
                Expression exprNew = exprOld.validateMulti(ctx, atypeRequest, errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        listNodes.set(iNode, exprNew);
                        }

                    collector.add(exprNew.getTypes());

                    if (exprNew.isConstant())
                        {
                        if (fGrabNext)
                            {
                            aconstVal = exprNew.toConstants();
                            }

                        if (labelCurrent == labelDefault)
                            {
                            // remember the default value so that we can compute the default value
                            // for the case in which nothing matches
                            aconstDefault = exprNew.toConstants();
                            }
                        }
                    }

                // at this point, we have found the trailing expression for the "current label"
                // representing all of the immediately preceding case statements, so reset it
                labelCurrent = null;

                // reset the "this is the expression that might provide a constant value for the
                // if-switch" flag
                if (fIfSwitch && fGrabNext && aconstVal == null)
                    {
                    // this would have been the expression value to provide the constant value, but
                    // the expression value itself is not constant, so the switch expression cannot
                    // resolve to a compile-time constant value
                    fAllConsts = false;
                    }

                fGrabNext = false;
                }
            }

        if (labelCurrent != null)
            {
            listNodes.get(listNodes.size()-1).log(errs, Severity.ERROR, Compiler.SWITCH_CASE_DANGLING);
            fValid = false;
            }
        else if (aconstCond != null && aconstVal == null && fAllConsts && aconstDefault != null)
            {
            aconstVal = aconstDefault;
            }
        else if (labelDefault == null && (!fCardinals || listVals.size() < typeCase.getIntCardinality()))
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
            int cVals = listVals.size();
            assert listLabels.size() == cVals;

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
                    if (listVals.size() >= (pintRange.getLong() >>> 2))
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
                    Label    label     = listLabels.get(i);
                    Constant constCase = listVals.get(i);
                    int      nIndex    = constCase.getIntValue().sub(pintMin).getInt();

                    aLabels[nIndex] = label;
                    }

                // fill in any gaps
                for (int i = 0; i < cRange; ++i)
                    {
                    if (aLabels[i] == null)
                        {
                        assert labelDefault != null;
                        aLabels[i] = labelDefault;
                        }
                    }

                m_alabelCase = aLabels;
                m_pintOffset = pintMin;
                }
            else
                {
                m_aconstCase = listVals  .toArray(new Constant[cVals]);
                m_alabelCase = listLabels.toArray(new Label   [cVals]);
                }
            }

        m_labelDefault = labelDefault;

        TypeConstant[] atypeActual = collector.inferMulti(atypeRequired);
        return finishValidations(atypeRequired, atypeActual, fValid ? TypeFit.Fit : TypeFit.NoFit, aconstVal, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateAssignments(ctx, code, aLVal, errs);
            }
        else
            {
            if (cond == null)
                {
                generateIfSwitch(ctx, code, aLVal, errs);
                }
            else
                {
                // a scope will be required if the switch condition declares any new variables
                if (m_fScope)
                    {
                    code.add(new Enter());
                    }

                generateJumpSwitch(ctx, code, aLVal, errs);

                if (m_fScope)
                    {
                    code.add(new Exit());
                    }
                }
            }
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
        
        Label labelDefault = m_labelDefault;
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
            code.add(new JumpVal(aArgVal, m_aconstCase, m_alabelCase, labelDefault));
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

        if (labelDefault != m_labelDefault)
            {
            // default is an assertion
            code.add(labelDefault);
            code.add(new Assert(pool().valFalse()));
            }

        code.add(labelExit);
        }

    @Override
    public boolean isCompletable()
        {
        return !m_fAborting;
        }

    
    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("switch (");

        if (cond != null && !cond.isEmpty())
            {
            boolean fFirst = true;
            for (AstNode node : cond)
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(node);
                }
            }

        sb.append(")\n    {");
        for (AstNode node : contents)
            {
            if (node instanceof CaseStatement)
                {
                sb.append('\n')
                  .append(indentLines(node.toString(), "    "));
                }
            else
                {
                sb.append(' ')
                  .append(node)
                  .append(';');
                }
            }
        sb.append("\n    }");

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected List<AstNode> cond;
    protected List<AstNode> contents;
    protected long          lEndPos;

    private transient boolean        m_fScope;
    private transient TypeConstant[] m_atypeCond;
    private transient Constant[]     m_aconstCase;
    private transient Label[]        m_alabelCase;
    private transient Label          m_labelDefault;
    private transient PackedInteger  m_pintOffset;
    private transient boolean        m_fAborting;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
