package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import static org.xvm.compiler.Lexer.isValidQualifiedModule;


/**
 * A type expression specifies a named type with optional parameters.
 *
 * @author cp 2017.03.31
 */
public class NamedTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NamedTypeExpression(Token immutable, List<Token> names, Token access, List<TypeExpression> params, long lEndPos)
        {
        this.immutable  = immutable;
        this.names      = names;
        this.access     = access;
        this.paramTypes = params;
        this.lEndPos    = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Assemble the qualified name.
     *
     * @return the dot-delimited name                       ≤
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


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < Compiler.Stage.Resolved.ordinal())
            {
            ConstantPool pool = getComponent().getConstantPool();
            TypeConstant constType = null;

            // stages of resolving (so that this method can be invoked idempotently)
            switch (m_nResolving)
                {
                case 0: // evaluate imports
                    {
                    // the process of evaluating imports is done first (and must be done once and
                    // only once) the first time that this is invoked, because that is the "natural"
                    // occurrance of the invocation of this method, occurring as the recursive
                    // descent through the AST is conducted, such that only those imports that this
                    // particular AST node should be able to use will have been registered; because
                    // this may occur long before the name can be unambiguously resolved, the type
                    // expression stores off the import answer (if one is found) as as "possible
                    // answer" for resolving the first name, and remembers which AST node held the
                    // import information from whence the answer was obtained, so that a subsequent
                    // pass can determine if another possible answer (obtained by a later resolving
                    // stage) will hide the import, or alternatively if the import hides that second
                    // answer
                    String  sName = (String) names.get(0).getValue();
                    AstNode node  = this;
                    while (node != null)
                        {
                        if (node instanceof StatementBlock)
                            {
                            StatementBlock block = (StatementBlock) node;
                            if (block.isFileBoundary())
                                {
                                // we've traversed up to a synthetic StatementBlock that is used to
                                // enclose the AST nodes from logically nested files; that means
                                // that we've hit a file boundary, and no imports beyond that are
                                // visible within the file that contains the TypeExpression that is
                                // currently being resolved
                                break;
                                }

                            ImportStatement stmtImport = ((StatementBlock) node).getImport(sName);
                            if (stmtImport != null)
                                {
                                // if no other name preempts this import statement, then it will be
                                // used as the substitution for the first name
                                m_stmtImport  = stmtImport;
                                m_blockImport = block;      // remember where the import was found
                                break;
                                }
                            }
                        node = node.getParent();
                        }
                    ++m_nResolving;
                    }
                    // fall through
                case 1: // evaluate first name
                    {
                    // the only names not "hideable" are those in the "parent chain" from this node
                    // to the root, i.e. the name of the module, package(s), class(es), and so on,
                    // leading from the root down to the current node
                    String   sName    = (String) names.get(0).getValue();
                    Constant constant = resolveParentBySingleName(sName);
                    if (constant == null)
                        {
                        // starting at this point, walk up the AST node chain, asking each level if
                        // it knows what the name is that we're looking for; if we reach the node
                        // associated with the import of the name (from step 0), then use that name
                        // TODO
                        }
// need a "names left to resolve" list?
                    ++m_nResolving;
                    }
                    // fall through
                case 2: // evaluate additional names
                    {
                    // TODO
                    ++m_nResolving;
                    }
                    // fall through
                case 3: // resolved
                }

            // if there is an access specified, or parameter types are non-null, then it must be a
            // ClassTypeConstant
            if (access != null || paramTypes != null)
                {
                // TODO
                }

            // if it is immutable, then it must be an ImmutableTypeConstant
            if (immutable != null)
                {
                constType = pool.ensureImmutableTypeConstant(constType);
                }

            setTypeConstant(constType);

            super.resolveNames(listRevisit, errs);
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

    protected transient int             m_nResolving;
    protected transient ImportStatement m_stmtImport;
    protected transient StatementBlock  m_blockImport;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "paramTypes");
    }
