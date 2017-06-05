package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A variable declaration statement specifies a type and a simply name for a variable, with an
 * optional initial value.
 *
 * Additionally, this can represent the combination of a variable "conditional declaration".
 *
 * @author cp 2017.04.04
 */
public class VariableDeclarationStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public VariableDeclarationStatement(TypeExpression type, Token name, Expression value)
        {
        this(type, name, null, value, true);
        }

    public VariableDeclarationStatement(TypeExpression type, Token name, Token op, Expression value)
        {
        this(type, name, op, value, false);
        }

    private VariableDeclarationStatement(TypeExpression type, Token name, Token op, Expression value, Boolean standalone)
        {
        this.name  = name;
        this.type  = type;
        this.value = value;
        this.cond  = op != null && op.getId() == Token.Id.COLON;
        this.term  = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isConditional()
        {
        return cond;
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return value == null ? name.getEndPosition() : value.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append(' ')
          .append(name.getValue() == null ? name.getId().TEXT : name.getValue());

        if (value != null)
            {
            sb.append(' ')
            .append(cond ? ':' : '=')
            .append(' ')
            .append(value);
            }

        if (term)
            {
            sb.append(';');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected Token          name;
    protected Expression     value;
    protected boolean        cond;
    protected boolean        term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
