package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpInt;
import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.JumpVal_N;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.ListMap;
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
        // determine the type to request from each "result expression" (i.e. the result of this
        // switch expression, each of which comes after some "case:" label)
        TypeConstant[] atypeRequest = atypeRequired == null
                ? getImplicitTypes(ctx)
                : atypeRequired;

        // the structure of the switch is determined using a case manager
        CaseManager<Expression> mgr = new CaseManager<>(this);
        m_casemgr = mgr;

        // validate the switch condition
        boolean fValid = mgr.validateCondition(ctx, cond, errs);

        // the case manager enters a new context if the switch condition declares variables
        Context ctxCase = mgr.getSwitchContext();
        if (ctxCase == null)
            {
            ctxCase = ctx;
            }

        ConstantPool   pool      = pool();
        TypeCollector  collector = new TypeCollector(pool);
        List<AstNode>  listNodes = contents;
        boolean        fInCase   = false;
        for (int iNode = 0, cNodes = listNodes.size(); iNode < cNodes; ++iNode)
            {
            AstNode node = listNodes.get(iNode);
            if (node instanceof CaseStatement)
                {
                fInCase = true;
                fValid &= mgr.validateCase(ctxCase, (CaseStatement) node, errs);
                }
            else // it's an expression value
                {
                Expression exprOld = (Expression) node;
                Expression exprNew = exprOld.validateMulti(ctxCase, atypeRequest, errs);

                if (fInCase)
                    {
                    mgr.endCaseGroup(exprNew == null ? exprOld : exprNew);
                    fInCase = false;
                    }
                else
                    {
                    node.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_EXPECTED);
                    fValid = false;
                    }

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
                    }
                }
            }

        // notify the case manager that we're finished collecting everything
        fValid &= mgr.validateEnd(ctxCase, errs);

        // determine the result type of the switch expression
        TypeConstant[] atypeActual = collector.inferMulti(atypeRequired);

        // determine the constant value of the switch expression
        Constant[] aconstVal = null;
        if (mgr.isSwitchConstant())
            {
            Expression exprResult = mgr.getCookie(mgr.getSwitchConstantLabel());
            aconstVal = exprResult.toConstants();
            }

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
                boolean fScope = m_casemgr.hasDeclarations();
                if (fScope)
                    {
                    code.add(new Enter());
                    }

                generateJumpSwitch(ctx, code, aLVal, errs);

                if (fScope)
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

        Label labelDefault = m_casemgr.getDefaultLabel();
        assert labelDefault != null;
        code.add(new Jump(labelDefault));

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

        Label labelDefault = m_casemgr.getDefaultLabel();
        if (labelDefault == null)
            {
            labelDefault = new Label("default_assert");
            }

        Argument[] aArgVal  = new Argument[m_casemgr.getConditionCount()];
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

            if (m_casemgr.usesJmpInt() && (!m_casemgr.getJmpIntOffset().equals(PackedInteger.ZERO)
                    || !exprCond.getType().isA(pool().typeInt())))
                {
                // either the offset is non-zero of the type is non-int; either way, convert it to a
                // zero-based int
                exprCond = new ToIntExpression(exprCond, m_casemgr.getJmpIntOffset(), errs);
                }

            Argument[] aArgsAdd = exprCond.generateArguments(ctx, code, true, true, errs);
            int        cArgsAdd = aArgsAdd.length;
            System.arraycopy(aArgsAdd, 0, aArgVal, ofArgVal, cArgsAdd);
            ofArgVal += cArgsAdd;
            }
        assert ofArgVal == aArgVal.length;

        Label[] alabelCase = m_casemgr.getCaseLabels();
        if (m_casemgr.usesJmpInt())
            {
            assert cond.size() == 1 && aArgVal.length == 1;
            code.add(new JumpInt(aArgVal[0], alabelCase, labelDefault));
            }
        else
            {
            Constant[] aconstCase = m_casemgr.getCaseConstants();
            code.add(m_casemgr.usesJmpValN()
                    ? new JumpVal_N(aArgVal, aconstCase, alabelCase, labelDefault)
                    : new JumpVal(aArgVal[0], aconstCase, alabelCase, labelDefault));
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

        if (labelDefault != m_casemgr.getDefaultLabel())
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

    private transient CaseManager<Expression> m_casemgr;
    private transient boolean                 m_fAborting;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
