package org.xvm.runtime;


import org.xvm.runtime.ServiceContext.Reentrancy;


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
                {
                // in Exclusive mode only the front fiber is allowed to run
                if (frame.f_context.m_reentrancy == Reentrancy.Exclusive)
                    {
                    if (ix != getHeadIndex())
                        {
                        return -1;
                        }
                    }
                // fall through
                }
            case Yielded:
                return 1;

            case InitialNew:
                {
                // we can only allow it to proceed:
                //  a) in Exclusive mode - only if there are no other fibers in front of it;
                //  b) in Open or Prioritized mode - only if there are no other fibers
                //     associated with that same context in front of it
                if (frame.f_context.m_reentrancy == Reentrancy.Exclusive)
                    {
                    return ix == getHeadIndex() ? 0 : -1;
                    }

                Fiber fiberCaller = frame.f_fiber.f_fiberCaller;
                if (fiberCaller == null)
                    {
                    // independent fiber
                    return 0;
                    }

                ServiceContext ctxCaller = fiberCaller.f_context;
                Frame[]        aFrame    = m_aFrame;
                int            cFrames   = aFrame.length;
                for (int i = m_ixHead; i != ix; i = (i+1) % cFrames)
                    {
                    Frame frameOther = aFrame[i];
                    if (frameOther != null)
                        {
                        Fiber fiberOtherCaller = frameOther.f_fiber.f_fiberCaller;
                        if (fiberOtherCaller != null && fiberOtherCaller.isAssociated(ctxCaller))
                            {
                            return -1;
                            }
                        }
                    }
                return 0;
                }
            }
        }

    private int getHeadIndex()
        {
        Frame[] aFrame  = m_aFrame;
        int     cFrames = aFrame.length;
        int     ixHead  = m_ixHead;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (ixHead + i) % cFrames;
            if (aFrame[ix] != null)
                {
                return ix;
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
