package org.xvm.compiler.ast;


import java.util.Iterator;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.AmbiguousIdentityConstant;
import org.xvm.asm.constants.IdentityConstant;

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

    public boolean isFirstTime()
        {
        return m_status == Status.INITIAL;
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
                IdentityConstant constResult = m_node.resolveParentBySingleName(m_sName);

                // if there is no obvious (unhideable) component that the name refers to, then we
                // need to start with the current node, and one by one walk up to the root, asking
                // at each level for the node to resolve the name
                if (constResult == null)
                    {
                    AstNode node = m_node;
                    WalkUpToTheRoot: while (node != null)
                        {
                        // if the first name refers to an import, then ask that import to figure out
                        // what the corresponding qualified name refers to (i.e. delegate!)
                        if (node == m_blockImport)
                            {
                            NameResolver resolver = m_stmtImport.getNameResolver();
                            switch (resolver.resolve(listRevisit, errs))
                                {
                                case RESOLVED:
                                    constResult = resolver.getIdentityConstant();
                                    break WalkUpToTheRoot;

                                case DEFERRED:
                                    // dependent on a node that is deferred, so this is deferred
                                    listRevisit.add(m_node);
                                    return Result.DEFERRED;

                                default:
                                case ERROR:
                                    // no need to log an error; the import already should have
                                    m_status = Status.ERROR;
                                    return Result.ERROR;
                                }
                            }

                        // otherwise, ask the node to resolve the name, and if it can't, we'll come
                        // back later
                        if (node == m_node || node.canResolveSingleName())
                            {
                            constResult = node.resolveSingleName(m_sName);
                            if (constResult instanceof AmbiguousIdentityConstant)
                                {
                                // TODO log error - name is ambiguous (info is in the constant)
                                m_status = Status.ERROR;
                                return Result.ERROR;
                                }

                            if (constResult != null)
                                {
                                break WalkUpToTheRoot;
                                }
                            }
                        else
                            {
                            listRevisit.add(m_node);
                            return Result.DEFERRED;
                            }

                        node = node.getParent();
                        }
                    }

                // last chance: check the implicitly imported names
                if (constResult == null)
                    {
                    constResult = getPool().ensureImplicitlyImportedIdentityConstant(m_sName);
                    }

                if (constResult == null)
                    {
                    // TODO log error - name is not resolvable
                    m_status = Status.ERROR;
                    return Result.ERROR;
                    }

                // first name has been resolved
                m_component = constResult.getComponent();
                m_sName     = m_iter.hasNext() ? m_iter.next() : null;
                m_status    = Status.RESOLVED_PARTIAL;
                // fall through

            case RESOLVED_PARTIAL:
                // at this point, we have a component to work from, so the next name has to be
                // relative to that component
                while (m_sName != null)
                    {
                    // TODO need some sort of resolve (not just a "get" at this level)
                    Component componentNext = m_component.getChild(m_sName);
                    if (componentNext == null) // TODO or ambiguous
                        {
                        // TODO log error - dot-name is not resolvable
                        m_status = Status.ERROR;
                        return Result.ERROR;
                        }

                    m_sName = m_iter.hasNext() ? m_iter.next() : null;
                    }

                // no names left to resolve
                m_status = Status.RESOLVED;
                // fall through

            case RESOLVED:
                // already resolved
                return Result.RESOLVED;

            default:
            case ERROR:
                // already determined to be unresolvable
                return Result.ERROR;
            }
        }

    public Result getResult()
        {
        switch (m_status)
            {
            case INITIAL:
            case CHECKED_IMPORTS:
            case RESOLVED_PARTIAL:
                // not done yet
                return Result.DEFERRED;

            case RESOLVED:
                // completed successfully
                return Result.RESOLVED;

            default:
            case ERROR:
                // cannot complete successfully
                return Result.ERROR;
            }
        }

    public Component getComponent()
        {
        return m_component;
        }

    public IdentityConstant getIdentityConstant()
        {
        return m_component == null ? null : m_component.getIdentityConstant();
        }

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
    private Status           m_status = Status.INITIAL;

    /**
     * The import statement selected by the import-checking phase, if any possible match was found.
     */
    private ImportStatement  m_stmtImport;

    /**
     * The node that the import was registered with, if any possible import match was found.
     */
    private StatementBlock   m_blockImport;

    /**
     * The component representing what the node has thus far resolved to.
     */
    private Component        m_component;
    }
