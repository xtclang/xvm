package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import java.util.Set;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;
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
                if (condNew instanceof VariableDeclarationStatement)
                    {
                    notImplemented(); // TODO
                    }
                else if (condNew instanceof ExpressionStatement)
                    {
                    Expression expr = ((ExpressionStatement) condNew).getExpression();
                    typeCase = expr.getType();
                    if (expr.hasConstantValue())
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

        TypeConstant typeRequest = typeRequired == null
                ? getImplicitType(ctx)
                : typeRequired;
        List<TypeConstant> listTypes = new ArrayList<>();

        Constant constVal  = null; // TODO - cond has to be not-null w/ constant and a matching case

        Set<Constant> setCase = new HashSet<>();
        for (AstNode node : contents)
            {
            if (node instanceof CaseStatement)
                {
                // validate the expression
                }
            else
                {
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
                        // TODO keep new expression
                        }

                    if (listTypes != null)
                        {
                        listTypes.add(exprNew.getType());
                        }

                    // TODO verify has constant / is constant
                    if (exprNew.hasConstantValue())
                        {
                        Constant constCase = exprNew.toConstant();
                        if (setCase.add(constCase))
                            {
                            if (constCond != null && constCond.equals(constCase))
                                {
                                // TODO this is the one specifying the constant value!!! set a flag and grab the next expression
                                }
                            }
                        else
                            {
                            // collision
                            // TODO log error
                            }
                        }
                    }
                }
            }

        TypeConstant typeActual = ListExpression.inferCommonType(
                listTypes.toArray(new TypeConstant[listTypes.size()]));
        // TODO handle null, handle error cases

        if (fScope)
            {
            ctx = ctx.exitScope();
            }

        return finishValidation(typeRequired, typeActual, fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchExpression.class, "cond", "contents");
    }
