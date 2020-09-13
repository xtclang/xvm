package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.MultiMethodConstant;
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

    public InvocationExpression(Expression expr, List<Expression> args, long lEndPos)
        {
        this.expr    = expr;
        this.args    = args;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr instanceof NameExpression
                && ((NameExpression) expr).getName().equals("versionMatches")
                && args.size() == 1
                && args.get(0) instanceof LiteralExpression
                && ((LiteralExpression) args.get(0)).getLiteral().getId() == Id.LIT_VERSION
                && ((NameExpression) expr).getLeftExpression() != null
                && ((NameExpression) expr).isOnlyNames()
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
        if (expr instanceof NameExpression)
            {
            // use the line that contains the method name (etc...) as the current line;
            // this is dramatically better for fluent style coding convention
            code.updateLineNumber(Source.calculateLine(
                    ((NameExpression) expr).getNameToken().getStartPosition()));
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
        return resolveReturnTypes(ctx, null, ErrorListener.BLACKHOLE);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        if (atypeRequired == null || atypeRequired.length == 0)
            {
            return TypeFit.Fit;
            }

        TypeConstant[] atype = resolveReturnTypes(ctx, atypeRequired,
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
                                                ErrorListener errs)
        {
        ConstantPool pool = pool();

        if (expr instanceof NameExpression)
            {
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            TypeConstant   typeLeft = null;
            if (exprLeft != null)
                {
                typeLeft = exprLeft.getImplicitType(ctx);
                if (typeLeft == null)
                    {
                    // the fact that getImplicitType() returned null may mean that the "left" name
                    // is not resolvable; try to produce a proper error message in that case
                    exprLeft.testFit(ctx, pool.typeObject(), errs);
                    return TypeConstant.NO_TYPES;
                    }
                }

            // the return types are a combination of required and redundant types
            TypeConstant[]       atypeReturn   = atypeRequired;
            List<TypeExpression> listRedundant = exprName.params;

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
            if (argMethod instanceof MethodConstant)
                {
                MethodConstant      idMethod    = (MethodConstant) argMethod;
                MethodStructure     method      = m_method;
                int                 cTypeParams = method.getTypeParamCount();
                GenericTypeResolver resolver    = null;

                if (cTypeParams > 0)
                    {
                    // resolve the type parameters against all the arg types we know by now
                    resolver = makeTypeParameterResolver(ctx, method,
                            m_fCall  || atypeReturn == null
                                ? atypeReturn
                                : pool.extractFunctionReturns(atypeReturn[0]));
                    }

                if (m_fCall)
                    {
                    if (method.isFunction() || method.isConstructor())
                        {
                        return resolveTypes(resolver, idMethod.getSignature().getRawReturns());
                        }
                    if (typeLeft == null)
                        {
                        typeLeft = m_targetinfo == null
                                ? ctx.getVar("this").getType() // "this" could be narrowed
                                : m_targetinfo.getTargetType();
                        }
                    SignatureConstant sigMethod = idMethod.getSignature();
                    return resolveTypes(resolver,
                            sigMethod.resolveAutoNarrowing(pool, typeLeft).getRawReturns());
                    }

                TypeConstant typeFn = m_fBindTarget
                        ? idMethod.getType()
                        : idMethod.getValueType(typeLeft);

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
            if (argMethod instanceof PropertyConstant)
                {
                TypeInfo     infoLeft = getTypeInfo(ctx, typeLeft, errs);
                PropertyInfo infoProp = infoLeft.findProperty((PropertyConstant) argMethod);

                typeArg = infoProp == null ? pool.typeObject() : infoProp.getType();
                }
            else
                {
                assert argMethod instanceof Register;
                typeArg = argMethod.getType().resolveTypedefs();
                }

            return typeArg.isA(pool.typeFunction())
                    ? calculateReturnType(typeArg)
                    : TypeConstant.NO_TYPES;
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
            return TypeConstant.NO_TYPES;
            }
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
            atypeReturn = atypeRequired == null ? TypeConstant.NO_TYPES : atypeRequired;
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
                fCond       = true;
                atypeReturn = atypeRequired.clone();
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
                if (typeParam == null || !typeParam.isA(pool().typeType()))
                    {
                    return null;
                    }
                atypeReturn[i] = typeParam.getParamType(0);
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
        // validated as possible, but if some of the expressions didn't validate, we can't
        // predictably find the desired method or function (e.g. without a left expression
        // providing validated type information)
        boolean      fValid = true;
        boolean      fCall  = !isSuppressCall();
        ConstantPool pool   = pool();

        // when we have a name expression on our immediate left, we do NOT (!!!) validate it,
        // because the name resolution is the responsibility of this InvocationExpression, and
        // the NameExpression itself will error on resolving a method/function name
        Validate:
        if (expr instanceof NameExpression)
            {
            // if the name expression has an expression on _its_ left, then we are now responsible
            // for validating that "left left" expression
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            TypeConstant   typeLeft = null;
            if (exprLeft != null)
                {
                Expression exprNew = exprLeft.validate(ctx, null, errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
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
                        fValid = false;
                        }
                    }
                }

            // the return types are a combination of required and redundant types
            TypeConstant[]       atypeReturn   = atypeRequired;
            List<TypeExpression> listRedundant = exprName.params;
            if (listRedundant != null)
                {
                atypeReturn = applyRedundantTypes(ctx, atypeRequired, listRedundant, true, errs);
                fValid      = atypeReturn != null;
                }

            if (!fValid)
                {
                break Validate;
                }

            // resolving the name will yield a method, a function, or something else that needs
            // to yield a function, such as a property or variable that holds a function or
            // something that can be converted to a function
            Argument argMethod = resolveName(ctx, true, typeLeft, atypeReturn, errs);
            if (argMethod == null)
                {
                break Validate;
                }

            if (m_fNamedArgs)
                {
                Map<String, Expression> mapNamedExpr = extractNamedArgs(args, errs);
                if (mapNamedExpr == null)
                    {
                    break Validate;
                    }

                assert argMethod instanceof MethodConstant;
                args = rearrangeNamedArgs(m_method, args, mapNamedExpr, errs);
                if (args == null)
                    {
                    // invalid names encountered
                    break Validate;
                    }
                }

            // when the expression is a name, and it is NOT a ".name", then we have to
            // record the dependency on the name, whether it's a variable (or an instance
            // property on the implicit "this") that contains a function reference, or (most
            // commonly) an implicit "this" for a method call
            if (exprLeft == null)
                {
                if (argMethod instanceof Register)
                    {
                    ctx.markVarRead(exprName.getNameToken(), errs);
                    }
                else if (argMethod instanceof MethodConstant ||
                         argMethod instanceof PropertyConstant)
                    {
                    Component component = ((IdentityConstant) argMethod).getComponent();
                    boolean   fStatic;
                    if (component == null)
                        {
                        TypeConstant type = m_targetinfo.getTargetType();
                        TypeInfo     info = getTypeInfo(ctx, type, errs);
                        fStatic = argMethod instanceof MethodConstant
                                ? info.getMethodById((MethodConstant) argMethod).isFunction()
                                : info.findProperty((PropertyConstant) argMethod).isConstant();
                        }
                    else
                        {
                        fStatic = component.isStatic();
                        }

                    if (!fStatic)
                        {
                        // there is a read of the implicit "this" variable TODO use TargetInfo to figure out how many "this" steps there are
                        Token tokName = exprName.getNameToken();
                        long  lPos    = tokName.getStartPosition();
                        Token tokThis = new Token(lPos, lPos, Id.THIS);
                        ctx.markVarRead(tokThis, errs);
                        }
                    }
                }
            else if (m_fBjarne)
                {
                args.add(0, exprLeft);
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

                TypeConstant[] atypeResult;
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
                return finishValidations(ctx, atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                }

            // handle method or function
            if (argMethod instanceof MethodConstant)
                {
                MethodConstant  idMethod    = (MethodConstant) argMethod;
                MethodStructure method      = m_method;
                boolean         fCondReturn = method.isConditionalReturn();
                TypeConstant[]  atypeParams = idMethod.getRawParams();
                int             cTypeParams = method.getTypeParamCount();
                int             cParams     = method.getVisibleParamCount();
                int             cArgs       = args.size();

                if (cTypeParams > 0)
                    {
                    // purge the type parameters and resolve the method signature
                    // against all the types we know by now
                    if (cParams > 0)
                        {
                        TypeConstant[] atype = new TypeConstant[cParams];

                        System.arraycopy(atypeParams, cTypeParams, atype, 0, cParams);

                        GenericTypeResolver resolver = makeTypeParameterResolver(ctx, method,
                                fCall || atypeReturn == null
                                    ? atypeReturn
                                    : pool.extractFunctionReturns(atypeReturn[0]));
                        atypeParams = resolveTypes(resolver, atype);
                        }
                    else
                        {
                        atypeParams = TypeConstant.NO_TYPES;
                        }
                    }

                // test the "regular fit" first and Tuple afterwards
                TypeConstant typeTuple = null;
                if (!testExpressions(ctx, args, atypeParams).isFit())
                    {
                    // otherwise, check the tuple based invoke (see AstNode.findMethod)
                    if (cArgs == 1)
                        {
                        typeTuple = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeParams);
                        if (!args.get(0).testFit(ctx, typeTuple, null).isFit())
                            {
                            // the regular "validateExpressions" call will report an error
                            typeTuple = null;
                            }
                        }
                    }

                TypeConstant[] atypeArgs;
                if (typeTuple == null)
                    {
                    atypeArgs = validateExpressions(ctx, args, atypeParams, errs);
                    }
                else
                    {
                    atypeArgs = validateExpressionsFromTuple(ctx, args, typeTuple, errs);
                    m_fTupleArg = true;
                    }

                if (typeLeft == null && !m_method.isFunction())
                    {
                    typeLeft = m_targetinfo == null
                            ? ctx.getVar("this").getType() // "this" could be narrowed
                            : m_targetinfo.getTargetType();
                    }

                if (atypeArgs != null)
                    {
                    Map<String, TypeConstant> mapTypeParams = Collections.EMPTY_MAP;
                    if (cTypeParams > 0)
                        {
                        // re-resolve against the validated types
                        mapTypeParams = method.resolveTypeParameters(atypeArgs,
                                fCall || atypeReturn == null
                                    ? atypeReturn
                                    : pool.extractFunctionReturns(atypeReturn[0]),
                                false);
                        if (mapTypeParams.size() < cTypeParams)
                            {
                            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                                    method.collectUnresolvedTypeParameters(mapTypeParams.keySet()));
                            break Validate;
                            }

                        Argument[] aargTypeParam = new Argument[mapTypeParams.size()];
                        int ix = 0;
                        for (TypeConstant typeArg : mapTypeParams.values())
                            {
                            if (typeArg.containsUnresolved())
                                {
                                log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                                        method.getParam(ix).getName());
                                break Validate;
                                }

                            TypeConstant typeParam = idMethod.getRawParams()[ix].getParamType(0);

                            // there's a possibility that type parameter constraints refer to
                            // previous type parameters, for example:
                            //   <T1 extends Base, T2 extends T1> foo(T1 v1, T2 v2) {...}
                            if (typeParam.containsTypeParameter(true))
                                {
                                typeParam = typeParam.resolveGenerics(pool, mapTypeParams::get);
                                }

                            if (!typeArg.isA(typeParam))
                                {
                                log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                        typeParam.getValueString(),
                                        typeArg.getValueString());
                                break Validate;
                                }
                            aargTypeParam[ix++] = typeArg.getType();
                            }
                        m_aargTypeParams = aargTypeParam;
                        }

                    TypeConstant[] atypeResult;
                    if (fCall)
                        {
                        SignatureConstant sigMethod = idMethod.getSignature();
                        if (!method.isFunction() && !method.isConstructor())
                            {
                            sigMethod = sigMethod.resolveAutoNarrowing(pool, typeLeft);
                            }
                        if (!mapTypeParams.isEmpty())
                            {
                            sigMethod = sigMethod.resolveGenericTypes(pool, mapTypeParams::get);
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
                                break Validate;
                                }
                            }
                        else if (atypeReturn != null)
                            {
                            // check for Tuple conversion for the return value; we know that the
                            // method should fit, so the only thing to figure out is whether
                            // "packing" to a Tuple is necessary
                            if (calculateReturnFit(typeLeft, sigMethod, fCall,
                                    atypeReturn, ErrorListener.BLACKHOLE).isPacking())
                                {
                                TypeConstant typePacked = pool.ensureParameterizedTypeConstant(
                                        pool.typeTuple(), atypeResult);
                                atypeResult = new TypeConstant[]{typePacked};
                                m_fPack     = true;
                                }
                            }
                        }
                    else
                        {
                        if (fCondReturn)
                            {
                            log(errs, Severity.ERROR, Compiler.CONDITIONAL_RETURN_NOT_ALLOWED,
                                method.getIdentityConstant().getValueString());
                            break Validate;
                            }

                        TypeConstant typeFn = m_fBindTarget
                                ? idMethod.getType()
                                : idMethod.getValueType(typeLeft);

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
                            typeFn = typeFn.resolveGenerics(pool, mapTypeParams::get);
                            }

                        atypeResult = new TypeConstant[] {typeFn};
                        }

                    return finishValidations(ctx, atypeRequired, atypeResult, TypeFit.Fit, null, errs);
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
                if (argMethod instanceof PropertyConstant)
                    {
                    if (m_typeTarget != null)
                        {
                        typeLeft = m_typeTarget;
                        }
                    TypeInfo     infoLeft = getTypeInfo(ctx, typeLeft, errs);
                    PropertyInfo infoProp = infoLeft.findProperty((PropertyConstant) argMethod);

                    typeFn = infoProp == null ? pool.typeObject() : infoProp.getType();
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
                                aargTypeParam[i] = new Register(pool.typeType(), i);
                                }
                            m_aargTypeParams = aargTypeParam;
                            }
                        }
                    }

                if (!typeFn.isA(pool.typeFunction()))
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            "Function", typeFn.getValueString());
                    break Validate;
                    }

                if (exprName.isSuppressDeref())
                    {
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
                        break Validate;
                        }

                    expr   = exprNew;
                    typeFn = exprNew.getType();
                    }

                TypeConstant[] atypeResult = validateFunction(ctx, typeFn,
                                                cTypeParams, cDefaults, atypeRequired, errs);
                if (atypeResult != null)
                    {
                    return finishValidations(ctx, atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                    }
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
                if (typeFn != null)
                    {
                    TypeConstant[] atypeResult = validateFunction(ctx, typeFn, 0, 0, atypeRequired, errs);
                    if (atypeResult != null)
                        {
                        return finishValidations(ctx, atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                        }
                    }
                }
            }

        return null;
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
                (nodeChild == expr || args.contains(nodeChild));
        }

    @Override
    public boolean isConditionalResult()
        {
        return m_fCondResult;
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        int cLVals = aLVal.length;
        int cRVals = getValueCount();

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
                aargResult[i] = generateBlackHole(null);
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

        Argument[] aargTypeParams = m_aargTypeParams;
        int        cTypeParams    = aargTypeParams == null ? 0 : aargTypeParams.length;
        boolean    fTargetOnStack = cArgs == 0 || args.stream().allMatch(Expression::isConstant);

        if (expr instanceof NameExpression)
            {
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            if (m_argMethod instanceof MethodConstant)
                {
                MethodConstant idMethod = (MethodConstant) m_argMethod;

                if (m_method.isFunction() || m_method.isConstructor())
                    {
                    // use the function identity as the argument & drop through to the function handling
                    assert !m_fBindTarget && (exprLeft == null || !exprLeft.hasSideEffects() ||
                                              m_fBjarne || m_idFormal != null);
                    if (m_idFormal == null)
                        {
                        argFn      = idMethod;
                        fConstruct = m_method.isConstructor();
                        }
                    else
                        {
                        // create a synthetic method constant for the formal type (for a funky
                        // interface type)
                        argFn      = pool().ensureMethodConstant(m_idFormal, idMethod.getSignature());
                        fConstruct = false;
                        }
                    }
                else
                    {
                    // idMethod is a MethodConstant for a method (including "finally")
                    if (m_fBindTarget)
                        {
                        // the method needs a target (its "this")
                        Argument argTarget;
                        if (exprLeft == null)
                            {
                            MethodStructure method = code.getMethodStructure();
                            if (m_targetinfo == null)
                                {
                                argTarget = ctx.generateThisRegister(code, method.isConstructor(), errs);
                                }
                            else
                                {
                                TypeConstant typeTarget = m_targetinfo.getTargetType();
                                int          cStepsOut  = m_targetinfo.getStepsOut();

                                if (cStepsOut > 0)
                                    {
                                    argTarget = createRegister(typeTarget, fTargetOnStack);
                                    code.add(new MoveThis(m_targetinfo.getStepsOut(), argTarget));
                                    }
                                else
                                    {
                                    argTarget = new Register(typeTarget,
                                        method.isConstructor() ? Op.A_STRUCT : Op.A_TARGET);
                                    }
                                }
                            }
                        else
                            {
                            argTarget = exprLeft.generateArgument(ctx, code, fLocalPropOk, fTargetOnStack, errs);
                            }

                        if (m_fCall)
                            {
                            updateLineNumber(code);

                            // it's a method, and we need to generate the necessary code that calls it;
                            // generate the arguments
                            int        cAll      = idMethod.getRawParams().length;
                            int        cDefaults = cAll - cTypeParams - cArgs;
                            Argument   arg       = null;
                            Argument[] aArgs     = null;
                            char       chArgs;

                            assert cTypeParams + cArgs + cDefaults == cAll;

                            if (cAll == 0)
                                {
                                chArgs = '0';
                                aArgs  = NO_RVALUES;
                                }
                            else if (cAll == 1)
                                {
                                chArgs = '1';
                                if (cArgs == 1)
                                    {
                                    arg = args.get(0).generateArgument(ctx, code, true, true, errs);
                                    }
                                else if (cTypeParams == 1)
                                    {
                                    arg = aargTypeParams[0];
                                    }
                                else if (cDefaults == 1)
                                    {
                                    arg = Register.DEFAULT;
                                    }
                                }
                            else
                                {
                                chArgs = 'N';
                                aArgs  = new Argument[cAll];

                                if (cTypeParams > 0)
                                    {
                                    System.arraycopy(aargTypeParams, 0, aArgs, 0, cTypeParams);
                                    }

                                for (int i = 0, of = cTypeParams; i < cArgs; ++i)
                                    {
                                    aArgs[of + i] = args.get(i).generateArgument(ctx, code, true, true, errs);
                                    }

                                for (int i = 0, of = cTypeParams + cArgs; i < cDefaults; ++i)
                                    {
                                    aArgs[of + i] = Register.DEFAULT;
                                    }
                                }

                            char chRets = '0';
                            if (cRets == 1)
                                {
                                chRets = '1';
                                }
                            else if (cRets > 1)
                                {
                                chRets = 'N';
                                }

                            switch (combine(chArgs, chRets))
                                {
                                case _00:
                                    code.add(new Invoke_00(argTarget, idMethod));
                                    break;

                                case _10:
                                    if (m_fTupleArg)
                                        {
                                        code.add(new Invoke_T0(argTarget, idMethod, arg));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_10(argTarget, idMethod, arg));
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
                                            code.add(new Invoke_TT(argTarget, idMethod, arg, aargResult[0]));
                                            }
                                        else
                                            {
                                            code.add(new Invoke_1T(argTarget, idMethod, arg, aargResult[0]));
                                            }
                                        }
                                    else
                                        {
                                        if (m_fTupleArg)
                                            {
                                            code.add(new Invoke_T1(argTarget, idMethod, arg, aargResult[0]));
                                            }
                                        else
                                            {
                                            code.add(new Invoke_11(argTarget, idMethod, arg, aargResult[0]));
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
                                        code.add(new Invoke_TN(argTarget, idMethod, arg, aargResult));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_1N(argTarget, idMethod, arg, aargResult));
                                        }
                                    break;

                                case _NN:
                                    code.add(new Invoke_NN(argTarget, idMethod, aArgs, aargResult));
                                    break;

                                default:
                                    throw new UnsupportedOperationException("invocation: " + combine(chArgs, chRets));
                                }

                            return;
                            }
                        else // _NOT_ m_fCall
                            {
                            // the method gets bound to become a function; do this and drop through
                            // to the function handling
                            argFn = new Register(idMethod.getSignature().asFunctionType());
                            code.add(new MBind(argTarget, idMethod, argFn));
                            }
                        }
                    else // _NOT_ m_fBindTarget
                        {
                        // the method instance itself is the result, e.g. "Method m = Frog.&jump();"
                        assert m_idConvert == null && !m_fBindParams && !m_fCall;
                        if (cLVals > 0)
                            {
                            aLVal[0].assign(m_argMethod, code, errs);
                            }
                        return;
                        }
                    }
                }
            else // it is a NameExpression but _NOT_ a MethodConstant
                {
                assert !m_fBindTarget;

                if (m_argMethod instanceof Register)
                    {
                    argFn = m_argMethod;
                    }
                else
                    {
                    // evaluate to find the argument (e.g. "var.prop", where prop holds a function)
                    argFn = exprName.generateArgument(ctx, code, false, fTargetOnStack, errs);
                    }
                }
            }
        else // _NOT_ an InvocationExpression of a NameExpression (i.e. it's just a function)
            {
            // obtain the function that will be bound and/or called
            assert !m_fBindTarget;
            argFn = expr.generateArgument(ctx, code, false, fTargetOnStack, errs);
            }

        // bind arguments and/or generate a call to the function specified by argFn; first, convert
        // it to the desired function if necessary
        TypeConstant typeFn = argFn.getType().resolveTypedefs();
        if (m_idConvert != null)
            {
            // argFn isn't a function; convert whatever-it-is into the desired function
            typeFn = m_idConvert.getRawReturns()[0];
            Register regFn = createRegister(typeFn, true);
            code.add(new Invoke_01(argFn, m_idConvert, regFn));
            argFn = regFn;
            }

        TypeConstant[] atypeParams = pool().extractFunctionParams(typeFn);
        int            cAll        = atypeParams == null ? 0 : atypeParams.length;

        if (m_fCall)
            {
            updateLineNumber(code);

            int        cDefaults = cAll - cTypeParams - cArgs;
            Argument   arg       = null;
            Argument[] aArgs     = null;
            char       chArgs;

            assert !m_fBindParams || cArgs > 0;

            if (cAll == 0)
                {
                chArgs = '0';
                aArgs  = NO_RVALUES;
                }
            else if (cAll == 1)
                {
                chArgs = '1';
                if (cArgs == 1)
                    {
                    arg = args.get(0).generateArgument(ctx, code, true, true, errs);
                    }
                else if (cTypeParams == 1)
                    {
                    arg = aargTypeParams[0];
                    }
                else if (cDefaults == 1)
                    {
                    arg = Register.DEFAULT;
                    }
                }
            else
                {
                chArgs = 'N';
                aArgs  = new Argument[cAll];

                if (cTypeParams > 0)
                    {
                    System.arraycopy(aargTypeParams, 0, aArgs, 0, cTypeParams);
                    }

                for (int i = 0, of = cTypeParams; i < cArgs; ++i)
                    {
                    aArgs[of + i] = args.get(i).generateArgument(ctx, code, true, true, errs);
                    }

                for (int i = 0, of = cTypeParams + cArgs; i < cDefaults; ++i)
                    {
                    aArgs[of + i] = Register.DEFAULT;
                    }
                }

            if (fConstruct)
                {
                MethodConstant idConstruct = (MethodConstant) argFn;
                switch (chArgs)
                    {
                    case '0':
                        code.add(new Construct_0(idConstruct));
                        break;

                    case '1':
                        code.add(new Construct_1(idConstruct, arg));
                        break;

                    case 'N':
                        code.add(new Construct_N(idConstruct, aArgs));
                        break;

                    case 'T':
                    default:
                        throw new UnsupportedOperationException("constructor by Tuple");
                    }
                return;
                }

            // generate registers for the return values
            char chRets = '0';
            if (cRets == 1)
                {
                chRets = '1';
                }
            else if (cRets > 1)
                {
                chRets = 'N';
                }

            switch (combine(chArgs, chRets))
                {
                case _00:
                    code.add(new Call_00(argFn));
                    break;

                case _10:
                    if (m_fTupleArg)
                        {
                        code.add(new Call_T0(argFn, arg));
                        }
                    else
                        {
                        code.add(new Call_10(argFn, arg));
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
                            code.add(new Call_TT(argFn, arg, aargResult[0]));
                            }
                        else
                            {
                            code.add(new Call_1T(argFn, arg, aargResult[0]));
                            }
                        }
                    else
                        {
                        if (m_fTupleArg)
                            {
                            code.add(new Call_T1(argFn, arg, aargResult[0]));
                            }
                        else
                            {
                            code.add(new Call_11(argFn, arg, aargResult[0]));
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
                        code.add(new Call_TN(argFn, arg, aargResult));
                        }
                    else
                        {
                        code.add(new Call_1N(argFn, arg, aargResult));
                        }
                    break;

                case _NN:
                    code.add(new Call_NN(argFn, aArgs, aargResult));
                    break;

                default:
                    throw new UnsupportedOperationException("invocation " + combine(chArgs, chRets));
                }
            return;
            }

        // see if we need to bind (or partially bind) the function
        int[]      aiArg = null;
        Argument[] aArg  = null;

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
            for (int i = 0; i < cTypeParams; ++i)
                {
                aiArg[i] = i;
                aArg [i] = aargTypeParams[i];
                }

            for (int i = 0, iBind = cTypeParams; i < cArgs; ++i)
                {
                Expression exprArg = args.get(i);
                if (!exprArg.isNonBinding())
                    {
                    aiArg[iBind] = cTypeParams + i;
                    aArg [iBind] = exprArg.generateArgument(ctx, code, true, true, errs);
                    iBind++;
                    }
                }
            }
        else if (argFn instanceof Register && ((Register) argFn).isSuper())
            {
            // non-bound super(...) function still needs to bind a target
            aiArg = new int[0];
            aArg  = NO_RVALUES;
            }

        if (cLVals > 0)
            {
            Assignable lval = aLVal[0];
            if (aiArg == null)
                {
                lval.assign(argFn, code, errs);
                }
            else if (lval.isLocalArgument())
                {
                code.add(new FBind(argFn, aiArg, aArg, lval.getLocalArgument()));
                }
            else
                {
                Register regFn = new Register(getType());
                code.add(new FBind(argFn, aiArg, aArg, regFn));
                lval.assign(regFn, code, errs);
                }
            }
        }


    // ----- method resolution helpers -------------------------------------------------------------

    /**
     * @return true iff this expression does not actually result in an invocation, but instead
     *         resolves to a reference to a method or a function as its result
     */
    protected boolean isSuppressCall()
        {
        return (expr instanceof NameExpression && ((NameExpression) expr).isSuppressDeref())
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
     * @return true iff the parameter is named
     */
    protected boolean isParamNamed(Expression expr)
        {
        return expr instanceof LabeledExpression;
        }

    /**
     * @return the name of the parameter, or null if the parameter is not named
     */
    protected String getParamName(Expression expr)
        {
        return isParamNamed(expr)
                ? ((LabeledExpression) expr).getName()
                : null;
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
     * @return the method constant, or null if it was not determinable,
     *         in which case an error has been reported
     */
    protected Argument resolveName(
            Context        ctx,
            boolean        fForce,
            TypeConstant   typeLeft,
            TypeConstant[] atypeReturn,
            ErrorListener  errs)
        {
        if (!fForce && m_argMethod != null)
            {
            return m_argMethod;
            }

        boolean fNoFBind   = !isAnyArgBound();
        boolean fNoCall    = isSuppressCall();
        boolean fNamedArgs = containsNamedArgs(args);

        m_targetinfo  = null;
        m_argMethod   = null;
        m_idConvert   = null;
        m_fBindTarget = false;
        m_fBindParams = !fNoFBind;
        m_fCall       = !fNoCall;
        m_fNamedArgs  = fNamedArgs;

        ConstantPool pool = pool();
        if (atypeReturn != null && fNoCall)
            {
            // "no call" means that we are expected to produce a function, but the code below
            // treats atypeReturn as function return types
            if (atypeReturn.length != 1)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, atypeReturn.length);
                return null;
                }

            TypeConstant typeFn = atypeReturn[0];

            if (typeFn.isA(pool.typeFunction()))
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
        boolean        fConstruct = sName.equals("construct");
        Expression     exprLeft   = exprName.left;
        if (exprLeft == null)
            {
            Argument arg = ctx.resolveName(tokName, ErrorListener.BLACKHOLE);

            // try to use the type info (e.g. when the target is of a relational type)
            if (arg == null && ctx.isMethod())
                {
                typeLeft = ctx.getVar("this").getType();

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
                    m_targetinfo  = new TargetInfo(sName, method, typeLeft, 0);
                    return arg;
                    }
                }

            if (arg instanceof Register)
                {
                Register reg         = (Register) arg;
                int      cTypeParams = 0;
                int      cDefaults   = 0;
                if (reg.isPredefined())
                    {
                    // report specific error messages for incorrect "this" or "super" use
                    switch (reg.getIndex())
                        {
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
                            if (!ctx.isMethod())
                                {
                                exprName.log(errs, Severity.ERROR, Compiler.NO_SUPER);
                                return null;
                                }
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

            if (arg instanceof TargetInfo)
                {
                TargetInfo       target     = (TargetInfo) arg;
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
                    ErrorListener    errsTemp   = errs.branch();
                    IdentityConstant idCallable = findMethod(ctx, typeTarget, info, sName,
                            args, kind, !fNoCall, id.isNested(), atypeReturn, errsTemp);
                    if (idCallable == null)
                        {
                        // check to see if we would have found something had we included methods in
                        // the search
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
                        m_targetinfo  = target; // (only used for non-constants)
                        m_argMethod   = idCallable;
                        m_method      = getMethod(info, idCallable);
                        m_fBindTarget = m_method != null && !m_method.isFunction();

                        errsTemp.merge();
                        return idCallable;
                        }
                    }
                else if (id instanceof PropertyConstant)
                    {
                    PropertyInfo prop = info.findProperty((PropertyConstant) id);
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
                        m_targetinfo = target; // (only used for non-constants)
                        m_argMethod  = id;
                        return id;
                        }
                    else
                        {
                        // the property requires a target, but there is no "left." before the prop
                        // name, and there is no "this." (explicit or implicit) because there is no
                        // this
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

            // TODO GG the same logic below for imports probably also need to be in NameExpression
            if (arg instanceof MultiMethodConstant)
                {
                // an import name can specify a MultiMethodConstant;
                // we only allow functions (not methods or constructors)
                IdentityConstant idClz      = ((MultiMethodConstant) arg).getParentConstant();
                TypeConstant     typeTarget = idClz.getType();
                TypeInfo         info       = getTypeInfo(ctx, typeTarget, errs);
                IdentityConstant idCallable = findMethod(ctx, typeTarget, info, sName,
                        args, MethodKind.Any, !fNoCall, false, atypeReturn, errs);
                if (idCallable != null)
                    {
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
                }
            else if (arg instanceof PropertyConstant)
                {
                // an import name can specify a static PropertyConstant
                return testStaticProperty(ctx, (PropertyConstant) arg, atypeReturn, errs);
                }

            if (sName.equals("construct"))
                {
                log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, ctx.getThisType().getValueString());
                }
            else
                {
                TypeConstant typeTarget = ctx.getThisType();
                if (ctx.isConstructor())
                    {
                    typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.STRUCT);
                    }
                log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sName, typeTarget.getValueString());
                }
            return null;
            }
        else // there is a "left" expression for the name
            {
            if (tokName.isSpecial())
                {
                tokName.log(errs, getSource(), Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, tokName.getValueText());
                return null;
                }

            // the left expression provides the scope to search for a matching method/function;
            // if the left expression is itself a NameExpression, and it's in identity mode (i.e. a
            // possible identity), then check the identity first
            if (exprLeft instanceof NameExpression)
                {
                NameExpression nameLeft = (NameExpression) exprLeft;
                if (nameLeft.getName().equals("super"))
                    {
                    log(errs, Severity.ERROR, Compiler.INVALID_SUPER_REFERENCE);
                    return null;
                    }

                if (nameLeft.isIdentityMode(ctx, false))
                    {
                    // the left identity
                    // - methods are included because there is a left, but since it is to obtain a
                    //   method reference, there must not be any arg binding or actual invocation
                    // - functions are included because the left is identity-mode
                    IdentityConstant idLeft = nameLeft.getIdentity(ctx);
                    Access           access = fConstruct ? Access.PROTECTED : Access.PUBLIC;
                    // TODO: if left is a super class or other contribution, use PROTECTED access as well
                    if (ctx.getThisClass().getIdentityConstant().isNestMateOf(idLeft))
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
                            // this can only be a "construct X(..)" call coming from "this:struct"
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
                            // this is either a function call (e.g. Duration.ofSeconds(1)) or
                            // a call on the Class itself (Point.instantiate(struct)), in which
                            // case the first "findCallable" will fail and therefore typeLeft must
                            // not be changed
                            infoLeft = idLeft.ensureTypeInfo(access, errs);
                            }
                        }

                    MethodKind kind = fConstruct          ? MethodKind.Constructor :
                                      fNoFBind && fNoCall ? MethodKind.Any :
                                                            MethodKind.Function;

                    ErrorListener errsTemp = errs.branch();
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
                    if (arg instanceof MethodConstant)
                        {
                        errsTemp.merge();

                        MethodConstant idMethod   = (MethodConstant) arg;
                        MethodInfo     infoMethod = infoLeft.getMethodById(idMethod);
                        assert infoMethod != null;

                        if (infoMethod.isAbstractFunction())
                            {
                            log(errs, Severity.ERROR, Compiler.ILLEGAL_FUNKY_CALL,
                                    idMethod.getValueString());
                            return null;
                            }

                        m_argMethod = idMethod;
                        m_method    = infoMethod.getTopmostMethodStructure(infoLeft);
                        return idMethod;
                        }
                    else if (arg instanceof PropertyConstant)
                        {
                        errsTemp.merge();
                        return testStaticProperty(ctx, (PropertyConstant) arg, atypeReturn, errs);
                        }
                    }

                switch (nameLeft.getMeaning())
                    {
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
                            TypeInfo     infoType = type.ensureTypeInfo(errs);

                            ErrorListener errsTemp = errs.branch();

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

                        TypeInfo      infoLeft = typeLeft.ensureTypeInfo(errs);
                        ErrorListener errsTemp = errs.branch();

                        Argument arg = findCallable(ctx, typeLeft, infoLeft, sName, MethodKind.Function,
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

                    case Variable:
                        {
                        Register reg = (Register) nameLeft.resolveRawArgument(ctx, false, errs);
                        if (!reg.isUnknown())
                            {
                            int             iReg   = reg.getIndex();
                            MethodStructure method = ctx.getMethod();
                            if (method.isTypeParameter(iReg))
                                {
                                TypeConstant  type     = reg.getType().getParamType(0);
                                TypeInfo      infoType = typeLeft.ensureTypeInfo(errs);
                                ErrorListener errsTemp = errs.branch();

                                Argument  arg = findCallable(ctx, type, infoType, sName, MethodKind.Function,
                                    false, atypeReturn, errsTemp);
                                if (arg instanceof MethodConstant)
                                    {
                                    m_argMethod   = arg;
                                    m_method      = getMethod(infoType, arg);
                                    m_fBindTarget = false;
                                    m_idFormal    = method.getParam(iReg).
                                            asTypeParameterConstant(method.getIdentityConstant());
                                    errsTemp.merge();
                                    return arg;
                                    }
                                }
                            }
                        }
                    }
                }

            // use the type of the left expression to get the TypeInfo that must contain the
            // method/function to call
            // - methods are included because there is a left and it is NOT identity-mode
            // - functions are NOT included because the left is NOT identity-mode
            TypeInfo      infoLeft = getTypeInfo(ctx, typeLeft, errs);
            ErrorListener errsMain = errs.branch();

            Argument arg = findCallable(ctx, typeLeft, infoLeft, sName, MethodKind.Method, false,
                                atypeReturn, errsMain);
            if (arg != null)
                {
                if (arg instanceof MethodConstant)
                    {
                    m_argMethod   = arg;
                    m_method      = getMethod(infoLeft, arg);
                    m_fBindTarget = m_method != null;
                    }
                else
                    {
                    // just return the property; the rest will be handled by the caller
                    assert arg instanceof PropertyConstant;
                    }
                errsMain.merge();
                return arg;
                }

            // allow for a function on the "left type" to be called (Bjarne'd):
            //    x.f(y, z) -> X.f(x, y, z), where X is the class of x
            if (typeLeft.isSingleUnderlyingClass(true) && !isSuppressCall() && !isAnyArgUnbound())
                {
                List<Expression> listArgs = new ArrayList<>(args);
                listArgs.add(0, exprLeft);

                ErrorListener errsAlt = errs.branch();

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

            if (exprLeft instanceof NameExpression && typeLeft.isA(pool.typeFunction()))
                {
                // it appears that they try to use a variable or property, but have a function instead
                if (errsMain.hasError(Compiler.MISSING_METHOD) &&
                        infoLeft.findMethods(sName, -1, MethodKind.Any).isEmpty())
                    {
                    NameExpression   exprFn = (NameExpression) exprLeft;
                    IdentityConstant idParent;
                    if (exprFn.isIdentityMode(ctx, false))
                        {
                        idParent = exprFn.getIdentity(ctx).getNamespace();
                        }
                    else
                        {
                        NameExpression nameLeft = (NameExpression) exprLeft;
                        switch (nameLeft.getMeaning())
                            {
                            case Property:
                                idParent = ((PropertyConstant) nameLeft.
                                    resolveRawArgument(ctx, false, errs)).getNamespace();
                                break;

                            case Variable:
                                idParent = ctx.getMethod().getIdentityConstant();
                                break;

                            default:
                                idParent = null;
                            }
                        }

                    if (idParent != null)
                        {
                        log(errsMain, Severity.ERROR, Compiler.SUSPICIOUS_FUNCTION_USE,
                                exprFn.getName(), idParent.getValueString());
                        }
                    }
                }
            errsMain.merge();
            }

        return null;
        }

    /**
     * @return a method structure for the specified argument; null if not a method constant
     */
    private MethodStructure getMethod(TypeInfo infoType, Argument arg)
        {
        if (arg instanceof MethodConstant)
            {
            MethodInfo infoMethod = infoType.getMethodById((MethodConstant) arg);
            assert infoMethod != null;

            return infoMethod.getTopmostMethodStructure(infoType);
            }
        return null;
        }

    /**
     * Find a named method or function that best matches the specified requirements.
     * </p>
     * Note: we need to pass both typeParent and infoParent, since in some context sensitive cases
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
        if (cArgs < cRequired)
            {
            log(errs, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT, cRequired, cArgs);
            fValid = false;
            }

        for (int i = 0, c = Math.min(cVisible, cArgs); i < c; ++i)
            {
            TypeConstant typeParam = atypeParams[cTypeParams + i];
            Expression   exprArg   = listArgs.get(i);

            ctx = ctx.enterInferring(typeParam);
            if (!exprArg.testFit(ctx, typeParam, errs).isFit())
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        typeParam.getValueString(), exprArg.getTypeString(ctx));
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

            int cFnReturns = atypeFnRet.length;
            int cReturns   = atypeReturn.length;

            if (cFnReturns < cReturns)
                {
                // missing the required return types; TODO: does it deserve a dedicated message?
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, "Function", typeFn.getValueString());
                return null;
                }

            for (int i = 0; i < cReturns; ++i)
                {
                TypeConstant typeFnRet = atypeFnRet[i];
                TypeConstant typeReq   = atypeReturn[i];
                if (!typeFnRet.isA(typeReq))
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            typeReq.getValueString(), typeFnRet.getValueString());
                    fValid = false;
                    }
                }
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

        if (cArgs < cRequired)
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
            return pool.extractFunctionReturns(typeFn);
            }

        if (m_fBindParams)
            {
            typeFn = bindFunctionParameters(typeFn);
            }

        if (atypeRequired != null && atypeRequired.length > 0)
            {
            // the isA() on a function allows a reduction of the parameter number
            // (allowing for default arguments), so we need to compensate here
            TypeConstant   typeReqFn      = atypeRequired[0];
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
            return atypeReturn == null ? TypeConstant.NO_TYPES : atypeReturn;
            }

        if (m_fBindParams)
            {
            typeFn = bindFunctionParameters(typeFn);
            }
        return new TypeConstant[] {typeFn};
        }

    /**
     * @return a type parameter resolver for a given method and array of return types
     *         or null if the type parameters could not be resolved
     */
    private GenericTypeResolver makeTypeParameterResolver(
            Context ctx, MethodStructure method, TypeConstant[] atypeReturn)
        {
        int            cArgs     = args.size();
        TypeConstant[] atypeArgs = new TypeConstant[cArgs];
        for (int i = 0; i < cArgs; i++)
            {
            atypeArgs[i] = args.get(i).getImplicitType(ctx);
            }

        Map<String, TypeConstant> mapTypeParams =
                method.resolveTypeParameters(atypeArgs, atypeReturn, true);

        return mapTypeParams.size() == method.getTypeParamCount()
                ? mapTypeParams::get
                : null;
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


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr)
          .append('(');

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
    protected List<Expression> args;
    protected long             lEndPos;

    private transient boolean         m_fBindTarget;     // do we require a target
    private transient boolean         m_fBindParams;     // do we need to bind any parameters
    private transient boolean         m_fCall;           // do we need to call/invoke
    private transient boolean         m_fTupleArg;       // indicates that arguments come from a tuple
    private transient boolean         m_fNamedArgs;      // are there named arguments
    private transient TargetInfo      m_targetinfo;      // for left==null with prop or method name
    private transient Argument        m_argMethod;
    private transient MethodStructure m_method;          // if m_argMethod is a MethodConstant,
                                                         // this holds the corresponding structure
    private transient TypeConstant    m_typeTarget;      // if m_argMethod is a PropertyConstant,
                                                         // referring to a function and the target
                                                         // is not "this context", it holds the
                                                         // target type
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }
