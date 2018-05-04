package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MultiMethodConstant;
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
 *   obtain reference to method or function   N       N       N
 *   function invocation                      N       N       Y
 *   binding function parameters / currying   N       Y       N
 *   function invocation                      N       Y       Y
 *   method binding                           Y       N       N
 *   method invocation                        Y       N       Y
 *   method and parameter binding             Y       Y       N
 *   method invocation                        Y       Y       Y
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
 *     since that also indicates that the InvocationExpression must not to perform the "call", but
 *     rather yields a method or function reference as its result.</li>
 * <li>The rules for determining th</li>
 * </ul>
 * <p/>
 * The rules for determining the method or function to call when the name is provided:
 * <ul>
 * <li>First, validate the (optional) left expression, and all of the (optional) params expressions
 *     of the NameExpression.</li>
 * <li>Second, determine whether the search will involve methods, functions, or both. Functions are
 *     included if (i) there is no left, or (ii) the left is identity-mode. Methods are included if
 *     (i) there is a left, or (ii) there is no left and the context is not static.</li>
 * <li>If there is no left, look for functions and methods that exist as children of the current
 *     component (which may be the method TODO), walking up the component tree until the method is reached.
 *     walking up the
 * on the TypeInfo for the type possible methods and/or functions that </li>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li>
 *
 * </ul>
 * <p/>
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

    // bind target: F/T
    // bind parameters: F/T
    // call: F/T
    //

    private transient boolean fBindTarget;
    private transient boolean fBindParams;
    private transient boolean fCall;

    enum BindParams {None, Partial, All}
    enum CallPlan {None, Invoke, Call}


    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }
