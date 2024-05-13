package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.IsExprAST;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNType;
import org.xvm.asm.op.JumpType;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

import org.xvm.util.Severity;


/**
 * Expression for "expression is expression" or "expression instanceof type".
 */
public class IsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IsExpression(Expression expr1, Token operator, Expression expr2, Token tokClose)
        {
        super(expr1, operator, expr2);

        lEndPos = tokClose.getEndPosition();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public long getEndPosition()
        {
        return lEndPos;
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
        return new TypeConstant[] {pool().typeBoolean(), expr2.getImplicitType(ctx)};
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeFit fit = TypeFit.Fit;

        Expression exprTarget = expr1.validate(ctx, null, errs);
        if (exprTarget == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1 = exprTarget;
            }

        ConstantPool pool     = pool();
        Expression   exprTest = expr2.validate(ctx, pool.typeType(), errs);
        if (exprTest == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = exprTest;
            }

        boolean        fSingle     = atypeRequired == null || atypeRequired.length <= 1;
        TypeConstant[] atypeActual = new TypeConstant[fSingle ? 1 : 2];
        Constant[]     aconstVal   = null;

        if (!fSingle && !getParent().allowsConditional(this))
            {
            log(errs, Severity.ERROR, Compiler.CONDITIONAL_RETURN_NOT_ALLOWED,
                    "(Boolean, Object) is()");
            return null;
            }

        atypeActual[0] = pool.typeBoolean();

        if (fit.isFit())
            {
            TypeConstant typeTarget = exprTarget.getType();
            TypeConstant typeTest   = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();

            if (typeTarget.isIncompatibleCombo(typeTest))
                {
                log(errs, Severity.ERROR, Compiler.TYPE_MATCHES_NEVER,
                        exprTarget.toString(), typeTarget.getValueString(), typeTest.getValueString());
                return null;
                }

            TypeConstant typeInferred = typeTest;
            CheckMatch:
            if (!typeTest.isFormalType() && exprTest.isConstant())
                {
                if (typeTarget.isTypeOfType())
                    {
                    // the test must be a type unless it's something that any Type is (e.g. Const),
                    // in which case just issue a warning
                    if (!typeTest.isTypeOfType())
                        {
                        if (pool.typeConst().isA(typeTest))
                            {
                            log(errs, Severity.WARNING, Compiler.TYPE_MATCHES_ALWAYS,
                                exprTarget.toString(), pool.typeType().getValueString(),
                                    typeTest.getValueString());
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.NOT_TYPE_OF_TYPE,
                                exprTarget.toString(), typeTest.getValueString());
                            return null;
                            }
                        }
                    if (typeTarget.getParamType(0).isFormalType())
                        {
                        break CheckMatch;
                        }
                    }
                if (typeTarget.isA(typeTest))
                    {
                    log(errs, Severity.WARNING, Compiler.TYPE_MATCHES_ALWAYS,
                        exprTarget.toString(), typeTarget.getValueString(),
                            typeTest.getValueString());
                    }
                }

            if (exprTarget.isConstant())
                {
                if (exprTest.isConstant() && fSingle)
                    {
                    aconstVal = new Constant[] {pool.valOf(typeTarget.isA(typeTest))};
                    }
                else
                    {
                    if (exprTest instanceof NamedTypeExpression exprNameType
                            && !exprNameType.isDynamic() && typeTest.isFormalType())
                        {
                        // if the test is a formal type, we can draw an inference from the target's type;
                        // consider this snippet (Value is a generic type):
                        //      if (Null.is(Value)) { ... }
                        // since Value type is assignable from Null, we can use that knowledge on the
                        // WhenTrue branch (widening the formal type to a union with the target's type)
                        FormalConstant constTest = (FormalConstant) typeTest.getDefiningConstant();
                        ctx.replaceGenericType(constTest, Branch.WhenTrue,
                                typeTest.union(pool, typeTarget).getType());
                        }
                    }
                }
            else if (exprTarget instanceof NameExpression exprName)
                {
                typeInferred = typeTarget.combine(pool, typeTest);

                exprName.narrowType(ctx, Branch.WhenTrue,  typeInferred);
                exprName.narrowType(ctx, Branch.WhenFalse, typeTarget.andNot(pool, typeTest));
                }

            if (!fSingle)
                {
                atypeActual[1] = typeInferred;
                }
            }
        else if (!fSingle)
            {
            // there's an error already, so just pretend that we return what was requested for the
            // conditional type
            atypeActual[1] = atypeRequired[1];
            }

        return finishValidations(ctx, atypeRequired, atypeActual, fit, aconstVal, errs);
        }

    @Override
    public boolean isConditionalResult()
        {
        return true;
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        int cLVals = aLVal.length;
        if (cLVals == 0)
            {
            return;
            }

        if (!aLVal[0].isLocalArgument())
            {
            super.generateAssignments(ctx, code, aLVal, errs);
            return;
            }

        Argument   argCond   = aLVal[0].getLocalArgument();
        Argument   argTarget = expr1.generateArgument(ctx, code, true, cLVals == 1, errs);
        Expression exprTest  = expr2;
        Argument   argType;

        // there is an asymmetry between processing of the static and dynamic exprTest type:
        // - if the test type is static (e.g. "x.is(String)"), the type of exprTest is a
        //     TypeConstant that "isTypeOfType" and we need to extract that underlying type;
        // - in the test type is dynamic (e.g. "Null.is(Serializable))", the argument produced
        //   by the exprTest would be a TypeHandle and the runtime should take care of the rest
        if (exprTest.isConstant())
            {
            argType = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();
            }
        else
            {
            argType = exprTest.generateArgument(ctx, code, false, true, errs);
            if (argType instanceof TypeConstant type)
                {
                argType = type.getParamType(0);
                }
            }

        code.add(new IsType(argTarget, argType, argCond));
        if (cLVals > 1)
            {
            if (argCond.isStack())
                {
                // since we need both to check it and return the value, it cannot be on stack
                Assignable varDupe = createTempVar(code, pool().typeBoolean(), false);
                Register   regDupe = varDupe.getRegister();
                code.add(new Move(argCond, regDupe)); // pop stack into argTest
                code.add(new Move(regDupe, argCond)); // push it back on stack
                argCond = regDupe;
                }

            Label label = new Label("skip_assign");
            code.add(new JumpFalse(argCond, label));
            aLVal[1].assign(argTarget, code, errs);
            code.add(label);
            }
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
        Argument argType   = expr2.getType().getParamType(0).resolveAutoNarrowingBase();
        code.add(fWhenTrue
                ? new JumpType(argTarget, argType, label)
                : new JumpNType(argTarget, argType, label));
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        TypeConstant[] atypeRet = getTypes();
        return new IsExprAST(expr1.getExprAST(ctx), expr2.getExprAST(ctx),
                atypeRet.length == 1 ? null : atypeRet[1]);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return String.valueOf(expr1) + '.' + operator.getId().TEXT + '(' + expr2 + ')';
        }


    // ----- fields --------------------------------------------------------------------------------

    private final long lEndPos;
    }