package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ChildClassConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ParentClassConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.NameResolver.Result;
import org.xvm.compiler.ast.Statement.Context;

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

        this.m_resolver = new NameResolver(this, names.stream().map(
                token -> (String) token.getValue()).iterator());
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
            as[i] = list.get(i).getValue().toString();
            }
        return as;
        }

    public Constant getIdentityConstant()
        {
        Constant constId = m_constId;
        if (constId == null)
            {
            m_constId = constId = new UnresolvedNameConstant(pool(), getNames(), isExplicitlyNonAutoNarrowing());
            }
        else if (constId instanceof ResolvableConstant)
            {
            m_constId = constId = ((ResolvableConstant) constId).unwrap();
            }
        return constId;
        }

    protected void setIdentityConstant(Constant constId)
        {
        if (constId instanceof ResolvableConstant)
            {
            constId = ((ResolvableConstant) constId).unwrap();
            }
        m_constId = constId;
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
     * <li>Type of a property;</li>
     * <li>Type of a method parameter;</li>
     * <li>Type of a method return value;</li>
     * <li>TODO</li>
     * </ul>
     *
     * @return true iff this type is used as part of a property type, a method return type, a method
     *         parameter type,
     */
    public boolean isAutoNarrowingAllowed()
        {
        TypeExpression type   = this;
        AstNode        parent = getParent();
        while (!(parent instanceof Statement))
            {
            if (parent instanceof TypeExpression)
                {
                type = (TypeExpression) parent;
                }

            parent = parent.getParent();
            }

        return parent instanceof ComponentStatement
                && ((ComponentStatement) parent).isAutoNarrowingAllowed(type);
        }

    /**
     * @return the constant to use
     */
    public Constant inferAutoNarrowing(Constant constId)
        {
        // check for auto-narrowing
        if (!constId.containsUnresolved() && isAutoNarrowingAllowed() != isExplicitlyNonAutoNarrowing())
            {
            if (isExplicitlyNonAutoNarrowing())
                {
                throw new IllegalStateException("log error: auto-narrowing override ('!') unexpected");
                }
            else if (constId instanceof ClassConstant) // and isAutoNarrowingAllowed()==true
                {
                // what is the "this:class"?
                Component component = getComponent();
                while (!(component instanceof ClassStructure))
                    {
                    component = component.getParent();
                    }
                ClassConstant constThisClass = (ClassConstant) component.getIdentityConstant();
                ClassConstant constThatClass = (ClassConstant) constId;

                // if "this:class" is the same as constId, then use ThisClassConstant(constId)
                if (constThisClass.equals(constThatClass))
                    {
                    return new ThisClassConstant(pool(), constThisClass);
                    }

                // get the "outermost class" for both "this:class" and constId
                ClassConstant constThisOutermost = constThisClass.getOutermost();
                ClassConstant constThatOutermost = constThatClass.getOutermost();
                if (constThisOutermost.equals(constThatOutermost))
                    {
                    // the two classes are related, so figure out how to describe "that" in relation
                    // to "this"
                    ConstantPool     pool       = pool();
                    PseudoConstant   constPath  = new ThisClassConstant(pool, constThisClass);
                    IdentityConstant constThis  = constThisClass;
                    IdentityConstant constThat  = constThatClass;
                    int              cThisDepth = constThisClass.getDepthFromOutermost();
                    int              cThatDepth = constThatClass.getDepthFromOutermost();
                    int              cReDescend = 0;
                    while (cThisDepth > cThatDepth)
                        {
                        constPath = new ParentClassConstant(pool, constPath);
                        constThis = constThis.getParentConstant();
                        --cThisDepth;
                        }
                    while (cThatDepth > cThisDepth)
                        {
                        ++cReDescend;
                        constThat = constThat.getParentConstant();
                        --cThatDepth;
                        }
                    while (!constThis.equals(constThat))
                        {
                        assert cThisDepth == cThatDepth && cThisDepth >= 0;

                        ++cReDescend;
                        constPath = new ParentClassConstant(pool, constPath);

                        constThis = constThis.getParentConstant();
                        constThat = constThat.getParentConstant();
                        --cThisDepth;
                        --cThatDepth;
                        }

                    constId = redescend(constPath, constThatClass, cReDescend);
                    }
                }
            }

        return constId;
        }

    /**
     * Recursively build onto the passed path to navigate the specified number of levels down to the
     * specified child.
     *
     * @param constPath   the path, thus far
     * @param constChild  the child to navigate to
     * @param cLevels     the number of levels down that the child is
     *
     * @return a PseudoConstant that represents the navigation down to the child
     */
    private PseudoConstant redescend(PseudoConstant constPath, IdentityConstant constChild, int cLevels)
        {
        if (cLevels == 0)
            {
            return constPath;
            }

        if (cLevels > 1)
            {
            constPath = redescend(constPath, constChild.getParentConstant(), cLevels-1);
            }

        return new ChildClassConstant(pool(), constPath, constChild.getName());
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
        return m_resolver;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        Constant             constId    = getIdentityConstant();
        Access               access     = getExplicitAccess();
        List<TypeExpression> paramTypes = this.paramTypes;

        if (constId instanceof TypeConstant)
            {
            // access needs to be null
            if (access != null)
                {
                throw new IllegalStateException("log error: access override unexpected");
                }

            // must be no type params
            if (paramTypes != null && !paramTypes.isEmpty())
                {
                throw new IllegalStateException("log error: type params unexpected");
                }

            return (TypeConstant) constId;
            }

        ConstantPool pool = pool();
        TypeConstant constType = pool().ensureTerminalTypeConstant(inferAutoNarrowing(constId));

        if (paramTypes != null)
            {
            int            cParams      = paramTypes.size();
            TypeConstant[] aconstParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                aconstParams[i] = paramTypes.get(i).ensureTypeConstant();
                }
            constType = pool.ensureParameterizedTypeConstant(constType, aconstParams);
            }

        if (access != null)
            {
            constType = pool.ensureAccessTypeConstant(constType, access);
            }

        if (immutable != null)
            {
            constType = pool.ensureImmutableTypeConstant(constType);
            }

        return constType;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        // TODO refactor (see note in super) - and also refactor ImportStatement

        boolean fWasResolved = alreadyReached(Stage.Resolved);
        AstNode nodeNew = super.resolveNames(listRevisit, errs);
        boolean fIsResolved  = alreadyReached(Stage.Resolved);

        if (nodeNew == this && fIsResolved && !fWasResolved)
            {
            NameResolver resolver = m_resolver;
            if (resolver.getResult() == Result.ERROR)
                {
                return this;
                }
            assert resolver.getResult() == Result.RESOLVED;

            // now that we have the resolved constId, update the unresolved m_constId to point to
            // the resolved one (just in case anyone is holding the wrong one
            Constant constId = inferAutoNarrowing(resolver.getConstant());
            if (m_constId instanceof ResolvableConstant)
                {
                ((ResolvableConstant) m_constId).resolve(constId);
                }

            // store the resolved id
            m_constId = constId;

            ensureTypeConstant();
            }

        return nodeNew;
        }

    @Override
    protected boolean validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;

        // TODO - @see NameResolver to resolve names: List<Token> names

        if (paramTypes != null)
            {
            for (TypeExpression type : paramTypes)
                {
                fValid &= type.validate(ctx, null, errs);
                }
            }

        return fValid;
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "paramTypes");
    }
