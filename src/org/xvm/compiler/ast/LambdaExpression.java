package org.xvm.compiler.ast;


import org.xvm.asm.Component;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

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
            Component            parent   = getComponent();
            MultiMethodStructure structMM = parent.ensureMultiMethodStructure(METHOD_NAME);
//            MethodStructure      lambda   = new MethodStructure(structMM, );
//            protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId,
//                    ConditionalConstant condition, Annotation[] annotations,
//                    Parameter[] aReturns, Parameter[] aParams, boolean fHasCode, boolean fUsesSuper)
//            structMM.addC
//            m_lambda = new MethodStructure()
            }

        super.registerStructures(mgr, errs);
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        super.resolveNames(mgr, errs);
        }

    // TODO


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
