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

        return selectCommonTypes(atypeThen, atypeElse, true, ErrorListener.BLACKHOLE);
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
        Plan plan;
        switch (plan = generatePlan(ctx))
            {
            case ThenIsFalse:
                atypeThen = new TypeConstant[] {pool.typeFalse()};
                atypeElse = atypeRequired;
                break;

            case ElseIsFalse:
                atypeThen = atypeRequired;
                atypeElse = new TypeConstant[] {pool.typeFalse()};
                break;

            default:
            case Symmetrical:
                atypeThen = atypeElse = atypeRequired == null
                        ? getImplicitTypes(ctx)
                        : atypeRequired;
                break;
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
            exprThen  = exprNewThen;
            atypeThen = exprNewThen.getTypes();
            // TODO check if it is short circuiting

            if (atypeElse.length == 0)
                {
                atypeElse = selectCommonTypes(atypeThen, TypeConstant.NO_TYPES, false, errs);
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
            exprElse  = exprNewElse;
            atypeElse = exprNewElse.getTypes();
            // TODO check if it is short circuiting
            }

        ctx.exit();

        if (fit.isFit() && exprNewCond.isConstant())
            {
            return exprNewCond.toConstant().equals(pool.valTrue())
                    ? replaceThisWith(exprNewThen)
                    : replaceThisWith(exprNewElse);
            }

        TypeConstant[] atypeResult;
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
                // we know by now that both typeThen and typeElse are assignable to
                // typeRequired (if not null). Let's try to find a common type for them
                // that is narrower than the required type
                atypeResult = selectCommonTypes(atypeThen, atypeElse, false, errs);
                for (int i = 0, c = atypeResult.length; i < c; i++)
                    {
                    TypeConstant typeResult = atypeResult[i];

                    if (typeResult == null)
                        {
                        TypeConstant typeThen = atypeThen[i];
                        TypeConstant typeElse = atypeElse[i];

                        // try to resolve formal types
                        boolean fFormalThen = typeThen.containsFormalType();
                        boolean fFormalElse = typeElse.containsFormalType();

                        if (fFormalThen || fFormalElse)
                            {
                            if (fFormalThen)
                                {
                                typeThen = typeThen.resolveConstraints(pool);
                                }
                            if (fFormalElse)
                                {
                                typeElse = typeElse.resolveConstraints(pool);
                                }
                            // since it's guaranteed that neither type contains formal, we can recurse
                            typeResult = Op.selectCommonType(typeThen, typeElse, errs);
                            }

                        if (typeResult == null)
                            {
                            typeResult = pool.ensureIntersectionTypeConstant(typeThen, typeElse);
                            if (atypeRequired.length > i && !typeResult.isA(atypeRequired[i]))
                                {
                                // leave as is
                                typeResult = atypeRequired[i];
                                }
                            }
                        atypeResult[i] = typeResult;
                        }
                    }
                break;
            }

        return finishValidations(atypeRequired, atypeResult, fit, null, errs);
        }

    @Override
    public boolean isAssignable()
        {
        return exprThen.isAssignable() && exprElse.isAssignable();
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
                    code.add(new JumpFalse(aArg[0], labelElse));
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
                    code.add(new JumpFalse(aArg[0], labelElse));
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
                    code.add(new JumpFalse(aArgThen[0], labelFalse));
                    }
                code.add(new Return_N(aArgThen));
                code.add(labelElse);

                Argument[] aArgElse = exprElse.generateArguments(ctx, code, true, !fCheckElse, errs);

                if (fCheckElse)
                    {
                    code.add(new JumpFalse(aArgElse[0], labelFalse));
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
     * @param atype1      the first type array
     * @param atype2      the second type array
     * @param fIntersect  if true, in an absence of common type generate an intersection type
     * @param errs        the error listener to log any errors to
     * @return            an array of common types (of the minimum of the two array sizes)
     */
    private TypeConstant[] selectCommonTypes(TypeConstant[] atype1, TypeConstant[] atype2,
                                             boolean fIntersect, ErrorListener errs)
        {
        int            cTypes      = Math.min(atype1.length, atype2.length);
        TypeConstant[] atypeCommon = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            TypeConstant typeThen = atype1[i];
            TypeConstant typeElse = atype2[i];

            TypeConstant typeCommon = Op.selectCommonType(typeThen, typeElse, errs);
            atypeCommon[i] = typeCommon == null && atype1 != null && typeElse != null && fIntersect
                    ? pool().ensureIntersectionTypeConstant(typeThen, typeElse)
                    : typeCommon;
            }
        return atypeCommon;
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

    enum Plan {Symmetrical, ThenIsFalse, ElseIsFalse}
    private transient Plan m_plan = Plan.Symmetrical;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }
