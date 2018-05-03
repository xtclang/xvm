package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.asm.op.P_Get;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A name expression specifies a name. This handles a simple name, a qualified name, a dot name
 * epxression, and names with type parameters and/or supress-de-reference symbols.
 *
 * <p/>
 * A simple name can refer to:
 * <ul>
 * <li>An import, which in turn refers to a module/package/class, property/constant,
 *     multi-method, or typedef;</li>
 * <li>A reserved identifier, such as "this";</li>
 * <li>A variable;</li>
 * <li>A parameter (other than being read-only, i.e. a Ref instead of a Var, this follows the
 *     same rules as for variables);</li>
 * <li>A property (i) defined within the current method body, (ii) defined by the current class
 *     (or within one of the sequence of components between the current method and that class),
 *     or (iii) defined by a containing class/package/module;</li>
 * <li>A constant (i) defined by the current class (or within one of the sequence of components
 *     between the current method and that class), or (ii) defined by a containing
 *     class/package/module;</li>
 * <li>A method (i) defined within the current method body, (ii) defined by the current class
 *     (or within one of the sequence of components between the current method and that class),
 *     or (iii) defined by a containing class/package/module;</li>
 * <li>A function (i) defined within the current method body, (ii) defined by the current class
 *     (or within one of the sequence of components between the current method and that class),
 *     or (iii) defined by a containing class/package/module;</li>
 * <li>A class (i) defined within the current method body, (ii) defined by the current class
 *     (or within one of the sequence of components between the current method and that class),
 *     or (iii) defined by a containing class/package/module, or (iv) the containing module
 *     itself;</li>
 * <li>A typedef (i) defined within the current method body, (ii) defined by the current class
 *     (or within one of the sequence of components between the current method and that class),
 *     or (iii) defined by a containing class/package/module;</li>
 * </ul>
 *
 * <p/>
 * The context either has a "this" (i.e. context from inside of an instance method), or it
 * doesn't (i.e. context from inside of a function). Even a lambda within a method has a "this",
 * since it can conceptually capture the "this" of the method. The presence of a "this" has to
 * be tracked, because the interpretation of a name will differ in some cases based on whether
 * there is a "this" or not.
 * <p/>
 * A name resolution also has an implicit de-reference, or an explicit non-dereference (a
 * suppression of the dereference using the "&" symbol). The result of the name being resolved
 * will differ based on whether the name is implicitly deferenced, or explicitly not
 * dereferenced.
 *
 * <p/>
 * The starting point for dereferencing is within a "method body", which is one of:
 * <ul>
 * <li>A method;</li>
 * <li>A function;</li>
 * <li>An initializer (e.g. for a property), which is implicitly a constructor (for a property),
 *     or a function (for a constant);</li>
 * </ul>
 *
 * <p/>
 * Furthermore, the starting point (i.e. the point at which the name to resolve is being used)
 * may be nested within a lambda expression. The lambda boundary represents a point at which
 * capture information must be accumulated, because each capture adds an implicit parameter to
 * the lambda (and thus an implicit argument to be included by the initializer of the lambda).
 * Assuming that the name resolves to a variable or parameter of type T:
 * <ul>
 * <li>A lambda that uses a captured variable name as an LVal adds an implicit type
 *     {@code Var<T>} parameter of that name.</li>
 * <li>A lambda that uses (i) a captured parameter name, or (ii) a captured effectively-constant
 *     variable name, as an RVal, adds an implicit type {@code T} parameter of that name.
 * <li>A lambda that uses a captured NOT-effectively-constant variable name as an RVal adds
 *     an implicit type {@code Ref<T>} parameter of that name.</li>
 * <li>If the lambda is nested within a lambda, and the name refers to a variable or parameter
 *     outside of the outer lambda, then -- in addition to the above! -- the outer lambda must
 *     capture the name on behalf of the inner lambda, allowing the inner lambda to capture it
 *     from the outer lambda (and recursively so, if nested more than one level deep).</li>
 * </ul>
 *
 * <p/>
 * Lastly, there is the determination of the name itself. If the name refers to the name of an
 * import, then that name is resolved first (recursively), such that the result is that the name
 * no longer refers to the name of an import, but rather to the component (Module, Package,
 * Class, Property, Multi-Method) being imported by that name.
 * <p/>
 * <code><pre>
 *   Name          method             specifies            "static" context /    specifies
 *   refers to     context            no-de-ref            identity mode         no-de-ref
 *   ------------  -----------------  -------------------  ------------------    -------------------
 *   Reserved      T                  Error                T                     Error
 *   - Virtual     T                  Error                Error                 Error
 *
 *   Parameter     T                  <- Ref               T                     <- Ref
 *   Local var     T                  <- Var               T                     <- Var
 *
 *   Property      T                  <- Ref/Var           PropertyConstant*[1]  PropertyConstant*
 *   - type param  T                  <- Ref               PropertyConstant*[1]  PropertyConstant*
 *   Constant      T                  <- Ref               T                     <- Ref
 *
 *   Class         ClassConstant*     ClassConstant*       ClassConstant*        ClassConstant*
 *   - related     PseudoConstant*    ClassConstant*       ClassConstant*        ClassConstant*
 *   Singleton     SingletonConstant  ClassConstant*       SingletonConstant     ClassConstant*
 *
 *   Typedef       Type<..>           Error                Type                  Error
 *
 *   MultiMethod   Error              Error                Error                 Error
 * </pre></code>
 * <p/>
 * Note: '*' signifies potential "identity mode"
 * <p/>
 * [1] must have a left hand side in identity mode; otherwise it is an Error
 * <p/>
 * Method and function evaluation is the most complex of these scenarios, because the no-de-ref
 * flag is on the name expression, but can also be implied by an argument of the
 * NonBindingExpression type. As a result, the InvocationExpression is responsible for checking
 * for a non-binding effect, which requires <i>at least</i> one of:
 * <ul>
 * <li>The "left" expression being a name or dot-name expression with {@link #isSuppressDeref()}
 *     evaluating to true; or</li>
 * <li>Any invocation argument with {@link #isNonBinding()} evaluating to true.</li>
 * </ul>
 * <p/>
 * The invocation expression does not delegate validation to the name expression; instead, it takes
 * on the responsibility of recognizing that there is a name expression, and validating the contents
 * on the name expression's behalf.
 */
public class NameExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * This constructor is used to implement a "simple name" expression.
     *
     * @param name  the (required) name
     */
    public NameExpression(Token name)
        {
        this(null, name, null, name.getEndPosition());
        }

    /**
     * This constructor is used to implement an "initial name" expression.
     *
     * @param amp      the (optional) no-de-reference token "&"
     * @param name     the (required) name
     * @param params   the (optional)
     * @param lEndPos  the end of the expression
     */
    public NameExpression(Token amp, Token name, List<TypeExpression> params, long lEndPos)
        {
        this(null, amp, name, params, lEndPos);
        }

    /**
     * This constructor is used to implement "dot name" expressions. The expression to the left of
     * the dot is passed as "left".
     *
     * @param left     the (optional) expression to the left of the dot
     * @param amp      the (optional) no-de-reference token "&"
     * @param name     the (required) name
     * @param params   the (optional)
     * @param lEndPos  the end of the expression
     */
    public NameExpression(Expression left, Token amp, Token name, List<TypeExpression> params, long lEndPos)
        {
        this.left    = left;
        this.amp     = amp;
        this.name    = name;
        this.params  = params;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper()
        {
        return name.getId() == Id.SUPER || left != null && left.usesSuper();
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        // can only be "Name.Name.present" form
        if (left instanceof NameExpression && amp == null && params == null && getName().equals("present"))
            {
            // left has to be all names
            NameExpression expr = (NameExpression) left;
            while (expr.left != null)
                {
                if (!expr.getName().equals("present") && expr.left instanceof NameExpression)
                    {
                    expr = (NameExpression) expr.left;
                    }
                else
                    {
                    return super.validateCondition(errs);
                    }
                }
            return true;
            }
        else
            {
            return super.validateCondition(errs);
            }
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            ConstantPool pool = pool();
            return pool.ensurePresentCondition(new UnresolvedNameConstant(pool,
                    ((NameExpression) left).collectNames(1), false));
            }

        return super.toConditionalConstant();
        }

    /**
     * @return true iff the expression is as "pure" name expression, i.e. containing only names
     */
    public boolean isOnlyNames()
        {
        return left == null || left instanceof NameExpression && ((NameExpression) left).isOnlyNames();
        }

    /**
     * Build a list of type names.
     *
     * @return a list of name tokens
     */
    public List<Token> getNameTokens()
        {
        return collectNameTokens(1);
        }

    /**
     * Build an array of names for the name expression.
     *
     * @param cNames  how many names so far (recursing right to left)
     *
     * @return an array of names
     */
    protected String[] collectNames(int cNames)
        {
        String[] asName;
        if (left instanceof NameExpression)
            {
            asName = ((NameExpression) left).collectNames(cNames + 1);
            }
        else
            {
            asName = new String[cNames];
            }
        asName[asName.length-cNames] = getName();
        return asName;
        }

    /**
     * Build an list of name tokens for the name expression.
     *
     * @param cNames  how many names so far (recursing right to left)
     *
     * @return a list of name tokens
     */
    protected List<Token> collectNameTokens(int cNames)
        {
        List<Token> list;
        if (left instanceof NameExpression)
            {
            list = ((NameExpression) left).collectNameTokens(cNames + 1);
            }
        else
            {
            list = new ArrayList<>(cNames);
            }
        list.add(getNameToken());
        return list;
        }

    /**
     * @return the expression that precedes this name, if this is a "dot name" expression, or null
     */
    public Expression getLeftExpression()
        {
        return left;
        }

    /**
     * @return true iff the expression is explicitly non-de-referencing, as with the '&' pre-fix on
     *         a class, property, or method name
     */
    public boolean isSuppressDeref()
        {
        return amp != null;
        }

    /**
     * @return true iff the expression is explicitly non-de-referencing, as with the '&' pre-fix on
     *         a class, property, or method name, or if the left expression is a name expression and
     *         it has any suppressed deref
     */
    public boolean hasAnySuppressDeref()
        {
        return isSuppressDeref() || left instanceof NameExpression && ((NameExpression) left).hasAnySuppressDeref();
        }

    /**
     * @return the name token
     */
    public Token getNameToken()
        {
        return name;
        }

    /**
     * @return the name
     */
    public String getName()
        {
        return name.getValueText();
        }

    /**
     * @return true iff there are any trailing type expressions
     */
    public boolean hasTrailingTypeParams()
        {
        return params != null && !params.isEmpty();
        }

    /**
     * @return the trailing {@code "<T1, T2>"} type expressions, or null
     */
    public List<TypeExpression> getTrailingTypeParams()
        {
        return params;
        }

    @Override
    public long getStartPosition()
        {
        // construct gets moved from its left-most position in the source code to a right-most
        // position in the AST, so if this is the "construct" node, then the word "construct" was
        // actually the starting position of the expression
        if (left != null && name.getId() != Id.CONSTRUCT)
            {
            return left.getStartPosition();
            }

        if (amp != null)
            {
            return amp.getStartPosition();
            }

        return name.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    public boolean isSpecial()
        {
        return name.isSpecial() || left instanceof NameExpression && ((NameExpression) left).isSpecial();
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return new NamedTypeExpression(null, collectNameTokens(1), null, null, params, lEndPos);
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        Argument arg = resolveRawArgument(ctx, true, ErrorListener.BLACKHOLE);
        if (arg == null)
            {
            // we need the "raw argument" to determine the type from
            return null;
            }

        // apply the "trailing type parameters"
        TypeConstant[] aParams = null;
        if (hasTrailingTypeParams())
            {
            List<TypeExpression> params = getTrailingTypeParams();
            if (params.isEmpty())
                {
                aParams = TypeConstant.NO_TYPES;
                }
            else
                {
                int                     cParams   = params.size();
                ArrayList<TypeConstant> listTypes = new ArrayList<>(cParams);
                for (int i = 0; i < cParams; ++i)
                    {
                    TypeConstant typeParam = params.get(i).getImplicitType(ctx);
                    if (typeParam == null)
                        {
                        break;
                        }
                    listTypes.add(typeParam);
                    }

                aParams = listTypes.toArray(new TypeConstant[cParams]);
                }
            }

        // figure out how we would translate the raw argument to a finished (RVal) argument
        return planCodeGen(ctx, arg, aParams, null, ErrorListener.BLACKHOLE);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        TypeConstant typeThis = getImplicitType(ctx);
        if (typeThis == null)
            {
            return TypeFit.NoFit;
            }

        if (typeRequired == null || typeThis.isA(typeRequired))
            {
            return pref == TuplePref.Required
                    ? TypeFit.Pack
                    : TypeFit.Fit;
            }

        if (typeThis.getConverterTo(typeRequired) != null)
            {
            return pref == TuplePref.Required
                    ? TypeFit.ConvPack
                    : TypeFit.Conv;
            }

        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid = true;

        // evaluate the left side first (we'll need it to be done before re-resolving our own raw
        // argument)
        if (left != null)
            {
            Expression leftNew = left.validate(ctx, null, TuplePref.Rejected, errs);
            if (leftNew == null)
                {
                fValid = false;
                }
            else
                {
                left = leftNew;
                }
            }

        // resolve the name to a "raw" argument, i.e. what does the name refer to, without
        // consideration to read-only vs. read-write, reference vs. de-reference, static vs.
        // virtual, and so on
        Argument argRaw = resolveRawArgument(ctx, true, errs);
        fValid &= argRaw != null;

        // validate the type parameters
        TypeConstant[] atypeParams = null;
        ConstantPool pool = pool();
        if (hasTrailingTypeParams())
            {
            int cParams = params.size();
            atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                TypeExpression exprOld = params.get(i);
                TypeExpression exprNew = (TypeExpression) exprOld.validate(
                        ctx, pool.typeType(), TuplePref.Rejected, errs);
                fValid &= exprNew != null;
                if (fValid)
                    {
                    if (exprNew != exprOld)
                        {
                        params.set(i, exprNew);
                        }
                    atypeParams[i] = exprNew.getType();
                    }
                }
            }

        if (!fValid)
            {
            // something failed previously, so we can't complete the validation
            finishValidation(TypeFit.NoFit, typeRequired, null);
            return null;
            }

        // translate the raw argument into the appropriate contextual meaning
        TypeFit      fit      = TypeFit.NoFit;
        TypeConstant type     = planCodeGen(ctx, argRaw, atypeParams, typeRequired, errs);
        Constant     constant = null;
        if (type != null)
            {
            fit = pref == TuplePref.Required
                    ? TypeFit.Pack
                    : TypeFit.Fit;

            if (typeRequired == null || type.isA(typeRequired))
                {
                switch (getMeaning())
                    {
                    case Class:
                        // class is ALWAYS a constant; it results in a ClassConstant, a
                        // PseudoConstant, a SingletonConstant, or a TypeConstant
                        switch (m_plan)
                            {
                            case None:
                                constant = (Constant) argRaw;
                                break;

                            case TypeOfClass:
                                // the class could either be identified (in the raw) by an identity
                                // constant, or a relative (pseudo) constant
                                assert argRaw instanceof IdentityConstant || argRaw instanceof PseudoConstant;
                                constant = pool.ensureTerminalTypeConstant((Constant) argRaw);
                                break;

                            case Singleton:
                                // theoretically, the singleton could be a parent of the current
                                // class, so we could have a PseudoConstant for it
                                assert argRaw instanceof IdentityConstant || argRaw instanceof PseudoConstant;
                                IdentityConstant idClass = argRaw instanceof PseudoConstant
                                        ? ((PseudoConstant) argRaw).getDeclarationLevelClass()
                                        : (IdentityConstant) argRaw;
                                constant = pool.ensureSingletonConstConstant(idClass);
                                break;

                            default:
                                throw new IllegalStateException("plan=" + m_plan);
                            }
                        break;

                    case Property:
                        // a non-constant property is ONLY a constant in identity mode; a constant
                        // property is only a constant iff the property itself has a compile-time
                        // constant
                        PropertyConstant  id   = (PropertyConstant) argRaw;
                        PropertyStructure prop = (PropertyStructure) id.getComponent();
                        if (prop.isConstant() && m_plan == Plan.PropertyDeref)
                            {
                            constant = prop.getInitialValue();
                            }
                        else if (!prop.isConstant() && m_plan == Plan.None)
                            {
                            constant = id;
                            }
                        break;
                    }
                }
            else
                {
                // look for a conversion
                MethodConstant method = type.ensureTypeInfo(errs).findConversion(typeRequired);
                if (method == null)
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            typeRequired.getValueString(), type.getValueString());
                    fit    = TypeFit.NoFit;
                    type   = typeRequired;
                    }
                else
                    {
                    // REVIEW how to standardize how conversions are done?
                    type = method.getRawReturns()[0];
                    fit  = fit.addConversion();
                    }
                }
            }

        return finishValidation(fit, type, constant);
        }

    @Override
    public boolean isAssignable()
        {
        if (m_fAssignable)
            {
            // determine assign-ability: only local variables and read/write properties are
            // assignable:
            //
            // Name          method             specifies            "static" context /    specifies
            // refers to     context            no-de-ref            identity mode         no-de-ref
            // ------------  -----------------  -------------------  ------------------    -------------------
            // Local var     T                  <- Var               T                     <- Var
            // Property      T                  <- Ref/Var           PropertyConstant*[1]  PropertyConstant*
            switch (getMeaning())
                {
                case Variable:
                    return m_plan == Plan.None;

                case Property:
                    return m_plan == Plan.PropertyDeref;
                }
            }

        return false;
        }

// REVIEW API - how to enable local-property mode
// REVIEW API - how to enable "next register" mode
// REVIEW API - how to take advantage of the "frame-local stack"
    @Override
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        Argument argRaw = m_arg;
        switch (m_plan)
            {
            case None:
                return argRaw;

            case PropertyDeref:
                if (false)  // TODO local property mode: how to know that we can return a property constant to represent the "get" of that property against "this"
                    {
                    // local property mode
                    return argRaw;
                    }
                else
                    {
                    // TODO allocate a register, do a PGET or LGET (for "this"), and return that register
                    Register reg = new Register();
                    // determine "this" (vs. some other ref
                    // code.add(new P_Get((PropertyConstant) argRaw, ));
                    return reg;
                    }

            case PropertyRef:
                return null; // TODO

            case TypeOfTypedef:
            case TypeOfClass:
            case Singleton:
                assert hasConstantValue();
                return super.generateArgument(code, fPack, errs);

            default:
                throw new IllegalStateException("arg=" + argRaw);
            }
        }


    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        if (isAssignable())
            {
            Argument arg = m_arg;
            if (arg instanceof Register)
                {
                return new Assignable((Register) arg);
                }
            else if (arg instanceof PropertyConstant)
                {
                // TODO: use getThisClass().toTypeConstant() for a type
                return new Assignable(new Register(pool().typeObject(), Op.A_TARGET), (PropertyConstant) arg);
                }
            else
                {
                return super.generateAssignable(code, errs);
                }
            }

        return null;
        }


    // ----- name resolution helpers ---------------------------------------------------------------

    /**
     * Resolve the expression to obtain a "raw" argument. Responsible for setting {@link #m_arg}.
     *
     * @param ctx     the compiler context
     * @param fForce  true to force the resolution, even if it has been done previously
     * @param errs    the error list to log errors to
     *
     * @return the raw argument, or null if it was not determinable
     */
    protected Argument resolveRawArgument(Context ctx, boolean fForce, ErrorListener errs)
        {
        if (!fForce && m_arg != null)
            {
            return m_arg;
            }

        // the first step is to resolve the name to a "raw" argument, i.e. what does the name refer
        // to, without consideration to read-only vs. read-write, reference vs. de-reference, static
        // vs. virtual, and so on
        String sName = name.getValueText();
        m_fAssignable = false;
        if (left == null)
            {
            // resolve the initial name
            Argument arg = ctx.resolveName(name, errs);
            if (arg == null)
                {
                log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                        sName, ctx.getMethod().getIdentityConstant().getSignature());
                }
            else if (arg instanceof Constant)
                {
                Constant constant = ((Constant) arg);
                switch (constant.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                    case Property:
                    case Typedef:
                        m_arg = arg;
                        break;

                    case MultiMethod:
                        // TODO log error
                        break;

                    default:
                        throw new IllegalStateException("format=" + constant.getFormat()
                                + ", constant=" + constant);
                    }
                }
            else if (arg instanceof Register)
                {
                m_fAssignable = ((Register) arg).isWritable();
                }
            }
        else
            {
            // attempt to use identity mode (e.g. "packageName.ClassName.PropName")
            boolean fValid = true;
            if (left instanceof NameExpression
                    && ((NameExpression) left).resolveRawArgument(ctx, false, errs) != null
                    && ((NameExpression) left).isIdentityMode(ctx))
                {
                // it must either be ".this" or a child of the component
                NameExpression   exprLeft = (NameExpression) left;
                IdentityConstant idLeft   = exprLeft.getIdentity(ctx);
                switch (name.getId())
                    {
                    case THIS:
                        if (ctx.isStatic())
                            {
                            // TODO log error
                            fValid = false;
                            break;
                            }

                        switch (idLeft.getFormat())
                            {
                            case Module:
                            case Package:
                            case Class:
                                // if the left is a class, then the result is a sequence of at
                                // least one (recursive) ParentClassConstant around a
                                // ThisClassConstant; from this (context) point, walk up looking
                                // for the specified class, counting the number of "parent
                                // class" steps to get there
                                PseudoConstant idRelative = exprLeft.getRelativeIdentity(ctx);
                                if (idRelative == null)
                                    {
                                    // TODO log error
                                    fValid = false;
                                    }
                                else
                                    {
                                    m_arg = idRelative;
                                    }
                                break;

                            case Property:
                                // if the left is a property, then the result is the same as if
                                // we had said "&propname", i.e. the result is a Ref/Var for the
                                // property in question (i.e. the property's "this")
                                // TODO - the property needs to be a parent of the current method, and not a constant value, and its class parent needs to be a "relative" like getRelativeIdentity()
                                // TODO - need to copy (or delegate to) the code for getting a Ref/Var for a property
                                break;

                            default:
                                throw new IllegalStateException("left=" + idLeft);
                            }
                        break;

                    case IDENTIFIER:
                        SimpleResolutionCollector collector = new SimpleResolutionCollector();
                        if (idLeft.getComponent().resolveName(sName, collector) == ResolutionResult.RESOLVED)
                            {
                            Constant constant = collector.getConstant();
                            switch (constant.getFormat())
                                {
                                case Package:
                                case Class:
                                case Property:
                                case Typedef:
                                    m_arg = constant;
                                    break;

                                case MultiMethod:
                                    // TODO log error
                                    fValid = false;
                                    break;

                                case Module:        // why an error? because it can't be nested
                                default:
                                    throw new IllegalStateException("format=" + constant.getFormat()
                                            + ", constant=" + constant);

                                }
                            }
                        break;

                    default:
                        name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                        break;
                    }
                }

            // if identity mode didn't answer the question, then use the TypeInfo to find the name
            // (e.g. "foo().x.y"
            if (fValid && m_arg == null)
                {
                // the name can refer to either a property or a typedef
                TypeConstant typeLeft = left.getImplicitType(ctx);
                if (typeLeft != null)
                    {
                    // TODO support or properties nested under something other than a class (need nested type infos?)
                    TypeInfo     infoType = typeLeft.ensureTypeInfo(errs);
                    PropertyInfo infoProp = infoType.findProperty(sName);
                    if (infoProp == null)
                        {
                        // TODO typedefs

                        name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_MISSING, sName);
                        }
                    else
                        {
                        m_arg         = infoProp.getIdentity();
                        m_fAssignable = infoProp.isVar() && !infoProp.isInjected();
                        }
                    }
                }
            }

        return m_arg;
        }

    /**
     * Determine how to transform a "raw" argument into the argument that this expression would
     * yield, if it is asked to yield an argument. Responsible for setting {@link #m_plan}.
     *
     * @param ctx          the compiler context
     * @param argRaw       the argument to translate
     * @param aTypeParams  the array of (>=0) type parameter types, or null if they are absent
     * @param typeDesired  the (optional) type to attempt to fulfill during translation
     * @param errs         the error list to log errors to
     *
     * @return
     */
    protected TypeConstant planCodeGen(
            Context ctx,
            Argument argRaw,
            TypeConstant[] aTypeParams,
            TypeConstant typeDesired,
            ErrorListener errs)
        {
        assert ctx != null && argRaw != null;

        if (argRaw instanceof Register)
            {
            Register reg = (Register) argRaw;
            if (reg.isPredefined())
                {
                // it turns out that the only thing that you can do with a predefined register is to
                // use it as a predefined register; all potential errors would have already been
                // detected and reported
                m_plan = Plan.None;
                return argRaw.getRefType();
                }


            *   Parameter     T                  <- Ref               T                     <- Ref
                    *   Local var     T                  <- Var               T                     <- Var
                    ? Meaning.Reserved
                    : Meaning.Variable;
            }

        if (argRaw instanceof Constant)
            {
            Constant constant = (Constant) argRaw;
            switch (constant.getFormat())
                {
                // class ID
                case Module:
                case Package:
                case Class:
                    // relative ID
                case ThisClass:
                case ParentClass:
                    return Meaning.Class;

                case Property:
                    return Meaning.Property;

                case Typedef:
                    return Meaning.Typedef;
                }
            }

        throw new IllegalStateException("arg=" + argRaw);

        boolean fNoDeref = isSuppressDeref();
        // TODO


        // a reserved name can not have type params
        if (m_arg instanceof Register && ((Register) m_arg).isPredefined())
            {
            // TODO log error
            fValid = false;
            }

        // validate the no-de-reference option
        if (m_arg != null && isSuppressDeref())
            {
            // TODO

            // a reserved name can not have no-de-ref
            if (m_arg instanceof Register && ((Register) m_arg).isPredefined())
                {
                // TODO log error
                fValid = false;
                }
            }


//        // resolve the name to an argument, and determine assignability
//        m_RVal = arg;
//        m_fAssignable = ctx.isVarWritable(sName); // TODO: handle properties
//
//        // validate that the expression can be of the required type
//        TypeConstant type = arg.getRefType();
//        TypeFit      fit  = TypeFit.Fit;
//        if (fValid && typeRequired != null && !type.isA(typeRequired))
//            {
//            // check if conversion in required
//            MethodConstant idMethod = type.ensureTypeInfo().findConversion(typeRequired);
//            if (idMethod == null)
//                {
//                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeRequired, arg.getRefType());
//                fValid = false;
//                }
//            else
//                {
//                // use the return value from the conversion function to figure out what type the
//                // literal should be converted to, and then do the conversion here in the
//                // compiler (eventually, once boot-strapped into Ecstasy, the compiler will be
//                // able to rely on the runtime itself to do conversions, and using containers,
//                // can even do so for user code)
//                type = idMethod.getSignature().getRawReturns()[0];
//                fit  = fit.addConversion();
//                }
//            }
//
//        if (!fValid)
//            {
//            // if there's any problem computing the type, and the expression is already invalid,
//            // then just agree to whatever was asked
//            if (typeRequired != null)
//                {
//                type = typeRequired;
//                }
//
//            fit = TypeFit.NoFit;
//            }
//        else if (pref == TuplePref.Required)
//            {
//            fit = fit.addPack();
//            }
//
//        boolean fConstant = m_RVal != null && m_RVal instanceof Constant && !m_fAssignable;
//        finishValidation(fit, type, fConstant ? (Constant) m_RVal : null);


        // Name
        // refers to   Result
        // ---------   -------------------------------------------------------------------------
        // Reserved    Argument index in the range [-0x01, -0x10]
        // Parameter   Argument index in the range [0, p), where p is the number of parameters
        // Local var   Argument index in the range >= p
        // Typedef     Argument index < -0x10 referring to TypedefConstant
        // Class       Argument index < -0x10 referring to ClassConstant
        // Property    Argument index < -0x10 referring to PropertyConstant
        // MMethod     Argument index < -0x10 referring to MultiMethodConstant

//
//                switch (((Constant) arg).getFormat())
//                    {
//                    case Module:
//                    case Package:
//                    case Class:
//                        // the trailing <params> results in a type constant
//                        type = pool().ensureParameterizedTypeConstant(
//                                ((IdentityConstant) arg).asTypeConstant(), aParams);
//                        break;
//
//                    case Typedef:
//                        if (isSuppressDeref())
//                            {
//                            // can't both provide <params> and suppress de-reference (since the
//                            // params are implicitly applied to the type as part of de-referencing
//                            return null;
//                            }
//                        else
//                            {
//                            // the typedef is just a redirect to another type
//                            type = ((TypedefConstant) arg).getReferredToType();
//
//                            // remove/replace the parameters
//                            return type.adoptParameters(aParams);
//                            }
//
//                    default:
//                        // trailing type params are not appropriate for whatever type this is
//                        return null;
//                    }
//                }
//
//            return translateType(type, ctx.isStatic(), !isSuppressDeref(), ErrorListener.BLACKHOLE);
//            }
//        else
//            {
//            TypeConstant typeLeft = left.getImplicitType(ctx);
//            if (typeLeft == null)
//                {
//                return null;
//                }
//
//            // the left hand side could be:
//            // - a reserved name (e.g. this)
//            // - a variable (including a parameter)
//            // - a property
//            // - a class
//            // - a typedef
//
//            // results in
//            // - a Ref/Var
//            // - a ClassConstant (etc.) or a PseudoConstant
//            // - a SingletonConstant
//            // - a TypedefConstant
//            // - a Property
//            // - a type
//            // - a normal reference
//            // - an error
//
//            // if
//            // TODO - we have the left side type, so figure out what the name refers to
//
//            // TODO - then apply the rules
//
//     * <p/>TODO remember ".this"
//     * <p/>TODO "construct" (placed at end of list by parser)
//            // the "arg" _is_ the context in this case
//            // REVIEW arg could represent a Ref/Var for a property, for example, so how to get the TypeInfo for _that_ property?
//            TypeConstant typeArg = arg.getRefType();
//            TypeInfo infoArg = typeArg.ensureTypeInfo(errs);
//            String       sName   = tokName.getValueText();
//
//            if (arg instanceof Register)
//                {
//                // this includes the unknown (TBD) register and actual register indexes (for parameters
//                // and local variables), and the reserved registers (for "this", etc.); the name has to
//                // be a property name (including type parameter names, and including constant value
//                // names) or a multi-method name (which includes both functions and methods) declared
//                // by the compile-time-type of the register
//                // REVIEW could it also possibly be a typedef name?
//                PropertyInfo prop = infoArg.findProperty(sName);
//                if (prop == null)
//                    {
//                    if (infoArg.containsMultiMethod(sName))
//                        {
//                        arg = new MultiMethodConstant(pool(), typeArg, sName);
//                        }
//                    }
//                else
//                    {
//                    arg = prop.getIdentity();
//                    }
//
//                }
//
//
//            }
//
//        // TODO - we have arg.getRefType() at this point, now apply the rules
        return null;
        }

    /**
     * @return the meaning of the name (after resolveRawArgument has finished), or null if it cannot be
     *         determined
     */
    protected Meaning getMeaning()
        {
        Argument arg = m_arg;
        if (arg == null)
            {
            return Meaning.Unknown;
            }

        if (arg instanceof Register)
            {
            Register reg = (Register) arg;
            return reg.isPredefined()
                    ? Meaning.Reserved
                    : Meaning.Variable;
            }

        if (arg instanceof Constant)
            {
            Constant constant = (Constant) arg;
            switch (constant.getFormat())
                {
                // class ID
                case Module:
                case Package:
                case Class:
                    // relative ID
                case ThisClass:
                case ParentClass:
                    return Meaning.Class;

                case Property:
                    return Meaning.Property;

                case Typedef:
                    return Meaning.Typedef;
                }
            }

        throw new IllegalStateException("arg=" + arg);
        }

    /**
     * @return true iff the name expression could represent a class or property identity, because
     *         either it is a simple name of a class or property, or because it augments an identity
     *         mode name expression by adding the name of a class or property
     */
    protected boolean isIdentityMode(Context ctx)
        {
        checkValidated();
        if (params == null && (left == null
                || left instanceof NameExpression && ((NameExpression) left).isIdentityMode(ctx)))
            {
            switch (getMeaning())
                {
                case Class:
                    // a class name can continue identity mode if no-de-ref is specified:
                    // Name        method             specifies            "static"            specifies
                    // refers to   context            no-de-ref            context             no-de-ref
                    // ---------   -----------------  -------------------  ------------------  -------------------
                    // Class       ClassConstant*     ClassConstant*       ClassConstant*      ClassConstant*
                    // - related   PseudoConstant*    ClassConstant*       ClassConstant*      ClassConstant*
                    // Singleton   SingletonConstant  ClassConstant*       SingletonConstant   ClassConstant*
                    // TODO this won't work for "pkg1.pkg2.ClassName" (packages are singletons)
                    return isSuppressDeref() || !((ClassStructure) getIdentity(ctx).getComponent()).isSingleton();

                case Property:
                    // a non-constant-property name can be "identity mode" if at least one of the
                    // following is true:
                    //   1) there is no left and the context is static; or
                    //   2) there is a left, and it is in identity mode;
                    //
                    // Name        method             specifies            "static"            specifies
                    // refers to   context            no-de-ref            context             no-de-ref
                    // ---------   -----------------  -------------------  ------------------  -------------------
                    // Property    T                  <- Ref/Var           Error               PropertyConstant*
                    // type param  T                  <- Ref               T                   <- Ref
                    // Constant    T                  <- Ref               T                   <- Ref
                    PropertyStructure prop = (PropertyStructure) getIdentity(ctx).getComponent();
                    return isSuppressDeref() && !prop.isConstant() && !prop.isTypeParameter() && left != null;
                }
            }

        return false;
        }

    /**
     * @return the class or property identity that the name expression indicates, iff the name
     *         expression is "identity mode"
     */
    protected IdentityConstant getIdentity(Context ctx)
        {
        return (IdentityConstant) m_arg;
        }

    /**
     * @return  the PseudoConstant representing the relationship of the parent class that this name
     *          expression refers to vis-a-vis the class containing the method for which the passed
     *          context exists
     */
    protected PseudoConstant getRelativeIdentity(Context ctx)
        {
        assert (!ctx.isStatic());
        assert isIdentityMode(ctx);

        ConstantPool     pool      = pool();
        IdentityConstant idTarget  = getIdentity(ctx);
        ClassStructure   clzParent = ctx.getMethod().getContainingClass();
        IdentityConstant idParent  = null;
        PseudoConstant   idDotThis = null;
        while (clzParent != null)
            {
            idParent  = clzParent.getIdentityConstant();
            idDotThis = idDotThis == null
                    ? pool.ensureThisClassConstant(idParent)
                    : pool.ensureParentClassConstant(idDotThis);

            if (idParent.equals(idTarget))
                {
                // found it!
                return idDotThis;
                }

            if (clzParent.isTopLevel() || clzParent.isStatic())
                {
                // can't ".this" beyond the outermost class, and can't ".this" from a static child
                return null;
                }

            clzParent = clzParent.getContainingClass();
            }

        return null;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (left != null)
            {
            sb.append(left)
              .append('.');
            }

        if (amp != null)
            {
            sb.append('&');
            }

        sb.append(name.getValueText());

        if (params != null)
            {
            sb.append('<');
            boolean first = true;
            for (Expression param : params)
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
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- inner class: SimpleResolutionCollector ------------------------------------------------

    /**
     * A simple implementation of the ResolutionCollector interface.
     */
    public static class SimpleResolutionCollector
            implements ResolutionCollector
        {
        @Override
        public ResolutionResult resolvedComponent(Component component)
            {
            m_constant  = component.getIdentityConstant();
            m_component = component;
            return ResolutionResult.RESOLVED;
            }

        @Override
        public ResolutionResult resolvedType(Constant constType)
            {
            m_constant = constType;
            return ResolutionResult.RESOLVED;
            }

        public Constant getConstant()
            {
            return m_constant;
            }

        public Component getComponent()
            {
            return m_component;
            }

        private Constant  m_constant;
        private Component m_component;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           left;
    protected Token                amp;
    protected Token                name;
    protected List<TypeExpression> params;
    protected long                 lEndPos;

    /**
     * Represents the category of argument that the expression yields.
     */
    enum Meaning {Unknown, Reserved, Variable, Property, Class, Typedef}

    /**
     * Represents the necessary argument/assignable transformation that the expression will have to
     * produce as part of compilation, if it is asked to produce an argument, an assignable, or an
     * assignment.
     */
    enum Plan {None, PropertyDeref, PropertyRef, TypeOfClass, TypeOfTypedef, Singleton}

    /**
     * Cached validation info: The raw argument that the name refers to.
     */
    private transient Argument m_arg;

    /**
     * Cached validation info: What has to be done with either the "R Value" or "L Value" in order
     * to implement the behavior implied by the name.
     */
    private transient Plan m_plan;

    /**
     * Cached validation info: Can the name be used as an "L value"?
     */
    private transient boolean m_fAssignable;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "left", "params");
    }
