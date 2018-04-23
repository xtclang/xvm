package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as a lambda parameter when nobody cares what the parameter is
 * and they just want it to go away quietly.
 */
public class NonBindingExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NonBindingExpression(long lStartPos, long lEndPos, TypeExpression type)
        {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        this.type      = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the type expression of the unbound argument, iff one was specified; otherwise null
     */
    public TypeExpression getArgType()
        {
        return type;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
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
        return type == null
                ? null
                : type.getImplicitType(ctx);
        }

    @Override
    public boolean isNonBinding()
        {
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return type == null
                ? "?"
                : "<" + type + ">?";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected long           lStartPos;
    protected long           lEndPos;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NonBindingExpression.class, "type");
    }
