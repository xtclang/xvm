package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.GP_Div;
import org.xvm.asm.op.GP_Mod;
import org.xvm.asm.op.GP_Mul;
import org.xvm.asm.op.GP_Sub;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * Relational operator expression (with @Op support) for something that follows the pattern
 * "expression operator expression".
 *
 * <ul>
 * <li><tt>BIT_OR:     "|"</tt> - </li>
 * <li><tt>BIT_XOR:    "^"</tt> - </li>
 * <li><tt>BIT_AND:    "&"</tt> - </li>
 * <li><tt>DOTDOT:     ".."</tt> - </li>
 * <li><tt>SHL:        "<<"</tt> - </li>
 * <li><tt>SHR:        ">><tt>"</tt> - </li>
 * <li><tt>USHR:       ">>><tt>"</tt> - </li>
 * <li><tt>ADD:        "+"</tt> - </li>
 * <li><tt>SUB:        "-"</tt> - </li>
 * <li><tt>MUL:        "*"</tt> - </li>
 * <li><tt>DIV:        "/"</tt> - </li>
 * <li><tt>MOD:        "%"</tt> - </li>
 * <li><tt>DIVMOD:     "/%"</tt> - </li>
 * </ul>
 *
 * TODO remove cut&paste:
    switch (operator.getId())
        {
        case BIT_OR:
        case BIT_XOR:
        case BIT_AND:
        case DOTDOT:
        case SHL:
        case SHR:
        case USHR:
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case MOD:
        case DIVMOD:
        }
 */
public class RelOpExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public RelOpExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);

        switch (operator.getId())
            {
            case BIT_OR:
            case BIT_XOR:
            case BIT_AND:
            case DOTDOT:
            case SHL:
            case SHR:
            case USHR:
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case DIVMOD:
                break;

            default:
                throw new IllegalArgumentException("operator: " + operator);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        switch (operator.getId())
            {
            case ADD:
            case BIT_OR:
                return new BiTypeExpression(expr1.toTypeExpression(), operator, expr2.toTypeExpression());

            default:
                return super.toTypeExpression();
            }
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        switch (operator.getId())
            {
            case BIT_AND:
            case BIT_OR:
                return expr1.validateCondition(errs) && expr2.validateCondition(errs);

            default:
                return super.validateCondition(errs);
            }
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        switch (operator.getId())
            {
            case BIT_AND:
                return expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant());

            case BIT_OR:
                return expr1.toConditionalConstant().addOr(expr2.toConditionalConstant());

            default:
                return super.toConditionalConstant();
            }
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
        MethodConstant method = getImplicitMethod(ctx);
        return method == null
                ? null
                : method.getRawReturns()[0];
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        if (operator.getId() == Id.DIVMOD)
            {
            MethodConstant method = getImplicitMethod(ctx);
            return method == null
                    ? null
                    : method.getRawReturns();
            }

        return super.getImplicitTypes(ctx);
        }

    /**
     * @return the method that the op will use implicitly if it is not provided an overriding type
     *         for inference purposes
     */
    protected MethodConstant getImplicitMethod(Context ctx)
        {
        TypeConstant typeLeft = expr1.getImplicitType(ctx);
        if (typeLeft == null)
            {
            // if the type of the left hand expression cannot be determined, then the result of the
            // op cannot be determined
            return null;
            }

        Set<MethodConstant> setOps = typeLeft.ensureTypeInfo().findOpMethods(
                getDefaultMethodName(), getOperatorString(), 1);
        if (setOps.isEmpty())
            {
            // if there are no ops, then a type cannot be determined
            return null;
            }

        // if there is one op method, then assume that is the one
        if (setOps.size() == 1)
            {
            return setOps.iterator().next();
            }

        // multiple ops: use the right hand expression to reduce the potential ops
        TypeConstant typeRight = expr2.getImplicitType(ctx);
        if (typeRight == null)
            {
            return null;
            }

        MethodConstant                         idBest  = null;
        Map<SignatureConstant, MethodConstant> mapBest = null;
        for (MethodConstant idMethod : setOps)
            {
            TypeConstant typeParam = idMethod.getRawReturns()[1];
            if (typeRight.isA(typeParam))
                {
                if (mapBest != null)
                    {
                    mapBest.put(idMethod.getSignature(), idMethod);
                    }
                else if (idBest == null || typeParam.isA(idBest.getRawReturns()[0]))
                    {
                    idBest = idMethod;
                    }
                else if (!idBest.getRawReturns()[0].isA(typeParam))
                    {
                    // ambiguous at this point
                    mapBest = new HashMap<>();
                    mapBest.put(idBest  .getSignature(), idBest  );
                    mapBest.put(idMethod.getSignature(), idMethod);
                    idBest = null;
                    }
                }
            }

        // if there are multiple possible options, pick the unambiguously best one
        if (mapBest != null)
            {
            SignatureConstant sigBest = typeLeft.selectBest(
                    mapBest.keySet().toArray(new SignatureConstant[mapBest.size()]));
            if (sigBest == null)
                {
                return null;
                }

            idBest = mapBest.get(sigBest);
            assert idBest != null;
            }

        return idBest;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        // testing the fit of a particular type for the expression involves starting with an
        // implicit type, and determining if it:
        //
        //   i) yields the typeRequired (Fit), or
        //  ii) yields something that converts to the typeRequired (Conv), or
        // iii) converts to something that yields the typeRequired (Conv)
        //
        // this logic must conform to the rules used by validate()

        TypeConstant typeLeft = expr1.getImplicitType(ctx);
        if (typeLeft == null)
            {
            return TypeFit.NoFit;
            }

        TypeFit             fitVia   = TypeFit.NoFit;
        TypeInfo            infoLeft = typeLeft.ensureTypeInfo();
        Set<MethodConstant> setOps   = infoLeft.findOpMethods(getDefaultMethodName(), getOperatorString(), 1);
        for (MethodConstant idMethod : setOps)
            {
            TypeConstant[] aRets = idMethod.getRawReturns();
            if (aRets.length >= 1)
                {
                TypeConstant typeResult = aRets[0];
                if (typeResult.isA(typeRequired))
                    {
                    // REVIEW do we need to test the "right" expression again the type of the param of the op method?
                    return TypeFit.Fit;
                    }
                else if (!fitVia.isFit() && typeResult.isAssignableTo(typeRequired))
                    {
                    // there is a solution via conversion on the result of an operator
                    fitVia = TypeFit.Conv;
                    }
                }
            }
        if (fitVia.isFit())
            {
            return fitVia;
            }

        for (MethodInfo infoAuto : infoLeft.getAutoMethodInfos())
            {
            TypeConstant typeConv = infoAuto.getSignature().getRawReturns()[0];
            TypeInfo     infoConv = typeConv.ensureTypeInfo();
            for (MethodConstant idMethod : infoConv.findOpMethods(getDefaultMethodName(), getOperatorString(), 1))
                {
                TypeConstant[] aRets = idMethod.getRawReturns();
                if (aRets.length >= 1 && aRets[0].isAssignableTo(typeRequired))
                    {
                    // there is a solution via an operator on the result of a conversion
                    return TypeFit.Conv;
                    }
                }
            }

        return TypeFit.NoFit;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
        {
        if (operator.getId() != Id.DIVMOD || atypeRequired.length < 2)
            {
            return super.testFitMulti(ctx, atypeRequired);
            }

        TypeConstant typeLeft = expr1.getImplicitType(ctx);
        if (typeLeft == null)
            {
            return TypeFit.NoFit;
            }

        TypeFit             fitVia   = TypeFit.NoFit;
        TypeInfo            infoLeft = typeLeft.ensureTypeInfo();
        Set<MethodConstant> setOps   = infoLeft.findOpMethods(getDefaultMethodName(), getOperatorString(), 1);
        for (MethodConstant idMethod : setOps)
            {
            TypeConstant[] aRets = idMethod.getRawReturns();
            if (aRets.length >= 2)
                {
                if (aRets[0].isA(atypeRequired[0]) && aRets[1].isA(atypeRequired[1]))
                    {
                    // REVIEW do we need to test the "right" expression again the type of the param of the op method?
                    return TypeFit.Fit;
                    }
                else if (!fitVia.isFit() && aRets[0].isAssignableTo(atypeRequired[0])
                                         && aRets[1].isAssignableTo(atypeRequired[1]))
                    {
                    // there is a solution via conversion on the result of an operator
                    fitVia = TypeFit.Conv;
                    }
                }
            }
        if (fitVia.isFit())
            {
            return fitVia;
            }

        for (MethodInfo infoAuto : infoLeft.getAutoMethodInfos())
            {
            TypeConstant typeConv = infoAuto.getSignature().getRawReturns()[0];
            TypeInfo     infoConv = typeConv.ensureTypeInfo();
            for (MethodConstant idMethod : infoConv.findOpMethods(getDefaultMethodName(), getOperatorString(), 1))
                {
                TypeConstant[] aRets = idMethod.getRawReturns();
                if (aRets.length >= 2 && aRets[0].isAssignableTo(atypeRequired[0])
                                      && aRets[1].isAssignableTo(atypeRequired[1]))
                    {
                    // there is a solution via an operator on the result of a conversion
                    return TypeFit.Conv;
                    }
                }
            }

        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // figure out the best types to use to validate the two sub-expressions
        TypeConstant type1 = null;
        TypeConstant type2 = null;
        if (typeRequired != null)
            {
            TypeConstant[] atypeValidate = determineValidationTypes(ctx, typeRequired);
            type1 = atypeValidate[0];
            type2 = atypeValidate[1];
            }

        // using the inferred types (if any), validate the expressions
        Expression expr1New = expr1.validate(ctx, type1, errs);
        Expression expr2New = expr2.validate(ctx, type2, errs);
        if (expr1New == null || expr2New == null)
            {
            return finishValidation(typeRequired, null, TypeFit.NoFit, null, errs);
            }

        // store the updates to the expressions (if any)
        expr1 = expr1New;
        expr2 = expr2New;

        // get the exact types of the expressions
        type1 = expr1New.getType();
        type2 = expr2New.getType();

        MethodConstant idOp = determineOperator(type1, type2, typeRequired, errs);
        if (idOp == null)
            {
            return finishValidation(typeRequired, type1, TypeFit.NoFit, null, errs);
            }
        m_idOp = idOp;

        // determine the actual result type
        TypeConstant typeResult = idOp.getRawReturns()[0];

        // determine if the result of this expression is itself constant
        Constant constResult = null;
        if (expr1New.hasConstantValue() && expr2New.hasConstantValue())
            {
            // delegate the operation to the constants
            Constant const1 = expr1New.toConstant();
            Constant const2 = expr2New.toConstant();
            try
                {
                constResult = expr1New.toConstant().apply(operator.getId(), expr2New.toConstant());
                }
            catch (ArithmeticException e)
                {
                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, typeResult,
                        getSource().toString(getStartPosition(), getEndPosition()));
                constResult = Constant.defaultValue(typeResult);
                }
            catch (UnsupportedOperationException | IllegalStateException e)
                {
                operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
                constResult = Constant.defaultValue(typeResult);
                }
            }

        return finishValidation(typeRequired, typeResult, TypeFit.Fit, constResult, errs);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired,
            ErrorListener errs)
        {
        if (operator.getId() != Id.DIVMOD || atypeRequired.length < 2)
            {
            return super.validateMulti(ctx, atypeRequired, errs);
            }

        // TODO "/%" -> validateMulti()
        notImplemented();
        return this;
        }

    /**
     * Calculate the types to use to validate the left and right expressions.
     *
     * @param ctx           the compiler context
     * @param typeRequired  the type (or first type if more than one) required
     *
     * @return an array of two types, the left and right types to use for validation
     */
    private TypeConstant[] determineValidationTypes(Context ctx, TypeConstant typeRequired)
        {
        // all of these operators work the same way, in terms of types and left associativity:
        //
        // 1) there is a "required type", which is optional. if a required type is provided, then
        //    we want to optimize for it, which means to get the expression tree using that type as
        //    early (deep in the tree) as possible, to enhance some combination of precision and
        //    (possibly) performance:
        //
        //    a) determine an implied type from the required type; for example, if the required type
        //       is Range<Int> and the operator is DOTDOT, then the type implies "Int", while if the
        //       required type is String and the operator is ADD, then the type implies "String".
        //
        //       * in most cases, the implied type is the same as the required type, with the
        //         possible exceptions being the DOTDOT (uses first type parameter) and DIVMOD
        //         (uses type of first value)
        //
        //       * the algorithm is simple: first test the expression to see if it can produce the
        //         required type, and if not, then test each type parameters of the required type
        //         (first one wins)
        //
        //    b) if there is an implied type, then find the appropriate op(s) on the implied type
        //       that yield(s) the required type
        //
        //    c) if any such op exists, test if the first expression can yield the implied type
        //       necessary left hand type
        //
        //    d) if it can yield the implied type, then check the second expression to see if it
        //       can yield the necessary right hand type (i.e. the parameter to the operator method)
        //       for each potential op
        //
        //    e) select the best match, if any match, with ambiguity resulting in an error, and no
        //       matches falling through to phase 2
        //
        // 2) if no op method and types were already determined, then the op method will have to be
        //    determined from the left hand type, which is validated "naturally" (no required type)
        TypeConstant type1 = null;
        TypeConstant type2 = null;
        TypeInference: if (typeRequired != null)
            {
            if (expr1.testFit(ctx, typeRequired).isFit())
                {
                Set<MethodConstant> setOps = typeRequired.ensureTypeInfo().findOpMethods(
                        getDefaultMethodName(), getOperatorString(), 1);
                for (MethodConstant idMethod : setOps)
                    {
                    if (expr2.testFit(ctx, idMethod.getRawParams()[0]).isFit()
                            && idMethod.getRawReturns()[0].isA(typeRequired))
                        {
                        type1 = typeRequired;
                        type2 = idMethod.getRawParams()[0];
                        break TypeInference; // TODO find the "best", not just the first
                        }
                    }
                }

            if (typeRequired.isParamsSpecified())
                {
                for (TypeConstant typeParam : typeRequired.getParamTypesArray())
                    {
                    if (expr1.testFit(ctx, typeParam).isFit())
                        {
                        Set<MethodConstant> setOps = typeParam.ensureTypeInfo().findOpMethods(
                                getDefaultMethodName(), getOperatorString(), 1);
                        for (MethodConstant idMethod : setOps)
                            {
                            if (expr2.testFit(ctx, idMethod.getRawParams()[0]).isFit()
                                    && idMethod.getRawReturns()[0].isA(typeRequired))
                                {
                                type1 = typeParam;
                                type2 = idMethod.getRawParams()[0];
                                break TypeInference; // TODO find the "best", not just the first
                                }
                            }
                        }
                    }
                }
            }

        return new TypeConstant[] {type1, type2};
        }

    /**
     * Find the method to use to implement the op.
     *
     * @param type1         the type of the first expression
     * @param type2         the type of the second expression
     * @param typeRequired  the type required to be produced by this expression (or the first of the
     *                      required types if more than one type is required)
     * @param errs          the list to log any errors to
     *
     * @return the op method, or null if no appropriate op method was found, or no one op method was
     *         the unambiguous best
     */
    private MethodConstant determineOperator(TypeConstant type1, TypeConstant type2, TypeConstant typeRequired, ErrorListener errs)
        {
        // select the method on expr1 that will be used to implement the op
        MethodConstant      idOp     = null;
        Set<MethodConstant> setOps   = null;
        MethodConstant      idConv   = null;
        Set<MethodConstant> setConvs = null;
        TypeInfo            info1    = type1.ensureTypeInfo(errs);
        for (MethodConstant method : info1.findOpMethods(getDefaultMethodName(), getOperatorString(), 1))
            {
            // determine if this method satisfies the types (param and return)
            TypeConstant typeParam = method.getRawParams()[0];
            if (type2.isA(typeParam))
                {
                // check for a perfect match
                if (typeRequired == null || method.getRawReturns()[0].isA(typeRequired))
                    {
                    if (setOps != null)
                        {
                        setOps.add(method);
                        }
                    else if (idOp == null)
                        {
                        idOp = method;
                        }
                    else
                        {
                        setOps = new HashSet<>();
                        setOps.add(idOp);
                        setOps.add(method);
                        idOp = null;
                        }
                    }
                // check if @Auto conversion of the return value would satisfy the required type
                else if (typeParam.ensureTypeInfo().findConversion(typeRequired) != null)
                    {
                    if (setConvs != null)
                        {
                        setConvs.add(method);
                        }
                    else if (idConv == null)
                        {
                        idConv = method;
                        }
                    else
                        {
                        setConvs = new HashSet<>();
                        setConvs.add(idConv);
                        setConvs.add(method);
                        idConv = null;
                        }
                    }
                }
            }

        // having collected all of the possible ops that could be used, select the one method to use
        if (idOp != null)
            {
            return idOp;
            }
        else if (setOps != null)
            {
            // find the best op method out of the multiple options
            notImplemented(); // TODO
            return null;
            }
        else if (idConv != null)
            {
            assert typeRequired != null;
            return idConv;
            }
        else if (setConvs != null)
            {
            // find the best op method and conversion out of the multiple options
            notImplemented(); // TODO
            return null;
            }
        else
            {
            // error: somehow, we got this far, but we couldn't find an op that matched the
            // necessary types
            operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
            return null;
            }
        }

    @Override
    public boolean isAborting()
        {
        // these can only complete if both sub-expressions can complete
        return expr1.isAborting() || expr2.isAborting();
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (!hasConstantValue())
            {
            switch (operator.getId())
                {
                case DIVMOD:
                case DOTDOT:
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                case MOD:
                case BIT_OR:
                case BIT_XOR:
                case BIT_AND:
                case SHL:
                case SHR:
                case USHR:
                    code.add(new Var(getType()));
                    Register regResult = code.lastRegister();
                    generateAssignment(code, new Assignable(regResult), errs);
                    return regResult;
                }
            }

        return super.generateArgument(code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public Argument[] generateArguments(Code code, boolean fLocalPropOk, boolean fUsedOnce,
            ErrorListener errs)
        {
        if (operator.getId() != Id.DIVMOD || isSingle())
            {
            return super.generateArguments(code, fLocalPropOk, fUsedOnce, errs);
            }

        // TODO /%
        notImplemented();
        return null;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            // evaluate the sub-expressions
            Argument arg1 = expr1.generateArgument(code, false, false, errs);
            Argument arg2 = expr2.generateArgument(code, false, false, errs);

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case BIT_OR:
                    // TODO
                    throw new UnsupportedOperationException();

                case BIT_XOR:
                    // TODO
                    throw new UnsupportedOperationException();

                case BIT_AND:
                    // TODO
                    throw new UnsupportedOperationException();

                case DOTDOT:
                    // TODO
                    throw new UnsupportedOperationException();

                case SHL:
                    // TODO
                    throw new UnsupportedOperationException();

                case SHR:
                    // TODO
                    throw new UnsupportedOperationException();

                case USHR:
                    // TODO
                    throw new UnsupportedOperationException();

                case ADD:
                    code.add(new GP_Add(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case SUB:
                    code.add(new GP_Sub(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case MUL:
                    code.add(new GP_Mul(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case DIVMOD:
                    // TODO /% with one real register and one black hole
                    notImplemented();
                    break;

                case DIV:
                    code.add(new GP_Div(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case MOD:
                    code.add(new GP_Mod(arg1, arg2, LVal.getLocalArgument()));
                    break;
                }

            return;
            }

        super.generateAssignment(code, LVal, errs);
        }

    @Override
    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
        {
        if (getValueCount() == 2)
            {
            assert operator.getId() == Id.DIVMOD;
            // TODO /%
            throw new UnsupportedOperationException();
            }

        super.generateAssignments(code, aLVal, errs);
        }


    // ----- helpers -------------------------------------------------------------------------------

    public String getDefaultMethodName()
        {
        switch (operator.getId())
            {
            case BIT_AND:
                return "and";

            case BIT_OR:
                return "or";

            case BIT_XOR:
                return "xor";

            case DOTDOT:
                return "through";

            case SHL:
                return "shiftLeft";

            case SHR:
                return "shiftRight";

            case USHR:
                return "shiftAllRight";

            case ADD:
                return "add";

            case SUB:
                return "sub";

            case MUL:
                return "mul";

            case DIV:
                return "div";

            case MOD:
                return "mod";

            case DIVMOD:
                return "divmod";

            default:
                throw new IllegalStateException();
            }
        }

    public String getOperatorString()
        {
        return operator.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    private transient MethodConstant m_idOp;
    }
