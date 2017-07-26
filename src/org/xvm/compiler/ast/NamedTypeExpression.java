package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.NameResolver.Result;

import org.xvm.util.Severity;

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

    public NamedTypeExpression(Token immutable, List<Token> names, Token access, List<TypeExpression> params, long lEndPos)
        {
        this.immutable  = immutable;
        this.names      = names;
        this.access     = access;
        this.paramTypes = params;
        this.lEndPos    = lEndPos;

        this.m_resolver = new NameResolver(this, names.stream().map(
                token -> (String) token.getValue()).iterator());
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public ClassTypeConstant asClassTypeConstant(ErrorListener errs)
        {
        TypeConstant constType = getTypeConstant();
        if (constType instanceof ClassTypeConstant)
            {
            return (ClassTypeConstant) constType;
            }

        // if there's already a type, and it's not a ClassType, then it's an error
        // if there's an immutable keyword, then it _can't_ be a ClassType
        if (constType != null && !(constType instanceof ResolvableConstant) || immutable != null)
            {
            log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE);
            }

        return super.asClassTypeConstant(errs);
        }

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
            assert resolver.getResult() == Result.RESOLVED;

            // determine the type's class
            IdentityConstant constId = resolver.getIdentityConstant();
            assert constId != null;
            setIdentityConstant(constId);

            // determine the type accessibility
            Access accessType = Access.PUBLIC;
            if (access != null)
                {
                switch (access.getId())
                    {
                    case PUBLIC:
                        accessType = Access.PUBLIC;
                        break;

                    case PROTECTED:
                        accessType = Access.PROTECTED;
                        break;

                    case PRIVATE:
                        accessType = Access.PRIVATE;
                        break;

                    default:
                        throw new IllegalStateException("access=" + access);
                    }
                }

            // determine the type parameters
            TypeConstant[] aconstParams = null;
            if (paramTypes != null)
                {
                int cParams = paramTypes.size();
                aconstParams = new TypeConstant[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    aconstParams[i] = paramTypes.get(i).getTypeConstant();
                    assert aconstParams[i] != null;
                    }
                }

            // create the ClassTypeConstant that represents the type expression
            ConstantPool pool      = getComponent().getConstantPool();
            TypeConstant constType = pool.ensureClassTypeConstant(constId, accessType, aconstParams);

            // if it is immutable, then it must be an ImmutableTypeConstant (which is _NOT_ a
            // ClassTypeConstant)
            if (immutable != null)
                {
                constType = pool.ensureImmutableTypeConstant(constType);
                }

            setTypeConstant(constType);
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
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    protected transient NameResolver m_resolver;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "paramTypes");
    }
