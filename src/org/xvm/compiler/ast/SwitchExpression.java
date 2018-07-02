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

import org.xvm.compiler.ast.ConditionalStatement.Usage;
import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" expression.
 *
 * <p/>REVIEW consider adding support for case expressions of Range&lt;T&gt; for switch(T)
 */
public class SwitchExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchExpression(Token keyword, ConditionalStatement cond, List<AstNode> contents, long lEndPos)
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
    public TypeConstant getImplicitType(Context ctx)
        {
        List<TypeConstant> listTypes = new ArrayList<>();
        for (AstNode node : contents)
            {
            if (node instanceof Expression)
                {
                listTypes.add(((Expression) node).getImplicitType(ctx));
                }
            }
        return ListExpression.inferCommonType(listTypes.toArray(new TypeConstant[listTypes.size()]));
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean      fValid    = true;
        boolean      fScope    = false;
        ConstantPool pool      = pool();
        Constant     constCond = null;
        TypeConstant typeCase  = null;
        boolean      fIfSwitch = cond == null;
        if (fIfSwitch)
            {
            typeCase  = pool.typeBoolean();
            constCond = pool.valTrue();
            }
        else
            {
            // let the conditional statement know that it is indeed being used as a condition
            cond.markConditional(Usage.Switch, new Label("switch_else"));

            fScope = cond.isScopeRequired();
            if (fScope)
                {
                ctx = ctx.enterScope();
                }

            ConditionalStatement condNew = (ConditionalStatement) cond.validate(ctx, errs);
            if (condNew == null)
                {
                fValid = false;
                }
            else
                {
                if (condNew.hasExpression())
                    {
                    Expression expr = condNew.getExpression();
                    typeCase = expr.getType();
                    if (expr.isConstant())
                        {
                        constCond = expr.toConstant();
                        }
                    }
                else
                    {
                    // switch can't use MultipleDeclarationStatement, for example, and the parser
                    // should know that
                    throw new IllegalStateException("cond=" + condNew);
                    }
                cond = condNew;
                }
            }

        // determine the type to request from each "result expression"
        TypeConstant typeRequest = typeRequired == null
                ? getImplicitType(ctx)
                : typeRequired;

        Constant           constVal     = null;
        List<Constant>     listVals     = fIfSwitch ? null : new ArrayList<>();
        Set<Constant>      setCase      = fIfSwitch ? null : new HashSet<>();
        boolean            fAllConsts   = true;
        boolean            fIntConsts   = !fIfSwitch && typeCase.isIntConvertible();
        PackedInteger      pintMin      = null;
        PackedInteger      pintMax      = null;
        boolean            fGrabNext    = false;
        List<Label>        listLabels   = fIfSwitch ? null : new ArrayList<>();
        Label              labelCurrent = null;
        Label              labelDefault = null;
        Constant           constDefault = null;
        int                cLabels      = 0;
        List<TypeConstant> listTypes    = new ArrayList<>();
        List<AstNode>      listNodes    = contents;
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
                if (listExprs == null)
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

                            if (exprCase.isConstant())
                                {
                                Constant constCase = exprCase.toConstant();
                                if (constCond != null && constCond.equals(constCase) && (!fIfSwitch || fAllConsts))
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
                Expression exprNew = exprOld.validate(ctx, typeRequest, errs);
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

                    if (listTypes != null)
                        {
                        listTypes.add(exprNew.getType());
                        }

                    if (exprNew.isConstant())
                        {
                        if (fGrabNext)
                            {
                            constVal = exprNew.toConstant();
                            }

                        if (labelCurrent == labelDefault)
                            {
                            // remember the default value so that we can compute the default value
                            // for the case in which nothing matches
                            constDefault = exprNew.toConstant();
                            }
                        }
                    }

                // at this point, we have found the trailing expression for the "current label"
                // representing all of the immediately preceding case statements, so reset it
                labelCurrent = null;

                // reset the "this is the expression that might provide a constant value for the
                // if-switch" flag
                if (fIfSwitch && fGrabNext && constVal == null)
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
        else if (constCond != null && constVal == null && fAllConsts && constDefault != null)
            {
            constVal = constDefault;
            }
        else if (labelDefault == null && (!fIntConsts || listVals.size() < typeCase.getIntCardinality()))
            {
            // this means that the switch would "short circuit", which is not allowed
            log(errs, Severity.ERROR, Compiler.SWITCH_DEFAULT_REQUIRED);
            fValid = false;
            }

        TypeConstant typeActual = ListExpression.inferCommonType(
                listTypes.toArray(new TypeConstant[listTypes.size()]));
        if (typeActual == null)
            {
            typeActual = typeRequest == null
                    ? pool.typeObject()
                    : typeRequest;
            }

        if (fScope)
            {
            ctx = ctx.exitScope();
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
            if (pintMin != null && typeCase.getExplicitClassFormat() != Component.Format.ENUM)
                {
                PackedInteger pintRange = pintMax.sub(pintMin);
                if (!pintRange.isBig())
                    {
                    // the idea is that if we have ints from 100,000 to 100,100, and there are roughly
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

        return finishValidation(typeRequired, typeActual, fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateAssignment(code, LVal, errs);
            }
        else
            {
            if (cond == null)
                {
                generateIfSwitch(code, LVal, errs);
                }
            else
                {
                if (cond.isScopeRequired())
                    {
                    code.add(new Enter());
                    }

                generateJumpSwitch(code, LVal, errs);

                if (cond.isScopeRequired())
                    {
                    code.add(new Exit());
                    }
                }
            }
        }

    private void generateIfSwitch(Code code, Assignable LVal, ErrorListener errs)
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
                        expr.generateConditionalJump(code, labelCur, true, errs);
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
                expr.generateAssignment(code, LVal, errs);
                code.add(new Jump(labelExit));
                }
            }
        code.add(labelExit);
        }

    private void generateJumpSwitch(Code code, Assignable LVal, ErrorListener errs)
        {
        Expression exprCond = cond.getExpression();
        if (m_aconstCase == null && (m_pintOffset != null || !exprCond.getType().isA(pool().typeInt())))
            {
            exprCond = new ToIntExpression(exprCond, m_pintOffset, errs);
            }
        Argument argVal = exprCond.generateArgument(code, true, true, errs);

        Label labelDefault = m_labelDefault;
        if (labelDefault == null)
            {
            labelDefault = new Label("default_assert");
            }

        if (m_aconstCase == null)
            {
            code.add(new JumpInt(argVal, m_alabelCase, labelDefault));
            }
        else
            {
            code.add(new JumpVal(argVal, m_aconstCase, m_alabelCase, labelDefault));
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

                    if (labelNew == labelDefault)
                        {
                        // short-circuit also goes to the default label
                        code.add(cond.getLabel());
                        }

                    labelCur = labelNew;
                    }
                }
            else
                {
                node.updateLineNumber(code);
                ((Expression) node).generateAssignment(code, LVal, errs);
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


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("switch (");

        if (cond != null)
            {
            sb.append(cond);
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

    protected Token                keyword;
    protected ConditionalStatement cond;
    protected List<AstNode>        contents;
    protected long                 lEndPos;

    private transient Constant[]    m_aconstCase;
    private transient Label[]       m_alabelCase;
    private transient Label         m_labelDefault;
    private transient PackedInteger m_pintOffset;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
