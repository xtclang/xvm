package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;


/**
 * Statement expression is conceptually similar to a lambda, except that it does not require an
 * actual function, and it behaves as if it is executed at the point in the code where it is
 * encountered. In other words, these two are conceptually quite similar:
 *
 * <code><pre>
 *   x = () -> {2 + 2}();   // note the trailing "call"
 * </pre></code>
 * and:
 * <code><pre>
 *   x = {return 2 + 2;}
 * </pre></code>
 * <p/>
 * To determine the type of the StatementExpression, the one or more required "return" statements
 * need to be analyzed to determine their types.
 * <p/>
 * REVIEW this expression could theoretically support a multi value
 * REVIEW this expression could theoretically support a conditional return
 */
public class StatementExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a StatementExpression.
     *
     * @param body  the
     */
    public StatementExpression(StatementBlock body)
        {
        this.body = body;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return body.getStartPosition();
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
    public TypeConstant getImplicitType(Context ctx)
        {
        return super.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        return super.validate(ctx, typeRequired, errs);
        }

    // TODO


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return body.toString();
        }

    @Override
    public String toDumpString()
        {
        return body.toDumpString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected StatementBlock body;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementExpression.class, "body");
    }
