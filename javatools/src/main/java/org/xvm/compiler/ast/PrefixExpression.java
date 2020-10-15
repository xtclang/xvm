package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Set;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * Generic expression for something that follows the pattern "operator expression".
 */
public class PrefixExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PrefixExpression(Token operator, Expression expr)
        {
        this.operator = operator;
        this.expr     = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return operator.getId() == Token.Id.NOT
                ? expr.validateCondition(errs)
                : super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        return operator.getId() == Token.Id.NOT
                ? expr.toConditionalConstant().negate()
                : super.toConditionalConstant();
        }

    @Override
    public long getStartPosition()
        {
        return operator.getStartPosition();
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


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Validation helper for UnaryXXX expressions.
     *
     * @param ctx           the compilation context for the statement
     * @param typeRequired  the type that the expression is expected to be able to provide, or null
     *                      if no particular type is expected (which requires the expression to
     *                      settle on a type on its own)
     * @param typeRight     the type of the right expression
     * @param sMethod       the method name of the op
     * @param sOp           the op name
     * @param errs          the error listener to log to
     *
     * @return the TypeConstant of the return type for the best matching op, or null if none could
     *         be chosen
     */
    protected TypeConstant findBestOp(Context ctx, TypeConstant typeRequired,
                                      TypeConstant typeRight, String sMethod, String sOp, ErrorListener errs)
        {
        Expression exprRight = expr.validate(ctx, typeRight, errs);

        if (exprRight != null)
            {
            MethodConstant idBest = null;

            expr      = exprRight;
            typeRight = exprRight.getType();

            TypeInfo            infoRight  = typeRight.ensureTypeInfo(errs);
            Set<MethodConstant> setOps     = infoRight.findOpMethods(sMethod, sOp, 0);
            boolean             fAmbiguous = false;
            for (MethodConstant idMethod : setOps)
                {
                if (typeRequired != null &&
                        !isAssignable(ctx, idMethod.getSignature().getRawReturns()[0], typeRequired))
                    {
                    continue;
                    }

                if (idBest == null)
                    {
                    idBest = idMethod;
                    }
                else
                    {
                    MethodInfo infoMethod = infoRight.getMethodById(idMethod);
                    MethodInfo infoBest   = infoRight.getMethodById(idBest);
                    if (infoMethod.getIdentity().equals(infoBest.getIdentity()))
                        {
                        continue;
                        }

                    SignatureConstant sigNew  = infoMethod.getSignature();
                    SignatureConstant sigBest = infoBest.getSignature();

                    boolean fNewBetter = sigNew.isSubstitutableFor(sigBest, typeRight);
                    boolean fOldBetter = sigBest.isSubstitutableFor(sigNew, typeRight);
                    if (fOldBetter ^ fNewBetter)
                        {
                        if (fNewBetter)
                            {
                            idBest = idMethod;
                            }
                        }
                    else
                        {
                        // note: theoretically could still be one better than either of these two, but
                        // for now, just assume it's an error at this point
                        fAmbiguous = true;
                        break;
                        }
                    }
                }

            if (idBest == null)
                {
                if (fAmbiguous)
                    {
                    log(errs, Severity.ERROR, Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                            operator.getValueText(), typeRight.getValueString());
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR,
                            operator.getValueText(), typeRight.getValueString());
                    }
                }
            else
                {
                return idBest.getSignature().getRawReturns()[0];
                }
            }

        return null;
        }

    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public boolean isTraceworthy()
        {
        return false;
        }

    @Override
    public boolean isCompletable()
        {
        return expr.isCompletable();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        boolean fPre = !(this instanceof SequentialAssignExpression) ||
                        ((SequentialAssignExpression) this).isPre();
        if (fPre)
            {
            sb.append(operator.getId().TEXT);
            }

        if (expr instanceof NameExpression)
            {
            sb.append(expr);
            }
        else
            {
            sb.append('(').append(expr).append(')');
            }

        if (!fPre)
            {
            sb.append(operator.getId().TEXT);
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      operator;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PrefixExpression.class, "expr");
    }
