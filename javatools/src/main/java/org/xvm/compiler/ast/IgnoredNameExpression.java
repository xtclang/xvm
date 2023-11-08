package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.RegisterAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as:
 * <ul><li>a lambda parameter when the lambda doesn't need the value of that parameter and just
 * wants to ignore that it exists altogether;
 * </li><li>a "do not bind" parameter when attempting to partially curry a method or function;
 * </li><li>a wild-card match in a case statement;
 * </li><li>an ignored LValue in an assignment, such as: {@code (_, Int x) = foo()}
 * </li></ul>
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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        return TypeFit.Fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeConstant type = typeRequired == null
                ? pool().typeObject()
                : typeRequired;

        return finishValidation(ctx, type, type, TypeFit.Fit, pool().ensureMatchAnyConstant(type), errs);
        }

    @Override
    public boolean isAssignable(Context ctx)
        {
        return true;
        }

    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs)
        {
        return new Assignable();
        }

    @Override
    public Argument generateArgument(Context ctx, Code code,
                                     boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return generateBlackHole(null);
        }

    @Override
    public ExprAST getExprAST()
        {
        return new RegisterAST(Op.A_IGNORE, pool().typeObject(), null);
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
        return Collections.emptyMap();
        }
    }