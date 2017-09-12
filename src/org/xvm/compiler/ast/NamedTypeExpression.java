package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.NameResolver.Result;

import static org.xvm.compiler.Lexer.isValidQualifiedModule;


/**
 * A type expression specifies a named type with optional parameters.
 *
 * @author cp 2017.03.31
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
            m_constId = constId = new UnresolvedNameConstant(getConstantPool(), getNames());
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
     * @return true iff the type is explicitly non-auto-narrowing
     */
    public boolean isNonAutoNarrowing()
        {
        return nonnarrow != null;
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

        ConstantPool pool      = getConstantPool();
        TypeConstant constType = pool.ensureTerminalTypeConstant(constId);

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
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        boolean fWasResolved = getStage().ordinal() >= Stage.Resolved.ordinal();
        super.resolveNames(listRevisit, errs);
        boolean fIsResolved  = getStage().ordinal() >= Stage.Resolved.ordinal();

        if (fIsResolved && !fWasResolved)
            {
            NameResolver resolver = m_resolver;
            if (resolver.getResult() == Result.ERROR)
                {
                return;
                }
            assert resolver.getResult() == Result.RESOLVED;

            Constant constId = resolver.getConstant();
            if (m_constId instanceof ResolvableConstant)
                {
                ((ResolvableConstant) m_constId).resolve(constId);
                }
            m_constId = constId;

            ensureTypeConstant();
            }
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
