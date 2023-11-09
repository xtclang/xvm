package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Version;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.BindFunctionAST;
import org.xvm.asm.ast.BindMethodAST;
import org.xvm.asm.ast.CallExprAST;
import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ConvertExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.InvokeExprAST;
import org.xvm.asm.ast.OuterExprAST;
import org.xvm.asm.ast.RegisterAST;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.NamedConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import org.xvm.util.Severity;


/**
 * Invocation expression represents calling a method or function. An oversimplification of the
 * model is as follows:
 * <ul>
 * <li><i>"Binding a method"</i>: Reference + Method = Function</li>
 * <li><i>"Binding parameters" (aka currying)</i>: Function + Argument(s) = Function'</li>
 * <li><i>"Calling a function"</i>: Function + () = Return Value(s)</li>
 * </ul>
 * <p/>
 * Most of the time, this is all accomplished in a single syntactic step, but not always:
 * <p/>
 * <pre><code>
 *   // bind target "list" to method "add", bind argument, call function
 *   list.add(item);
 *
 *   // on "List" type, find "add" method with one parameter (four alternatives shown)
 *   Method m = List.&add(?);
 *   Method m = List.&add(&lt;List.Element&gt;?);
 *   Method m = List.add(?);
 *   Method m = List.add(&lt;List.Element&gt;?);
 *
 *    // bind target "list" to method "add", bind argument
 *   function void () fn = list.&add(item);
 *
 *   // call the function held in "fn"
 *   fn();
 * </code></pre>
 * <p/>
 * There are op codes for:
 * <ul>
 * <li>Binding a method to its target reference to create a function;</li>
 * <li>Binding any subset (including all) parameters of a function to create a new function;</li>
 * <li>Calling a function (16 different ops, including short forms for calling functions whose
 *     parameters have been bound vs. functions that still have unbound parameters);</li>
 * <li>Invoking a method using a target reference (16 different ops);</li>
 * <li>Instantiating a new object and invoking its constructor (16 different ops); and</li>
 * <li>Invoking another constructor from within a constructor (4 different ops);</li>
 * </ul>
 * <p/>
 * Each of these operations is type safe, requiring a provably correct target reference, arguments,
 * and destinations for each of the return values.
 * <p/>
 * <pre><code>
 *                                            bind    bind
 *   description                              target  args    call    result
 *   ---------------------------------------  ------  ------  ------  ------------------------------
 *   obtain reference to method or function                           method or function
 *   function invocation                                      X       result of call
 *   binding function parameters / currying           X               function from a function
 *   function invocation                              X       X       result of call
 *   method binding                           X                       function from a method name
 *   method invocation                        X               X       result of call
 *   method and parameter binding             X       X               function from a method name
 *   method invocation                        X       X       X       result of call
 * </code></pre>
 * <p/>
 * The implementation is specialized when the method or function <b>name</b> is provided. The
 * invocation expression knows this situation exists because its {@link #expr} refers to a {@link
 * NameExpression}. The responsibilities of the InvocationExpression are expanded as follows:
 * <ul>
 * <li>The {@link #expr} itself is <b>NOT</b> asked to validate! All of the information that it
 *     contains is instead validated by and used by the InvocationExpression directly.</li>
 * <li>The NameExpression's own {@link NameExpression#left left} expression (if any) represents
 *     the class/type within which -- or reference on which -- the method or function will be
 *     found; a lack of a left expression implies a possible "this." for non-static code contexts,
 *     and the current name-resolution {@link Context} for both non-static and static code
 *     contexts.</li>
 * <li>The NameExpression's purpose in this case is to provide information to the
 *     InvocationExpression so that it can locate the correct method or function. In addition to
 *     the context and the name, there are optional "redundant returns" on the NameExpression in
 *     {@link NameExpression#params params}. The InvocationExpression must validate these, and for
 *     each redundant return type provided, it must ensure that any method/function that it selects
 *     matches that redundant return.</li>
 * <li>Lastly, the NameExpression includes a no-de-reference indicator, {@link
 *     NameExpression#isSuppressDeref() isSuppressDeref()}, which tells the InvocationExpression
 *     not to perform the "call" portion itself, but rather to yield the method or function
 *     reference as a result. This information may overlap with information that the
 *     InvocationExpression has from its own method arguments, if any is a NonBindingExpression,
 *     since that also indicates that the InvocationExpression must not perform the "call", but
 *     rather yields a method or function reference as its result.</li>
 * <li>...</li>
 * </ul>
 * <p/>
 * The rules for determining the method or function to call when the name is provided:
 * <ol>
 * <li>Validate the (optional) left expression, and all of the (optional) redundant return type
 *     {@link NameExpression#params params} expressions of the NameExpression.</li>
 * <li>Determine whether the search will include methods, functions, or both. Functions are included
 *     if (i) there is no left, or (ii) the left is identity-mode. Methods are included if (i) there
 *     is a left, (ii) there is no left and the context is not static, or (iii) the call itself is
 *     suppressed and no arguments are bound (i.e. no "this" is required to bind the method).</li>
 * <li>If the name has a {@code left} expression, that expression provides the scope to search for
 *     a matching method/function. If the left expression is itself a NameExpression, then the scope
 *     may actually refer to two separate types, because the NameExpression may indicate both (i)
 *     identity mode and (ii) reference mode. In this case, the identity mode is treated as a
 *     first scope, and the reference mode is treated as a second scope.</li>
 * <li>If the name does not have a {@code left} expression, then walk up the AST parent node chain
 *     looking for a registered name, i.e. a local variable of that name, stopping once the
 *     containing method/function (but <b>not</b> a lambda, since it has a permeable barrier to
 *     enable local variable capture) is reached. If a match is found, then that is the function to
 *     use, and it is an error if the type of that variable is not a function, or a reference that
 *     has an @Auto conversion to a function. (Done.)</li>
 * <li>Otherwise, for a name without a {@code left} expression (which provides its scope),
 *     determine the sequence of scopes that will be searched for matching methods/functions. For
 *     example, the point from which the call is occurring could be inside a (i) lambda, inside a
 *     (ii) method, inside a (iii) property, inside a (iv) child class, inside a (v) static child
 *     class, inside a (vi) top level class, inside a (vii) package, inside a (viii) module; in this
 *     example, scope (i) is searched first for any matching methods and functions, then scope (ii),
 *     then scope (ii), (iii), (iv), and (v). Because scope (v) is a static child, when scope (vi)
 *     is searched, it is only searched for functions, <i>unless</i> the InvocationExpression is
 *     <b>not</b> performing a "call" (i.e. no "this" is required), in which case methods are
 *     included. The package and module are omitted from the search; we do not venture past the
 *     top level class barrier in the search.</li>
 * <li>Starting at the first scope, check for a property of that name; if one exists, treat it using
 *     the rules from step 4 above: If a match is found, then that is the method/function to use,
 *     and it is an error if the type of that property/constant is not a method, a function, or a
 *     reference that has an @Auto conversion to a method or function. (Done.)</li>
 * <li>Otherwise, find the methods/functions that match the above criteria, as follows:
 *     (i) including only method and/or functions as appropriate; (ii) matching the name; (iii) for
 *     each named argument, having a matching parameter name on the method/function; (iv) after
 *     accounting for named arguments, having at least as many parameters as the number of provided
 *     arguments, and no more <i>required</i> parameters than the number of provided arguments; (v)
 *     having each argument from steps (iii) and (iv) be isA() or @Auto convertible to the type of
 *     each corresponding parameter; and (vi) matching (i.e. isA()) any specified redundant return
 *     types.</li>
 * <li>If no methods or functions match from steps 6 &amp; 7, then repeat at the next outer scope.
 *     If there are no more outer scopes, then it is an error. (Done.)</li>
 * <li>If one method match from steps 6 &amp; 7, then that method is selected. (Done.)</li>
 * <li>If multiple methods/functions match from steps 6 &amp; 7, then the <i>best</i> one must be
 *     selected. First, the algorithm from {@link TypeConstant#selectBest(SignatureConstant[])} is
 *     used. If that algorithm results in a single selection, then that single selection is used.
 *     Otherwise, the redundant return types are used as a tie breaker; if that results in a single
 *     selection, then that single selection is used. Otherwise, the ambiguity is an error.
 *     (Done.)</li>
 * </ol>
 * <p/>
 * The "construct" name (which is actually a keyword) indicates a simplified set of rules;
 * specifically:
 * <ul>
 * <li>It requires the name to either (i) have no <i>left</i>, or (ii) have a <i>left</i> that is
 *     itself a NameExpression in identity-mode;</li>
 * <li>Only the constructors are searched; the name cannot specify a variable or a property;</li>
 * <li>There cannot / must not be any redundant returns, so any associated rules are ignored.</li>
 * </ul>
 */
