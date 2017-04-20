package org.xvm.compiler.ast;


import java.lang.reflect.Field;


/**
 * A type expression that can't figure out how to be a type exception. It pretends to be a type,
 * but it's going to end in misery and compiler errors.
 *
 * @author cp 2017.04.07
 */
public class BadTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BadTypeExpression(Expression nonType)
        {
        this.nonType = nonType;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean canComplete()
        {
        return false;
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
        return "/* NOT A TYPE!!! */ " + nonType;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression nonType;

    private static final Field[] CHILD_FIELDS = fieldsForNames(BadTypeExpression.class, "nonType");
    }
