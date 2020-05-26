package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.New_0;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Token;


/**
 * A "throw expression" is a non-completing expression that throws an exception.
 *
 * <p/>TODO serious issues with types, because the expression cannot complete, yet it factors into
 *          type analysis. for example, "if (x?.y : assert)" does not evaluate to Boolean
 *      -> parent expression should always check isCompleteable() before factoring in type info?
 *      -> need to create a "subtype of all types" pseudo-type for compile-time that non-completing
 *         expressions can report as their type (that has an isA() implementation that returns true)
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
        return expr == null ? keyword.getEndPosition() : expr.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return null;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return TypeConstant.NO_TYPES;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        return TypeFit.Fit;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        return TypeFit.Fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = validateThrow(ctx, errs);
        TypeFit fit    = fValid ? TypeFit.Fit : TypeFit.NoFit;

        // it doesn't really matter what type we choose here, but finishValidation() requires one
        TypeConstant typeActual = typeRequired == null ? pool().typeException() : typeRequired;
        ctx.setReachable(false);
        return finishValidation(ctx, typeRequired, typeActual, fit, null, errs);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        boolean        fValid      = validateThrow(ctx, errs);
        TypeConstant[] atypeActual = atypeRequired == null ? TypeConstant.NO_TYPES : atypeRequired;
        TypeFit        fit         = fValid ? TypeFit.Fit : TypeFit.NoFit;
        ctx.setReachable(false);
        return finishValidations(ctx, atypeRequired, atypeActual, fit, null, errs);
        }

    @Override
    public boolean isAssignableTo(TypeConstant typeThat)
        {
        // sure, this expression can be one of those, whatever that is
        return true;
        }

    @Override
    public boolean isTypeBoolean()
        {
        // sure, whatever you want
        return true;
        }

    /**
     * @return true iff validation is successful
     */
    protected boolean validateThrow(Context ctx, ErrorListener errs)
        {
        if (expr != null)
            {
            // validate the throw value expressions
            Expression exprNew = expr.validate(ctx, pool().typeException(), errs);
            if (exprNew != expr)
                {
                if (exprNew == null)
                    {
                    return false;
                    }
                expr = exprNew;
                }
            }
        return true;
        }

    @Override
    public boolean isRuntimeConstant()
        {
        // sure, you can use this where a constant is required, although it does NOT have a compile
        // time constant available (i.e. the expression still requires code generation)
        return true;
        }

    @Override
    public boolean isCompletable()
        {
        return false;
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr != null && expr.isShortCircuiting();
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        generateThrow(ctx, code, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        generateThrow(ctx, code, errs);
        return generateBlackHole(getValueCount() == 0 ? pool().typeObject() : getType());
        }

    @Override
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        generateThrow(ctx, code, errs);

        TypeConstant[] aTypes = getTypes();
        int cArgs = aTypes.length;
        Register[] aArgs = new Register[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = generateBlackHole(aTypes[i]);
            }
        return aArgs;
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        generateThrow(ctx, code, errs);
        }

    /**
     * Generate the actual code for the throw.
     *
     * @param ctx   the statement context
     * @param code  the code block
     * @param errs  the error list to log any errors to
     */
    protected void generateThrow(Context ctx, Code code, ErrorListener errs)
        {
        Argument arg;
        if (expr == null)
            {
            ConstantPool   pool = pool();
            TypeConstant   type = pool.ensureEcstasyTypeConstant("Assertion");
            TypeInfo       info = type.ensureTypeInfo(errs);
            MethodConstant id   = info.findConstructor(null, null);
                           arg  = createRegister(type, true);

            code.add(new New_0(id, arg));
            }
        else
            {
            arg = expr.generateArgument(ctx, code, true, true, errs);
            }

        code.add(new Throw(arg));
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return keyword + (expr == null ? "" : " " + expr.toString()) + ';';
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
