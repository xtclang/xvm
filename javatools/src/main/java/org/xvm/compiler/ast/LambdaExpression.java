package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Assignment;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PendingTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.asm.op.FBind;
import org.xvm.asm.op.L_Get;
import org.xvm.asm.op.MBind;
import org.xvm.asm.op.MoveRef;
import org.xvm.asm.op.MoveThis;
import org.xvm.asm.op.MoveVar;
import org.xvm.asm.op.P_Get;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Context.CaptureContext;
import org.xvm.compiler.ast.LabeledStatement.LabelVar;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import org.xvm.util.Handy;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * Lambda expression is an inlined function. This version uses parameters that are assumed to be
 * names only.
 */
public class LambdaExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the LambdaExpression.
     *
     * @param params     either a list of Expression objects or a list of Parameter objects
     * @param operator   the operator (for now always {@link Id#LAMBDA})
     * @param body       the StatementBlock of the lambda
     * @param lStartPos  the expression's start position in the source code
     */
    public LambdaExpression(List params, Token operator, StatementBlock body, long lStartPos)
        {
        if (!params.isEmpty() && params.get(0) instanceof Expression)
            {
            assert params.stream().allMatch(Expression.class::isInstance);
            this.paramNames = params;
            }
        else
            {
            assert params.stream().allMatch(Parameter.class::isInstance);
            this.params = params;
            }

        this.operator  = operator;
        this.body      = body;
        this.lStartPos = lStartPos;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isComponentNode()
        {
        return true;
        }

    @Override
    public Component getComponent()
        {
        MethodStructure method = m_lambda;
        return method == null
                ? super.getComponent()
                : method;
        }

    /**
     * @return the lambda structure (null until the validation phase)
     */
    public MethodStructure getLambda()
        {
        return m_lambda;
        }

    /**
     * @return true iff the lambda declaration has parameters
     */
    public boolean hasParameters()
        {
        return paramNames != null && !paramNames.isEmpty()
                || params != null && !params.isEmpty();
        }

    /**
     * @return the number of parameters
     */
    public int getParamCount()
        {
        if (paramNames != null && !paramNames.isEmpty())
            {
            return paramNames.size();
            }

        if (params != null && !params.isEmpty())
            {
            return params.size();
            }

        return 0;
        }

    /**
     * @return an array of parameter names
     */
    public String[] getParamNames()
        {
        int c = getParamCount();
        if (c == 0)
            {
            return NO_NAMES;
            }

        String[] as = new String[c];
        if (hasOnlyParamNames())
            {
            for (int i = 0; i < c; ++i)
                {
                Expression expr = paramNames.get(i);
                as[i] = expr instanceof NameExpression exprName
                        ? exprName.getName()
                        : null;
                }
            }
        else
            {
            for (int i = 0; i < c; ++i)
                {
                Parameter param = params.get(i);
                as[i] = param.getName();
                }
            }

        return as;
        }

    /**
     * @return true iff the lambda declaration has parameters, but did not specify parameter types
     */
    public boolean hasOnlyParamNames()
        {
        return paramNames != null && !paramNames.isEmpty();
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return body.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- code container methods ----------------------------------------------------------------

    @Override
    public TypeConstant[] getReturnTypes()
        {
        TypeConstant typeFn = isValidated() ? getType() : m_typeRequired;
        return pool().extractFunctionReturns(typeFn);
        }

    @Override
    public boolean isReturnConditional()
        {
        // this may be called during validation, when we're still trying to figure out what exactly
        // does get returned
        TypeConstant typeFn = isValidated() ? getType() : m_typeRequired;
        return typeFn != null && pool().isConditionalReturn(typeFn);
        }

    @Override
    public void collectReturnTypes(TypeConstant[] atypeRet)
        {
        TypeCollector collector = m_collector;
        if (collector == null)
            {
            m_collector = collector = new TypeCollector(pool());
            }
        collector.add(atypeRet);
        }


    // ----- compilation (Statement) ---------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        // just like the MethodDeclarationStatement, lambda expressions are considered to be
        // completely opaque, and so a lambda defers the processing of its children at this point,
        // because it wants everything around its children to be set up by the time those children
        // need to be able to answer all the questions about names and types and so on
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        // see note above
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        // see note above
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        MethodStructure method = m_lambda;

        // the method body containing this must validate the lambda expression, which then will
        // create the lambda body (the method structure), and that has to happen before we try to
        // spit out any code
        if (method == null)
            {
            mgr.requestRevisit();
            mgr.deferChildren();
            return;
            }

        if (getParentBlock().isTerminatedAbnormally())
            {
            if (method.getIdentityConstant().isNascent())
                {
                // stub out the lambda structure
                configureLambda(TypeConstant.NO_TYPES, Handy.NO_ARGS, 0,
                        new boolean[0], TypeConstant.NO_TYPES);
                }

            mgr.deferChildren();
            mgr.markComplete();
            return;
            }

        // this is where the magic happens:
        // - by this point in time, the method body containing the lambda has already been validated
        // - the validation included validating (a temp copy of) this expression in order to
        //   determine definite assignment information (VAS data) which is used to itemize the
        //   captures by the lambda, and whether the read-only captures should use values or
        //   references
        // - when the expression was validated, it started by creating the MethodStructure for the
        //   lambda (m_lambda)
        // - when the expression was subsequently asked to generate code that obtains the lambda
        //   (via generateAssignment), it was then able to use that VAS information to build the
        //   final signature for the lambda, including all the parameters necessary to capture
        //   the various variables in the lexical scope of the lambda declaration that needed to be
        //   passed to the lambda (via FBIND)
        // - so now, at this point, we have the signature, we have the method structure, and we just
        //   have to emit the code corresponding to the lambda
        if (catchUpChildren(errs))
            {
            if (!body.compileMethod(method.createCode(), errs))
                {
                mgr.deferChildren();
                }
            }
        }


    // ----- compilation (Expression) --------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (!ensurePrepared(ErrorListener.BLACKHOLE))
            {
            return null;
            }

        if (isValidated())
            {
            return getType();
            }

        assert m_typeRequired == null && m_collector == null;

        // consider three lambdas:
        // 1) a lambda defined with no parameters: "() -> ..."
        // 2) a lambda defined with typed parameters: "(Int x, Int y) -> ..."
        // 3) a lambda defined with only parameter names: "x -> ..."
        // the problem with the last form is that the type of the lambda includes the types of the
        // parameters, and the type cannot be inferred from its name
        if (hasOnlyParamNames())
            {
            return null;
            }

        int            cParams     = getParamCount();
        String[]       asParams    = cParams == 0 ? NO_NAMES : new String[cParams];
        TypeConstant[] atypeParams = cParams == 0 ? TypeConstant.NO_TYPES : new TypeConstant[cParams];

        if (!collectParamNamesAndTypes(null, atypeParams, asParams, ErrorListener.BLACKHOLE))
            {
            return null;
            }

        TypeConstant[] atypeReturns =
                extractReturnTypes(ctx, atypeParams, asParams, null, ErrorListener.BLACKHOLE);
        return atypeReturns == null
                ? null
                : pool().buildFunctionType(buildParamTypes(), atypeReturns);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        if (errs == null)
            {
            errs = ErrorListener.BLACKHOLE;
            }

        if (!ensurePrepared(errs))
            {
            return TypeFit.NoFit;
            }

        if (isValidated())
            {
            return calcFit(ctx, getType(), typeRequired);
            }

        ConstantPool pool = pool();

        // short-circuit for simple cases (i.e. where any function type will do)
        TypeFit fit = calcFit(ctx, pool.typeFunction(), typeRequired);
        if (fit.isFit())
            {
            return fit;
            }

        assert typeRequired != null; // the calcFit() above would have returned a "Fit""

        if (typeRequired instanceof IntersectionTypeConstant typeInter)
            {
            Set<TypeConstant> setFunctions = typeInter.collectMatching(pool.typeFunction(), null);
            for (TypeConstant typeFunction : setFunctions)
                {
                TypeConstant[] atypeReqParams  = pool.extractFunctionParams(typeFunction);
                TypeConstant[] atypeReqReturns = pool.extractFunctionReturns(typeFunction);

                fit = calculateTypeFitImpl(ctx, atypeReqParams, atypeReqReturns, errs);
                if (fit.isFit())
                    {
                    return fit;
                    }
                }
            return TypeFit.NoFit;
            }
        else
            {
            TypeConstant[] atypeReqParams  = pool.extractFunctionParams(typeRequired);
            TypeConstant[] atypeReqReturns = pool.extractFunctionReturns(typeRequired);

            return calculateTypeFitImpl(ctx, atypeReqParams, atypeReqReturns, errs);
            }
        }

    private TypeFit calculateTypeFitImpl(Context ctx, TypeConstant[] atypeReqParams,
                                         TypeConstant[] atypeReqReturns, ErrorListener errs)
        {
        int cParams = getParamCount();
        if (atypeReqParams != null && atypeReqParams.length == cParams && atypeReqReturns != null)
            {
            String[]       asParams    = cParams == 0   ? NO_NAMES : new String[cParams];
            TypeConstant[] atypeParams = cParams == 0   ? TypeConstant.NO_TYPES : new TypeConstant[cParams];

            if (!collectParamNamesAndTypes(atypeReqParams, atypeParams, asParams, errs))
                {
                return TypeFit.NoFit;
                }

            TypeConstant[] atypeReturns =
                    extractReturnTypes(ctx, atypeParams, asParams, atypeReqReturns, errs);
            if (atypeReturns == null)
                {
                return TypeFit.NoFit;
                }

            int cReturns = atypeReturns.length;
            if (cReturns == 0 && isPending(atypeReqReturns))
                {
                // the lambda thinks that it's void and the caller didn't have an opinion
                return TypeFit.Fit;
                }

            int cReqReturns = atypeReqReturns.length;
            if (cReqReturns <= cReturns)
                {
                for (int i = 0; i < cReqReturns; i++)
                    {
                    TypeConstant typeReturn = atypeReturns[i];

                    if (typeReturn == null || !typeReturn.isA(atypeReqReturns[i]))
                        {
                        return TypeFit.NoFit;
                        }
                    }
                return TypeFit.Fit;
                }
            }
        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        if (!ensurePrepared(errs))
            {
            return finishValidation(ctx, typeRequired, null, TypeFit.NoFit, null, errs);
            }

        // validation only occurs once, but we'll put some extra checks in up front, because we do
        // weird stuff on lambdas like cloning the AST so that we can pass over it once for the
        // expression validation, but use another copy of the body for the lambda method/function
        // compilation itself
        assert m_typeRequired == null && m_collector == null && m_lambda == null;

        // extract the required types for the parameters and return values
        ConstantPool   pool            = pool();
        TypeConstant   typeReqFn       = null;
        TypeConstant[] atypeReqParams  = null;
        TypeConstant[] atypeReqReturns = null;

        if (typeRequired != null)
            {
            typeReqFn = typeRequired = typeRequired.resolveTypedefs();
            if (typeRequired instanceof IntersectionTypeConstant typeInter)
                {
                Set<TypeConstant> setFunctions = typeInter.collectMatching(pool.typeFunction(), null);
                for (TypeConstant typeFunction : setFunctions)
                    {
                    TypeConstant[] atypeTestP = pool.extractFunctionParams(typeFunction);
                    TypeConstant[] atypeTestR = pool.extractFunctionReturns(typeFunction);

                    if (calculateTypeFitImpl(ctx, atypeTestP, atypeTestR, errs).isFit())
                        {
                        atypeReqParams  = atypeTestP;
                        atypeReqReturns = atypeTestR;
                        typeReqFn       = pool.buildFunctionType(atypeReqParams,
                                            replacePending(atypeReqReturns));
                        break;
                        }
                    }
                }
            else if (typeRequired.isA(pool.typeFunction()))
                {
                atypeReqParams  = pool.extractFunctionParams(typeRequired);
                atypeReqReturns = pool.extractFunctionReturns(typeRequired);
                typeReqFn       = pool.buildFunctionType(atypeReqParams,
                                    replacePending(atypeReqReturns));
                }
            }

        boolean fValid      = true;
        int     cReqParams  = atypeReqParams  == null ? 0 : atypeReqParams.length;
        int     cReqReturns = atypeReqReturns == null ? 0 : atypeReqReturns.length;
        int     cParams     = getParamCount();

        if (atypeReqParams != null && cParams != cReqParams)
            {
            errs.log(Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT,
                    new Object[]{cReqParams, cParams},
                    getSource(), getStartPosition(), operator.getStartPosition());
            fValid = false;
            }

        // evaluate the parameter declarations
        String[]       asParams    = cParams == 0 ? NO_NAMES : new String[cParams];
        TypeConstant[] atypeParams = cParams == 0 ? TypeConstant.NO_TYPES : new TypeConstant[cParams];

        fValid &= collectParamNamesAndTypes(atypeReqParams, atypeParams, asParams, errs);

        // even if we know we cannot proceed, we need to validate the lambda to report on errors
        // that would not have been otherwise reported
        m_typeRequired = typeReqFn;
        m_lambda       = instantiateLambda(errs);

        // the logic below is basically a copy of the "extractReturnTypes" method,
        // which we couldn't use since we need two things back: the types and the new context
        StatementBlock blockTemp = (StatementBlock) body.clone();
        if (!new StageMgr(blockTemp, Stage.Validated, errs).fastForward(20))
            {
            blockTemp.discard(true);
            return null;
            }

        LambdaContext ctxLambda = enterCapture(ctx, blockTemp, atypeParams, asParams);
        StatementBlock blockNew = (StatementBlock) blockTemp.validate(ctxLambda, errs);
        if (blockNew == null)
            {
            blockTemp.discard(true);
            fValid = false;
            }
        else
            {
            // we do NOT store off the validated block; the block does NOT belong to the lambda
            // expression; rather, it belongs to the function (m_lambda) that we created, and the
            // real (not temp) block will get validated and compiled by generateCode() above
            blockNew.discard(true);
            }

        // collected VAS information from the lambda context
        ctxLambda.exit();

        if (!fValid)
            {
            return null;
            }

        TypeFit        fit = TypeFit.Fit;
        TypeConstant[] atypeRets;
        boolean        fCond;
        if (m_collector == null)
            {
            // the lambda is a void function that is missing a closing return; the statement block
            // will automatically add a trailing "return" when it is compiled
            atypeRets = TypeConstant.NO_TYPES;
            fCond     = false;
            if (cReqReturns > 0)
                {
                if (isPending(atypeReqReturns))
                    {
                    // the lambda thinks that it's void and the caller didn't have an opinion;
                    // make it void indeed
                    atypeRets = new TypeConstant[cReqReturns];
                    Arrays.fill(atypeRets, pool.typeTuple0());
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.RETURN_EXPECTED);
                    fit = TypeFit.NoFit;
                    }
                }
            }
        else
            {
            atypeRets   = m_collector.inferMulti(atypeReqReturns);
            fCond       = cReqReturns > 1 && m_collector.isConditional();
            m_collector = null;

            if (atypeRets == null)
                {
                atypeRets = cReqReturns == 0 ? TypeConstant.NO_TYPES : atypeReqReturns;
                fit       = TypeFit.NoFit;
                }
            else
                {
                int cReturns = atypeRets.length;
                if (cReturns > cReqReturns)
                    {
                    // the lambda has more return values that the caller needs; adjust it
                    atypeRets = Arrays.copyOfRange(atypeRets, 0, cReqReturns);
                    }
                }
            }

        TypeConstant   typeActual = null;
        MethodConstant constVal   = null;
        if (fit.isFit())
            {
            // while we have enough info at this point to build a signature, what we lack is the
            // effectively final data that will only get reported (via exit() on the context) as
            // the variables go out of scope in the method body that contains this lambda, so we need
            // to store off the data from the capture context, and defer the signature creation to the
            // generateAssignment() method
            m_ctxLambda = ctxLambda;

            typeActual = fCond
                ? pool.buildConditionalFunctionType(atypeParams, atypeRets)
                : pool.buildFunctionType(atypeParams, atypeRets);

            if (ctxLambda.getCaptureMap().isEmpty() &&
                ctxLambda.getFormalMap().isEmpty() &&
                !ctxLambda.isLambdaMethod())
                {
                // there are no bindings, so the lambda is a constant i.e. the function is the value
                configureLambda(atypeParams, asParams, 0, null, atypeRets);
                constVal = m_lambda.getIdentityConstant();
                }
            }

        return finishValidation(ctx, typeRequired, typeActual, fit, constVal, errs);
        }

    /**
     * Collect the parameter types and names into the specified arrays.
     *
     * @param atypeReqParams  the required types
     * @param atypeParams     the array to collect types into
     * @param asParams        the array to collect names into
     * @param errs            the error listener
     *
     * @return true iff there were no errors
     */
    protected boolean collectParamNamesAndTypes(TypeConstant[] atypeReqParams,
                                                TypeConstant[] atypeParams, String[] asParams,
                                                ErrorListener errs)
        {
        boolean fValid     = true;
        int     cReqParams = atypeReqParams == null ? 0 : atypeReqParams.length;
        int     cParams    = atypeParams.length;

        if (hasOnlyParamNames())
            {
            if (atypeReqParams == null)
                {
                errs.log(Severity.ERROR, Compiler.PARAMETER_TYPES_REQUIRED, null,
                        getSource(), paramNames.get(0).getStartPosition(),
                        paramNames.get(cParams-1).getEndPosition());
                fValid = false;
                }

            Set<String> setNames = new HashSet<>();
            for (int i = 0; i < cParams; ++i)
                {
                Expression expr = paramNames.get(i);
                if (expr instanceof NameExpression exprName)
                    {
                    String sName = exprName.getName();
                    asParams[i] = sName;
                    if (!(expr instanceof IgnoredNameExpression))
                        {
                        if (!setNames.add(sName))
                            {
                            expr.log(errs, Severity.ERROR, Compiler.DUPLICATE_PARAMETER, sName);
                            fValid = false;
                            }
                        }
                    }
                else
                    {
                    expr.log(errs, Severity.ERROR, Compiler.NAME_REQUIRED);
                    fValid = false;
                    }

                atypeParams[i] = i < cReqParams ? atypeReqParams[i] : pool().typeObject();
                }
            }
        else
            {
            Set<String> setNames = new HashSet<>();
            for (int i = 0; i < cParams; ++i)
                {
                Parameter param = params.get(i);
                String    sName = asParams[i] = param.getName();

                if (!sName.equals(Id.ANY.TEXT) && !setNames.add(sName))
                    {
                    param.log(errs, Severity.ERROR, Compiler.DUPLICATE_PARAMETER, sName);
                    asParams[i] = Id.ANY.TEXT;
                    fValid      = false;
                    }

                TypeConstant typeParam = param.getType().ensureTypeConstant();
                if (typeParam.containsUnresolved())
                    {
                    atypeParams[i] = pool().typeObject();
                    log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, typeParam.getValueString());
                    fValid = false;
                    }
                else
                    {
                    atypeParams[i] = typeParam;
                    if (i < cReqParams)
                        {
                        // the types don't have to match exactly, but the lambda must not attempt to
                        // narrow the required type for a parameter
                        TypeConstant typeReq = atypeReqParams[i];
                        if (typeReq != null && !typeReq.isA(typeParam))
                            {
                            param.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                    typeReq.getValueString(), typeParam.getValueString());
                            fValid = false;
                            }
                        }
                    }
                }
            }

        return fValid;
        }

    /**
     * Extract the return types from this lambda expression without changing its state.
     *
     * @param ctx          the context
     * @param atypeParams  the type parameters
     * @param asParams     the parameter names
     * @param atypeReturns (optional) the required return types
     * @param errs         the error list to log to
     *
     * @return an array of return types; null if the return types could not be calculated
     */
    private TypeConstant[] extractReturnTypes(Context ctx,
                                              TypeConstant[] atypeParams, String[] asParams,
                                              TypeConstant[] atypeReturns, ErrorListener errs)
        {
        // clone the body (to avoid damaging the original) and validate it to calculate its type
        StatementBlock blockTemp = (StatementBlock) body.clone();

        if (!new StageMgr(blockTemp, Stage.Validated, errs).fastForward(20))
            {
            blockTemp.discard(true);
            return null;
            }

        // prior to calling "blockTemp.validate()" below we need to prime the expected lambda type;
        // note that only the return types portion is going to be used via "getReturnTypes()" method
        if (atypeReturns != null)
            {
            m_typeRequired = pool().buildFunctionType(atypeParams, replacePending(atypeReturns));
            }

        // use a black-hole context (to avoid damaging the original)
        Context ctxTemp = ctx.enter();
        ctxTemp   = enterCapture(ctxTemp, blockTemp, atypeParams, asParams);
        blockTemp = (StatementBlock) blockTemp.validate(ctxTemp, errs);
        ctxTemp.discard();

        try
            {
            // the resulting returned types come back in m_collector (if everything succeeds)
            if (blockTemp == null)
                {
                return null;
                }

            // extract return types
            if (m_collector == null)
                {
                return TypeConstant.NO_TYPES;
                }

            return m_collector.inferMulti(atypeReturns); // TODO conditional
            }
        finally
            {
            m_collector    = null;
            m_typeRequired = null;

            if (blockTemp != null)
                {
                blockTemp.discard(true);
                }
            }
        }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        Argument[] aargBind = calculateBindings(ctx, code, errs);
        if (m_lambda.isFunction() && aargBind.length == 0)
            {
            // if no binding is required (either MBIND or FBIND) then the resulting argument is the
            // identity of the lambda function itself
            return m_lambda.getIdentityConstant();
            }

        return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        Argument[] aBindArgs = calculateBindings(ctx, code, errs);
        int        cBindArgs = aBindArgs.length;

        boolean fBindTarget = !m_lambda.isFunction();
        boolean fBindParams = cBindArgs > 0;

        int[] anBind = null;
        if (fBindParams)
            {
            anBind = new int[cBindArgs];
            for (int i = 0; i < cBindArgs; ++i)
                {
                anBind[i] = i;
                }
            }

        MethodConstant idLambda = m_lambda.getIdentityConstant();
        if (fBindTarget | fBindParams)
            {
            if (LVal.isLocalArgument())
                {
                Argument argResult = LVal.getLocalArgument();
                if (fBindTarget & fBindParams)
                    {
                    Register regThis = ctx.generateThisRegister(code, false, errs);
                    Register regTemp = new Register(idLambda.getSignature().asFunctionType(), Op.A_STACK);
                    code.add(new MBind(regThis, idLambda, regTemp));
                    code.add(new FBind(regTemp, anBind, aBindArgs, argResult));
                    }
                else if (fBindTarget)
                    {
                    Register regThis = ctx.generateThisRegister(code, false, errs);
                    code.add(new MBind(regThis, idLambda, argResult));
                    }
                else if (fBindParams)
                    {
                    code.add(new FBind(idLambda, anBind, aBindArgs, argResult));
                    }
                }
            else
                {
                super.generateAssignment(ctx, code, LVal, errs);
                }
            }
        else
            {
            // neither target nor param binding
            LVal.assign(idLambda, code, errs);
            }
        }

    @Override
    protected void discard(boolean fRecurse)
        {
        super.discard(fRecurse);

        if (m_lambda != null)
            {
            m_lambda.getParent().removeChild(m_lambda);
            m_lambda = null;
            }
        }

    @Override
    public AstNode clone()
        {
        // the reference to the lambda's method structure should not be a part of the cloned state
        LambdaExpression exprClone = (LambdaExpression) super.clone();
        exprClone.m_lambda = null;
        return exprClone;
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * This is used to catch up the parts of the lambda expression that are necessary to work with
     * the lambda within its containing method, such that it can be asked questions about type
     * information, etc.
     *
     * @param errs  the error list to log to
     *
     * @return true if the lambda expression was able to prepare (catch up)
     */
    protected boolean ensurePrepared(ErrorListener errs)
        {
        if (m_fPrepared || params == null || params.isEmpty())
            {
            return m_fPrepared = true;
            }

        boolean fPrepared = true;
        for (AstNode param : params)
            {
            fPrepared &= new StageMgr(param, Stage.Validated, errs).fastForward(20);
            }

        return m_fPrepared = fPrepared;
        }

    /**
     * For lambdas that do NOT use the "names only" form of parameter declaration, determine the
     * parameter types.
     *
     * @return an array of the parameter types
     */
    protected TypeConstant[] buildParamTypes()
        {
        if (!hasParameters())
            {
            return TypeConstant.NO_TYPES;
            }

        assert !hasOnlyParamNames();

        List<Parameter> list   = params;
        int             cTypes = list.size();
        TypeConstant[]  aTypes = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; ++i)
            {
            Parameter param = list.get(i);
            aTypes[i] = param.getType().ensureTypeConstant();
            }
        return aTypes;
        }

    MethodStructure instantiateLambda(ErrorListener errs)
        {
        TypeConstant[] atypes   = null;
        String[]       asParams;
        if (paramNames == null)
            {
            // build an array of types and an array of names
            int cParams = params == null ? 0 : params.size();
            atypes   = new TypeConstant[cParams];
            asParams = new String[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Parameter param = params.get(i);
                atypes  [i] = param.getType().ensureTypeConstant();
                asParams[i] = param.getName();
                }
            }
        else
            {
            // build an array of names
            int cParams = paramNames.size();
            asParams = new String[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Expression expr = paramNames.get(i);
                if (expr instanceof NameExpression exprName)
                    {
                    // note: could also be an IgnoredNameExpression
                    asParams[i] = exprName.getName();
                    }
                else
                    {
                    expr.log(errs, Severity.ERROR, Compiler.NAME_REQUIRED);
                    asParams[i] = Id.ANY.TEXT;
                    }
                }
            }

        Component            container = getParent().getComponent();
        MultiMethodStructure structMM  = container.ensureMultiMethodStructure(METHOD_NAME);
        MethodStructure      method    =  structMM.createLambda(atypes, asParams);
        donateSource(method);
        return method;
        }

    /**
     * Determine if MBIND and/or FBIND is necessary. If MBIND is necessary, then make the structure
     * used for the lambda into a method (not a function). If FBIND is necessary, then build the
     * list of arguments needed to bind those parameters. Regardless, build the actual signature of
     * the structure used for the lambda.
     *
     * @param ctx   the compilation context for the lambda
     * @param code  the code block being compiled into
     * @param errs  the error list to log any errors to
     *
     * @return an array of arguments to pass to FBIND
     */
    protected Argument[] calculateBindings(Context ctx, Code code, ErrorListener errs)
        {
        MethodStructure lambda    = m_lambda;
        LambdaContext   ctxLambda = m_ctxLambda;

        assert lambda != null && lambda.isLambda();
        assert ctxLambda != null;

        MethodConstant idLambda = lambda.getIdentityConstant();
        if (idLambda.isNascent())
            {
            // this is the first time that we have a chance to put together the signature, because
            // this is the first time that we are being called after validate()
            ConstantPool          pool         = ctx.pool();
            TypeConstant          typeFn       = getType();
            String[]              asParams     = getParamNames();
            TypeConstant[]        atypeParams  = pool.extractFunctionParams(typeFn);
            TypeConstant[]        atypeReturns = pool.extractFunctionReturns(typeFn);
            Map<String, Argument> mapFormal    = ctxLambda.getFormalMap();
            Map<String, Boolean>  mapCapture   = ctxLambda.getCaptureMap();
            int                   cTypeParams  = mapFormal.size();
            int                   cCaptures    = mapCapture.size();

            // there is a chance that a formal type has been also captured; dedupe it
            if (cTypeParams > 0 && cCaptures > 0
                    && mapCapture.keySet().removeAll(mapFormal.keySet()))
                {
                cCaptures = mapCapture.size();
                }

            int        cBindArgs       = cTypeParams + cCaptures;
            Argument[] aBindArgs       = NO_RVALUES;
            boolean[]  afImplicitDeref = null;

            // MBIND is indicated by the method structure *NOT* being static
            lambda.setStatic(!ctxLambda.isLambdaMethod());

            // FBIND is indicated by >0 bind arguments being returned from this method
            if (cBindArgs > 0)
                {
                Map<String, Register> mapRegisters   = ctxLambda.ensureRegisterMap();
                int                   cLambdaParams  = atypeParams.length;
                int                   cAllParams     = cBindArgs + cLambdaParams;
                TypeConstant[]        atypeAllParams = new TypeConstant[cAllParams];
                String[]              asAllParams    = new String[cAllParams];
                int                   iParam         = 0;

                aBindArgs = new Argument[cBindArgs];

                Map<FormalConstant, TypeConstant> mapRedefine = new HashMap<>();
                for (Entry<String, Argument> entry : mapFormal.entrySet())
                    {
                    String       sCapture  = entry.getKey();
                    Argument     argFormal = entry.getValue();
                    TypeConstant typeReg   = pool.ensureRegisterConstant(
                                                idLambda, iParam, sCapture).getType();
                    TypeConstant typeFormal;
                    Register     regFormal;
                    if (argFormal instanceof TargetInfo infoGeneric)
                        {
                        typeFormal = infoGeneric.getType(); // type of type
                        regFormal  = new Register(typeFormal, Op.A_STACK);

                        // make sure that every lambda's parameter of this generic type is redefined
                        // to point to the corresponding lambda's type parameter
                        PropertyConstant idGeneric = (PropertyConstant) infoGeneric.getId();
                        mapRedefine.put(idGeneric, typeReg);

                        if (infoGeneric.getStepsOut() > 0)
                            {
                            Register regTarget = new Register(infoGeneric.getTargetType(), Op.A_STACK);
                            code.add(new MoveThis(infoGeneric.getStepsOut(), regTarget));
                            code.add(new P_Get(idGeneric, regTarget, regFormal));
                            }
                        else
                            {
                            code.add(new L_Get(idGeneric, regFormal));
                            }
                        }
                    else
                        {
                        // the source is a register (type parameter); resolve to its constraint
                        regFormal = (Register) argFormal;

                        // make sure that every lambda's parameter of this formal type is redefined
                        // to point to the corresponding lambda's type parameter
                        TypeConstant typeParam = regFormal.getType().getParamType(0);
                        mapRedefine.put((TypeParameterConstant) typeParam.getDefiningConstant(), typeReg);

                        typeFormal = typeParam.resolveConstraints().getType();
                        }

                    asAllParams   [iParam] = sCapture;
                    atypeAllParams[iParam] = typeFormal;
                    aBindArgs     [iParam] = regFormal;

                    iParam++;
                    }

                // Note: the MoveRef ops are allowed to push arguments on the stack since the
                // run-time knows to load them up in the inverse order
                for (Entry<String, Boolean> entry : mapCapture.entrySet())
                    {
                    String       sCapture       = entry.getKey();
                    Register     regCapture     = mapRegisters.get(sCapture);
                    TypeConstant typeCapture    = regCapture.getType();
                    boolean      fImplicitDeref = false;

                    if (regCapture instanceof LabelVar)
                        {
                        log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED,
                                "Label capturing (\"" + sCapture + "\")");
                        }

                    regCapture = regCapture.getOriginalRegister(); // remove any inferences
                    if (entry.getValue())
                        {
                        // it's a read/write capture; capture the Var
                        typeCapture = regCapture.isVar()
                                ? regCapture.ensureRegType(true)
                                : pool.ensureParameterizedTypeConstant(pool.typeVar(), typeCapture);
                        Register regVal = regCapture;
                        Register regVar = new Register(typeCapture, Op.A_STACK);
                        code.add(new MoveVar(regVal, regVar));
                        regCapture = regVar;
                        fImplicitDeref = true;
                        }
                    else if (!regCapture.isEffectivelyFinal())
                        {
                        // it's a read-only capture, but since we were unable to prove that the
                        // register was effectively final, we need to capture the Ref
                        typeCapture = regCapture.isVar()
                                ? regCapture.ensureRegType(true)
                                : pool.ensureParameterizedTypeConstant(pool.typeVar(), typeCapture);
                        Register regVal = regCapture;
                        Register regVar = new Register(typeCapture, Op.A_STACK);
                        code.add(new MoveRef(regVal, regVar));
                        regCapture     = regVar;
                        fImplicitDeref = true;
                        }

                    asAllParams   [iParam] = sCapture;
                    atypeAllParams[iParam] = typeCapture;
                    aBindArgs     [iParam] = regCapture;

                    if (fImplicitDeref)
                        {
                        if (afImplicitDeref == null)
                            {
                            afImplicitDeref = new boolean[cBindArgs];
                            }
                        afImplicitDeref[iParam] = true;
                        }

                    ++iParam;
                    }
                assert iParam == cBindArgs;

                System.arraycopy(atypeParams, 0, atypeAllParams, cBindArgs, cLambdaParams);
                System.arraycopy(asParams   , 0, asAllParams   , cBindArgs, cLambdaParams);
                atypeParams = atypeAllParams;
                asParams    = asAllParams;

                if (!mapRedefine.isEmpty())
                    {
                    GenericTypeResolver resolver = new GenericTypeResolver()
                        {
                        @Override
                        public TypeConstant resolveGenericType(String sFormalName)
                            {
                            return null;
                            }

                        @Override
                        public TypeConstant resolveFormalType(FormalConstant constFormal)
                            {
                            return mapRedefine.get(constFormal);
                            }
                        };

                    for (int i = cTypeParams, c = atypeParams.length; i < c; i++)
                        {
                        TypeConstant typeOld = atypeParams[i].resolveTypedefs();
                        TypeConstant typeNew = typeOld.resolveGenerics(pool, resolver);
                        if (typeNew != typeOld)
                            {
                            atypeParams[i] = typeNew;
                            }
                        }
                    }
                }
            m_aBindArgs = aBindArgs;

            // store the resulting signature for the lambda
            configureLambda(atypeParams, asParams, cTypeParams, afImplicitDeref, atypeReturns);
            }

        return m_aBindArgs;
        }

    /**
     * Configure the lambda's parameters, and fill in the lambda's signature information.
     *
     * @param atypeParams     the type of each lambda parameter
     * @param asParams        the name of each lambda parameter
     * @param cFormal         the number of formal type parameters
     * @param afImpliedDeref  indicates whether each lambda parameter needs an implicit de-reference
     * @param atypeRets       the type of each lambda return value
     */
    protected void configureLambda(TypeConstant[] atypeParams, String[] asParams, int cFormal,
            boolean[] afImpliedDeref, TypeConstant[] atypeRets)
        {
        MethodStructure   lambda = m_lambda;
        ConstantPool      pool   = pool();
        SignatureConstant sig    = pool.ensureSignatureConstant(METHOD_NAME, atypeParams, atypeRets);

        int cParams = atypeParams.length;
        int cNames  = asParams.length;
        org.xvm.asm.Parameter[] aparamParams = new org.xvm.asm.Parameter[cParams];
        for (int i = 0; i < cParams; ++i)
            {
            String sName = i < cNames ? asParams[i] : null;
            aparamParams[i] = new org.xvm.asm.Parameter(pool, atypeParams[i], sName, null, false, i, i < cFormal);

            // check if the parameter needs to be marked as being an implicit de-reference
            if (afImpliedDeref != null && afImpliedDeref.length > i && afImpliedDeref[i])
                {
                aparamParams[i].markImplicitDeref();
                }
            }

        // TODO support "conditional"
        int cRets = atypeRets.length;
        org.xvm.asm.Parameter[] aparamRets = new org.xvm.asm.Parameter[cRets];
        for (int i = 0; i < cRets; ++i)
            {
            aparamRets[i] = new org.xvm.asm.Parameter(pool, atypeRets[i], null, null, true, i, false);
            }

        lambda.configureLambda(aparamParams, cFormal, aparamRets);
        lambda.getIdentityConstant().setSignature(sig);
        }

    /**
     * @return the LambdaContext stored off after the successful validation
     */
    protected LambdaContext getValidatedContext()
        {
        assert isValidated();
        return m_ctxLambda;
        }

    /**
     * @return true iff lambda requires "this"
     */
    protected boolean isRequiredThis()
        {
        assert isValidated();
        return m_ctxLambda.isLambdaMethod();
        }

    /**
     * Replace all PendingTypeConstants in the specified array with the Object type.
     */
    private TypeConstant[] replacePending(TypeConstant[] atype)
        {
        TypeConstant[] atypeRets = atype;
        for (int i = 0, c = atype == null ? 0 : atype.length; i < c; i++)
            {
            if (atype[i] instanceof PendingTypeConstant)
                {
                if (atypeRets == atype)
                    {
                    atypeRets = atype.clone();
                    }
                atypeRets[i] = pool().typeObject();
                }
            }
        return atypeRets;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        boolean first = true;
        for (Object param : (params == null ? paramNames : params))
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(param);
            }

        sb.append(')')
          .append(' ')
          .append(operator.getId().TEXT);

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        String s = body.toString();
        if (s.indexOf('\n') >= 0)
            {
            sb.append('\n')
              .append(indentLines(s, "    "));
            }
        else
            {
            sb.append(' ')
              .append(s);
            }

        return sb.toString();
        }

    @Override
    public String toDumpString()
        {
        return toSignatureString() + " {...}";
        }


    // ----- CaptureContext ------------------------------------------------------------------------

    /**
     * Create a context that bridges from the current context into a special compilation mode in
     * which the values (or references / variables) of the outer context can be <i>captured</i>.
     *
     * @param ctx          the current (soon to be outer) context
     * @param body         the StatementBlock of the lambda, anonymous inner class, or statement
     *                     expression
     * @param atypeParams  types of the explicit parameters for the context (e.g. for a lambda)
     * @param asParams     names of the explicit parameters for the context (e.g. for a lambda)
     *
     * @return a capturing context
     */
    protected static LambdaContext enterCapture(Context ctx, StatementBlock body,
                                                TypeConstant[] atypeParams, String[] asParams)
        {
        return new LambdaContext(ctx, body, atypeParams, asParams);
        }

    /**
     * A context for compiling lambda expressions.
     */
    public static class LambdaContext
            extends CaptureContext
        {
        /**
         * Construct a Lambda CaptureContext.
         *
         * @param ctxOuter     the context within which this context is nested
         * @param body         the StatementBlock of the lambda / inner class, whose parent is one
         *                     of: NewExpression, LambdaExpression, or StatementExpression
         * @param atypeParams  types of the explicit parameters for the context (e.g. for a lambda)
         * @param asParams     names of the explicit parameters for the context (e.g. for a lambda)
         */
        public LambdaContext(Context ctxOuter, StatementBlock body, TypeConstant[] atypeParams,
                             String[] asParams)
            {
            super(ctxOuter);

            assert atypeParams == null && asParams == null
                    || atypeParams != null && asParams != null && atypeParams.length == asParams.length;
            f_atypeParams = atypeParams;
            f_asParams    = asParams;
            }

        @Override
        protected void promoteNonCompleting(Context ctxInner)
            {
            // Lambda's non-completion has no effect on the parent's context
            }

        @Override
        public void requireThis(long lPos, ErrorListener errs)
            {
            getOuterContext().requireThis(lPos, errs);
            captureThis();
            }

        @Override
        protected void markVarRead(boolean fNested, String sName, Token tokName, boolean fDeref,
                                   ErrorListener errs)
            {
            // variable capture will create a parameter (a variable in this scope) for the lambda,
            // so if the variable isn't already declared in this scope, but it exists in the outer
            // scope, then capture it
            Context ctxOuter = getOuterContext();
            if (!isVarDeclaredInThisScope(sName) && ctxOuter.isVarReadable(sName))
                {
                if (isReservedName(sName))
                    {
                    boolean fAllowConstructor = false;
                    switch (sName)
                        {
                        // the only names that we capture _without_ a capture parameter are the
                        // various "this" references that refer to "this" object
                        case "this":
                        case "this:struct":
                        case "this:class":
                            fAllowConstructor = true;
                            // fall through
                        case "this:target":
                        case "this:public":
                        case "this:protected":
                        case "this:private":
                            if (fAllowConstructor && ctxOuter.isConstructor())
                                {
                                captureThis();
                                }
                            else
                                {
                                requireThis(tokName.getStartPosition(), errs);
                                }
                            return;

                        case "this:service":
                        case "this:module":
                            // these two are available globally, and are _not_ captured
                            return;
                        }
                    }
                }

            super.markVarRead(fNested, sName, tokName, fDeref, errs);
            }

        /**
         * @return true iff the lambda is built as a method (and not as a function) in order to
         *         capture the "this" object reference
         */
        public boolean isLambdaMethod()
            {
            return isThisCaptured();
            }

        @Override
        protected boolean hasInitialNames()
            {
            return true;
            }

        @Override
        public Map<String, Argument> getFormalMap()
            {
            // if the lambda requires "this", there is no need to capture the generic types
            Map<String, Argument> mapFormal = super.getFormalMap();
            if (isLambdaMethod() && !mapFormal.isEmpty())
                {
                mapFormal = new HashMap<>(mapFormal);
                mapFormal.values().removeIf(arg ->
                    {
                    TypeConstant type = arg.getType();
                    return type.isTypeOfType() && type.getParamType(0).isGenericType();
                    });
                }
            return mapFormal;
            }

        @Override
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            Context        ctxOuter    = getOuterContext();
            TypeConstant[] atypeParams = f_atypeParams;
            String[]       asParams    = f_asParams;
            int            cParams     = atypeParams == null ? 0 : atypeParams.length;
            for (int i = 0; i < cParams; ++i)
                {
                TypeConstant type  = atypeParams[i];
                String       sName = asParams[i];
                if (!sName.equals(Id.ANY.TEXT) && type != null)
                    {
                    Register     reg          = new Register(type);
                    TypeConstant typeNarrowed = ctxOuter.resolveFormalType(type);
                    if (typeNarrowed != type)
                        {
                        reg = reg.narrowType(typeNarrowed);
                        reg.markInPlace();
                        }
                    mapByName.put(sName, reg);

                    // the variable has been definitely assigned, but not multiple times (i.e. it's
                    // still effectively final)
                    ensureDefiniteAssignments().put(sName, Assignment.AssignedOnce);
                    }
                }
            }

        private final TypeConstant[] f_atypeParams;
        private final String[]       f_asParams;
        }


    // ----- fields --------------------------------------------------------------------------------

    private static final String[] NO_NAMES    = Handy.NO_ARGS;
    private static final String   METHOD_NAME = "->";

    protected List<Parameter>  params;
    protected List<Expression> paramNames;
    protected Token            operator;
    protected StatementBlock   body;
    protected long             lStartPos;

    /**
     * Set to true after the expression prepares.
     */
    private transient boolean m_fPrepared;
    /**
     * The required type (stored here so that it can be picked up by other nodes below this node in
     * the AST).
     */
    private transient TypeConstant m_typeRequired;
    /**
     * A list of types from various return statements (collected here from information provided by
     * other nodes below this node in the AST).
     */
    private transient TypeCollector m_collector;
    /**
     * The lambda structure itself.
     */
    private transient MethodStructure m_lambda;
    /**
     * The LambdaContext that collected all the necessary information during validation.
     */
    private transient LambdaContext m_ctxLambda;
    /**
     * A cached array of bound arguments. Private to calculateBindings().
     */
    private transient Argument[] m_aBindArgs = NO_RVALUES;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(LambdaExpression.class, "params", "paramNames", "body");
    }