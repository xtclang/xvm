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

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
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

    public String[] getModuleNames()
        {
        List<Token> list = module;
        int         c    = list.size();
        String[]    as   = new String[c];
        for (int i = 0; i < c; ++i)
            {
            as[i] = list.get(i).getValueText();
            }
        return as;
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
        else if (constId instanceof ResolvableConstant)
            {
            m_constId = constId = ((ResolvableConstant) constId).unwrap();
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

        switch (access.getId())
            {
            case PUBLIC:
                return Access.PUBLIC;
            case PROTECTED:
                return Access.PROTECTED;
            case PRIVATE:
                return Access.PRIVATE;
            case STRUCT:
                return Access.STRUCT;
            default:
                throw new IllegalStateException("access=" + access);
            }
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

            if (parent instanceof AnnotatedTypeExpression &&
                    type == ((AnnotatedTypeExpression) parent).type)
                {
                type = (TypeExpression) parent;
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
            else if (constId instanceof ClassConstant) // isAutoNarrowingAllowed()
                {
                ClassStructure clzThis = getComponent().getContainingClass();
                if (clzThis != null && clzThis.getIdentityConstant().getFormat() == Format.Class)
                    {
                    return ((ClassConstant) clzThis.getIdentityConstant()).
                            calculateAutoNarrowingConstant((ClassConstant) constId);
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

        if (left instanceof NamedTypeExpression)
            {
            listNames = ((NamedTypeExpression) left).collectNames(cNames + cNamesThis);
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
    protected TypeConstant instantiateTypeConstant(Context ctx)
        {
        Constant             constId    = getIdentityConstant();
        Access               access     = getExplicitAccess();
        List<TypeExpression> listParams = paramTypes;

        if (constId instanceof TypeConstant)
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

            return (TypeConstant) constId;
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
                atypeParams[i] = listParams.get(i).ensureTypeConstant(ctx);
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
            if (parent instanceof NewExpression)
                {
                NewExpression exprNew = (NewExpression) parent;
                if (exprNew.left != null)
                    {
                    // defer the virtual child name resolution till validation time
                    m_fVirtualChild = true;
                    return;
                    }
                }

            while (parent != null)
                {
                if (parent instanceof TypeCompositionStatement)
                    {
                    TypeCompositionStatement stmt = (TypeCompositionStatement) parent;
                    ClassStructure           clz  = (ClassStructure) stmt.getComponent();
                    if (stmt.getName().equals(getName()))
                        {
                        if (!stmt.alreadyReached(Stage.Resolved))
                            {
                            // mixins naturally imply formal type parameters from their contributions
                            // (most likely the "into"s); there is logic in TypeCompositionStatement that
                            // adds implicit type parameters and we need to defer the resolution util
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

        ErrorListener errsTemp = errs.branch();
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
                Format   format  = constId.getFormat();
                boolean fProceed = true;
                if (!format.isTypeable())
                    {
                    errsTemp.merge();
                    log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, constId.getValueString());
                    fProceed = false;
                    }
                else if (format == Format.Property || format == Format.Typedef)
                    {
                    if (paramTypes != null)
                        {
                        errsTemp.merge();
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                        fProceed = false;
                        }
                    else if (format == Format.Property && ((PropertyConstant) constId).isFormalType()
                            && names != null && names.size() > 1)
                        {
                        errsTemp.merge();
                        log(errs, Severity.ERROR, Compiler.INVALID_FORMAL_TYPE_IDENTITY);
                        fProceed = false;
                        }
                    }

                if (!fProceed)
                    {
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
                if (left == null && names != null && names.size() > 1 && access == null
                        && immutable == null && paramTypes == null && getCodeContainer() != null)
                    {
                    // assume that the type is "dynamic", for example: "that.Element"
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
        ConstantPool         pool       = pool();
        List<TypeExpression> listParams = paramTypes;
        boolean              fValid     = true;
        TypeConstant         type       = null;

        if (m_constId == null || m_constId.containsUnresolved())
            {
            // first, try to re-resolve the type (ignore "deferred" or "error" scenarios)
            ErrorListener errsTemp = errs.branch();
            NameResolver  resolver = getNameResolver();
            switch (resolver.resolve(errsTemp))
                {
                case DEFERRED:
                case ERROR:
                    break;

                case RESOLVED:
                    {
                    Constant constId = resolver.getConstant();
                    if (!constId.getFormat().isTypeable())
                        {
                        errsTemp.merge();
                        log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, constId.getValueString());
                        return null;
                        }

                    if (constId.getFormat() == Format.Property)
                        {
                        if (paramTypes != null)
                            {
                            errsTemp.merge();
                            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                            return null;
                            }
                        if (((PropertyConstant) constId).isFormalType()
                                && names != null && names.size() > 1)
                            {
                            errsTemp.merge();
                            log(errs, Severity.ERROR, Compiler.INVALID_FORMAL_TYPE_IDENTITY);
                            return null;
                            }
                        }

                    m_constId = inferAutoNarrowing(constId, errsTemp);
                    }
                }
            }

        if (m_constId == null || m_constId.containsUnresolved())
            {
            // this can only mean that the name resolution ended in an error
            // and has been deferred (e.g. "that.Element" or "that.Key.Element")
            NameExpression exprOld = new NameExpression(names.get(0));
            getParent().adopt(exprOld);

            for (int i = 1, cNames = names.size(); i < cNames; i++)
                {
                NameExpression exprNext = new NameExpression(exprOld, null, names.get(i), null, lEndPos);
                exprOld.adopt(exprNext);
                exprOld = exprNext;
                }

            NameExpression exprNew = m_exprDynamic =
                    (NameExpression) exprOld.validate(ctx, pool().typeType(), errs);

            if (exprNew == null ||
                finishValidation(ctx, typeRequired, exprNew.getType(), TypeFit.Fit, null, errs) == null)
                {
                return null;
                }
            m_constId = exprNew.getType();
            return this;
            }

        if (left == null)
            {
            if (m_constId instanceof TypeConstant)
                {
                type = (TypeConstant) m_constId;
                }
            else
                {
                type = calculateDefaultType(ctx, m_constId, errs);
                }

            if (type.containsGenericType(false))
                {
                // the type is either a simple generic (Element), a formal child (Element.Key),
                // or a narrowed formal type (Element + Stringable)
                ctx.useGenericType(names.get(0).getValueText(), errs);
                }

            if (m_fExternalTypedef && type.containsGenericType(true))
                {
                TypeConstant typeParent =
                    ((TypedefConstant) m_constId).getParentConstant().getType();
                type = type.resolveGenerics(pool, typeParent.normalizeParameters());
                }
            }
        else
            {
            TypeExpression exprOld = left;

            Expression exprNew = exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                type = left.getType().getParamType(0); // Type<DataType, OuterType>

                for (Token name : names)
                    {
                    TypeInfo infoLeft = type.ensureTypeInfo(errs);
                    String   sName    = name.getValueText();

                    type = infoLeft.calculateChildType(pool, sName);
                    if (type == null)
                        {
                        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                        fValid = false;
                        break;
                        }
                    }
                }
            }

        if (fValid && listParams != null)
            {
            TypeConstant[] atypeParams = validateParameters(ctx, listParams, errs);
            if (atypeParams == null)
                {
                fValid = false;
                }
            else if (type.isParamsSpecified())
                {
                TypeConstant[] atypeActual = type.getParamTypesArray();
                if (!Arrays.equals(atypeActual, atypeParams))
                    {
                    // this can happen for example, if m_constId is a typedef for a function
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                    fValid = false;
                    }
                }
            else
                {
                // this is a duplicate of the check in calculateDefaultType() in case we got
                // to this point bypassing that logic
                if (type.isExplicitClassIdentity(true))
                    {
                    ClassStructure clzTarget = (ClassStructure)
                            type.getSingleUnderlyingClass(true).getComponent();

                    fValid = clzTarget.isTuple() ||
                             atypeParams.length <= clzTarget.getTypeParamCount();
                    }
                else
                    {
                    fValid = false;
                    }

                if (fValid)
                    {
                    type = pool.ensureParameterizedTypeConstant(type, atypeParams);
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
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
        return fValid
                ? finishValidation(ctx, typeRequired, typeType, TypeFit.Fit, typeType, errs)
                : null;
        }

    /**
     * Validate the parameters.
     *
     * @return the parameter types or null if the validation failed
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
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                continue;
                }

            if (exprNew.isDynamic())
                {
                log(errs, Severity.ERROR, Compiler.UNSUPPORTED_DYNAMIC_TYPE_PARAMS);
                fValid = false;
                continue;
                }

            if (exprNew != exprOld)
                {
                listParams.set(i, exprNew);
                }

            TypeConstant   typeParam = exprNew.getType();
            TypeConstant[] atypeSub  = typeParam.getParamTypesArray();
            if (atypeSub.length >= 1 && typeParam.isA(pool.typeType()))
                {
                atypeParams[i] = atypeSub[0];
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool.typeType().getValueString(), typeParam.getValueString());
                fValid = false;
                }
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

            boolean       fThisClass          = false;
            boolean       fAllowFormalVirtual = false;
            ClassConstant idTarget;
            switch (constTarget.getFormat())
                {
                case ThisClass:
                    fThisClass = true;
                    // fall through
                case ParentClass:
                    fAllowFormalVirtual = true;
                    // fall through
                case ChildClass:
                    idTarget = (ClassConstant) ((PseudoConstant) constTarget).getDeclarationLevelClass();
                    break;

                case Class:
                    idTarget = (ClassConstant) constTarget;
                    break;

                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) constTarget;
                    assert idProp.isFormalType();

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

                case Typedef:
                    {
                    TypedefConstant  idTypedef = (TypedefConstant) constTarget;
                    TypeConstant     typeRef   = idTypedef.getReferredToType();
                    IdentityConstant idFrom    = idTypedef.getParentConstant();
                    IdentityConstant idClass   = getComponent().getContainingClass().getIdentityConstant();

                    if (!idFrom.isNestMateOf(idClass))
                        {
                        // we are "importing" a typedef from an outside class and need to resolve all
                        // formal types to their canonical values; but we cannot do it until all names
                        // are resolved
                        m_fExternalTypedef = true;
                        }

                    return typeRef;
                    }

                case UnresolvedName:
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
                        ClassConstant  idBase  = ((ClassConstant) idClass).getOutermost();
                        ClassStructure clzBase = (ClassStructure) idBase.getComponent();

                        if (clzBase.containsUnresolvedContribution())
                            {
                            return new UnresolvedTypeConstant(pool,
                                new UnresolvedNameConstant(pool, clzTarget.getName()));
                            }

                        // Note: keep the formal types when in a constructor
                        boolean fFormalParent = !(component instanceof MethodStructure &&
                                            ((MethodStructure) component).isFunction());
                        boolean fFormalChIld = fFormalParent && fAllowFormalVirtual && paramTypes == null;

                        typeTarget = pool.ensureVirtualTypeConstant(
                                clzBase, clzTarget, fFormalParent, fFormalChIld, fThisClass);
                        assert typeTarget != null;
                        }
                    else if (idTarget.equals(idClass) || clzClass.isVirtualChild())
                        {
                        // the target is the context class itself (e.g. Interval type referred to
                        // by a method in Interval mixin), or
                        // the context class is a virtual child referring to an outside mate
                        // (e.g. List.Cursor property referring to the containing List type);
                        // default to the formal type unless the type parameters are explicitly
                        // specified by this expression or the context is static (e.g. function)
                        if (clzTarget.isParameterized() && paramTypes == null && !component.isStatic())
                            {
                            typeTarget = pool.ensureClassTypeConstant(constTarget, null,
                                clzTarget.getFormalType().getParamTypesArray());
                            }
                        }

                    if (ctx != null && typeTarget != null)
                        {
                        typeTarget = typeTarget.resolveGenerics(pool, ctx.getFormalTypeResolver());
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
                    typeTarget = pool.ensureVirtualTypeConstant(clzBase, clzTarget, false, false, false);
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
                    ? pool.ensureTerminalTypeConstant(constTarget)
                    : typeTarget;
            }
        else
            {
            TypeConstant type = left.ensureTypeConstant(ctx);

            switch (m_constId.getFormat())
                {
                case Class:
                    if (names != null)
                        {
                        for (Token name : names)
                            {
                            type = pool.ensureVirtualChildTypeConstant(type, name.getValueText());
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
            sb.append(left.toString())
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
