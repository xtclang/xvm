package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

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
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        switch (generatePlan(ctx))
            {
            case ThenIsFalse:
                return exprElse.testFitMulti(ctx, atypeRequired, errs);

            case ElseIsFalse:
                return exprThen.testFitMulti(ctx, atypeRequired, errs);

            default:
            case Symmetrical:
                return exprThen.testFitMulti(ctx, atypeRequired, errs).combineWith(
                       exprElse.testFitMulti(ctx, atypeRequired, errs));
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
        Usage          use   = Usage.Any;
        Plan           plan;
        switch (plan = generatePlan(ctx))
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
                    if (cElse > 0)
                        {
                        if (exprThen.testFitMulti(ctxThen, atypeElse, null).isFit())
                            {
                            atypeThen = atypeElse;
                            use       = Usage.Else;
                            break;
                            }
                        }

                    if (cThen > 0)
                        {
                        if (exprElse.testFitMulti(ctxElse, atypeThen, null).isFit())
                            {
                            atypeElse = atypeThen;
                            use       = Usage.Then;
                            break;
                            }
                        }

                    // try to resolve formal types
                    TypeConstant[] atypeThenR = resolveConstraints(atypeThen);
                    TypeConstant[] atypeElseR = resolveConstraints(atypeElse);
                    if (atypeElseR != null)
                        {
                        if (exprThen.testFitMulti(ctxThen, atypeElseR, null).isFit())
                            {
                            atypeThen = atypeElse = atypeElseR;
                            use       = Usage.Else;
                            break;
                            }
                        }

                    if (atypeThenR != null)
                        {
                        if (exprElse.testFitMulti(ctxElse, atypeThenR, null).isFit())
                            {
                            atypeElse = atypeThen = atypeThenR;
                            use       = Usage.Then;
                            break;
                            }
                        }

                    // nothing worked; try an intersection of the resolved types
                    if (cThen == 0)
                        {
                        atypeThen = atypeElse;
                        use       = Usage.Else;
                        break;
                        }
                    if (cElse == 0)
                        {
                        atypeElse = atypeThen;
                        use       = Usage.Then;
                        break;
                        }

                    use = Usage.Intersection;

                    if (atypeThenR != null && atypeElseR != null)
                        {
                        TypeConstant[] atypeCommonR = selectCommonTypes(atypeThenR, atypeElseR);
                        if (exprThen.testFitMulti(ctxThen, atypeCommonR, null).isFit() &&
                            exprElse.testFitMulti(ctxElse, atypeCommonR, null).isFit() )
                            {
                            atypeThen = atypeElse = atypeCommonR;
                            break;
                            }
                        }

                    // continue to validation with a regular intersection (which is most likely to fail now)
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

        ctx = ctx.enterFork(true);
        Expression exprNewThen = exprThen.validateMulti(ctx, atypeThen, errs);
        ctx = ctx.exit();

        if (exprNewThen == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprThen = exprNewThen;

            // TODO check if it is short circuiting

            if (atypeThen.length == 0 || use == Usage.Intersection)
                {
                atypeThen = exprNewThen.getTypes();
                }
            }

        ctx = ctx.enterFork(false);
        Expression exprNewElse = exprElse.validateMulti(ctx, atypeElse, errs);
        ctx = ctx.exit();

        if (exprNewElse == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprElse = exprNewElse;

            // TODO check if it is short circuiting

            if (atypeElse.length == 0 || use == Usage.Intersection)
                {
                atypeElse = exprNewElse.getTypes();
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
                    switch (use)
                        {
                        case Any:
                        case Then:
                            atypeResult = atypeThen;
                            break;
                        case Else:
                            atypeResult = atypeElse;
                            break;
                        case Intersection:
                            atypeResult = selectCommonTypes(atypeThen, atypeElse);
                            break;
                        }
                    break;
                    }
                }
            }

        return finishValidations(ctx, atypeRequired, atypeResult, fit, null, errs);
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


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * A helper method to create an array of common types for two arrays.
     *
     * @param atype1  the first type array
     * @param atype2  the second type array
     * @return        an array of common types (of the minimum of the two array sizes)
     */
    private TypeConstant[] selectCommonTypes(TypeConstant[] atype1, TypeConstant[] atype2)
        {
        int            cTypes      = Math.min(atype1.length, atype2.length);
        TypeConstant[] atypeCommon = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            TypeConstant typeThen = atype1[i];
            TypeConstant typeElse = atype2[i];

            TypeConstant typeCommon = Op.selectCommonType(typeThen, typeElse, ErrorListener.BLACKHOLE);
            atypeCommon[i] = typeCommon == null && atype1 != null && typeElse != null
                    ? pool().ensureIntersectionTypeConstant(typeThen, typeElse)
                    : typeCommon;
            }
        return atypeCommon;
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

    private Plan generatePlan(Context ctx)
        {
        if (m_fConditional)
            {
            TypeConstant typeFalse = pool().typeFalse();

            // test "? (true, result) : false" first
            if (exprElse.testFit(ctx, typeFalse, null).isFit())
                {
                return m_plan = Plan.ElseIsFalse;
                }

            // test "? false : (true, result)" next
            if (exprThen.testFit(ctx, typeFalse, null).isFit())
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
                if (!typeTuple.isA(pool.typeTuple()) || typeTuple.getParamsCount() == 0)
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
        StringBuilder sb = new StringBuilder();

        sb.append(cond)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression cond;
    protected Expression exprThen;
    protected Expression exprElse;

    private transient boolean m_fConditional;

    private enum Plan {Symmetrical, ThenIsFalse, ElseIsFalse}
    private transient Plan m_plan = Plan.Symmetrical;

    private enum Usage {Any, Then, Else, Intersection};

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }
