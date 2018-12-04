package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as a lambda parameter when nobody cares what the parameter is
 * and they just want it to go away quietly.
 */
public class IgnoredNameExpression
        extends NameExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IgnoredNameExpression(Token name)
        {
        super(name);
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        return new BadTypeExpression(this);
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return pool().typeObject();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeConstant type = typeRequired == null
                ? pool().typeObject()
                : typeRequired;

        return finishValidation(type, type, TypeFit.Fit, null, errs);
        }

    @Override
    public boolean isAssignable()
        {
        return true;
        }

    @Override
    public Argument generateArgument(Context ctx, Code code,
                                     boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return fUsedOnce
            ? new Register(pool().typeObject(), Op.A_STACK)
            : new Register(pool().typeObject());
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "_";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        return Collections.EMPTY_MAP;
        }
    }
