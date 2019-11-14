package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.ParentClassConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;
import org.xvm.asm.constants.TypeParameterConstant;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.LabeledStatement.LabelVar;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import org.xvm.util.ListMap;
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
     * This constructor is used to implement a "simple name" expression.
     *
     * @param name  the (required) name
     */
    NameExpression(AstNode parent, Token name, Register reg)
        {
        this(name);

        if (reg != null)
            {
            m_plan        = Plan.None;
            m_arg         = reg;
            m_fAssignable = reg.isWritable();
            }

        parent.adopt(this);
        setStage(parent.getStage());
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
        return isSuppressDeref() ||
            left instanceof NameExpression && ((NameExpression) left).hasAnySuppressDeref();
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
        NameExpressions:
        if (left instanceof NameExpression)
            {
            // we're going to split a chain of name expression (A).(B<T>).(C).(D<U>) into
            // a chain of NamedTypeExpressions (A.B<T>).(C.D<U>)
            List<Token> tokens = new ArrayList<>();
            tokens.add(name);

            // moving to the left, find any previous parameterized named expression
            NameExpression exprLeft = (NameExpression) left;
            NameExpression exprPrev;

            while (true)
                {
                if (exprLeft.params != null)
                    {
                    exprPrev = exprLeft;
                    break;
                    }

                tokens.add(0, exprLeft.name);

                Expression expr = exprLeft.left;
                if (!(expr instanceof NameExpression))
                    {
                    // if expr is null, there is no left named type and therefore no reason to split
                    // this into a chain of NamedTypeExpressions;
                    // otherwise this should be an error and if will be reported later
                    break NameExpressions;
                    }
                exprLeft = (NameExpression) expr;
                }

            NamedTypeExpression exprLeftType = (NamedTypeExpression) exprPrev.toTypeExpression();

            return new NamedTypeExpression(exprLeftType, tokens, params, lEndPos);
            }

        return new NamedTypeExpression(null, getNameTokens(), null, null, params, lEndPos);
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- LValue methods ------------------------------------------------------------------------

    @Override
    public boolean isLValueSyntax()
        {
        return true;
        }

    @Override
    public Expression getLValueExpression()
        {
        return this;
        }

    @Override
    public void updateLValueFromRValueTypes(Context ctx, TypeConstant[] aTypes)
        {
        assert aTypes != null && aTypes.length >= 1;

        TypeConstant typeThis = getType();
        TypeConstant typeNew  = aTypes[0];
        Argument     arg      = ctx.getVar(getName());
        TypeConstant typeOld  = arg == null ? typeThis : arg.getType();

        if (!typeOld.equals(typeNew))
            {
            typeNew = typeNew.combine(pool(), typeThis);

            narrowType(ctx, Context.Branch.Always, typeNew);
            }
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (isValidated())
            {
            return getType();
            }

        Argument arg = resolveRawArgument(ctx, true, ErrorListener.BLACKHOLE);
        if (arg == null)
            {
            // we need the "raw argument" to determine the type from
            return null;
            }

        // figure out how we would translate the raw argument to a finished (RVal) argument
        return planCodeGen(ctx, arg,
                getImplicitTrailingTypeParameters(ctx), null, ErrorListener.BLACKHOLE);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        checkDepth();

        if (typeRequired == null)
            {
            return TypeFit.Fit;
            }

        if (errs == null)
            {
            errs = ErrorListener.BLACKHOLE;
            }

        Argument arg = resolveRawArgument(ctx, true, errs);
        if (arg == null)
            {
            return TypeFit.NoFit;
            }

        TypeConstant typeActual = planCodeGen(ctx, arg,
                getImplicitTrailingTypeParameters(ctx), typeRequired, errs);
        return calcFit(ctx, typeActual, typeRequired);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // evaluate the left side first (we'll need it to be done before re-resolving our own raw
        // argument)
        if (left != null)
            {
            Expression leftNew = left.validate(ctx, null, errs);
            if (leftNew == null)
                {
                // we couldn't resolve the left side; no reason to continue
                return finishValidation(typeRequired, typeRequired, TypeFit.NoFit, null, errs);
                }
            left = leftNew;
            }

        // resolve the name to a "raw" argument, i.e. what does the name refer to, without
        // consideration to read-only vs. read-write, reference vs. de-reference, static vs.
        // virtual, and so on
        Argument argRaw = resolveRawArgument(ctx, true, errs);
        boolean  fValid = argRaw != null;

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
                    atypeParams[i] = exprNew.ensureTypeConstant();
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
        Constant     constVal = null;
        if (type != null)
            {
            fit = TypeFit.Fit;

            if (typeRequired == null || type.isAssignableTo(typeRequired))
                {
                switch (getMeaning())
                    {
                    case Class:
                        // other than "Outer.this", class is ALWAYS a constant; it results in a
                        // ClassConstant, a PseudoConstant, a SingletonConstant, or a TypeConstant
                        switch (m_plan)
                            {
                            case None:
                                constVal = (Constant) argRaw;
                                break;

                            case OuterThis:
                            case OuterRef:
                                // not a constant
                                break;

                            case Singleton:
                                // theoretically, the singleton could be a parent of the current
                                // class, so we could have a PseudoConstant for it
                                assert argRaw instanceof IdentityConstant || argRaw instanceof PseudoConstant;
                                IdentityConstant idClass = argRaw instanceof PseudoConstant
                                        ? ((PseudoConstant) argRaw).getDeclarationLevelClass()
                                        : (IdentityConstant) argRaw;
                                constVal = pool.ensureSingletonConstConstant(idClass);
                                break;

                            default:
                                throw new IllegalStateException("plan=" + m_plan);
                            }
                        break;

                    case Type:
                        // the class could either be identified (in the raw) by an identity
                        // constant, or a relative (pseudo) constant
                        assert argRaw instanceof IdentityConstant || argRaw instanceof PseudoConstant;
                        constVal = type;
                        break;

                    case Property:
                        // a non-constant property is ONLY a constant in identity mode; a constant
                        // property is only a constant iff the property itself has a compile-time
                        // constant
                        PropertyConstant  id   = (PropertyConstant) argRaw;
                        PropertyStructure prop = (PropertyStructure) id.getComponent();
                        if (prop.isConstant())
                            {
                            if (m_plan == Plan.Singleton || m_plan == Plan.PropertyDeref)
                                {
                                constVal = prop.hasInitialValue()
                                        ? prop.getInitialValue()
                                        : pool.ensureSingletonConstConstant(id);
                                }
                            }
                        else if (m_plan == Plan.None)
                            {
                            constVal = id;
                            }
                        break;
                    }
                }
            }

        // TODO the "no deref" thing is very awkward here, because we still need to force a capture,
        //      even if we are not de-referencing the variable (i.e. the markVarRead() API is wrong)
        if (left == null && !isSuppressDeref() && isRValue())
            {
            switch (getMeaning())
                {
                case Reserved:
                case Variable:
                    ctx.markVarRead(getNameToken(), errs);
                    if (type.isGenericType())
                        {
                        PropertyConstant idProp = (PropertyConstant) type.getDefiningConstant();
                        ctx.useGenericType(idProp.getName(), errs);
                        }
                    break;

                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) argRaw;

                    if (idProp.isFormalType())
                        {
                        ctx.useGenericType(getName(), errs);
                        }
                    else if (!idProp.getComponent().isStatic())
                        {
                        // there is a read of the implicit "this" variable
                        ctx.requireThis(getStartPosition(), errs);
                        }
                    break;
                    }
                }
            } // TODO else account for ".this"???

        if (left instanceof NameExpression && ((NameExpression) left).getMeaning() == Meaning.Label)
            {
            LabelVar labelVar = (LabelVar) ctx.getVar(((NameExpression) left).getNameToken(), errs);
            String   sVar     = getName();
            if (labelVar.isPropReadable(sVar))
                {
                labelVar.markPropRead(sVar);
                }
            else
                {
                String sLabel = ((NameExpression) left).getName();
                log(errs, Severity.ERROR, Compiler.LABEL_VARIABLE_ILLEGAL, sVar, sLabel);
                return finishValidation(typeRequired, null, TypeFit.NoFit, null, errs);
                }
            }

        return finishValidation(typeRequired, type, fit, constVal, errs);
        }

    @Override
    public boolean isCompletable()
        {
        return left == null || left.isCompletable();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return left != null && left.isShortCircuiting();
        }

    @Override
    public boolean isTraceworthy()
        {
        if (!isCompletable())
            {
            return false;
            }

        switch (getMeaning())
            {
            case Variable:
            case Property:
            case FormalChildType:
                return true;

            case Reserved: // TODO - some of these are traceworthy, right?
            default:
            case Unknown:
            case Method:
            case Class:
            case Typedef:
            case Label:
                return false;
            }
        }

    @Override
    public boolean isAssignable(Context ctx)
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

    @Override
    public void requireAssignable(Context ctx, ErrorListener errs)
        {
        if (isAssignable(ctx))
            {
            if (left == null)
                {
                switch (getMeaning())
                    {
                    case Reserved:
                    case Variable:
                        // this is assignable
                        return;

                    case Property:
                        // "this" is used only if the property is not a constant
                        if (!((PropertyConstant) m_arg).getComponent().isStatic())
                            {
                            // there is a read of the implicit "this" variable
                            ctx.requireThis(getStartPosition(), errs);
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
    public void markAssignment(Context ctx, boolean fCond, ErrorListener errs)
        {
        if (isAssignable(ctx))
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
                        break;
                    }
                }
            }
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        // constants are processed by generateArgument() method
        if (LVal.isLocalArgument() && !isConstant())
            {
            // optimize the code for a couple of paths (see symmetrical logic at generateArgument())
            Argument argLVal = LVal.getLocalArgument();
            Argument argRaw  = m_arg;

            switch (m_plan)
                {
                case OuterThis:
                    {
                    assert getMeaning() == Meaning.Class;

                    // TODO: the instanceof ParenClassConstant check will go away (p.this for property p on Map will become this.Map.&p)
                    if (argRaw instanceof ParentClassConstant)
                        {
                        int cSteps = ((ParentClassConstant) argRaw).getDepth();

                        code.add(new MoveThis(cSteps, argLVal));
                        return;
                        }

                    if (argRaw instanceof TypeConstant)
                        {
                        int cSteps = m_targetInfo.getStepsOut();

                        code.add(new MoveThis(cSteps, argLVal));
                        return;
                        }
                    break;
                    }

                case OuterRef:
                    {
                    assert getMeaning() == Meaning.Class;

                    int cSteps = argRaw instanceof ThisClassConstant
                            ? 0
                            : ((ParentClassConstant) argRaw).getDepth();

                    TypeConstant typeRef      = getType();
                    TypeConstant typeReferent = typeRef.getParamType(0);

                    Register regTemp = createRegister(typeReferent, false);
                    code.add(new MoveThis(cSteps, regTemp));
                    code.add(new MoveRef(regTemp, argLVal));
                    return;
                    }

                case PropertyDeref:
                    {
                    if (left instanceof NameExpression &&
                            ((NameExpression) left).getMeaning() == Meaning.Label)
                        {
                        break;
                        }

                    if (!LVal.supportsLocalPropMode())
                        {
                        // local property mode
                        break;
                        }

                    PropertyConstant idProp = (PropertyConstant) argRaw;
                    switch (calculatePropertyAccess())
                        {
                        case SingletonParent:
                            {
                            IdentityConstant  idParent    = idProp.getParentConstant();
                            SingletonConstant idSingleton = pool().ensureSingletonConstConstant(idParent);
                            code.add(new P_Get(idProp, idSingleton, argLVal));
                            break;
                            }

                        case Outer:
                            {
                            int      cSteps   = m_targetInfo.getStepsOut();
                            Register regOuter = createRegister(m_targetInfo.getType(), true);
                            code.add(new MoveThis(cSteps, regOuter));
                            code.add(new P_Get(idProp, regOuter, argLVal));
                            break;
                            }

                        case This:
                            code.add(new L_Get(idProp, argLVal));
                            break;

                        case Left:
                            {
                            assert !idProp.getComponent().isStatic();
                            Argument argLeft = left.generateArgument(ctx, code, false, true, errs);
                            code.add(new P_Get(idProp, argLeft, argLVal));
                            break;
                            }
                        }
                    return;
                    }

                case PropertyRef:
                    {
                    PropertyConstant idProp    = (PropertyConstant) argRaw;
                    Argument         argTarget = left == null
                            ? new Register(ctx.getThisType(), Op.A_TARGET)
                            : left.generateArgument(ctx, code, true, true, errs);

                    code.add(m_fAssignable
                            ? new P_Var(idProp, argTarget, argLVal)
                            : new P_Ref(idProp, argTarget, argLVal));
                    return;
                    }

                case RegisterRef:
                    {
                    Register regRVal = (Register) argRaw;

                    code.add(m_fAssignable
                            ? new MoveVar(regRVal, argLVal)
                            : new MoveRef(regRVal, argLVal));
                    return;
                    }

                case BindTarget:
                    {
                    MethodConstant idMethod  = (MethodConstant) argRaw;
                    Argument       argTarget = left == null
                            ? new Register(ctx.getThisType(), Op.A_TARGET)
                            : left.generateArgument(ctx, code, true, true, errs);

                    if (m_mapTypeParams == null)
                        {
                        code.add(new MBind(argTarget, idMethod, argLVal));
                        }
                    else
                        {
                        Register regFn = createRegister(pool().typeFunction(), true);
                        code.add(new MBind(argTarget, idMethod, regFn));
                        bindTypeParameters(ctx, code, regFn, argLVal);
                        }
                    return;
                    }
                }
            }

        super.generateAssignment(ctx, code, LVal, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        Argument argRaw = m_arg;
        switch (m_plan)
            {
            case None:
                assert getMeaning() != Meaning.Label;

                if (m_mapTypeParams != null)
                    {
                    Register regFn = createRegister(argRaw.getType(), fUsedOnce);
                    bindTypeParameters(ctx, code, argRaw, regFn);
                    return regFn;
                    }
                return argRaw;

            case OuterThis:
                {
                // TODO: this scenario will go away (p.this for property p on Map will become this.Map.&p)
                if (argRaw instanceof ThisClassConstant)
                    {
                    // it's just "this" (but note that it results in the public type)
                    return generateReserved(code, Op.A_PUBLIC, errs);
                    }

                TypeConstant typeOuter;
                int          cSteps;
                if (argRaw instanceof TypeConstant)
                    {
                    typeOuter = (TypeConstant) argRaw;
                    cSteps    = m_targetInfo.getStepsOut();
                    }
                else
                    {
                    typeOuter = getType();
                    cSteps    = ((ParentClassConstant) argRaw).getDepth();
                    }

                Register regOuter = createRegister(typeOuter, fUsedOnce);
                if (typeOuter.getAccess() == Access.PUBLIC)
                    {
                    code.add(new MoveThis(cSteps, regOuter));
                    }
                else
                    {
                    code.add(new MoveThis(cSteps, regOuter, typeOuter.getAccess()));
                    }
                return regOuter;
                }

            case OuterRef:
                {
                TypeConstant typeRef;
                TypeConstant typeOuter;
                int          cSteps;
                if (argRaw instanceof TypeConstant)
                    {
                    typeOuter = (TypeConstant) argRaw;
                    typeRef   = pool().ensureParameterizedTypeConstant(pool().typeRef(), typeOuter);
                    cSteps    = m_targetInfo.getStepsOut();
                    }
                else
                    {
                    typeRef   = getType();
                    typeOuter = typeRef.getParamType(0);
                    cSteps    = argRaw instanceof ThisClassConstant
                            ? 0
                            : ((ParentClassConstant) argRaw).getDepth();
                    }

                Register regOuter = createRegister(typeOuter, fUsedOnce);
                code.add(new MoveThis(cSteps, regOuter));

                Register regRef = createRegister(typeRef, fUsedOnce);
                code.add(new MoveRef(regOuter, regRef));
                return regRef;
                }

            case PropertyDeref:
                {
                if (left instanceof NameExpression)
                    {
                    NameExpression nameLeft = (NameExpression) left;
                    if (nameLeft.getMeaning() == Meaning.Label)
                        {
                        LabelVar labelVar = (LabelVar) nameLeft.m_arg;
                        return labelVar.getPropRegister(getName());
                        }
                    }

                PropertyConstant idProp  = (PropertyConstant) argRaw;
                Register         regTemp = createRegister(getType(), fUsedOnce);
                switch (calculatePropertyAccess())
                    {
                    case SingletonParent:
                        {
                        IdentityConstant  idParent    = idProp.getParentConstant();
                        SingletonConstant idSingleton = pool().ensureSingletonConstConstant(idParent);
                        code.add(new P_Get(idProp, idSingleton, regTemp));
                        break;
                        }

                    case Outer:
                        {
                        int      cSteps   = m_targetInfo.getStepsOut();
                        Register regOuter = createRegister(m_targetInfo.getType(), true);
                        code.add(new MoveThis(cSteps, regOuter));
                        code.add(new P_Get(idProp, regOuter, regTemp));
                        break;
                        }

                    case This:
                        if (idProp.getName().equals("outer"))
                            {
                            code.add(new MoveThis(1, regTemp));
                            }
                        else
                            {
                            if (fLocalPropOk || idProp.getComponent().isStatic())
                                {
                                return idProp;
                                }
                            code.add(new L_Get(idProp, regTemp));
                            }
                        break;

                    case Left:
                        {
                        if (idProp.getComponent().isStatic())
                            {
                            return idProp;
                            }
                        Argument argLeft = left.generateArgument(ctx, code, false, true, errs);
                        code.add(new P_Get(idProp, argLeft, regTemp));
                        break;
                        }
                    }
                return regTemp;
                }

            case PropertyRef:
                {
                PropertyConstant idProp    = (PropertyConstant) argRaw;
                Argument         argTarget = left == null
                        ? new Register(ctx.getThisType(), Op.A_TARGET)
                        : left.generateArgument(ctx, code, true, true, errs);

                Register regRef = createRegister(getType(), fUsedOnce);
                code.add(m_fAssignable
                        ? new P_Var(idProp, argTarget, regRef)
                        : new P_Ref(idProp, argTarget, regRef));
                return regRef;
                }

            case RegisterRef:
                {
                Register regVal = (Register) argRaw;
                Register regRef = createRegister(getType(), fUsedOnce);
                code.add(m_fAssignable
                        ? new MoveVar(regVal, regRef)
                        : new MoveRef(regVal, regRef));
                return regRef;
                }

            case Singleton:
                assert !isConstant();
                assert ((IdentityConstant) argRaw).getComponent().isStatic();
                return argRaw;

            case TypeOfTypedef:
            case TypeOfClass:
                assert isConstant();
                return toConstant();

            case TypeOfFormalChild:
                {
                FormalTypeChildConstant idChild = (FormalTypeChildConstant) argRaw;

                Argument argTarget = left.generateArgument(ctx, code, true, true, errs);

                Register regType = createRegister(idChild.getType(), fUsedOnce);
                code.add(new P_Get(idChild, argTarget, regType));
                return regType;
                }

            case BindTarget:
                {
                MethodConstant idMethod  = (MethodConstant) argRaw;
                Argument       argTarget = left == null
                        ? new Register(ctx.getThisType(), Op.A_TARGET)
                        : left.generateArgument(ctx, code, true, true, errs);

                Register regFn = createRegister(idMethod.getType(), fUsedOnce);
                if (m_mapTypeParams == null)
                    {
                    code.add(new MBind(argTarget, idMethod, regFn));
                    return regFn;
                    }
                else
                    {
                    Register regFn0 = createRegister(pool().typeFunction(), false);
                    code.add(new MBind(argTarget, idMethod, regFn0));
                    bindTypeParameters(ctx, code, regFn0, regFn);
                    return regFn;
                    }
                }

            default:
                throw new IllegalStateException("arg=" + argRaw);
            }
        }

    private void bindTypeParameters(Context ctx, Code code, Argument argFnOrig, Argument argFnResult)
        {
        List<Map.Entry<String, TypeConstant>> list = m_mapTypeParams.asList();

        int        cParams  = list.size();
        int[]      anBindIx = new int[cParams];
        Argument[] aArgBind = new Argument[cParams];

        for (int i = 0; i < cParams; i++)
            {
            Map.Entry<String, TypeConstant> entry = list.get(i);

            String       sName = entry.getKey();
            TypeConstant type  = entry.getValue();

            anBindIx[i] = i;
            if (type.isGenericType())
                {
                TypeInfo infoThis = ctx.getThisType().ensureTypeInfo();

                // first type goes on stack
                Register regType = createRegister(pool().typeType(), i == 0);
                code.add(new L_Get(infoThis.findProperty(sName).getIdentity(), regType));

                aArgBind[i] = regType;
                }
            else if (type.isTypeParameter())
                {
                int iReg = ((TypeParameterConstant) type.getDefiningConstant()).getRegister();
                aArgBind[i] = new Register(type, iReg);
                }
            else
                {
                // the type itself is the value
                aArgBind[i] = type;
                }
            }
        code.add(new FBind(argFnOrig, anBindIx, aArgBind, argFnResult));
        }

    @Override
    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs)
        {
        if (isAssignable(ctx))
            {
            TargetInfo target = m_targetInfo;
            Argument   arg    = m_arg;
            if (arg instanceof Register)
                {
                assert target == null;
                return new Assignable((Register) arg);
                }
            else if (arg instanceof PropertyConstant)
                {
                PropertyConstant idProp = (PropertyConstant) arg;
                Argument         argTarget;

                if (left == null)
                    {
                    ClassStructure clz = ctx.getThisClass();

                    switch (m_propAccessPlan)
                        {
                        case SingletonParent:
                            {
                            IdentityConstant  idParent    = idProp.getParentConstant();
                            SingletonConstant idSingleton = pool().ensureSingletonConstConstant(idParent);
                            code.add(new Var_I(idParent.getType(), idSingleton));
                            argTarget = code.lastRegister();
                            break;
                            }

                        case This:
                            argTarget = new Register(clz.getFormalType(), Op.A_TARGET);
                            break;

                        case Outer:
                            for (int nDepth = target.getStepsOut(); --nDepth >= 0;)
                                {
                                clz = clz.getContainingClass();
                                }
                            argTarget = createRegister(clz.getFormalType(), true);
                            code.add(new MoveThis(target.getStepsOut(), argTarget));
                            break;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    argTarget = left.generateArgument(ctx, code, true, true, errs);
                    }

                return new Assignable(argTarget, idProp);
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
     * @return an array of trailing or nul if there are none
     */
    protected TypeConstant[] getImplicitTrailingTypeParameters(Context ctx)
        {
        if (hasTrailingTypeParams())
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
            return listTypes.toArray(new TypeConstant[cParams]);
            }
        else
            {
            return null;
            }
        }

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

        m_targetInfo  = null;
        m_arg         = null;
        m_fAssignable = false;

        // the first step is to resolve the name to a "raw" argument, i.e. what does the name refer
        // to, without consideration to read-only vs. read-write, reference vs. de-reference, static
        // vs. virtual, and so on
        ConstantPool pool  = pool();
        String       sName = name.getValueText();
        if (left == null)
            {
            // resolve the initial name; try to avoid double-reporting
            ErrorListener errsTemp = errs.branch();

            Argument arg = ctx.resolveName(name, errsTemp);
            if (arg == null)
                {
                if (errsTemp.hasSeriousErrors())
                    {
                    errsTemp.merge();
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                            sName, ctx.getMethod().getIdentityConstant().getValueString());
                    }
                return null;
                }

            if (arg instanceof Register)
                {
                m_arg         = arg;
                m_fAssignable = ((Register) arg).isWritable();
                }
            else if (arg instanceof Constant)
                {
                Constant constant = ((Constant) arg);
                switch (constant.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                    case Typedef:
                    case Property:
                        m_arg = arg;
                        break;

                    // TODO if multi-method, log an error; it shouldn't cause an Exception!
                    default:
                        throw new IllegalStateException("format=" + constant.getFormat()
                                + ", constant=" + constant);
                    }
                }
            else if (arg instanceof TargetInfo)
                {
                TargetInfo       target = m_targetInfo = (TargetInfo) arg;
                IdentityConstant id     = target.getId();

                switch (id.getFormat())
                    {
                    case MultiMethod:
                        {
                        // TODO still some work here to
                        //      (i) save off the TargetInfo
                        //      (ii) use it in code gen
                        //      (iii) mark the this (and out this's) as being used
                        MultiMethodConstant  idMM = (MultiMethodConstant) id;
                        MultiMethodStructure mms  = (MultiMethodStructure) idMM.getComponent();

                        Collection<MethodStructure> methods = mms.methods();

                        if (methods.size() == 1)
                            {
                            m_arg = methods.iterator().next().getIdentityConstant();
                            }
                        else
                            {
                            // return the MultiMethod; the caller will decide which to use
                            m_arg = idMM;
                            }
                        break;
                        }

                    case Property:
                        {
                        TypeInfo info = target.getTargetType().ensureTypeInfo(errs);

                        // TODO still some work here to mark the this (and out this's) as being used
                        PropertyInfo prop = info.findProperty((PropertyConstant) id);
                        if (prop == null)
                            {
                            throw new IllegalStateException("missing property: " + id + " on " + target.getTargetType());
                            }

                        if (info.isTopLevel() && info.isStatic() &&
                                !info.getClassStructure().equals(ctx.getThisClass()))
                            {
                            m_propAccessPlan = PropertyAccess.SingletonParent;
                            }
                        else if (prop.isConstant() && prop.getInitializer() != null)
                            {
                            m_propAccessPlan = PropertyAccess.SingletonParent;
                            }
                        else if (target.getStepsOut() == 0)
                            {
                            m_propAccessPlan = PropertyAccess.This;
                            }
                        else
                            {
                            m_propAccessPlan = PropertyAccess.Outer;
                            }

                        PropertyConstant idProp = (PropertyConstant) id;
                        if (idProp.isTypeSequenceTypeParameter())
                            {
                            m_arg         = idProp;
                            m_fAssignable = false;
                            }
                        else
                            {
                            m_arg         = prop.getIdentity();
                            m_fAssignable = prop.isVar();
                            }
                        break;
                        }

                    case Module:
                    case Package:
                    case Class:
                        // this indicates an "outer this"
                        m_arg = target.getType();
                        break;

                    default:
                        throw new IllegalStateException("unsupported constant format: " + id);
                    }
                }
            }
        else // left is NOT null
            {
            // the "Type.this" construct is not supported; use "this.Type" instead
            if (sName.equals("this"))
                {
                log(errs, Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                return null;
                }

            // attempt to use identity mode (e.g. "packageName.ClassName.PropName")
            boolean fValid  = true;
            boolean fIdMode = left instanceof NameExpression
                    && ((NameExpression) left).resolveRawArgument(ctx, false, errs) != null
                    && ((NameExpression) left).isIdentityMode(ctx, true);
            if (fIdMode)
                {
                // it must be a child of the component
                NameExpression   exprLeft  = (NameExpression) left;
                IdentityConstant idLeft    = exprLeft.getIdentity(ctx);
                SimpleCollector  collector = new SimpleCollector();
                // TODO this seems all wrong - why are we using the "id" instead of the TypeConstant.ensureTypeInfo() ???
                if (idLeft.getComponent().resolveName(sName, Access.PUBLIC, collector) == ResolutionResult.RESOLVED)
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
                if (typeLeft == null)
                    {
                    return null;
                    }

                FormalConstant constFormal = null;
                if (typeLeft.isTypeOfType() && left instanceof NameExpression)
                    {
                    Argument argLeft = ((NameExpression) left).m_arg;

                    switch (((NameExpression) left).getMeaning())
                        {
                        case Variable:
                            {
                            // Example (Array.x):
                            //   static <CompileType extends Hasher> Int hashCode(CompileType array)
                            //       {
                            //       Int hash = 0;
                            //       for (CompileType.Element el : array)
                            //           {
                            //           hash += CompileType.Element.hashCode(el);
                            //           }
                            //       return hash;
                            //       }
                            //
                            // typeLeft is a type of the "CompileType" type parameter and we need to
                            // produce "CompileType.Element" formal child constant
                            //
                            // Another example is:
                            //  Boolean f = CompileType.is(Type<Array>) && CompileType.Element.is(Type<Int>);
                            //
                            // in that case, "typeLeft" for the second expression is a union
                            // (CompileType + Array), but we still need to produce "CompileType.Element"
                            // formal child constant

                            TypeConstant typeFormal = ((Register) argLeft).getOriginalType().getParamType(0);
                            if (typeFormal.isSingleDefiningConstant())
                                {
                                argLeft = typeFormal.getDefiningConstant();
                                if (argLeft instanceof FormalConstant)
                                    {
                                    constFormal = (FormalConstant) argLeft;
                                    typeLeft    = typeLeft.getParamType(0);
                                    }
                                }
                            break;
                            }

                        case Property:
                            {
                            // There are two examples of what we need to handle:
                            // a) the property is a formal property and the name refers to its
                            //    constraint's formal property:
                            //   class C<Element extends Array>
                            //       {
                            //       foo()
                            //           {
                            //           assert Element.Element.is(Type<Orderable>);
                            //           }
                            //       }
                            // or
                            // b) the name refers to a property on the Type object represented
                            //    by this property:
                            //   class C<Element>
                            //       {
                            //       foo()
                            //           {
                            //           Property[] props = Element.properties;
                            //           }
                            //       }
                            PropertyConstant idProp = (PropertyConstant) argLeft;
                            if (idProp.isFormalType() &&
                                    idProp.getConstraintType().containsGenericParam(sName))
                                {
                                constFormal = idProp;
                                typeLeft    = typeLeft.getParamType(0);
                                }
                            break;
                            }
                        }
                    }
                else if (typeLeft.isFormalType() && !typeLeft.isFormalTypeSequence())
                    {
                    // Example (Enum.x):
                    //   static <CompileType extends Enum> Ordered compare(CompileType value1, CompileType value2)
                    //   {
                    //   return value1.ordinal <=> value2.ordinal;
                    //   }
                    //
                    // "this" is value1.ordinal
                    // typeLeft is the "CompileType" type parameter

                    typeLeft = ((FormalConstant) typeLeft.getDefiningConstant()).getConstraintType();
                    }

                // TODO support or properties nested under something other than a class (need nested type infos?)
                if (left instanceof NameExpression)
                    {
                    Argument arg = ((NameExpression) left).m_arg;

                    // "this:target" has private access in this context
                    // as well as a "nest mate" (a class that is collocated with the context class)
                    if (arg instanceof Register &&
                            ((Register) arg).isTarget() || isNestMate(ctx, typeLeft))
                        {
                        typeLeft = pool.ensureAccessTypeConstant(typeLeft, Access.PRIVATE);
                        }
                    }

                TypeInfo     infoType = typeLeft.ensureTypeInfo(errs);
                PropertyInfo infoProp = infoType.findProperty(sName);
                if (infoProp == null)
                    {
                    Set<MethodConstant> setMethods = infoType.findMethods(sName, -1, MethodKind.Any);
                    switch (setMethods.size())
                        {
                        case 0:
                            // TODO check if the name refers to a typedef

                            // process the "this.OuterName" construct
                            if (!fIdMode)
                                {
                                Constant constTarget = new NameResolver(this, sName)
                                        .forceResolve(ErrorListener.BLACKHOLE);
                                if (constTarget instanceof IdentityConstant && constTarget.isClass())
                                    {
                                    // if the left is a class, then the result is a sequence of at
                                    // least one (recursive) ParentClassConstant around a
                                    // ThisClassConstant; from this (context) point, walk up looking
                                    // for the specified class, counting the number of "parent
                                    // class" steps to get there
                                    PseudoConstant idRelative = getRelativeIdentity(typeLeft, (IdentityConstant) constTarget);
                                    if (idRelative != null)
                                        {
                                        m_arg = idRelative;
                                        return idRelative;
                                        }
                                    }
                                }

                            name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_MISSING,
                                sName, typeLeft.getValueString());
                            break;

                        case 1:
                            // there's just a single method by that name
                            m_arg = setMethods.iterator().next();
                            break;

                        default:
                            // there are more then one method by that name;
                            // return the MultiMethod; let the caller decide
                            m_arg = setMethods.iterator().next().getParentConstant();
                            break;
                        }
                    }
                else
                    {
                    if (constFormal == null)
                        {
                        m_arg         = infoProp.getIdentity();
                        m_fAssignable = infoProp.isVar();
                        }
                    else
                        {
                        m_arg         = pool.ensureFormalTypeChildConstant(constFormal, sName);
                        m_fAssignable = false;
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

            // label variables do not actually exist
            if (reg.isLabel() && (fSuppressDeref || !(getParent() instanceof NameExpression)))
                {
                log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, getName());
                }

            if (fSuppressDeref)
                {
                // assert !reg.isPredefined(); // REVIEW GG: see SoftVar.x
                m_plan = Plan.RegisterRef;
                return reg.ensureRegType(pool, !m_fAssignable);
                }
            else
                {
                // use the register itself (the "T" column in the table above)
                m_plan = Plan.None;

                // there is a possibility that the register type in this context is narrower than
                // its original type; we can return it only if it fits the desired type
                TypeConstant typeLocal = reg.getType();
                return isRValue() || typeDesired != null && typeLocal.isA(typeDesired)
                        ? typeLocal
                        : reg.getOriginalType();
                }
            }

        if (argRaw instanceof TypeConstant)
            {
            // this can only mean an "outer this"
            TypeConstant typeParent = (TypeConstant) argRaw;
            if (isSuppressDeref())
                {
                m_plan = Plan.OuterRef;
                return pool.ensureParameterizedTypeConstant(pool.typeRef(), typeParent);
                }
            else
                {
                m_plan = Plan.OuterThis;
                return typeParent;
                }
            }

        assert argRaw instanceof Constant;
        Constant constant = (Constant) argRaw;
        switch (constant.getFormat())
            {
            case ThisClass:
                {
                TypeConstant typeThis = pool.ensureAccessTypeConstant(
                        constant.getType().adoptParameters(pool, ctx.getThisType()), Access.PRIVATE);
                if (isSuppressDeref())
                    {
                    m_plan = Plan.OuterRef;
                    return pool.ensureParameterizedTypeConstant(pool.typeRef(), typeThis);
                    }
                else
                    {
                    m_plan = Plan.None;
                    return typeThis;
                    }
                }

            case ParentClass:
                {
                // TODO GG: that needs to be calculated; it can be a VirtualChildTypeConstant
                TypeConstant typeParent = pool.ensureAccessTypeConstant(
                    ((ParentClassConstant) constant).getDeclarationLevelClass().getFormalType(),
                    Access.PRIVATE);

                NameExpression exprLeft = (NameExpression) left;
                if (exprLeft.isSuppressDeref())
                    {
                    m_plan = Plan.OuterRef;
                    return pool.ensureParameterizedTypeConstant(pool.typeRef(), typeParent);
                    }
                else
                    {
                    m_plan = Plan.OuterThis;
                    return typeParent;
                    }
                }

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
                    TypeConstant type;
                    if (aTypeParams == null)
                        {
                        ClassConstant  idClass = (ClassConstant) constant;
                        ClassStructure clzThis = getComponent().getContainingClass();
                        if (clzThis != null && clzThis.getIdentityConstant().equals(idClass))
                            {
                            type = clzThis.getFormalType();
                            }
                        else
                            {
                            type = pool.ensureTerminalTypeConstant(constant);
                            }
                        }
                    else
                        {
                        type = pool.ensureClassTypeConstant(constant, null, aTypeParams);
                        }

                    m_plan = Plan.TypeOfClass;
                    return type.getType();
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

                // TODO: this scenario will go away (p.this for property p on Map will become this.Map.&p)
                if (name.getValueText().equals("this"))
                    {
                    assert left instanceof NameExpression;
                    m_plan = Plan.OuterThis;
                    return ((PropertyConstant) constant).getRefType(left.getType());
                    }

                PropertyConstant  id   = (PropertyConstant) argRaw;
                PropertyStructure prop = (PropertyStructure) id.getComponent();
                TypeConstant      type = prop.getType();

                if (id.isTypeSequenceTypeParameter())
                    {
                    assert !m_fAssignable;
                    m_plan = Plan.PropertyDeref;
                    return id.getConstraintType();
                    }

                if (prop.isConstant())
                    {
                    assert !m_fAssignable;
                    m_plan = prop.hasInitialValue() ? Plan.PropertyDeref : Plan.Singleton;
                    return type;
                    }

                TypeConstant typeLeft;
                if (left == null)
                    {
                    if (m_targetInfo == null)
                        {
                        typeLeft = pool.ensureAccessTypeConstant(ctx.getThisType(), Access.PRIVATE);

                        // resolve the property type
                        ClassStructure clz = ctx.getThisClass();
                        if (clz != prop.getParent())
                            {
                            // the property may originate in a contribution
                            // (e.g. Interval.x refers to Range.upperBound)
                            PropertyInfo infoProp = clz.getFormalType().
                                    ensureTypeInfo(errs).findProperty(id);
                            if (infoProp != null)
                                {
                                type = infoProp.getType();
                                }
                            }
                        }
                    else
                        {
                        typeLeft = m_targetInfo.getTargetType();
                        type     = typeLeft.ensureTypeInfo(errs).findProperty(id).getType();
                        }

                    // check for a narrowed property type
                    Argument argNarrowed = ctx.getVar(prop.getName());
                    if (argNarrowed instanceof TargetInfo)
                        {
                        type = argNarrowed.getType();
                        }
                    else
                        {
                        type = type.resolveAutoNarrowing(pool, false, typeLeft);
                        }
                    }
                else
                    {
                    typeLeft = left.getImplicitType(ctx);
                    if (isNestMate(ctx, typeLeft))
                        {
                        typeLeft = pool.ensureAccessTypeConstant(typeLeft, Access.PRIVATE);
                        }
                    PropertyInfo infoProp = typeLeft.ensureTypeInfo(errs).findProperty(id);
                    if (infoProp != null)
                        {
                        type = infoProp.getType().resolveAutoNarrowing(pool, false, typeLeft);
                        }
                    }

                if (isIdentityMode(ctx, false))
                    {
                    m_plan = Plan.None;
                    // TODO parameterized type of Property<Target, Referent, Implementation>
                    return pool.typeProperty();
                    }

                if (isSuppressDeref())
                    {
                    m_plan = Plan.PropertyRef;
                    return id.getRefType(typeLeft);
                    }
                else
                    {
                    m_plan = Plan.PropertyDeref;
                    return type;
                    }
                }

            case FormalTypeChild:
                {
                FormalTypeChildConstant idFormal = (FormalTypeChildConstant) constant;
                m_plan = Plan.TypeOfFormalChild;
                return idFormal.getType();
                }

            case Typedef:
                {
                if (aTypeParams != null)
                    {
                    // TODO have to incorporate type params
                    notImplemented();
                    }

                m_plan = Plan.TypeOfTypedef;
                TypeConstant typeRef = ((TypedefConstant) constant).getReferredToType();
                return typeRef.getType();
                }

            case Method:
                {
                // the constant refers to a method or function
                MethodConstant idMethod = (MethodConstant) argRaw;
                m_plan = idMethod.isFunction()
                        ? Plan.None
                        : Plan.BindTarget;

                TypeConstant typeFn = idMethod.getRefType(null);
                if (typeDesired != null)
                    {
                    MethodStructure method = (MethodStructure) idMethod.getComponent();
                    if (method == null)
                        {
                        TypeConstant typeLeft = left == null
                                ? ctx.getThisType()
                                : left.getImplicitType(ctx);
                        // we know the method is accessible; use the private view just in case
                        TypeInfo infoLeft = pool.ensureAccessTypeConstant(typeLeft, Access.PRIVATE).
                                ensureTypeInfo(errs);
                        MethodInfo infoMethod = infoLeft.getMethodBySignature(idMethod.getSignature());
                        method   = infoMethod.getTopmostMethodStructure(infoLeft);
                        assert method != null;
                        }

                    int cTypeParams = method.getTypeParamCount();
                    if (cTypeParams > 0)
                        {
                        TypeConstant[]  atypeArgs = pool.extractFunctionParams(typeDesired);
                        ListMap<String, TypeConstant> mapTypeParams =
                            method.resolveTypeParameters(atypeArgs, TypeConstant.NO_TYPES, false);

                        if (mapTypeParams.size() < cTypeParams)
                            {
                            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                                    method.collectUnresolvedTypeParameters(mapTypeParams.keySet()));
                            return null;
                            }

                        // resolve the function signature against all the types we know by now
                        typeFn          = typeFn.resolveGenerics(pool, mapTypeParams::get);
                        m_mapTypeParams = mapTypeParams;
                        }
                    }
                return typeFn;
                }

            case MultiMethod:
                // the constant refers to a method or function
                m_plan = Plan.None;
                if (typeDesired != null)
                    {
                    // TODO: find the match
                    }
                log(errs, Severity.ERROR, Compiler.NAME_AMBIGUOUS,
                        ((MultiMethodConstant) constant).getName());
                return null;

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
                    ? reg.isLabel()
                            ? Meaning.Label
                            : Meaning.Reserved
                    : Meaning.Variable;
            }

        if (arg instanceof TypeConstant)
            {
            // outer this
            return Meaning.Reserved;
            }

        if (arg instanceof Constant)
            {
            Constant constant = (Constant) arg;
            switch (constant.getFormat())
                {
                    // class ID
                case Module:
                case Package:
                    // relative ID
                case ThisClass:
                case ParentClass:
                    return Meaning.Class;

                case Class:
                    return m_plan == Plan.TypeOfClass
                            ? Meaning.Type
                            : Meaning.Class;

                case Property:
                    return Meaning.Property;

                case FormalTypeChild:
                    return Meaning.FormalChildType;

                case Method:
                case MultiMethod:
                    return Meaning.Method;

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
            case Type:
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

                return isSuppressDeref() && !prop.isConstant() && !prop.isGenericTypeParameter()
                    && left != null && ((NameExpression) left).isIdentityMode(ctx, true);
                }
            }

        return false;
        }

    /**
     * This method is not currently used.
     *
     * @return true iff the specified register represents a type parameter
     */
    protected boolean isTypeParameter(Context ctx, Register reg)
        {
        return !reg.isUnknown() && ctx.getMethod().isTypeParameter(reg.getIndex());
        }

    /**
     * @return the class or property identity that the name expression indicates, iff the name
     *         expression is "identity mode"
     */
    protected IdentityConstant getIdentity(Context ctx)
        {
        return m_arg instanceof IdentityConstant
                ? (IdentityConstant) m_arg
                : ((TypeConstant) m_arg).getSingleUnderlyingClass(true);
        }

    /**
     * @return  the PseudoConstant representing the relationship of the parent class that this name
     *          expression refers to vis-a-vis the class containing the method for which the passed
     *          context exists
     */
    protected PseudoConstant getRelativeIdentity(TypeConstant typeFrom, IdentityConstant idTarget)
        {
        // verify that we can "walk up the line" starting from the specified type
        if (!typeFrom.isSingleUnderlyingClass(true))
            {
            return null;
            }
        Component component  = typeFrom.getSingleUnderlyingClass(true).getComponent();
        if (!(component instanceof ClassStructure))
            {
            return null;
            }

        ConstantPool     pool       = pool();
        ClassStructure   clzParent  = (ClassStructure) component;
        PseudoConstant   idVirtPath = null;
        while (clzParent != null)
            {
            IdentityConstant idParent = clzParent.getIdentityConstant();
            idVirtPath = idVirtPath == null
                    ? pool.ensureThisClassConstant(idParent)
                    : pool.ensureParentClassConstant(idVirtPath);

            if (idParent.equals(idTarget) || clzParent.hasContribution(idTarget, true))
                {
                // found it!
                return idVirtPath;
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

    protected PropertyAccess calculatePropertyAccess()
        {
        if (m_propAccessPlan != null)
            {
            return m_propAccessPlan;
            }

        if (left == null)
            {
            return PropertyAccess.This;
            }

        if (left instanceof NameExpression)
            {
            NameExpression exprLeft = (NameExpression) left;
            // check that "this" is not "OuterThis"
            if (exprLeft.getName().equals("this") && exprLeft.m_plan == Plan.None)
                {
                return PropertyAccess.This;
                }
            }
        return PropertyAccess.Left;
        }


    /**
     * Narrow the type of the variable represented by this expression for the specified context branch.
     * <p/>
     * Note: This can only be used during the validate() stage after this name expression
     *       has been validated.
     *
     * @param ctx         the context
     * @param branch      the branch
     * @param typeNarrow  the narrowing type
     */
    public void narrowType(Context ctx, Context.Branch branch, TypeConstant typeNarrow)
        {
        if (typeNarrow != null)
            {
            assert isValidated();

            if (left != null)
                {
                // TODO: to allow an expression "a.b.c" to be narrowed, all parents have to be immutable
                return;
                }

            String   sName = getName();
            Argument arg   = resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);

            // we are only concerned with registers and type parameters;
            // properties and constants are ignored
            if (arg instanceof Register)
                {
                ctx.narrowLocalRegister(sName, (Register) arg, branch, typeNarrow);
                }
            else
                {
                if (arg instanceof TargetInfo)
                    {
                    TargetInfo       info = (TargetInfo) arg;
                    IdentityConstant id   = info.getId();
                    if (id instanceof PropertyConstant)
                        {
                        PropertyConstant idProp = (PropertyConstant) arg;

                        assert sName.equals(id.getName());

                        if (idProp.isFormalType())
                            {
                            ctx.replaceGenericArgument(sName, branch, new TargetInfo(info, typeNarrow));
                            }
                        else  // allow narrowing for immutable properties
                            {
                            TypeConstant     typeTarget = info.getTargetType();
                            IdentityConstant idTarget   = typeTarget.getSingleUnderlyingClass(false);
                            if (idTarget.equals(ctx.getThisClass().getIdentityConstant()) &&
                                ctx.getMethod().isConstructor())
                                {
                                // no property narrowing in the constructor
                                }
                            else if (typeTarget.isImmutable())
                                {
                                ctx.narrowProperty(sName, idProp, branch, new TargetInfo(info, typeNarrow));
                                }
                            }
                        }
                    }
                else if (arg instanceof PropertyConstant)
                    {
                    PropertyConstant idProp = (PropertyConstant) arg;

                    assert sName.equals(idProp.getName());

                    if (idProp.isFormalType())
                        {
                        assert typeNarrow.isTypeOfType();

                        TargetInfo info = new TargetInfo(sName, idProp, true, idProp.getNamespace().getType(), 0);
                        ctx.replaceGenericArgument(sName, branch, new TargetInfo(info, typeNarrow));
                        }
                    else // allow narrowing for immutable properties
                        {
                        if (ctx.getMethod().isConstructor())
                            {
                            // no property narrowing in the constructor
                            }
                        else if (ctx.getThisClass().isConst())
                            {
                            TargetInfo info = new TargetInfo(sName, idProp, true, ctx.getThisType(), 0);
                            ctx.narrowProperty(sName, idProp, branch, new TargetInfo(info, typeNarrow));
                            }
                        }
                    }
                else if (arg instanceof TypeParameterConstant)
                    {
                    TypeParameterConstant contParam = (TypeParameterConstant) arg;
                    MethodConstant        idMethod  = contParam.getMethod();
                    int                   nParam    = contParam.getRegister();
                    MethodStructure       method    = (MethodStructure) idMethod.getComponent();

                    // ctx.narrowTypeParameter(sName, branch, typeNarrow);
                    }
                }
            }
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
    enum Meaning {Unknown, Reserved, Variable, Property, FormalChildType, Method, Class, Type, Typedef, Label}

    /**
     * Represents the necessary argument/assignable transformation that the expression will have to
     * produce as part of compilation, if it is asked to produce an argument, an assignable, or an
     * assignment.
     */
    enum Plan {None, OuterThis, OuterRef, RegisterRef, PropertyDeref, PropertyRef, TypeOfClass,
               TypeOfTypedef, Singleton, TypeOfFormalChild, BindTarget}

    /**
     * If the plan is None or BindTarget, and this expression represents a method or function,
     * we may need to bind type parameters.
     */
    private ListMap<String, TypeConstant> m_mapTypeParams;

    /**
     * Cached validation info: The optional TargetInfo that provides context for the initial name,
     * if the initial name is related to "this".
     */
    private transient TargetInfo m_targetInfo;

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
     * There are three possible scenarios getting to a property represented by this expression:
     *
     * 1) the property is on a singleton parent (module, package or singleton class)
     *    (left must be null)
     * 2) the property is on an instance parent
     *    (left must be null)
     * 3) the property is on this
     *    (left must be null)
     * 4) the property is on "left"
     *    (left must be not null)
     */
    enum PropertyAccess {SingletonParent, Outer, This, Left}

    /**
     * The chosen property access plan.
     */
    private PropertyAccess m_propAccessPlan;

    /**
     * Cached validation info: Can the name be used as an "L value"?
     */
    private transient boolean m_fAssignable;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "left", "params");
    }
