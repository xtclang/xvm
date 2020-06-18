package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


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
        TypeConstant typeRequired;
        boolean      fAssert      = false;
        switch (keyword.getId())
            {
            case ASSERT:
            case ASSERT_ARG:
            case ASSERT_BOUNDS:
            case ASSERT_TODO:
                typeRequired = ctx.pool().typeBoolean();
                fAssert = true;
                break;

            case ASSERT_RND:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                // throw requires an exception, but T0D0 and various asserts do not; make sure that
                // the asserts are guaranteed to fail, since they do not & can not produce a value
                log(errs, Severity.ERROR, Compiler.ASSERT_EXPRESSION_MUST_THROW, null);
                return false;

            case THROW:
                typeRequired = ctx.pool().typeException();
                break;

            default:
                log(errs, Severity.FATAL, Compiler.FATAL_ERROR, "(throw keyword=" + keyword + ")");
                return false;
            }

        if (expr != null)
            {
            // validate the throw value expressions
            Expression exprNew = expr.validate(ctx, typeRequired, errs);
            if (exprNew != expr)
                {
                if (exprNew == null)
                    {
                    return false;
                    }
                expr = exprNew;
                }

            if (fAssert && !exprNew.isConstantFalse())
                {
                log(errs, Severity.ERROR, Compiler.ASSERT_EXPRESSION_MUST_THROW, null);
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
        String sThrow;
        switch (keyword.getId())
            {
            case THROW:
                Argument arg = expr.generateArgument(ctx, code, true, true, errs);
                code.add(new Throw(arg));
                return;

            case ASSERT:
                sThrow = "IllegalState";
                break;

            case ASSERT_ARG:
                sThrow = "IllegalArgument";
                break;

            case ASSERT_BOUNDS:
                sThrow = "OutOfBounds";
                break;

            case ASSERT_TODO:
                sThrow = "UnsupportedOperation";
                break;

            default:
                throw new IllegalStateException("keyword="+keyword);
            }

        ConstantPool   pool   = ctx.pool();
        MethodConstant constr = AssertStatement.findExceptionConstructor(pool, sThrow, errs);
        code.add(new Assert(pool.valFalse(), constr));
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
