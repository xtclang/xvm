package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Constant;

import org.xvm.compiler.ErrorListener;


/**
 * This represents the progress toward resolution of a name for a particular AstNode.
 *
 * @author cp 2017.07.20
 */
public class NameResolver
    {
    public NameResolver(AstNode node)
        {
        assert node instanceof NameResolving;
        m_node = node;
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
        // TODO
        return Result.DEFERRED;
        }

//    /**
//     * Resolve a simple name.
//     * <p/>
//     * When evaluating a name, without any knowledge about the name, determine what the name refers
//     * to. In the case of a multi-part name, this specifically is the resolution of just the first
//     * part, i.e. up to the first dot.
//     * TODO conditional support, and possibly a CompositeConstant a la the CompositeComponent
//     *
//     * @param sName  the name to resolve
//     *
//     * @return the Constant that the name refers to
//     */
//    public Constant resolveFirstName(String sName)
//        {
//        // first see if one of the parents answers to that name (this ensures that we can always
//        // get to any global name by starting from the root)
//        Constant constant = resolveParentBySingleName(sName);
//
//        // first see if one of the parents answers to that name (this ensures that we can always
//        // get to any global name by starting from the root)
//
//
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
//        if (constant == null)
//            {
//            // implicit names
//            constant = getComponent().getConstantPool().ensureImplicitlyImportedIdentityConstant(sName);
//            }
//
//        return constant;
//        }

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
    private AstNode         m_node;

    /**
     * The current internal status of the name resolution.
     */
    private Status          m_status;

    /**
     * The import statement selected by the import-checking phase, if any possible match was found.
     */
    private ImportStatement m_stmtImport;

    /**
     * The node that the import was registered with, if any possible import match was found.
     */
    private StatementBlock  m_blockImport;
    }
