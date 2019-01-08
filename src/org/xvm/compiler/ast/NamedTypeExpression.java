package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ChildClassConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;
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

    public NamedTypeExpression(Token immutable, List<Token> names, Token access, Token nonnarrow,
            List<TypeExpression> params, long lEndPos)
        {
        this.immutable  = immutable;
        this.names      = names;
        this.access     = access;
        this.nonnarrow  = nonnarrow;
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
                ClassConstant  idThatClass     = (ClassConstant) constId;
                ClassStructure structThisClass = getComponent().getContainingClass();
                ClassStructure structThatClass = (ClassStructure) idThatClass.getComponent();

                if (structThisClass != null
                        && structThisClass.getFormat().isAutoNarrowingAllowed()
                        && structThatClass.getFormat().isAutoNarrowingAllowed())
                    {
                    IdentityConstant idClz = structThisClass.getIdentityConstant();
                    if (idClz.getFormat() == Constant.Format.Class)
                        {
                        ClassConstant constThisClass = (ClassConstant) structThisClass.getIdentityConstant();
                        return constThisClass.calculateAutoNarrowingConstant(idThatClass);
                        }
                    }
                }
            }

        return constId;
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
            m_resolver = resolver = new NameResolver(this, names.stream().map(
                    token -> (String) token.getValue()).iterator());
            }
        return resolver;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        Constant             constId    = getIdentityConstant();
        Access               access     = getExplicitAccess();
        List<TypeExpression> listParams = this.paramTypes;

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
        if (listParams == null)
            {
            type = calculateDefaultType(null, constId);
            }
        else
            {
            int            cParams      = listParams.size();
            TypeConstant[] aconstParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                aconstParams[i] = listParams.get(i).ensureTypeConstant();
                }
            type = pool.ensureParameterizedTypeConstant(
                    pool.ensureTerminalTypeConstant(constId), aconstParams);
            }

        // unlike the parametrization, we shouldn't modify unresolved types; doing so can cause
        // a double-dipping during resolution (e.g. Object:protected:protected)
        if (!(type instanceof UnresolvedTypeConstant))
            {
            if (access != null && access != Access.PUBLIC)
                {
                type = pool.ensureAccessTypeConstant(type, access);
                }

            if (immutable != null)
                {
                type = pool.ensureImmutableTypeConstant(type);
                }
            }

        return type;
        }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        info.addContribution(this);
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        ErrorList    errsTemp = new ErrorList(100);
        NameResolver resolver = getNameResolver();
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
                if (!constId.getFormat().isTypable())
                    {
                    errsTemp.logTo(errs);
                    log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, constId.getValueString());
                    // cannot proceed
                    mgr.deferChildren();
                    return;
                    }

                // now that we have the resolved constId, update the unresolved m_constId to point to
                // the resolved one (just in case anyone is holding the wrong one
                Constant constIdNew = m_constId = inferAutoNarrowing(constId, errsTemp);

                if (m_constUnresolved != null)
                    {
                    m_constUnresolved.resolve(constIdNew);
                    m_constUnresolved = null;
                    }

                // update the type constant

                resetTypeConstant();
                TypeConstant typeNew = ensureTypeConstant();

                if (m_typeUnresolved != null)
                    {
                    m_typeUnresolved.resolve(typeNew);
                    m_typeUnresolved = null;
                    }
                break;
                }

            case ERROR:
                if (names.size() > 1 && access == null && immutable == null && paramTypes == null)
                    {
                    // assume that the type is "dynamic", for example: "that.ElementType"
                    return;
                    }

                // cannot proceed
                mgr.deferChildren();
                break;
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool         pool       = pool();
        List<TypeExpression> listParams = this.paramTypes;
        boolean              fValid     = true;
        TypeConstant         type;

        if (m_constId == null || m_constId.containsUnresolved())
            {
            // this can only mean that the name resolution ended in an error
            // and has been deferred

            NameExpression exprOld = new NameExpression(names.get(0));

            for (int i = 1, cNames = names.size(); i < cNames; i++)
                {
                NameExpression exprNext = new NameExpression(exprOld, null, names.get(i), null, lEndPos);
                exprNext.adopt(exprOld);
                exprOld = exprNext;
                }

            getParent().adopt(exprOld);

            Expression exprNew = exprOld.validate(ctx, pool().typeType(), errs);
            return exprNew == null
                    ? null
                    : finishValidation(typeRequired, exprNew.getType(), TypeFit.Fit, null, errs);
            }

        if (listParams == null)
            {
            type = calculateDefaultType(ctx, m_constId);
            }
        else
            {
            TypeConstant[] atypeParams = new TypeConstant[listParams.size()];
            for (int i = 0, c = listParams.size(); i < c; ++i)
                {
                TypeExpression exprOrig = listParams.get(i);
                TypeExpression expr     = (TypeExpression) exprOrig.validate(ctx, null, errs);
                if (expr == null)
                    {
                    fValid         = false;
                    atypeParams[i] = pool.typeObject();
                    }
                else
                    {
                    if (expr != exprOrig)
                        {
                        listParams.set(i, expr);
                        }

                    TypeConstant   typeParam = expr.getType();
                    TypeConstant[] atypeSub  = typeParam.getParamTypesArray();
                    if (atypeSub.length >= 1 && typeParam.isA(pool.typeType()))
                        {
                        atypeParams[i] = atypeSub[0];
                        }
                    else
                        {
                        expr.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            pool.ensureParameterizedTypeConstant(pool.typeType(),
                                pool.typeObject()).getValueString(), typeParam.getValueString());

                        fValid         = false;
                        atypeParams[i] = pool.typeObject();
                        }
                    }
                }
            type = pool.ensureParameterizedTypeConstant(
                    pool.ensureTerminalTypeConstant(m_constId), atypeParams);
            }

        TypeConstant typeType = pool.ensureParameterizedTypeConstant(pool.typeType(), type);

        return finishValidation(typeRequired, typeType, fValid ? TypeFit.Fit : TypeFit.NoFit, type, errs);
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

        IdentityConstant idFormalTarget;
        switch (constTarget.getFormat())
            {
            case ThisClass:
                idFormalTarget = ((ThisClassConstant) constTarget).getDeclarationLevelClass();
                break;

            case ChildClass:
                idFormalTarget = ((ChildClassConstant) constTarget).getDeclarationLevelClass();
                break;

            case Class:
                idFormalTarget = (IdentityConstant) constTarget;
                break;

            case Property:
                {
                PropertyConstant idProp = (PropertyConstant) constTarget;
                assert idProp.isTypeParameter();

                if (ctx != null)
                    {
                    // see if the FormalType was narrowed
                    Argument arg = ctx.getVar(idProp.getName());
                    if (arg != null)
                        {
                        return arg.getType();
                        }
                    }

                return idProp.getFormalType();
                }

            case TypeParameter:
                idFormalTarget = null;
                break;

            case UnresolvedName:
                return m_typeUnresolved =
                        new UnresolvedTypeConstant(pool, (UnresolvedNameConstant) constTarget);

            default:
                idFormalTarget = null;
                break;
            }

        TypeConstant typeTarget = pool.ensureTerminalTypeConstant(constTarget);
        if (idFormalTarget != null)
            {
            ClassStructure clzTarget = (ClassStructure) idFormalTarget.getComponent();
            if (clzTarget.isParameterized())
                {
                ClassStructure   clzClass = getComponent().getContainingClass();
                IdentityConstant idClass  = clzClass.getIdentityConstant();

                if (idFormalTarget.isNestMateOf(idClass))
                    {
                    // TODO: for child classes create ParameterizedTC(ChildTC(typeParent, clzChild))
                    typeTarget = pool.ensureParameterizedTypeConstant(typeTarget,
                            clzTarget.getFormalType().getParamTypesArray());
                    }
                }
            }
        return typeTarget;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

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

    protected Token                immutable;
    protected List<Token>          names;
    protected Token                access;
    protected Token                nonnarrow;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    protected transient NameResolver m_resolver;
    protected transient Constant     m_constId;

    // unresolved constant that may have been created by this statement
    protected transient UnresolvedNameConstant m_constUnresolved;
    protected transient UnresolvedTypeConstant m_typeUnresolved;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "paramTypes");
    }
