package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.GP_And;
import org.xvm.asm.op.GP_Div;
import org.xvm.asm.op.GP_DivMod;
import org.xvm.asm.op.GP_DotDot;
import org.xvm.asm.op.GP_Mod;
import org.xvm.asm.op.GP_Mul;
import org.xvm.asm.op.GP_Or;
import org.xvm.asm.op.GP_Shl;
import org.xvm.asm.op.GP_Shr;
import org.xvm.asm.op.GP_ShrAll;
import org.xvm.asm.op.GP_Sub;
import org.xvm.asm.op.GP_Xor;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * Relational operator expression (with @Op support) for something that follows the pattern
 * "expression operator expression".
 * <p/>
 * <ul>
 * <li><tt>COND_XOR:   "^^"</tt> - </li>
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
            case COND_XOR:
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
            TypeConstant typeParam = idMethod.getRawParams()[0];
            if (typeRight.isAssignableTo(typeParam))
                {
                if (mapBest != null)
                    {
                    mapBest.put(idMethod.getSignature(), idMethod);
                    }
                else if (idBest == null || typeParam.isAssignableTo(idBest.getRawParams()[0]))
                    {
                    idBest = idMethod;
                    }
                else if (!idBest.getRawParams()[0].isA(typeParam))
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
                    mapBest.keySet().toArray(new SignatureConstant[0]));
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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
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
        String              sMethod  = getDefaultMethodName();
        String              sOp      = getOperatorString();
        Set<MethodConstant> setOps   = infoLeft.findOpMethods(sMethod, sOp, 1);
        for (MethodConstant idMethod : setOps)
            {
            TypeConstant[] aRets = idMethod.getRawReturns();
            if (aRets.length >= 1)
                {
                TypeConstant typeResult = aRets[0];
                if (typeResult.isA(typeRequired))
                    {
                    // the "right" expression will be checked during validation
                    return TypeFit.Fit;
                    }
                if (!fitVia.isFit() && typeResult.isAssignableTo(typeRequired))
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

        ConstantPool pool = pool();
        for (MethodInfo infoAuto : infoLeft.getAutoMethodInfos())
            {
            TypeConstant typeConv = infoAuto.getSignature().getRawReturns()[0];
            if (typeConv.isAutoNarrowing())
                {
                typeConv = typeConv.resolveAutoNarrowing(pool, false, infoLeft.getType());
                }

            for (MethodConstant idMethod : typeConv.ensureTypeInfo().findOpMethods(sMethod, sOp, 1))
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
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        if (operator.getId() != Id.DIVMOD || atypeRequired.length < 2)
            {
            return super.testFitMulti(ctx, atypeRequired, errs);
            }

        TypeConstant typeLeft = expr1.getImplicitType(ctx);
        if (typeLeft == null)
            {
            return TypeFit.NoFit;
            }

        TypeFit             fitVia   = TypeFit.NoFit;
        TypeInfo            infoLeft = typeLeft.ensureTypeInfo();
        String              sMethod  = getDefaultMethodName();
        String              sOp      = getOperatorString();
        Set<MethodConstant> setOps   = infoLeft.findOpMethods(sMethod, sOp, 1);
        for (MethodConstant idMethod : setOps)
            {
            TypeConstant[] aRets = idMethod.getRawReturns();
            if (aRets.length >= 2)
                {
                if (aRets[0].isA(atypeRequired[0]) && aRets[1].isA(atypeRequired[1]))
                    {
                    // the "right" expression will be checked during validation
                    return TypeFit.Fit;
                    }
                if (!fitVia.isFit() && aRets[0].isAssignableTo(atypeRequired[0])
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
            for (MethodConstant idMethod : infoConv.findOpMethods(sMethod, sOp, 1))
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
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeFit fit = TypeFit.Fit;

        // figure out the best types to use to validate the two sub-expressions
        TypeConstant typeRequired = atypeRequired != null && atypeRequired.length >= 1
                ? atypeRequired[0]
                : null;

        // using the inferred types (if any), validate the expressions
        TypeConstant type1Req = guessLeftType(ctx, typeRequired);
        Expression   expr1New = expr1.validate(ctx, type1Req, errs);
        TypeConstant type1Act = null;
        if (expr1New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1    = expr1New;
            type1Act = expr1New.getType();
            }

        TypeConstant type2Req = selectRightType(ctx, typeRequired, type1Act);
        Expression   expr2New = expr2.validate(ctx, type2Req, errs);
        TypeConstant type2Act = null;
        if (expr2New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2    = expr2New;
            type2Act = expr2New.getType();
            }

        if (!fit.isFit())
            {
            // bail out
            return finishValidations(atypeRequired, null, TypeFit.NoFit, null, errs);
            }

        boolean        fMulti       = operator.getId() == Id.DIVMOD;
        int            cExpected    = fMulti ? 2 : 1;
        int            cResults     = cExpected;
        TypeConstant[] atypeResults = null;
        ErrorList      errsTemp     = new ErrorList(1);
        MethodConstant idOp         = findOpMethod(type1Act, type2Act, typeRequired, errsTemp);

        if (idOp == null)
            {
            // try to resolve the formal types and see if there is an op that matches
            if (type1Act.containsFormalType() || type2Act.containsFormalType())
                {
                ConstantPool pool = pool();

                type1Act = type1Act.resolveConstraints(pool);
                type2Act = type2Act.resolveConstraints(pool);

                idOp = findOpMethod(type1Act, type2Act, typeRequired, ErrorListener.BLACKHOLE);
                }

            if (idOp == null)
                {
                errsTemp.logTo(errs);
                }
            }

        if (idOp != null)
            {
            atypeResults = idOp.getRawReturns();
            cResults     = atypeResults.length;
            }
        if (idOp == null || cResults < cExpected)
            {
            if (cResults < cExpected)
                {
                operator.log(errs, getSource(), Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cExpected, cResults);
                }

            TypeConstant[] atypeFake = fMulti
                    ? new TypeConstant[] {type1Act, type2Act}
                    : new TypeConstant[] {type1Act};
            return finishValidations(atypeRequired, atypeFake, TypeFit.NoFit, null, errs);
            }
        m_idOp = idOp;

        // determine if the result of this expression is itself constant
        Constant[] aconstResult = null;
        if (expr1New.isConstant() && expr2New.isConstant())
            {
            // delegate the operation to the constants
            try
                {
                Constant constResult = expr1New.toConstant().apply(operator.getId(), expr2New.toConstant());
                aconstResult = fMulti
                        ? ((ArrayConstant) constResult).getValue() // divmod result is in a tuple
                        : new Constant[] {constResult};
                }
            catch (RuntimeException e) {}
            }

        return finishValidations(atypeRequired, atypeResults, fit, aconstResult, errs);
        }

    /**
     * Calculate the type to use to validate the left expressions.
     *
     * @param ctx           the compiler context
     * @param typeRequired  the type (or first type if more than one) required, or null
     *
     * @return the type to request from the left expression, or null
     */
    private TypeConstant guessLeftType(Context ctx, TypeConstant typeRequired)
        {
        // all of these operators work the same way, in terms of types and left associativity:
        //
        // 1) there is a "required type", which is optional. if a required type is provided, then
        //    we want to optimize for it, which means to get the expression tree using that type as
        //    early (deep in the tree) as possible, to enhance some combination of precision and
        //    (possibly) performance:
        //
        //    a) determine an implied type from the required type; for example, if the required type
        //       is Interval<Int> and the operator is DOTDOT, then the type implies "Int", while if
        //       the required type is String and the operator is ADD, then the type implies "String"
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
        if (typeRequired == null)
            {
            // no basis for a guess
            return null;
            }

        String sMethod = getDefaultMethodName();
        String sOp     = getOperatorString();
        if (expr1.testFit(ctx, typeRequired, null).isFit())
            {
            Set<MethodConstant> setOps = typeRequired.ensureTypeInfo().findOpMethods(sMethod, sOp, 1);
            for (MethodConstant idMethod : setOps)
                {
                if (expr2.testFit(ctx, idMethod.getRawParams()[0], null).isFit()
                        && idMethod.getRawReturns()[0].isAssignableTo(typeRequired))
                    {
                    // TODO find best, not just the first
                    return typeRequired;
                    }
                }
            }

        if (typeRequired.isParamsSpecified())
            {
            for (TypeConstant typeParam : typeRequired.getParamTypesArray())
                {
                if (expr1.testFit(ctx, typeParam, null).isFit())
                    {
                    Set<MethodConstant> setOps = typeParam.ensureTypeInfo().findOpMethods(sMethod, sOp, 1);
                    for (MethodConstant idMethod : setOps)
                        {
                        if (expr2.testFit(ctx, idMethod.getRawParams()[0], null).isFit()
                                && idMethod.getRawReturns()[0].isAssignableTo(typeRequired))
                            {
                            // TODO find best, not just the first
                            return typeParam;
                            }
                        }
                    }
                }
            }

        return null;
        }

    /**
     * Calculate the type to use to validate the right expressions. This is a continuation of the
     * logic described in {@link #guessLeftType(Context, TypeConstant)}.
     *
     * @param ctx           the compiler context
     * @param typeRequired  the type (or first type if more than one) required, or null
     * @param typeLeft      the type of the left expression, or null
     *
     * @return the type to request from the right expression, or null
     */
    private TypeConstant selectRightType(Context ctx, TypeConstant typeRequired, TypeConstant typeLeft)
        {
        if (typeLeft == null)
            {
            // we're already screwed
            return null;
            }

        Set<MethodConstant> setOps = typeLeft.ensureTypeInfo().findOpMethods(
                getDefaultMethodName(), getOperatorString(), 1);
        for (MethodConstant idMethod : setOps)
            {
            if (expr2.testFit(ctx, idMethod.getRawParams()[0], null).isFit()
                    && (typeRequired == null || idMethod.getRawReturns()[0].isAssignableTo(typeRequired)))
                {
                // TODO find best, not just the first
                return idMethod.getRawParams()[0];
                }
            }

        // no op that we could find will work, so just let the expression validate naturally and
        // check for the predictable errors then
        return null;
        }

    /**
     * Find the method to use to implement the op.
     * TODO: consider merging with ArrayAccessExpression.findOpMethod()
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
    private MethodConstant findOpMethod(
            TypeConstant  type1,
            TypeConstant  type2,
            TypeConstant  typeRequired,
            ErrorListener errs)
        {
        // select the method on expr1 that will be used to implement the op
        MethodConstant      idBest   = null;
        Set<MethodConstant> setOps   = null;
        MethodConstant      idConv   = null;
        Set<MethodConstant> setConvs = null;
        TypeInfo            info1    = type1.ensureTypeInfo(errs);
        String              sMethod  = getDefaultMethodName();
        String              sOp      = getOperatorString();
        for (MethodConstant method : info1.findOpMethods(sMethod, sOp, 1))
            {
            // determine if this method satisfies the types (param and return)
            TypeConstant typeParam  = method.getRawParams()[0];
            TypeConstant typeReturn = method.getRawReturns()[0];
            if (type2.isAssignableTo(typeParam) &&
                    (typeRequired == null || typeReturn.isAssignableTo(typeRequired)))
                {
                // check for a perfect match
                if (type2.isA(typeParam) &&
                        (typeRequired == null || typeReturn.isA(typeRequired)))
                    {
                    if (setOps != null)
                        {
                        setOps.add(method);
                        }
                    else if (idBest == null)
                        {
                        idBest = method;
                        }
                    else
                        {
                        SignatureConstant sigOp     = idBest.getSignature();
                        SignatureConstant sigMethod = method.getSignature();
                        if (!sigOp.equals(sigMethod))
                            {
                            if (sigMethod.isSubstitutableFor(sigOp, type1))
                                {
                                continue;
                                }
                            if (sigOp.isSubstitutableFor(sigMethod, type1))
                                {
                                idBest = method;
                                continue;
                                }
                            setOps = new HashSet<>();
                            setOps.add(idBest);
                            setOps.add(method);
                            idBest = null;
                            }
                        }
                    }
                else
                    {
                    if (setConvs != null)
                        {
                        setConvs.add(method);
                        }
                    else if (idConv == null)
                        {
                        idConv = method;
                        }
                    else if (!idConv.getSignature().equals(method.getSignature()))
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
        if (idBest != null)
            {
            return idBest;
            }

        if (setOps != null)
            {
            // find the best op method out of the multiple options
            idBest = chooseBestMethod(setOps, type2);

            if (idBest == null)
                {
                operator.log(errs, getSource(), Severity.ERROR, Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                                sOp, type1.getValueString());
                }
            return idBest;
            }

        if (idConv != null)
            {
            return idConv;
            }

        if (setConvs != null)
            {
            // find the best op method and conversion out of the multiple options
            // find the best op method out of the multiple options
            idBest = chooseBestMethod(setConvs, type2);

            if (idBest == null)
                {
                operator.log(errs, getSource(), Severity.ERROR, Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                                sOp, type1.getValueString());
                }
            return idBest;
            }

        // error: somehow, we got this far, but we couldn't find an op that matched the
        // necessary types
        operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
        return null;
        }

    /**
     * Find the best matching method from the set that has a parameter of the specified type.

     * @param setOps      the methods
     * @param typeActual  the actual parameter type
     *
     * @return the best method id or null if nothing matches or ambiguous
     */
    private MethodConstant chooseBestMethod(Set<MethodConstant> setOps, TypeConstant typeActual)
        {
        MethodConstant idBest = null;
        for (Iterator<MethodConstant> iter = setOps.iterator(); iter.hasNext();)
            {
            MethodConstant idMethod  = iter.next();
            TypeConstant   typeParam = idMethod.getRawParams()[0];

            if (typeActual.equals(typeParam))
                {
                return idMethod;
                }

            if (typeActual.isA(typeParam))
                {
                idBest = idMethod;
                }
            else
                {
                iter.remove();
                }
            }
        return setOps.size() == 1 ? idBest : null;
        }

    @Override
    public boolean isCompletable()
        {
        // these can only complete if both sub-expressions can complete
        return expr1.isCompletable() && expr2.isCompletable();
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (!LVal.isLocalArgument())
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        Argument argLVal = LVal.getLocalArgument();

        // evaluate the sub-expressions
        // (Note: all the ops used below know to inverse the order of args on the stack)
        Argument arg1 = expr1.generateArgument(ctx, code, true, true, errs);
        Argument arg2 = expr2.generateArgument(ctx, code, true, true, errs);

        // generate the op that combines the two sub-expressions
        switch (operator.getId())
            {
            case BIT_OR:
                code.add(new GP_Or(arg1, arg2, argLVal));
                return;

            case BIT_XOR:
            case COND_XOR:
                code.add(new GP_Xor(arg1, arg2, argLVal));
                return;

            case BIT_AND:
                code.add(new GP_And(arg1, arg2, argLVal));
                return;

            case DOTDOT:
                code.add(new GP_DotDot(arg1, arg2, argLVal));
                return;

            case SHL:
                code.add(new GP_Shl(arg1, arg2, argLVal));
                return;

            case SHR:
                code.add(new GP_Shr(arg1, arg2, argLVal));
                return;

            case USHR:
                code.add(new GP_ShrAll(arg1, arg2, argLVal));
                return;

            case ADD:
                code.add(new GP_Add(arg1, arg2, argLVal));
                return;

            case SUB:
                code.add(new GP_Sub(arg1, arg2, argLVal));
                return;

            case MUL:
                code.add(new GP_Mul(arg1, arg2, argLVal));
                return;

            case DIVMOD:
                // "/%" with a single return needs a black hole for the second one
                code.add(new GP_DivMod(arg1, arg2,
                        new Argument[]{argLVal, generateBlackHole(null)}));
                return;

            case DIV:
                code.add(new GP_Div(arg1, arg2, argLVal));
                return;

            case MOD:
                code.add(new GP_Mod(arg1, arg2, argLVal));
                return;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        switch (aLVal.length)
            {
            default:
                throw new IllegalStateException();

            case 2:
                if (operator.getId() != Id.DIVMOD)
                    {
                    throw new IllegalStateException();
                    }

                if (aLVal[0].isLocalArgument() && aLVal[1].isLocalArgument())
                    {
                    Argument arg1 = expr1.generateArgument(ctx, code, true, true, errs);
                    Argument arg2 = expr2.generateArgument(ctx, code, true, true, errs);
                    code.add(new GP_DivMod(arg1, arg2, new Argument[]
                            {aLVal[0].getLocalArgument(), aLVal[1].getLocalArgument()}));
                    }
                else
                    {
                    super.generateAssignments(ctx, code, aLVal, errs);
                    }
                return;

            case 1:
                generateAssignment(ctx, code, aLVal[0], errs);
                return;

            case 0:
                generateAssignment(ctx, code, new Assignable(), errs);
                return;
            }
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
            case COND_XOR:
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
