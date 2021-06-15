package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * A type expression that can't figure out how to be a type expression. It pretends to be a type,
 * but it's going to end in misery and compiler errors.
 */
public class BadTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BadTypeExpression(Expression nonType)
        {
        this.nonType = nonType;
        }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs)
        {
        return new UnresolvedTypeConstant(pool(),
                new UnresolvedNameConstant(pool(), nonType.toString()));
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isCompletable()
        {
        return false;
        }

    @Override
    public long getStartPosition()
        {
        return nonType.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return nonType.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, nonType.toString());
        return null;
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
