package org.xvm.compiler.ast;


import java.util.Iterator;
import java.util.List;

import org.xvm.asm.Constant;

import org.xvm.asm.ConstantPool;
import org.xvm.compiler.ErrorListener;


/**
 * This represents the progress toward resolution of a name for a particular AstNode.
 *
 * @author cp 2017.07.20
 */
public class NameResolver
    {
    public NameResolver(AstNode node, Iterator<String> iterNames)
        {
        assert node instanceof NameResolving;
        assert iterNames != null && iterNames.hasNext();

        m_node = node;
        m_iter = iterNames;
        }

    /**
     * TODO
     *
     * @param listRevisit  a list to add any nodes to that need to be revisted during this compiler
     *                     pass
     * @param errs         the error list to log any errors etc. to
     *
     * @return one of {@link Result#DEFERRED} to indicate that the NameResolver added the node to
     *         {@code listRevisit}, {@link Result#ERROR} to indicate that the NameResolver has
     *         logged an error to {@code errs} and given up all hope of resolving the name, or
     *         {@link Result#RESOLVED} to indicate that the name has been successfully resolved
     */
    public Result resolve(List<AstNode> listRevisit, ErrorListener errs)
        {
        switch (m_status)
            {
            case INITIAL:
                // just starting out. load the first name to resolve
                m_sName = m_iter.next();

                // the first name could be an import, in which case that needs to be evaluated right
                // away (because the imports will continue to be registered as the AST is resolved,
                // so the answers to the questions about the imports will change if we don't ask now
                // and store off the result)
                m_stmtImport = m_node.resolveImportBySingleName(m_sName);
                if (m_stmtImport != null)
                    {
                    AstNode parent = m_stmtImport.getParent();
                    while (!(parent instanceof StatementBlock))
                        {
                        parent = parent.getParent();
                        }
                    m_blockImport = (StatementBlock) parent;
                    }
                m_status = Status.CHECKED_IMPORTS;
                // fall through

            case CHECKED_IMPORTS:
                // resolve the first name if possible; otherwise defer (to come back to this point).
                // remember to check the import statement if it exists (but only use it when we make
                // our way up to the node that the import was registered with). if no one knows what
                // the first name is, then check if it is an implicitly imported identity.

                // check if the name is an unhideable name
                m_constResult = m_node.resolveParentBySingleName(m_sName);

                if (m_constResult == null)
                    {
                    AstNode node = m_node;
                    while (node != null)
                        {

                        }
                    }

                // last chance: check the implicitly imported names
                if (m_constResult == null)
                    {
                    m_constResult = getPool().ensureImplicitlyImportedIdentityConstant(m_sName);
                    }

                if (m_constResult == null)
                    {
                    // TODO log error
                    m_status = Status.ERROR;
                    return Result.ERROR;
                    }
                else if (!m_iter.hasNext())
                    {
                    // there was only one name to resolve, and it was a parent node; we're done
                    m_status = Status.RESOLVED;
                    return Result.RESOLVED;
                    }

                // first name has been resolved
                m_sName  = m_iter.next();
                m_status = Status.RESOLVED_PARTIAL;
                // fall through

            case RESOLVED_PARTIAL:
                // TODO - attempt to resolve the next name (previously resolved name will be spec'd by the constant)
                if (false)
                    {
                    }
                else if (m_iter.hasNext())
                    {
                    // first name was the name of one of the parent nodes; additional names to
                    // resolve from that parent
                    m_sName  = m_iter.next();
                    m_status = Status.RESOLVED_PARTIAL;
                    return resolve(listRevisit, errs);
                    }
                else
                    {
                    // there was only one name to resolve, and it was a parent node; we're done
                    m_status = Status.RESOLVED;
                    return Result.RESOLVED;
                    }

            case RESOLVED:
                // already resolved
                return Result.RESOLVED;

            default:
            case ERROR:
                // already determined to be unresolvable
                return Result.ERROR;
            }
        }

//        // next, walk up the AST tree, looking for that simple name. this has three sub-steps:
//        // 1) if the AST node is a statement block, then it may know an import by that name
//        // 2) if the AST node is a component statement, then the component may have a child by that
//        //    name
//        // 3) if the AST node is a component statement, then the component may know the name via one
//        //    or more of its compositions. if only by one, then that result is used. if by more than
//        //    one, then the results must all refer to the same thing, or the result is ambiguous,
//        //    and the name is unresolvable.
//        if (constant == null)
//            {
//            AstNode node = this;
//            while (node != null)
//                {
//                if (node instanceof StatementBlock)
//                    {
//                    ImportStatement stmt = ((StatementBlock) node).getImport(sName);
//                    if (stmt != null)
//                        {
//                        if (!stmt.canResolveName())
//                            {
//                            // TODO queue this resolve & return failure - need errs & retry list & a way to say "failure"
//                            }
//
//                        // the result can be determined by resolving the sequence of names
//                        // represented by the import
//                        // TODO = resolveFirstName(stmt.getQualifiedNamePart(0));
//                        // TODO need errs & retry list & a way to say "failure"
//                        }
//                    }
//                else if (node instanceof ComponentStatement)
//                    {
//                    ComponentStatement stmt = (ComponentStatement) node;
//                    // TODO name could reference a child
//                    }
//
//                node = node.getParent();
//                }
//            }
//

    private ConstantPool getPool()
        {
        return m_node.getComponent().getConstantPool();
        }

    // ----- inner classes -------------------------------------------------------------------------

    /**
     * Classes that can provide a NameResolver should implement this interface.
     */
    public interface NameResolving
        {
        NameResolver getNameResolver();
        }

    /**
     * The result of an attempt to resolve the name.
     */
    public enum Result {DEFERRED, RESOLVED, ERROR}

    /**
     * The possible internal states for the resolver.
     */
    private enum Status {INITIAL, CHECKED_IMPORTS, RESOLVED_PARTIAL, RESOLVED, ERROR}


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The node that this NameResolver is working for.
     */
    private AstNode          m_node;

    /**
     * The sequence of names to resolve.
     */
    private Iterator<String> m_iter;

    /**
     * The current simple name to resolve.
     */
    private String           m_sName;

    /**
     * The current internal status of the name resolution.
     */
    private Status           m_status;

    /**
     * The import statement selected by the import-checking phase, if any possible match was found.
     */
    private ImportStatement  m_stmtImport;

    /**
     * The node that the import was registered with, if any possible import match was found.
     */
    private StatementBlock   m_blockImport;

    /**
     * The constant representing what the node has thus far resolved to.
     */
    private Constant         m_constResult;
    }
