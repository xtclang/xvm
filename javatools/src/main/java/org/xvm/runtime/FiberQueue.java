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
    private int m_cSize  = 0;

    public void add(Frame frame)
        {
        Frame[] aFrame = ensureCapacity(++m_cSize);

        int iInsert = m_ixTail;

        if (aFrame[iInsert] != null)
            {
            compact();
            iInsert = m_ixTail;
            }
        aFrame[iInsert] = frame;

        if (m_ixHead < 0)
            {
            m_ixHead = iInsert;
            }
        m_ixTail = (iInsert + 1) % aFrame.length;
        }

    public boolean isEmpty()
        {
        return m_cSize == 0;
        }

    /**
     * @return {@code true} iff there are fibers which are ready for processing.
     */
    public boolean isReady()
        {
        if (m_cSize == 0)
            {
            return false;
            }

        int cFrames = m_aFrame.length;
        int ixHead  = m_ixHead;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (i + ixHead) % cFrames;

            int iPriority = checkPriority(ix);
            if (iPriority >= 0)
                {
                return true;
                }
            }
        return false;
        }

    public int size()
        {
        return m_cSize;
        }

    /**
     * Retrieve the first frame that is ready, giving priority to "associated or yielded".
     */
    public Frame getAssociatedOrYielded()
        {
        if (m_cSize == 0)
            {
            return null;
            }

        int cFrames = m_aFrame.length;
        int ixHead  = m_ixHead;
        int ixInit  = -1;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (i + ixHead) % cFrames;

            int iPriority = checkPriority(ix);
            if (iPriority >= 1)
                {
                return remove(ix);
                }
            if (iPriority == 0 && ixInit == -1)
                {
                ixInit = ix;
                }
            }

        return ixInit >= 0
            ? remove(ixInit)
            : null;
        }

    /**
     * Retrieve the first frame that is ready (any priority).
     */
    public Frame getAnyReady()
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
            if (iPriority >= 0)
                {
                return remove(ix);
                }
            }
        return null;
        }

    /**
     * Retrieve any available frame (any priority, ready or not).
     */
    public Frame getAny()
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

            if (m_aFrame[ix] != null)
                {
                return remove(ix);
                }
            }
        return null;
        }

    /**
     * Calculate the priority of the fiber at the specified index.
     * The return values are:
     * <ul>
     *   <li/>[2]  a waiting fiber that is marked as "ready"
     *   <li/>[1]  initial associated or yielded
     *   <li/>[0]  initial new
     *   <li/>[-1] not ready
     *   <li/>[-2] empty
     * </ul>
     */
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
                return frame.f_fiber.isReady() ? 2 : -1;

            case InitialAssociated:
            case Yielded:
                return 1;

            case InitialNew:
                {
                // we can only allow it to proceed (at priority zero):
                //  - in Exclusive mode - only if there are no waiting fibers;
                //  - in Prioritized mode - only if there are no waiting fibers
                //     associated with that same context
                switch (frame.f_context.getReentrancy())
                    {
                    default:
                    case Forbidden:
                        // no more than one fiber is allowed
                        return m_cSize == 1 ? 0 : -1;

                    case Exclusive:
                        // don't allow a new fiber unless it belongs to already existing thread of
                        // execution or the only one
                        return isAssociatedWaiting(null) ? -1 : 0;

                    case Prioritized:
                        {
                        // don't allow a new fiber if it has a common origin with any waiting fiber
                        Fiber fiberCaller = frame.f_fiber.f_fiberCaller;
                        if (fiberCaller != null && isAssociatedWaiting(fiberCaller))
                            {
                            return -1;
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
     * @return true iff there are any waiting frames associated with the specified fiber
     */
    private boolean isAssociatedWaiting(Fiber fiberCaller)
        {
        return getFirstAssociatedIndex(FiberStatus.Waiting, fiberCaller) != -1;
        }

    /**
     * Get the index of the first frame of the specified status associated with the specified context.
     *
     * NOTE: a waiting frame with a native stack frame is exempt from association rules since all
     *       the natural execution has completed.
     *
     * @param status       the status to check for
     * @param fiberCaller  the fiber to check association with (null for "any")
     *
     * @return the first matching index or -1
     */
    private int getFirstAssociatedIndex(FiberStatus status, Fiber fiberCaller)
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

                    if (fiberCaller == null || fiber.isAssociated(fiberCaller))
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

    private Frame[] ensureCapacity(int cCapacity)
        {
        Frame[] aFrame = m_aFrame;
        int     cOld   = aFrame.length;
        if (cCapacity > cOld)
            {
            int     cNew = Math.max(cCapacity, cOld + (cOld >> 2)); // 1.25
            Frame[] aNew = new Frame[cNew];

            assert m_ixHead == m_ixTail;
            if (m_ixHead == 0)
                {
                System.arraycopy(aFrame, 0, aNew, 0, cOld);
                }
            else
                {
                // copy and re-arrange
                int cHead = cOld - m_ixHead;
                System.arraycopy(aFrame, m_ixHead, aNew, 0,     cHead);
                System.arraycopy(aFrame, 0       , aNew, cHead, m_ixHead);
                m_ixHead = 0;
                m_ixTail = cOld;
                }
            m_aFrame = aFrame = aNew;
            }
        return aFrame;
        }

    // move all the not-empty spaces toward the head
    private void compact()
        {
        Frame[] aFrame  = m_aFrame;
        int     cFrames = aFrame.length;

        assert aFrame[m_ixHead] != null;

        int iFrom = (m_ixHead + 1) % cFrames;
        int iTo   = iFrom;

        for (int i = 1; i < cFrames; i++)
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
