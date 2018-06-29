package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.ConditionalStatement.Usage;
import org.xvm.compiler.ast.Statement.Context;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" expression.
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
        if (cond == null)
            {
            typeCase  = pool.typeBoolean();
            constCond = pool.valTrue();
            }
        else
            {
            // let the conditional statement know that it is indeed being used as a condition
            cond.markConditional(Usage.Switch, new Label());

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
// REVIEW we should NOT be using "instanceof" here; it should be part of ConditionalStatement API
                if (condNew instanceof VariableDeclarationStatement)
                    {
                    typeCase = ((VariableDeclarationStatement) condNew).getType();
                    }
                else if (condNew instanceof ExpressionStatement)
                    {
                    Expression expr = ((ExpressionStatement) condNew).getExpression();
                    typeCase = expr.getType();
                    if (expr.isConstant())
                        {
                        constCond = expr.toConstant();
                        }
                    }
                else
                    {
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
        List<TypeConstant> listTypes    = new ArrayList<>();
        Set<Constant>      setCase      = new HashSet<>();
        Label              labelDefault = null;
        Label              labelCurrent = null;
        int                nLabels      = 0;
        List<AstNode>      listNodes    = contents;
        for (int iNode = 0, cNodes = listNodes.size(); iNode < cNodes; ++iNode)
            {
            AstNode node = listNodes.get(iNode);
            if (node instanceof CaseStatement)
                {
                if (labelCurrent == null)
                    {
                    labelCurrent = new Label("case_" + (++nLabels));
                    }

                CaseStatement    stmtCase  = (CaseStatement) node;
                List<Expression> listExprs = stmtCase.exprs;
                if (listExprs == null)
                    {
                    if (labelDefault == null)
                        {
                        labelDefault = labelCurrent;
                        }
                    else
                        {
                        // TODO log error
                        throw new IllegalStateException("multiple default labels");
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
                                if (mapCase.containsKey(constCase))
                                    {
                                    // collision
                                    // TODO log error
                                    throw new IllegalStateException("duplicate case: " + constCase);
                                    }
                                else
                                    {
                                    mapCase.put(constCase, labelCurrent);

                                    // TODO check if this is the one we're looking for to find the constant value of the switch
                                    // TODO - cond has to be not-null w/ constant and a matching case
                                    }
                                }
                            else
                                {
                                // TODO log error
                                throw new IllegalStateException("expr not constant: " + exprCase);
                                }
                            }
                        }
                    }
                }
            else
                {
                if (labelCurrent == null)
                    {
                    // TODO log error
                    throw new IllegalStateException("value precedes the first case");
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

                    labelCurrent = null;

                    // TODO if this is the one that we're looking for for the constant value of the switch ...
                    // if (... && exprNew.isConstant())
                    //     {
                    //     constVal = exprNew.toConstant();
                    //     }
                    }
                }
            }

        if (labelCurrent != null)
            {
            // TODO log error
            throw new IllegalStateException("missing value for the last case");
            }

        if (labelDefault == null)
            {
            // TODO this means that the switch "short circuits"
            }

        TypeConstant typeActual = ListExpression.inferCommonType(
                listTypes.toArray(new TypeConstant[listTypes.size()]));

        if (fScope)
            {
            ctx = ctx.exitScope();
            }

        return finishValidation(typeRequired, typeActual, fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateAssignment(code, LVal, errs);
            }

        // TODO need cases to labels
        // TODO need labels to expressions
        code.add(new JumpVal(argVal, aArgCase, aLabels, labelDefault));
        for (Expression expr : )
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
                  .append((Expression) node)
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

    private transient

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
