package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.ChildClassConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeParameterConstant;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import static org.xvm.compiler.Lexer.isValidQualifiedModule;


/**
 * A type expression specifies a named type with optional parameters.
 */
public class NamedTypeExpression
        extends TypeExpression
        implements NameResolver.NameResolving
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a NamedTypeExpression without a "left".
     */
    public NamedTypeExpression(Token immutable, List<Token> names, Token access, Token nonnarrow,
                               List<TypeExpression> params, long lEndPos)
        {
        this.left       = null;
        this.immutable  = immutable;
        this.names      = names;
        this.access     = access;
        this.nonnarrow  = nonnarrow;
        this.paramTypes = params;
        this.lStartPos  = immutable == null ? names.get(0).getStartPosition() : immutable.getStartPosition();
        this.lEndPos    = lEndPos;
        }

    /**
     * Construct a NamedTypeExpression with a "left".
     */
    public NamedTypeExpression(TypeExpression left, List<Token> names,
                               List<TypeExpression> params, long lEndPos)
        {
        this.left       = left;
        this.immutable  = null;
        this.names      = names;
        this.access     = null;
        this.nonnarrow  = null;
        this.paramTypes = params;
        this.lStartPos  = names.get(0).getStartPosition();
        this.lEndPos    = lEndPos;
        }

    /**
     * Construct a synthetic validated NamedTypeExpression.
     */
    public NamedTypeExpression(Expression exprSource, TypeConstant type)
        {
        this.left       = null;
        this.immutable  = null;
        this.access     = null;
        this.nonnarrow  = null;
        this.paramTypes = null;
        this.lStartPos  = exprSource.getStartPosition();
        this.lEndPos    = exprSource.getEndPosition();
        this.names      = Collections.singletonList(new Token(lStartPos, lEndPos,
                            Token.Id.IDENTIFIER, type.getValueString())); // used for "toString" only
        setTypeConstant(type);
        setStage(Stage.Validated);
        setParent(exprSource);
        }

    /**
     * Construct a NamedTypeExpression with an optional module. (This is NOT for the compiler.)
     */
    public NamedTypeExpression(List<Token> module, List<Token> names,
                               List<TypeExpression> params, long lEndPos)
        {
        this.module     = module;
        this.left       = null;
        this.immutable  = null;
        this.names      = names;
        this.access     = null;
        this.nonnarrow  = null;
        this.paramTypes = params;
        this.lStartPos  = (module == null ? names : module).get(0).getStartPosition();
        this.lEndPos    = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Assemble the qualified name of the module. This information and this method are not used by
     * compilation; this is for runtime support only.
     *
     * @return the dot-delimited name of the module, or null if no module is specified
     */
    public String getModule()
        {
        if (module == null)
            {
            return null;
            }

        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for (Token name : module)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(name.getValueText());
            }

        return sb.toString();
        }

    /**
     * Assemble the qualified name.
     *
     * @return the dot-delimited name
     */
    public String getName()
        {
        if (names == null)
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for (Token name : names)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(name.getValueText());
            }

        return sb.toString();
        }

    public String[] getNames()
        {
        List<Token> list = names;
        int         c    = list == null ? 0 : list.size();
        String[]    as   = new String[c];
        for (int i = 0; i < c; ++i)
            {
            as[i] = list.get(i).getValueText();
            }
        return as;
        }

    public Constant getIdentityConstant()
        {
        Constant constId = m_constId;
        if (constId == null)
            {
            m_constUnresolved = new UnresolvedNameConstant(pool(), getNames(), isExplicitlyNonAutoNarrowing());
            m_constId = constId = m_constUnresolved;
            }
        else
            {
            m_constId = constId = constId.resolve();
            }
        return constId;
        }

    public List<TypeExpression> getParamTypes()
        {
        return paramTypes;
        }

    /**
     * @return null if no access is explicit; otherwise one of PUBLIC, PROTECTED, PRIVATE
     */
    public Access getExplicitAccess()
        {
        if (access == null)
            {
            return null;
            }

        return switch (access.getId())
            {
            case PUBLIC    -> Access.PUBLIC;
            case PROTECTED -> Access.PROTECTED;
            case PRIVATE   -> Access.PRIVATE;
            case STRUCT    -> Access.STRUCT;
            default        -> throw new IllegalStateException("access=" + access);
            };
        }

    /**
     * @return true iff the type is explicitly non-auto-narrowing (the '!' post-operator)
     */
    public boolean isExplicitlyNonAutoNarrowing()
        {
        return nonnarrow != null;
        }

    /**
     * @return true iff the type represents a virtual child
     */
    public boolean isVirtualChild()
        {
        return m_fVirtualChild;
        }

    /**
     * Auto-narrowing is allowed for a type used in the following scenarios:
     * <ul>
     *   <li>Type of a property;</li>
     *   <li>Type of a method parameter;</li>
     *   <li>Type of a method return value;</li>
     * </ul>
     *
     * @return true iff this type is used as part of a property type, a method return type, a method
     *         parameter type,
     */
    public boolean isAutoNarrowingAllowed()
        {
        TypeExpression type   = this;
        AstNode        parent = getParent();
        while (true)
            {
            if (!parent.isAutoNarrowingAllowed(type))
                {
                // annotation; disallow auto-narrowing
                return false;
                }

            if (parent.isComponentNode())
                {
                // the containing component didn't reject; we are good to auto-narrow
                return true;
                }

            if (parent instanceof AnnotatedTypeExpression exprAnno && type == exprAnno.type)
                {
                type = exprAnno;
                }

            parent = parent.getParent();
            }
        }

    /**
     * @return the constant to use
     */
    protected Constant inferAutoNarrowing(Constant constId, ErrorListener errs)
        {
        // check for auto-narrowing
        if (!constId.containsUnresolved() && isAutoNarrowingAllowed() != isExplicitlyNonAutoNarrowing())
            {
            if (isExplicitlyNonAutoNarrowing())
                {
                log(errs, Severity.ERROR, Compiler.AUTO_NARROWING_ILLEGAL);
                }
            else if (constId instanceof ClassConstant idClz) // isAutoNarrowingAllowed()
                {
                Component      component = getComponent();
                ClassStructure clzThis   = component instanceof ClassStructure clz
                    ? clz
                    : component.getContainingClass();
                if (clzThis != null && clzThis.getIdentityConstant().getFormat() == Format.Class)
                    {
                    return ((ClassConstant) clzThis.getIdentityConstant()).
                            calculateAutoNarrowingConstant(idClz);
                    }
                }
            }

        return constId;
        }

    /**
     * Build a list of names for this NamedTypeExpression.
     *
     * @param cNames  how many names so far (recursing right to left)
     *
     * @return a list of names
     */
    protected List<String> collectNames(int cNames)
        {
        List<Token>  listThis   = names;
        int          cNamesThis = listThis == null ? 0 : listThis.size();
        List<String> listNames;

        if (left instanceof NamedTypeExpression exprName)
            {
            listNames = exprName.collectNames(cNames + cNamesThis);
            }
        else
            {
            listNames = new ArrayList<>(cNames + cNamesThis);
            }

        for (int i = 0; i < cNamesThis; ++i)
            {
            listNames.add(listThis.get(i).getValueText());
            }
        return listNames;
        }

    /**
     * @return true iff this expression fully specifies a virtual child class identity
     */
    protected boolean isFullyQualifiedChild()
        {
        assert m_resolver != null && m_resolver.getResult() == NameResolver.Result.RESOLVED;

        IdentityConstant idFirst = (IdentityConstant) m_resolver.getBaseConstant();

        // module and package are always "fully qualified" and so are non-child classes
        return !(idFirst instanceof ClassConstant)
            || !((ClassStructure) idFirst.getComponent()).isVirtualChild();
        }

    /**
     * Determine if this NamedTypeExpression could be a module name.
     *
     * @return true iff this NamedTypeExpression is just a name, and that name is a legal name for
     *         a module
     */
    public boolean isValidModuleName()
        {
        return immutable == null && access == null && (paramTypes == null || paramTypes.isEmpty())
                && isValidQualifiedModule(getName());
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
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


    // ----- NameResolving methods -----------------------------------------------------------------

    @Override
    public NameResolver getNameResolver()
        {
        NameResolver resolver = m_resolver;
        if (resolver == null || resolver.getNode() != this)
            {
            m_resolver = resolver = new NameResolver(this, collectNames(0).iterator());
            }
        return resolver;
        }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs)
        {
        Constant             constId    = getIdentityConstant();
        Access               access     = getExplicitAccess();
        List<TypeExpression> listParams = paramTypes;

        if (constId instanceof TypeConstant type)
            {
            // access needs to be null
            if (access != null)
                {
                throw new IllegalStateException("log error: access override unexpected");
                }

            // must be no type params
            if (listParams != null && !listParams.isEmpty())
                {
                throw new IllegalStateException("log error: type params unexpected");
                }

            return type;
            }

        // constId has been already "auto-narrowed" by resolveNames()
        ConstantPool pool = pool();
        TypeConstant type = calculateDefaultType(ctx, constId, ErrorListener.BLACKHOLE);

        if (listParams != null)
            {
            int            cParams     = listParams.size();
            TypeConstant[] atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                atypeParams[i] = listParams.get(i).ensureTypeConstant(ctx, errs);
                }

            type = pool.ensureParameterizedTypeConstant(type, atypeParams);
            }

        if (access != null && access != Access.PUBLIC)
            {
            type = pool.ensureAccessTypeConstant(type, access);
            }

        if (immutable != null)
            {
            type = pool.ensureImmutableTypeConstant(type);
            }

        return type;
        }

    @Override
    protected void setTypeConstant(TypeConstant constType)
        {
        if (!constType.containsUnresolved())
            {
            m_constId = constType;
            }

        super.setTypeConstant(constType);
        }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        info.addContribution(this);
        }

    @Override
    public boolean isDynamic()
        {
        return m_exprDynamic != null;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        if (left == null)
            {
            // check for a virtual child scenario: "parent.new [@Mixin] Child<...>(...)"
            AstNode parent = getParent();
            while (parent instanceof AnnotatedTypeExpression)
                {
                parent = parent.getParent();
                }
            if (parent instanceof NewExpression exprNew)
                {
                if (exprNew.left != null)
                    {
                    // defer the virtual child name resolution till validation time
                    m_fVirtualChild = true;
                    return;
                    }
                }

            while (parent != null)
                {
                if (parent instanceof TypeCompositionStatement stmt)
                    {
                    ClassStructure clz = (ClassStructure) stmt.getComponent();
                    if (stmt.getName().equals(getName()))
                        {
                        if (!stmt.alreadyReached(Stage.Resolved))
                            {
                            // mixins naturally imply formal type parameters from their contributions
                            // (most likely the "into"s); there is logic in TypeCompositionStatement that
                            // adds implicit type parameters, and we need to defer the resolution util
                            // then - see TypeCompositionStatement.addImplicitTypeParameters()
                            if (clz != null && clz.getFormat() == Component.Format.MIXIN &&
                                    !clz.isParameterized())
                                {
                                mgr.requestRevisit();
                                return;
                                }
                            }
                        // once we found the name we're looking for, we're done
                        break;
                        }

                    if (clz != null && clz.isTopLevel())
                        {
                        // the only reason we're in the loop in the first place is to look for an
                        // enclosing mixin of the same name; at this point, we've proven that it is
                        // not the case
                        break;
                        }
                    }
                parent = parent.getParent();
                }
            }
        else
            {
            // process "left" first
            boolean fDone = mgr.processChildrenExcept(node -> node != left);
            if (!fDone)
                {
                mgr.requestRevisit();
                return;
                }
            }

        ErrorListener errsTemp = errs.branch(this);
        NameResolver  resolver = getNameResolver();
        switch (resolver.resolve(errsTemp))
            {
            case DEFERRED:
                mgr.requestRevisit();
                return;

            case RESOLVED:
                {
                if (!mgr.processChildren())
                    {
                    mgr.requestRevisit();
                    return;
                    }

                Constant constId = resolver.getConstant();
                boolean fProceed = checkValidType(constId, errsTemp);

                if (!fProceed)
                    {
                    errsTemp.merge();
                    mgr.deferChildren();
                    return;
                    }

                // now that we have the resolved constId, update the unresolved m_constId to point to
                // the resolved one (just in case anyone is holding the wrong one
                Constant constIdNew = m_constId = inferAutoNarrowing(constId, errsTemp);

                if (!errsTemp.hasSeriousErrors())
                    {
                    if (m_constUnresolved != null)
                        {
                        m_constUnresolved.resolve(constIdNew);
                        m_constUnresolved = null;
                        }

                    UnresolvedTypeConstant typeUnresolved = m_typeUnresolved;
                    if (typeUnresolved != null)
                        {
                        if (typeUnresolved.containsUnresolved())
                            {
                            TypeConstant typeNew = calculateDefaultType(null, constIdNew, errsTemp);
                            if (typeNew.containsUnresolved())
                                {
                                mgr.requestRevisit();
                                return;
                                }
                            typeUnresolved.resolve(typeNew);
                            }
                        m_typeUnresolved = null;
                        }
                    }

                if (errsTemp.hasSeriousErrors())
                    {
                    // cannot proceed
                    errsTemp.merge();
                    mgr.deferChildren();
                    return;
                    }

                // reset the type constant
                resetTypeConstant();
                break;
                }

            case ERROR:
                if (left == null && names != null && access == null
                        && immutable == null && paramTypes == null && getCodeContainer() != null)
                    {
                    // assume that the type is "dynamic", for example:
                    // "that.Element" or "that.Key.Element"
                    NameExpression exprDyn = new NameExpression(names.get(0));
                    getParent().adopt(exprDyn);

                    for (int i = 1, cNames = names.size(); i < cNames; i++)
                        {
                        NameExpression exprNext = new NameExpression(exprDyn, null, names.get(i), null, lEndPos);
                        exprDyn.adopt(exprNext);
                        exprDyn = exprNext;
                        }
                    m_exprDynamic = exprDyn;
                    return;
                    }

                // cannot proceed
                errsTemp.merge();
                break;
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool = pool();

        if (m_exprDynamic != null)
            {
            NameExpression exprOld = m_exprDynamic;
            NameExpression exprNew = (NameExpression) exprOld.validate(ctx, pool.typeType(), errs);
            if (exprNew == null)
                {
                return null;
                }
            m_exprDynamic = exprNew;

            TypeConstant typeType = exprNew.getType();

            if (exprNew.isSimpleName() && typeType.getParamType(0).equals(pool.typeObject()))
                {
                typeType = transformType(ctx, ctx.getMethod(), exprNew);
                }

            // the underlying type could be either dynamic formal (e.g. array.Element),
            // or an actual type (e.g. Person.StructType, which is equivalent to Person:struct)
            m_constId = typeType.getParamType(0);
            return finishValidation(ctx, typeRequired, typeType, TypeFit.Fit, null, errs);
            }

        if (m_constId == null || m_constId.containsUnresolved())
            {
            // first, try to re-resolve the type
            ErrorListener errsTemp = errs.branch(this);
            NameResolver  resolver = getNameResolver();
            switch (resolver.resolve(errsTemp))
                {
                case DEFERRED:
                case ERROR:
                    errsTemp.merge();
                    return null;

                case RESOLVED:
                    {
                    Constant constId = resolver.getConstant();
                    if (!checkValidType(constId, errsTemp))
                        {
                        errsTemp.merge();
                        return null;
                        }
                    m_constId = inferAutoNarrowing(constId, errsTemp);
                    }
                }
            }

        TypeConstant type;
        if (left == null)
            {
            if (m_constId instanceof TypeConstant constType)
                {
                type = constType;
                }
            else
                {
                type = calculateDefaultType(ctx, m_constId, errs);
                if (type.containsUnresolved())
                    {
                    log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, getName());
                    return null;
                    }
                }

            // there is no reason to look at the type parameters; the validation logic recurses
            // into the underlying NameTypeExpression nodes
            if (type.containsFormalType(false))
                {
                // the type is either a simple formal (Element), a formal child (Element.Key),
                // or a narrowed formal type (Element + Stringable)
                ctx.useFormalType(type, errs);
                }

            if (m_fExternalTypedef)
                {
                if (type.containsGenericType(true))
                    {
                    TypeConstant typeParent =
                        ((TypedefConstant) m_constId).getParentConstant().getType();
                    type = type.resolveGenerics(pool, typeParent.normalizeParameters());
                    }
                }
            }
        else
            {
            TypeExpression exprOld = left;
            Expression     exprNew = exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                return null;
                }

            type = left.getType().getParamType(0); // Type<DataType, OuterType>

            for (Token name : names)
                {
                TypeInfo infoLeft = type.ensureTypeInfo(errs);
                String   sName    = name.getValueText();

                type = infoLeft.calculateChildType(pool, sName);
                if (type == null)
                    {
                    log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                    return null;
                    }
                }
            }

        List<TypeExpression> listParams = paramTypes;
        if (listParams != null)
            {
            TypeConstant[] atypeParams = validateParameters(ctx, listParams, errs);
            if (atypeParams == null)
                {
                return null;
                }
            if (type.isParamsSpecified())
                {
                TypeConstant[] atypeActual = type.getParamTypesArray();
                if (!Arrays.equals(atypeActual, atypeParams))
                    {
                    // this can happen for example, if m_constId is a typedef for a function
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    return null;
                    }
                }
            else
                {
                // this is a duplicate of the check in calculateDefaultType() in case we got
                // to this point bypassing that logic
                if (type.isExplicitClassIdentity(true) &&
                        (type.isTuple() || atypeParams.length <= type.getMaxParamsCount()))
                    {
                    type = pool.ensureParameterizedTypeConstant(type, atypeParams);
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    return null;
                    }
                }
            }

        Access access = getExplicitAccess();
        if (access != null && access != Access.PUBLIC)
            {
            type = pool.ensureAccessTypeConstant(type, access);
            }

        if (immutable != null)
            {
            type = pool.ensureImmutableTypeConstant(type);
            }

        TypeConstant typeType = type.getType();
        return finishValidation(ctx, typeRequired, typeType, TypeFit.Fit, typeType, errs);
        }

    /**
     * Validate the type parameters.
     *
     * @return the type parameter types or null if the validation failed
     */
    private TypeConstant[] validateParameters(Context ctx, List<TypeExpression> listParams,
                                              ErrorListener errs)
        {
        ConstantPool pool   = pool();
        boolean      fValid = true;

        TypeConstant[] atypeParams = new TypeConstant[listParams.size()];
        for (int i = 0, c = listParams.size(); i < c; ++i)
            {
            TypeExpression exprOld = listParams.get(i);
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, pool.typeType(), errs);

            if (exprNew == null)
                {
                fValid = false;
                continue;
                }

            if (exprNew != exprOld)
                {
                listParams.set(i, exprNew);
                }

            atypeParams[i] = exprNew.ensureTypeConstant(ctx, errs);
            }
        return fValid ? atypeParams : null;
        }

    /**
     * Considering the containing class, calculate a type for the specified target constant in the
     * absence of explicitly provided type parameters.
     *
     * Note, the returned type should not be parameterized; it will be done later by the caller.
     *
     * @return a resulting type
     */
    protected TypeConstant calculateDefaultType(Context ctx, Constant constTarget, ErrorListener errs)
        {
        ConstantPool pool = pool();

        if (left == null)
            {
            // in a context of "this compilation unit", an absence of type parameters
            // is treated as "formal types" (only for virtual children).
            // Consider an example:
            //
            //  class Parent<T0>
            //    {
            //    void foo()
            //       {
            //       Parent p;  // (1) means Parent<T0>
            //       Child1 c1; // (2) means Child1<Parent<T0>>
            //       Child2 c2; // (3) means Child2<Parent<T0>, ?>
            //       Child3 c3; // (3) means naked Child3 type
            //       }
            //
            //    class Child1
            //      {
            //      void foo()
            //         {
            //         Parent p;  // (4) means Parent<T0>
            //         Child1 c1; // (5) means Child1<Parent<T0>>
            //         Child2 c2; // (6) means Child2<Parent<T0>, ?>
            //         }
            //      }
            //
            //    class Child2<T2>
            //      {
            //      void foo()
            //         {
            //         Parent p;  // (4) means Parent<T0>
            //         Child2 c2; // (5) means Child1<Parent<T0>, T2>
            //         Child1 c1; // (7) means Child1<Parent<T0>>
            //         }
            //      }
            //    }
            //

            boolean       fAllowFormal = false;
            boolean       fParent      = false;
            boolean       fRetainConst = true;
            ClassConstant idTarget;
            switch (constTarget.getFormat())
                {
                case ParentClass:
                    fParent = true;
                    // fall through
                case ThisClass:
                    // we can only generate an implicit formal type for "this" or "parent"
                    fAllowFormal = true;
                    idTarget     = (ClassConstant) ((PseudoConstant) constTarget).getDeclarationLevelClass();
                    break;

                case ChildClass:
                    // we don't retain the child const unless it's a virtual child
                    fRetainConst = false;
                    idTarget     = (ClassConstant) ((ChildClassConstant) constTarget).getDeclarationLevelClass();
                    break;

                case Class:
                    idTarget = (ClassConstant) constTarget;
                    break;

                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) constTarget;
                    if (idProp.isFormalType())
                        {
                        if (ctx != null)
                            {
                            // see if the FormalType was narrowed
                            Argument arg = ctx.getVar(idProp.getName());
                            if (arg != null)
                                {
                                TypeConstant typeType = arg.getType();
                                assert typeType.isTypeOfType();
                                return typeType.getParamType(0);
                                }
                            }

                        return idProp.getFormalType();
                        }
                    else
                        {
                        return new UnresolvedTypeConstant(pool,
                            new UnresolvedNameConstant(pool, idProp.getName()));
                        }
                    }

                case Typedef:
                    {
                    TypedefConstant  idTypedef = (TypedefConstant) constTarget;
                    TypeConstant     typeRef   = idTypedef.getReferredToType();
                    IdentityConstant idFrom    = idTypedef.getParentConstant();
                    IdentityConstant idClass   = getComponent().getContainingClass().getIdentityConstant();

                    if (!idFrom.isNestMateOf(idClass))
                        {
                        if (idFrom.equals(pool.clzType()))
                            {
                            // transform Type's typedefs, for example:
                            //      Element.Comparer => Type<Element>.Comparer
                            Constant idLeft = m_resolver.getBaseConstant();
                            if (idLeft instanceof PropertyConstant idProp)
                                {
                                typeRef = typeRef.resolveGenerics(pool,
                                        idProp.getFormalType().getType());
                                }
                            else if (idLeft instanceof TypeParameterConstant)
                                {
                                typeRef = typeRef.resolveGenerics(pool, idLeft.getType().getType());
                                }
                            }

                        // we are "importing" a typedef from an outside class and need to resolve all
                        // formal types to their canonical values; but we cannot do it until all names
                        // are resolved
                        m_fExternalTypedef = true;
                        }

                    return typeRef;
                    }

                case UnresolvedName:
                    if (m_exprDynamic != null && ctx != null)
                        {
                        TypeConstant type = m_exprDynamic.getImplicitType(ctx);
                        if (type != null)
                            {
                            assert type.isTypeOfType();
                            return type.getParamType(0);
                            }
                        }
                    return m_typeUnresolved =
                            new UnresolvedTypeConstant(pool, (UnresolvedNameConstant) constTarget);

                case TypeParameter:
                default:
                    idTarget = null;
                    break;
                }

            TypeConstant   typeTarget = null;
            Component      component  = getComponent();
            ClassStructure clzClass   = component.getContainingClass();
            if (idTarget != null && clzClass != null)
                {
                IdentityConstant idClass   = clzClass.getIdentityConstant();
                ClassStructure   clzTarget = (ClassStructure) idTarget.getComponent();

                if (idTarget.isNestMateOf(idClass))
                    {
                    if (clzTarget.isVirtualChild())
                        {
                        ClassConstant idBase = isFullyQualifiedChild()
                                ? idTarget.getAutoNarrowingBase()
                                : ((ClassConstant) idClass).getOutermost();
                        ClassStructure clzBase = (ClassStructure) idBase.getComponent();
                        if (clzBase.containsUnresolvedContribution())
                            {
                            return new UnresolvedTypeConstant(pool,
                                new UnresolvedNameConstant(pool, clzTarget.getName()));
                            }

                        // keep the formal types when in constructors or lambdas (a lambda that
                        // refers to a virtual child will make a decision to become a method later)
                        boolean fFormalParent = true;
                        if (component instanceof MethodStructure method)
                            {
                            fFormalParent = !method.isFunction() || method.isLambda();
                            }
                        boolean fFormalChild = fFormalParent && fAllowFormal && paramTypes == null;

                        typeTarget = fParent
                                ? clzTarget.getAutoNarrowingFormalType()
                                : pool.ensureVirtualTypeConstant(
                                    clzBase, clzTarget, fFormalParent, fFormalChild, constTarget);
                        assert typeTarget != null;
                        }
                    else if (clzClass.isVirtualDescendant(idTarget))
                        {
                        // the target is the context class itself (e.g. Interval type referred to
                        // by a method in Interval mixin), or
                        // the context class is a virtual child referring to its ascendant
                        // (e.g. List.Cursor property referring to the containing List type);
                        // default to the formal type unless the type parameters are explicitly
                        // specified by this expression or the context is static (e.g. function)
                        if (paramTypes == null && clzTarget.isParameterized() &&
                            (!component.isStatic() ||
                              component instanceof MethodStructure method &&
                                    method.isConstructor() && !method.isPropertyInitializer()))
                            {
                            typeTarget = pool.ensureClassTypeConstant(constTarget, null,
                                clzTarget.getFormalType().getParamTypesArray());
                            }
                        }

                    if (ctx != null && typeTarget != null)
                        {
                        typeTarget = ctx.resolveFormalType(typeTarget);
                        }
                    }
                else if (clzTarget.isVirtualChild())
                    {
                    ClassConstant  idBase  = idTarget.getAutoNarrowingBase();
                    ClassStructure clzBase = (ClassStructure) idBase.getComponent();

                    if (clzBase.containsUnresolvedContribution())
                        {
                        return new UnresolvedTypeConstant(pool,
                                new UnresolvedNameConstant(pool, clzTarget.getName()));
                        }
                    typeTarget = pool.ensureVirtualTypeConstant(clzBase, clzTarget, false, false, idTarget);
                    }

                else if (clzTarget.isInnerChild())
                    {
                    ClassStructure clzParent = clzTarget.getContainingClass();

                    typeTarget = pool.ensureInnerChildTypeConstant(clzParent.getFormalType(), idTarget);
                    }

                boolean fValid;
                if (clzTarget.isParameterized())
                    {
                    int cParams = paramTypes == null ? 0 : paramTypes.size();
                    fValid = clzTarget.isTuple() || cParams <= clzTarget.getTypeParamCount();
                    }
                else
                    {
                    fValid = paramTypes == null;
                    }

                if (!fValid)
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    }
                }

            return typeTarget == null
                    ? pool.ensureTerminalTypeConstant(fRetainConst ? constTarget : idTarget)
                    : typeTarget;
            }
        else
            {
            TypeConstant type = left.ensureTypeConstant(ctx, errs);

            switch (m_constId.getFormat())
                {
                case ChildClass:
                case Class:
                    if (names != null)
                        {
                        for (Token name : names)
                            {
                            if (!type.isExplicitClassIdentity(true))
                                {
                                log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, type.getValueString());
                                break;
                                }

                            ClassConstant  idLeft  = (ClassConstant) type.getSingleUnderlyingClass(true);
                            ClassStructure clzLeft = (ClassStructure) idLeft.getComponent();
                            String         sChild  = name.getValueText();
                            Component      child   = clzLeft.findChildDeep(sChild);
                            if (child instanceof ClassStructure clzChild)
                                {
                                if (clzChild.isVirtualChild())
                                    {
                                    type = isExplicitlyNonAutoNarrowing()
                                            ? pool.ensureVirtualChildTypeConstant(type, sChild)
                                            : pool.ensureThisVirtualChildTypeConstant(type, sChild);
                                    }
                                else
                                    {
                                    type = clzChild.getIdentityConstant().getType();
                                    }
                                }
                            else
                                {
                                log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE,
                                        child.getIdentityConstant());
                                break;
                                }
                            }
                        }
                    return type;

                case Typedef:
                    {
                    // resolve the typedef in the context of the referring type
                    TypeConstant typeTypedef = ((TypedefConstant) m_constId).getReferredToType();
                    return typeTypedef.resolveGenerics(pool, type);
                    }

                case UnresolvedName:
                    return m_typeUnresolved =
                        new UnresolvedTypeConstant(pool, (UnresolvedNameConstant) m_constId);

                default:
                    // invalid name; leave unresolved to be reported later
                    return new UnresolvedTypeConstant(pool,
                            new UnresolvedNameConstant(pool, getNames(), false));
                }
            }
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return isDynamic()
                ? m_exprDynamic.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs)
                : getType().resolveAutoNarrowingBase();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return false iff the specified constant doesn't represent a valid type and an error
     *         has been reported
     */
    private boolean checkValidType(Constant constId, ErrorListener errs)
        {
        Format format = constId.getFormat();
        if (!format.isTypeable())
            {
            log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, constId.getValueString());
            return false;
            }

        if (format == Format.Property || format == Format.Typedef)
            {
            if (paramTypes != null)
                {
                log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                return false;
                }
            if (format == Format.Property &&
                    (!((PropertyConstant) constId).isFormalType() ||
                        names != null && names.size() > 1))
                {
                log(errs, Severity.ERROR, Compiler.INVALID_FORMAL_TYPE_IDENTITY);
                return false;
                }
            }
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (module != null)
            {
            sb.append(getModule())
              .append(':');
            }

        if (left != null)
            {
            sb.append(left)
              .append('.');
            }

        if (immutable != null)
            {
            sb.append("immutable ");
            }

        sb.append(getName());

        if (access != null)
            {
            sb.append(':')
              .append(access.getId().TEXT);
            }

        if (nonnarrow != null)
            {
            sb.append('!');
            }

        if (paramTypes != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : paramTypes)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
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

    protected List<Token>          module;
    protected TypeExpression       left;
    protected Token                immutable;
    protected List<Token>          names;
    protected Token                access;
    protected Token                nonnarrow;
    protected List<TypeExpression> paramTypes;
    protected long                 lStartPos;
    protected long                 lEndPos;

    protected transient NameResolver   m_resolver;
    protected transient Constant       m_constId;
    protected transient boolean        m_fVirtualChild;
    protected transient boolean        m_fExternalTypedef;
    private   transient NameExpression m_exprDynamic;

    // unresolved constant that may have been created by this statement
    protected transient UnresolvedNameConstant m_constUnresolved;
    protected transient UnresolvedTypeConstant m_typeUnresolved;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "left", "paramTypes");
    }