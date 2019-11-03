package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
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
        this.lEndPos    = lEndPos;
        }

    /**
     * Construct a NamedTypeExpression with a "left".
     */
    public NamedTypeExpression(NamedTypeExpression left, List<Token> names,
                               List<TypeExpression> params, long lEndPos)
        {
        this.left       = left;
        this.immutable  = null;
        this.names      = names;
        this.access     = null;
        this.nonnarrow  = null;
        this.paramTypes = params;
        this.lEndPos    = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Assemble the qualified name.
     *
     * @return the dot-delimited name
     */
    public String getName()
        {
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
            sb.append(name.getValue());
            }

        return sb.toString();
        }

    public String[] getNames()
        {
        List<Token> list = names;
        int         c    = list.size();
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

            if (parent instanceof TypeExpression)
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
        int          cNamesThis = listThis.size();
        List<String> listNames;

        if (left == null)
            {
            listNames = new ArrayList<>(cNames + cNamesThis);
            }
        else
            {
            listNames = left.collectNames(cNames + names.size());
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
        return immutable == null ? names.get(0).getStartPosition() : immutable.getStartPosition();
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

        ConstantPool pool = pool();

        // constId has been already "auto-narrowed" by resolveNames()
        TypeConstant type;
        if (left == null)
            {
            type = calculateDefaultType(ctx, constId);
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
            }
        else
            {
            type = left.ensureTypeConstant(ctx);

            for (Token name : names)
                {
                type = pool.ensureVirtualChildTypeConstant(type, name.getValueText());
                }

            if (listParams != null)
                {
                int            cParams    = listParams.size();
                TypeConstant[] atypParams = new TypeConstant[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    atypParams[i] = listParams.get(i).ensureTypeConstant(ctx);
                    }
                type = pool.ensureParameterizedTypeConstant(type, atypParams);
                }
            }

        if (type.containsUnresolved())
            {
            m_typeValidate = type;
            }
        else
            {
            if (type.validate(ErrorListener.BLACKHOLE))
                {
                // we cannot report any error now, so let's just create a non-resolvable
                // type that look like this type
                return new UnresolvedTypeConstant(pool,
                    new UnresolvedNameConstant(pool, getNames(), false));
                }
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
            // check for a virtual child scenario: "parent.new Child<...>(...)"
            AstNode parent = getParent();
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
                boolean fProceed = true;
                if (!constId.getFormat().isTypeable())
                    {
                    errsTemp.merge();
                    log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, constId.getValueString());
                    fProceed = false;
                    }
                else if (constId.getFormat() == Format.Property)
                    {
                    if (paramTypes != null)
                        {
                        errsTemp.merge();
                        log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
                        fProceed = false;
                        }
                    else if (((PropertyConstant) constId).isFormalType() && names.size() > 1)
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

                    if (m_typeUnresolved != null)
                        {
                        m_typeUnresolved.resolve(calculateDefaultType(null, constIdNew));
                        m_typeUnresolved = null;
                        }

                    if (m_typeValidate != null)
                        {
                        m_typeValidate.validate(errsTemp);
                        m_typeValidate = null;
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
                if (left == null && access == null && immutable == null && paramTypes == null &&
                        getCodeContainer() != null)
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

            return exprNew == null
                    ? null
                    : finishValidation(typeRequired, exprNew.getType(), TypeFit.Fit, null, errs);
            }

        if (left == null)
            {
            if (m_constId instanceof TypeConstant)
                {
                type = (TypeConstant) m_constId;
                }
            else
                {
                type = calculateDefaultType(ctx, m_constId);
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
                type = type.resolveGenerics(pool, typeParent.normalizeParameters(pool));
                }
            }
        else
            {
            NamedTypeExpression exprOld = left;

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

                    // is it a typedef?
                    TypeConstant typeChild = infoLeft.getTypedefType(sName);
                    if (typeChild != null)
                        {
                        // resolve the typedef in the context of its container
                        type = typeChild.resolveGenerics(pool, infoLeft.getType());
                        continue;
                        }

                    // is it a virtual child?
                    typeChild = infoLeft.getVirtualChildType(sName);
                    if (typeChild != null)
                        {
                        type = typeChild;
                        continue;
                        }

                    log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                    fValid = false;
                    break;
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
                type = pool.ensureParameterizedTypeConstant(type, atypeParams);
                }
            }

        return fValid
                ? finishValidation(typeRequired, type.getType(), TypeFit.Fit, null, errs)
                : finishValidation(typeRequired, null, TypeFit.NoFit, null, errs);
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
     * @return a resulting type
     */
    protected TypeConstant calculateDefaultType(Context ctx, Constant constTarget)
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

        ConstantPool pool = pool();

        ClassConstant idTarget;
        switch (constTarget.getFormat())
            {
            case ThisClass:
            case ParentClass:
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
                TypedefConstant idTypedef = (TypedefConstant) constTarget;
                TypeConstant     typeRef  = idTypedef.getReferredToType();
                IdentityConstant idFrom   = idTypedef.getParentConstant();
                IdentityConstant idClass  = getComponent().getContainingClass().getIdentityConstant();

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

                    typeTarget = createVirtualTypeConstant(clzBase, clzTarget, true);
                    assert typeTarget != null;
                    }
                else
                    {
                    // the target is the base class itself or some of it's contributions
                    // (e.g. HashMap or Map if we are inside of HashMap);
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

                typeTarget = createVirtualTypeConstant(clzBase, clzTarget, false);
                }
            }

        return typeTarget == null
                ? pool.ensureTerminalTypeConstant(constTarget)
                : typeTarget;
        }

    private TypeConstant createVirtualTypeConstant(ClassStructure clzBase, ClassStructure clzTarget, boolean fFormal)
        {
        assert clzTarget.isVirtualChild();

        String           sName     = clzTarget.getName();
        ClassStructure   clzParent = (ClassStructure) clzTarget.getParent();
        IdentityConstant idParent  = clzParent.getIdentityConstant();

        if (clzBase.equals(clzParent) || clzBase.hasContribution(idParent, false))
            {
            // we've reached the "top"
            TypeConstant typeParent = fFormal ? clzBase.getFormalType() : clzBase.getCanonicalType();
            return pool().ensureVirtualChildTypeConstant(typeParent, sName);
            }

        if (!clzParent.isVirtualChild())
            {
            // somehow the classes didn't coalesce
            return null;
            }

        TypeConstant typeParent = createVirtualTypeConstant(clzBase, clzParent, fFormal);
        TypeConstant typeTarget = pool().ensureVirtualChildTypeConstant(typeParent, sName);

        if (clzTarget.getTypeParamCount() > 0)
            {
            TypeConstant[] atypeParams = fFormal
                    ? clzTarget.getFormalType().getParamTypesArray()
                    : clzTarget.getCanonicalType().getParamTypesArray();

            typeTarget = pool().ensureParameterizedTypeConstant(typeTarget, atypeParams);
            }
        return typeTarget;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        assert isDynamic();

        return m_exprDynamic.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

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

    protected NamedTypeExpression  left;
    protected Token                immutable;
    protected List<Token>          names;
    protected Token                access;
    protected Token                nonnarrow;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    protected transient NameResolver   m_resolver;
    protected transient Constant       m_constId;
    protected transient boolean        m_fVirtualChild;
    protected transient boolean        m_fExternalTypedef;
    private   transient NameExpression m_exprDynamic;

    // unresolved constant that may have been created by this statement
    protected transient UnresolvedNameConstant m_constUnresolved;
    protected transient UnresolvedTypeConstant m_typeUnresolved;
    protected transient TypeConstant           m_typeValidate;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "left", "paramTypes");
    }
