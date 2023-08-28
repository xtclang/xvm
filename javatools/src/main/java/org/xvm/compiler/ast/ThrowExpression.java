package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.LanguageAST.ExprAST;
import org.xvm.asm.ast.ThrowExprAST;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A "throw expression" is a commonly non-completing expression that throws an exception.
 */
public class ThrowExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ThrowExpression(Token keyword, Expression expr, Expression message)
        {
        this(keyword, expr, message, null);
        }

    public ThrowExpression(Token keyword, Expression expr, Expression message, Token endToken)
        {
        this.keyword = keyword;
        this.expr    = expr;
        this.message = message;

        if (endToken != null)
            {
            lEndPos = endToken.getEndPosition();
            }
        else if (message != null)
            {
            lEndPos = message.getEndPosition();
            }
        else if (expr != null)
            {
            lEndPos = expr.getEndPosition();
            }
        else
            {
            lEndPos = keyword.getEndPosition();
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the expression for the exception being thrown, or the assert condition (which must
     *         evaluate to false), or null
     */
    public Expression getException()
        {
        return expr;
        }

    /**
     * @return the optional expression for the message to include in a T0D0 or assert exception
     */
    public Expression getMessage()
        {
        return message;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
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

    /**
     * @return true iff this is a T0D0 expression
     */
    @Override
    public boolean isTodo()
        {
        return keyword.getId() == Token.Id.TODO;
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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        return TypeFit.Fit;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive, ErrorListener errs)
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
    public boolean isStandalone()
        {
        return true;
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
        boolean      fValid       = true;
        boolean      fAssert      = false;
        TypeConstant typeRequired = null;
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
                log(errs, Severity.ERROR, Compiler.ASSERT_EXPRESSION_MUST_THROW);
                fValid  = false;
                break;

            case TODO:
                break;

            case THROW:
                typeRequired = ctx.pool().typeException();
                break;

            default:
                log(errs, Severity.FATAL, Compiler.FATAL_ERROR, "(throw keyword=" + keyword + ")");
                fValid = false;
                break;
            }

        if (fValid && expr != null)
            {
            // validate the throw value expressions
            Expression exprNew = expr.validate(ctx, typeRequired, errs);
            if (exprNew != expr)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    expr = exprNew;
                    }
                }

            if (fAssert && fValid && !exprNew.isConstantFalse())
                {
                log(errs, Severity.ERROR, Compiler.ASSERT_EXPRESSION_MUST_THROW);
                fValid = false;
                }
            }

        if (message != null)
            {
            Expression exprNew = message.validate(ctx, pool().typeString(), errs);
            if (exprNew != message)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    message = exprNew;
                    }
                }
            }

        return fValid;
        }

    @Override
    public boolean isRuntimeConstant()
        {
        // sure, you can use this where a constant is required, although it does NOT have a compile
        // time constant available (i.e. the expression still requires code generation)
        return true;
        }

    @Override
    public boolean isAssignable(Context ctx)
        {
        if (isTodo())
            {
            // sure, you can use this where an assignable is required
            return true;
            }

        return super.isAssignable(ctx);
        }

    @Override
    public boolean isCompletable()
        {
        // example to consider: "throw failure?;"
        return isShortCircuiting();
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        if (isTodo())
            {
            // the message of the T0D0 should not be able to prevent the exception from being thrown by
            // short-circuiting
            return false;
            }

        return super.allowsShortCircuit(nodeChild);
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
        int            cArgs  = aTypes.length;
        Register[]     aArgs  = new Register[cArgs];
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
        Argument argEx;
        if (keyword.getId() == Token.Id.THROW)
            {
            assert message == null;
            argEx = expr.generateArgument(ctx, code, true, true, errs);
            }
        else
            {
            assert expr == null;

            // throw new {sThrow}(message, null)
            ConstantPool   pool     = pool();
            ClassConstant  constEx  = computeExceptionClass();
            MethodConstant constNew = constEx.findConstructor(pool.typeString१(), pool.typeException१());
            Argument       argMsg   = message == null
                    ? pool.ensureStringConstant(computeMessage())
                    : message.generateArgument(ctx, code, false, false, errs);

            argEx = code.createRegister(constEx.getType());
            code.add(new New_N(constNew, new Argument[] {argMsg, pool.valNull()}, argEx));
            }
        code.add(new Throw(argEx));
        }

    @Override
    public ExprAST<Constant> getExprAST()
        {
        ExprAST<Constant> astEx;
        ExprAST<Constant> astMsg;
        if (keyword.getId() == Token.Id.THROW)
            {
            assert message == null;

            astEx  = expr.getExprAST();
            astMsg = null;
            }
        else
            {
            assert expr == null;

            ConstantPool  pool    = pool();
            ClassConstant constEx = computeExceptionClass();

            astEx  = new ConstantExprAST<>(constEx.getType(), constEx);
            astMsg = message == null
                    ? new ConstantExprAST<>(pool.typeString(), pool.ensureStringConstant(computeMessage()))
                    : message.getExprAST();
            }
        return new ThrowExprAST<>(getType(), astEx, astMsg);
        }

    private ClassConstant computeExceptionClass()
        {
        String sThrow;
        switch (keyword.getId())
            {
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
            case TODO:
                sThrow = "UnsupportedOperation";
                break;

            default:
                throw new IllegalStateException("keyword="+keyword);
            }
        return pool().ensureEcstasyClassConstant(sThrow);
        }

    private String computeMessage()
        {
        return keyword.getId() == Token.Id.TODO ? "TODO" : "Assertion failed";
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (isTodo() && message != null)
            {
            // the message contains "T0D0"
            sb.append(message);
            }
        else
            {
            sb.append(keyword);

            if (expr != null)
                {
                sb.append(' ')
                  .append(expr);
                }

            if (message != null)
                {
                sb.append(" as ")
                  .append(message);
                }
            }

        sb.append(';');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression expr;
    protected Expression message;
    private final long   lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ThrowExpression.class, "expr", "message");
    }