public class InvocationExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public InvocationExpression(Expression expr, boolean async, List<Expression> args, long lEndPos)
        {
        this.expr    = expr;
        this.async   = async;
        this.args    = args;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this invocation is marked as asynchronous
     */
    public boolean isAsync()
        {
        return async;
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr instanceof NameExpression exprName
                && "versionMatches".equals(exprName.getName())
                && args.size() == 1
                && args.get(0) instanceof LiteralExpression argLit
                && argLit.getLiteral().getId() == Id.LIT_VERSION
                && exprName.getLeftExpression() != null
                && exprName.isOnlyNames()
                || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            // build the qualified module name
            StringBuilder sb    = new StringBuilder();
            List<Token>   names = ((NameExpression) expr).getNameTokens();
            for (int i = 0, c = names.size() - 1; i < c; ++i)
                {
                if (i > 0)
                    {
                    sb.append('.');
                    }
                sb.append(names.get(i).getValueText());
                }

            ConstantPool pool    = pool();
            String       sModule = sb.toString();
            Version      version = ((LiteralExpression) args.get(0)).getVersion();
            return pool.ensureImportVersionCondition(
                    pool.ensureModuleConstant(sModule), pool.ensureVersionConstant(version));
            }

        return super.toConditionalConstant();
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected void updateLineNumber(Code code)
        {
        if (expr instanceof NameExpression exprName)
            {
            // use the line that contains the method name (etc...) as the current line;
            // this is dramatically better for fluent style coding convention
            code.updateLineNumber(Source.calculateLine(exprName.getNameToken().getStartPosition()));
            }
        else
            {
            super.updateLineNumber(code);
            }
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
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
        return resolveReturnTypes(ctx, null, false, ErrorListener.BLACKHOLE);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs)
        {
        if (atypeRequired == null || atypeRequired.length == 0)
            {
            return TypeFit.Fit;
            }

        TypeConstant[] atype = resolveReturnTypes(ctx, atypeRequired, fExhaustive,
                                    errs == null ? ErrorListener.BLACKHOLE : errs);

        return calcFitMulti(ctx, atype, atypeRequired);
        }

    /**
     * A common implementation for both {@link #getImplicitType} and
     * {@link Expression#testFitMulti} that calculates the return types produced
     * by this InvocationExpression.
     *
     * @param ctx            the compilation context for the statement
     * @param atypeRequired  the types that the expression is being asked to provide (optional)
     * @param errs           the error listener
     *
     * @return an array of the types produced by the expression, or an empty array if the expression
     *         is void (or if its type cannot be determined)
     */
    protected TypeConstant[] resolveReturnTypes(Context ctx, TypeConstant[] atypeRequired,
                                                boolean fExhaustive, ErrorListener errs)
        {
        if (isValidated())
            {
            return getTypes();
            }

        ConstantPool pool = pool();

        if (expr instanceof NameExpression exprName)
            {
            Expression   exprLeft = exprName.left;
            TypeConstant typeLeft = null;
            if (exprLeft != null)
                {
                typeLeft = exprLeft.getImplicitType(ctx);
                if (typeLeft == null)
                    {
                    // the fact that getImplicitType() returned null may mean that the "left" name
                    // is not resolvable; try to produce a proper error message in that case
                    exprLeft.testFit(ctx, pool.typeObject(), fExhaustive, errs);
                    return TypeConstant.NO_TYPES;
                    }
                }

            // the return types are a combination of required and redundant types
            TypeConstant[]       atypeReturn   = atypeRequired;
            List<TypeExpression> listRedundant = exprName.getTrailingTypeParams();

            if (listRedundant != null)
                {
                // collect as many redundant return types as possible to help narrow down the
                // possible method/function matches and combine with required types
                atypeReturn = applyRedundantTypes(ctx, atypeRequired, listRedundant, false, errs);
                if (atypeReturn == null)
                    {
                    return TypeConstant.NO_TYPES;
                    }
                }

            Argument argMethod = resolveName(ctx, false, typeLeft, atypeReturn, errs);
            if (argMethod == null)
                {
                return TypeConstant.NO_TYPES;
                }

            // handle conversion to function
            if (m_idConvert != null)
                {
                // the first return type of the idConvert method must be a function, which in turn
                // has two sub-types, the first of which is its "params" and the second of which is
                // its "returns", and the returns is a tuple type parameterized by the types of the
                // return values from the function
                TypeConstant[] atypeConvRets = m_idConvert.getRawReturns();
                TypeConstant   typeFn        = atypeConvRets[0];

                assert typeFn.isA(pool.typeFunction());
                if (m_fCall)
                    {
                    return pool.extractFunctionReturns(typeFn);
                    }

                if (m_fBindParams)
                    {
                    typeFn = bindFunctionParameters(typeFn);
                    }
                return new TypeConstant[]{typeFn};
                }

            // handle method or function
            if (argMethod instanceof MethodConstant idMethod)
                {
                MethodStructure     method      = m_method;
                int                 cTypeParams = method.getTypeParamCount();
                int                 cReturns    = method.getReturnCount();
                GenericTypeResolver resolver    = null;

                if (cTypeParams > 0)
                    {
                    // resolve the type parameters against all the arg types we know by now
                    TypeConstant[] atype = m_fCall || cReturns == 0
                            ? atypeReturn
                            : pool.extractFunctionReturns(atypeReturn[0]);
                    resolver = makeTypeParameterResolver(ctx, method, false, typeLeft, atype,
                                    ErrorListener.BLACKHOLE);
                    }

                if (m_fCall)
                    {
                    if (cReturns == 0)
                        {
                        // we allow a void method's return into an empty Tuple
                        return atypeReturn.length == 1 && isVoid(atypeReturn)
                                ? atypeReturn
                                : TypeConstant.NO_TYPES;
                        }

                    if (method.isFunction() || method.isConstructor())
                        {
                        return resolveTypes(resolver, idMethod.getSignature().getRawReturns());
                        }
                    if (typeLeft == null)
                        {
                        typeLeft = m_targetInfo == null
                                ? ctx.getThisType()
                                : m_targetInfo.getTargetType();
                        }
                    SignatureConstant sigMethod = idMethod.getSignature();

                    return resolveTypes(resolver,
                            sigMethod.resolveAutoNarrowing(pool, typeLeft, null).getRawReturns());
                    }

                TypeConstant typeFn = m_fBindTarget
                        ? idMethod.getSignature().asFunctionType()
                        : idMethod.getValueType(pool, typeLeft);

                if (cTypeParams > 0)
                    {
                    typeFn = removeTypeParameters(typeFn, cTypeParams);
                    }

                if (resolver != null)
                    {
                    typeFn = typeFn.resolveGenerics(pool, resolver);
                    }

                if (m_fBindParams)
                    {
                    typeFn = bindFunctionParameters(typeFn);
                    }
                return new TypeConstant[]{typeFn};
                }

            // must be a property or a variable of type function (@Auto conversion possibility
            // already handled above); the function has two tuple sub-types, the second of which is
            // the "return types" of the function
            TypeConstant typeArg;
            if (argMethod instanceof PropertyConstant idProp)
                {
                TypeInfo     infoLeft = getTypeInfo(ctx, typeLeft, errs);
                PropertyInfo infoProp = infoLeft.findProperty(idProp);

                typeArg = infoProp == null ? pool.typeObject() : infoProp.inferImmutable(typeLeft);
                }
            else
                {
                assert argMethod instanceof Register;
                typeArg = argMethod.getType().resolveTypedefs();
                }

            if (typeArg.isA(pool.typeFunction()) || typeArg.isA(pool.typeMethod()))
                {
                return calculateReturnType(typeArg);
                }

            // try to guess what went wrong
            if (argMethod instanceof PropertyConstant)
                {
                if (typeLeft == null)
                    {
                    typeLeft = ctx.getThisType();
                    }
                log(errs, Severity.ERROR, Compiler.SUSPICIOUS_PROPERTY_USE,
                        exprName.getName(), typeLeft.getValueString());
                }
            }
        else // not a NameExpression
            {
            // it has to either be a function or convertible to a function
            TypeConstant typeFn = expr.getImplicitType(ctx);
            if (typeFn != null)
                {
                // since we didn't call "resolveName", need to set the corresponding values
                m_fBindTarget = false;
                m_fBindParams = isAnyArgBound();
                m_fCall       = !isSuppressCall();
                m_fNamedArgs  = containsNamedArgs(args);

                typeFn = testFunction(ctx, typeFn, 0, 0, atypeRequired, errs);
                if (typeFn != null)
                    {
                    return calculateReturnType(typeFn);
                    }
                }
            }
        return TypeConstant.NO_TYPES;
        }

    /**
     * Calculate the return types by combining the required and redundant type information.
     *
     * @param ctx            the compiler context
     * @param atypeRequired  the array of required types
     * @param listRedundant  the list of type expressions for redundant types
     * @param fValidate      if true, the type expression should be validated
     * @param errs           the error listener
     *
     * @return the array of return types or null if the return types cannot be calculated
     */
    private TypeConstant[] applyRedundantTypes(Context ctx, TypeConstant[] atypeRequired,
                                               List<TypeExpression> listRedundant,
                                               boolean fValidate, ErrorListener errs)
        {
        ConstantPool   pool    = pool();
        boolean        fNoCall = isSuppressCall();
        TypeConstant   typeFn;
        TypeConstant[] atypeReturn;

        if (fNoCall)
            {
            if (atypeRequired == null)
                {
                typeFn      = pool.typeFunction();
                atypeReturn = TypeConstant.NO_TYPES;
                }
            else
                {
                typeFn = atypeRequired[0];
                if (typeFn.isA(pool.typeFunction()))
                    {
                    atypeReturn = pool.extractFunctionReturns(typeFn);
                    }
                else if (pool.typeFunction().isA(typeFn)) // e.g. Object
                    {
                    typeFn      = pool.typeFunction();
                    atypeReturn = TypeConstant.NO_TYPES;
                    }
                else
                    {
                    // no fit
                    if (fValidate)
                        {
                        log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                "Function", typeFn.getValueString());
                        }
                    return null;
                    }
                }
            }
        else
            {
            typeFn      = null;
            atypeReturn = atypeRequired == null ? TypeConstant.NO_TYPES : atypeRequired.clone();
            }

        int     cRequired  = atypeReturn.length;
        int     cRedundant = listRedundant.size();
        boolean fCond      = false;

        if (cRedundant > cRequired)
            {
            atypeReturn = Arrays.copyOf(atypeReturn, cRedundant);
            }
        else if (cRedundant < cRequired)
            {
            // the only case when we have fewer types than required is a conditional return
            if (cRequired == cRedundant + 1 && atypeRequired[0].isA(pool.typeBoolean()))
                {
                fCond = true;
                }
            else
                {
                if (fValidate)
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_MISMATCH);
                    }
                return null;
                }
            }

        for (int i = 0; i < cRedundant; ++i)
            {
            TypeExpression exprType = listRedundant.get(i);

            if (fValidate)
                {
                int          ixType      = fCond ? i + 1 : i;
                TypeConstant typeTypeReq = i < cRequired
                        ? atypeReturn[ixType].getType()
                        : pool.typeType();

                TypeExpression exprTypeNew = (TypeExpression) exprType.validate(ctx, typeTypeReq, errs);
                if (exprTypeNew == null)
                    {
                    return null;
                    }

                if (exprTypeNew != exprType)
                    {
                    // WARNING: mutating contents of the NameExpression, which has been
                    //          _subsumed_ by this InvocationExpression
                    listRedundant.set(i, exprTypeNew);
                    }
                atypeReturn[ixType] = exprTypeNew.getType().getParamType(0);
                }
            else
                {
                TypeConstant typeParam = exprType.getImplicitType(ctx);
                if (typeParam == null || !typeParam.isTypeOfType())
                    {
                    return null;
                    }
                TypeConstant typeReturn = typeParam.getParamType(0);
                if (typeReturn.containsUnresolved())
                    {
                    return null;
                    }
                atypeReturn[i] = typeReturn;
                }
            }

        if (fNoCall)
            {
            typeFn      = pool.buildFunctionType(pool.extractFunctionParams(typeFn), atypeReturn);
            atypeReturn = new TypeConstant[] {typeFn};
            }
        return atypeReturn;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        // the reason for tracking success (fValid) is that we want to get as many things
        // validated as possible, but if some expressions didn't validate, we can't predictably find
        // the desired method or function (e.g. without a left expression providing validated type
        // information)
        ConstantPool   pool        = pool();
        boolean        fCall       = !isSuppressCall();
        TypeConstant[] atypeResult = null;

        // when we have a name expression on our immediate left, we do NOT (!!!) validate it,
        // because the name resolution is the responsibility of this InvocationExpression, and
        // the NameExpression itself will error on resolving a method/function name
        Validate:
        if (expr instanceof NameExpression exprName)
            {
            // if the name expression has an expression on _its_ left, then we are now responsible
            // for validating that "left's left" expression
            Expression   exprLeft = exprName.left;
            TypeConstant typeLeft = null;
            if (exprLeft != null)
                {
                Expression exprNew = exprLeft.validate(ctx, null, errs);
                if (exprNew == null)
                    {
                    return null;
                    }

                if (exprNew != exprLeft)
                    {
                    // WARNING: mutating contents of the NameExpression, which has been
                    //          _subsumed_ by this InvocationExpression
                    exprName.left = exprLeft = exprNew;
                    }

                typeLeft = exprLeft.getType();
                if (typeLeft == null)
                    {
                    exprLeft.log(errs, Severity.ERROR, Compiler.RETURN_REQUIRED);
                    return null;
                    }
                }

            // the return types are a combination of required and redundant types
            TypeConstant[]       atypeReturn   = atypeRequired;
            List<TypeExpression> listRedundant = exprName.getTrailingTypeParams();
            if (listRedundant != null)
                {
                atypeReturn = applyRedundantTypes(ctx, atypeRequired, listRedundant, true, errs);
                if (atypeReturn == null)
                    {
                    return null;
                    }
                }

            // transform the return types using the current context if possible
            if (atypeReturn != null)
                {
                for (int i = 0, c = atypeReturn.length; i < c; i++)
                    {
                    TypeConstant typeRet = atypeReturn[i];
                    if (typeRet != null && typeRet.containsGenericType(true))
                        {
                        TypeConstant typeRetR = typeRet.resolveGenerics(pool, ctx.getThisType());
                        if (typeRetR != typeRet)
                            {
                            if (atypeReturn == atypeRequired)
                                {
                                atypeReturn = atypeRequired.clone();
                                }
                            atypeReturn[i] = typeRetR;
                            }
                        }
                    }
                }

            // resolving the name will yield a method, a function, or something else that needs
            // to yield a function, such as a property or variable that holds a function or
            // something that can be converted to a function
            List<Expression> listArgs = args;
            ErrorListener    errsTemp = errs.branch(this);

            Argument argMethod = resolveName(ctx, true, typeLeft, atypeReturn, errsTemp);
            if (argMethod == null)
                {
                // as the last resort, validate the arguments before trying to resolve the name again
                // (regardless of the outcome, this final validation errors should be reported, but
                // the order in which they were encountered needs to be preserved)
                ErrorListener  errsMain  = errs.branch(this);
                TypeConstant[] atypeArgs = validateExpressions(ctx, listArgs, null, errsMain);
                if (atypeArgs == null)
                    {
                    errsTemp.merge();
                    errsMain.merge();
                    return null;
                    }

                errsMain.merge();
                argMethod = resolveName(ctx, true, typeLeft, atypeReturn, errs);
                if (argMethod == null)
                    {
                    return null;
                    }
                }

            if (m_idFormal != null)
                {
                // argMethod is a function on a formal type or a formal property
                assert typeLeft.isTypeOfType();
                typeLeft = typeLeft.getParamType(0);
                }

            if (m_fNamedArgs)
                {
                assert argMethod instanceof MethodConstant;
                listArgs = rearrangeNamedArgs(m_method, listArgs, errs);
                if (listArgs == null)
                    {
                    // invalid names encountered
                    return null;
                    }
                args = listArgs;
                }

            // when the expression is a name, and it is NOT a ".name", then we have to
            // record the dependency on the name, whether it's a variable (or an instance
            // property on the implicit "this") that contains a function reference, or (most
            // commonly) an implicit "this" for a method call
            if (exprLeft == null)
                {
                if (argMethod instanceof Register)
                    {
                    ctx.markVarRead(exprName.getNameToken(), true, errs);
                    }
                else
                    {
                    boolean fStatic = argMethod instanceof MethodConstant idMethod
                            ? getMethod(ctx, idMethod).isFunction()
                            : getProperty(ctx, (PropertyConstant) argMethod).isStatic();

                    if (!fStatic)
                        {
                        // there is a read of the implicit "this" variable
                        Token tokName = exprName.getNameToken();
                        long  lPos    = tokName.getStartPosition();
                        Token tokThis = new Token(lPos, lPos, Id.THIS);
                        ctx.markVarRead(tokThis, true, errs);
                        }
                    }
                }
            else if (m_fBjarne)
                {
                listArgs.add(0, exprLeft);
                }

            // handle conversion to function
            if (m_idConvert != null)
                {
                // the first return type of the idConvert method must be a function, which in turn
                // has two sub-types, the first of which is its "params" and the second of which is
                // its "returns", and the returns is a tuple type parameterized by the types of the
                // return values from the function
                TypeConstant[] atypeConvRets = m_idConvert.getRawReturns();
                TypeConstant   typeFn        = atypeConvRets[0];

                assert typeFn.isA(pool.typeFunction());

                if (fCall)
                    {
                    atypeResult = pool.extractFunctionReturns(typeFn);
                    }
                else
                    {
                    if (m_fBindParams)
                        {
                        typeFn = bindFunctionParameters(typeFn);
                        }
                    atypeResult = new TypeConstant[]{typeFn};
                    }
                break Validate;
                }

            // handle method or function
            if (argMethod instanceof MethodConstant idMethod)
                {
                MethodStructure method      = m_method;
                TypeConstant[]  atypeParams = idMethod.getRawParams();
                int             cTypeParams = method.getTypeParamCount();
                int             cParams     = method.getVisibleParamCount();
                int             cReturns    = atypeReturn == null ? 0 : atypeReturn.length;
                boolean         fCondReturn = method.isConditionalReturn() && cReturns > 1;

                if (cTypeParams > 0)
                    {
                    // purge the type parameters and resolve the method signature
                    // against all the types we know by now (marking unresolved as "pending")
                    if (cParams > 0)
                        {
                        GenericTypeResolver resolver = makeTypeParameterResolver(ctx, method, true,
                                typeLeft,
                                fCall || cReturns == 0
                                    ? atypeReturn
                                    : pool.extractFunctionReturns(atypeReturn[0]), errs);
                        if (resolver == null)
                            {
                            return null;
                            }

                        TypeConstant[] atype = new TypeConstant[cParams];
                        System.arraycopy(atypeParams, cTypeParams, atype, 0, cParams);

                        atypeParams = resolveTypes(resolver, atype);
                        }
                    else
                        {
                        atypeParams = TypeConstant.NO_TYPES;
                        }
                    }

                if (typeLeft == null && !m_method.isFunction())
                    {
                    typeLeft = m_targetInfo == null
                            ? ctx.getThisType()
                            : m_targetInfo.getTargetType();
                    }

                TypeConstant[] atypeArgs = validateExpressions(ctx, listArgs, atypeParams, errs);
                if (atypeArgs == null)
                    {
                    return null;
                    }

                Map<FormalConstant, TypeConstant> mapTypeParams = Collections.emptyMap();
                if (cTypeParams > 0)
                    {
                    transformTypeArguments(ctx, method, listArgs, atypeArgs);

                    // re-resolve against the validated types
                    mapTypeParams = method.resolveTypeParameters(pool,
                        typeLeft,
                        atypeArgs,
                        fCall || cReturns == 0
                            ? atypeReturn
                            : pool.extractFunctionReturns(atypeReturn[0]), false);
                    if (mapTypeParams.size() < cTypeParams)
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                            method.collectUnresolvedTypeParameters(mapTypeParams.keySet().
                                stream().map(NamedConstant::getName).collect(Collectors.toSet())));
                        return null;
                        }

                    Argument[]   aargTypeParam  = new Argument[mapTypeParams.size()];
                    List<String> listUnresolved = null;
                    int          iArg           = 0;
                    for (TypeConstant typeArg : mapTypeParams.values())
                        {
                        if (typeArg.containsUnresolved() || typeArg.equals(pool.typeObject()))
                            {
                            if (listUnresolved == null)
                                {
                                listUnresolved = new ArrayList<>();
                                }
                            listUnresolved.add(method.getParam(iArg++).getName());
                            continue;
                            }

                        TypeConstant typeConstraint = idMethod.getRawParams()[iArg].getParamType(0);

                        // there's a possibility that type parameter constraints refer to
                        // previous type parameters, for example:
                        //   <T1 extends Base, T2 extends T1> foo(T1 v1, T2 v2) {...}
                        if (typeConstraint.containsTypeParameter(true))
                            {
                            typeConstraint = typeConstraint.resolveGenerics(pool,
                                                GenericTypeResolver.of(mapTypeParams));
                            }

                        if (!typeArg.isA(typeConstraint))
                            {
                            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                    typeConstraint.getValueString(), typeArg.getValueString());
                            return null;
                            }
                        aargTypeParam[iArg++] = typeArg.getType();
                        }

                    if (listUnresolved != null)
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE, listUnresolved);
                        return null;
                        }
                    m_aargTypeParams = aargTypeParam;
                    }

                if (fCall)
                    {
                    SignatureConstant sigMethod = idMethod.getSignature();
                    if (sigMethod.containsAutoNarrowing(true))
                        {
                        sigMethod = sigMethod.resolveAutoNarrowing(pool, typeLeft, null);
                        }
                    if (!mapTypeParams.isEmpty())
                        {
                        sigMethod = sigMethod.resolveGenericTypes(pool,
                                        GenericTypeResolver.of(mapTypeParams));
                        }
                    atypeResult = sigMethod.getRawReturns();

                    if (fCondReturn)
                        {
                        if (getParent().allowsConditional(this))
                            {
                            m_fCondResult = true;
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.CONDITIONAL_RETURN_NOT_ALLOWED,
                                method.getIdentityConstant().getValueString());
                            return null;
                            }
                        }
                    if (cReturns > 0)
                        {
                        // check for Tuple conversion for the return value; we know that the
                        // method should fit, so the only thing to figure out is whether
                        // "packing" to a Tuple is necessary
                        if (atypeReturn.length == 0)
                            {
                            atypeResult = atypeReturn;
                            }
                        else if (calculateReturnFit(sigMethod, fCall, atypeReturn, ctx.getThisType(),
                                ErrorListener.BLACKHOLE).isPacking())
                            {
                            atypeResult = new TypeConstant[]{pool.ensureTupleType(atypeResult)};
                            m_fPack     = true;
                            }
                        }
                    }
                else
                    {
                    TypeConstant typeFn = m_fBindTarget
                            ? idMethod.getSignature().asFunctionType()
                            : idMethod.getValueType(pool, typeLeft);

                    if (cTypeParams > 0)
                        {
                        typeFn = removeTypeParameters(typeFn, cTypeParams);
                        }

                    if (m_fBindParams)
                        {
                        typeFn = bindFunctionParameters(typeFn);
                        }

                    if (!mapTypeParams.isEmpty())
                        {
                        typeFn = typeFn.resolveGenerics(pool, GenericTypeResolver.of(mapTypeParams));
                        }

                    atypeResult = new TypeConstant[] {typeFn};
                    }
                }
            else
                {
                // must be a property or a variable of type function (@Auto conversion possibility
                // already handled above); the function has two tuple sub-types, the second of which is
                // the "return types" of the function
                int          cTypeParams = 0;
                int          cDefaults   = 0;
                TypeConstant typeFn;
                if (argMethod instanceof PropertyConstant idProp)
                    {
                    if (m_targetInfo != null)
                        {
                        typeLeft = m_targetInfo.getTargetType();
                        }
                    else if (m_typeTarget != null)
                        {
                        typeLeft = m_typeTarget;
                        }
                    TypeInfo     infoLeft = getTypeInfo(ctx, typeLeft, errs);
                    PropertyInfo infoProp = infoLeft.findProperty(idProp);
                    if (infoProp == null)
                        {
                        log(errs, Severity.ERROR, Compiler.PROPERTY_INACCESSIBLE,
                                idProp.getValueString(), typeLeft.getValueString());
                        return null;
                        }
                    typeFn = infoProp.inferImmutable(typeLeft);
                    }
                else
                    {
                    assert argMethod instanceof Register;

                    typeFn = argMethod.getType().resolveTypedefs();

                    if (((Register) argMethod).isSuper())
                        {
                        MethodStructure method = ctx.getMethod();

                        cDefaults   = method.getDefaultParamCount();
                        cTypeParams = method.getTypeParamCount();
                        if (cTypeParams > 0 && typeFn.isA(pool.typeFunction()))
                            {
                            Argument[] aargTypeParam = new Argument[cTypeParams];
                            for (int i = 0; i < cTypeParams; i++)
                                {
                                aargTypeParam[i] = ctx.getParameter(i);
                                }
                            m_aargTypeParams = aargTypeParam;
                            }
                        }
                    }

                if (!typeFn.isA(pool.typeFunction()) && !typeFn.isA(pool.typeMethod()))
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            "Function", typeFn.getValueString());
                    return null;
                    }

                if (exprName.isSuppressDeref())
                    {
                    if (typeFn.isA(pool.typeMethod()))
                        {
                        log(errs, Severity.ERROR, Compiler.INVALID_PROPERTY_REF);
                        return null;
                        }
                    assert argMethod instanceof Register && !fCall;

                    // this must be a scenario of binding a function at a register, e.g.:
                    //      function void (Int) fn1 = ...
                    //      function void ()    fn0 = &fn1(42);
                    // validation of the exprName would produce a Ref<Function> (&fn1), so we skip it;
                    if (((Register) argMethod).isSuper() && cTypeParams > 0)
                        {
                        // the caller doesn't expect to see type parameters in the signature;
                        // we need to remove them from the function type
                        TypeConstant[] atypeFnParams  = pool.extractFunctionParams(typeFn);
                        TypeConstant[] atypeFnReturns = pool.extractFunctionReturns(typeFn);
                        typeFn = pool.buildFunctionType(
                                Arrays.copyOfRange(atypeFnParams, cTypeParams, atypeFnParams.length),
                                atypeFnReturns);
                        cTypeParams = 0;
                        }
                    }
                else
                    {
                    Expression exprNew = exprName.validate(ctx, typeFn, errs);
                    if (exprNew == null)
                        {
                        return null;
                        }

                    expr   = exprNew;
                    typeFn = exprNew.getType();

                    if (typeFn.isA(pool.typeMethod()))
                        {
                        TypeConstant typeTarget = typeFn.getParamType(0);
                        if (!typeLeft.isA(typeTarget))
                            {
                            log(errs, Severity.ERROR, Compiler.INVALID_METHOD_TARGET,
                                typeTarget.getValueString(), exprName.getName());
                            return null;
                            }
                        m_fBindTarget = true;
                        m_argMethod   = argMethod;
                        }
                    }

                atypeResult = validateFunction(ctx, typeFn, cTypeParams, cDefaults, atypeRequired, errs);
                }
            }
        else // the expr is NOT a NameExpression
            {
            // it has to either be a function or convertible to a function
            Expression exprNew = expr.validate(ctx, pool.typeFunction(), errs);
            if (exprNew != null)
                {
                expr = exprNew;

                // since we didn't call "resolveName", need to set the corresponding values
                m_fBindTarget = false;
                m_fBindParams = isAnyArgBound();
                m_fCall       = fCall;
                m_fNamedArgs  = containsNamedArgs(args);

                // first we need to validate the function, to make sure that the type includes
                // sufficient information about parameter and return types, and that it fits with
                // the arguments that we have
                TypeConstant typeFn = testFunction(ctx, exprNew.getType(), 0, 0, atypeRequired, errs);
                if (typeFn == null)
                    {
                    return null;
                    }

                atypeResult = validateFunction(ctx, typeFn, 0, 0, atypeRequired, errs);
                }
            }

        if (atypeResult == null)
            {
            return null;
            }

        if (async)
            {
            if (!fCall)
                {
                log(errs, Severity.ERROR, Compiler.ASYNC_NOT_ALLOWED);
                return null;
                }

            if (atypeRequired == null || atypeRequired.length == 0)
                {
                // no required type means there's no left; however, there could be a continuation
                // expression, assuming the result is a future, e.g.:
                //      svc.f^().whenComplete(handler);
                // this expression will produce the result as "@Future Var<T>"
                m_fAutoFuture = true;
                if (atypeResult.length > 0)
                    {
                    atypeResult = atypeResult.clone(); // don't mess up the actual types
                    for (int i = 0, c = atypeResult.length; i < c; i++)
                        {
                        atypeResult[i] = pool.ensureFutureVar(atypeResult[i]);
                        }
                    }
                else
                    {
                    // @Future<Void>
                    atypeResult = new TypeConstant[] {pool.ensureFutureVar(pool.typeTuple0())};
                    }
                }
            }
        return finishValidations(ctx, atypeRequired, atypeResult, TypeFit.Fit, null, errs);
        }

    @Override
    public boolean isStandalone()
        {
        return true;
        }

    @Override
    public boolean isTraceworthy()
        {
        return m_fCall;
        }

    @Override
    public boolean isCompletable()
        {
        for (Expression arg : args)
            {
            if (!arg.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting() || args.stream().anyMatch(Expression::isShortCircuiting);
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return super.allowsShortCircuit(nodeChild) &&
                (nodeChild == expr ||
                 nodeChild instanceof Expression exprChild && args.contains(exprChild));
        }

    @Override
    public boolean isConditionalResult()
        {
        return m_fCondResult;
        }

    @Override
    public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk,
                                        boolean fUsedOnce, ErrorListener errs)
        {
        if (async)
            {
            TypeConstant[] atype  = getTypes();
            int            cRVals = getValueCount();
            Assignable[]   aLVal  = new Assignable[cRVals];

            if (m_fAutoFuture)
                {
                for (int i = 0; i < cRVals; i++)
                    {
                    Register reg = code.createRegister(atype[i], null);
                    code.add(new Var_D(reg));

                    aLVal[i] = new Assignable(reg);
                    }

                generateAssignments(ctx, code, aLVal, errs);

                Argument[] aargResult = new Argument[cRVals];
                for (int i = 0; i < cRVals; i++)
                    {
                    Register regVar = code.createRegister(atype[i], null);

                    code.add(new MoveVar(aLVal[i].getRegister(), regVar));
                    aargResult[i] = regVar;
                    }
                return aargResult;
                }

            if (getParent() instanceof ReturnStatement)
                {
                // let the caller deal with dynamic return values
                ConstantPool pool       = pool();
                Argument[]   aargResult = new Argument[cRVals];

                for (int i = 0; i < cRVals; i++)
                    {
                    Register reg = code.createRegister(pool.ensureFutureVar(atype[i]), null);

                    code.add(new Var_D(reg));

                    aLVal[i]      = new Assignable(reg);
                    aargResult[i] = reg;
                    }

                generateAssignments(ctx, code, aLVal, errs);
                return aargResult;
                }
            }

        return super.generateArguments(ctx, code, fLocalPropOk, fUsedOnce & !async, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        ConstantPool pool   = pool();
        int          cLVals = aLVal.length;
        int          cRVals = getValueCount();
        boolean      fAsync = async;

        assert !m_fPack || cLVals == 1; // pack must be into a single LValue

        Argument[] aargResult = new Argument[cRVals];
        for (int i = 0; i < cRVals; i++)
            {
            if (i < cLVals)
                {
                Assignable LVal = aLVal[i];
                if (!LVal.isLocalArgument())
                    {
                    // create a temp register for every assignable
                    super.generateAssignments(ctx, code, aLVal, errs);
                    return;
                    }

                aargResult[i] = LVal.getLocalArgument();
                }
            else
                {
                aargResult[i] = new Register(pool.typeObject(),
                                             null, fAsync ? Op.A_IGNORE_ASYNC : Op.A_IGNORE);
                }
            }

        // 1. NameExpression cannot (must not!) attempt to resolve method / function names; it is an
        //    assertion or error if it tries; that is the responsibility of InvocationExpression
        // 2. To avoid an out-of-order execution, we cannot allow the use of local properties
        //    except for the target when there are no arguments
        // 3. The arguments are allowed to be pushed on the stack since the run-time knows to load
        //    them up in the inverse order; however, the target itself or the function should not be
        //    put on the stack unless there are no arguments

        int      cArgs        = args.size();
        int      cRets        = aargResult.length;
        boolean  fConstruct   = false;
        boolean  fLocalPropOk = cArgs == 0;
        Argument argFn;
        ExprAST  astFn;

        Argument[] aargTypeParams = m_aargTypeParams;
        int        cTypeParams    = aargTypeParams == null ? 0 : aargTypeParams.length;
        boolean    fTargetOnStack = cArgs == 0 || args.stream().allMatch(Expression::isConstant);

        if (expr instanceof NameExpression exprName)
            {
            Expression exprLeft = exprName.left;
            if (m_argMethod instanceof MethodConstant idMethod)
                {
                idMethod = rebaseMethodConstant(idMethod, m_method);
                astFn    = new ConstantExprAST(idMethod);

                if (m_method.isFunction() || m_method.isConstructor())
                    {
                    // use the function identity as the argument & drop through to the function handling
                    assert !m_fBindTarget && (exprLeft == null || !exprLeft.hasSideEffects() ||
                                              m_fBjarne || m_idFormal != null);
                    if (m_idFormal == null)
                        {
                        argFn      = m_method.getIdentityConstant();
                        fConstruct = m_method.isConstructor();
                        if (exprLeft instanceof TraceExpression)
                            {
                            // give the TraceExpression a chance to generate arguments
                            exprLeft.generateVoid(ctx, code, errs);
                            }
                        }
                    else
                        {
                        // create a synthetic method constant for the formal type (for a funky
                        // interface type)
                        if (exprLeft != null)
                            {
                            TypeConstant typeType = exprLeft.getType();
                            assert typeType.isTypeOfType();
                            TypeConstant typeLeft = typeType.getParamType(0);

                            if (typeLeft.isFormalType())
                                {
                                if (exprLeft instanceof TraceExpression)
                                    {
                                    // same as above; allow the TraceExpression to generate args
                                    exprLeft.generateVoid(ctx, code, errs);
                                    }
                                }
                            else
                                {
                                Register regType = (Register) exprLeft.generateArgument(
                                                        ctx, code, fLocalPropOk, false, errs);
                                m_idFormal = pool.ensureDynamicFormal(
                                                idMethod, regType, m_idFormal, exprName.getName());
                                }
                            }
                        argFn      = pool.ensureMethodConstant(m_idFormal, idMethod.getSignature());
                        fConstruct = false;
                        }
                    }
                else
                    {
                    // idMethod is a MethodConstant for a method (including "finally")
                    if (m_fBindTarget)
                        {
                        Argument argTarget = generateTarget(ctx, code, exprLeft, fLocalPropOk,
                                                fTargetOnStack, errs);
                        if (m_fCall)
                            {
                            updateLineNumber(code);

                            // it's a method, and we need to generate the necessary code that calls it;
                            // generate the arguments
                            int        cAll      = idMethod.getRawParams().length;
                            int        cDefaults = cAll - cTypeParams - cArgs;
                            Argument   arg0      = null;
                            Argument[] aArgs     = null;
                            ExprAST[]  aAsts;
                            char       chArgs;

                            assert cTypeParams + cArgs + cDefaults == cAll;

                            if (cAll == 0)
                                {
                                chArgs = '0';
                                aArgs  = NO_RVALUES;
                                aAsts  = BinaryAST.NO_EXPRS;
                                }
                            else if (cAll == 1)
                                {
                                chArgs = '1';
                                if (cArgs == 1)
                                    {
                                    Expression expr = args.get(0);
                                    arg0  = expr.generateArgument(ctx, code, true, true, errs);
                                    aAsts = new ExprAST[] {expr.getExprAST()};
                                    }
                                else if (cTypeParams == 1)
                                    {
                                    arg0  = aargTypeParams[0];
                                    aAsts = new ExprAST[] {toExprAst(arg0)};
                                    }
                                else // (cDefaults == 1)
                                    {
                                    arg0 = Register.DEFAULT;
                                    aAsts = new ExprAST[] {RegisterAST.defaultReg(idMethod.getRawParams()[0])};
                                    }
                                }
                            else
                                {
                                chArgs = 'N';
                                aArgs  = new Argument[cAll];
                                aAsts  = new ExprAST[cAll];

                                if (cTypeParams > 0)
                                    {
                                    System.arraycopy(aargTypeParams, 0, aArgs, 0, cTypeParams);
                                    for (int i = 0; i < cTypeParams; i++)
                                        {
                                        aAsts[i] = toExprAst(aargTypeParams[i]);
                                        }
                                    }

                                for (int i = 0; i < cArgs; ++i)
                                    {
                                    Expression expr = args.get(i);
                                    Argument   arg  = expr.generateArgument(ctx, code, true, true, errs);
                                    int        iArg = cTypeParams + i;
                                    aArgs[iArg] = i == cArgs-1 ? arg : ensurePointInTime(code, arg);
                                    aAsts[iArg] = expr.getExprAST();
                                    }

                                for (int i = 0; i < cDefaults; ++i)
                                    {
                                    int iArg = cTypeParams + cArgs + i;
                                    aArgs[iArg] = Register.DEFAULT;
                                    aAsts[iArg] = RegisterAST.defaultReg(idMethod.getRawParams()[cArgs + i]);
                                    }
                                }

                            char chRets;
                            switch (cRets)
                                {
                                case 0:
                                    if (!fAsync)
                                        {
                                        chRets = '0';
                                        break;
                                        }
                                    aargResult = new Argument[] {Register.ASYNC};
                                    // fall through
                                case 1:
                                    chRets = '1';
                                    break;

                                default:
                                    chRets = 'N';
                                    break;
                                }

                            switch (combine(chArgs, chRets))
                                {
                                case _00:
                                    code.add(new Invoke_00(argTarget, idMethod));
                                    break;

                                case _10:
                                    if (m_fTupleArg)
                                        {
                                        code.add(new Invoke_T0(argTarget, idMethod, arg0));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_10(argTarget, idMethod, arg0));
                                        }
                                    break;

                                case _N0:
                                    code.add(new Invoke_N0(argTarget, idMethod, aArgs));
                                    break;

                                case _01:
                                    if (m_fPack)
                                        {
                                        code.add(new Invoke_0T(argTarget, idMethod, aargResult[0]));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_01(argTarget, idMethod, aargResult[0]));
                                        }
                                    break;

                                case _11:
                                    if (m_fPack)
                                        {
                                        if (m_fTupleArg)
                                            {
                                            code.add(new Invoke_TT(argTarget, idMethod, arg0, aargResult[0]));
                                            }
                                        else
                                            {
                                            code.add(new Invoke_1T(argTarget, idMethod, arg0, aargResult[0]));
                                            }
                                        }
                                    else
                                        {
                                        if (m_fTupleArg)
                                            {
                                            code.add(new Invoke_T1(argTarget, idMethod, arg0, aargResult[0]));
                                            }
                                        else
                                            {
                                            code.add(new Invoke_11(argTarget, idMethod, arg0, aargResult[0]));
                                            }
                                        }
                                    break;

                                case _N1:
                                    if (m_fPack)
                                        {
                                        code.add(new Invoke_NT(argTarget, idMethod, aArgs, aargResult[0]));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_N1(argTarget, idMethod, aArgs, aargResult[0]));
                                        }
                                    break;

                                case _0N:
                                    code.add(new Invoke_0N(argTarget, idMethod, aargResult));
                                    break;

                                case _1N:
                                    if (m_fTupleArg)
                                        {
                                        code.add(new Invoke_TN(argTarget, idMethod, arg0, aargResult));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_1N(argTarget, idMethod, arg0, aargResult));
                                        }
                                    break;

                                case _NN:
                                    code.add(new Invoke_NN(argTarget, idMethod, aArgs, aargResult));
                                    break;

                                default:
                                    throw new UnsupportedOperationException("invocation: " + combine(chArgs, chRets));
                                }

                            m_astInvoke = new InvokeExprAST(idMethod, getTypes(), m_astTarget, aAsts, fAsync);
                            return;
                            }
                        else // _NOT_ m_fCall
                            {
                            // the method gets bound to become a function; do this and drop through
                            // to the function handling
                            argFn = code.createRegister(idMethod.getSignature().asFunctionType());
                            code.add(new MBind(argTarget, idMethod, argFn));
                            astFn = new BindMethodAST(m_astTarget, idMethod, argFn.getType());
                            }
                        }
                    else // _NOT_ m_fBindTarget
                        {
                        // the method instance itself is the result, e.g. "Method m = Frog.&jump();"
                        assert m_idConvert == null && !m_fBindParams && !m_fCall;
                        if (cLVals > 0)
                            {
                            aLVal[0].assign(idMethod, code, errs);
                            }
                        return;
                        }
                    }
                }
            else // it is a NameExpression but _NOT_ a MethodConstant
                {
                if (m_fBindTarget)
                    {
                    // this is a method call; the method itself is a property or a register
                    Argument argTarget = generateTarget(ctx, code, exprLeft, fLocalPropOk,
                                            fTargetOnStack, errs);
                    if (m_argMethod instanceof PropertyConstant idProp)
                        {
                        PropertyStructure prop = (PropertyStructure) idProp.getComponent();
                        if (prop.isConstant() && prop.hasInitialValue())
                            {
                            MethodConstant idMethod = (MethodConstant) prop.getInitialValue();

                            argFn = code.createRegister(idMethod.getSignature().asFunctionType());
                            astFn = new ConstantExprAST(idProp);

                            code.add(new MBind(argTarget, idMethod, argFn));
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED, "Dynamic method invocation");
                            return;
                            }
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED, "Dynamic method invocation");
                        return;
                        }
                    }
                else
                    {
                    if (m_argMethod instanceof Register regFn)
                        {
                        argFn = regFn;
                        astFn = regFn.getRegisterAST();
                        }
                    else
                        {
                        // evaluate to find the argument (e.g. "var.prop", where prop holds a function)
                        argFn = exprName.generateArgument(ctx, code, false, fTargetOnStack, errs);
                        astFn = exprName.getExprAST();
                        }
                    }
                }
            }
        else // _NOT_ an InvocationExpression of a NameExpression (i.e. it's just a function)
            {
            // obtain the function that will be bound and/or called
            assert !m_fBindTarget;
            argFn = expr.generateArgument(ctx, code, false, fTargetOnStack, errs);
            astFn = expr.getExprAST();
            assert argFn.getType().isA(pool.typeFunction());
            }

        // bind arguments and/or generate a call to the function specified by argFn; first, convert
        // it to the desired function if necessary
        TypeConstant   typeFn = argFn.getType().resolveTypedefs();
        MethodConstant idConv = m_idConvert;
        if (idConv != null)
            {
            // argFn isn't a function; convert whatever-it-is into the desired function
            typeFn = idConv.getRawReturns()[0];
            Register regFn = new Register(typeFn, null, Op.A_STACK);
            code.add(new Invoke_01(argFn, idConv, regFn));
            argFn = regFn;
            astFn = new ConvertExprAST(astFn,
                        new TypeConstant[]{typeFn}, new MethodConstant[]{idConv});
            }

        TypeConstant[] atypeParams = pool.extractFunctionParams(typeFn);
        int            cAll        = atypeParams == null ? 0 : atypeParams.length;

        if (m_fCall)
            {
            updateLineNumber(code);

            int        cDefaults = cAll - cTypeParams - cArgs;
            Argument   arg0      = null;
            Argument[] aArgs     = null;
            ExprAST[]  aAsts;
            char       chArgs;

            assert !m_fBindParams || cArgs > 0;
            assert cDefaults >= 0;

            switch (cAll)
                {
                case 0:
                    chArgs = '0';
                    aArgs  = NO_RVALUES;
                    aAsts  = BinaryAST.NO_EXPRS;
                    break;

                case 1:
                    chArgs = '1';
                    if (cArgs == 1)
                        {
                        Expression expr = args.get(0);
                        arg0  = expr.generateArgument(ctx, code, true, true, errs);
                        aAsts = new ExprAST[] {expr.getExprAST()};
                        }
                    else if (cTypeParams == 1)
                        {
                        arg0  = aargTypeParams[0];
                        aAsts = new ExprAST[] {toExprAst(arg0)};
                        }
                    else // (cDefaults == 1)
                        {
                        arg0  = Register.DEFAULT;
                        aAsts = new ExprAST[] {RegisterAST.defaultReg(atypeParams[0])};
                        }
                    break;

                default:
                    chArgs = 'N';
                    aArgs  = new Argument[cAll];
                    aAsts  = new ExprAST[cAll];

                    if (cTypeParams > 0)
                        {
                        System.arraycopy(aargTypeParams, 0, aArgs, 0, cTypeParams);
                        for (int i = 0; i < cTypeParams; i++)
                            {
                            aAsts[i] = toExprAst(aArgs[i]);
                            }
                        }

                    for (int i = 0; i < cArgs; ++i)
                        {
                        Expression expr = args.get(i);
                        Argument   arg  = expr.generateArgument(ctx, code, true, true, errs);
                        int        iArg = cTypeParams + i;
                        aArgs[iArg] = i == cArgs-1 ? arg : ensurePointInTime(code, arg);
                        aAsts[iArg] = expr.getExprAST();
                        }

                    for (int i = 0; i < cDefaults; ++i)
                        {
                        int iArg = cTypeParams + cArgs + i;
                        aArgs[iArg] = Register.DEFAULT;
                        aAsts[iArg] = RegisterAST.defaultReg(atypeParams[i]);
                        }
                    break;
                }

            if (fConstruct)
                {
                MethodConstant idConstruct = (MethodConstant) argFn;
                switch (chArgs)
                    {
                    case '0' -> code.add(new Construct_0(idConstruct));
                    case '1' -> code.add(new Construct_1(idConstruct, arg0));
                    case 'N' -> code.add(new Construct_N(idConstruct, aArgs));
                    case 'T' -> throw new UnsupportedOperationException("TODO: Construct_T");
                    default  -> throw new IllegalStateException();
                    }
                m_astInvoke = new CallExprAST(astFn, TypeConstant.NO_TYPES, aAsts, fAsync);
                return;
                }

            // generate registers for the return values
            char chRets;
            switch (cRets)
                {
                case 0:
                    if (!fAsync)
                        {
                        chRets = '0';
                        break;
                        }
                    aargResult = new Argument[] {Register.ASYNC};
                    // fall through
                case 1:
                    chRets = '1';
                    break;
                default:
                    chRets = 'N';
                    break;
                }

            switch (combine(chArgs, chRets))
                {
                case _00:
                    code.add(new Call_00(argFn));
                    break;

                case _10:
                    if (m_fTupleArg)
                        {
                        code.add(new Call_T0(argFn, arg0));
                        }
                    else
                        {
                        code.add(new Call_10(argFn, arg0));
                        }
                    break;

                case _N0:
                    code.add(new Call_N0(argFn, aArgs));
                    break;

                case _01:
                    if (m_fPack)
                        {
                        code.add(new Call_0T(argFn, aargResult[0]));
                        }
                    else
                        {
                        code.add(new Call_01(argFn, aargResult[0]));
                        }
                    break;

                case _11:
                    if (m_fPack)
                        {
                        if (m_fTupleArg)
                            {
                            code.add(new Call_TT(argFn, arg0, aargResult[0]));
                            }
                        else
                            {
                            code.add(new Call_1T(argFn, arg0, aargResult[0]));
                            }
                        }
                    else
                        {
                        if (m_fTupleArg)
                            {
                            code.add(new Call_T1(argFn, arg0, aargResult[0]));
                            }
                        else
                            {
                            code.add(new Call_11(argFn, arg0, aargResult[0]));
                            }
                        }
                    break;

                case _N1:
                    if (m_fPack)
                        {
                        code.add(new Call_NT(argFn, aArgs, aargResult[0]));
                        }
                    else
                        {
                        code.add(new Call_N1(argFn, aArgs, aargResult[0]));
                        }
                    break;

                case _0N:
                    code.add(new Call_0N(argFn, aargResult));
                    break;

                case _1N:
                    if (m_fTupleArg)
                        {
                        code.add(new Call_TN(argFn, arg0, aargResult));
                        }
                    else
                        {
                        code.add(new Call_1N(argFn, arg0, aargResult));
                        }
                    break;

                case _NN:
                    code.add(new Call_NN(argFn, aArgs, aargResult));
                    break;

                default:
                    throw new UnsupportedOperationException("invocation " + combine(chArgs, chRets));
                }

            m_astInvoke = new CallExprAST(astFn, getTypes(), aAsts, fAsync);
            return;
            }

        // see if we need to bind (or partially bind) the function
        int[]      aiArg = null;
        Argument[] aArg  = null;
        ExprAST[]  aAst  = null;

        // count the number of parameters to bind, which includes all type parameters and all
        // default values, so for a function:
        //      void foo(Boolean a = false, Int b = 0, String c = "")
        // an expression "&foo(b=5)" will result into a function of the type
        // "function void (Boolean, String)", that leaves arguments "a" and "c" unbound,
        // and an expression "&foo(true)" will result into a function of the type
        // "function void (Int, String)", that leaves arguments "b" and "c" unbound
        int cBind = cTypeParams;
        for (int i = 0; i < cArgs; ++i)
            {
            if (!args.get(i).isNonBinding())
                {
                ++cBind;
                }
            }

        if (cBind > 0)
            {
            aiArg = new int[cBind];
            aArg  = new Argument[cBind];
            aAst  = new ExprAST[cBind];
            for (int i = 0; i < cTypeParams; ++i)
                {
                aiArg[i] = i;
                aArg [i] = aargTypeParams[i];
                aAst [i] = toExprAst(aargTypeParams[i]);
                }

            for (int i = 0, iBind = cTypeParams; i < cArgs; ++i)
                {
                Expression exprArg = args.get(i);
                if (!exprArg.isNonBinding())
                    {
                    aiArg[iBind] = cTypeParams + i;
                    aArg [iBind] = ensurePointInTime(code,
                            exprArg.generateArgument(ctx, code, false, true, errs));
                    aAst [iBind] = exprArg.getExprAST();

                    iBind++;
                    }
                }
            }
        else if (argFn instanceof Register regFn && regFn.isSuper())
            {
            // non-bound super(...) function still needs to bind a target
            aiArg = new int[0];
            aArg  = NO_RVALUES;
            aAst  = ExprAST.NO_EXPRS;
            }

        if (cLVals > 0)
            {
            Assignable lval = aLVal[0];
            if (aiArg == null)
                {
                lval.assign(argFn, code, errs);
                m_astInvoke = astFn;
                }
            else
                {
                if (lval.isLocalArgument())
                    {
                    code.add(new FBind(argFn, aiArg, aArg, lval.getLocalArgument()));
                    }
                else
                    {
                    Register regFn = code.createRegister(getType());
                    code.add(new FBind(argFn, aiArg, aArg, regFn));
                    lval.assign(regFn, code, errs);
                    }
                m_astInvoke = new BindFunctionAST(astFn, aiArg, aAst, getType());
                }
            }
        }

    /**
     * @return the Argument for the target and set the {@link #m_astTarget}
     */
    private Argument generateTarget(Context ctx, Code code, Expression exprLeft,
                                    boolean fLocalPropOk, boolean fTargetOnStack, ErrorListener errs)
        {
        // the method needs a target (its "this")
        Argument argTarget;
        if (exprLeft == null)
            {
            TargetInfo targetInfo = m_targetInfo;
            Register   regTarget;
            if (targetInfo == null)
                {
                regTarget   = ctx.getThisRegister();
                m_astTarget = regTarget.getRegisterAST();
                }
            else
                {
                TypeConstant typeTarget = targetInfo.getTargetType();
                int          cStepsOut  = targetInfo.getStepsOut();
                if (cStepsOut > 0)
                    {
                    regTarget = code.createRegister(typeTarget, fTargetOnStack);
                    code.add(new MoveThis(cStepsOut, regTarget));
                    m_astTarget = new OuterExprAST(ctx.getThisRegisterAST(), cStepsOut, typeTarget);
                    }
                else
                    {
                    regTarget = ctx.getThisRegister();
                    if (!typeTarget.equals(regTarget.getType()))
                        {
                        // most likely the typeTarget has "private" access
                        regTarget = regTarget.narrowType(typeTarget);
                        }
                    m_astTarget = regTarget.getRegisterAST();
                    }
                }
            argTarget = regTarget;
            }
        else
            {
            argTarget   = exprLeft.generateArgument(ctx, code, fLocalPropOk, fTargetOnStack, errs);
            m_astTarget = exprLeft.getExprAST();
            }
        return argTarget;
        }

    @Override
    public ExprAST getExprAST()
        {
        return m_astInvoke == null ? super.getExprAST() : m_astInvoke;
        }


    // ----- method resolution helpers -------------------------------------------------------------

    /**
     * @return true iff this expression does not actually result in an invocation, but instead
     *         resolves to a reference to a method or a function as its result
     */
    protected boolean isSuppressCall()
        {
        return (expr instanceof NameExpression exprName && exprName.isSuppressDeref())
                || isAnyArgUnbound();
        }

    /**
     * @return true iff any argument will be bound
     */
    protected boolean isAnyArgBound()
        {
        for (Expression expr : args)
            {
            if (!expr.isNonBinding())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return true iff any argument will be left unbound
     */
    protected boolean isAnyArgUnbound()
        {
        for (Expression expr : args)
            {
            if (expr.isNonBinding())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Resolve the expression to determine the referred to method or function. Responsible for
     * setting {@link #m_argMethod}, {@link #m_idConvert}, {@link #m_fBindTarget},
     * {@link #m_fBindParams}, and {@link #m_fCall}.
     *
     * @param ctx         the compiler context
     * @param fForce      true to force the resolution, even if it has been done previously
     * @param typeLeft    the type of the "left" expression of the name, or null if there is no left
     * @param atypeReturn (optional) an array of return types
     * @param errs        the error listener to log errors to
     *
     * @return the method constant, or null if it was not determinable, in which case an error has
     *         been reported
     */
    protected Argument resolveName(Context ctx, boolean fForce, TypeConstant typeLeft,
                                   TypeConstant[] atypeReturn, ErrorListener errs)
        {
        if (!fForce && m_argMethod != null)
            {
            return m_argMethod;
            }

        boolean fNoFBind   = !isAnyArgBound();
        boolean fNoCall    = isSuppressCall();
        boolean fNamedArgs = containsNamedArgs(args);

        m_targetInfo  = null;
        m_argMethod   = null;
        m_idConvert   = null;
        m_fBindTarget = false;
        m_fBindParams = !fNoFBind;
        m_fCall       = !fNoCall;
        m_fNamedArgs  = fNamedArgs;

        ConstantPool pool = pool();
        if (atypeReturn != null && atypeReturn.length > 0 && fNoCall)
            {
            // "no call" means that we are expected to produce a function, but the code below
            // treats atypeReturn as function return types
            if (atypeReturn.length > 1)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, atypeReturn.length);
                return null;
                }

            TypeConstant typeFn = atypeReturn[0];

            if (typeFn.containsFunctionType())
                {
                atypeReturn = pool.extractFunctionReturns(typeFn);
                }
            else if (pool.typeFunction().isA(typeFn)) // e.g. Object
                {
                // whatever the invocation finds is going to work
                atypeReturn = TypeConstant.NO_TYPES;
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, "Function", typeFn.getValueString());
                return null;
                }
            }

        // if the name does not have a left expression, then walk up the AST parent node chain
        // looking for a registered name, i.e. a local variable of that name, stopping once the
        // containing method/function (but <b>not</b> a lambda, since it has a permeable barrier to
        // enable local variable capture) is reached
        NameExpression exprName   = (NameExpression) expr;
        Token          tokName    = exprName.getNameToken();
        String         sName      = exprName.getName();
        boolean        fConstruct = "construct".equals(sName);
        boolean        fSingleton = false;
        Expression     exprLeft   = exprName.left;
        if (exprLeft == null)
            {
            Argument arg = ctx.resolveName(tokName, ErrorListener.BLACKHOLE);

            if (arg == null)
                {
                typeLeft = ctx.getThisType();

                if (ctx.isMethod())
                    {
                    // try to use the type info
                    TypeInfo infoLeft = getTypeInfo(ctx, typeLeft, errs);

                    arg = findCallable(ctx, typeLeft, infoLeft, sName, MethodKind.Any,
                                true, atypeReturn, ErrorListener.BLACKHOLE);
                    if (arg instanceof MethodConstant)
                        {
                        MethodStructure method = getMethod(infoLeft, arg);
                        assert method != null;

                        m_argMethod   = arg;
                        m_method      = method;
                        m_fBindTarget = !method.isFunction();
                        m_targetInfo  = new TargetInfo(sName, method, typeLeft, 0);
                        return arg;
                        }
                    }

                // report the error
                if ("construct".equals(sName))
                    {
                    log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR,
                            ctx.getThisType().getValueString());
                    }
                else if ("super".equals(sName))
                    {
                    log(errs, Severity.ERROR, Compiler.NO_SUPER);
                    }
                else
                    {
                    TypeConstant typeTarget = ctx.getThisType();
                    TypeInfo     infoTarget = getTypeInfo(ctx, null, ErrorListener.BLACKHOLE);

                    // check if the method would be callable from outside the constructor
                    if (ctx.isConstructor() &&
                            findCallable(ctx, typeTarget, infoTarget, sName, MethodKind.Any,
                                true, atypeReturn, ErrorListener.BLACKHOLE) != null)
                        {
                        log(errs, Severity.ERROR, Compiler.INVALID_CALL_FROM_CONSTRUCT, sName);
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sName,
                                typeTarget.getValueString());
                        }
                    }
                return null;
                }

            if (arg instanceof Register reg)
                {
                int cTypeParams = 0;
                int cDefaults   = 0;
                if (reg.isPredefined())
                    {
                    // report specific error messages for incorrect "this" or "super" use
                    switch (reg.getIndex())
                        {
                        case Op.A_THIS:
                        case Op.A_TARGET:
                        case Op.A_PUBLIC:
                        case Op.A_PROTECTED:
                        case Op.A_PRIVATE:
                        case Op.A_STRUCT:
                            if (ctx.isFunction())
                                {
                                exprName.log(errs, Severity.ERROR, Compiler.NO_THIS);
                                return null;
                                }
                            break;

                        case Op.A_SUPER:
                            {
                            if (ctx.isFunction())
                                {
                                exprName.log(errs, Severity.ERROR, Compiler.NO_SUPER);
                                return null;
                                }

                            if (ctx.isConstructor())
                                {
                                // carved out special use for using direct "super()" in constructors
                                Contribution contribExtends = ctx.getThisClass().
                                        findContribution(Component.Composition.Extends);
                                if (contribExtends == null || ctx.isFunction())
                                    {
                                    exprName.log(errs, Severity.ERROR, Compiler.NON_VIRTUAL_SUPER);
                                    return null;
                                    }

                                if (fNoCall)
                                    {
                                    exprName.log(errs, Severity.ERROR, Compiler.INVALID_SUPER_REFERENCE);
                                    return null;
                                    }

                                TypeConstant typeSuper = pool.ensureAccessTypeConstant(
                                        contribExtends.getTypeConstant(), Access.PROTECTED);

                                TypeInfo       infoSuper   = typeSuper.ensureTypeInfo(errs);
                                MethodConstant idConstruct = (MethodConstant) findCallable(ctx, typeSuper,
                                        infoSuper, "construct", MethodKind.Constructor,
                                        false, atypeReturn, ErrorListener.BLACKHOLE);
                                if (idConstruct == null)
                                    {
                                    log(errs, Severity.ERROR, Compiler.IMPLICIT_SUPER_CONSTRUCTOR_MISSING,
                                        ctx.getThisType().getValueString(), typeSuper.getValueString());
                                    return null;
                                    }

                                MethodStructure ctor = getMethod(infoSuper, idConstruct);
                                assert ctor != null;

                                m_argMethod   = idConstruct;
                                m_method      = ctor;
                                m_fBindTarget = false;
                                return idConstruct;
                                }

                            // the code below is slightly opportunistic; there is a chance that
                            // none of the "super" methods have the required number of arguments
                            // DEFERRED: we need to add a corresponding check, which is not as
                            // simple as just getting the MethodInfo from the context class -
                            // the current method can be a nested property accessor as well
                            MethodStructure method = ctx.getMethod();

                            cTypeParams = method.getTypeParamCount();
                            cDefaults   = method.getDefaultParamCount();
                            break;
                            }
                        }
                    }

                if (testFunction(ctx, arg.getType(), cTypeParams, cDefaults, atypeReturn, errs) == null)
                    {
                    return null;
                    }
                m_argMethod = arg;
                return arg;
                }

            if (arg instanceof TargetInfo target)
                {
                TypeConstant     typeTarget = target.getTargetType();
                TypeInfo         info       = typeTarget.ensureTypeInfo(errs);
                IdentityConstant id         = target.getId();
                if (id instanceof MultiMethodConstant)
                    {
                    // find the method based on the signature
                    // TODO this only finds methods immediately contained within the class; does not find nested methods!!!
                    MethodKind kind =
                            fConstruct                                ? MethodKind.Constructor :
                            (fNoCall && fNoFBind) || target.hasThis() ? MethodKind.Any :
                                                                        MethodKind.Function;
                    ErrorListener    errsTemp   = errs.branch(this);
                    IdentityConstant idCallable = findMethod(ctx, typeTarget, info, sName,
                            args, kind, !fNoCall, id.isNested(), atypeReturn, errsTemp);
                    if (idCallable == null)
                        {
                        // check to see if we had found something had we included methods in the
                        // search
                        if (kind == MethodKind.Function &&
                                findMethod(ctx, typeTarget, info, sName, args, MethodKind.Method,
                                    !fNoCall, id.isNested(), atypeReturn, ErrorListener.BLACKHOLE) != null)
                            {
                            if (target.getStepsOut() > 0)
                                {
                                exprName.log(errs, Severity.ERROR, Compiler.NO_OUTER_METHOD,
                                    target.getTargetType().removeAccess().getValueString(), sName);
                                }
                            else
                                {
                                exprName.log(errs, Severity.ERROR, Compiler.NO_THIS_METHOD,
                                    sName, target.getTargetType().removeAccess().getValueString());
                                }
                            }
                        else
                            {
                            errsTemp.merge();
                            }
                        return null;
                        }
                    else
                        {
                        m_targetInfo  = target; // (only used for non-constants)
                        m_argMethod   = idCallable;
                        m_method      = getMethod(info, idCallable);
                        m_fBindTarget = m_method != null && !m_method.isFunction();

                        errsTemp.merge();
                        return idCallable;
                        }
                    }
                else if (id instanceof PropertyConstant idProp)
                    {
                    PropertyInfo prop = info.findProperty(idProp);
                    if (prop == null)
                        {
                        throw new IllegalStateException("missing property: " + id + " on " + target.getTargetType());
                        }

                    if (testFunction(ctx, prop.getType(), 0, 0, atypeReturn, errs) == null)
                        {
                        return null;
                        }

                    if (prop.isConstant() || target.hasThis())
                        {
                        m_targetInfo = target; // (only used for non-constants)
                        m_argMethod  = id;
                        return id;
                        }
                    else
                        {
                        // the property requires a target, but there is no "left." before the prop
                        // name, and there is no "this." (explicit or implicit) because there is no
                        // "this"
                        exprName.log(errs, Severity.ERROR, Compiler.NO_THIS_PROPERTY, sName, target.getTargetType());
                        return null;
                        }
                    }
                else
                    {
                    throw new IllegalStateException("unsupported constant format: " + id);
                    }
                }

            // must NOT have resolved the name to a method constant (that should be impossible)
            assert !(arg instanceof MethodConstant);

            if (arg instanceof MultiMethodConstant idMM)
                {
                // an import name can specify a MultiMethodConstant;
                // we only allow functions (not methods or constructors)
                IdentityConstant idClz      = idMM.getParentConstant();
                TypeConstant     typeTarget = idClz.getFormalType();
                TypeInfo         info       = getTypeInfo(ctx, typeTarget, errs);
                IdentityConstant idCallable = findMethod(ctx, typeTarget, info, sName,
                        args, MethodKind.Any, !fNoCall, false, atypeReturn, errs);
                if (idCallable == null)
                    {
                    return null;
                    }

                MethodStructure method = getMethod(info, idCallable);
                if (!method.isFunction())
                    {
                    exprName.log(errs, Severity.ERROR, Compiler.NO_THIS_PROPERTY,
                            sName, idCallable.getParentConstant().getValueString());
                    return null;
                    }
                m_method      = method;
                m_argMethod   = idCallable;
                m_fBindTarget = false;
                return idCallable;
                }

            if (arg instanceof PropertyConstant idProp)
                {
                // an import name can specify a static PropertyConstant
                return testStaticProperty(ctx, idProp, atypeReturn, errs);
                }

            log(errs, Severity.ERROR, Compiler.ILLEGAL_INVOCATION, tokName.getValueText());
            return null;
            }

        // there is a "left" expression for the name
        if (tokName.isSpecial())
            {
            tokName.log(errs, getSource(), Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, tokName.getValueText());
            return null;
            }

        // the left expression provides the scope to search for a matching method/function;
        // if the left expression is itself a NameExpression, and it's in identity mode (i.e. a
        // possible identity), then check the identity first
        if (exprLeft instanceof NameExpression nameLeft)
            {
            if ("super".equals(nameLeft.getName()))
                {
                log(errs, Severity.ERROR, Compiler.INVALID_SUPER_REFERENCE);
                return null;
                }

            if (nameLeft.isIdentityMode(ctx, true))
                {
                // the left identity
                // - methods are included because there is a left, but since it is to obtain a
                //   method reference, there must not be any arg binding or actual invocation
                // - functions are included because the left is identity-mode
                IdentityConstant idLeft = nameLeft.getIdentity(ctx);
                Access           access = fConstruct ? Access.PROTECTED : Access.PUBLIC;
                // TODO: if left is a super class or other contribution, use PROTECTED access as well
                if (ctx.getThisClassId().isNestMateOf(idLeft))
                    {
                    access = Access.PRIVATE;
                    }

                TypeInfo infoLeft;
                if (nameLeft.getMeaning() == NameExpression.Meaning.Type)
                    {
                    // "Class" meaning in IdentityMode can only indicate a "type-of-class" scenario
                    assert typeLeft.isTypeOfType();

                    typeLeft = typeLeft.getParamType(0);
                    if (access != typeLeft.getAccess())
                        {
                        typeLeft = pool.ensureAccessTypeConstant(typeLeft, access);
                        }
                    infoLeft = typeLeft.ensureTypeInfo(errs);
                    }
                else
                    {
                    if (fConstruct)
                        {
                        // this can only be a "construct X(...)" call coming from "this:struct"
                        // context
                        ClassStructure clzThis = ctx.getThisClass();
                        Contribution   contrib = clzThis.findContribution(idLeft);
                        if (contrib == null)
                            {
                            log(errs, Severity.ERROR, Compiler.INVALID_CONSTRUCT_CALL,
                                    idLeft.getValueString());
                            return null;
                            }

                        TypeConstant typeContrib = contrib.getTypeConstant();
                        switch (contrib.getComposition())
                            {
                            case Equal:
                                assert typeContrib.equals(ctx.getThisType());

                                typeLeft = pool.ensureAccessTypeConstant(typeContrib, Access.PRIVATE);
                                break;

                            case Extends:
                                if (!clzThis.getSuper().getIdentityConstant().equals(idLeft))
                                    {
                                    log(errs, Severity.WARNING, Compiler.SUPER_CONSTRUCTOR_SKIPPED);
                                    }
                                // fall through
                            default:
                                typeLeft = pool.ensureAccessTypeConstant(typeContrib, access);
                                break;
                            }
                        infoLeft = typeLeft.ensureTypeInfo(errs);
                        }
                    else
                        {
                        // this is either:
                        // - a function call (e.g. Duration.ofSeconds(1)), or
                        // - a function call with an explicit formal target type (e.g.
                        //   List<Int>.equals(l1, l2)), or
                        // - a call on the Class itself (Point.instantiate(struct)), in which
                        //   case the first "findCallable" will fail and therefore typeLeft must
                        //   not be changed
                        // - a method call for a singleton (e.g. TestReflection.report(...))
                        TypeConstant typeTarget = idLeft.getType();
                        if (access != Access.PUBLIC)
                            {
                            typeTarget = typeTarget.ensureAccess(access);
                            }
                        infoLeft = typeTarget.ensureTypeInfo(errs);

                        if (typeTarget.isParamsSpecified())
                            {
                            if (infoLeft.isSingleton() || typeTarget.isA(pool.typeClass()))
                                {
                                exprLeft.log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                                return null;
                                }
                            m_typeTarget = typeTarget;
                            }
                        }
                    }
                fSingleton = infoLeft.isSingleton();

                MethodKind kind = fConstruct                        ? MethodKind.Constructor :
                                  fNoFBind && fNoCall || fSingleton ? MethodKind.Any :
                                                                      MethodKind.Function;

                ErrorListener errsTemp = errs.branch(this);
                Argument      arg      = findCallable(ctx, infoLeft.getType(), infoLeft, sName,
                        kind, false, atypeReturn, errsTemp);

                if (arg == null && kind == MethodKind.Function &&
                        findCallable(ctx, infoLeft.getType(), infoLeft, sName,
                            MethodKind.Any, false, atypeReturn, ErrorListener.BLACKHOLE) != null)
                    {
                    exprName.log(errs, Severity.ERROR, Compiler.NO_THIS_METHOD,
                            sName, infoLeft.getType().getValueString());
                    return null;
                    }
                if (arg instanceof MethodConstant idMethod)
                    {
                    errsTemp.merge();

                    MethodInfo infoMethod = infoLeft.getMethodById(idMethod);
                    assert infoMethod != null;

                    if (infoMethod.isAbstractFunction())
                        {
                        log(errs, Severity.ERROR, Compiler.ILLEGAL_FUNKY_CALL,
                                idMethod.getValueString());
                        return null;
                        }

                    m_argMethod   = idMethod;
                    m_method      = infoMethod.getTopmostMethodStructure(infoLeft);
                    m_fBindTarget = fSingleton && !m_method.isFunction();
                    return idMethod;
                    }
                if (arg instanceof PropertyConstant idProp)
                    {
                    errsTemp.merge();
                    return testStaticProperty(ctx, idProp, atypeReturn, errs);
                    }
                }

            switch (nameLeft.getMeaning())
                {
                case Class:
                    fSingleton = typeLeft.ensureTypeInfo(errs).isSingleton();
                    break;

                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) nameLeft.resolveRawArgument(ctx, false, errs);
                    if (idProp.isFormalType())
                        {
                        // Example (NaturalHasher.x):
                        //   Int hashOf(Value value)
                        //     {
                        //     return Value.hashCode(value);
                        //     }
                        //
                        // "this" is "Value.hashCode(value)"
                        // idProp.getFormalType() is NaturalHasher.Value

                        TypeConstant type     = nameLeft.getImplicitType(ctx).getParamType(0);
                        TypeInfo     infoType = getTypeInfo(ctx, type, errs);

                        ErrorListener errsTemp = errs.branch(this);

                        Argument arg = findCallable(ctx, type, infoType, sName, MethodKind.Function,
                            false, atypeReturn, errsTemp);
                        if (arg instanceof MethodConstant)
                            {
                            m_argMethod   = arg;
                            m_method      = getMethod(infoType, arg);
                            m_fBindTarget = false;
                            m_idFormal    = idProp;
                            errsTemp.merge();
                            return arg;
                            }
                        }
                    break;
                    }

                case FormalChildType:
                    {
                    // Example:
                    //   static <CompileType extends Hasher> Int hashCode(CompileType array)
                    //      {
                    //      Int hash = 0;
                    //      for (CompileType.Element el : array)
                    //          {
                    //          hash += CompileType.Element.hashCode(el);
                    //          }
                    //      return hash;
                    //      }
                    // "this" is "CompileType.Element.hashCode(el)"
                    //  typeLeft is a type of "CompileType.Element" formal type child

                    assert typeLeft.isTypeOfType();
                    TypeConstant  typeFormal = typeLeft.getParamType(0);
                    TypeInfo      infoLeft   = typeFormal.ensureTypeInfo(errs);
                    ErrorListener errsTemp   = errs.branch(this);

                    Argument arg = findCallable(ctx, typeFormal, infoLeft, sName, MethodKind.Function,
                                                false, atypeReturn, errsTemp);
                    if (arg instanceof MethodConstant)
                        {
                        m_argMethod   = arg;
                        m_method      = getMethod(infoLeft, arg);
                        m_fBindTarget = false;
                        m_idFormal    = (FormalTypeChildConstant) nameLeft.getIdentity(ctx);
                        errsTemp.merge();
                        return arg;
                        }
                    break;
                    }
                }
            }

        // use the type of the left expression to get the TypeInfo that must contain the
        // method/function to call
        // - methods are included because there is a left, and it is NOT identity-mode
        // - functions are NOT included because the left is NOT identity-mode
        TypeInfo      infoLeft = getTypeInfo(ctx, typeLeft, errs);
        ErrorListener errsMain = errs.branch(this);
        MethodKind    kind     = fConstruct ? MethodKind.Constructor :
                                 fSingleton ? MethodKind.Any :
                                              MethodKind.Method;

        Argument arg = findCallable(ctx, typeLeft, infoLeft, sName, kind, false, atypeReturn, errsMain);
        if (arg != null)
            {
            if (arg instanceof MethodConstant)
                {
                m_argMethod   = arg;
                m_method      = getMethod(infoLeft, arg);
                m_fBindTarget = m_method != null && !m_method.isFunction();
                }
            else
                {
                // just return the property; the rest will be handled by the caller
                assert arg instanceof PropertyConstant;
                }
            errsMain.merge();
            return arg;
            }

        if (typeLeft.isFormalTypeType())
            {
            // allow for a function on a formal type to be called, e.g.:
            //  CompileType.f(x), where CompileType's constraint type has the function "f(x)"
            FormalConstant idFormal       = (FormalConstant) typeLeft.getParamType(0).getDefiningConstant();
            TypeConstant   typeConstraint = idFormal.getConstraintType();
            TypeInfo       infoConstraint = typeConstraint.ensureTypeInfo(ErrorListener.BLACKHOLE);

            ErrorListener errsAlt = errs.branch(this);

            arg = findMethod(ctx, typeConstraint, infoConstraint, sName, args, MethodKind.Function,
                        !fNoCall, false, atypeReturn, errsAlt);
            if (arg != null)
                {
                m_argMethod   = arg;
                m_method      = getMethod(infoConstraint, arg);
                m_fBindTarget = false;
                m_idFormal    = idFormal;
                errsAlt.merge();
                return arg;
                }
            }
        else if (typeLeft.isTypeOfType())
            {
            // almost identical to the above case, allow calling a function on a class represented
            // by the DataType; this for example allows writing (assuming "Type<Number> numType")
            //      "numType.fixedBitLength()"
            // instead of more explicit
            //      "numType.DataType.fixedBitLength()"
            TypeConstant typeDataType = typeLeft.getParamType(0);
            TypeInfo     infoDataType = typeDataType.ensureTypeInfo(ErrorListener.BLACKHOLE);

            ErrorListener errsAlt = errs.branch(this);

            arg = findMethod(ctx, typeDataType, infoDataType, sName, args, MethodKind.Function,
                        !fNoCall, false, atypeReturn, errsAlt);
            if (arg != null)
                {
                m_argMethod   = arg;
                m_method      = getMethod(infoDataType, arg);
                m_fBindTarget = false;
                m_idFormal    = (PropertyConstant) pool.clzType().getComponent().
                                    getChild("DataType").getIdentityConstant();
                errsAlt.merge();
                return arg;
                }
            }
        else if (typeLeft.isSingleUnderlyingClass(true) && !isSuppressCall() && !isAnyArgUnbound())
            {
            // allow for a function on the "left type" to be called (Bjarne'd):
            //    x.f(y, z) -> X.f(x, y, z), where X is the class of x
            List<Expression> listArgs = new ArrayList<>(args);
            listArgs.add(0, exprLeft);

            ErrorListener errsAlt = errs.branch(this);

            arg = findMethod(ctx, typeLeft, infoLeft, sName, listArgs, MethodKind.Function,
                        !fNoCall, false, atypeReturn, errsAlt);
            if (arg != null)
                {
                m_argMethod   = arg;
                m_method      = getMethod(infoLeft, arg);
                m_fBindTarget = false;
                m_fBjarne     = true;
                errsAlt.merge();
                return arg;
                }
            }

        if (exprLeft instanceof NameExpression nameLeft && typeLeft.isA(pool.typeFunction()))
            {
            // it appears that they try to use a variable or property, but have a function instead
            if (errsMain.hasError(Compiler.MISSING_METHOD) &&
                    infoLeft.findMethods(sName, -1, MethodKind.Any).isEmpty())
                {
                IdentityConstant idParent = nameLeft.isIdentityMode(ctx, false)
                    ? nameLeft.getIdentity(ctx).getNamespace()
                    : switch (nameLeft.getMeaning())
                        {
                        case Property -> ((PropertyConstant) nameLeft.
                                            resolveRawArgument(ctx, false, errs)).getNamespace();
                        case Variable -> ctx.getMethod().getIdentityConstant();
                        default       -> null;
                        };

                if (idParent != null)
                    {
                    log(errsMain, Severity.ERROR, Compiler.SUSPICIOUS_FUNCTION_USE,
                            nameLeft.getName(), idParent.getValueString());
                    }
                }
            }

        errsMain.merge();
        return null;
        }

    /**
     * @return a MethodStructure for the specified id
     */
    private MethodStructure getMethod(Context ctx, MethodConstant idMethod)
        {
        MethodStructure method = m_method;
        if (method == null)
            {
            method = (MethodStructure) idMethod.getComponent();
            if (method == null)
                {
                TypeConstant type = m_targetInfo.getTargetType();
                TypeInfo     info = getTypeInfo(ctx, type, ErrorListener.BLACKHOLE);

                method = getMethod(info, idMethod);
                }
            }
        return method;
        }

    /**
     * @return a method structure for the specified argument; null if not a method constant
     */
    private MethodStructure getMethod(TypeInfo infoType, Argument arg)
        {
        if (arg instanceof MethodConstant idMethod)
            {
            MethodInfo infoMethod = infoType.getMethodById(idMethod);
            assert infoMethod != null;

            return infoMethod.getTopmostMethodStructure(infoType);
            }
        return null;
        }

    /**
     * @return a PropertyStructure for the specified id
     */
    private PropertyStructure getProperty(Context ctx, PropertyConstant idProp)
        {
        PropertyStructure prop = (PropertyStructure) idProp.getComponent();
        if (prop == null)
            {
            TypeConstant type = m_targetInfo.getTargetType();
            TypeInfo     info = getTypeInfo(ctx, type, ErrorListener.BLACKHOLE);

            prop = info.findProperty(idProp).getHead().getStructure();
            }
        return prop;
        }

    /**
     * Find a named method or function that best matches the specified requirements.
     * </p>
     * Note: we need to pass both typeParent and infoParent, since in some context-sensitive cases
     *  typeParent.ensureTypeInfo() != infoParent and infoParent.getType() != typeParent
     *
     * @param ctx           the context
     * @param typeParent    the type to search the method or function for
     * @param infoParent    the TypeInfo to search for the method or function on
     * @param sName         the name of the method or function
     * @param kind          the kind of methods to include in the search
     * @param fAllowNested  if true, nested methods can be used at the target
     * @param aRedundant    the redundant return type information (helps to clarify which method or
     *                      function to select)
     * @param errs          the error listener to log errors to
     *
     * @return the matching method, function, or (rarely) property
     */
    protected IdentityConstant findCallable(
            Context        ctx,
            TypeConstant   typeParent,
            TypeInfo       infoParent,
            String         sName,
            MethodKind     kind,
            boolean        fAllowNested,
            TypeConstant[] aRedundant,
            ErrorListener  errs)
        {
        // check for a property of that name; if one exists, it must be of type function, or a type
        // with an @Auto conversion to function - which will be verified by testFunction()
        PropertyInfo prop = infoParent.findProperty(sName);
        if (prop != null)
            {
            return prop.getIdentity();
            }

        return findMethod(ctx, typeParent, infoParent, sName, args, kind,
                    m_fCall, fAllowNested, aRedundant, errs);
        }

    /**
     * Check if the specified property is a static function that matches the specified return types.
     * This method also sets up the {@link #m_typeTarget} value.
     *
     * @param ctx         the compiler context
     * @param idProp      the property id
     * @param atypeReturn (optional) an array of required return types
     * @param errs        the error listener to log errors to
     *
     * @return the property id or null if an error has been reported
     */
    protected Argument testStaticProperty(Context ctx, PropertyConstant idProp,
                                        TypeConstant[] atypeReturn, ErrorListener errs)
        {
        ConstantPool      pool  = pool();
        String            sName = idProp.getName();
        PropertyStructure prop  = (PropertyStructure) idProp.getComponent();

        if (!prop.isConstant())
            {
            expr.log(errs, Severity.ERROR, Compiler.NO_THIS_PROPERTY,
                    sName, idProp.getParentConstant().getValueString());
            return null;
            }

        if (testFunction(ctx, prop.getType(), 0, 0, atypeReturn, errs) == null)
            {
            expr.log(errs, Severity.ERROR, Compiler.NOT_TYPE_OF_TYPE,
                    sName, pool.typeFunction().getValueString());
            return null;
            }

        m_typeTarget = idProp.getParentConstant().getType();
        m_argMethod  = idProp;
        return idProp;
        }

    /**
     * Check the type of the thing that is either a function or needs to be converted into a
     * function.
     * <p/>
     * Responsible for setting the {@link #m_idConvert} field if a conversion is necessary.
     *
     * @param ctx         the compiler context
     * @param typeFn      the type of the function (or the type of the object that should know how
     *                    to convert itself into a function)
     * @param cTypeParams the number of type parameters the function carries
     * @param cDefaults   the number of default parameters the function allows
     * @param atypeReturn (optional) an array of required return types
     * @param errs        the error listener to log errors to
     *
     * @return the type of the function, or null if a type-safe type for the function could not be
     *         determined, in which case an error has been reported
     */
    protected TypeConstant testFunction(
            Context        ctx,
            TypeConstant   typeFn,
            int            cTypeParams,
            int            cDefaults,
            TypeConstant[] atypeReturn,
            ErrorListener  errs)
        {
        ConstantPool pool = pool();

        // if a match is found, then that is the function to use, and it is an error if the
        // type of that variable is not a function or a reference that has an @Auto
        // conversion to a function
        typeFn = typeFn.resolveTypedefs();

        boolean        fFunction = typeFn.isA(pool.typeFunction());
        MethodConstant idConvert = null;
        if (!fFunction)
            {
            if (typeFn.isA(pool.typeMethod()))
                {
                if (ctx.isMethod())
                    {
                    TypeConstant typeThis   = ctx.getThisType();
                    TypeConstant typeTarget = typeFn.getParamType(0);
                    if (!typeThis.isA(typeTarget))
                        {
                        log(errs, Severity.ERROR, Compiler.INVALID_METHOD_TARGET,
                                typeTarget.getValueString(), typeThis.getValueString());
                        return null;
                        }
                    ctx.requireThis(getStartPosition(), errs);
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.NO_THIS);
                    return null;
                    }
                }
            else
                {
                idConvert = typeFn.getConverterTo(pool.typeFunction());
                if (idConvert == null)
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE, "Function", typeFn.getValueString());
                    return null;
                    }
                else
                    {
                    typeFn = idConvert.getRawReturns()[0];
                    }
                }
            }

        // function must be parameterized by 2 fields: param types and return types
        // each is a parameterized "tuple" type constant with an array of types
        TypeConstant[] atypeParams = pool.extractFunctionParams(typeFn);
        if (atypeParams == null)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_PARAM_INFORMATION);
            return null;
            }

        if (m_fNamedArgs)
            {
            log(errs, Severity.ERROR, Compiler.ILLEGAL_ARG_NAME);
            return null;
            }

        int     cAllParams = atypeParams.length;
        int     cVisible   = cAllParams - cTypeParams;
        int     cRequired  = cVisible - cDefaults;
        boolean fValid     = true;

        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        if (cArgs > cVisible || cArgs < cRequired)
            {
            log(errs, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT, cRequired, cArgs);
            fValid = false;
            }

        for (int i = 0, c = Math.min(cVisible, cArgs); i < c; ++i)
            {
            TypeConstant typeParam = atypeParams[cTypeParams + i];
            Expression   exprArg   = listArgs.get(i);

            ctx = ctx.enterInferring(typeParam);
            if (!exprArg.testFit(ctx, typeParam, false, errs).isFit())
                {
                if (!errs.hasSeriousErrors())
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            typeParam.getValueString(), exprArg.getTypeString(ctx));
                    }
                fValid = false;
                }
            ctx = ctx.exit();
            }

        if (cArgs < cVisible)
            {
            // there are some default arguments; strip them from the function type
            typeFn = pool.buildFunctionType(
                    Arrays.copyOfRange(atypeParams, 0, cArgs),
                    pool.extractFunctionReturns(typeFn));
            }

        if (atypeReturn != null)
            {
            TypeConstant[] atypeFnRet = pool.extractFunctionReturns(typeFn);
            if (atypeFnRet == null)
                {
                log(errs, Severity.ERROR, Compiler.MISSING_PARAM_INFORMATION);
                return null;
                }

            TypeFit fit = calculateReturnFit(atypeFnRet, expr.toString(), m_fCall,
                                atypeReturn, ctx.getThisType(), errs);
            m_fPack = fit.isPacking();
            fValid  = fit.isFit();
            }

        if (fValid)
            {
            m_idConvert = idConvert;
            return typeFn;
            }
        else
            {
            return null;
            }
        }

    /**
     * Validate each of the arguments against their required types as dictated by the function type.
     *
     * @param ctx            the compiler context
     * @param typeFn         the type of the function
     * @param cTypeParams    the number of type parameters the function carries
     * @param cDefaults      the number of default parameters the function allows
     * @param atypeRequired  (optional) an array or required types
     * @param errs           the error listener to log errors to
     *
     * @return the type of the function return types for a "call" scenario,
     *         the type of the function itself for a "bind" scenario,
     *         or null if the validation fails, in which case an error has been reported
     */
    protected TypeConstant[] validateFunction(
            Context        ctx,
            TypeConstant   typeFn,
            int            cTypeParams,
            int            cDefaults,
            TypeConstant[] atypeRequired,
            ErrorListener  errs)
        {
        ConstantPool   pool        = pool();
        TypeConstant[] atypeParams = pool.extractFunctionParams(typeFn);
        int            cAllParams  = atypeParams.length;
        int            cVisible    = cAllParams - cTypeParams;
        int            cRequired   = cVisible - cDefaults;
        int            cArgs       = args.size();

        if (cArgs > cVisible || cArgs < cRequired)
            {
            log(errs, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT, cRequired, cArgs);
            return null;
            }

        if (cTypeParams > 0)
            {
            atypeParams = Arrays.copyOfRange(atypeParams, cTypeParams, cAllParams);
            }

        if (validateExpressions(ctx, args, atypeParams, errs) == null)
            {
            return null;
            }

        if (m_fCall)
            {
            return m_fPack
                ? new TypeConstant[] {typeFn.getParamType(1)}
                : pool.extractFunctionReturns(typeFn);
            }

        if (m_fBindParams)
            {
            typeFn = bindFunctionParameters(typeFn);
            }

        if (atypeRequired != null && atypeRequired.length > 0)
            {
            TypeConstant typeReqFn = atypeRequired[0];
            if (!typeReqFn.equals(pool.typeObject())) // any function is an Object
                {
                // the isA() on a function allows a reduction of the parameter number
                // (allowing for default arguments), so we need to compensate here
                TypeConstant[] atypeReqParams = pool.extractFunctionParams(typeReqFn);
                TypeConstant[] atypeFnParams  = pool.extractFunctionParams(typeFn);
                int            cReqParams     = atypeReqParams == null ? 0 : atypeReqParams.length;
                int            cFnParams      = atypeFnParams  == null ? 0 : atypeFnParams.length;

                if (cReqParams < cFnParams)
                    {
                    // report the "wrong type" rather than the "wrong argument count"
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeReqFn, typeFn);
                    return null;
                    }
                }
            }

        return new TypeConstant[] {typeFn};
        }

    /**
     * @return return type for the specified argument type, which is known to be a Function
     */
    protected TypeConstant[] calculateReturnType(TypeConstant typeFn)
        {
        TypeConstant[] atypeReturn;
        if (m_fCall)
            {
            atypeReturn = pool().extractFunctionReturns(typeFn);
            return atypeReturn == null ? TypeConstant.NO_TYPES :
                   m_fPack             ? new TypeConstant[] {pool().ensureTupleType(atypeReturn)}
                                       : atypeReturn;
            }

        if (m_fBindParams)
            {
            if (args.size() > pool().extractFunctionParams(typeFn).length)
                {
                return TypeConstant.NO_TYPES;
                }
            typeFn = bindFunctionParameters(typeFn);
            }

        return new TypeConstant[] {typeFn};
        }

    /**
     * @return a type parameter resolver for a given method and array of return types or null if the
     *         type parameters could not be resolved, reporting the unresolved type parameters to
     *         the error list
     */
    private GenericTypeResolver makeTypeParameterResolver(Context ctx, MethodStructure method,
            boolean fAllowPending, TypeConstant typeTarget, TypeConstant[] atypeReturn, ErrorListener errs)
        {
        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        TypeConstant[] atypeArgs = new TypeConstant[cArgs];
        for (int i = 0; i < cArgs; i++)
            {
            atypeArgs[i] = listArgs.get(i).getImplicitType(ctx);
            }

        transformTypeArguments(ctx, method, listArgs, atypeArgs);

        Map<FormalConstant, TypeConstant> mapTypeParams =
                method.resolveTypeParameters(pool(), typeTarget, atypeArgs, atypeReturn, fAllowPending);

        if (mapTypeParams.size() == method.getTypeParamCount())
            {
            return GenericTypeResolver.of(mapTypeParams);
            }

        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                method.collectUnresolvedTypeParameters(mapTypeParams.keySet().
                    stream().map(NamedConstant::getName).collect(Collectors.toSet())));
        return null;
        }

    /**
     * Resolve the specified types against the specified resolver.
     *
     * @return an array of resolved types
     */
    private TypeConstant[] resolveTypes(GenericTypeResolver resolver, TypeConstant[] atype)
        {
        TypeConstant[] atypeResolved = atype;
        if (resolver != null)
            {
            ConstantPool pool = pool();
            for (int i = 0, c = atype.length; i < c; i++)
                {
                TypeConstant typeOriginal = atype[i];
                TypeConstant typeResolved = typeOriginal.resolveGenerics(pool, resolver);
                if (typeResolved != typeOriginal)
                    {
                    if (atypeResolved == atype)
                        {
                        atypeResolved = atype.clone();
                        }
                    atypeResolved[i] = typeResolved;
                    }
                }
            }
        return atypeResolved;
        }

    /**
     * Create a new function type by binding all bounded expressions.
     *
     * @param typeFn  the original function type
     *
     * @return a new function type that skips all bound parameters
     */
    private TypeConstant bindFunctionParameters(TypeConstant typeFn)
        {
        ConstantPool     pool     = pool();
        List<Expression> listArgs = args;
        for (int i = listArgs.size() - 1; i >= 0; --i)
            {
            Expression expr = listArgs.get(i);
            if (!expr.isNonBinding())
                {
                typeFn = pool.bindFunctionParam(typeFn, i, null);
                }
            }
        return typeFn;
        }

    /**
     * Create a new function type by removing the type parameters from the function arguments.
     *
     * @param typeFn       the original function type
     * @param cTypeParams  the number of type parameters to remove
     *
     * @return a new function type that skips all bound parameters
     */
    private TypeConstant removeTypeParameters(TypeConstant typeFn, int cTypeParams)
        {
        ConstantPool   pool        = pool();
        TypeConstant[] atypeParams = pool.extractFunctionParams(typeFn);

        return pool.buildFunctionType(
                Arrays.copyOfRange(atypeParams, cTypeParams, atypeParams.length),
                pool.extractFunctionReturns(typeFn));
        }

    /**
     * There are scenarios, when a MethodConstant doesn't actually point to a method structure.
     * That allows the compiler to supply more specific target bound type information on the method
     * signature for the runtime.
     * <p/>
     * The purpose of this method is to make sure that despite that "disconnect", the identity of
     * the "rebased" MethodConstant parent identifies the parent of the actual method structure,
     * allowing the runtime quickly identify the topmost structure in the virtual call chain that
     * is known at the compile time.
     *
     * @param idMethod  the MethodConstant to evaluate and, if necessary, rebase the parent of
     * @param method    the actual method structure
     *
     * @return the MethodConstant whose name space is the same as the method structure parent's
     *         identity
     */
    private MethodConstant rebaseMethodConstant(MethodConstant idMethod, MethodStructure method)
        {
        if (!method.equals(idMethod.getComponent()))
            {
            if (method.getAccess() == Access.PRIVATE)
                {
                // for private methods, the idMethod *must be* the actual identity
                idMethod = method.getIdentityConstant();
                }
            else if (idMethod.isTopLevel()) // exempt methods inside properties or methods
                {
                Component parentId     = idMethod.getNamespace().getComponent();
                Component parentMethod = method.getParent().getParent();
                if (!parentId.equals(parentMethod))
                    {
                    idMethod = pool().ensureMethodConstant(
                            parentMethod.getIdentityConstant(), idMethod.getSignature());
                    }
                }
            }

        return idMethod;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr);
        if (async)
            {
            sb.append('^');
            }
        sb.append('(');

        boolean first = true;
        for (Expression arg : args)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(arg);
            }

        sb.append(')');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @param p  '0', '1', or 'N'
     * @param r  '0', '1', or 'N'
     *
     * @return an int value that combines p|r
     */
    static int combine(int p, int r)
        {
        return (p << 8) | r;
        }

    static final int _00 = ('0' << 8) | '0';
    static final int _01 = ('0' << 8) | '1';
    static final int _0N = ('0' << 8) | 'N';
    static final int _10 = ('1' << 8) | '0';
    static final int _11 = ('1' << 8) | '1';
    static final int _1N = ('1' << 8) | 'N';
    static final int _N0 = ('N' << 8) | '0';
    static final int _N1 = ('N' << 8) | '1';
    static final int _NN = ('N' << 8) | 'N';


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       expr;
    protected boolean          async;
    protected List<Expression> args;
    protected long             lEndPos;

    private transient boolean         m_fBindTarget;     // do we require a target
    private transient boolean         m_fBindParams;     // do we need to bind any parameters
    private transient boolean         m_fCall;           // do we need to call/invoke
    private transient boolean         m_fTupleArg;       // indicates that arguments come from a tuple
                                                         // (currently not supported)
    private transient boolean         m_fNamedArgs;      // are there named arguments
    private transient TargetInfo      m_targetInfo;      // for left==null with prop or method name
    private transient Argument        m_argMethod;
    private transient MethodStructure m_method;          // if m_argMethod is a MethodConstant,
                                                         // this holds the corresponding structure
    private transient TypeConstant    m_typeTarget;      // if m_argMethod is a PropertyConstant,
                                                         // referring to a function and the target
                                                         // is not "this context", it holds the
                                                         // target type;
                                                         // if m_argMethod is a MethodConstant for
                                                         // a function, then it holds an explicitly
                                                         // specified target type to be used by
                                                         // formal type parameters resolution
    private transient boolean         m_fCondResult;     // indicates that the invocation expression
                                                         // produces a conditional result
    private transient boolean         m_fBjarne;         // indicates that the invocation expression
                                                         // was Bjarne-transformed from x.f() to X.f(x)
    private transient boolean         m_fPack;           // indicates that invocation return(s) should
                                                         // be "packed" into a Tuple
    private transient FormalConstant  m_idFormal;        // if not null, indicates that the invocation
                                                         // expression applies to a function on a formal
                                                         // type (e.g. Value.hashCode(value))
    private transient Argument[]      m_aargTypeParams;  // "hidden" type parameters
    private transient MethodConstant  m_idConvert;       // conversion method
    private transient boolean         m_fAutoFuture;     // implicit FutureVar

    /**
     * Cached ExprAST nodes for the target and the invocation.
     */
    private transient ExprAST m_astTarget;
    private transient ExprAST m_astInvoke;

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }