package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

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
 *   Name        method             specifies            "static"            specifies
 *   refers to   context            no-de-ref            context             no-de-ref
 *   ---------   -----------------  -------------------  ------------------  -------------------
 *   Reserved    T                  Error                T                   Error
 *   - Virtual   T                  Error                Error               Error
 *
 *   Parameter   T                  <- Ref               T                   <- Ref
 *   Local var   T                  <- Var               T                   <- Var
 *
 *   Property    T                  <- Ref/Var           Property            Error
 *   Constant    T                  Error                T                   Error
 *
 *   Class       ClassConstant      Error                ClassConstant       Error
 *   - related   PseudoConstant     Error                ClassConstant       Error
 *   Singleton   SingletonConstant  ClassConstant        SingletonConstant   ClassConstant
 *
 *   Typedef     Type<..>           TypedefConstant      Type                TypedefConstant
 *
 *   MMethod     MMethod            MMethod -1           MMethod (static)    MMethod -1 (static)
 * </pre></code>
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

    // TODO
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

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant type;
        if (left == null)
            {
            Argument arg = ctx.resolveName(name, ErrorListener.BLACKHOLE);
            if (arg == null)
                {
                return null;
                }

            type = arg.getRefType();

            // apply the "trailing type parameters"
            List<TypeExpression> params = getTrailingTypeParams();
            if (params != null)
                {
                // the arg must be a class or a typedef
                if (!(arg instanceof Constant))
                    {
                    return null;
                    }

                int            cParams = params.size();
                TypeConstant[] aParams = new TypeConstant[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    TypeConstant typeParam = params.get(i).getImplicitType(ctx);
                    if (typeParam == null)
                        {
                        return null;
                        }
                    aParams[i] = typeParam;
                    }

                switch (((Constant) arg).getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                        // the trailing <params> results in a type constant
                        type = pool().ensureParameterizedTypeConstant(
                                ((IdentityConstant) arg).asTypeConstant(), aParams);
                        break;

                    case Typedef:
                        if (isSuppressDeref())
                            {
                            // can't both provide <params> and suppress de-reference (since the
                            // params are implicitly applied to the type as part of de-referencing
                            return null;
                            }
                        else
                            {
                            // the typedef is just a redirect to another type
                            type = ((TypedefConstant) arg).getReferredToType();

                            // remove/replace the parameters
                            return type.adoptParameters(aParams);
                            }

                    default:
                        // trailing type params are not appropriate for whatever type this is
                        return null;
                    }
                }

            return translateType(type, ctx.isStatic(), !isSuppressDeref(), ErrorListener.BLACKHOLE);
            }
        else
            {
            TypeConstant typeLeft = left.getImplicitType(ctx);
            if (typeLeft == null)
                {
                return null;
                }

            // the left hand side could be:
            // - a reserved name (e.g. this)
            // - a variable (including a parameter)
            // - a property
            // - a class
            // - a typedef

            // results in
            // - a Ref/Var
            // - a ClassConstant (etc.) or a PseudoConstant
            // - a SingletonConstant
            // - a TypedefConstant
            // - a Property
            // - a type
            // - a normal reference
            // - an error

            // if
            // TODO - we have the left side type, so figure out what the name refers to

            // TODO - then apply the rules

                /*
     * <p/>TODO remember ".this"
     * <p/>TODO "construct" (placed at end of list by parser)
    */
            // the "arg" _is_ the context in this case
            // REVIEW arg could represent a Ref/Var for a property, for example, so how to get the TypeInfo for _that_ property?
            TypeConstant typeArg = arg.getRefType();
            TypeInfo infoArg = typeArg.ensureTypeInfo(errs);
            String       sName   = tokName.getValueText();

            if (arg instanceof Register)
                {
                // this includes the unknown (TBD) register and actual register indexes (for parameters
                // and local variables), and the reserved registers (for "this", etc.); the name has to
                // be a property name (including type parameter names, and including constant value
                // names) or a multi-method name (which includes both functions and methods) declared
                // by the compile-time-type of the register
                // REVIEW could it also possibly be a typedef name?
                PropertyInfo prop = infoArg.findProperty(sName);
                if (prop == null)
                    {
                    if (infoArg.containsMultiMethod(sName))
                        {
                        arg = new MultiMethodConstant(pool(), typeArg, sName);
                        }
                    }
                else
                    {
                    arg = prop.getIdentity();
                    }

                }


            }

        // TODO - we have arg.getRefType() at this point, now apply the rules
        return null;
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

        // the first step is to resolve the name to a "raw" argument, i.e. what does the name refer
        // to, without consideration to read-only vs. read-write, reference vs. de-reference, static
        // vs. virtual, and so on
        String sName = name.getValueText();
        if (left == null)
            {
            // resolve the initial name
            Argument arg = ctx.resolveName(name, errs);
            if (arg == null)
                {
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.NAME_MISSING,
                        sName, ctx.getMethod().getIdentityConstant().getSignature());
                fValid = false;
                }
            else if (arg instanceof Register)
                {
                Register reg = (Register) arg;
                if (reg.isUnknown() || reg.getIndex() >= 0)
                    {
                    m_meaning = Meaning.Variable;
                    }
                else
                    {
                    m_meaning = Meaning.Reserved;
                    }
                m_arg = reg;
                }
            else
                {
                Constant constant = (Constant) arg;
                switch (constant.getFormat())
                    {
                    case Class:
                    case Package:
                    case Module:
                        m_meaning = Meaning.Class;
                        break;

                    case Property:
                        m_meaning = Meaning.Property;
                        break;

                    case Typedef:
                        m_meaning = Meaning.Typedef;
                        break;

                    default:
                        throw new IllegalStateException("format=" + constant.getFormat()
                                + ", constant=" + constant);
                    }
                m_arg = constant;
                }
            }
        else
            {
            // a name expression may have a "left hand side", which means that the name is being used
            // as a "dot name" expression
            Expression leftNew = left.validate(ctx, null, TuplePref.Rejected, errs);
            if (leftNew == null)
                {
                // there was an error on the left hand side, so it will be impossible to determine
                // the meaning of this name
                fValid = false;
                }
            else
                {
                left = leftNew;

                // when there is a "left hand side", it is possible that this name is a continuation
                // of the name on the left hand side, which is called the "identity mode".

                if (leftNew instanceof NameExpression && ((NameExpression) leftNew).isIdentityMode())
                    {
                    NameExpression leftName = (NameExpression) leftNew;
                    switch (leftName.m_meaning)
                        {
                        case Reserved:
                        case Variable:
                        case Class:
                        case Property:
                        case Typedef:
                            // TODO
                            break;

                        default:
                            throw new IllegalStateException();
                        }
                    }

                // obtain a component from the left-hand-side, if that is possible


                // consider that the left-hand-side could be part of a qualified name that this
                // expression is a further part of, or the left-hand-side could be a constant value


                // use the left-hand-side to resolve the name
                if (left.hasConstantValue())
                    {
                    // TODO module, package, class, pseudo, property
                    }

                // the left hand side could be:
                // - a reserved name (e.g. this)
                // - a variable (including a parameter)
                // - a property
                // - a class
                // - a typedef

                // identi
                // TODO
                }
            }

        // a name expression may have type params from the construct:
        //      QualifiedNameName TypeParameterTypeList-opt
        ConstantPool pool = pool();
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

        switch ()
        // TODO if it's a reserved name, then neither no-de-ref nor params can be specified

        if (!fValid)
            {
            // something failed previously, so we can't complete the validation
            finishValidation(TypeFit.NoFit, typeRequired, null);
            return null;
            }

        // translate the argument that we found by that name into the appropriate contextual meaning
        arg = translateArg(arg, ctx.isStatic(), !isSuppressDeref(), errs);

        // check required type
        // TODO


        // TODO here down

        // resolve the name to an argument, and determine assignability
        m_RVal = arg;
        m_fAssignable = ctx.isVarWritable(sName); // TODO: handle properties

        // validate that the expression can be of the required type
        TypeConstant type = arg.getRefType();
        TypeFit      fit  = TypeFit.Fit;
        if (fValid && typeRequired != null && !type.isA(typeRequired))
            {
            // check if conversion in required
            MethodConstant idMethod = type.ensureTypeInfo().findConversion(typeRequired);
            if (idMethod == null)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeRequired, arg.getRefType());
                fValid = false;
                }
            else
                {
                // use the return value from the conversion function to figure out what type the
                // literal should be converted to, and then do the conversion here in the
                // compiler (eventually, once boot-strapped into Ecstasy, the compiler will be
                // able to rely on the runtime itself to do conversions, and using containers,
                // can even do so for user code)
                type = idMethod.getSignature().getRawReturns()[0];
                fit  = fit.addConversion();
                }
            }

        if (!fValid)
            {
            // if there's any problem computing the type, and the expression is already invalid,
            // then just agree to whatever was asked
            if (typeRequired != null)
                {
                type = typeRequired;
                }

            fit = TypeFit.NoFit;
            }
        else if (pref == TuplePref.Required)
            {
            fit = fit.addPack();
            }

        boolean fConstant = m_RVal != null && m_RVal instanceof Constant && !m_fAssignable;
        finishValidation(fit, type, fConstant ? (Constant) m_RVal : null);

        return fValid
                ? this
                : null;
        }

    @Override
    public boolean isAssignable()
        {
        return m_fAssignable;
        }

    @Override
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        return m_RVal == null
                ? generateBlackHole(getType())
                : m_RVal;
        }


    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        Assignable LVal = m_LVal;
        if (LVal == null && isAssignable())
            {
            if (m_RVal instanceof Register)
                {
                LVal = new Assignable((Register) m_RVal);
                }
            else if (m_RVal instanceof PropertyConstant)
                {
                // TODO: use getThisClass().toTypeConstant() for a type
                LVal = new Assignable(
                    new Register(pool().typeObject(), Op.A_TARGET),
                    (PropertyConstant) m_RVal);
                }
            else
                {
                LVal = super.generateAssignable(code, errs);
                }
            m_LVal = LVal;
            }

        return LVal;
        }


    // ----- name resolution helpers ---------------------------------------------------------------

    boolean isIdentityMode()
        {
        // TODO this is wrong; see table below
        return (m_meaning == Meaning.Class || m_meaning == Meaning.Property)
                && (left == null || left instanceof NameExpression && ((NameExpression) left).isIdentityMode());
        }

    /**
     * Translate TODO
     *
     * There is a natural inconsistency here, because an identity can theoretically be used on the
     * left-hand-side to mean either the class or the singleton. For example, with the "Person"
     * class having a property "name", "Person.name" is ambiguous in that it could refer to the
     * "name" property on the "Class" class, or to the "name" property on the "Person" class, but
     * by convention, the ambiguity is decided by always looking first at Person, and only on a miss
     * looking at Class.
     *
     * <p/>
     * <code><pre>
     *   Name        method             specifies            "static"            specifies
     *   refers to   context            no-de-ref            context             no-de-ref
     *   ---------   -----------------  -------------------  ------------------  -------------------
     *   Reserved    T                  Error                T                   Error
     *   - Virtual   T                  Error                Error               Error
     *
     *   Parameter   T                  <- Ref               T                   <- Ref
     *   Local var   T                  <- Var               T                   <- Var
     *
     *   Property    T                  <- Ref/Var           PropertyConstant*   PropertyConstant*
     *   Constant    T                  <- Ref/Var           T                   PropertyConstant*
     *
     *   Class       ClassConstant*     ClassConstant*       ClassConstant*      ClassConstant*
     *   - related   PseudoConstant     ClassConstant*       ClassConstant*      ClassConstant*
     *   Singleton   SingletonConstant  ClassConstant*       SingletonConstant   ClassConstant*
     *
     *   Typedef     Type<..>           TypedefConstant      Type                TypedefConstant
     *
     *   MMethod     Error              Error                Error               Error
     * </pre></code>
     * Note: '*' signifies "identity mode"
     *
     * @param type     the raw type being translated, or null
     * @param fStatic  true if the context within which the type is being translated is necessarily
     *                 static (i.e. "this" is not available)
     * @param fDeref   true if the type is being de-referenced; false if the de-reference is being
     *                 explicitly suppressed
     * @param errs     the error list to log errors to
     *
     * @return the translated type, or null
     */
    TypeConstant translateType(TypeConstant type, boolean fStatic, boolean fDeref, ErrorListener errs)
        {
        if (type == null)
            {
            return null;
            }

        // TODO
        // if (type.isClass())

        // ClassConstant
        // - if refers to singleton, then SingletonConstant
        // - if virtual and refers to a "virtually related class", then PseudoConstant

        // PropertyConstant
        // - if virtual OR the property is a constant, then resolve to type of property
        // - if not virtual and not constant, then stays the PropertyConstant

        // Variable
        // - type of variable

        // Reserved
        //
        // - type of reserved name
        return null;
        }

    Argument translateArg(Argument arg, boolean fStatic, boolean fDeref, ErrorListener errs)
        {
        // TODO
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


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           left;
    protected Token                amp;
    protected Token                name;
    protected List<TypeExpression> params;
    protected long                 lEndPos;

    // cached validation info
    enum Meaning {Reserved, Variable, Property, Class, Typedef}
    private Meaning    m_meaning;
    private Argument   m_arg;
    private Argument   m_RVal;
    private boolean    m_fAssignable;
    private Assignable m_LVal;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "left", "params");
    }
