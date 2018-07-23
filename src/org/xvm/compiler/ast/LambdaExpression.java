package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * Lambda expression is an inlined function. This version uses parameters that are assumed to be
 * names only.
 */
public class LambdaExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     *
     * @param params     either a list of Expression objects or a list of Parameter objects
     * @param operator
     * @param body
     * @param lStartPos
     */
    public LambdaExpression(List params, Token operator, StatementBlock body, long lStartPos)
        {
        if (!params.isEmpty() && params.get(0) instanceof Expression)
            {
            assert params.stream().allMatch(Expression.class::isInstance);
            this.paramNames = params;
            }
        else
            {
            assert params.stream().allMatch(org.xvm.asm.Parameter.class::isInstance);
            this.params = params;
            }

        this.operator  = operator;
        this.body      = body;
        this.lStartPos = lStartPos;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Component getComponent()
        {
        MethodStructure method = m_lambda;
        return method == null
                ? super.getComponent()
                : method;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return body.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        if (m_lambda == null)
            {
            TypeConstant[] atypes   = null;
            String[]       asParams = null;
            if (paramNames == null)
                {
                // build an array of types and an array of names
                int cParams = params == null ? 0 : params.size();
                atypes   = new TypeConstant[cParams];
                asParams = new String[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    Parameter param = params.get(i);
                    atypes  [i] = param.getType().ensureTypeConstant();
                    asParams[i] = param.getName();
                    }
                }
            else
                {
                // build an array of names
                int cParams = paramNames.size();
                asParams = new String[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    Expression expr = paramNames.get(i);
                    if (expr instanceof NameExpression)
                        {
                        // note: could also be an IgnoredNameExpression
                        asParams[i] = ((NameExpression) expr).getName();
                        }
                    else
                        {
                        expr.log(errs, Severity.ERROR, Compiler.NAME_REQUIRED);
                        asParams[i] = Id.IGNORED.TEXT;
                        }
                    }
                }

            Component            container = getParent().getComponent();
            MultiMethodStructure structMM  = container.ensureMultiMethodStructure(METHOD_NAME);
            MethodStructure      lambda    = structMM.createLambda(atypes, asParams);
            // TODO
            m_lambda = lambda;
            }

        super.registerStructures(mgr, errs);
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        super.resolveNames(mgr, errs);
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        // TODO
        return super.getImplicitType(ctx);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        // TODO
        return super.testFit(ctx, typeRequired);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // TODO at the end of validate, we build the lambda signature, and set it on the MethodConstant
        return super.validate(ctx, typeRequired, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        boolean first = true;
        for (Object param : (params == null ? paramNames : params))
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(param);
            }

        sb.append(')')
          .append(' ')
          .append(operator.getId().TEXT);

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        String s = body.toString();
        if (s.indexOf('\n') >= 0)
            {
            sb.append('\n')
              .append(indentLines(s, "    "));
            }
        else
            {
            sb.append(' ')
              .append(s);
            }

        return sb.toString();
        }

    @Override
    public String toDumpString()
        {
        return toSignatureString() + " {...}";
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final String METHOD_NAME = "->";

    protected List<Parameter>  params;
    protected List<Expression> paramNames;
    protected Token            operator;
    protected StatementBlock   body;
    protected long             lStartPos;

    private MethodStructure m_lambda;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LambdaExpression.class, "params", "paramNames", "body");
    }
