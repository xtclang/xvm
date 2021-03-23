package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

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
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeFit fit = TypeFit.Fit;
        for (AstNode node : contents)
            {
            if (node instanceof Expression)
                {
                fit = fit.combineWith(((Expression) node).testFitMulti(ctx, atypeRequired, errs));
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

        // create a new context in case there are short-circuiting conditions that result in
        // narrowing inferences (see comments in SwitchStatement.validateImpl)
        ctx = ctx.enter();

        // validate the switch condition
        boolean fValid = mgr.validateCondition(ctx, cond, errs);

        // the case manager enters a new context if the switch condition declares variables
        Context ctxCase = mgr.getSwitchContext();
        if (ctxCase == null)
            {
            ctxCase = ctx;
            }

        ctxCase = ctxCase.enterIf();

        ConstantPool   pool      = pool();
        TypeCollector  collector = new TypeCollector(pool);
        List<AstNode>  listNodes = contents;
        boolean        fInCase   = false;
        int            cExprs    = 0;
        for (int iNode = 0, cNodes = listNodes.size(); iNode < cNodes; ++iNode)
            {
            AstNode node = listNodes.get(iNode);
            if (node instanceof CaseStatement)
                {
                if (fInCase)
                    {
                    ctxCase = ctxCase.enterOr();
                    }

                fValid &= mgr.validateCase(ctxCase, (CaseStatement) node, errs);

                if (fInCase)
                    {
                    ctxCase = ctxCase.exit();
                    }
                else
                    {
                    fInCase = true;
                    }
                }
            else // it's an expression value
                {
                if (!fInCase)
                    {
                    node.log(errs, Severity.ERROR, Compiler.SWITCH_CASE_EXPECTED);
                    fValid = false;
                    break;
                    }
                fInCase = false;
                cExprs++;

                ctxCase = ctxCase.enterFork(true);

                Expression exprOld = (Expression) node;
                Expression exprNew = exprOld.validateMulti(ctxCase, atypeRequest, errs);

                ctxCase = ctxCase.exit();

                mgr.endCaseGroup(exprNew == null ? exprOld : exprNew);

                if (iNode < cNodes - 1)
                    {
                    ctxCase = ctxCase.enterFork(false).enterIf();
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

                    if (exprNew.isCompletable())
                        {
                        collector.add(exprNew.getTypes());
                        }
                    }
                }
            }

        for (int i = 0, c = 2*cExprs - 1; i < c; ++i)
            {
            ctxCase = ctxCase.exit();
            }

        // notify the case manager that we're finished collecting everything
        fValid &= mgr.validateEnd(ctxCase, errs);

        ctx = ctx.exit();

        TypeConstant[] atypeActual = TypeConstant.NO_TYPES;
        Constant[]     aconstVal   = null;
        if (fValid)
            {
            // determine the result type of the switch expression
            atypeActual = collector.inferMulti(atypeRequired);
            if (atypeActual.length == 0)
                {
                log(errs, Severity.ERROR, Compiler.SWITCH_TYPES_NONUNIFORM);
                fValid = false;
                }

            // determine the constant value of the switch expression
            if (mgr.isSwitchConstant())
                {
                Expression exprResult = mgr.getCookie(mgr.getSwitchConstantLabel());
                aconstVal = exprResult.toConstants();
                }
            }

        return fValid
                ? finishValidations(ctx, atypeRequired, atypeActual, TypeFit.Fit, aconstVal, errs)
                : null;
        }

    @Override
    protected boolean allowsConditional(Expression exprChild)
        {
        return false;
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
            // a scope will be required if the switch condition declares any new variables
            boolean fScope = m_casemgr.hasDeclarations();
            if (fScope)
                {
                code.add(new Enter());
                }

            if (m_casemgr.usesIfLadder())
                {
                m_casemgr.generateIfLadder(ctx, code, contents, errs);
                }
            else
                {
                m_casemgr.generateJumpTable(ctx, code, errs);
                }

            generateCaseBodies(ctx, code, aLVal, errs);

            if (fScope)
                {
                code.add(new Exit());
                }
            }
        }

    private void generateCaseBodies(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        List<AstNode> aNodes    = contents;
        int           cNodes    = aNodes.size();
        Label         labelCur  = null;
        Label         labelExit = new Label("switch_end");
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

    @Override
    public boolean isCompletable()
        {
        return !m_casemgr.isConditionAborting();
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
