package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

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
 *   description                              target  args    call
 *   ---------------------------------------  ------  ------  ------
 *   obtain reference to method or function
 *   function invocation                                      X
 *   binding function parameters / currying           X
 *   function invocation                              X       X
 *   method binding                           X
 *   method invocation                        X               X
 *   method and parameter binding             X       X
 *   method invocation                        X       X       X
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
 *     is a left, or (ii) there is no left and the context is not static.</li>
 * <li>If the name has a {@code left} expression, that expression provides the scope to search for
 *     a matching method/function. If the left expression is itself a NameExpression, then the scope
 *     may actually refer to two separate types, because the NameExpression may indicate both (i)
 *     identity mode and (ii) reference mode. In this case, the identity mode is treated as a
 *     first scope, and the reference mode is treated as a second scope.</li>
 * <li>If the name does not have a {@code left} expression, then walk up the AST parent node chain
 *     looking for a registered name, i.e. a local variable of that name, stopping once the
 *     containing method/function (but <b>not</b> a lambda, since it has a permeable barrier to
 *     enable local variable capture) is reached. If a match is found, then that is the
 *     method/function to use, and it is an error if the type of that variable is not a method, a
 *     function, or a reference that has an @Auto conversion to a method or function. (Done.)</li>
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
 * <p/>
 * Deferred implementation items:
 * <ul><li>TODO default parameter values
 * </li><li>TODO named parameters
 * </li></ul>
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
                && args.get(0) instanceof VersionExpression
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
            Version      version = ((VersionExpression) args.get(0)).getVersion();
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
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        ConstantPool pool       = pool();
        TypeConstant typeMethod = expr.getImplicitType(ctx);
        if (typeMethod != null)
            {
            TypeConstant[] a = null;
            if (typeMethod.isA(pool.typeMethod()) || typeMethod.isA(pool.typeFunction()))
                {
                a = typeMethod.getParamTypesArray();
                // TODO
                }
            else
                {
                // check for an @Auto that converts to
                }
            }

        MethodConstant idMethod = resolveName(ctx, false, typeLeft, aRedundant, aArgs, ErrorListener.BLACKHOLE);
        if (idMethod != null)
            {
            return idMethod.getRawReturns();
            }

        return TypeConstant.NO_TYPES;
        }


    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid   = true;
        boolean fNoDeRef = false;

        // TODO validate parameters - might set fNoDeRef to true if any of the params is "?"

        if (expr instanceof NameExpression)
            {
            // when we have a name expression on the left, we do NOT (!!!) validate it, because the
            // name resolution is the responsibility of this InvocationExpression, and the
            // NameExpression will error on resolving a method/function name
            MultiMethodConstant  idMMethod = null;
            NameExpression       exprName  = (NameExpression) expr;
            Expression           left      =  exprName.left;
            Token                name      = exprName.name;
            String               sName     = name.getValueText();
            List<TypeExpression> params    = exprName.params;                // redundant returns

            // it's possible that the name indicates that the invocation is a partial binding and
            // not actually an invocation
            fNoDeRef |= exprName.isSuppressDeref();

            // a name expression may have type params from the construct:
            //      QualifiedNameName TypeParameterTypeList-opt
            // these names form the optional "redundant returns" portion of the method/function
            // invocation
            ConstantPool pool   = pool();
            boolean      fValid = true;
            if (params != null)
                {
                for (int i = 0, c = params.size(); i < c; ++i)
                    {
                    TypeExpression typeOld = params.get(i);
                    TypeExpression typeNew = (TypeExpression) typeOld.validate(
                            ctx, pool.typeType(), TuplePref.Rejected, errs);
                    fValid &= typeNew != null;
                    if (typeNew != typeOld && typeNew != null)
                        {
                        params.set(i, typeNew);
                        }
                    }
                }

            if (left == null)
                {
                Argument arg = ctx.resolveName(name, errs);
                if (arg instanceof MultiMethodConstant)
                    {
                    idMMethod = (MultiMethodConstant) arg;
                    }
                else if (arg == null)
                    {
                    log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                            sName, ctx.getMethod().getIdentityConstant().getSignature());
                    fValid = false;
                    }
                else
                    {
                    // TODO log error - the name has to resolve to a method/function
                    fValid = false;
                    }
                }
            else
                {
                Expression leftNew = left.validate(ctx, null, TuplePref.Rejected, errs);
                if (leftNew == null)
                    {
                    // TODO null is an error, so the idea is to get out of here (but maybe do some work first, in case we can catch other errors)
                    return null;
                    }
                else if (leftNew != left)
                    {
                    exprName.left = left = leftNew;
                    }

                if (left.isVoid())
                    {
                    // TODO log error
                    return null;
                    }

                TypeInfo infoType = left.getType().ensureTypeInfo(errs);
                // TODO that info is where we will ultimately find the sName
                }

            if (!fValid)
                {
                return null;
                }

            // find the correct Method within the MultiMethod, using the (optional) redundant return
            // types, and the parameters
            // TODO
            }
        else // the expr is NOT a NameExpression
            {
            Expression exprNew = expr.validate(ctx, pool().typeFunction(), TuplePref.Rejected, errs);
            if (exprNew != expr)
                {
                if (exprNew != null)
                    {
                    expr = exprNew;
                    }
                }

            // verify that the arguments match the parameters
            // TODO

            }

        if (fValid)
            {

            }
        // TODO we have an "expr" that represents the thing being invoked, and we have "args" that represents the things being passed
        // TODO we may need one to validate the other, i.e. we may need to know the arg types to find the method, the the method to validate the args by required type

        // if (expr.validate(ctx,))
        // TODO

        // TODO goal is to figure out which one of these that we are responsible for producing code to do:
        // 1) call a method
        // 2) call a function
        // 3) bind a method (and possibly (partially) bind the resulting function)
        // 4) (partially) bind a function

        // TODO finishValidation() or finishValidations()
        return finishValidation(fit, typeRequired == null ? pool().typeObject() : typeRequired, null);
        }


    // ----- method resolution helpers -------------------------------------------------------------

    /**
     * @return true iff this expression does not actually result in an invocation, but instead
     *         resolves to a reference to a method or a function as its result
     */
    protected boolean isSuppressCall()
        {
        if (expr instanceof NameExpression && ((NameExpression) expr).isSuppressDeref())
            {
            return true;
            }

        for (Expression exprArg : args)
            {
            if (exprArg.isNonBinding())
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
     * setting {@link #m_idMethod}.
     *
     * @param ctx         the compiler context
     * @param fForce      true to force the resolution, even if it has been done previously
     * @param typeLeft    the type of the "left" expression of the name, or null if there is no left
     * @param aRedundant  the types of any "redundant return" indicators
     * @param aArgs       array of argument types, with null meaning "any" (i.e. "?")
     * @param errs        the error list to log errors to
     *
     * @return the method constant, or null if it was not determinable
     */
    protected MethodConstant resolveName(
            Context        ctx,
            boolean        fForce,
            TypeConstant   typeLeft,
            TypeConstant[] aRedundant,
            TypeConstant[] aArgs,
            ErrorListener  errs)
        {
        if (!fForce && m_idMethod != null)
            {
            return m_idMethod;
            }

        NameExpression exprName   = (NameExpression) expr;
        int            cRedundant = aRedundant == null ? 0 : aRedundant.length;
        // TODO

        return m_idMethod;
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


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       expr;
    protected List<Expression> args;
    protected long             lEndPos;

    private transient boolean fBindTarget;
    private transient boolean fBindParams;
    private transient boolean fCall;

    private transient MethodConstant m_idMethod;

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }
