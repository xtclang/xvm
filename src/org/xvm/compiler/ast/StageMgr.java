package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.function.Predicate;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Compiler.Stage;

import org.xvm.compiler.ast.AstNode.ChildIterator;


/**
 * A Stage Manager is used to shepher the AST nodes through their various stages.
 */
public class StageMgr
    {
    /**
     * Construct a Stage Manager that will progress the specified node (and any under it) to the
     * specified target stage.
     *
     * @param node         the node to process
     * @param stageTarget  the target stage
     * @param errs         the optional error list to log to
     */
    public StageMgr(AstNode node, Stage stageTarget, ErrorListener errs)
        {
        assert node != null;
        assert stageTarget != null && stageTarget.isTargetable();

        m_listRevisit = Collections.singletonList(node);
        m_target      = stageTarget;
        m_errs        = errs == null ? ErrorListener.BLACKHOLE : errs;
        }

    /**
     * @return true iff the stage manager has completed the processing of the nodes to achieve the
     *         target stage
     */
    public boolean isComplete()
        {
        // complete if it isn't currently processing and there's nothing queued to process
        return m_cur == null && m_listRevisit == null;
        }

    /**
     * Process all of the nodes that can be processed at this point.
     *
     * @return true iff the stage manager has completed the processing of the nodes to achieve the
     *         target stage
     */
    public boolean processComplete()
        {
        if (m_listRevisit != null)
            {
            for (AstNode node : takeRevisitList())
                {
                processInternal(node);
                }
            }

        ++m_cIters;
        return m_listRevisit == null;
        }

    /**
     * From whatever stage the node is at, iterate through the stages to the target stage.
     *
     * @param cMaxIters the maximum number of iterations to try to fast-forward to the target stage
     *
     * @return true iff the fast-forward was successful
     */
    public boolean fastForward(int cMaxIters)
        {
        boolean fDone = false;
        Stage stageUltimate = m_target;
        try
            {
            m_target = Stage.Registered;
            while (m_target.compareTo(stageUltimate) <= 0 && m_cIters < cMaxIters)
                {
                while (!processComplete() && m_cIters < cMaxIters)
                    {
                    }

                if (isComplete())
                    {
                    if (m_target == stageUltimate)
                        {
                        fDone = true;
                        break;
                        }

                    // advance to next target
                    m_target = Stage.valueOf(m_target.ordinal() + 2);
                    }
                }
            }
        finally
            {
            m_target = stageUltimate;
            }
        return fDone;
        }

    /**
     * @return the number of processing iterations so far by this Stage Manager
     */
    public int getIterations()
        {
        return m_cIters;
        }

    /**
     * @return this Stage Manager's error list
     */
    public ErrorListener getErrorListener()
        {
        return m_errs;
        }

    /**
     * @return this Stage Manager's target Stage
     */
    public Stage getTargetStage()
        {
        return m_target;
        }

    /**
     * @param node  the node to attempt to advance to the target stage
     */
    protected boolean processInternal(AstNode node)
        {
        boolean fDone      = true;
        AstNode nodePrev   = m_cur;
        byte    nFlagsPrev = m_nFlags;
        try
            {
            m_cur    = node;
            m_nFlags = 0;

            Stage stageCur = node.getStage();
            stageCur.ensureValid();

            Stage stageTarget = m_target;
            if (stageCur.compareTo(stageTarget) < 0)
                {
                Stage stageRequired = requiredStage(stageTarget);
                if (stageCur.compareTo(stageRequired) < 0)
                    {
                    throw new IllegalStateException("current stage=" + stageCur
                            + ", target stage=" + stageTarget
                            + ", required stage=" + stageRequired
                            + ", node=" + node.getDumpDesc());
                    }

                node.setStage(stageTarget.getTransitionStage());
                switch (stageTarget)
                    {
                    case Registered:
                        node.registerStructures(this, m_errs);
                        break;

                    case Loaded:
                        // nothing to do
                        break;

                    case Resolved:
                        node.resolveNames(this, m_errs);
                        break;

                    case Validated:
                        node.validateExpressions(this, m_errs);
                        break;

                    case Emitted:
                        node.generateCode(this, m_errs);
                        break;

                    default:
                        throw new IllegalStateException("unsupported target: " + stageTarget);
                    }
                }

            if (!isChildrenProcessed() && !isChildrenDeferred())
                {
                fDone &= processChildren();
                }

            if (isRevisitRequested())
                {
                fDone = false;
                }
            else
                {
                markComplete();
                }
            }
        finally
            {
            m_cur    = nodePrev;
            m_nFlags = nFlagsPrev;
            }

        return fDone;
        }

    private Stage requiredStage(Stage target)
        {
        switch (target)
            {
            case Registered:
                return Stage.Initial;

            case Loaded:
            case Resolved:
                return Stage.Registered;

            case Validated:
                return Stage.Resolved;

            case Emitted:
                return Stage.Validated;

            default:
                throw new IllegalStateException("unsupported target: " + target);
            }
        }

    /**
     * @return the node currently being processed
     */
    public AstNode ensureCurrentNode()
        {
        AstNode node = m_cur;
        if (node == null)
            {
            throw new IllegalStateException();
            }
        return node;
        }

    /**
     * Replace the currently-being-processed node with another node.
     */
    public void replaceSelf(AstNode node)
        {
        AstNode       nodePrev = ensureCurrentNode();
        ChildIterator iterKids = m_iterKids;
        if (iterKids == null)
            {
            AstNode nodeParent = nodePrev.getParent();
            if (nodeParent == null)
                {
                throw new IllegalStateException("not a replaceable child: "
                        + nodePrev.getDumpDesc());
                }
            nodeParent.replaceChild(nodePrev, node);
            }
        else
            {
            iterKids.replaceWith(node);
            }
        }

    /**
     * Mark the current node as needing to be revisited.
     */
    public void requestRevisit()
        {
        AstNode node = ensureCurrentNode();
        if (!isRevisitRequested())
            {
            List<AstNode> list = m_listRevisit;
            if (list == null)
                {
                list = new ArrayList<>();
                m_listRevisit = list;
                }

            list.add(node);
            m_nFlags = (byte) (m_nFlags | QUEUED_SELF);
            }
        }

    /**
     * @return true iff this node has invoked requestRevisit()
     */
    public boolean isRevisitRequested()
        {
        return (m_nFlags & QUEUED_SELF) != 0;
        }

    /**
     * Suspend the processing of the current node and process each of its children, only returning
     * to the current node after all of the children return.
     */
    public boolean processChildren()
        {
        return processChildrenExcept(null);
        }

    /**
     * Suspend the processing of the current node and process each of its children, only returning
     * to the current node after all of the children return.
     */
    public boolean processChildrenExcept(Predicate exclude)
        {
        boolean fDone = true;
        ChildIterator iterPrev = m_iterKids;
        try
            {
            AstNode node = ensureCurrentNode();

            // create a child iterator
            ChildIterator iter = node.children();
            m_iterKids = iter;

            // mark this as having visited its children
            m_nFlags = (byte) (m_nFlags | VISITED_KIDS);

            while (iter.hasNext())
                {
                AstNode nodeChild = iter.next();
                if (exclude == null || !exclude.test(nodeChild))
                    {
                    fDone &= processInternal(nodeChild);
                    }
                }
            }
        finally
            {
            m_iterKids = iterPrev;
            }
        return fDone;
        }

    /**
     * @return true once processChildren() has been invoked
     */
    public boolean isChildrenProcessed()
        {
        return (m_nFlags & VISITED_KIDS) != 0;
        }

    /**
     * Specify that the children should <b>not</b> be processed by this stage.
     */
    public void deferChildren()
        {
        m_nFlags = (byte) (m_nFlags | DEFER_KIDS);
        }

    /**
     * @return true iff it has been specified that the processing of the children for this stage
     *         will be deferred
     */
    public boolean isChildrenDeferred()
        {
        return (m_nFlags & DEFER_KIDS) != 0;
        }

    /**
     * Mark the current node as completing.
     */
    public void markComplete()
        {
        ensureCurrentNode().setStage(m_target);
        }

    /**
     * Obtain the list of nodes that still require processing.
     * <p/>
     * Note: Normally the revisiting is performed by calling {@link #processComplete()} in a loop
     * until it returns {@code true}. Only call this method directly if assuming the responsibility
     * for finishing all of the processing.
     *
     * @return a list of nodes to revisit
     */
    public List<AstNode> takeRevisitList()
        {
        List<AstNode> listPrevious = m_listRevisit;
        m_listRevisit = null;
        return listPrevious == null
                ? Collections.EMPTY_LIST
                : listPrevious;
        }


    // ------ data members -------------------------------------------------------------------------

    /**
     * Target stage for this stage manager.
     */
    private Stage m_target;

    /**
     * List of nodes that still require processing.
     */
    private List<AstNode> m_listRevisit;

    /**
     * Count of iterations that this Stage Manager has already performed.
     */
    private int m_cIters;

    /**
     * Error list to log processing errors to.
     */
    private ErrorListener m_errs;

    /**
     * The current node being processed if processing is occurring.
     */
    private AstNode m_cur;

    /**
     * The processing flags associated with the currently processing node.
     */
    private byte m_nFlags;

    /**
     * The current ChildIterator, if processing is currently active and the node being processed is
     * a child node.
     */
    private ChildIterator m_iterKids;

    private static final int QUEUED_SELF  = 0x1;
    private static final int VISITED_KIDS = 0x2;
    private static final int DEFER_KIDS   = 0x4;
    }
