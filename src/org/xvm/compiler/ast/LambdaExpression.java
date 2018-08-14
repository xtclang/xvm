package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;

import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.FBind;
import org.xvm.asm.op.MBind;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.CaptureContext;
import org.xvm.compiler.ast.Statement.Context;

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
     *
     * @param params     either a list of Expression objects or a list of Parameter objects
     * @param operator
     * @param body
     * @param lStartPos
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
            assert params.stream().allMatch(org.xvm.asm.Parameter.class::isInstance);
            this.params = params;
            }

        this.operator  = operator;
        this.body      = body;
        this.lStartPos = lStartPos;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Component getComponent()
        {
        MethodStructure method = m_lambda;
        return method == null
                ? super.getComponent()
                : method;
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


    // ----- compilation (Statement) ---------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        // just like the MethodDeclarationStatement, lambda expressions are considered to be
        // completely opaque, and so a lambda defers the processing of its children at this point,
        // because it wants everything around its children to be set up by the time those children
        // need to be able to answer all of the questions about names and types and so on
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
    public void validateExpressions(StageMgr mgr, ErrorListener errs)
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

        checkDebug(); // TODO remove

        // if the lambda was somehow determined to produce a constant value with no side-effects,
        // then there shouldn't be a lambda body and we're already done here
        if (isConstant())
            {
            assert method == null;
            mgr.deferChildren();
            return;
            }

        // the method body containing this must validate the lambda expression, which then will
        // create the lambda body (the method structure), and that has to happen before we try to
        // spit out any code
        if (method == null)
            {
            mgr.requestRevisit();
            mgr.deferChildren();
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
        //   final signature for the lambda, including all of the parameters necessary to capture
        //   the various variables in the lexical scope of the lambda declaration that needed to be
        //   passed to the lambda (via fbind)
        // - so now, at this point, we have the signature, we have the method structure, and we just
        //   have to emit the code corresponding to the lambda
        if (catchUpChildren(errs))
            {
            body.compileMethod(method.createCode(), errs);
            }
        }


    // ----- compilation (Expression) --------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (isValidated())
            {
            return getType();
            }

        checkDebug(); // TODO remove

        assert m_typeRequired == null && m_listRetTypes == null;

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

        // clone the body (to avoid damaging the original) and validate it to calculate its type
        StatementBlock blockTemp = (StatementBlock) body.clone();

        // use a black-hole context (to avoid damaging the original)
        ctx = new BlackholeContext(ctx);

        // create a capture context for the lambda
        int     cParams     = getParamCount();
        String[]       asParams    = cParams == 0 ? NO_NAMES : new String[cParams];
        TypeConstant[] atypeParams = cParams == 0 ? TypeConstant.NO_TYPES : new TypeConstant[cParams];
        Set<String>    setNames    = new HashSet<>();
        for (int i = 0; i < cParams; ++i)
            {
            Parameter param = params.get(i);
            String    sName = param.getName();
            asParams   [i] = !sName.equals(Id.IGNORED.TEXT) && setNames.add(sName) ? sName : Id.IGNORED.TEXT;
            atypeParams[i] = param.getType().ensureTypeConstant();
            }

        ctx       = ctx.enterCapture(blockTemp, atypeParams, asParams);
        blockTemp = (StatementBlock) blockTemp.validate(ctx, ErrorListener.BLACKHOLE);
        ctx       = ctx.exitScope();

        // the resulting returned types come back in m_listRetTypes (if everything succeeds)
        if (blockTemp == null)
            {
            m_listRetTypes = null;
            return null;
            }

        // extract return types
        TypeConstant[] atypeReturns = TypeConstant.NO_TYPES;
        if (m_listRetTypes != null && !m_listRetTypes.isEmpty())
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;

            TypeConstant typeRet = ListExpression.inferCommonType(aTypes);
            if (typeRet == null)
                {
                return null;
                }

            atypeReturns = new TypeConstant[] {typeRet};
            }

        return buildFunctionType(pool(), buildParamTypes(), atypeReturns);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        if (isValidated())
            {
            return calcFit(ctx, getType(), typeRequired);
            }

        // short-circuit for simple cases
        TypeConstant typeFunction = pool().typeFunction();
        TypeFit fit = calcFit(ctx, typeFunction, typeRequired);
        if (fit.isFit())
            {
            return fit;
            }

        return calcFit(ctx, getImplicitType(ctx), typeRequired);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        checkDebug(); // TODO remove

        // validation only occurs once, but we'll put some extra checks in up front, because we do
        // weird stuff on lambdas like cloning the AST so that we can pass over it once for the
        // expression validation, but use another copy of the body for the lambda method/function
        // compilation itself
        assert m_typeRequired == null && m_listRetTypes == null && m_lambda == null;

        // extract the required types for the parameters and return values
        ConstantPool pool = pool();
        TypeConstant[] atypeReqParams  = null;
        TypeConstant[] atypeReqReturns = null;
        if (typeRequired != null)
            {
            atypeReqParams  = extractParamTypes(pool, typeRequired);
            atypeReqReturns = extractReturnTypes(pool, typeRequired);
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
        if (hasOnlyParamNames())
            {
            if (atypeReqParams == null)
                {
                errs.log(Severity.ERROR, Compiler.PARAMETER_TYPES_REQUIRED, null,
                        getSource(), paramNames.get(0).getStartPosition(), paramNames.get(cParams-1).getEndPosition());
                fValid = false;
                }

            Set<String> setNames = new HashSet<>();
            for (int i = 0; i < cParams; ++i)
                {
                Expression expr = paramNames.get(i);
                if (expr instanceof NameExpression)
                    {
                    String sName = ((NameExpression) expr).getName();
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

                if (i < cReqParams)
                    {
                    atypeParams[i] = atypeReqParams[i];
                    }
                }
            }
        else
            {
            Set<String> setNames = new HashSet<>();
            for (int i = 0; i < cParams; ++i)
                {
                Parameter param = params.get(i);
                String    sName = param.getName();
                asParams[i] = sName;
                if (!sName.equals(Id.IGNORED.TEXT) && !setNames.add(sName))
                    {
                    param.log(errs, Severity.ERROR, Compiler.DUPLICATE_PARAMETER, sName);
                    fValid = false;
                    }

                atypeParams[i] = param.getType().ensureTypeConstant();
                if (i < cReqParams)
                    {
                    // the types don't have to match exactly, but the lambda must not attempt to
                    // narrow the required type for a parameter
                    if (!atypeReqParams[i].isA(atypeParams[i]))
                        {
                        param.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                atypeReqParams[i].getValueString(), atypeParams[i].getValueString());
                        fValid = false;
                        }
                    }
                }
            }

        if (!fValid)
            {
            return finishValidation(typeRequired, null, TypeFit.NoFit, null, errs);
            }

        m_typeRequired = typeRequired;
        m_lambda       = instantiateLambda(errs);

        TypeFit        fit       = TypeFit.Fit;
        StatementBlock blockTemp = (StatementBlock) body.clone();
        CaptureContext ctxLambda = ctx.enterCapture(blockTemp, atypeParams, asParams);
        StatementBlock blockNew  = (StatementBlock) blockTemp.validate(ctxLambda, errs);
        if (blockNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            // we do NOT store off the validated block; the block does NOT belong to the lambda
            // expression; rather, it belongs to the function (m_lambda) that we created, and the
            // real (not temp) block will get validated and compiled by generateCode() above
            }

        // collected VAS information from the lambda context
        ctxLambda.exitScope();

        TypeConstant typeActual = null;
        if (m_listRetTypes != null)
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;
            typeActual = ListExpression.inferCommonType(aTypes);
            }
        if (typeActual == null)
            {
            fit = TypeFit.NoFit;
            }

        // while we have enough info at this point to build a signature, what we lack is the
        // effectively final data that will only get reported (via exitScope() on the context) as
        // the variables go out of scope in the method body that contains this lambda, so we need
        // to store off the data from the capture context, and defer the signature creation to the
        // generateAssignment() method
        m_mapCapture     = ctxLambda.getCaptureMap();
        m_setCaptureRsvd = ctxLambda.getReservedNameSet();

        return finishValidation(typeRequired, typeActual, fit, null, errs);
        }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        checkDebug(); // TODO remove

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
        checkDebug(); // TODO remove

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
                super.generateAssignment(ctx, code, LVal, errs);
                }
            else
                {
                Argument argResult = LVal.getLocalArgument();
                if (fBindTarget & fBindParams)
                    {
                    Register regTemp = new Register(idLambda.getSignature().asFunctionType(), Op.A_STACK);
                    code.add(new MBind(ctx.resolveReservedName("this:target"), idLambda, regTemp));
                    code.add(new FBind(regTemp, anBind, aBindArgs, argResult));
                    }
                else if (fBindTarget)
                    {
                    code.add(new MBind(ctx.resolveReservedName("this:target"), idLambda, argResult));
                    }
                else if (fBindParams)
                    {
                    code.add(new FBind(idLambda, anBind, aBindArgs, argResult));
                    }
                }
            }
        else
            {
            // neither target nor param binding
            LVal.assign(idLambda, code, errs);
            }
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
        assert m_lambda != null && m_lambda.getIdentityConstant().isLambda();
        assert m_mapCapture != null && m_setCaptureRsvd != null;

        if (m_lambda.getIdentityConstant().isNascent())
            {
            // this is the first time that we have a chance to put together the signature, because
            // this is the first time that we are being called after validate()
            TypeConstant   typeFn       = getType();
            ConstantPool   pool         = ctx.pool();
            TypeConstant[] atypeParams  = extractParamTypes(pool, typeFn);
            TypeConstant[] atypeReturns = extractReturnTypes(pool, typeFn);
            boolean        fBindTarget  = false;
            boolean        fBindParams  = false;
            Argument[]     aBindArgs    = NO_RVALUES;

            if (!m_setCaptureRsvd.isEmpty())
                {
                // TODO
                }
            if (!m_mapCapture.isEmpty())
                {
                // TODO
                }

            // store the resulting signature for the lambda
            m_lambda.getIdentityConstant().setSignature(pool.ensureSignatureConstant(METHOD_NAME, atypeParams, atypeReturns));

            // MBIND is indicated by the method structure *NOT* being static
            if (fBindTarget)
                {
                m_lambda.setStatic(false);
                }

            // FBIND is indicated by >0 bind arguments being returned from this method
            m_aBindArgs = aBindArgs;
            }

        return m_aBindArgs;
        }

    // TODO remove
    static LambdaExpression exprDebug;
    void checkDebug()
        {
        if (exprDebug == null && !getComponent().getIdentityConstant().getModuleConstant().toString().contains("Ecstasy"))
            {
            exprDebug = this;
            }
        if (this == exprDebug)
            {
            String s = toString();
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * @return the required type, which is the specified required type during validation, or the
     *         actual type once the expression is validatd
     */
    TypeConstant getRequiredType()
        {
        TypeConstant[] aRetTypes = extractReturnTypes(pool(), isValidated() ? getType() : m_typeRequired);
        return aRetTypes == null || aRetTypes.length == 0 ? null : aRetTypes[0];
        }

    void addReturnType(TypeConstant typeRet)
        {
        List<TypeConstant> list = m_listRetTypes;
        if (list == null)
            {
            m_listRetTypes = list = new ArrayList<>();
            }
        list.add(typeRet);
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

    /**
     * TODO make this a helper somewhere
     *
     * @param pool          the ConstantPool
     * @param atypeParams   the parameter types of the function
     * @param atypeReturns  the return types of the function
     *
     * @return the function type
     */
    public static TypeConstant buildFunctionType(ConstantPool pool, TypeConstant[] atypeParams, TypeConstant[] atypeReturns)
        {
        return pool.ensureParameterizedTypeConstant(
                pool.typeFunction(),
                pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeParams),
                pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeReturns));
        }

    /**
     * TODO make this a helper somewhere
     *
     * @param pool          the ConstantPool
     * @param typeFunction  the type to extract from
     *
     * @return the parameter types for the function, or null if the types cannot be determined
     */
    public static TypeConstant[] extractParamTypes(ConstantPool pool, TypeConstant typeFunction)
        {
        if (typeFunction != null)
            {
            if (typeFunction.isA(pool.typeFunction())
                    && typeFunction.isParamsSpecified()
                    && typeFunction.getParamsCount() > 0)
                {
                TypeConstant typeParams = typeFunction.getParamTypesArray()[0];
                if (typeParams.isA(pool.typeTuple()) && typeParams.isParamsSpecified())
                    {
                    return typeParams.getParamTypesArray();
                    }
                }
            }

        return null;
        }

    /**
     * TODO make this a helper somewhere
     *
     * @param pool          the ConstantPool
     * @param typeFunction  the type to extract from
     *
     * @return the return types of the function, or null if the types cannot be determined
     */
    public static TypeConstant[] extractReturnTypes(ConstantPool pool, TypeConstant typeFunction)
        {
        if (typeFunction != null)
            {
            if (typeFunction.isA(pool.typeFunction())
                    && typeFunction.isParamsSpecified()
                    && typeFunction.getParamsCount() > 1)
                {
                TypeConstant typeParams = typeFunction.getParamTypesArray()[1];
                if (typeParams.isA(pool.typeTuple()) && typeParams.isParamsSpecified())
                    {
                    return typeParams.getParamTypesArray();
                    }
                }
            }

        return null;
        }

    MethodStructure instantiateLambda(ErrorListener errs)
        {
        TypeConstant[] atypes   = null;
        String[]       asParams = null;
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
                if (expr instanceof NameExpression)
                    {
                    // note: could also be an IgnoredNameExpression
                    asParams[i] = ((NameExpression) expr).getName();
                    }
                else
                    {
                    expr.log(errs, Severity.ERROR, Compiler.NAME_REQUIRED);
                    asParams[i] = Id.IGNORED.TEXT;
                    }
                }
            }

        Component            container = getParent().getComponent();
        MultiMethodStructure structMM  = container.ensureMultiMethodStructure(METHOD_NAME);
        MethodStructure      lambda    = structMM.createLambda(atypes, asParams);

        return lambda;
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


    // ----- inner class: ValidatingContext --------------------------------------------------------

    /**
     * A context that can be used to validate without allowing any mutating operations to leak
     * through to the underlying context.
     */
    protected static class BlackholeContext
            extends Context
        {
        /**
         * Construct a ValidatingContext around an actual context.
         *
         * @param ctxOuter  the actual context
         */
        public BlackholeContext(Context ctxOuter)
            {
            super(ctxOuter);
            }

        @Override
        public Context exitScope()
            {
            // no-op (don't push data up to outer scope)

            return super.getOuterContext();
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private static final String[] NO_NAMES = new String[0];

    public static final String METHOD_NAME = "->";

    protected List<Parameter>  params;
    protected List<Expression> paramNames;
    protected Token            operator;
    protected StatementBlock   body;
    protected long             lStartPos;


    /**
     * The required type (stored here so that it can be picked up by other nodes below this node in
     * the AST).
     */
    private transient TypeConstant         m_typeRequired;
    /**
     * A list of types from various return statements (collected here from information provided by
     * other nodes below this node in the AST).
     */
    private transient List<TypeConstant>   m_listRetTypes;
    /**
     * The lambda structure itself.
     */
    private transient MethodStructure      m_lambda;
    /**
     * The variables captured by the lambda, with an associated "true" flag if the lambda needs to
     * capture the variable in a read/write mode.
     */
    private transient Map<String, Boolean> m_mapCapture;
    /**
     * The reserved names captured by the lambda.
     */
    private transient Set<String>          m_setCaptureRsvd;
    /**
     * A cached array of bound arguments. Private to calculateBindings().
     */
    private transient Argument[]           m_aBindArgs;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LambdaExpression.class, "params", "paramNames", "body");
    }
