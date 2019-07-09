package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_T;


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
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant typeThen = exprThen.getImplicitType(ctx);
        TypeConstant typeElse = exprElse.getImplicitType(ctx);

        TypeConstant typeCommon = Op.selectCommonType(typeThen, typeElse, ErrorListener.BLACKHOLE);
        return typeCommon == null && typeThen != null && typeElse != null
                ? pool().ensureIntersectionTypeConstant(typeThen, typeElse)
                : typeCommon;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        switch (generatePlan(ctx))
            {
            case ThenIsFalse:
                return exprElse.testFit(ctx, typeRequired, errs);

            case ElseIsFalse:
                return exprThen.testFit(ctx, typeRequired, errs);

            default:
            case Symmetrical:
                return exprThen.testFit(ctx, typeRequired, errs).combineWith(
                       exprElse.testFit(ctx, typeRequired, errs));
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
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

        TypeConstant typeThen, typeElse;
        Plan plan;
        switch (plan = generatePlan(ctx))
            {
            case ThenIsFalse:
                typeThen = pool.typeFalse();
                typeElse = typeRequired;
                break;

            case ElseIsFalse:
                typeThen = typeRequired;
                typeElse = pool.typeFalse();
                break;

            default:
            case Symmetrical:
                typeThen = typeElse = typeRequired == null
                        ? getImplicitType(ctx)
                        : typeRequired;
                break;
            }

        ctx = ctx.enterFork(true);
        Expression exprNewThen = exprThen.validate(ctx, typeThen, errs);
        ctx = ctx.exit();

        if (exprNewThen == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprThen = exprNewThen;
            typeThen = exprNewThen.getType();
            // TODO check if it is short circuiting

            if (typeElse == null)
                {
                typeElse = Op.selectCommonType(typeThen, null, errs);
                }
            }

        ctx = ctx.enterFork(false);
        Expression exprNewElse = exprElse.validate(ctx, typeElse, errs);
        ctx = ctx.exit();

        if (exprNewElse == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprElse = exprNewElse;
            typeElse = exprNewElse.getType();
            // TODO check if it is short circuiting
            }

        ctx.exit();

        if (fit.isFit() && exprNewCond.isConstant())
            {
            return exprNewCond.toConstant().equals(pool.valTrue())
                    ? replaceThisWith(exprNewThen)
                    : replaceThisWith(exprNewElse);
            }

        TypeConstant typeResult;
        switch (plan)
            {
            case ThenIsFalse:
                typeResult = ensureConditionalType(pool, typeElse);
                break;

            case ElseIsFalse:
                typeResult = ensureConditionalType(pool, typeThen);
                break;

            default:
            case Symmetrical:
                // we know by now that both typeThen and typeElse are assignable to
                // typeRequired (if not null). Let's try to find a common type for them
                // that is narrower than the required type
                typeResult = Op.selectCommonType(typeThen, typeElse, errs);
                if (typeResult == null)
                    {
                    typeResult = pool.ensureIntersectionTypeConstant(typeThen, typeElse);
                    if (!typeResult.isA(typeRequired))
                        {
                        // leave as is (but not null)
                        typeResult = typeRequired == null
                                ? pool.typeObject()
                                : typeRequired;
                        }
                    }
                break;
            }

        return finishValidation(typeRequired, typeResult, fit, null, errs);
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
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        Label labelElse = new Label("else");
        Label labelEnd  = new Label("end");

        cond.generateConditionalJump(ctx, code, labelElse, false, errs);
        exprThen.generateAssignment(ctx, code, LVal, errs);
        code.add(new Jump(labelEnd));
        code.add(labelElse);
        exprElse.generateAssignment(ctx, code, LVal, errs);
        code.add(labelEnd);
        }

    /**
     * Custom logic for conditional return.
     *
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param errs  the error list to log any errors to
     */
    public void generateConditionalReturn(Context ctx, Code code, ErrorListener errs)
        {
        switch (m_plan)
            {
            case ThenIsFalse:
                {
                Label labelElse = new Label("else");

                cond.generateConditionalJump(ctx, code, labelElse, true, errs);
                Argument arg = exprElse.generateArgument(ctx, code, true, true, errs);
                code.add(new Return_T(arg));
                code.add(labelElse);
                code.add(new Return_1(pool().valFalse()));
                break;
                }

            case ElseIsFalse:
                {
                Label labelElse = new Label("else");

                cond.generateConditionalJump(ctx, code, labelElse, false, errs);
                Argument arg = exprThen.generateArgument(ctx, code, true, true, errs);
                code.add(new Return_T(arg));
                code.add(labelElse);
                code.add(new Return_1(pool().valFalse()));
                break;
                }

            default:
            case Symmetrical:
                {
                Argument arg = generateArgument(ctx, code, true, true, errs);
                code.add(new Return_T(arg));
                break;
                }
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

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

    private static TypeConstant ensureConditionalType(ConstantPool pool, TypeConstant typeTuple)
        {
        assert typeTuple.isA(pool.typeTuple()) && typeTuple.getParamsCount() > 0;

        TypeConstant[] atypeResult = typeTuple.getParamTypesArray();
        if (atypeResult[0].equals(pool.typeBoolean()))
            {
            return typeTuple;
            }

        atypeResult    = atypeResult.clone();
        atypeResult[0] = pool.typeBoolean();

        return pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeResult);
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
