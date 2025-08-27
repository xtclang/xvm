package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.ast.BindMethodAST;
import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.OuterExprAST;
import org.xvm.asm.ast.PropertyExprAST;
import org.xvm.asm.ast.UnaryOpExprAST;
import org.xvm.asm.ast.UnaryOpExprAST.Operator;

import org.xvm.asm.constants.*;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Context.Branch;
import org.xvm.compiler.ast.LabeledStatement.LabelVar;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import org.xvm.runtime.Utils;

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
 * The context either has a "this" (i.e. context from inside an instance method), or it
 * doesn't (i.e. context from inside a function). Even a lambda within a method has a "this",
 * since it can conceptually capture the "this" of the method. The presence of a "this" has to
 * be tracked, because the interpretation of a name will differ in some cases based on whether
 * there is a "this" or not.
 * <p/>
 * A name resolution also has an implicit de-reference, or an explicit non-dereference (a
 * suppression of the de-reference using the "&" symbol). The result of the name being resolved
 * will differ based on whether the name is implicitly de-referenced, or explicitly not
 * de-referenced.
 *
 * <p/>
 * The starting point for de-referencing is within a "method body", which is one of:
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
 * [1] must have a left-hand side in identity mode; otherwise it is an Error
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
        return "super".equals(name.getValueText()) || left != null && left.usesSuper();
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        // can only be "Name.Name.present" form
        if (left instanceof NameExpression exprName &&
                amp == null && params == null && "present".equals(getName()))
            {
            // left has to be all names
            while (exprName.left != null)
                {
                if (!"present".equals(exprName.getName()) && exprName.left instanceof NameExpression)
                    {
                    exprName = (NameExpression) exprName.left;
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
     * @return true iff the expression is a simple name expression, i.e. just a single, non-special
     *         name with no other stuff
     */
    public boolean isSimpleName()
        {
        return left == null && !isSuppressDeref() && !isSpecial() && !hasTrailingTypeParams();
        }

    /**
     * @return true iff the expression is a "pure" name expression, i.e. composed of only
     *         NameExpressions
     */
    public boolean isOnlyNames()
        {
        return left == null ||
               left instanceof NameExpression exprName && exprName.isOnlyNames();
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
        if (left instanceof NameExpression exprName)
            {
            asName = exprName.collectNames(cNames + 1);
            }
        else
            {
            asName = new String[cNames];
            }
        asName[asName.length-cNames] = getName();
        return asName;
        }

    /**
     * Build a list of name tokens for the name expression.
     *
     * @param cNames  how many names so far (recursing right to left)
     *
     * @return a list of name tokens
     */
    protected List<Token> collectNameTokens(int cNames)
        {
        List<Token> list;
        if (left instanceof NameExpression exprName)
            {
            list = exprName.collectNameTokens(cNames + 1);
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
     * @return true iff the expression is explicitly non-de-referencing, as with the '&' prefix on
     *         a class, property, or method name
     */
    public boolean isSuppressDeref()
        {
        return amp != null;
        }

    /**
     * @return true iff the expression is explicitly non-de-referencing, as with the '&' pre-fix on
     *         a class, property, or method name, or if the left expression is a name expression, and
     *         it has any suppressed dereference
     */
    public boolean hasAnySuppressDeref()
        {
        return isSuppressDeref() ||
            left instanceof NameExpression exprName && exprName.hasAnySuppressDeref();
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
        return name.isSpecial() ||
               left instanceof NameExpression exprName && exprName.isSpecial();
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        TypeExpression exprType = null;

        NameExpressions:
        if (left instanceof NameExpression exprLeft)
            {
            // we're going to split a chain of name expression (A).(B<T>).(C).(D<U>) into
            // a chain of NamedTypeExpressions (A.B<T>).(C.D<U>)
            List<Token> tokens = new ArrayList<>();
            tokens.add(name);

            // moving to the left, find any previous parameterized named expression
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
                    // if expr is null, there is no left-named type and therefore no reason to split
                    // this into a chain of NamedTypeExpressions;
                    // otherwise this should be an error, which will be reported later
                    break NameExpressions;
                    }
                exprLeft = (NameExpression) expr;
                }

            NamedTypeExpression exprLeftType = (NamedTypeExpression) exprPrev.toTypeExpression();

            exprType = new NamedTypeExpression(exprLeftType, tokens, params, lEndPos);
            }

        if (exprType == null)
            {
            exprType = new NamedTypeExpression(null, getNameTokens(), null, null, params, lEndPos);
            }

        exprType.setParent(getParent());
        return exprType;
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
    public void updateLValueFromRValueTypes(Context ctx, Branch branch, boolean fCond,
                                            TypeConstant[] aTypes)
        {
        assert aTypes != null && aTypes.length >= 1;

        if (m_arg instanceof Register reg)
            {
            // if the R-value type is an enum value (except Null), widen it to its parent
            // Enumeration before merging with the L-value, but don't go beyond declared type
            TypeConstant typeOld  = reg.getType();
            TypeConstant typeOrig = reg.getOriginalType();
            TypeConstant typeNew  = aTypes[0];
            TypeConstant typeWide = typeNew.widenEnumValueTypes();

            if (typeWide != typeNew && typeWide.isA(reg.getOriginalType()))
                {
                typeNew = typeWide;
                }

            if (fCond)
                {
                typeNew = typeNew.union(pool(), typeOld);
                }

            // there is no reason to be too smart and make unnecessary inferences; one example
            // is a consumer-only type C<T> that would be widened if the formal type T has been
            // narrowed by another inference
            if (typeNew.isA(typeOld) || typeNew.isA(typeOrig))
                {
                ctx.narrowLocalRegister(getName(), reg, branch, typeNew);
                }
            }
        }

    @Override
    public void resetLValueTypes(Context ctx)
        {
        ctx.restoreOriginalType(getName());
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return isValidated()
                ? getType()
                : getImplicitType(ctx, null, ErrorListener.BLACKHOLE);
        }

    /**
     * Determine the type that this expression will resolve to, if it is given some inference
     * information.
     *
     * @param ctx          the compiler context
     * @param typeDesired  the (optional) type to attempt to fulfill during translation
     * @param errs         the error list to log errors to
     *
     * @return the resolved type or null if it cannot be resolved
     */
    public TypeConstant getImplicitType(Context ctx, TypeConstant typeDesired, ErrorListener errs)
        {
        Argument arg = resolveRawArgument(ctx, true, errs);

        // we need the "raw argument" to determine the actual type
        return arg == null
                ? null
                : planCodeGen(ctx, arg, getImplicitTrailingTypeParameters(ctx), typeDesired, true, errs);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        if (typeRequired == null)
            {
            return TypeFit.Fit;
            }

        if (errs == null)
            {
            errs = ErrorListener.BLACKHOLE;
            }

        return calcFit(ctx, getImplicitType(ctx, typeRequired, errs), typeRequired);
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
                return finishValidation(ctx, typeRequired, typeRequired, TypeFit.NoFit, null, errs);
                }
            left = leftNew;
            }

        // resolve the name to a "raw" argument, i.e. what does the name refer to, without
        // consideration to read-only vs. read-write, reference vs. de-reference, static vs.
        // virtual, and so on
        boolean fInferring = typeRequired != null && !typeRequired.equals(pool().typeObject());
        if (fInferring)
            {
            ctx = ctx.enterInferring(typeRequired);
            }

        Argument argRaw = resolveRawArgument(ctx, true, errs);
        boolean  fValid = argRaw != null;

        if (fInferring)
            {
            ctx = ctx.exit();
            }

        // validate the type parameters
        TypeConstant[] atypeParams = null;
        ConstantPool   pool        = pool();
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
            return finishValidation(ctx, typeRequired, typeRequired, TypeFit.NoFit, null, errs);
            }

        // translate the raw argument into the appropriate contextual meaning
        TypeConstant type = planCodeGen(ctx, argRaw, atypeParams, typeRequired, false, errs);
        if (type == null)
            {
            // an error must've been reported
            return null;
            }
        argRaw = m_arg; // may have been modified by planCodeGen()

        Constant constVal = null;
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
                        assert argRaw instanceof IdentityConstant ||
                               argRaw instanceof PseudoConstant;

                        IdentityConstant idClass = argRaw instanceof PseudoConstant constPseudo
                                ? constPseudo.getDeclarationLevelClass()
                                : (IdentityConstant) argRaw;
                        constVal = pool.ensureSingletonConstConstant(idClass);
                        break;

                    case TypeOfClass:
                        constVal = type;
                        break;

                    default:
                        throw new IllegalStateException("plan=" + m_plan);
                    }
                break;

            case Type:
                // the class could either be identified (in the raw) by an identity
                // constant a relative (pseudo) constant or a typedef
                assert argRaw instanceof IdentityConstant ||
                       argRaw instanceof PseudoConstant   ||
                       argRaw instanceof TypedefConstant  ;
                constVal = type;
                break;

            case Property:
                // a non-constant property is ONLY a constant in identity mode; a constant
                // property is only a constant iff the property itself has a compile-time
                // constant
                PropertyConstant id = (PropertyConstant) argRaw;
                switch (m_plan)
                    {
                    case Singleton:
                    case PropertyDeref:
                        PropertyStructure prop = (PropertyStructure) id.getComponent();
                        if (prop.isRuntimeConstant())
                            {
                            constVal = prop.getInitialValue();
                            if (constVal instanceof DeferredValueConstant &&
                                    !errs.hasSeriousErrors())
                                {
                                // this error is very unlikely to surface up; most commonly it
                                // will simply force another cycle in the name resolution
                                log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                                return null;
                                }

                            if (constVal == null)
                                {
                                constVal = pool.ensureSingletonConstConstant(id);
                                }
                            }
                        break;

                    case PropertySelf:
                        {
                        assert type.isA(pool.typeProperty());

                        TypeConstant typeParent = left == null
                                ? ctx.getThisType()
                                : m_fClassAttribute || !isIdentityMode(ctx, false)
                                        ? left.getType()
                                        : left.getType().getParamType(0);
                        constVal = pool.ensurePropertyClassTypeConstant(typeParent, id);
                        break;
                        }

                    case None:
                        {
                        assert type.isA(pool.typeProperty());
                        assert left == null || left.getType().isA(pool.typeClass());

                        TypeConstant typeParent = left == null
                                ? ctx.getThisType()
                                : m_fClassAttribute
                                        ? left.getType()
                                        : left.getType().getParamType(0);
                        constVal = pool.ensurePropertyClassTypeConstant(typeParent, id);
                        break;
                        }
                    }
                break;

            case Method:
                {
                MethodConstant idMethod = (MethodConstant) argRaw;
                switch (m_plan)
                    {
                    case None:
                        if (m_mapTypeParams == null)
                            {
                            constVal = idMethod;
                            }
                        break;

                    case BindTarget:
                        if (left == null)
                            {
                            ctx.requireThis(getStartPosition(), errs);
                            }
                        else if (left instanceof NameExpression exprName &&
                                exprName.getMeaning() == Meaning.Class)
                            {
                            IdentityConstant idClz = exprName.getIdentity(ctx);
                            ClassStructure   clz   = (ClassStructure) idClz.getComponent();
                            if (!clz.isSingleton())
                                {
                                log(errs, Severity.ERROR, Compiler.NO_THIS_METHOD,
                                        idMethod.getValueString(), exprName.getName());
                                }
                            }
                        break;

                    case BjarneLambda:
                        constVal = m_idBjarnLambda;
                        break;
                    }
                }
            }

        if (left == null && isRValue())
            {
            switch (getMeaning())
                {
                case Variable:
                    if (type.containsFormalType(true))
                        {
                        ctx.useFormalType(type, errs);
                        }

                    ctx.markVarRead(getNameToken(), !isSuppressDeref(), errs);
                    break;

                case Reserved:
                    ctx.markVarRead(getNameToken(), true, errs);
                    break;

                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) argRaw;

                    if (idProp.isFormalType())
                        {
                        ctx.useFormalType(idProp.getFormalType(), errs);
                        }
                    else if (!idProp.getComponent().isStatic() && m_plan != Plan.PropertySelf)
                        {
                        // we don't need "this" to access a property on a singleton unless it's
                        // within an initializer (to avoid a circular reference)
                        PropertyStructure prop   = (PropertyStructure) idProp.getComponent();
                        MethodStructure   method = ctx.getMethod();
                        if (prop.getParent() instanceof ClassStructure clzParent &&
                                clzParent.isSingleton() &&
                                (method == null || !method.isPotentialInitializer()))
                            {
                            m_propAccessPlan    = PropertyAccess.SingletonParent;
                            m_idSingletonParent = clzParent.getIdentityConstant();
                            }
                        // there is a read of the implicit "this" variable
                        else if (getParent() instanceof NameExpression)
                            {
                            if (!ctx.requireThis(getStartPosition(), null))
                                {
                                // we know that this expression represents a property but there is
                                // no "this"; we can only proceed with the identity mode here;
                                // it becomes the outer expression's job to report any errors that
                                // result from this decision
                                m_plan = Plan.PropertyIdentity;
                                }
                            }
                        else
                            {
                            ctx.requireThis(getStartPosition(), errs);
                            }
                        }
                    break;
                    }
                }
            }

        if (left instanceof NameExpression exprName && exprName.getMeaning() == Meaning.Label)
            {
            LabelVar labelVar = (LabelVar) ctx.getVar(exprName.getNameToken(), errs);
            String   sVar     = getName();
            if (labelVar.isPropReadable(sVar))
                {
                labelVar.markPropRead(ctx, sVar);
                }
            else
                {
                String sLabel = exprName.getName();
                log(errs, Severity.ERROR, Compiler.LABEL_VARIABLE_ILLEGAL, sVar, sLabel);
                return finishValidation(ctx, typeRequired, null, TypeFit.NoFit, null, errs);
                }
            }

        // if the type could fully be resolved in the current context, do it now
        if (type != null && type.isGenericType())
            {
            TypeConstant typeResolved = type.resolveGenerics(pool, ctx.getThisType());
            if (!typeResolved.isGenericType())
                {
                type = typeResolved;
                }
            }

        return finishValidation(ctx, typeRequired, type, TypeFit.Fit, constVal, errs);
        }

    @Override
    public boolean isCompletable()
        {
        return left == null || left.isCompletable();
        }

    @Override
    public boolean isConditionalResult()
        {
        return getValueCount() == 1 && isConstantFalse();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return left != null && left.isShortCircuiting();
        }

    @Override
    protected SideEffect mightAffect(Expression exprLeft, Argument arg)
        {
        switch (super.mightAffect(exprLeft, arg))
            {
            case DefNo:
                return SideEffect.DefNo;

            case AnyCompute:
                return getMeaning() == Meaning.Property && !isSuppressDeref()
                        ? SideEffect.DefYes
                        : SideEffect.DefNo;

            case Unknown:
                // we know that "left" expression is not a property, so "this" expression can only
                // impact the left one if both refer to the same register and "this" expression is
                // held by a SequentialAssignmentExpression
                return exprLeft instanceof NameExpression that &&
                       this.getMeaning() == Meaning.Variable &&
                       that.getMeaning() == Meaning.Variable &&
                       this.getName().equals(that.getName())
                            ? SideEffect.AnySeqOp
                            : SideEffect.DefNo;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public boolean isTraceworthy()
        {
        if (!isCompletable())
            {
            return false;
            }

        return switch (getMeaning())
            {
            case Variable,
                 Property,
                 FormalChildType -> true; // TODO - some of these are traceworthy, right?
            case Reserved,
                 Unknown,
                 Method,
                 Class,
                 Type,
                 Label           -> false;
            };
        }

    @Override
    protected void selectTraceableExpressions(Map<String, Expression> mapExprs)
        {
        switch (getMeaning())
            {
            case Variable:
            case Property:
            case FormalChildType:
                // stop recursing further
                return;
            }
        super.selectTraceableExpressions(mapExprs);
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
                        ctx.markVarWrite(getNameToken(), fCond, errs);
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
                    assert getMeaning() == Meaning.Class || getMeaning() == Meaning.Reserved;

                    Access access = getType().getAccess();
                    int    cSteps = argRaw instanceof ParentClassConstant constParent
                            ? constParent.getDepth()
                            : ((TargetInfo) argRaw).getStepsOut();

                    code.add(new MoveThis(cSteps, argLVal, access));
                    m_astResult = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, getType());
                    return;
                    }

                case OuterRef:
                    {
                    assert getMeaning() == Meaning.Class;

                    int cSteps = argRaw instanceof ParentClassConstant constParent
                            ? constParent.getDepth()
                            : 0;

                    TypeConstant typeRef      = getType();
                    TypeConstant typeReferent = typeRef.getParamType(0);

                    Register regTemp = code.createRegister(typeReferent);
                    code.add(new MoveThis(cSteps, regTemp, typeRef.getAccess()));
                    code.add(new MoveRef(regTemp, argLVal));

                    ExprAST astTemp = ctx.getThisRegisterAST();
                    if (cSteps > 0)
                        {
                        astTemp = new OuterExprAST(astTemp, cSteps, typeReferent);
                        }
                    if (typeRef.getAccess() != typeReferent.getAccess())
                        {
                        Operator op = switch (typeRef.getAccess())
                            {
                            case PUBLIC    -> Operator.Public;
                            case PROTECTED -> Operator.Protected;
                            case PRIVATE   -> Operator.Private;
                            default        -> throw new IllegalStateException();
                            };
                        astTemp = new UnaryOpExprAST(astTemp, op, getType());
                        }
                    m_astResult = new UnaryOpExprAST(astTemp, Operator.Ref, getType());
                    return;
                    }

                case PropertyDeref:
                    {
                    if (left instanceof NameExpression exprName &&
                            exprName.getMeaning() == Meaning.Label)
                        {
                        break;
                        }

                    if (!LVal.supportsLocalPropMode())
                        {
                        // local property mode
                        break;
                        }

                    PropertyConstant idProp = (PropertyConstant) argRaw;
                    switch (calculatePropertyAccess(false))
                        {
                        case SingletonParent:
                            {
                            SingletonConstant idSingleton =
                                pool().ensureSingletonConstConstant(m_idSingletonParent);
                            code.add(new P_Get(idProp, idSingleton, argLVal));

                            ExprAST astSingleton = new ConstantExprAST(idSingleton);
                            m_astResult = new PropertyExprAST(astSingleton, idProp);
                            break;
                            }

                        case Outer:
                            {
                            int          cSteps   = m_targetInfo.getStepsOut();
                            TypeConstant type     = m_targetInfo.getType();
                            Register     regOuter = new Register(type, null, Op.A_STACK);
                            code.add(new MoveThis(cSteps, regOuter, type.getAccess()));
                            code.add(new P_Get(idProp, regOuter, argLVal));

                            ExprAST astOuter = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, type);
                            m_astResult = new PropertyExprAST(astOuter, idProp);
                            break;
                            }

                        case This:
                            if (idProp.equals(argLVal))
                                {
                                log(errs, Severity.ERROR, Compiler.PROP_SELF_ASSIGNED,
                                        idProp.getName());
                                }
                            code.add(new L_Get(idProp, argLVal));

                            m_astResult = new PropertyExprAST(ctx.getThisRegisterAST(), idProp);
                            break;

                        case Left:
                            {
                            assert !idProp.getComponent().isStatic();
                            Argument argLeft = left.generateArgument(ctx, code, false, true, errs);
                            code.add(new P_Get(idProp, argLeft, argLVal));

                            m_astResult = new PropertyExprAST(left.getExprAST(ctx), idProp);
                            break;
                            }
                        }
                    return;
                    }

                case PropertyRef:
                    {
                    PropertyConstant idProp    = (PropertyConstant) argRaw;
                    Argument         argTarget = generateRefTarget(ctx, code, idProp, errs);

                    if ("outer".equals(idProp.getName()))
                        {
                        TypeConstant typeTarget = argTarget.getType().resolveConstraints();
                        TypeConstant typeOuter  = typeTarget.isVirtualChild()
                                ? typeTarget.getParentType()
                                : pool().typeObject();

                        Register regOuter = new Register(typeOuter, null, Op.A_STACK);
                        code.add(new P_Get(idProp, argTarget, regOuter));
                        code.add(new MoveRef(regOuter, argLVal));
                        }
                    else
                        {
                        code.add(m_fAssignable
                                ? new P_Var(idProp, argTarget, argLVal)
                                : new P_Ref(idProp, argTarget, argLVal));
                        }

                    ExprAST astTarget = new PropertyExprAST(m_astRefTarget, idProp);
                    m_astResult = new UnaryOpExprAST(astTarget,
                                    m_fAssignable ? Operator.Var : Operator.Ref, getType());
                    return;
                    }

                case RegisterRef:
                    {
                    Register regRVal = (Register) argRaw;

                    code.add(m_fAssignable
                            ? new MoveVar(regRVal, argLVal)
                            : new MoveRef(regRVal, argLVal));

                    m_astResult = new UnaryOpExprAST(regRVal.getRegisterAST(),
                                    m_fAssignable ? Operator.Var : Operator.Ref, getType());
                    return;
                    }

                case BindTarget:
                    {
                    MethodConstant idMethod  = (MethodConstant) argRaw;
                    Argument       argTarget;
                    ExprAST        astTarget;
                    if (left == null)
                        {
                        int cSteps = m_targetInfo == null ? 0 : m_targetInfo.getStepsOut();
                        if (cSteps > 0)
                            {
                            TypeConstant typeTarget = m_targetInfo.getTargetType();
                            argTarget = new Register(typeTarget, null, Op.A_STACK);
                            code.add(new MoveThis(cSteps, argTarget, typeTarget.getAccess()));

                            astTarget = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeTarget);
                            }
                        else
                            {
                            argTarget = ctx.getThisRegister();
                            astTarget = ctx.getThisRegisterAST();
                            }
                        }
                    else
                        {
                        argTarget = left.generateArgument(ctx, code, true, true, errs);
                        astTarget = left.getExprAST(ctx);
                        }

                    if (m_mapTypeParams == null)
                        {
                        code.add(new MBind(argTarget, idMethod, argLVal));
                        }
                    else
                        {
                        Register regFn = new Register(pool().typeFunction(), null, Op.A_STACK);
                        code.add(new MBind(argTarget, idMethod, regFn));
                        bindTypeParameters(ctx, code, regFn, argLVal);
                        }
                    m_astResult = new BindMethodAST(astTarget, idMethod, getType());
                    return;
                    }

                case BjarneLambda:
                    {
                    MethodConstant idHandler = m_idBjarnLambda;

                    code.add(new Move(idHandler, argLVal));

                    m_astResult = new ConstantExprAST(idHandler);
                    return;
                    }
                }
            }

        super.generateAssignment(ctx, code, LVal, errs);
        }

    @Override
    public Argument generateArgument(Context ctx, Code code,
                                     boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        Argument argRaw = m_arg;
        switch (m_plan)
            {
            case None:
                switch (getMeaning())
                    {
                    case Reserved:
                        if (left instanceof NameExpression nameLeft &&
                                nameLeft.m_plan == Plan.OuterThis)
                            {
                            // indicates a "this.C" scenario used inside a "property class" for this
                            // class property
                            TypeConstant typeOuter = argRaw.getType();
                            Register     regOuter  = code.createRegister(typeOuter, fUsedOnce);
                            code.add(new MoveThis(1, regOuter, typeOuter.getAccess()));

                            m_astResult = new OuterExprAST(ctx.getThisRegisterAST(), 1, typeOuter);
                            assert m_mapTypeParams == null;
                            return regOuter;
                            }
                        break;

                        case Label:
                            throw new IllegalStateException();

                        default:
                            break;
                        }

                if (m_mapTypeParams != null)
                    {
                    Register regFn = code.createRegister(argRaw.getType(), fUsedOnce);
                    bindTypeParameters(ctx, code, argRaw, regFn);
                    System.err.println("TODO: AST for " + this);
                    // TODO GG: m_astResult =
                    return regFn;
                    }
                m_astResult = toExprAst(argRaw);
                return argRaw;

            case OuterThis:
                {
                TypeConstant typeOuter;
                int          cSteps;
                if (argRaw instanceof ParentClassConstant constParent)
                    {
                    typeOuter = getType();
                    cSteps    = constParent.getDepth();
                    }
                else
                    {
                    TargetInfo targetInfo = (TargetInfo) argRaw;
                    typeOuter = targetInfo.getType();
                    cSteps    = targetInfo.getStepsOut();
                    }

                if (left instanceof NameExpression nameLeft &&
                        nameLeft.m_plan == Plan.OuterThis)
                    {
                    // indicates a "this.C" scenario used inside a "property class" for a child
                    // class property
                    cSteps++;
                    }

                Register regOuter = code.createRegister(typeOuter, fUsedOnce);
                code.add(new MoveThis(cSteps, regOuter, typeOuter.getAccess()));

                m_astResult = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeOuter);
                return regOuter;
                }

            case OuterRef:
                {
                TypeConstant typeRef;
                TypeConstant typeOuter;
                int          cSteps;
                if (argRaw instanceof TargetInfo targetInfo)
                    {
                    typeOuter = targetInfo.getType();
                    typeRef   = pool().ensureParameterizedTypeConstant(pool().typeRef(), typeOuter);
                    cSteps    = targetInfo.getStepsOut();
                    }
                else
                    {
                    typeRef   = getType();
                    typeOuter = typeRef.getParamType(0);
                    cSteps    = argRaw instanceof ParentClassConstant constParent
                                ? constParent.getDepth()
                                : 0;
                    }

                Register regOuter = code.createRegister(typeOuter, fUsedOnce);
                code.add(new MoveThis(cSteps, regOuter, typeOuter.getAccess()));

                Register regRef = code.createRegister(typeRef, fUsedOnce);
                code.add(new MoveRef(regOuter, regRef));

                m_astResult = new UnaryOpExprAST(
                        new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeOuter),
                        Operator.Ref, typeRef);
                return regRef;
                }

            case PropertyDeref:
                {
                if (left instanceof NameExpression nameLeft)
                    {
                    if (nameLeft.getMeaning() == Meaning.Label)
                        {
                        LabelVar labelVar = (LabelVar) nameLeft.m_arg;
                        Register regLabel = labelVar.getPropRegister(ctx, getName());
                        m_astResult = regLabel.getRegisterAST();
                        return regLabel;
                        }
                    }

                PropertyConstant idProp  = (PropertyConstant) argRaw;
                Register         regTemp = code.createRegister(getType(), fUsedOnce);

                switch (calculatePropertyAccess(false))
                    {
                    case SingletonParent:
                        {
                        SingletonConstant idSingleton =
                            pool().ensureSingletonConstConstant(m_idSingletonParent);

                        code.add(new P_Get(idProp, idSingleton, regTemp));

                        ExprAST astSingleton = new ConstantExprAST(idSingleton);
                        m_astResult = new PropertyExprAST(astSingleton, idProp);
                        break;
                        }

                    case Outer:
                        {
                        int          cSteps    = m_targetInfo.getStepsOut();
                        TypeConstant typeOuter = m_targetInfo.getType();
                        Register     regOuter  = new Register(typeOuter, null, Op.A_STACK);

                        code.add(new MoveThis(cSteps, regOuter, typeOuter.getAccess()));
                        if (idProp.isFutureVar())
                            {
                            regTemp = code.createRegister(typeOuter);
                            code.add(new Var_D(regTemp));
                            }
                        code.add(new P_Get(idProp, regOuter, regTemp));

                        ExprAST astOuter = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeOuter);
                        m_astResult = new PropertyExprAST(astOuter, idProp);
                        break;
                        }

                    case This:
                        if ("outer".equals(idProp.getName()))
                            {
                            code.add(new MoveThis(1, regTemp));

                            m_astResult = new OuterExprAST(ctx.getThisRegisterAST(), 1, getType());
                            }
                        else
                            {
                            if (idProp.isFutureVar())
                                {
                                regTemp = code.createRegister(idProp.getRefType(ctx.getThisType()));
                                code.add(new Var_D(regTemp));
                                }
                            else
                                {
                                if (fLocalPropOk || idProp.getComponent().isStatic())
                                    {
                                    return idProp;
                                    }
                                }
                            code.add(new L_Get(idProp, regTemp));

                            m_astResult = new PropertyExprAST(ctx.getThisRegisterAST(), idProp);
                            }
                        break;

                    case Left:
                        {
                        if (idProp.getComponent().isStatic())
                            {
                            return idProp;
                            }
                        Argument argLeft = left.generateArgument(ctx, code, false, true, errs);
                        if (idProp.isFutureVar())
                            {
                            regTemp = code.createRegister(idProp.getRefType(argLeft.getType()));
                            code.add(new Var_D(regTemp));
                            }
                        code.add(new P_Get(idProp, argLeft, regTemp));

                        m_astResult = new PropertyExprAST(left.getExprAST(ctx), idProp);
                        break;
                        }

                    default:
                        throw new IllegalStateException();
                    }
                return regTemp;
                }

            case PropertyRef:
                {
                PropertyConstant idProp    = (PropertyConstant) argRaw;
                Argument         argTarget = generateRefTarget(ctx, code, idProp, errs);
                Register         regRef;

                if ("outer".equals(idProp.getName()))
                    {
                    ConstantPool pool       = pool();
                    TypeConstant typeTarget = argTarget.getType().resolveConstraints();
                    TypeConstant typeOuter  = typeTarget.isVirtualChild()
                            ? typeTarget.getParentType()
                            : pool.typeObject();

                    Register regOuter = new Register(typeOuter, null, Op.A_STACK);
                    code.add(new P_Get(idProp, argTarget, regOuter));

                    TypeConstant typeRef = pool.ensureParameterizedTypeConstant(
                                                pool.typeRef(), typeOuter);
                    regRef = code.createRegister(typeRef, fUsedOnce);
                    code.add(new MoveRef(regOuter, regRef));
                    }
                else
                    {
                    regRef = code.createRegister(getType(), fUsedOnce);
                    code.add(m_fAssignable
                            ? new P_Var(idProp, argTarget, regRef)
                            : new P_Ref(idProp, argTarget, regRef));
                    }
                ExprAST astTarget = new PropertyExprAST(m_astRefTarget, idProp);
                m_astResult = new UnaryOpExprAST(astTarget,
                                m_fAssignable ? Operator.Var : Operator.Ref, getType());
                return regRef;
                }

            case RegisterRef:
                {
                Register regVal = (Register) argRaw;
                Register regRef = code.createRegister(getType(), fUsedOnce);
                code.add(m_fAssignable
                        ? new MoveVar(regVal, regRef)
                        : new MoveRef(regVal, regRef));

                m_astResult = new UnaryOpExprAST(regVal.getRegisterAST(),
                                m_fAssignable ? Operator.Var : Operator.Ref, getType());
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
                Register regType   = code.createRegister(idChild.getType(), fUsedOnce);
                code.add(new P_Get(idChild, argTarget, regType));

                m_astResult = new PropertyExprAST(left.getExprAST(ctx), idChild);
                return regType;
                }

            case BindTarget:
                {
                MethodConstant idMethod = (MethodConstant) argRaw;
                Argument       argTarget;
                ExprAST        astTarget;
                if (left == null)
                    {
                    int cSteps = m_targetInfo == null ? 0 : m_targetInfo.getStepsOut();
                    if (cSteps > 0)
                        {
                        TypeConstant typeTarget = m_targetInfo.getTargetType();
                        argTarget = new Register(typeTarget, null, Op.A_STACK);
                        code.add(new MoveThis(cSteps, argTarget, typeTarget.getAccess()));

                        astTarget = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeTarget);
                        }
                    else
                        {
                        argTarget = ctx.getThisRegister();
                        astTarget = ctx.getThisRegisterAST();
                        }
                    }
                else
                    {
                    argTarget = left.generateArgument(ctx, code, true, true, errs);
                    astTarget = left.getExprAST(ctx);
                    }

                Register regFn = code.createRegister(idMethod.getType(), fUsedOnce);
                if (m_mapTypeParams == null)
                    {
                    code.add(new MBind(argTarget, idMethod, regFn));
                    }
                else
                    {
                    Register regFn0 = code.createRegister(pool().typeFunction());
                    code.add(new MBind(argTarget, idMethod, regFn0));
                    bindTypeParameters(ctx, code, regFn0, regFn);
                    }
                m_astResult = new BindMethodAST(astTarget, idMethod, getType());
                return regFn;
                }

            case BjarneLambda:
                {
                MethodConstant idHandler = m_idBjarnLambda;

                m_astResult = new ConstantExprAST(idHandler);
                return idHandler;
                }

            default:
                throw new IllegalStateException("arg=" + argRaw);
            }
        }

    /**
     * Create a {@link MethodConstant#getBjarneLambdaType Bjarne lambda} function for the specified
     * method.
     *
     * Note, that for every occurrence of an expression in the form of "T.m(a)" that requires
     * production of a function that takes an argument "t" of the target type "T" at index zero,
     * this method creates a new lambda performing the following transformation:
     *      {@code (t, a, ...) -> t.m(a, ...)}
     *
     * @param clz       the containing class
     * @param idMethod  the underlying method
     * @param cReturns  the number of return values expected by the caller
     *
     * @return the BjarneLambda id
     */
    private MethodConstant createBjarneLambda(ClassStructure clz, MethodConstant idMethod,
                                              int cReturns)
        {
        ConstantPool         pool        = pool();
        MultiMethodStructure mms         = clz.ensureMultiMethodStructure("->");
        TypeConstant         typeLambda  = idMethod.getBjarneLambdaType();
        TypeConstant[]       atypeParam  = pool.extractFunctionParams(typeLambda);
        TypeConstant[]       atypeReturn = pool.extractFunctionReturns(typeLambda);

        int cParams      = atypeParam.length;
        int cOrigReturns = atypeReturn.length;

        assert cParams > 0 && cReturns <= cOrigReturns;

        org.xvm.asm.Parameter[] aparamParam = new org.xvm.asm.Parameter[cParams];
        for (int i = 0; i < cParams; i++)
            {
            aparamParam[i] = new org.xvm.asm.Parameter(pool, atypeParam[i], "p"+i, null, false, i, false);
            }

        org.xvm.asm.Parameter[] aparamReturn = new org.xvm.asm.Parameter[cReturns];
        for (int i = 0; i < cReturns; i++)
            {
            aparamReturn[i] = new org.xvm.asm.Parameter(pool, atypeReturn[i], null, null, true, i, false);
            }

        if (cReturns < cOrigReturns)
            {
            atypeReturn = Arrays.copyOfRange(atypeReturn, 0, cReturns);
            }

        MethodStructure lambda = mms.createLambda(TypeConstant.NO_TYPES, Utils.NO_NAMES);

        lambda.configureLambda(aparamParam, 0, aparamReturn);
        lambda.setStatic(true);
        lambda.getIdentityConstant().setSignature(
                pool.ensureSignatureConstant("->", atypeParam, atypeReturn));

        Code     code      = lambda.createCode();
        Register regTarget = new Register(atypeParam[0], null, 0);
        switch (cParams-1)
            {
            case 0:
                {
                switch (cReturns)
                    {
                    case 0:
                        code.add(new Invoke_00(regTarget, idMethod));
                        code.add(Return_0.INSTANCE);
                        break;

                    case 1:
                        {
                        Register regRet = new Register(atypeReturn[0], null, Op.A_STACK);
                        code.add(new Invoke_01(regTarget, idMethod, regRet));
                        code.add(new Return_1(regRet));
                        break;
                        }

                    default:
                        {
                        Register[] aregRet = new Register[cReturns];
                        for (int i = 0; i < cReturns; i++)
                            {
                            aregRet[i] = new Register(atypeReturn[i], null, Op.A_STACK);
                            }
                        code.add(new Invoke_0N(regTarget, idMethod, aregRet));
                        code.add(new Return_N(aregRet));
                        break;
                        }
                    }
                break;
                }

            case 1:
                {
                Register regParam = new Register(atypeParam[1], null, 1);
                switch (cReturns)
                    {
                    case 0:
                        code.add(new Invoke_10(regTarget, idMethod, regParam));
                        code.add(Return_0.INSTANCE);
                        break;

                    case 1:
                        {
                        Register regRet = new Register(atypeReturn[0], null, Op.A_STACK);
                        code.add(new Invoke_11(regTarget, idMethod, regParam, regRet));
                        code.add(new Return_1(regRet));
                        break;
                        }

                    default:
                        {
                        Register[] aregRet = new Register[cReturns];
                        for (int i = 0; i < cReturns; i++)
                            {
                            aregRet[i] = new Register(atypeReturn[i], null, Op.A_STACK);
                            }
                        code.add(new Invoke_1N(regTarget, idMethod, regParam, aregRet));
                        code.add(new Return_N(aregRet));
                        break;
                        }
                    }
                break;
                }

            default:
                {
                Register[] aregParam = new Register[cParams];
                for (int i = 1; i < cParams; i++)
                    {
                    aregParam[i] = new Register(atypeParam[i], null, i);
                    }
                switch (cReturns)
                    {
                    case 0:
                        code.add(new Invoke_N0(regTarget, idMethod, aregParam));
                        code.add(Return_0.INSTANCE);
                        break;

                    case 1:
                        {
                        Register regRet = new Register(atypeReturn[0], null, Op.A_STACK);
                        code.add(new Invoke_N1(regTarget, idMethod, aregParam, regRet));
                        code.add(new Return_1(regRet));
                        break;
                        }

                    default:
                        {
                        Register[] aregRet = new Register[cReturns];
                        for (int i = 0; i < cReturns; i++)
                            {
                            aregRet[i] = new Register(atypeReturn[i], null, Op.A_STACK);
                            }
                        code.add(new Invoke_NN(regTarget, idMethod, aregParam, aregRet));
                        code.add(new Return_N(aregRet));
                        break;
                        }
                    }
                break;
                }
            }

        lambda.forceAssembly(pool);

        return lambda.getIdentityConstant();
        }

    /**
     * Create a {@link MethodConstant#getBjarneLambdaType Bjarne lambda} function for the specified
     * property getter.
     *
     * Note, that for every occurrence of an expression in the form of "T.p" that requires
     * production of a function that takes an argument "t" of the target type "T" at index zero,
     * this method creates a new lambda performing the following transformation:
     *      t -> t.p
     *
     * @param clz       the containing class
     * @param idProp  the underlying property
     *
     * @return the BjarneLambda id
     */
    private MethodConstant createBjarneLambda(ClassStructure clz, PropertyConstant idProp)
        {
        ConstantPool         pool       = pool();
        MultiMethodStructure mms        = clz.ensureMultiMethodStructure("->");
        TypeConstant         typeParam  = idProp.getNamespace().getType();
        TypeConstant         typeReturn = idProp.getType();

        org.xvm.asm.Parameter[] aparamParam = new org.xvm.asm.Parameter[]
            {new org.xvm.asm.Parameter(pool, typeParam, "p", null, false, 0, false)};

        org.xvm.asm.Parameter[] aparamReturn = new org.xvm.asm.Parameter[]
            {new org.xvm.asm.Parameter(pool, typeReturn, null, null, true, 0, false)};

        MethodStructure lambda = mms.createLambda(TypeConstant.NO_TYPES, Utils.NO_NAMES);

        lambda.configureLambda(aparamParam, 0, aparamReturn);
        lambda.setStatic(true);
        lambda.getIdentityConstant().setSignature(
                pool.ensureSignatureConstant("->",
                    new TypeConstant[]{typeParam}, new TypeConstant[]{typeReturn}));

        Code     code      = lambda.createCode();
        Register regTarget = new Register(typeParam, null, 0);
        Register regRet    = new Register(typeReturn, null, Op.A_STACK);

        code.add(new P_Get(idProp, regTarget, regRet));
        code.add(new Return_1(regRet));
        lambda.forceAssembly(pool);

        return lambda.getIdentityConstant();
        }

    /**
     * Helper method to generate an argument for the property Ref target.
     */
    private Argument generateRefTarget(Context ctx, Code code,
                                       PropertyConstant idProp, ErrorListener errs)
        {
        switch (calculatePropertyAccess(true))
            {
            case SingletonParent:
                m_astRefTarget = new ConstantExprAST(m_idSingletonParent);
                return pool().ensureSingletonConstConstant(m_idSingletonParent);

            case Outer:
                {
                int          cSteps    = m_targetInfo.getStepsOut();
                TypeConstant typeOuter = m_targetInfo.getTargetType().ensureAccess(Access.PRIVATE);
                Register     regOuter  = new Register(typeOuter, null, Op.A_STACK);
                code.add(new MoveThis(cSteps, regOuter, Access.PRIVATE));

                m_astRefTarget = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, typeOuter);
                return regOuter;
                }

            case This:
                m_astRefTarget = ctx.getThisRegisterAST();
                return ctx.getThisRegister();

            case Left:
                assert !idProp.getComponent().isStatic();
                Argument arg = left.generateArgument(ctx, code, true, true, errs);
                m_astRefTarget = left.getExprAST(ctx);
                return arg;

            default:
                throw new IllegalStateException();
            }
        }

    private void bindTypeParameters(Context ctx, Code code, Argument argFnOrig, Argument argFnResult)
        {
        List<Map.Entry<FormalConstant, TypeConstant>> list = m_mapTypeParams.asList();

        int        cParams  = list.size();
        int[]      anBindIx = new int[cParams];
        Argument[] aArgBind = new Argument[cParams];

        for (int i = 0; i < cParams; i++)
            {
            Map.Entry<FormalConstant, TypeConstant> entry = list.get(i);

            FormalConstant constFormal = entry.getKey();
            TypeConstant   type        = entry.getValue();

            anBindIx[i] = i;
            if (type.isGenericType())
                {
                TypeInfo infoThis = ctx.getThisType().ensureTypeInfo();

                // first type goes on stack
                Register regType = code.createRegister(pool().typeType(), i == 0);
                code.add(new L_Get(infoThis.findProperty(constFormal.getName()).getIdentity(), regType));

                aArgBind[i] = regType;
                }
            else if (type.isTypeParameter())
                {
                int iReg = ((TypeParameterConstant) constFormal).getRegister();
                aArgBind[i] = ctx.getParameter(iReg);
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
            if (arg instanceof Register reg)
                {
                assert target == null;
                m_astResult = reg.getRegisterAST();
                return new Assignable(reg);
                }
            if (arg instanceof PropertyConstant idProp)
                {
                Argument argTarget;
                ExprAST  astTarget;
                if (left == null)
                    {
                    Register regTarget;
                    switch (m_propAccessPlan)
                        {
                        case SingletonParent:
                            {
                            IdentityConstant  idParent    = m_idSingletonParent;
                            SingletonConstant idSingleton = pool().ensureSingletonConstConstant(idParent);
                            regTarget = code.createRegister(idParent.getType());
                            code.add(new Var_I(regTarget, idSingleton));

                            astTarget = regTarget.getRegAllocAST();
                            break;
                            }

                        case This:
                            regTarget = ctx.getThisRegister();
                            astTarget = regTarget.getRegisterAST();
                            break;

                        case Outer:
                            {
                            ClassStructure clz   = ctx.getThisClass();
                            int           cSteps = target.getStepsOut();
                            for (int i = cSteps; --i >= 0;)
                                {
                                clz = clz.getContainingClass();
                                }
                            regTarget = new Register(clz.getFormalType(), null, Op.A_STACK);
                            code.add(new MoveThis(cSteps, regTarget));

                            astTarget = new OuterExprAST(ctx.getThisRegisterAST(), cSteps, getType());
                            break;
                            }

                        default:
                            throw new IllegalStateException();
                        }
                    argTarget = regTarget;
                    }
                else
                    {
                    argTarget = left.generateArgument(ctx, code, true, true, errs);
                    astTarget = left.getExprAST(ctx);
                    }
                m_astResult = new PropertyExprAST(astTarget, idProp);
                return new Assignable(argTarget, idProp);
                }

            return super.generateAssignable(ctx, code, errs);
            }

        return null;
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        if (m_astResult != null)
            {
            return m_astResult;
            }

        // there is a possibility that the caller never called generateArgument/Assignment, which
        // would be the case if this is an LValue

        Argument argRaw = m_arg;
        if ((argRaw instanceof PropertyConstant && m_plan == Plan.PropertyDeref ||
             argRaw instanceof Register reg && !reg.isStack())
                && !getTypeFit().isConverting())
            {
            // for [static] properties we generate a ConstantExprAST based on the property id rather
            // than the property value, leaving an optimization possibility to the BAST compiler
            return toExprAst(argRaw);
            }

        return isConstant() && isRValue()
                ? new ConstantExprAST(toConstant())
                : super.getExprAST(ctx);
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
            for (TypeExpression param : params)
                {
                TypeConstant typeParam = param.getImplicitType(ctx);
                if (typeParam == null)
                    {
                    break;
                    }

                // the typeParam comes back as "Type<String, Object>" instead of "String"; unwrap
                assert typeParam.isTypeOfType() && typeParam.isParamsSpecified();
                listTypes.add(typeParam.getParamType(0));
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
            ErrorListener errsTemp = errs.branch(this);

            Argument arg = ctx.resolveName(name, errsTemp);

            errsTemp.merge();
            if (arg == null && !errsTemp.hasSeriousErrors())
                {
                MethodStructure  method = ctx.getMethod();
                IdentityConstant idCtx  = method == null
                        ? ctx.getThisClassId()
                        : method.getIdentityConstant();

                log(errs, Severity.ERROR, Compiler.NAME_MISSING, sName, idCtx.getValueString());
                return null;
                }

            if (arg instanceof Register reg)
                {
                m_arg         = arg;
                m_fAssignable = reg.isWritable();
                }
            else if (arg instanceof Constant constant)
                {
                switch (constant.getFormat())
                    {
                    case Package:
                    case Module:
                    case Class:
                    case Typedef:
                        if (isSuppressDeref())
                            {
                            log(errs, Severity.ERROR, Compiler.INVALID_PROPERTY_REF);
                            return null;
                            }
                        // fall through
                    case Property:
                        m_arg = arg;
                        break;

                    case MultiMethod:
                        {
                        MultiMethodConstant  idMM = (MultiMethodConstant) constant;
                        MultiMethodStructure mms  = (MultiMethodStructure) idMM.getComponent();

                        Collection<MethodStructure> methods = mms.methods();

                        m_arg = methods.size() == 1
                                ? methods.iterator().next().getIdentityConstant()
                                : idMM; // return the MultiMethod; the caller will decide what to do
                        break;
                        }

                    default:
                        throw new IllegalStateException("format=" + constant.getFormat()
                                + ", constant=" + constant);
                    }
                }
            else if (arg instanceof TargetInfo target)
                {
                m_targetInfo = target;

                IdentityConstant id = target.getId();
                switch (id.getFormat())
                    {
                    case MultiMethod:
                        {
                        // TODO still some work here to
                        //      (i) save off the TargetInfo
                        //      (ii) use it in code gen
                        //      (iii) mark "this" (and outer "this") as being used
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
                        TypeConstant typeTarget = target.getTargetType();
                        TypeInfo     infoTarget = getTypeInfo(ctx, typeTarget, errs);

                        // TODO still some work here to mark the this (and outer this) as being used
                        PropertyInfo prop = infoTarget.findProperty((PropertyConstant) id);
                        if (prop == null)
                            {
                            throw new IllegalStateException("missing property: " + id + " on " + typeTarget);
                            }

                        if (infoTarget.isTopLevel() && infoTarget.isStatic() &&
                                !infoTarget.getClassStructure().equals(ctx.getThisClass())
                            ||
                               prop.isConstant() && prop.getInitializer() != null)
                            {
                            // the property's parent may not be the target class, but one of its
                            // contributions; relying on "prop.getIdentity()" would be incorrect
                            m_propAccessPlan    = PropertyAccess.SingletonParent;
                            m_idSingletonParent = infoTarget.getIdentity();
                            }
                        else if (target.getStepsOut() == 0)
                            {
                            if (!prop.isConstant() && prop.getHead().getStructure().isSynthetic() &&
                                    ctx.getMethod().isPropertyInitializer())
                                {
                                // synthetic properties (shorthand declaration) are known not to be
                                // computed until all initializers are done
                                log(errs, Severity.ERROR, Compiler.PROPERTY_INACCESSIBLE,
                                        prop.getName(), typeTarget.removeAccess().getValueString());
                                return null;
                                }
                            m_propAccessPlan = PropertyAccess.This;
                            }
                        else
                            {
                            if (!prop.isConstant() && target.getStepsOut() > 0 && !target.hasThis())
                                {
                                log(errs, Severity.ERROR, Compiler.NO_OUTER_PROPERTY,
                                        typeTarget.removeAccess().getValueString(), prop.getName());
                                return null;
                                }
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
                        m_arg = target;
                        break;

                    default:
                        throw new IllegalStateException("unsupported constant format: " + id);
                    }
                }
            }
        else // left is NOT null
            {
            // the "Type.this" construct is not supported; use "this.Type" instead
            if ("this".equals(sName))
                {
                log(errs, Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                return null;
                }

            // attempt to use identity mode (e.g. "packageName.ClassName.PropName")
            boolean fIdMode = left instanceof NameExpression exprName
                    && exprName.resolveRawArgument(ctx, false, errs) != null
                    && exprName.isIdentityMode(ctx, true);
            if (fIdMode)
                {
                NameExpression   exprLeft = (NameExpression) left;
                IdentityConstant idLeft   = exprLeft.getIdentity(ctx);
                if (idLeft.getFormat() == Constant.Format.Package)
                    {
                    PackageStructure pkg = (PackageStructure) idLeft.getComponent();
                    if (pkg.isModuleImport())
                        {
                        idLeft = pkg.getImportedModule().getIdentityConstant();
                        }
                    }

                IdentityConstant idChild;
                if (idLeft instanceof PropertyConstant idProp)
                    {
                    TypeInfo infoProp = pool.ensurePropertyClassTypeConstant(
                            idProp.getParentConstant().getFormalType().
                                ensureAccess(Access.PRIVATE), idProp).ensureTypeInfo(errs);
                    idChild = infoProp.findName(pool, sName);
                    }
                else if (idLeft.getComponent() instanceof ClassStructure clzLeft &&
                            clzLeft.findChildDeep(sName) instanceof TypedefStructure typedef)
                    {
                    idChild = typedef.getIdentityConstant();
                    }
                else
                    {
                    idChild = getTypeInfo(ctx, idLeft.getType(), errs).findName(pool, sName);
                    }

                if (idChild == null)
                    {
                    // no child of that name on "Left"; try "Class<Left>"
                    TypeInfo infoClz  = idLeft.getValueType(pool, null).ensureTypeInfo(errs);

                    idChild           = infoClz.findName(pool, sName);
                    m_fClassAttribute = idChild != null;
                    }

                if (idChild != null)
                    {
                    switch (idChild.getFormat())
                        {
                        case Package:
                        case Class:
                        case Property:
                        case Method:
                        case Typedef:
                            m_arg = idChild;
                            break;

                        case MultiMethod:
                            log(errs, Severity.ERROR, Compiler.NAME_AMBIGUOUS, sName);
                            return null;

                        case Module: // a module can't be nested
                        default:
                            throw new IllegalStateException("format=" + idChild.getFormat()
                                    + ", constant=" + idChild);
                        }
                    }
                }

            // if identity mode didn't answer the question, then use the TypeInfo to find the name
            // (e.g. "foo().x.y"
            if (m_arg == null)
                {
                // the name can refer to either a property or a typedef
                TypeConstant typeLeft = left.getImplicitType(ctx);
                if (typeLeft == null || typeLeft.containsUnresolved())
                    {
                    // we need to exempt unresolved annotation parameters with validated dynamic
                    // "ExpressionConstant" constants (see AnnotationExpression#ensureAnnoation())
                    if (!(typeLeft instanceof AnnotatedTypeConstant typeAnno)
                            || typeAnno.getUnderlyingType().containsUnresolved())
                        {
                        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, getName());
                        return null;
                        }
                    }

                if (typeLeft.isTypeOfType() && left instanceof NameExpression exprLeft)
                    {
                    FormalConstant constFormal = null;
                    switch (exprLeft.getMeaning())
                        {
                        case Variable:
                            {
                            // There are two examples of what we need to handle:
                            // a) the property is a formal property and the name refers to its
                            //    constraint's formal property: (e.g. Array.x):
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
                            // typeLeft is a type of the "CompileType" type parameter, and we need
                            // to produce "CompileType.Element" formal child constant
                            //
                            // Another example is:
                            //  Boolean f = CompileType.is(Type<Array>) && CompileType.Element.is(Type<Int>);
                            //
                            // in that case, "typeLeft" for the second expression is an intersection
                            // (CompileType + Array), but we still need to produce
                            // "CompileType.Element" formal child constant
                            //
                            // b) the name refers to a property on the Type object represented by
                            //    this variable (e.g. NullableMapping.x):
                            // <Subtype> conditional Mapping<SubType<>> narrow(Type<SubType> type)
                            //    {
                            //    if (type.form == Union) {...}
                            //    }
                            TypeConstant typeData = typeLeft.getParamType(0);
                            if (typeData.containsGenericParam(sName))
                                {
                                // typeData can be a synthetic intersection type produced by type inference
                                // logic, and we cannot simply ask it for the defining constant
                                Register     argLeft    = (Register) exprLeft.m_arg;
                                TypeConstant typeFormal = argLeft.getOriginalType().getParamType(0);
                                if (typeFormal.isFormalType())
                                    {
                                    constFormal = (FormalConstant) typeFormal.getDefiningConstant();
                                    }
                                typeLeft = typeData;
                                }
                            else if ("OuterType".equals(sName) && typeData.isFormalType())
                                {
                                constFormal = (FormalConstant) typeData.getDefiningConstant();
                                }
                            break;
                            }

                        case FormalChildType:
                            {
                            // example: CompileType.OuterType.Element
                            TypeConstant typeData = typeLeft.getParamType(0);
                            if (typeData.isFormalType() &&
                                    typeData.resolveConstraints().containsGenericParam(sName))
                                {
                                constFormal = (FormalConstant) typeData.getDefiningConstant();
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
                            PropertyConstant idProp = (PropertyConstant) exprLeft.m_arg;
                            if (idProp.isFormalType() &&
                                    idProp.getConstraintType().containsGenericParam(sName))
                                {
                                constFormal = idProp;
                                }
                            break;
                            }
                        }

                    if (constFormal != null)
                        {
                        m_fAssignable = false;
                        return m_arg  = pool.ensureFormalTypeChildConstant(constFormal, sName);
                        }
                    }

                TypeInfo infoLeft     = getTypeInfo(ctx, typeLeft, errs);
                boolean  fCheckAccess = !typeLeft.isAccessSpecified() &&  // see getTypeInfo() doc
                                        infoLeft.getType().getAccess() != Access.PRIVATE;

                IdentityConstant idChild = infoLeft.findName(pool, sName);
                if (idChild == null)
                    {
                    // process the "this.OuterName" construct
                    if (!typeLeft.isTypeOfType() && !fIdMode)
                        {
                        Constant constTarget = new NameResolver(this, sName)
                                .forceResolve(ErrorListener.BLACKHOLE);
                        if (constTarget instanceof IdentityConstant && constTarget.isClass())
                            {
                            if (constTarget.equals(pool.clzOuter()))
                                {
                                // this.Outer
                                if (!typeLeft.isVirtualChild())
                                    {
                                    log(errs, Severity.ERROR, Compiler.INVALID_OUTER_THIS);
                                    return null;
                                    }
                                return m_arg = new TargetInfo(sName, typeLeft.getParentType(), 1);
                                }

                            if (typeLeft instanceof PropertyClassTypeConstant &&
                                    constTarget.getType().equals(typeLeft.getParentType()))
                                {
                                return m_arg = new TargetInfo(sName, typeLeft.getParentType(), 1);
                                }

                            // they may choose to emphasize the exact type of "this" by writing:
                            // "this.X", where X is this class itself, a super class or any other
                            // contribution into this class; in all cases we'll treat it as "this"
                            if (ctx.getThisType().isA(constTarget.getType()))
                                {
                                TypeConstant typeTarget = constTarget.getType().
                                        adoptParameters(pool, ctx.getThisType());
                                return m_arg = ctx.getThisRegister().narrowType(typeTarget);
                                }

                            // if the left is a class, then the result is a sequence of at
                            // least one (recursive) ParentClassConstant around a
                            // ThisClassConstant; from this (context) point, walk up looking
                            // for the specified class, counting the number of "parent
                            // class" steps to get there
                            PseudoConstant idRelative =
                                    getRelativeIdentity(typeLeft, (IdentityConstant) constTarget);
                            if (idRelative != null)
                                {
                                return m_arg = idRelative;
                                }
                            }
                        }
                    log(errs, Severity.ERROR, Compiler.NAME_MISSING, sName, typeLeft.getValueString());
                    return null;
                    }

                switch (idChild.getFormat())
                    {
                    case Class:
                        {
                        // the only thing we allow here is a reference to a typedef when a type is
                        // required; e.g.:
                        //   Aggregator<Element> collector = ...
                        //   collector.Accumulator accumulator = collector.init();
                        ChildInfo infoChild = infoLeft.getChildInfosByName().get(sName);
                        assert infoChild != null;

                        if (infoChild.getIdentity() instanceof TypedefConstant idTypedef)
                            {
                            m_arg = idTypedef;
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.CLASS_UNEXPECTED, sName);
                            }
                        break;
                        }

                    case Typedef:
                        log(errs, Severity.ERROR, Compiler.TYPEDEF_UNEXPECTED,
                                sName, typeLeft.getValueString());
                        break;

                    case Property:
                        {
                        PropertyInfo infoProp = infoLeft.findProperty((PropertyConstant) idChild);
                        if (infoProp == null ||
                                fCheckAccess && !infoProp.isVisible(ctx.getThisClassId()))
                            {
                            log(errs, Severity.ERROR, Compiler.PROPERTY_INACCESSIBLE,
                                    idChild.getName(), typeLeft.getValueString());
                            return null;
                            }
                        m_arg         = idChild;
                        m_fAssignable = infoProp.isVar();
                        break;
                        }

                    case Method:
                        {
                        MethodInfo infoMethod = infoLeft.getMethodById((MethodConstant) idChild);
                        if (infoMethod == null ||
                                fCheckAccess && !infoMethod.isVisible(ctx.getThisClassId()))
                            {
                            log(errs, Severity.ERROR, Compiler.METHOD_INACCESSIBLE,
                                    idChild.getValueString(), typeLeft.getValueString());
                            return null;
                            }
                        }
                        // fall through
                    case MultiMethod:
                        // there are more than one method by that name;
                        // return the MultiMethod; let the caller decide
                        m_arg = idChild;
                        break;

                    case Module: // a module can't be nested
                    default:
                        throw new IllegalStateException("format=" + idChild.getFormat()
                                + ", constant=" + idChild);
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
     * @param fDraft       if true, avoid making any structural changes; the plan may change
     * @param errs         the error list to log errors to
     *
     * @return the type of the expression
     */
    protected TypeConstant planCodeGen(Context ctx, Argument argRaw, TypeConstant[] aTypeParams,
                                       TypeConstant typeDesired, boolean fDraft, ErrorListener errs)
        {
        assert ctx != null && argRaw != null;
        ConstantPool pool           = pool();
        boolean      fSuppressDeref = isSuppressDeref();

        if (argRaw instanceof Register reg)
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

            // label variables do not actually exist
            if (reg.isLabel() && (fSuppressDeref || !(getParent() instanceof NameExpression)))
                {
                log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, getName());
                }

            if (fSuppressDeref)
                {
                m_plan = Plan.RegisterRef;
                return reg.ensureRegType(!m_fAssignable);
                }
            else
                {
                // use the register itself (the "T" column in the table above)
                m_plan = Plan.None;

                // there is a possibility that the register type in this context is narrower than
                // its original type; we can return it only for an r-value
                return isRValue()
                        ? reg.getType()
                        : reg.getOriginalType();
                }
            }

        if (argRaw instanceof TargetInfo targetInfo)
            {
            // this can only mean an "outer this"
            if (fSuppressDeref)
                {
                m_plan = Plan.OuterRef;
                return pool.ensureParameterizedTypeConstant(pool.typeRef(), targetInfo.getType());
                }
            else
                {
                m_plan = Plan.OuterThis;
                return targetInfo.getType();
                }
            }

        Constant constant = (Constant) argRaw;
        switch (constant.getFormat())
            {
            case ThisClass:
                {
                TypeConstant typeThis = pool.ensureAccessTypeConstant(
                        constant.getType().adoptParameters(pool, ctx.getThisType()), Access.PRIVATE);
                if (fSuppressDeref)
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
                {
                // if the name could be a singleton, then that is the default, with the other two
                // choices being a type or a class; the presence of type parameters indicates a
                // type or a class
                boolean fCouldBeSingleton = !isIdentityMode(ctx, false);
                boolean fSingletonDesired = typeDesired != null && constant.getType().isA(typeDesired);
                boolean fClassDesired     = typeDesired != null && typeDesired.isA(pool.typeClass());
                boolean fTypeDesired      = typeDesired != null && typeDesired.isTypeOfType();
                boolean fHasTypeParams    = aTypeParams != null;
                if (fCouldBeSingleton && (fSingletonDesired
                        || (!fClassDesired && !fTypeDesired && !fHasTypeParams)))
                    {
                    if (fHasTypeParams)
                        {
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                        }

                    m_plan = Plan.Singleton;
                    return pool.ensureTerminalTypeConstant(constant);
                    }

                // determine the type of the class
                if (aTypeParams != null || fTypeDesired)
                    {
                    TypeConstant     type      = null;
                    IdentityConstant idTarget  = (IdentityConstant) constant;
                    Component        component = getComponent();
                    ClassStructure   clzThis   = component.getContainingClass();
                    ClassStructure   clzTarget = (ClassStructure) idTarget.getComponent();

                    if (clzThis != null)
                        {
                        IdentityConstant idThis = clzThis.getIdentityConstant();
                        if (idThis.equals(idTarget))
                            {
                            type = clzThis.getFormalType();
                            }
                        else
                            {
                            if (clzTarget.isVirtualChild())
                                {
                                boolean        fMate   = idTarget.isNestMateOf(idThis);
                                ClassConstant  idBase  = fMate
                                    ? ((ClassConstant) idThis).getOutermost()
                                    : ((ClassConstant) idTarget).getAutoNarrowingBase();
                                ClassStructure clzBase = (ClassStructure) idBase.getComponent();
                                boolean        fFormal = !(component instanceof MethodStructure method &&
                                                           method.isFunction());
                                type = pool.ensureVirtualTypeConstant(clzBase, clzTarget,
                                    fFormal && fMate, /*fParameterize*/ false, idTarget);
                                }
                            }
                        }

                    if (type == null)
                        {
                        type = pool.ensureTerminalTypeConstant(idTarget);
                        }

                   if (aTypeParams != null)
                        {
                        if (clzTarget.isTuple() ||
                            clzTarget.isParameterized() &&
                                aTypeParams.length <= clzTarget.getTypeParamCount())
                            {
                            type = type.adoptParameters(pool, aTypeParams);
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                            }
                        }

                    if (fTypeDesired)
                        {
                        m_plan = Plan.TypeOfClass;
                        return type.getType();
                        }
                    else
                        {
                        IdentityConstant clz = pool.ensureClassConstant(type);
                        m_plan = Plan.None;
                        m_arg  = clz;
                        return clz.getValueType(pool, type);
                        }
                    }
                else
                    {
                    m_plan = Plan.None;

                    if (!(constant instanceof ClassConstant))
                        {
                        // Module or Package are represented by a DecoratedClassConstant
                        m_arg = pool.ensureClassConstant(constant.getType());
                        }

                    TypeConstant typeThisClass = ctx.getThisClass().getFormalType();
                    return ((IdentityConstant) constant).getValueType(pool, typeThisClass);
                    }
                }

            case Property:
                {
                if (aTypeParams != null)
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    }

                // argRaw refers to the actual property identity, while the targetInfo may point to
                // a context specific id (i.e. nested)
                PropertyConstant  idProp   = (PropertyConstant) argRaw;
                TypeConstant      typeProp = idProp.getType();
                TargetInfo        target   = m_targetInfo;
                boolean           fClass   = m_fClassAttribute; // the class of Class?
                PropertyInfo      infoProp;
                TypeConstant      typeLeft;

                // use the type inference to differentiate between a property de-referencing
                // and the Property instance itself (check for both Property and Property? types)
                if (typeDesired != null && typeDesired.removeNullable().isA(pool.typeProperty()) &&
                        !typeProp.removeNullable().isA(pool.typeProperty()))
                    {
                    if (fSuppressDeref)
                        {
                        log(errs, Severity.ERROR, Compiler.INVALID_PROPERTY_REF);
                        return null;
                        }

                    if (left == null)
                        {
                        typeLeft = target == null ? ctx.getThisType() : target.getTargetType();
                        }
                    else
                        {
                        typeLeft = left.getImplicitType(ctx);
                        if (!fClass && isIdentityMode(ctx, false))
                            {
                            if (typeLeft.isA(pool.typeClass()))
                                {
                                typeLeft = typeLeft.getParamType(0);
                                }
                            else
                                {
                                // we can only get here if the left side is a property name that is
                                // being used in identity mode; that means that we need to report
                                // one of two errors, either the failure from ctx.requireThis()
                                // (since we don't have the "this" to deref the property), or a type
                                // mismatch (cannot convert type Property to the required type)
                                assert left instanceof NameExpression nameLeft
                                        && nameLeft.getMeaning() == Meaning.Property
                                        && (nameLeft.m_plan == Plan.None ||
                                            nameLeft.m_plan == Plan.PropertyIdentity);
                                if (!idProp.getComponent().isStatic() &&
                                        !ctx.requireThis(getStartPosition(), errs))
                                    {
                                    return null;
                                    }
                                }
                            }
                        }

                    infoProp = findProperty(ctx, typeLeft, idProp, errs);
                    if (infoProp == null)
                        {
                        return null;
                        }

                    m_plan = Plan.PropertySelf;
                    return idProp.getValueType(pool, typeLeft);
                    }

                if (idProp.isTypeSequenceTypeParameter())
                    {
                    assert !m_fAssignable;
                    m_plan = Plan.PropertyDeref;
                    return idProp.getConstraintType();
                    }

                PropertyStructure prop = (PropertyStructure) idProp.getComponent();
                if (prop.isConstant() && !fSuppressDeref)
                    {
                    assert !m_fAssignable;
                    m_plan = prop.hasInitialValue() ? Plan.PropertyDeref : Plan.Singleton;
                    return typeProp;
                    }

                ComputePropertyInfo:
                if (left == null)
                    {
                    if (target == null)
                        {
                        typeLeft = pool.ensureAccessTypeConstant(ctx.getThisType(), Access.PRIVATE);
                        }
                    else
                        {
                        idProp   = (PropertyConstant) target.getId();
                        typeLeft = target.getTargetType();
                        }

                    infoProp = findProperty(ctx, typeLeft, idProp, errs);
                    if (infoProp != null)
                        {
                        // check for a narrowed property type
                        Argument argNarrowed = ctx.getVar(prop.getName());
                        typeProp = argNarrowed instanceof TargetInfo
                                ? argNarrowed.getType()
                                : infoProp.inferImmutable(typeLeft).
                                        resolveAutoNarrowing(pool, false, typeLeft, null);
                        }
                    }
                else
                    {
                    // Consider the following statements:
                    //     val c = Boolean.count;
                    //     val r = Boolean.&count
                    // It's quite clear that "c" is an Int and "r" is a Ref<Int>;
                    // however it leaves no [syntax] room to specify a property "count" on Boolean class.
                    // For that case we will need the l-value to direct us:
                    //     Property p = Boolean.count;
                    // using the same syntax as in a case of a non-singleton class:
                    //     val p = Person.name;

                    typeLeft = left.getImplicitType(ctx);
                    if (fClass && typeDesired != null && typeDesired.isA(pool.typeProperty())
                            || !fClass && isIdentityMode(ctx, false))
                        {
                        if (fSuppressDeref)
                            {
                            log(errs, Severity.ERROR, Compiler.INVALID_PROPERTY_REF);
                            return null;
                            }

                        if (typeLeft.isA(pool.typeClass()))
                            {
                            if (!fClass)
                                {
                                typeLeft = typeLeft.getParamType(0);
                                }
                            }
                        else
                            {
                            // we can only get here if the left side is a property name that is being
                            // used in identity mode; that means that we need to report one of two
                            // errors, either the failure from ctx.requireThis() (since we don't
                            // have the "this" to deref the property), or a type mismatch (cannot
                            // convert type Property to the required type)
                            assert left instanceof NameExpression nameLeft
                                    && nameLeft.getMeaning() == Meaning.Property
                                    && (nameLeft.m_plan == Plan.None ||
                                        nameLeft.m_plan == Plan.PropertyIdentity);
                            if (!prop.isStatic() &&
                                    !ctx.requireThis(getStartPosition(), errs))
                                {
                                return null;
                                }
                            }

                        infoProp = findProperty(ctx, typeLeft, idProp, errs);
                        if (infoProp == null)
                            {
                            break ComputePropertyInfo;
                            }

                        if (typeDesired != null && typeDesired.isA(pool.typeFunction()) &&
                                !typeProp.isA(pool.typeFunction()))
                            {
                            // turn the property into a lambda
                            m_plan = Plan.BjarneLambda;
                            if (!fDraft)
                                {
                                m_idBjarnLambda = createBjarneLambda(ctx.getThisClass(), idProp);
                                }
                            return idProp.getBjarneLambdaType();
                            }
                        else
                            {
                            m_plan = Plan.None;
                            return idProp.getValueType(pool, typeLeft);
                            }
                        }

                    infoProp = findProperty(ctx, typeLeft, idProp, errs);
                    if (infoProp == null)
                        {
                        break ComputePropertyInfo;
                        }

                    // consider the following code topology:
                    //
                    //   class Parent<Element extends Orderable> {
                    //       class Child(Element e) implements Orderable {
                    //           static <CT extends Child> Ordered compare(CT v1, CT v2) {
                    //               return v1.e <=> v2.e;
                    //   }}}
                    //
                    // The type of the name expression "v1.e" should be computed as a formal
                    // type "Element" against an actual type parameter type "Parent<X>.Child"
                    if (typeProp.isFormalType() && typeLeft.isTypeParameter())
                        {
                        TypeConstant typeConstraint = typeLeft.resolveConstraints();
                        if (typeConstraint.isVirtualChild())
                            {
                            IdentityConstant idConstraint  = typeConstraint.getSingleUnderlyingClass(true);
                            ClassStructure   clzConstraint = (ClassStructure) idConstraint.getComponent();

                            FormalConstant idFormal = (FormalConstant) typeProp.getDefiningConstant();
                            if (clzConstraint.isVirtualDescendant(idFormal.getParentConstant()))
                                {
                                TypeParameterConstant idParam =
                                        (TypeParameterConstant) typeLeft.getDefiningConstant();

                                idProp   = pool.ensureFormalTypeChildConstant(idParam, idFormal.getName());
                                typeProp = idProp.getType();
                                break ComputePropertyInfo;
                                }
                            }
                        }

                    if (infoProp.isFormalType())
                        {
                        if (m_fClassAttribute)
                            {
                            // this can only be one of the Class' formal types,
                            // for example: Point.StructType
                            if (isSuppressDeref())
                                {
                                log(errs, Severity.ERROR, Compiler.INVALID_PROPERTY_REF);
                                return null;
                                }
                            assert typeLeft.isA(pool.typeClass());
                            typeProp = infoProp.getType().resolveAutoNarrowing(pool, false, typeLeft, null);
                            }
                        else
                            {
                            boolean fDynamic = false;

                            CheckDynamic:
                            if (left instanceof NameExpression exprLeft)
                                {
                                if (exprLeft.isSuppressDeref())
                                    {
                                    // this is a Ref property access, e.g.: "&outer.Referent"
                                    break CheckDynamic;
                                    }

                                switch (exprLeft.getMeaning())
                                    {
                                    case Variable:
                                        {
                                        // this is a dynamic formal type (e.g. array.Element)
                                        Register              argLeft      = (Register) exprLeft.m_arg;
                                        DynamicFormalConstant constDynamic = pool.ensureDynamicFormal(
                                                ctx.getMethod().getIdentityConstant(), argLeft,
                                                idProp, exprLeft.getName());
                                        typeProp = constDynamic.getType().getType();
                                        fDynamic = true;
                                        break;
                                        }

                                    case Property:
                                        // REVIEW support dynamic types for properties?
                                        break;

                                    default:
                                        break;
                                    }
                                }

                            if (!fDynamic)
                                {
                                typeProp = idProp.getType();
                                }
                            }
                        }
                    else
                        {
                        TypeConstant typeResolved = infoProp.inferImmutable(typeLeft);

                        // strictly speaking, we could call "resolveDynamicType" regardless of
                        // the desired type, but the implications of that are quite significant,
                        // so we leave it to be dealt with as a part of the much wider "dynamic
                        // type support" project
                        if (typeDesired != null && typeDesired.containsDynamicType())
                            {
                            typeResolved = resolveDynamicType(ctx, typeLeft, typeProp, typeResolved);
                            }

                        typeProp = typeResolved.resolveAutoNarrowing(pool, false, typeLeft, null);
                        }
                    }

                if (infoProp == null)
                    {
                    if (!errs.hasSeriousErrors())
                        {
                        log(errs, Severity.ERROR, Compiler.NAME_MISSING, idProp.getName(),
                            typeLeft.getValueString());
                        }
                    return null;
                    }

                if (fSuppressDeref)
                    {
                    m_plan = Plan.PropertyRef;
                    return infoProp.isCustomLogic()
                            ? pool.ensurePropertyClassTypeConstant(typeLeft, idProp)
                            : infoProp.getBaseRefType();
                    }
                else
                    {
                    m_plan = Plan.PropertyDeref;
                    return typeProp;
                    }
                }

            case FormalTypeChild:
                {
                FormalTypeChildConstant idFormal = (FormalTypeChildConstant) constant;
                m_plan = Plan.TypeOfFormalChild;
                return idFormal.getType().getType();
                }

            case Typedef:
                {
                if (aTypeParams != null)
                    {
                    // TODO have to incorporate type params
                    throw notImplemented();
                    }

                m_plan = Plan.TypeOfTypedef;

                TypeConstant typeRef = ((TypedefConstant) constant).getReferredToType();
                TypeConstant typeLeft = left == null
                        ? ctx.getThisType()
                        : left.getImplicitType(ctx);
                return typeRef.getType().resolveGenerics(pool, typeLeft);
                }

            case Method:
                {
                // the constant refers to a method or function
                MethodConstant  idMethod = (MethodConstant) argRaw;
                MethodStructure method   = (MethodStructure) idMethod.getComponent();
                TypeConstant    typeFn;

                if (idMethod.isFunction())
                    {
                    m_plan = Plan.None;
                    typeFn = idMethod.getType();
                    }
                else
                    {
                    if (typeDesired != null && typeDesired.isA(pool.typeMethod()))
                        {
                        // they explicitly desire a method; give them the method
                        m_plan = Plan.None;
                        TypeConstant typeMethod = idMethod.getType();
                        if (method != null)
                            {
                            Annotation[] aAnno = method.getAnnotations();
                            if (aAnno != null && aAnno.length > 0)
                                {
                                typeMethod = pool.ensureAnnotatedTypeConstant(typeMethod, aAnno);
                                }
                            }
                        return typeMethod;
                        }

                    if (left instanceof NameExpression exprName &&
                                exprName.getMeaning() == Meaning.Class)
                        {
                        IdentityConstant idClz = exprName.getIdentity(ctx);
                        ClassStructure   clz   = (ClassStructure) idClz.getComponent();
                        if (clz.isSingleton())
                            {
                            m_plan = Plan.BindTarget;
                            typeFn = idMethod.getSignature().asFunctionType();
                            }
                        else
                            {
                            // turn a method into a "method handle" function
                            m_plan = Plan.BjarneLambda;
                            typeFn = idMethod.getBjarneLambdaType();
                            if (!fDraft)
                                {
                                int cReturns = typeDesired == null
                                            ? idMethod.getRawReturns().length
                                            : pool.extractFunctionReturns(typeDesired).length;
                                m_idBjarnLambda =
                                    createBjarneLambda(ctx.getThisClass(), idMethod, cReturns);
                                }
                            }
                        }
                    else
                        {
                        // "bind" the method into a function
                        m_plan = Plan.BindTarget;
                        typeFn = idMethod.getSignature().asFunctionType();
                        }
                    }

                if (typeDesired != null)
                    {
                    if (method == null)
                        {
                        TypeConstant typeLeft = left == null
                                ? ctx.getThisType()
                                : left.getImplicitType(ctx);
                        TypeInfo   infoLeft   = getTypeInfo(ctx, typeLeft, errs);
                        MethodInfo infoMethod = infoLeft.getMethodBySignature(idMethod.getSignature());
                        if (infoMethod == null)
                            {
                            log(errs, Severity.ERROR, Compiler.METHOD_INACCESSIBLE,
                                    idMethod.getValueString(), infoLeft.getType().getValueString());
                            return null;
                            }
                        method = infoMethod.getTopmostMethodStructure(infoLeft);
                        assert method != null;
                        m_arg = method.getIdentityConstant();
                        }

                    int cTypeParams = method.getTypeParamCount();
                    if (cTypeParams > 0)
                        {
                        TypeConstant[] atypeArgs = pool.extractFunctionParams(typeDesired);
                        if (atypeArgs == null)
                            {
                            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                    typeDesired.getValueString(), typeFn.getValueString());
                            return null;
                            }

                        ListMap<FormalConstant, TypeConstant> mapTypeParams = method.resolveTypeParameters(
                                pool, null, atypeArgs, TypeConstant.NO_TYPES, false);

                        if (mapTypeParams.size() < cTypeParams)
                            {
                            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                                    method.collectUnresolvedTypeParameters(mapTypeParams.keySet().
                                        stream().map(NamedConstant::getName).collect(Collectors.toSet())));
                            return null;
                            }

                        // resolve the function signature against all the types we know by now
                        typeFn          = typeFn.resolveGenerics(pool, GenericTypeResolver.of(mapTypeParams));
                        m_mapTypeParams = mapTypeParams;
                        }

                    if (m_plan == Plan.BindTarget && typeDesired.isA(pool.typeFunction()))
                        {
                        int cArgDesired  = pool.extractFunctionParams(typeDesired).length;
                        int cArgVisible  = method.getVisibleParamCount();
                        int cArgRequired = method.getRequiredParamCount();
                        if (cArgDesired < cArgRequired || cArgDesired > cArgVisible)
                            {
                            log(errs, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT,
                                    cArgDesired, cArgRequired);
                            return null;
                            }

                        if (cArgVisible > cArgDesired)
                            {
                            // theoretically speaking, we could have produced an FBind opcode
                            // binding all the default arguments to Register,DEFAULT, but it's
                            // not necessary, due to the runtime compensation
                            // (see Frame.getArgument() check for a missing argument)
                            for (int i = cArgVisible-1; i > cArgDesired-1; i--)
                                {
                                typeFn = pool.bindFunctionParam(typeFn, i);
                                }
                            }
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

    private PropertyInfo findProperty(Context ctx, TypeConstant typeTarget, PropertyConstant idProp,
                                      ErrorListener errs)
        {
        TypeInfo infoTarget   = getTypeInfo(ctx, typeTarget, errs);
        boolean  fCheckAccess = !typeTarget.isAccessSpecified(); // see the comments in getTypeInfo()

        PropertyInfo infoProp = infoTarget.findProperty(idProp);
        if (infoProp == null ||
                fCheckAccess && !infoProp.isVisible(ctx.getThisClassId()))
            {
            log(errs, Severity.ERROR, Compiler.PROPERTY_INACCESSIBLE,
                    idProp.getName(), typeTarget.getValueString());
            return null;
            }

        return infoProp;
        }

    /**
     * Resolve the property type into a dynamic type is possible.
     *
     * @param ctx          the context
     * @param typeLeft     the type for the "left" expression
     * @param typeProp     the original property type, which could be formal
     * @param typeResolved the resolved property type as computed by the TypeInfo
     *
     * @return a resolved property type
     */
    private TypeConstant resolveDynamicType(Context ctx, TypeConstant typeLeft,
                                            TypeConstant typeProp, TypeConstant typeResolved)
        {
        ConstantPool pool = pool();

        CheckDynamic:
        if (left instanceof NameExpression exprLeft &&
                typeLeft.isSingleDefiningConstant() && !typeLeft.isFormalType())
            {
            if (exprLeft.isSuppressDeref())
                {
                // this is a Ref property access, e.g.: "&outer.assigned"
                break CheckDynamic;
                }

            Argument argLeft = exprLeft.resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);
            if (!(argLeft instanceof Register regLeft))
                {
                break CheckDynamic;
                }

            if (regLeft.isPredefined())
                {
                break CheckDynamic;
                }

            MethodConstant   idMethod = ctx.getMethod().getIdentityConstant();
            String           sName    = exprLeft.getName();
            IdentityConstant idParent = (IdentityConstant) typeLeft.
                    removeAutoNarrowing().getDefiningConstant();

            if (typeProp.isGenericType())
                {
                FormalConstant idFormal =
                        (FormalConstant) typeProp.getDefiningConstant();
                if (!typeResolved.isGenericType() &&
                        idFormal.getParentConstant().equals(idParent))
                    {
                    return pool.ensureDynamicFormal(
                            idMethod, regLeft, idFormal, sName).getType();
                    }
                }
            else if (typeProp.containsGenericType(true))
                {
                // if the property type contains a generic type and that
                // generic type belongs to the left argument, replace it with
                // a corresponding dynamic type
                GenericTypeResolver resolver = new GenericTypeResolver()
                    {
                    @Override
                    public TypeConstant resolveGenericType(String sFormalName)
                        {
                        return typeResolved.resolveGenericType(sFormalName);
                        }

                    @Override
                    public TypeConstant resolveFormalType(FormalConstant idFormal)
                        {
                        return idFormal.getParentConstant().equals(idParent)
                            ? pool.ensureDynamicFormal(
                                idMethod, regLeft, idFormal, sName).getType()
                            : resolveGenericType(idFormal.getName());
                        }
                    };

                return typeProp.resolveGenerics(pool, resolver);
                }
            }
        return typeResolved;
        }

    /**
     * @return the meaning of the name (after resolveRawArgument has finished), or null if it cannot be
     *         determined
     */
    protected Meaning getMeaning()
        {
        Argument arg = m_arg;
        switch (arg)
            {
            case null:
                return Meaning.Unknown;

            case Register reg:
                return reg.isPredefined()
                    ? reg.isLabel()
                    ? Meaning.Label
                    : Meaning.Reserved
                    : Meaning.Variable;

            case TargetInfo ignored:
                // this indicates an "outer this"
                return Meaning.Reserved;

            case Constant constant:
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
                    case DecoratedClass:
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
                        return Meaning.Type;
                    }

            default:
            }

        throw new IllegalStateException("arg=" + arg);
        }

    /**
     * @return true iff this name represents a DVar register
     */
    public boolean isDynamicVar()
        {
        return m_arg instanceof Register reg && reg.isVar();
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
                    if (!((NameExpression) left).isIdentityMode(ctx, true) || "this".equals(name.getValueText()))
                        {
                        return false;
                        }
                    }

                IdentityConstant id = getIdentity(ctx);
                if (id instanceof TypedefConstant idTypedef)
                    {
                    TypeConstant typeRef = idTypedef.getReferredToType();
                    if (typeRef.isSingleUnderlyingClass(false))
                        {
                        id = typeRef.getSingleUnderlyingClass(false);
                        }
                    else
                        {
                        return false;
                        }
                    }

                if (fSoft)
                    {
                    if (id instanceof ModuleConstant || id instanceof PackageConstant)
                        {
                        return true;
                        }
                    }

                return isSuppressDeref() || !((ClassStructure) id.getComponent()).isSingleton();
                }

            case Property:
                {
                // a non-constant-property name can be "identity mode" if at least one of the
                // following is true:
                //   1) there is no left and the context is static; or
                //   2) there is a left, and it is in identity mode, but this is not a class attribute
                //
                // Name         method  specifies  "static" context /   specifies
                // refers to    context no-de-ref  identity mode        no-de-ref
                // ------------ ------- ---------- ------------------   -------------------
                // Property     T       <- Ref/Var PropertyConstant*[1] PropertyConstant*
                // - type param T       <- Ref     PropertyConstant*[1] PropertyConstant*
                // Constant     T       <- Ref     T                    <- Ref
                //
                // *[1] must have a left-hand side in identity mode; otherwise it is an Error
                PropertyStructure prop = (PropertyStructure) getIdentity(ctx).getComponent();

                return !prop.isConstant() && !m_fClassAttribute &&
                    (left == null
                        ? m_plan == Plan.PropertyIdentity
                        : ((NameExpression) left).isIdentityMode(ctx, false));
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
        return m_arg instanceof IdentityConstant id
                ? id
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
        if (!typeFrom.isExplicitClassIdentity(true))
            {
            return null;
            }
        Component component = typeFrom.getSingleUnderlyingClass(true).getComponent();
        if (!(component instanceof ClassStructure clzFrom))
            {
            return null;
            }

        ConstantPool   pool       = pool();
        PseudoConstant idVirtPath = null;
        int            cDepth     = 0;
        while (clzFrom != null)
            {
            IdentityConstant idFrom = clzFrom.getIdentityConstant();
            idVirtPath = idVirtPath == null
                    ? pool.ensureThisClassConstant(idFrom)
                    : pool.ensureParentClassConstant(idVirtPath);

            if (idFrom.equals(idTarget) ||
                    (cDepth > 0 && clzFrom.hasContribution(idTarget)))
                {
                // found it!
                return idVirtPath;
                }

            if (clzFrom.isTopLevel() || clzFrom.isStatic())
                {
                // can't ".this" beyond the outermost class, and can't ".this" from a static child
                return null;
                }

            clzFrom = clzFrom.getContainingClass();
            cDepth++;
            }

        return null;
        }

    protected PropertyAccess calculatePropertyAccess(boolean fRef)
        {
        if (m_propAccessPlan != null)
            {
            return m_propAccessPlan;
            }

        if (left == null)
            {
            return PropertyAccess.This;
            }

        if (left instanceof NameExpression exprLeft)
            {
            // check that "this" is not "OuterThis"
            if ("this".equals(exprLeft.getName()) &&
                    (fRef || exprLeft.m_plan == Plan.None))
                {
                return PropertyAccess.This;
                }
            }
        return PropertyAccess.Left;
        }

    /**
     * Helper method for BAST production for in-place operations for atomic properties.
     *
     * @param typeArg  if null, the operation is a unary one (e.g. "preIncrement"), otherwise a
     *                   binary (e.g. "addAssign")
     */
    protected MethodConstant findAtomicInPlaceAssignMethod(
                Context ctx, String sMethod, String sOp, TypeConstant typeArg)
        {
        TypeConstant typeTarget = switch (calculatePropertyAccess(true))
            {
            // "p += k" -> "&p.addAssign(k)"
            case This -> ctx.getThisType().ensureAccess(Access.PRIVATE);

            // "c.p += k"    -> "c.&p.addAssign(k)"
            // "f().p += k"  -> "f().&p.addAssign(k)"
            // "a[0].p += k" -> "a[0].&p.addAssign(k)"
            case Left -> getLeftExpression().getType();

            default ->
                throw new IllegalStateException();
            };

        int                 cArgs      = typeArg == null ? 0 : 1;
        PropertyConstant    idProp     = (PropertyConstant) m_arg;
        TypeConstant        typeVar    = idProp.getRefType(typeTarget);
        Set<MethodConstant> setMethods = typeVar.ensureTypeInfo().findOpMethods(sMethod, sOp, cArgs);
        return switch (setMethods.size())
            {
            case 0  -> null;
            case 1  -> setMethods.iterator().next();
            default -> RelOpExpression.chooseBestMethod(setMethods, typeArg);
            };
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
    public void narrowType(Context ctx, Branch branch, TypeConstant typeNarrow)
        {
        if (typeNarrow != null)
            {
            assert isValidated();

            Argument arg = resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);

            if (left != null)
                {
                if (arg instanceof FormalTypeChildConstant constFormal)
                    {
                    // e.g.: CompileType.Element
                    ctx.replaceGenericType(constFormal, branch, typeNarrow);
                    }

                // TODO: to allow an expression "a.b.c" to be narrowed, all parents have to be immutable
                return;
                }

            // we are only concerned with registers and type parameters;
            // properties and constants are ignored
            String sName = getName();
            if (arg instanceof Register reg)
                {
                ctx.narrowLocalRegister(sName, reg, branch, typeNarrow);
                }
            else
                {
                if (arg instanceof TargetInfo info)
                    {
                    IdentityConstant id = info.getId();
                    if (id instanceof PropertyConstant idProp)
                        {
                        assert sName.equals(id.getName());

                        // make sure the property hasn't been hidden by a local var
                        if (ctx.getVar(sName) instanceof Register)
                            {
                            return;
                            }

                        if (idProp.isFormalType())
                            {
                            ctx.replaceGenericArgument(idProp, branch, new TargetInfo(info, typeNarrow));
                            }
                        else  // allow narrowing for immutable properties
                            {
                            TypeConstant     typeTarget = info.getTargetType();
                            IdentityConstant idTarget   = typeTarget.getSingleUnderlyingClass(false);
                            MethodStructure  method     = ctx.getMethod();
                            if (idTarget.equals(ctx.getThisClassId()) &&
                                    (method.isConstructor() || method.isValidator()))
                                {
                                // no property narrowing in the constructor
                                }
                            else if (typeTarget.isImmutable())
                                {
                                ctx.narrowProperty(sName, idProp, branch, new TargetInfo(info, typeNarrow));
                                }
                            }
                        }
                    else if (id instanceof IdentityConstant)
                        {
                        // narrow the "outer this"
                        ctx.replaceArgument("this", branch, new TargetInfo(info, typeNarrow));
                        }
                    }
                else if (arg instanceof PropertyConstant idProp)
                    {
                    assert sName.equals(idProp.getName());

                    // make sure the property hasn't been hidden by a local var
                    if (ctx.getVar(sName) instanceof Register)
                        {
                        return;
                        }

                    if (idProp.isFormalType())
                        {
                        assert typeNarrow.isTypeOfType();

                        TargetInfo info = new TargetInfo(sName, idProp, true, idProp.getNamespace().getType(), 0);
                        ctx.replaceGenericArgument(idProp, branch, new TargetInfo(info, typeNarrow));
                        }
                    else // allow narrowing for immutable properties
                        {
                        MethodStructure method = ctx.getMethod();
                        if (method.isConstructor() || method.isValidator())
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
                else if (arg instanceof TypeParameterConstant constTypeParam)
                    {
                    ctx.replaceGenericType(constTypeParam, branch, typeNarrow);
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
    protected enum Meaning {Unknown, Reserved, Variable, Property, FormalChildType, Method, Class,
            Type, Label}

    /**
     * Represents the necessary argument/assignable transformation that the expression will have to
     * produce as part of compilation, if it is asked to produce an argument, an assignable, or an
     * assignment.
     */
    protected enum Plan {None, OuterThis, OuterRef, RegisterRef, PropertyDeref, PropertyRef,
            PropertySelf, PropertyIdentity, TypeOfClass, TypeOfTypedef, Singleton, TypeOfFormalChild,
            BindTarget, BjarneLambda,
    }

    /**
     * If the plan is None or BindTarget, and this expression represents a method or function,
     * we may need to bind type parameters.
     */
    private ListMap<FormalConstant, TypeConstant> m_mapTypeParams;

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
     * If the plan is {@link Plan#BjarneLambda}, the corresponding lambda id.
     */
    private transient MethodConstant m_idBjarnLambda;

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
    protected enum PropertyAccess {SingletonParent, Outer, This, Left}

    /**
     * The chosen property access plan.
     */
    private transient PropertyAccess m_propAccessPlan;

    /**
     * If the property access plan is {@link PropertyAccess#SingletonParent}, the identity of the
     * parent.
     */
    private transient IdentityConstant m_idSingletonParent;

    /**
     * If true, indicates that the argument refers to a property or method for a class of Class
     * specified by the "left" expression.
     */
    private transient boolean m_fClassAttribute;

    /**
     * Cached validation info: Can the name be used as an "L value"?
     */
    private transient boolean m_fAssignable;

    /**
     * A cached ExprAST node for the result of {@link #generateRefTarget}.
     */
    private transient ExprAST m_astRefTarget;

    /**
     * A cached ExprAST node for this expression.
     */
    private transient ExprAST m_astResult;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "left", "params");
    }