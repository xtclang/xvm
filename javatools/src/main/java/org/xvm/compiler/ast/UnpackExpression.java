package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.MultiExprAST;
import org.xvm.asm.ast.UnpackExprAST;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.I_Get;


/**
 * A tuple un-packing expression. This unpacks the values from the sub-expression tuple.
 */
public class UnpackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnpackExpression(Expression exprTuple, ErrorListener errs)
        {
        super(exprTuple);

        if (exprTuple.isValidated())
            {
            adoptValidation(null, exprTuple, errs);
            }
        }


    // ----- Expression compilation ----------------------------------------------------------------

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
        return isValidated()
                ? getTypes()
                : expr.getImplicitType(ctx).getParamTypesArray();
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs)
        {
        TypeConstant typeTuple = pool().ensureTupleType(atypeRequired);

        return expr.testFit(ctx, typeTuple, fExhaustive, errs);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeConstant typeTuple = pool().ensureTupleType(atypeRequired);

        Expression exprOld = expr;
        Expression exprNew = exprOld.validate(ctx, typeTuple, errs);

        return exprNew == null
                ? null
                : adoptValidation(ctx, expr = exprNew, errs);
        }


    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        expr.generateVoid(ctx, code, errs);
        }

    @Override
    public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk,
                                        boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstants();
            }

        if (expr instanceof TupleExpression exprTuple)
            {
            List<Expression> exprs  = exprTuple.getExpressions();
            int              cExprs = exprs.size();
            Argument[]       aArgs  = new Argument[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                Argument arg = exprs.get(i).generateArgument(ctx, code, false, false, errs);
                aArgs[i] = i == cExprs-1
                        ? arg
                        : ensurePointInTime(code, arg);
                }

            return aArgs;
            }

        Argument argTuple = expr.generateArgument(ctx, code, true, false, errs);

        ConstantPool   pool    = pool();
        TypeConstant[] aTypes  = getTypes();
        int            cValues = aTypes.length;
        Register[]     aRegs   = new Register[cValues];

        for (int i = 0; i < cValues; i++)
            {
            Assignable LValue   = createTempVar(code, aTypes[i], false);
            Register   regValue = LValue.getRegister();
            code.add(new I_Get(argTuple, pool.ensureIntConstant(i), regValue));
            aRegs[i] = regValue;
            }

        return aRegs;
        }

    @Override
    public ExprAST getExprAST()
        {
        if (isConstant())
            {
            // the constant is already unpacked; we need to pass it on
            Constant[] aconst = toConstants();
            int        cVals  = aconst.length;
            ExprAST[]  aAst   = new ExprAST[cVals];
            for (int i = 0; i < cVals; i++)
                {
                aAst[i] = new ConstantExprAST(aconst[i]);
                }
            return new MultiExprAST(aAst);
            }
        return new UnpackExprAST(expr.getExprAST(), getTypes());
        }


    // ----- helpers ------------------------------------------------------------------

    /**
     * Adopt the type information from a validated expression.
     *
     * @param ctx        the compiler context
     * @param exprTuple  the validated expression that yields a Tuple type
     * @param errs       the error listener
     */
    protected Expression adoptValidation(Context ctx, Expression exprTuple, ErrorListener errs)
        {
        TypeConstant typeTuple = exprTuple.getType();
        assert typeTuple.isTuple() && typeTuple.isParamsSpecified();

        TypeConstant[] atypeField = typeTuple.getParamTypesArray();
        Constant[]     aconstVal  = null;

        if (exprTuple.isConstant())
            {
            Constant constTuple = exprTuple.toConstant();
            assert constTuple.getFormat() == Format.Tuple;
            aconstVal = ((ArrayConstant) constTuple).getValue();
            }

        return finishValidations(ctx, null, atypeField, expr.getTypeFit().addUnpack(), aconstVal, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Unpacked: " + getUnderlyingExpression().toString();
        }
    }