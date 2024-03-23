package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.TernaryExprAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_N;


/**
 * A ternary expression is the "a ? b : c" expression.
 */
public class TernaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TernaryExpression(Expression cond, Expression exprThen, Expression exprElse)
        {
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return cond.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprElse.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    /**
     * Mark this ternary as "possibly" asymmetrical - returning conditional "false" on some branch.
     *
     * This method must be called *before* validation or testFit.
     */
    public void markConditional()
        {
        m_fConditional = true;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean allowsConditional(Expression exprChild)
        {
        return getParent().allowsConditional(this) &&
                (exprChild == exprThen || exprChild == exprElse);
        }

    @Override
    protected boolean hasSingleValueImpl()
        {
        return false;
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        TypeConstant[] atypeThen = exprThen.getImplicitTypes(ctx);
        TypeConstant[] atypeElse = exprElse.getImplicitTypes(ctx);

        int c = atypeThen.length;
        if (c != atypeElse.length)
            {
            return TypeConstant.NO_TYPES;
            }

        return selectCommonTypes(atypeThen, atypeElse);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs)
        {
        switch (generatePlan(ctx, fExhaustive))
            {
            case ThenIsFalse:
                return exprElse.testFitMulti(ctx, atypeRequired, fExhaustive, errs);

            case ElseIsFalse:
                return exprThen.testFitMulti(ctx, atypeRequired, fExhaustive, errs);

            case Symmetrical:
                {
                TypeFit fitThen = exprThen.testFitMulti(ctx, atypeRequired, fExhaustive, errs);
                TypeFit fitElse = exprElse.testFitMulti(ctx, atypeRequired, fExhaustive, errs);
                if (fitThen.isFit() && fitElse.isFit())
                    {
                    return fitThen.combineWith(fitElse);
                    }

                if ((fitThen.isFit() || fitElse.isFit()) && fExhaustive)
                    {
                    // only one of the branches fits; there's a possibility that a type inference
                    // introduced by the condition expression will make both fit; since the caller
                    // relies on an "exhaustive" check, let's spare no effort
                    return testFitMultiExhaustive(ctx, atypeRequired, errs);
                    }
                return TypeFit.NoFit;
                }

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeFit      fit  = TypeFit.Fit;
        ConstantPool pool = pool();

        ctx = ctx.enterIf();

        Expression exprNewCond = cond.validate(ctx, pool.typeBoolean(), errs);
        if (exprNewCond == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            cond = exprNewCond;
            // TODO check if it is short circuiting
            }

        TypeConstant[] atypeThen, atypeElse;
        Usage          use = null;
        Plan           plan;
        switch (plan = generatePlan(ctx, false))
            {
            case ThenIsFalse:
                atypeThen = new TypeConstant[] {pool.typeFalse()};
                atypeElse = atypeRequired == null ? TypeConstant.NO_TYPES : atypeRequired;
                break;

            case ElseIsFalse:
                atypeThen = atypeRequired == null ? TypeConstant.NO_TYPES : atypeRequired;
                atypeElse = new TypeConstant[] {pool.typeFalse()};
                break;

            default:
            case Symmetrical:
                {
                if (atypeRequired != null && atypeRequired.length > 0)
                    {
                    use       = Usage.Required;
                    atypeThen = atypeElse = atypeRequired;
                    break;
                    }

                Context ctxThen = ctx.enterFork(true);
                Context ctxElse = ctx.enterFork(false);

                try
                    {
                    atypeThen = exprThen.getImplicitTypes(ctxThen);
                    atypeElse = exprElse.getImplicitTypes(ctxElse);

                    int cThen = atypeThen.length;
                    int cElse = atypeElse.length;

                    // try to figure out which side is more flexible
                    TypeFit fitThen = cElse > 0
                            ? exprThen.testFitMulti(ctxThen, atypeElse, false, null)
                            : TypeFit.NoFit;

                    TypeFit fitElse = cThen > 0
                            ? exprElse.testFitMulti(ctxElse, atypeThen, false, null)
                            : TypeFit.NoFit;

                    use = computeUsage(fitThen, fitElse);
                    if (use != null)
                        {
                        break;
                        }

                    // try to resolve formal types
                    TypeConstant[] atypeThenR = resolveConstraints(atypeThen);
                    TypeConstant[] atypeElseR = resolveConstraints(atypeElse);
                    if (atypeElseR != null)
                        {
                        fitThen = exprThen.testFitMulti(ctxThen, atypeElseR, false, null);
                        }

                    if (atypeThenR != null)
                        {
                        fitElse = exprElse.testFitMulti(ctxElse, atypeThenR, false, null);
                        }

                    use = computeUsage(fitThen, fitElse);
                    if (use != null)
                        {
                        if (use == Usage.Then || use == Usage.Any)
                            {
                            atypeThen = atypeThenR;
                            }
                        if (use == Usage.Else || use == Usage.Any)
                            {
                            atypeElse = atypeElseR;
                            }
                        break;
                        }

                    // nothing worked; try a union of the resolved types
                    if (cThen == 0)
                        {
                        use = Usage.Else;
                        break;
                        }
                    if (cElse == 0)
                        {
                        use = Usage.Then;
                        break;
                        }

                    use = Usage.Union;

                    if (atypeThenR != null && atypeElseR != null)
                        {
                        TypeConstant[] atypeCommonR = selectCommonTypes(atypeThenR, atypeElseR);
                        if (exprThen.testFitMulti(ctxThen, atypeCommonR, false, null).isFit() &&
                            exprElse.testFitMulti(ctxElse, atypeCommonR, false, null).isFit() )
                            {
                            atypeThen = atypeElse = atypeCommonR;
                            break;
                            }
                        }

                    // continue to validation with a regular union (which is most likely to fail now)
                    atypeThen = atypeElse = selectCommonTypes(atypeThen, atypeElse);
                    break;
                    }
                finally
                    {
                    ctxThen.discard();
                    ctxElse.discard();
                    }
                }
            }

        if (use == Usage.Any)
            {
            // even though both types fit, choose "more" parameterized one if possible
            for (int i = 0, c = Math.min(atypeThen.length, atypeElse.length); i < c; i++)
                {
                TypeConstant typeT = atypeThen[i];
                TypeConstant typeE = atypeElse[i];
                int          cT    = typeT.getParamsCount();
                int          cE    = typeE.getParamsCount();

                if (cT != cE)
                    {
                    use = cT > cE ? Usage.Then : Usage.Else;
                    break;
                    }
                }
            }

        TypeConstant[] atypeThenV = null;
        TypeConstant[] atypeElseV = null;

        ctx = ctx.enterFork(true);
        Expression exprNewThen =
                exprThen.validateMulti(ctx, use == Usage.Else ? atypeElse : atypeThen, errs);
        ctx = ctx.exit();

        if (exprNewThen == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprThen = exprNewThen;

            // TODO check if it is short circuiting

            atypeThenV = exprNewThen.getTypes();
            if (isSpecified(atypeThen) || use == Usage.Union)
                {
                atypeThen = atypeThenV;
                }
            }

        ctx = ctx.enterFork(false);
        Expression exprNewElse =
                exprElse.validateMulti(ctx, use == Usage.Then ? atypeThen : atypeElse, errs);
        ctx = ctx.exit();

        if (exprNewElse == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprElse = exprNewElse;

            // TODO check if it is short circuiting

            atypeElseV = exprNewElse.getTypes();
            if (isSpecified(atypeElse) || use == Usage.Union)
                {
                atypeElse = atypeElseV;
                }
            }

        ctx.exit();

        TypeConstant[] atypeResult = TypeConstant.NO_TYPES;
        if (fit.isFit())
            {
            if (exprNewCond.isConstant())
                {
                return exprNewCond.toConstant().equals(pool.valTrue())
                        ? replaceThisWith(exprNewThen)
                        : replaceThisWith(exprNewElse);
                }

            switch (plan)
                {
                case ThenIsFalse:
                    atypeResult = ensureConditionalType(pool, atypeElse);
                    break;

                case ElseIsFalse:
                    atypeResult = ensureConditionalType(pool, atypeThen);
                    break;

                default:
                case Symmetrical:
                    {
                    atypeResult = switch (use)
                        {
                        case Required -> selectWiderTypes(atypeThenV, atypeElseV, atypeRequired);
                        case Any      -> selectNarrowerTypes(atypeThen, atypeElse);
                        case Then     -> atypeThen;
                        case Else     -> atypeElse;
                        case Union    -> selectCommonTypes(atypeThen, atypeElse);
                        };
                    break;
                    }
                }
            }

        return finishValidations(ctx, atypeRequired, atypeResult, fit, null, errs);
        }

    private boolean isSpecified(TypeConstant[] atype)
        {
        if (atype == null)
            {
            return false;
            }
        for (TypeConstant type : atype)
            {
            if (!type.equals(pool().typeObject()))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean isAssignable(Context ctx)
        {
        return exprThen.isAssignable(ctx) && exprElse.isAssignable(ctx);
        }

    @Override
    public boolean isCompletable()
        {
        return cond.isCompletable() && (exprThen.isCompletable() || exprElse.isCompletable());
        }

    @Override
    public boolean isShortCircuiting()
        {
        return cond.isShortCircuiting() || exprThen.isShortCircuiting() || exprElse.isShortCircuiting();
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        Label labelElse = new Label("else");
        Label labelEnd  = new Label("end");

        cond.generateConditionalJump(ctx, code, labelElse, false, errs);
        exprThen.generateAssignments(ctx, code, aLVal, errs);
        code.add(new Jump(labelEnd));
        code.add(labelElse);
        exprElse.generateAssignments(ctx, code, aLVal, errs);
        code.add(labelEnd);
        }

    /**
     * Custom logic for conditional return.
     *
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param errs  the error listener to log any errors to
     */
    public void generateConditionalReturn(Context ctx, Code code, ErrorListener errs)
        {
        // Note: it's a responsibility of the conditional return to *not* return anything else
        //       if the value at index 0 is "False"
        Label labelElse = new Label("else");
        switch (m_plan)
            {
            case ThenIsFalse:
                {
                boolean fCheck = !exprElse.isConditionalResult();

                cond.generateConditionalJump(ctx, code, labelElse, true, errs);

                Argument[] aArg = exprElse.generateArguments(ctx, code, true, !fCheck, errs);

                if (fCheck)
                    {
                    addTrueCheck(code, aArg[0], labelElse);
                    }
                code.add(new Return_N(aArg));
                code.add(labelElse);
                code.add(new Return_1(pool().valFalse()));
                break;
                }

            case ElseIsFalse:
                {
                boolean fCheck = !exprThen.isConditionalResult();

                cond.generateConditionalJump(ctx, code, labelElse, false, errs);

                Argument[] aArg = exprThen.generateArguments(ctx, code, true, !fCheck, errs);

                if (fCheck)
                    {
                    addTrueCheck(code, aArg[0], labelElse);
                    }
                code.add(new Return_N(aArg));
                code.add(labelElse);
                code.add(new Return_1(pool().valFalse()));
                break;
                }

            default:
            case Symmetrical:
                {
                boolean fCheckThen = !exprThen.isConditionalResult();
                boolean fCheckElse = !exprElse.isConditionalResult();
                Label   labelFalse = fCheckThen || fCheckElse ? new Label("false") : null;

                cond.generateConditionalJump(ctx, code, labelElse, false, errs);

                Argument[] aArgThen = exprThen.generateArguments(ctx, code, true, !fCheckThen, errs);

                if (fCheckThen)
                    {
                    fCheckThen = addTrueCheck(code, aArgThen[0], labelFalse);
                    }
                code.add(new Return_N(aArgThen));
                code.add(labelElse);

                Argument[] aArgElse = exprElse.generateArguments(ctx, code, true, !fCheckElse, errs);

                if (fCheckElse)
                    {
                    fCheckElse = addTrueCheck(code, aArgElse[0], labelFalse);
                    }
                code.add(new Return_N(aArgElse));

                if (fCheckThen || fCheckElse)
                    {
                    code.add(labelFalse);
                    code.add(new Return_1(pool().valFalse()));
                    }
                break;
                }
            }
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        return new TernaryExprAST(cond.getExprAST(ctx),
                exprThen.getExprAST(ctx), exprElse.getExprAST(ctx), getTypes());
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the compilation plan
     */
    public Plan getPlan()
        {
        return m_plan;
        }

    /**
     * Compute the usage plan, prioritizing a direct fit over a conversion fit.
     *
     * @param fitThen  a fit for "then" using "else" implicit type
     * @param fitElse  a fit for "else" using "then" implicit type
     */
    private Usage computeUsage(TypeFit fitThen, TypeFit fitElse)
        {
        if (fitThen.isFit())
            {
            if (fitElse.isFit())
                {
                if (fitThen == TypeFit.Fit)
                    {
                    return fitElse == TypeFit.Fit
                            ? Usage.Any
                            : Usage.Else;
                    }
                else
                    {
                    return Usage.Then;
                    }
                }
            else
                {
                return Usage.Else;
                }
            }
        else if (fitElse.isFit())
            {
            return Usage.Then;
            }
        return null;
        }

    /**
     * A helper method to create an array of narrower types for two arrays.
     *
     * @param atypeThen  the first type array
     * @param atypeElse  the second type array
     *
     * @return an array of narrower types
     */
    private TypeConstant[] selectNarrowerTypes(TypeConstant[] atypeThen, TypeConstant[] atypeElse)
        {
        int cThen = atypeThen.length;
        int cElse = atypeElse.length;

        if (cThen > cElse)
            {
            return atypeThen;
            }
        if (cElse > cThen)
            {
            return atypeElse;
            }
        for (int i = 0; i < cThen; i++)
            {
            TypeConstant typeThen = atypeThen[i];
            TypeConstant typeElse = atypeElse[i];

            if (!typeThen.isAssignableTo(typeElse))
                {
                return atypeElse;
                }
            if (!typeElse.isAssignableTo(typeThen))
                {
                return atypeThen;
                }
            }
        return atypeThen;
        }

    /**
     * A helper method to create an array of wider types for two arrays, but no wider than the third.
     *
     * @param atypeThen  the first type array
     * @param atypeElse  the second type array
     * @param atypeElse  the required type array
     *
     * @return an array of wider types
     */
    private TypeConstant[] selectWiderTypes(TypeConstant[] atypeThen, TypeConstant[] atypeElse,
                                            TypeConstant[] atypeRequired)
        {
        int cRequired = atypeRequired.length;
        int cThen     = atypeThen.length;
        int cElse     = atypeElse.length;

        if (cRequired == 1 && cThen == 1 && cElse == 1)
            {
            // most common case
            TypeConstant typeThen = atypeThen[0];
            TypeConstant typeElse = atypeElse[0];

            return typeThen.isAssignableTo(typeElse) ? atypeElse
                 : typeElse.isAssignableTo(typeThen) ? atypeThen
                                                     : atypeRequired;
            }
        else
            {
            TypeConstant[] atypeResult = atypeRequired.clone();

            for (int i = 0; i < cRequired; i++)
                {
                TypeConstant typeReq  = atypeRequired[i];
                TypeConstant typeThen = i < cThen ? atypeThen[i] : typeReq;
                TypeConstant typeElse = i < cElse ? atypeElse[i] : typeReq;

                if (typeThen.isAssignableTo(typeElse))
                    {
                    atypeResult[i] = typeElse;
                    }
                else if (typeElse.isAssignableTo(typeThen))
                    {
                    atypeResult[i] = typeThen;
                    }
                }
            return atypeResult;
            }
        }

    private TypeConstant[] resolveConstraints(TypeConstant[] atype)
        {
        int            cTypes = atype.length;
        TypeConstant[] atypeR = null;

        for (int i = 0; i < cTypes; i++)
            {
            TypeConstant type = atype[i];

            if (type.containsFormalType(true))
                {
                if (atypeR == null)
                    {
                    atypeR = new TypeConstant[cTypes];
                    }
                atypeR[i] = type.resolveConstraints();
                }

            }
        return atypeR;
        }

    private Plan generatePlan(Context ctx, boolean fExhaustive)
        {
        if (m_fConditional)
            {
            TypeConstant typeFalse = pool().typeFalse();

            // test "? (true, result) : false" first
            if (exprElse.testFit(ctx, typeFalse, fExhaustive, null).isFit())
                {
                return m_plan = Plan.ElseIsFalse;
                }

            // test "? false : (true, result)" next
            if (exprThen.testFit(ctx, typeFalse, fExhaustive, null).isFit())
                {
                return m_plan = Plan.ThenIsFalse;
                }
            }
        return m_plan = Plan.Symmetrical;
        }

    private static TypeConstant[] ensureConditionalType(ConstantPool pool, TypeConstant[] atypeCond)
        {
        switch (atypeCond.length)
            {
            case 0:
                return atypeCond;

            case 1:
                {
                TypeConstant typeTuple = atypeCond[0];
                if (!typeTuple.isTuple() || typeTuple.getParamsCount() == 0)
                    {
                    return TypeConstant.NO_TYPES;
                    }

                TypeConstant[] atypeResult = typeTuple.getParamTypesArray();
                return atypeResult[0].isA(pool.typeBoolean())
                         ? atypeResult
                         : TypeConstant.NO_TYPES;
                }

            default:
                return atypeCond[0].isA(pool.typeBoolean())
                         ? atypeCond
                         : TypeConstant.NO_TYPES;
            }
        }

    /**
     * Add a check for the "true" value for a conditional return;
     *
     * @return true if the check has been added; false if the check is not necessary
     */
    private boolean addTrueCheck(Code code, Argument arg, Label label)
        {
        if (arg.equals(pool().valTrue()))
            {
            return false;
            }

        code.add(new JumpFalse(arg, label));
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return cond + " ? " + exprThen + " : " + exprElse;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression cond;
    protected Expression exprThen;
    protected Expression exprElse;

    private transient boolean m_fConditional;

    public enum Plan {Symmetrical, ThenIsFalse, ElseIsFalse}
    private transient Plan m_plan = Plan.Symmetrical;

    private enum Usage {Required, Any, Then, Else, Union}

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }