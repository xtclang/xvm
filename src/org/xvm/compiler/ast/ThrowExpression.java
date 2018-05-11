package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;


/**
 * A "throw expression" is a non-completing expression that throws an exception.
 */
public class ThrowExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ThrowExpression(Token keyword, Expression expr)
        {
        this.keyword = keyword;
        this.expr    = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the expression for the exception being thrown
     */
    public Expression getException()
        {
        return expr;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr.getEndPosition();
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
        // TODO GG - I need a "happy type" i.e. a type that works for anything (isA() everything!!!)
        return null;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        return TypeFit.Fit;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref)
        {
        return TypeFit.Fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        validateThrow(ctx, errs);
        finishValidation(TypeFit.Fit, typeRequired, null);
        return this;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref, ErrorListener errs)
        {
        validateThrow(ctx, errs);
        finishValidations(TypeFit.Fit, atypeRequired, null);
        return this;
        }

    protected void validateThrow(Context ctx, ErrorListener errs)
        {
        // validate the throw value expressions
        Expression exprNew = expr.validate(ctx, pool().typeException(), TuplePref.Rejected, errs);
        if (exprNew != expr && exprNew != null)
            {
            expr = exprNew;
            }
        }

    @Override
    public boolean isConstant()
        {
        return true;
        }

    @Override
    public boolean isAborting()
        {
        return true;
        }

    @Override
    public void generateVoid(Code code, ErrorListener errs)
        {
        generateThrow(code, errs);
        }

    @Override
    public Argument[] generateArguments(Code code, boolean fPack, ErrorListener errs)
        {
        generateThrow(code, errs);

        TypeConstant[] aTypes = getTypes();
        int            cArgs  = aTypes.length;
        Register[]     aArgs  = new Register[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = generateBlackHole(aTypes[i]);
            }
        return aArgs;
        }

    @Override
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        generateThrow(code, errs);
        }

    /**
     * Generate the actual code for the throw.
     */
    protected void generateThrow(Code code, ErrorListener errs)
        {
        code.add(new Throw(expr.generateArgument(code, false, false, false, errs)));
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "throw " + expr.toString() + ';';
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ThrowExpression.class, "expr");
    }
