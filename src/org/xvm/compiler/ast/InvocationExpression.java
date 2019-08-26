package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
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
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodType;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
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
 *   Method m = List.&add(&lt;List.ElementType&gt;?);
 *   Method m = List.add(?);
 *   Method m = List.add(&lt;List.ElementType&gt;?);
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
                    return TypeConstant.NO_TYPES;
                    }

                if (isNestMate(ctx, typeLeft))
                    {
                    typeLeft = pool.ensureAccessTypeConstant(typeLeft, Access.PRIVATE);
                    }
                }

            // collect as many redundant return types as possible to help narrow down the
            // possible method/function matches and combine with required types
            TypeConstant[]       atypeReturn   = atypeRequired;
            List<TypeExpression> listRedundant = exprName.params;
            if (listRedundant != null)
                {
                int cRequired  = atypeRequired == null ? 0 : atypeRequired.length;
                int cRedundant = listRedundant.size();

                if (cRedundant > cRequired)
                    {
                    atypeReturn = new TypeConstant[cRedundant];
                    }
                else
                    {
                    atypeReturn = atypeRequired.clone(); // keep the tail as is
                    }

                for (int i = 0; i < cRedundant; ++i)
                    {
                    TypeConstant typeParam = listRedundant.get(i).getImplicitType(ctx);
                    if (typeParam == null || !typeParam.isA(pool().typeType())
                                          || typeParam.getParamsCount() != 1)
                        {
                        break;
                        }
                    atypeReturn[i] = typeParam.getParamTypesArray()[0];
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
                    return typeFn.getParamTypesArray()[F_RETS].getParamTypesArray();
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
                MethodConstant  idMethod = (MethodConstant) argMethod;
                MethodStructure method   = m_method;

                GenericTypeResolver resolver = null;
                if (method.getTypeParamCount() > 0)
                    {
                    // resolve the type parameters against all the arg types we know by now
                    resolver = makeTypeParameterResolver(ctx, method,
                            m_fCall ? atypeReturn : TypeConstant.NO_TYPES);
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
                    return resolveTypes(resolver,
                            idMethod.resolveAutoNarrowing(pool, typeLeft).getRawReturns());
                    }

                TypeConstant typeFn = m_fBindTarget
                    ? idMethod.getType()
                    : idMethod.getRefType(typeLeft);

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
                if (typeLeft == null || isNestMate(ctx, typeLeft))
                    {
                    typeLeft = pool.ensureAccessTypeConstant(ctx.getThisType(), Access.PRIVATE);
                    }

                PropertyConstant idProp   = (PropertyConstant) argMethod;
                PropertyInfo     infoProp = typeLeft.ensureTypeInfo().findProperty(idProp);

                typeArg = infoProp == null
                        ? pool.typeObject()
                        : infoProp.getType();
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
                typeFn = testFunction(ctx, typeFn, atypeRequired, errs);
                if (typeFn != null)
                    {
                    return calculateReturnType(typeFn);
                    }
                }
            return TypeConstant.NO_TYPES;
            }
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        // validate the invocation arguments, some of which may be left unbound (e.g. "?")
        boolean      fValid = true;
        ConstantPool pool   = pool();

        // when we have a name expression on our immediate left, we do NOT (!!!) validate it,
        // because the name resolution is the responsibility of this InvocationExpression, and
        // the NameExpression itself will error on resolving a method/function name
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

                if (fValid && isNestMate(ctx, typeLeft))
                    {
                    typeLeft = pool.ensureAccessTypeConstant(typeLeft, Access.PRIVATE);
                    }
                }

            // the return types are a combination of required and redundant types
            TypeConstant[] atypeReturn = atypeRequired;

            // validate the "redundant returns" expressions
            List<TypeExpression> listRedundant = exprName.params;

            ValidateRedundant:
            if (listRedundant != null)
                {
                int     cRequired  = atypeRequired == null ? 0 : atypeRequired.length;
                int     cRedundant = listRedundant.size();
                boolean fCond      = false;

                if (cRedundant >= cRequired)
                    {
                    atypeReturn = new TypeConstant[cRedundant];
                    }
                else
                    {
                    // the only case when we have fewer types than required is a conditional return
                    if (cRequired == cRedundant + 1 && atypeRequired[0].isA(pool.typeBoolean()))
                        {
                        fCond       = true;
                        atypeReturn = atypeRequired.clone();
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_MISMATCH);
                        fValid = false;
                        break ValidateRedundant;
                        }
                    }

                for (int i = 0; i < cRedundant; ++i)
                    {
                    int          ixType      = fCond ? i + 1 : i;
                    TypeConstant typeTypeReq = i < cRequired
                            ? pool.ensureParameterizedTypeConstant(pool.typeType(), atypeRequired[ixType])
                            : pool.typeType();

                    TypeExpression exprOld = listRedundant.get(i);
                    TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, typeTypeReq, errs);
                    if (exprNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        if (exprNew != exprOld)
                            {
                            // WARNING: mutating contents of the NameExpression, which has been
                            //          _subsumed_ by this InvocationExpression
                            listRedundant.set(i, exprNew);
                            }
                        atypeReturn[ixType] = exprNew.getType().getParamTypesArray()[0];
                        }
                    }
                }

            // the reason for tracking success (fValid) is that we want to get as many things
            // validated as possible, but if some of the expressions didn't validate, we can't
            // predictably find the desired method or function (e.g. without a left expression
            // providing validated type information)
            if (fValid)
                {
                // resolving the name will yield a method, a function, or something else that needs
                // to yield a function, such as a property or variable that holds a function or
                // something that can be converted to a function
                Argument argMethod = resolveName(ctx, true, typeLeft, atypeReturn, errs);
                if (argMethod != null)
                    {
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
                                TypeInfo info = m_targetinfo.getTargetType().ensureTypeInfo(errs);
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
                        if (m_fCall)
                            {
                            atypeResult = typeFn.getParamTypesArray()[F_RETS].getParamTypesArray();
                            }
                        else
                            {
                            if (m_fBindParams)
                                {
                                typeFn = bindFunctionParameters(typeFn);
                                }
                            atypeResult = new TypeConstant[]{typeFn};
                            }
                        return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                        }

                    // handle method or function
                    ValidateMethod:
                    if (argMethod instanceof MethodConstant)
                        {
                        MethodConstant  idMethod = (MethodConstant) argMethod;
                        MethodStructure method   = m_method;

                        if (typeLeft != null && !typeLeft.isFormalType() && typeLeft.getParamsCount() == 0 &&
                                !method.isFunction() && !method.isConstructor())
                            {
                            // prevent a naked type to be used for consuming methods
                            ClassStructure clz = (ClassStructure) idMethod.getNamespace().getComponent();
                            if (clz.getTypeParamCount() > 0)
                                {
                                for (StringConstant constName : clz.getTypeParams().keySet())
                                    {
                                    if (method.consumesFormalType(constName.getValue()))
                                        {
                                        log(errs, Severity.ERROR, Compiler.ILLEGAL_NAKED_TYPE_INVOCATION,
                                            typeLeft.getValueString(),
                                            method.getIdentityConstant().getValueString());
                                        break ValidateMethod;
                                        }
                                    }
                                }
                            }

                        TypeConstant[] atypeArgs   = idMethod.getRawParams();
                        int            cTypeParams = method.getTypeParamCount();
                        int            cArgs       = args.size();

                        if (cTypeParams > 0)
                            {
                            // purge the type parameters and resolve the method signature
                            // against all the types we know by now
                            int cParams = method.getParamCount() - cTypeParams;
                            if (cParams > 0)
                                {
                                TypeConstant[] atype   = new TypeConstant[cParams];

                                System.arraycopy(atypeArgs, cTypeParams, atype, 0, cParams);

                                GenericTypeResolver resolver = makeTypeParameterResolver(ctx, method,
                                        m_fCall ? atypeReturn : TypeConstant.NO_TYPES);
                                atypeArgs = resolveTypes(resolver, atype);
                                }
                            else
                                {
                                atypeArgs = TypeConstant.NO_TYPES;
                                }
                            }

                        // test the "regular fit" first and Tuple afterwards
                        TypeConstant typeTuple = null;
                        if (!testExpressions(ctx, args, atypeArgs).isFit())
                            {
                            // otherwise, check the tuple based invoke (see Expression.findMethod)
                            if (cArgs == 1)
                                {
                                typeTuple = pool.ensureParameterizedTypeConstant(
                                        pool.typeTuple(), atypeArgs);
                                if (!args.get(0).testFit(ctx, typeTuple, null).isFit())
                                    {
                                    // the regular "validateExpressions" call will report an error
                                    typeTuple = null;
                                    }
                                }
                            }

                        if (typeTuple == null)
                            {
                            atypeArgs = validateExpressions(ctx, args, atypeArgs, errs);
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
                                        m_fCall ? atypeReturn : TypeConstant.NO_TYPES);
                                if (mapTypeParams.size() < cTypeParams)
                                    {
                                    // TODO: need a better error
                                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                                    break ValidateMethod;
                                    }

                                Argument[] aargTypeParam = new Argument[mapTypeParams.size()];
                                int ix = 0;
                                for (TypeConstant type : mapTypeParams.values())
                                    {
                                    TypeConstant typeArgType    = type.getType();
                                    TypeConstant typeMethodType = idMethod.getRawParams()[ix];
                                    if (!typeArgType.isA(typeMethodType))
                                        {
                                        log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                                typeMethodType.getValueString(),
                                                typeArgType.getValueString());
                                        break ValidateMethod;
                                        }
                                    aargTypeParam[ix++] = typeArgType;
                                    }
                                m_aargTypeParams = aargTypeParam;
                                }

                            m_cDefaults = method.getParamCount() - cTypeParams - cArgs;

                            TypeConstant[] atypeResult;
                            if (m_fCall)
                                {
                                SignatureConstant sigRet = method.isFunction() || method.isConstructor()
                                        ? idMethod.getSignature()
                                        : idMethod.resolveAutoNarrowing(pool, typeLeft);
                                if (!mapTypeParams.isEmpty())
                                    {
                                    sigRet = sigRet.resolveGenericTypes(pool, mapTypeParams::get);
                                    }
                                atypeResult = sigRet.getRawReturns();
                                }
                            else
                                {
                                TypeConstant typeFn = m_fBindTarget
                                    ? idMethod.getType()
                                    : idMethod.getRefType(typeLeft);

                                if (!mapTypeParams.isEmpty())
                                    {
                                    typeFn = typeFn.resolveGenerics(pool, mapTypeParams::get);
                                    }

                                if (m_fBindParams)
                                    {
                                    typeFn = bindFunctionParameters(typeFn);
                                    }
                                atypeResult = new TypeConstant[] {typeFn};
                                }

                            return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                            }
                        }
                    else
                        {
                        // must be a property or a variable of type function (@Auto conversion possibility
                        // already handled above); the function has two tuple sub-types, the second of which is
                        // the "return types" of the function
                        TypeConstant typeFn;
                        if (argMethod instanceof PropertyConstant)
                            {
                            if (typeLeft == null  || isNestMate(ctx, typeLeft))
                                {
                                typeLeft = pool.ensureAccessTypeConstant(ctx.getThisType(), Access.PRIVATE);
                                }
                            PropertyConstant idProp   = (PropertyConstant) argMethod;
                            PropertyInfo     infoProp = typeLeft.ensureTypeInfo(errs).findProperty(idProp);

                            typeFn = infoProp == null ? pool.typeObject() : infoProp.getType();
                            }
                        else
                            {
                            assert argMethod instanceof Register;
                            typeFn = argMethod.getType().resolveTypedefs();
                            }

                        if (typeFn.isA(pool.typeFunction()))
                            {
                            Expression exprNew = expr.validate(ctx, typeFn, errs);
                            if (exprNew != null)
                                {
                                expr = exprNew;

                                TypeConstant[] atypeResult = validateFunction(ctx, typeFn, errs);
                                if (atypeResult != null)
                                    {
                                    return finishValidations(atypeRequired, atypeResult,
                                            TypeFit.Fit, null, errs);
                                    }
                                }
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                    "Function", typeFn.getValueString());
                            }
                        }
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

                m_fBindTarget = false;
                m_fBindParams = isAnyArgBound();
                m_fCall       = !isSuppressCall();

                // first we need to validate the function, to make sure that the type includes
                // sufficient information about parameter and return types, and that it fits with
                // the arguments that we have
                TypeConstant typeFn = testFunction(ctx, exprNew.getType(), atypeRequired, errs);
                if (typeFn != null)
                    {
                    TypeConstant[] atypeResult = validateFunction(ctx, typeFn, errs);
                    if (atypeResult != null)
                        {
                        return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                        }
                    }
                }
            }

        return finishValidations(atypeRequired, atypeRequired == null ?
            TypeConstant.NO_TYPES : atypeRequired, TypeFit.NoFit, null, errs);
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
        return nodeChild == expr || args.contains(nodeChild);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        int cLVals = aLVal.length;
        int cRVals = getValueCount();

        assert cLVals <= cRVals;

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
        int        cDefaults      = m_cDefaults;
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
                            // it's a method, and we need to generate the necessary code that calls it;
                            // generate the arguments
                            int        cAll  = idMethod.getRawParams().length;
                            Argument   arg   = null;
                            Argument[] aArgs = null;
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
                                    arg = args.get(0).generateArgument(ctx, code, false, true, errs);
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
                                    aArgs[of + i] = args.get(i).generateArgument(ctx, code, false, true, errs);
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
                                    code.add(new Invoke_01(argTarget, idMethod, aargResult[0]));
                                    break;

                                case _11:
                                    if (m_fTupleArg)
                                        {
                                        code.add(new Invoke_T1(argTarget, idMethod, arg, aargResult[0]));
                                        }
                                    else
                                        {
                                        code.add(new Invoke_11(argTarget, idMethod, arg, aargResult[0]));
                                        }
                                    break;

                                case _N1:
                                    code.add(new Invoke_N1(argTarget, idMethod, aArgs, aargResult[0]));
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
                if (exprLeft == null || !exprLeft.hasSideEffects())
                    {
                    // take the argument (e.g. "super") & drop through to the function handling
                    argFn = m_argMethod;
                    }
                else
                    {
                    // evaluate to find the argument (e.g. "var.prop", where prop holds a function)
                    argFn = expr.generateArgument(ctx, code, fLocalPropOk, fTargetOnStack, errs);
                    }
                }
            }
        else // _NOT_ an InvocationExpression of a NameExpression (i.e. it's just a function)
            {
            // obtain the function that will be bound and/or called
            assert !m_fBindTarget;
            argFn = expr.generateArgument(ctx, code, fLocalPropOk, fTargetOnStack, errs);
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

        if (!m_fCall && !m_fBindParams)
            {
            // not binding anything; not calling anything; just returning the function itself
            if (cLVals > 0)
                {
                aLVal[0].assign(argFn, code, errs);
                }
            return;
            }

        TypeConstant[] atypeSub    = typeFn.getParamTypesArray();
        TypeConstant[] atypeParams = atypeSub[F_ARGS].getParamTypesArray();
        int            cAll        = atypeParams.length;

        assert cTypeParams + cArgs + cDefaults == cAll;

        if (m_fCall)
            {
            Argument   arg   = null;
            Argument[] aArgs = null;
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
                    arg = args.get(0).generateArgument(ctx, code, false, true, errs);
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
                    aArgs[of + i] = args.get(i).generateArgument(ctx, code, false, true, errs);
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
                    code.add(new Call_10(argFn, arg));
                    break;

                case _N0:
                    code.add(new Call_N0(argFn, aArgs));
                    break;

                case _01:
                    code.add(new Call_01(argFn, aargResult[0]));
                    break;

                case _11:
                    code.add(new Call_11(argFn, arg, aargResult[0]));
                    break;

                case _N1:
                    code.add(new Call_N1(argFn, aArgs, aargResult[0]));
                    break;

                case _0N:
                    code.add(new Call_0N(argFn, aargResult));
                    break;

                case _1N:
                    code.add(new Call_1N(argFn, arg, aargResult));
                    break;

                case _NN:
                    code.add(new Call_NN(argFn, aArgs, aargResult));
                    break;

                default:
                    throw new UnsupportedOperationException("invocation " + combine(chArgs, chRets));
                }
            return;
            }

        // bind (or partially bind) the function
        assert m_fBindParams;

        // count the number of parameters to bind
        int cBind     = cTypeParams + cDefaults;
        int ofDefault = cAll - cDefaults;
        for (int i = 0; i < cArgs; ++i)
            {
            if (!args.get(i).isNonBinding())
                {
                ++cBind;
                }
            }

        int[]      aiArg = new int[cBind];
        Argument[] aArg  = new Argument[cBind];
        for (int i = 0, iNext = 0; i < cArgs; ++i)
            {
            if (i < cTypeParams)
                {
                aiArg[iNext] = i;
                aArg [iNext] = aargTypeParams[i];
                iNext++;
                }
            else if (i >= ofDefault)
                {
                aiArg[iNext] = i;
                aArg [iNext] = Register.DEFAULT;
                iNext++;
                }
            else if (!args.get(i).isNonBinding())
                {
                aiArg[iNext] = i;
                aArg [iNext] = args.get(i).generateArgument(ctx, code, false, true, errs);
                iNext++;
                }
            }

        Register regFn = new Register(getType());
        code.add(new FBind(argFn, aiArg, aArg, regFn));

        if (cLVals > 0)
            {
            aLVal[0].assign(regFn, code, errs);
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

        boolean fNoFBind = !isAnyArgBound();
        boolean fNoCall  = isSuppressCall();

        m_targetinfo  = null;
        m_argMethod   = null;
        m_idConvert   = null;
        m_fBindTarget = false;
        m_fBindParams = !fNoFBind;
        m_fCall       = !fNoCall;

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

            atypeReturn = pool.extractFunctionReturns(typeFn);
            if (atypeReturn == null)
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

                TypeInfo infoLeft = typeLeft.ensureTypeInfo(errs);

                arg = findCallable(ctx, infoLeft, sName, MethodType.Either, true,
                        atypeReturn, ErrorListener.BLACKHOLE);
                if (arg != null)
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
                if (testFunction(ctx, arg.getType(), atypeReturn, errs) == null)
                    {
                    return null;
                    }

                m_argMethod = arg;
                return arg;
                }

            if (arg instanceof TargetInfo)
                {
                TargetInfo       target = (TargetInfo) arg;
                TypeInfo         info   = target.getTargetType().ensureTypeInfo(errs);
                IdentityConstant id     = target.getId();
                if (id instanceof MultiMethodConstant)
                    {
                    // find the method based on the signature
                    // TODO this only finds methods immediately contained within the class; does not find nested methods!!!
                    MethodType methodType = fConstruct
                            ? MethodType.Constructor
                            : (fNoCall && fNoFBind) || target.hasThis()
                                    ? MethodType.Either
                                    : MethodType.Function;
                    IdentityConstant idCallable = findCallable(ctx, info, sName, methodType,
                                                    id.isNested(), atypeReturn, errs);
                    if (idCallable == null)
                        {
                        // check to see if we would have found something had we included methods in
                        // the search
                        if (methodType == MethodType.Function && findCallable(ctx, info, sName,
                                MethodType.Method, id.isNested(), atypeReturn, ErrorListener.BLACKHOLE) != null)
                            {
                            exprName.log(errs, Severity.ERROR, Compiler.NO_THIS_METHOD, sName, target.getTargetType());
                            }
                        return null;
                        }
                    else
                        {
                        m_targetinfo  = target; // (only used for non-constants)
                        m_argMethod   = idCallable;
                        m_method      = getMethod(info, idCallable);
                        m_fBindTarget = m_method != null && !m_method.isFunction();
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

                    if (testFunction(ctx, prop.getType(), atypeReturn, errs) == null)
                        {
                        return null;
                        }
                    else if (prop.isConstant() || target.hasThis())
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

            if (sName.equals("construct"))
                {
                log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, ctx.getThisType().getValueString());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sName);
                }
            return null;
            }
        else // there is a "left" expression for the name
            {
            if (tokName.isSpecial())
                {
                tokName.log(errs, getSource(), Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
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
                    boolean  fPreserveOrigin;

                    if (nameLeft.getMeaning() == NameExpression.Meaning.Type)
                        {
                        // "Class" meaning in IdentityMode can only indicate a "type-of-class" scenario
                        assert typeLeft.isTypeOfType();

                        typeLeft = typeLeft.getParamType(0);
                        if (access != typeLeft.getAccess())
                            {
                            typeLeft = pool.ensureAccessTypeConstant(typeLeft, access);
                            }
                        infoLeft         = typeLeft.ensureTypeInfo(errs);
                        fPreserveOrigin  = false;
                        }
                    else
                        {
                        infoLeft         = idLeft.ensureTypeInfo(access, errs);
                        fPreserveOrigin  = true;
                        }

                    MethodType methodType = fConstruct ? MethodType.Constructor
                            : fNoFBind && fNoCall ? MethodType.Either : MethodType.Function;

                    Argument arg = findCallable(ctx, infoLeft, sName, methodType, false, atypeReturn, errs);
                    if (arg != null)
                        {
                        MethodConstant idMethod   = (MethodConstant) arg;
                        MethodInfo     infoMethod = infoLeft.getMethodById(idMethod);
                        assert infoMethod != null;

                        if (infoMethod.isAbstractFunction())
                            {
                            log(errs, Severity.ERROR, Compiler.ILLEGAL_FUNKY_CALL,
                                    idMethod.getValueString());
                            return null;
                            }

                        if (fPreserveOrigin && !idMethod.getNamespace().equals(idLeft))
                            {
                            // preserve the origin information on the function's MethodConstant;
                            // for example, if there is a call "Point.hashCode(p)", the runtime
                            // should know to use the Point's structure even though the "hashCode"
                            // only declared on Const
                            idMethod = pool.ensureMethodConstant(idLeft, idMethod.getSignature());
                            }

                        m_argMethod = idMethod;
                        m_method    = infoMethod.getTopmostMethodStructure(infoLeft);
                        return idMethod;
                        }
                    }

                switch (nameLeft.getMeaning())
                    {
                    case Property:
                        {
                        PropertyConstant idProp = (PropertyConstant) nameLeft.getIdentity(ctx);
                        if (idProp.isFormalType())
                            {
                            // Example (NaturalHasher.x):
                            //   Int hashOf(ValueType value)
                            //     {
                            //     return ValueType.hashCode(value);
                            //     }
                            //
                            // "this" is "ValueType.hashCode(value)"
                            // idProp.getFormalType() is NaturalHasher.ValueType

                            TypeInfo  infoType = idProp.getFormalType().ensureTypeInfo(errs);
                            ErrorList errsTemp = new ErrorList(1);

                            Argument arg = findCallable(ctx, infoType, sName, MethodType.Function, false,
                                                atypeReturn, errsTemp);
                            if (arg != null)
                                {
                                m_argMethod   = arg;
                                m_method      = getMethod(infoType, arg);
                                m_fBindTarget = false;
                                m_idFormal    = idProp;
                                errsTemp.logTo(errs);
                                return arg;
                                }
                            }
                        break;
                        }

                    case FormalType:
                        {
                        // Example:
                        //   static <CompileType extends Hasher> Int hashCode(CompileType array)
                        //      {
                        //      Int hash = 0;
                        //      for (CompileType.ElementType el : array)
                        //          {
                        //          hash += CompileType.ElementType.hashCode(el);
                        //          }
                        //      return hash;
                        //      }
                        // "this" is "CompileType.ElementType.hashCode(el)"
                        //  typeLeft is a type of "CompileType.ElementType" formal type child

                        TypeInfo  infoLeft = typeLeft.ensureTypeInfo(errs);
                        ErrorList errsTemp = new ErrorList(1);

                        Argument arg = findCallable(ctx, infoLeft, sName, MethodType.Function, false,
                                            atypeReturn, errsTemp);
                        if (arg != null)
                            {
                            m_argMethod   = arg;
                            m_method      = getMethod(infoLeft, arg);
                            m_fBindTarget = false;
                            m_idFormal    = (FormalTypeChildConstant) nameLeft.getIdentity(ctx);
                            errsTemp.logTo(errs);
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
                                TypeParameterConstant idParam = method.getParam(iReg).
                                        asTypeParameterConstant(method.getIdentityConstant());

                                TypeInfo  infoLeft = idParam.getType().ensureTypeInfo(errs);
                                ErrorList errsTemp = new ErrorList(1);
                                Argument  arg      = findCallable(ctx, infoLeft, sName, MethodType.Function, false,
                                                        atypeReturn, errsTemp);
                                if (arg != null)
                                    {
                                    m_argMethod   = arg;
                                    m_method      = getMethod(infoLeft, arg);
                                    m_fBindTarget = false;
                                    m_idFormal    = idParam;
                                    errsTemp.logTo(errs);
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
            TypeInfo  infoLeft = typeLeft.ensureTypeInfo(errs);
            ErrorList errsTemp = new ErrorList(1);

            Argument arg = findCallable(ctx, infoLeft, sName, MethodType.Method, false,
                                atypeReturn, errsTemp);
            if (arg != null)
                {
                m_argMethod   = arg;
                m_method      = getMethod(infoLeft, arg);
                m_fBindTarget = m_method != null;
                errsTemp.logTo(errs);
                return arg;
                }

            // allow for a function on the "left type" to be called (Bjarne'd):
            //    x.f(y, z) -> X.f(x, y, z), where X is the class of x
            if (typeLeft.isSingleUnderlyingClass(true) && !isSuppressCall() && !isAnyArgUnbound())
                {
                List<Expression> listArgs = new ArrayList<>(args);
                listArgs.add(0, exprLeft);

                ErrorList errsTempB = new ErrorList(1);
                arg = findMethod(ctx, infoLeft, sName, listArgs, MethodType.Function, false,
                            atypeReturn, errsTempB);
                if (arg != null)
                    {
                    m_argMethod   = arg;
                    m_method      = getMethod(infoLeft, arg);
                    m_fBindTarget = false;
                    m_fBjarne     = true;
                    errsTempB.logTo(errs);
                    return arg;
                    }
                }

            errsTemp.logTo(errs);
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
     *
     *
     * @param ctx           the context
     * @param infoParent    the TypeInfo to search for the method or function on
     * @param sName         the name of the method or function
     * @param methodType    the categories of methods to include in the search
     * @param fAllowNested  if true, nested methods can be used at the target
     * @param aRedundant    the redundant return type information (helps to clarify which method or
     *                      function to select)
     * @param errs          the error listener to log errors to
     *
     * @return the matching method, function, or (rarely) property
     */
    protected IdentityConstant findCallable(
            Context        ctx,
            TypeInfo       infoParent,
            String         sName,
            MethodType     methodType,
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

        return findMethod(ctx, infoParent, sName, args, methodType, fAllowNested, aRedundant, errs);
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
     * @param atypeReturn (optional) an array of required return types
     * @param errs        the error listener to log errors to
     *
     * @return the type of the function, or null if a type-safe type for the function could not be
     *         determined, in which case an error has been reported
     */
    protected TypeConstant testFunction(
            Context        ctx,
            TypeConstant   typeFn,
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
                typeFn = idConvert.getRawReturns()[F_ARGS];
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

        int     cParams = atypeParams.length;
        boolean fValid  = true;

        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        if (cParams != cArgs)
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cParams, cArgs);
            fValid = false;
            }
        for (int i = 0, c = Math.min(cParams, cArgs); i < c; ++i)
            {
            TypeConstant typeParam = atypeParams[i];
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
     * @param ctx     the compiler context
     * @param typeFn  the type of the function
     * @param errs    the error listener to log errors to
     *
     * @return the type of the function return types, or null if the validation fails,
     *         in which case an error has been reported
     */
    protected TypeConstant[] validateFunction(Context ctx, TypeConstant typeFn, ErrorListener errs)
        {
        ConstantPool   pool      = pool();
        TypeConstant[] atypeArgs = pool.extractFunctionParams(typeFn);

        if (validateExpressions(ctx, args, atypeArgs, errs) != null)
            {
            if (m_fCall)
                {
                return pool.extractFunctionReturns(typeFn);
                }

            if (m_fBindParams)
                {
                typeFn = bindFunctionParameters(typeFn);
                }

            return new TypeConstant[] {typeFn};
            }
        return null;
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
    private GenericTypeResolver makeTypeParameterResolver(Context ctx, MethodStructure method,
                                                          TypeConstant[] atypeReturn)
        {
        int            cArgs     = args.size();
        TypeConstant[] atypeArgs = new TypeConstant[cArgs];
        for (int i = 0; i < cArgs; i++)
            {
            atypeArgs[i] = args.get(i).getImplicitType(ctx);
            }

        Map<String, TypeConstant> mapTypeParams =
                method.resolveTypeParameters(atypeArgs, atypeReturn);

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
                typeFn = pool.bindFunctionParam(typeFn, i);
                }
            }
        return typeFn;
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

    /**
     * Function type first parameter is function param types tuple:
     * <p/>
     * {@code Function<ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>}
     */
    public static final int F_ARGS = 0;
    /**
     * Function type second parameter is return types tuple:
     * <p/>
     * {@code Function<ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>}
     */
    public static final int F_RETS = 1;

    protected Expression       expr;
    protected List<Expression> args;
    protected long             lEndPos;

    private transient boolean         m_fBindTarget;     // do we require a target
    private transient boolean         m_fBindParams;     // do we need to bind any parameters
    private transient boolean         m_fCall;           // do we need to call/invoke
    private transient boolean         m_fTupleArg;       // indicates that arguments come from a tuple
    private transient TargetInfo      m_targetinfo;      // for left==null with prop or method name
    private transient Argument        m_argMethod;
    private transient MethodStructure m_method;          // if m_argMethod is a MethodConstant,
                                                         // this holds the corresponding structure
    private transient boolean         m_fBjarne;         // indicates that the invocation expression
                                                         // was Bjarne-transformed from x.f() to X.f(x)
    private transient FormalConstant  m_idFormal;        // if not null, indicates that the invocation
                                                         // expression applies to a function on a formal
                                                         // type (e.g. ValueType.hashCode(value))
    private transient Argument[]      m_aargTypeParams;  // "hidden" type parameters
    private transient int             m_cDefaults;       // number of default arguments
    private transient MethodConstant  m_idConvert;       // conversion method

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }
