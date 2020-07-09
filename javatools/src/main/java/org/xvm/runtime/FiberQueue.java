package org.xvm.runtime;


import org.xvm.runtime.Fiber.FiberStatus;

/**
 * {@link FiberQueue} represents a queue-like data structure holding all pending Fibers and
 * facilitating a quick selection algorithm for the scheduler.
 */
public class FiberQueue
    {
    // a circular array
    private Frame[] m_aFrame = new Frame[16];

    private int m_ixHead = -1; // head
    private int m_ixTail = 0;  // past the tail - insertion point
    private int m_cSize = 0;

    public void add(Frame frame)
        {
        ensureCapacity(++m_cSize);

        int iInsert = m_ixTail;

        if (m_aFrame[iInsert] != null)
            {
            compact();
            iInsert = m_ixTail;
            }
        m_aFrame[iInsert] = frame;

        if (m_ixHead < 0)
            {
            m_ixHead = iInsert;
            }
        m_ixTail = (iInsert + 1) % m_aFrame.length;
        }

    public boolean isEmpty()
        {
        return m_cSize == 0;
        }

    public int size()
        {
        return m_cSize;
        }

    // get the first of the waiting fibers that is either "ready" or timed-out
    public Frame getWaitingReady()
        {
        return getNext(2);
        }

    // get the first with a priority no less than "associated or yielded"
    public Frame getAssociatedOrYielded()
        {
        return getNext(1);
        }

    // get the first that is ready (any priority)
    public Frame getAnyReady()
        {
        return getNext(0);
        }

    // get the first frame (any priority)
    public Frame getAny()
        {
        return getNext(-1);
        }

    // get the next fiber with a priority no less than the specified one
    private Frame getNext(int nPriority)
        {
        if (m_cSize == 0)
            {
            return null;
            }

        int cFrames = m_aFrame.length;
        int ixHead  = m_ixHead;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (i + ixHead) % cFrames;

            int iPriority = checkPriority(ix);
            if (iPriority >= nPriority)
                {
                return remove(ix);
                }
            }
        return null;
        }

    // return the priority of the fiber at the specified index and update the cache indexes:
    // [2]  a waiting fiber that is marked as "ready"
    // [1]  initial associated or yielded
    // [0]  initial new
    // [-1] not ready
    // [-2] empty
    private int checkPriority(int ix)
        {
        Frame frame = m_aFrame[ix];
        if (frame == null)
            {
            return -2;
            }

        switch (frame.f_fiber.getStatus())
            {
            default:
                throw new IllegalStateException();

            case Waiting:
                if (frame.f_fiber.isReady())
                    {
                    return 2;
                    }
                else
                    {
                    return -1;
                    }

            case InitialAssociated:
            case Yielded:
                return 1;

            case InitialNew:
                {
                // we can only allow it to proceed (at priority zero):
                //  - in Exclusive mode - only if there are no waiting fibers;
                //  - in Prioritized mode - only if there are no waiting fibers
                //     associated with that same context

                switch (frame.f_context.m_reentrancy)
                    {
                    default:
                    case Forbidden:
                        // no more than one fiber is allowed
                        return m_cSize == 1 ? 0 : -1;

                    case Exclusive:
                        return isAssociatedWaiting(null) ? -1 : 0;

                    case Prioritized:
                        {
                        Fiber fiberCaller = frame.f_fiber.f_fiberCaller;
                        if (fiberCaller != null)
                            {
                            ServiceContext ctxCaller = fiberCaller.f_context;
                            if (isAssociatedWaiting(ctxCaller))
                                {
                                return -1;
                                }
                            }
                        // break through
                        }
                    case Open:
                        return 0;
                    }
                }
            }
        }

    /**
     * @return true iff there are any waiting frames associated with the specified context
     */
    private boolean isAssociatedWaiting(ServiceContext context)
        {
        return getFirstAssociatedIndex(FiberStatus.Waiting, context) != -1;
        }

    /**
     * Get the index of the first frame of the specified status associated with the specified context.
     *
     * NOTE: a waiting frame with a native stack frame is exempt from association rules since all
     *       the natural execution has completed.
     *
     * @param status   the status to check for
     * @param context  the service context to check association with (null for "any")
     *
     * @return the first matching index or -1
     */
    private int getFirstAssociatedIndex(FiberStatus status, ServiceContext context)
        {
        Frame[] aFrame  = m_aFrame;
        int     cFrames = aFrame.length;
        int     ixHead  = m_ixHead;

        for (int i = 0; i < cFrames; i++)
            {
            int   ix    = (ixHead + i) % cFrames;
            Frame frame = aFrame[ix];

            if (frame != null)
                {
                Fiber fiber = frame.f_fiber;
                if (fiber.getStatus() == status)
                    {
                    if (status == FiberStatus.Waiting && frame.isNativeStack())
                        {
                        // waiting native stack is exempt
                        continue;
                        }

                    if (context == null)
                        {
                        return ix;
                        }

                    Fiber fiberCaller = fiber.f_fiberCaller;
                    if (fiberCaller != null && fiberCaller.isAssociated(context))
                        {
                        return ix;
                        }
                    }
                }
            }
        return -1;
        }

    private Frame remove(int ix)
        {
        Frame frame = m_aFrame[ix];

        assert frame != null;

        m_aFrame[ix] = null;

        if (--m_cSize > 0)
            {
            // move the head if necessary
            if (ix == m_ixHead)
                {
                int cCapacity = m_aFrame.length;
                do
                    {
                    ix = (ix + 1) % cCapacity;
                    }
                while (m_aFrame[ix] == null);

                m_ixHead = ix;
                }
            }
        else
            {
            m_ixHead = -1;
            m_ixTail = 0;
            }
        return frame;
        }

    private void ensureCapacity(int cCapacity)
        {
        if (cCapacity > m_aFrame.length)
            {
            int cNewCapacity = cCapacity + (cCapacity >> 2); // 1.25

            Frame[] aNew = new Frame[cNewCapacity];

            System.arraycopy(m_aFrame, 0, aNew, 0, m_aFrame.length);
            m_aFrame = aNew;
            }
        }

    // move all the not-empty spaces toward the head
    private void compact()
        {
        Frame[] aFrame  = m_aFrame;
        int     cFrames = m_aFrame.length;

        assert aFrame[m_ixHead] != null;

        int iFrom = (m_ixHead + 1) % cFrames;
        int iTo   = iFrom;

        for (int i = 1, c = aFrame.length; i < c; i++)
            {
            Frame frame = aFrame[iFrom];
            if (frame != null)
                {
                if (iFrom != iTo)
                    {
                    assert aFrame[iTo] == null;
                    aFrame[iTo] = frame;
                    aFrame[iFrom] = null;
                    }
                iTo = (iTo + 1) % cFrames;
                }
            iFrom = (iFrom + 1) % cFrames;
            }

        assert aFrame[iTo] == null;
        m_ixTail = iTo;
        }

    public String toString()
        {
        return "size=" + m_cSize;
        }
    }
