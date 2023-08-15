package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A case statement. This can only occur within a switch statement. (It's not a "real" statement;
 * it's more like a label.)
 */
public class CaseStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public CaseStatement(Token keyword, List<Expression> exprs, Token tokColon)
        {
        this.keyword = keyword;
        this.exprs   = exprs;
        this.lEndPos = tokColon.getEndPosition();
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this is the "default:" case
     */
    public boolean isDefault()
        {
        return exprs == null;
        }

    /**
     * @return the expressions of the values of the case, or null if this is the "default:" case
     */
    public List<Expression> getExpressions()
        {
        return exprs;
        }

    /**
     * @return the number of expressions
     */
    public int getExpressionCount()
        {
        return exprs == null ? 0 : exprs.size();
        }

    /**
     * @return the label assigned to this case statement, or null if none has been assigned
     */
    public Label getLabel()
        {
        return m_label;
        }

    /**
     * @param label  the label for this case statement
     */
    void setLabel(Label label)
        {
        assert m_label == null;
        m_label = label;
        }

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
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        // the case statement is a marker; it's just data, not an actual compilable AST node
        throw new IllegalStateException();
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, AstHolder holder,
                           ErrorListener errs)
        {
        // the case statement is a marker; it's just data, not an actual compilable AST node
        throw new IllegalStateException();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (exprs != null)
            {
            sb.append(' ')
              .append(exprs.get(0));

            for (int i = 1, c = exprs.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(exprs.get(i));
                }
            }

        sb.append(':');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token            keyword;
    protected List<Expression> exprs;
    protected long             lEndPos;

    private transient Label m_label;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CaseStatement.class, "exprs");
    }
