package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.ParentClassConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.asm.op.L_Get;
import org.xvm.asm.op.MoveThis;
import org.xvm.asm.op.P_Get;

import org.xvm.asm.op.P_Ref;
import org.xvm.asm.op.P_Var;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A name expression specifies a name. This handles a simple name, a qualified name, a dot name
 * expression, and names with type parameters and/or suppress-de-reference symbols.
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
 * will differ based on whether the name is implicitly dereferenced, or explicitly not
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
        return name.getValueText().equals("super") || left != null && left.usesSuper();
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
     *         it has any suppressed dereference
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
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;

        // evaluate the left side first (we'll need it to be done before re-resolving our own raw
        // argument)
        if (left != null)
            {
            Expression leftNew = left.validate(ctx, null, errs);
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
                        ctx, pool.typeType(), errs);
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
            return finishValidation(typeRequired, typeRequired, TypeFit.NoFit, null, errs);
            }

        // translate the raw argument into the appropriate contextual meaning
        TypeFit      fit      = TypeFit.NoFit;
        TypeConstant type     = planCodeGen(ctx, argRaw, atypeParams, typeRequired, errs);
        Constant     constant = null;
        if (type != null)
            {
            fit = TypeFit.Fit;

            if (typeRequired == null || type.isA(typeRequired))
                {
                switch (getMeaning())
                    {
                    case Class:
                        // other than "Outer.this", class is ALWAYS a constant; it results in a
                        // ClassConstant, aPseudoConstant, a SingletonConstant, or a TypeConstant
                        switch (m_plan)
                            {
                            case None:
                                constant = (Constant) argRaw;
                                break;

                            case OuterThis:
                                // not a constant
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
            }

        // TODO the "no deref" thing is very awkward here, because we still need to force a capture,
        //      even if we are not de-referencing the variable (i.e. the markVarRead() API is wrong)
        if (left == null && !isSuppressDeref() && getParent().isRValue(this))
            {
            switch (getMeaning())
                {
                case Reserved:
                case Variable:
                    ctx.markVarRead(getNameToken(), errs);
                    break;

                case Property:
                    // "this" is used only if the property is not a constant
                    if (!((PropertyConstant) argRaw).getComponent().isStatic())
                        {
                        // there is a read of the implicit "this" variable
                        Token tokName = getNameToken();
                        long  lPos    = tokName.getStartPosition();
                        Token tokThis = new Token(lPos, lPos, Id.THIS);
                        ctx.markVarRead(tokThis, errs);
                        }
                    break;
                }
            } // TODO else account for ".this"???

        return finishValidation(typeRequired, type, fit, constant, errs);
        }

    @Override
    public boolean isAborting()
        {
        return left != null && left.isAborting();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return left != null && left.isShortCircuiting();
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
                    return m_plan == Plan.None || m_plan == Plan.RegisterDeref;

                case Property:
                    return m_plan == Plan.PropertyDeref;
                }
            }

        return false;
        }

    @Override
    public void requireAssignable(Context ctx, ErrorListener errs)
        {
        if (isAssignable())
            {
            if (left == null)
                {
                switch (getMeaning())
                    {
                    case Reserved:
                    case Variable:
                        ctx.markVarWrite(getNameToken(), errs);
                        break;

                    case Property:
                        // "this" is used only if the property is not a constant
                        if (!((PropertyConstant) resolveRawArgument(ctx, false, errs)).getComponent().isStatic())
                            {
                            // there is a read of the implicit "this" variable
                            Token tokName = getNameToken();
                            long  lPos    = tokName.getStartPosition();
                            Token tokThis = new Token(lPos, lPos, Id.THIS);
                            ctx.markVarWrite(tokThis, errs);
                            }
                        break;
                    }
                } // TODO else account for ".this"???
            }
        else
            {
            super.requireAssignable(ctx, errs);
            }
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
//        // TODO this code came from InvocationExpression; evaluate NameExpression code for necessary fixes
//            Argument argMethod = m_argMethod;
//            if (argMethod instanceof Register)
//                {
//                assert !exprName.hasSideEffects();
//                assert exprLeft == null;
//                argFn = argMethod;
//                }
//            else if (argMethod instanceof PropertyConstant)
//                {
//                assert !exprName.hasSideEffects();
//                PropertyConstant  idProp = (PropertyConstant) argMethod;
//                PropertyStructure prop   = (PropertyStructure) idProp.getComponent();
//                if (prop.isConstant())
//                    {
//                    if (prop.hasInitialValue())
//                        {
//                        argFn = prop.getInitialValue();
//                        }
//                    else
//                        {
//                        // generate code to get the value of the constant property
//                        Register regResult = new Register(prop.getType());
//                        code.add(new P_Get(idProp, Register.IGNORE, regResult));
//                        argFn = regResult;
//                        }
//                    }
//                else
//                    {
//                    Argument argTarget;
//                    if (exprLeft == null)
//                        {
//                        // use "this"
//                        MethodStructure method = code.getMethodStructure();
//                        assert !method.isFunction();
//                        argTarget = generateReserved(method.isConstructor() ? Op.A_STRUCT : Op.A_PRIVATE, errs);
//                        }
//                    else
//                        {
//                        argTarget = exprLeft.generateArgument(code, false, true, true, errs);
//                        }
//
//                    // TODO
//                    }
//                }

        Argument argRaw = m_arg;
        switch (m_plan)
            {
            case None:
                return argRaw;

            case OuterThis:
                switch (getMeaning())
                    {
                    case Class:
                        {
                        int cSteps = 0;
                        PseudoConstant idClz = (PseudoConstant) argRaw;
                        while (idClz instanceof ParentClassConstant)
                            {
                            idClz = ((ParentClassConstant) idClz).getChildClass();
                            ++cSteps;
                            }
                        assert idClz instanceof ThisClassConstant;

                        if (cSteps == 0)
                            {
                            // it's just "this" (but note that it results in the public type)
                            return generateReserved(Op.A_PUBLIC, errs);
                            }

                        Register regOuter = new Register(((PseudoConstant) argRaw).getType());
                        code.add(new MoveThis(cSteps, regOuter));
                        return regOuter;
                        }

                    case Property:
                        {
                        PropertyConstant idProp = (PropertyConstant) argRaw;
                        IdentityConstant idClz  = idProp.getClassIdentity();
                        int cSteps = 0;

                        // count the steps up to the class containing the property
                        IdentityConstant idParent = code.getMethodStructure().getIdentityConstant();
                        while (idParent.equals(idProp))
                            {
                            if (idParent.isClass())
                                {
                                ++cSteps;
                                }
                            idParent = idParent.getParentConstant();
                            }

                        Register regThis;
                        if (cSteps == 0)
                            {
                            regThis = (Register) generateReserved(Op.A_PRIVATE, errs);
                            }
                        else
                            {
                            regThis = new Register(idClz.getType());
                            code.add(new MoveThis(cSteps, regThis));
                            }

                        TypeConstant typeLeft = left == null ? null : left.getType();
                        TypeConstant type     = idProp.getRefType(typeLeft);
                        Register     regOuter = new Register(type);
                        code.add(type.isA(pool().typeVar())
                                ? new P_Var(idProp, regThis, regOuter)
                                : new P_Ref(idProp, regThis, regOuter));
                        return regOuter;
                        }

                    default:
                        throw new IllegalStateException("arg=" + argRaw);
                    }

            case PropertyDeref:
                // TODO this is not complete; the "implicit this" covers both nested properties and outer properties
                boolean fThisProp = left == null; // TODO or left == this
                if (fThisProp && fLocalPropOk)
                    {
                    // local property mode
                    return argRaw;
                    }
                else
                    {
                    Register reg = new Register(getType());
                    if (fThisProp)
                        {
                        code.add(new L_Get((PropertyConstant) argRaw, reg));
                        }
                    else
                        {
                        Argument argLeft = left.generateArgument(ctx, code, false, false, errs);
                        code.add(new P_Get((PropertyConstant) argRaw, argLeft, reg));
                        }

                    return reg;
                    }

            case PropertyRef:
            case RegisterRef:
                // TODO
                throw new UnsupportedOperationException("&" + getName());

            case TypeOfTypedef:
            case TypeOfClass:
            case Singleton:
                assert isConstant();
                return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);

            default:
                throw new IllegalStateException("arg=" + argRaw);
            }
        }

    @Override
    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs)
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
                return super.generateAssignable(ctx, code, errs);
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

        m_arg         = null;
        m_fAssignable = false;

        // the first step is to resolve the name to a "raw" argument, i.e. what does the name refer
        // to, without consideration to read-only vs. read-write, reference vs. de-reference, static
        // vs. virtual, and so on
        String sName = name.getValueText();
        if (left == null)
            {
            // resolve the initial name; avoid double-reporting by passing the BLACKHOLE errors
            Argument arg = ctx.resolveName(name, ErrorListener.BLACKHOLE);
            if (arg == null)
                {
                log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                        sName, ctx.getMethod().getIdentityConstant().getValueString());
                }
            else if (arg instanceof Constant)
                {
                Constant constant = ((Constant) arg);
                switch (constant.getFormat())
                    {
                    case Property:
                        {
                        PropertyConstant idProp = (PropertyConstant) arg;
                        ClassStructure   clzTop = (ClassStructure) idProp.getNamespace().getComponent();

                        // we will use the private access info here since the access restrictions
                        // must have been already checked by the "resolveName"
                        TypeInfo     infoClz = pool().ensureAccessTypeConstant(clzTop.getFormalType(),
                            Access.PRIVATE).ensureTypeInfo(errs);

                        PropertyInfo infoProp = infoClz.findProperty(idProp);

                        // there is a possibility that the name was found by the resolver
                        // (which is using the class structure contributions), but missing in the
                        // info, which must have already reported the contribution problem
                        if (infoProp == null)
                            {
                            log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                                    sName, ctx.getMethod().getIdentityConstant().getSignature());
                            break;
                            }

                        m_arg         = infoProp.getIdentity();
                        m_fAssignable = infoProp.isVar() && !infoProp.isInjected();
                        break;
                        }

                    case Module:
                    case Package:
                    case Class:
                    case Typedef:
                        m_arg = arg;
                        break;

                    default:
                        throw new IllegalStateException("format=" + constant.getFormat()
                                + ", constant=" + constant);
                    }
                }
            else if (arg instanceof Register)
                {
                m_arg         = arg;
                m_fAssignable = ((Register) arg).isWritable();
                }
            }
        else if (sName.equals("this"))
            {
            if (ctx.isFunction())
                {
                name.log(errs, getSource(), Severity.ERROR, Compiler.NO_THIS);
                }
            else if (left instanceof NameExpression
                    && ((NameExpression) left).resolveRawArgument(ctx, false, errs) != null)
                {
                NameExpression   exprLeft = (NameExpression) left;
                IdentityConstant idLeft   = exprLeft.getIdentity(ctx);
                switch (idLeft.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                        // the left has to be an identity mode
                        if (exprLeft.isIdentityMode(ctx, true))
                            {
                            // if the left is a class, then the result is a sequence of at
                            // least one (recursive) ParentClassConstant around a
                            // ThisClassConstant; from this (context) point, walk up looking
                            // for the specified class, counting the number of "parent
                            // class" steps to get there
                            PseudoConstant idRelative = exprLeft.getRelativeIdentity(ctx);
                            if (idRelative == null)
                                {
                                log(errs, Severity.ERROR, Compiler.MISSING_RELATIVE, sName);
                                }
                            else
                                {
                                m_arg = idRelative;
                                }
                            }
                        else
                            {
                            name.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                            }
                        break;

                    case Property:
                        // the left has to be in identity mode OR the property name does not have a
                        // left
                        if (exprLeft.isIdentityMode(ctx, true) || exprLeft.left == null)
                            {
                            // the property needs to be a parent (or grandparent, etc.) of the
                            // current method, and not a constant value, and its class parent needs
                            // to be a "relative" like getRelativeIdentity()
                            Component parent = ctx.getMethod();
                            NextParent: while (true)
                                {
                                IdentityConstant idParent = parent.getIdentityConstant();
                                switch (idParent.getFormat())
                                    {
                                    case Property:
                                        PropertyStructure prop = (PropertyStructure) parent;
                                        if (prop.isConstant() || prop.isTypeParameter())
                                            {
                                            name.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                                            break NextParent;
                                            }

                                        if (idParent.equals(idLeft))
                                            {
                                            // if the left is a property, then the result is the
                                            // same as if we had said "&propname", i.e. the result
                                            // is a Ref/Var for the property in question (i.e. the
                                            // property's "this")
                                            m_arg = idLeft;
                                            break NextParent;
                                            }
                                        break;

                                    case Class:
                                        if (!((ClassStructure) parent).isTopLevel() && !parent.isStatic())
                                            {
                                            break;
                                            }
                                        // fall through
                                    case Module:
                                    case Package:
                                        // these are top level; it's an error that we haven't
                                        // already found the property
                                        name.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                                        break NextParent;
                                    }

                                parent = parent.getParent();
                                }
                            }
                        else
                            {
                            name.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                            }
                        break;

                    default:
                        throw new IllegalStateException("left=" + idLeft);
                    }
                }
            else
                {
                name.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                }
            }
        else // left is NOT null, and it is not a ".this"
            {
            // attempt to use identity mode (e.g. "packageName.ClassName.PropName")
            boolean fValid = true;
            if (left instanceof NameExpression
                    && ((NameExpression) left).resolveRawArgument(ctx, false, errs) != null
                    && ((NameExpression) left).isIdentityMode(ctx, true))
                {
                // it must be a child of the component
                NameExpression   exprLeft  = (NameExpression) left;
                IdentityConstant idLeft    = exprLeft.getIdentity(ctx);
                SimpleCollector  collector = new SimpleCollector();
                if (idLeft.getComponent().resolveName(sName, collector) == ResolutionResult.RESOLVED)
                    {
                    Constant constant = collector.getResolvedConstant();
                    switch (constant.getFormat())
                        {
                        case Package:
                        case Class:
                        case Property:
                        case Typedef:
                            m_arg = constant;
                            break;

                        case MultiMethod:
                            log(errs, Severity.ERROR, Compiler.UNEXPECTED_METHOD_NAME, sName);
                            fValid = false;
                            break;

                        case Module:        // why an error? because it can't be nested
                        default:
                            throw new IllegalStateException("format=" + constant.getFormat()
                                    + ", constant=" + constant);
                        }
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
                    if (left instanceof NameExpression)
                        {
                        Argument arg = ((NameExpression) left).m_arg;

                        // "this:target" has private access in this context
                        // as well as a target of the same class as the context
                        if (arg instanceof Register && ((Register) arg).isTarget() ||
                                !typeLeft.isGenericType() &&
                                typeLeft.isSingleUnderlyingClass(false) &&
                                typeLeft.getSingleUnderlyingClass(false).isNestMate(
                                        ctx.getThisClass().getIdentityConstant()))
                            {
                            switch (typeLeft.getAccess())
                                {
                                case PROTECTED:
                                    typeLeft = typeLeft.getUnderlyingType();
                                    assert !typeLeft.isAccessSpecified();
                                    // fall through
                                case PUBLIC:
                                    typeLeft = pool().ensureAccessTypeConstant(typeLeft, Access.PRIVATE);
                                    break;
                                }
                            }

                        }
                    TypeInfo     infoType = typeLeft.ensureTypeInfo(errs);
                    PropertyInfo infoProp = infoType.findProperty(sName);
                    if (infoProp == null)
                        {
                        // TODO typedefs

                        name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_MISSING,
                            sName, typeLeft.getValueString());
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
     * @return the type of the expression
     */
    protected TypeConstant planCodeGen(
            Context         ctx,
            Argument        argRaw,
            TypeConstant[]  aTypeParams,
            TypeConstant    typeDesired,
            ErrorListener   errs)
        {
        assert ctx != null && argRaw != null;
        ConstantPool pool = pool();

        if (argRaw instanceof Register)
            {
            // meaning    type (de-ref)  type (no-de-ref)
            // ---------  -------------  ----------------
            // Reserved     T            n/a (already reported an Error)
            // - Virtual    T            n/a (already reported an Error)
            // Parameter    T            <- Ref
            // Local var    T            <- Var

            // validate that there are no trailing type parameters (not allowed for registers)
            if (aTypeParams != null)
                {
                log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                }

            Register reg            = (Register) argRaw;
            boolean  fSuppressDeref = isSuppressDeref();
            boolean  fAutoDeref     = reg.isImplicitDeref();
            if (fSuppressDeref & !fAutoDeref)
                {
                assert !reg.isPredefined();
                m_plan = Plan.RegisterRef;
                return pool.ensureParameterizedTypeConstant(
                        m_fAssignable ? pool.typeVar() : pool.typeRef(), reg.getType());
                }
            else if (!fSuppressDeref && fAutoDeref)
                {
                assert reg.getType().isA(pool().typeRef()) && reg.getType().getParamsCount() >= 1;
                m_plan = Plan.RegisterDeref;
                return reg.getType().getParamTypesArray()[0];
                }
            else
                {
                // use the register itself (the "T" column in the table above)
                m_plan = Plan.None;
                return reg.getType();
                }
            }

        assert argRaw instanceof Constant;
        Constant constant = (Constant) argRaw;
        switch (constant.getFormat())
            {
            case ThisClass:
                m_plan = Plan.None;
                return pool.ensureAccessTypeConstant(
                    constant.getType().adoptParameters(pool, ctx.getThisType()), Access.PRIVATE);

            case ParentClass:
                if (name.getValueText().equals("this"))
                    {
                    assert left instanceof NameExpression;
                    m_plan = Plan.OuterThis;
                    return pool.ensureAccessTypeConstant(
                        constant.getType().adoptParameters(pool, ctx.getThisType()), Access.PRIVATE);
                    }
                // fall through
            // class ID
            case Module:
            case Package:
            case Class:
                // handle the SingletonConstant use cases
                if (!isIdentityMode(ctx, false))
                    {
                    if (aTypeParams != null)
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                        }

                    m_plan = Plan.Singleton;
                    return pool.ensureTerminalTypeConstant(constant);
                    }

                // determine the type of the class
                if (aTypeParams != null || (typeDesired != null && typeDesired.isA(pool.typeType())))
                    {
                    TypeConstant type = pool.ensureTerminalTypeConstant(constant);
                    if (aTypeParams != null)
                        {
                        type = pool.ensureParameterizedTypeConstant(type, aTypeParams);
                        }

                    m_plan = Plan.TypeOfClass;
                    return pool.ensureParameterizedTypeConstant(pool.typeType(), type);
                    }
                else
                    {
                    m_plan = Plan.None;

                    ClassStructure   clazz     = ctx.getThisClass();
                    IdentityConstant idClass   = clazz.getIdentityConstant();
                    TypeConstant     typeClass = clazz.getFormalType();

                    return constant instanceof PseudoConstant
                            ? ((PseudoConstant) constant).resolveClass(idClass).getRefType(typeClass)
                            : ((IdentityConstant) constant).getRefType(typeClass);
                    }

            case Property:
                {
                if (aTypeParams != null)
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    }

                if (name.getValueText().equals("this"))
                    {
                    assert left instanceof NameExpression;
                    m_plan = Plan.OuterThis;
                    return ((PropertyConstant) constant).getRefType(left.getType());
                    }

                PropertyConstant  id   = (PropertyConstant) argRaw;
                PropertyStructure prop = (PropertyStructure) id.getComponent();
                TypeConstant      type = prop.getType();

                // resolve the property type
                if (left == null)
                    {
                    ClassStructure clz = ctx.getThisClass();
                    if (clz != prop.getParent())
                        {
                        PropertyInfo infoProp = clz.getFormalType().
                                ensureTypeInfo().findProperty(id);
                        if (infoProp != null)
                            {
                            type = infoProp.getType();
                            }
                        }
                    }
                else
                    {
                    PropertyInfo infoProp = left.getImplicitType(ctx).
                            ensureTypeInfo().findProperty(id);
                    if (infoProp != null)
                        {
                        type = infoProp.getType();
                        }
                    }

                if (!prop.isConstant() && isIdentityMode(ctx, false))
                    {
                    m_plan = Plan.None;
                    // TODO parameterized type of Property<TargetType, PropertyType>
                    return pool.typeProperty();
                    }

                if (isSuppressDeref())
                    {
                    m_plan = Plan.PropertyRef;
                    return pool.ensureParameterizedTypeConstant(
                            m_fAssignable ? pool.typeVar() : pool.typeRef(), type);
                    }
                else
                    {
                    m_plan = Plan.PropertyDeref;
                    return type;
                    }
                }
            case Typedef:
                if (aTypeParams != null)
                    {
                    // TODO have to incorporate type params
                    notImplemented();
                    }

                m_plan = Plan.TypeOfTypedef;
                TypeConstant typeRef = ((TypedefConstant) constant).getReferredToType();
                return pool.ensureParameterizedTypeConstant(
                        pool.typeType(), typeRef.adoptParameters(pool, ctx.getThisType()));

            default:
                throw new IllegalStateException("constant=" + constant);
            }
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
     * REVIEW: the "soft" concept is ugly; Cam to review
     * @param fSoft  if true, allow this expression to return true if the name expression could
     *               potentially represent a class identity (e.g. package or module)
     *
     * @return true iff the name expression could represent a class or property identity, because
     *         either it is a simple name of a class or property, or because it augments an identity
     *         mode name expression by adding the name of a class or property
     */
    protected boolean isIdentityMode(Context ctx, boolean fSoft)
        {
        // identity mode requires the left side to be absent or to be a name expression
        if (left != null && !(left instanceof NameExpression))
            {
            return false;
            }

        switch (getMeaning())
            {
            case Class:
                {
                // a class name can continue identity mode if no-de-ref is specified:
                // Name        method             specifies            "static"            specifies
                // refers to   context            no-de-ref            context             no-de-ref
                // ---------   -----------------  -------------------  ------------------  -------------------
                // Class       ClassConstant*     ClassConstant*       ClassConstant*      ClassConstant*
                // - related   PseudoConstant*    ClassConstant*       ClassConstant*      ClassConstant*
                // Singleton   SingletonConstant  ClassConstant*       SingletonConstant   ClassConstant*

                if (left != null)
                    {
                    // 1) the "left" NameExpression must be identity mode, and
                    // 2) this NameExpression must NOT be ".this"
                    if (!((NameExpression) left).isIdentityMode(ctx, true) || name.getValueText().equals("this"))
                        {
                        return false;
                        }
                    }

                if (fSoft)
                    {
                    IdentityConstant id = getIdentity(ctx);
                    if (id instanceof ModuleConstant || id instanceof PackageConstant)
                        {
                        return true;
                        }
                    }

                return isSuppressDeref() || !((ClassStructure) getIdentity(ctx).getComponent()).isSingleton();
                }

            case Property:
                {
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

                if (name.getValueText().equals("this"))
                    {
                    // "propname.this" is legal, as long as we're on code somewhere nested inside of
                    // "propname", and we can get a "this" for the class that contains it
                    return left instanceof NameExpression && ((NameExpression) left).left == null;   // TODO grab checks from below?
                    }

                return isSuppressDeref() && !prop.isConstant() && !prop.isTypeParameter()
                    && left != null && ((NameExpression) left).isIdentityMode(ctx, true);
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
        assert (!ctx.isFunction());
        assert isIdentityMode(ctx, false);

        ConstantPool     pool      = pool();
        IdentityConstant idTarget  = getIdentity(ctx);
        ClassStructure   clzParent = ctx.getMethod().getContainingClass();
        PseudoConstant   idDotThis = null;
        while (clzParent != null)
            {
            IdentityConstant idParent = clzParent.getIdentityConstant();
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
            if (left instanceof NameExpression)
                {
                sb.append(left);
                }
            else
                {
                sb.append('(').append(left).append(')');
                }
            sb.append('.');
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

    /**
     * Represents the category of argument that the expression yields.
     */
    enum Meaning {Unknown, Reserved, Variable, Property, Class, Typedef}

    /**
     * Represents the necessary argument/assignable transformation that the expression will have to
     * produce as part of compilation, if it is asked to produce an argument, an assignable, or an
     * assignment.
     */
    enum Plan {None, OuterThis, RegisterDeref, RegisterRef, PropertyDeref, PropertyRef, TypeOfClass, TypeOfTypedef, Singleton}

    /**
     * Cached validation info: The raw argument that the name refers to.
     */
    private transient Argument m_arg;

    /**
     * Cached validation info: What has to be done with either the "R Value" or "L Value" in order
     * to implement the behavior implied by the name.
     */
    private transient Plan m_plan = Plan.None;

    /**
     * Cached validation info: Can the name be used as an "L value"?
     */
    private transient boolean m_fAssignable;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "left", "params");
    }
