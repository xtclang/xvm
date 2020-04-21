package org.xvm.runtime;


import org.xvm.runtime.Fiber.FiberStatus;
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

    // cached indexes of the top fiber for a given priority
    // [2] a waiting fiber that is marked as "ready"
    // [1] initial associated or yielded
    // [0] initial new
    private int[] m_aixPriority = new int[] {-1, -1, -1};

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

        checkPriority(iInsert);
        }

    public boolean isEmpty()
        {
        return m_cSize == 0;
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

        for (int i = 2; i >= nPriority; i--)
            {
            int ix = m_aixPriority[i];
            if (ix >= 0)
                {
                m_aixPriority[i] = -1;
                return remove(ix);
                }
            }

        int cFrames = m_aFrame.length;
        int ixHead  = m_ixHead;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (i + ixHead) % cFrames;

            int iPriority = checkPriority(ix);
            if (iPriority >= nPriority)
                {
                m_aixPriority[iPriority] = -1;
                return remove(ix);
                }
            }
        return null;
        }

    // return the priority of the fiber at the specified index and update the cache indexes
    private int checkPriority(int ix)
        {
        Frame frame = m_aFrame[ix];
        if (frame == null)
            {
            return -2; // allow to differentiate empty from not-ready
            }

        switch (frame.f_fiber.getStatus())
            {
            default:
                throw new IllegalStateException();

            case Waiting:
                if (frame.f_fiber.isReady())
                    {
                    if (m_aixPriority[2] < 0)
                        {
                        m_aixPriority[2] = ix;
                        }
                    return 2;
                    }
                else
                    {
                    return -1;
                    }

            case InitialAssociated:
                {
                // in Exclusive mode only the front fiber is allowed to run
                ServiceContext context = frame.f_fiber.f_context;
                if (context.m_reentrancy == Reentrancy.Exclusive)
                    {
                    if (ix != getHeadIndex())
                        {
                        return -1;
                        }
                    }
                // fall through
                }
            case Yielded:
                if (m_aixPriority[1] < 0)
                    {
                    m_aixPriority[1] = ix;
                    }
                return 1;

            case InitialNew:
                {
                // we can only allow it to proceed:
                //  a) in Exclusive mode - only if there are no other fibers in front
                //  b) in Open or Prioritized mode - only if there are no other InitialNew fibers
                //     associated with that same context in front of it
                ServiceContext context = frame.f_fiber.f_context;
                if (context.m_reentrancy == Reentrancy.Exclusive)
                    {
                    return ix == getHeadIndex() ? 0 : -1;
                    }

                Frame[] aFrame  = m_aFrame;
                int     cFrames = aFrame.length;
                int     ixHead  = m_ixHead;
                for (int i = ixHead; i != ix; i = (i+1) % cFrames)
                    {
                    Frame frameOther = aFrame[i];
                    if (frameOther != null)
                        {
                        Fiber fiberOther = frameOther.f_fiber;
                        if (fiberOther.getStatus() == FiberStatus.InitialNew &&
                            fiberOther.isAssociated(context))
                            {
                            return -1;
                            }
                        }
                    }
                if (m_aixPriority[0] < 0)
                    {
                    m_aixPriority[0] = ix;
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

        // reset the cached indexes
        m_aixPriority[0] = m_aixPriority[1] = m_aixPriority[2] = -1;
        }

    public String toString()
        {
        return "size=" + m_cSize;
        }
    }
