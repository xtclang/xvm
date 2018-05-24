package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Compiler.Stage;


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
     * @param errs         the error list to log to
     */
    public StageMgr(AstNode node, Stage stageTarget, ErrorListener errs)
        {
        assert node != null;
        assert stageTarget != null && stageTarget.isTargetable();
        assert stageTarget.isAtLeast(node.getStage());
        assert node.getStage().isAtLeast(stageTarget.getPrevTargetStage());

        m_cur    = node;
        m_target = stageTarget;
        m_errs   = errs == null ? ErrorListener.BLACKHOLE : errs;
        }

    /**
     * Process all of the nodes that can be processed at this point.
     *
     * @return true iff the stage manager has processing work left to do
     */
    public boolean revisitRequired()
        {
        // the first time through, m_cur is already set to the top node to process, and the revisit
        // list is not present
        AstNode node = m_cur;
        assert node == null | m_listRevisit == null;
        if (node != null)
            {
            m_cur = null;
            processInternal(node);
            }
        else if (m_listRevisit != null)
            {
            for (node : takeRevisitList())
                {
                processInternal(node);
                }
            }

        ++m_cIters;
        return m_listRevisit == null;
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
    protected void processInternal(AstNode node)
        {
        AstNode nodePrev      = m_cur;
        byte    nFlagsPrev    = m_nFlags;
        try
            {
            m_cur    = node;
            m_nFlags = 0;

            Stage stageCur = node.getStage();
            stageCur.ensureValid();

            Stage stageTarget = m_target;
            if (!stageCur.isAtLeast(stageTarget))
                {
                if (!stageCur.isAtLeast(stageTarget.getPrevTargetStage()))
                    {
                    throw new IllegalStateException("target stage=" + stageTarget
                            + ", expected minimum stage=" + stageTarget.getPrevTargetStage()
                            + ", node=" + node.getDumpDesc());
                    }

                node.setStage(stageTarget.getTransitionStage());
                switch (stageTarget)
                    {
                    case Registered:
                        node.registerStructures(this, m_errs);
                        break;

                    case Loaded:
                        break;
                    case Resolved:
                        break;
                    case Validated:
                        break;
                    case Emitted:
                        break;
                    }

                // TODO

                }

            if (!isChildrenProcessed() && !isChildrenDeferred())
                {
                processChildren();
                }

            if (!isRevisitRequested())
                {
                markComplete();
                }
            }
        finally
            {
            m_cur    = nodePrev;
            m_nFlags = nFlagsPrev;
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
        // TODO - as part of implementing processChildren()
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
    public void processChildren()
        {
        AstNode node = ensureCurrentNode();

        // mark this as having visited its children
        m_nFlags = (byte) (m_nFlags | VISITED_KIDS);

        // TODO TODO
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
     * Note: Normally the revisiting is performed by calling {@link #revisitRequired()} in a loop
     * until it returns {@code false}. Only call this method directly if assuming the responsibility
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

    // TODO expression validation stuff:
    public Expression validateExpression(Expression expr)
        {
        // TODO
        return null;
        }
    // TODO pack()
    // TODO unpack()
    // TODO convert()

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
     * The top node to process, or the current node being processed if processing is occurring.
     */
    private AstNode m_cur;

    /**
     * The processing flags associated with the currently processing node.
     */
    private byte m_nFlags;

    private static final int QUEUED_SELF  = 0x1;
    private static final int VISITED_KIDS = 0x2;
    private static final int DEFER_KIDS   = 0x4;
    }
