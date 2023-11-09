package org.xvm.runtime;


import org.xvm.runtime.Fiber.FiberStatus;


/**
 * {@link FiberQueue} represents a queue-like data structure holding all pending Fibers and
 * facilitating a quick selection algorithm for the scheduler.
 */
public class FiberQueue
    {
    private final ServiceContext f_context;

    // a circular array
    private Frame[] m_aFrame = new Frame[16];

    private int m_ixHead = -1; // head
    private int m_ixTail = 0;  // past the tail - insertion point
    private int m_cSize  = 0;

    public FiberQueue(ServiceContext ctx)
        {
        f_context = ctx;
        }

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
     * Retrieve the first frame that is ready.
     */
    public Frame getReady()
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
     * Retrieve any available frame (ready or not).
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
     * Report on the status of the fiber queue. Temporary: for debugging only.
     */
    public String reportStatus()
        {
        if (m_cSize == 0)
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        int cFrames = m_aFrame.length;
        int ixHead  = m_ixHead;
        for (int i = 0; i < cFrames; i++)
            {
            int ix = (i + ixHead) % cFrames;

            int iPriority = checkPriority(ix);
            if (iPriority == -1)
                {
                Frame frame = m_aFrame[ix];
                if (sb.isEmpty())
                    {
                    sb.append(frame.f_context);
                    }
                sb.append("\nframe=")
                  .append(frame);

                Fiber fiber = frame.f_fiber;
                switch (fiber.getStatus())
                    {
                    case Waiting:
                        sb.append(" waiting");
                        break;

                    case Initial:
                        sb.append(" new");
                        break;
                    }
                }
            }
        return sb.toString();
        }

    /**
     * Calculate the priority of the fiber at the specified index.
     * The return values are:
     * <ul>
     *   <li/>[2]  running
     *   <li/>[1]  a waiting fiber that is marked as "ready"
     *   <li/>[0]  initial new
     *   <li/>[-1] not ready
     *   <li/>[-2] blocked
     *   <li/>[-3] empty
     * </ul>
     */
    private int checkPriority(int ix)
        {
        Frame frame = m_aFrame[ix];
        if (frame == null)
            {
            return -3;
            }

        Fiber fiber = frame.f_fiber;
        switch (fiber.getStatus())
            {
            case Running:
                return 2;

            case Waiting:
                if (!fiber.isReady())
                    {
                    return -1;
                    }

                // native waiting stack indicates a terminating fiber waiting on a future result
                return frame.isNativeStack()
                    || fiber == f_context.getSynchronizationOwner()
                    || canResume(fiber)
                        ? 1 : -2;

            case Initial:
                return canResume(fiber) ? 0 : -2;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff a frame (which is not the synchronization owner) can be started or resumed
     */
    private boolean canResume(Fiber fiber)
        {
        switch (f_context.getSynchronicity())
            {
            case Critical:
                return false;

            case Synchronized:
                Fiber fiberCaller = fiber.f_fiberCaller;
                return fiberCaller != null &&
                        fiberCaller.isContinuationOf(f_context.getSynchronizationOwner());

            case Concurrent:
                return !isAnyNonConcurrentWaiting(fiber);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Check if there is any "waiting" frame that is not concurrent safe with regard to the
     * specified fiber.
     *
     * Note1: this method should be called only if the service is Concurrent (i.e. frame-dependent)
     * Note2: if this method returns true, it also sets the "blocker" on the evaluating fiber
     *
     * @param fiberCandidate  the fiber that the service is evaluating for execution (either Initial
     *                        or Waiting)
     *
     * @return true iff there are any non-concurrent waiting frames
     */
    private boolean isAnyNonConcurrentWaiting(Fiber fiberCandidate)
        {
        Frame[] aFrame      = m_aFrame;
        int     cFrames     = aFrame.length;
        Fiber   fiberCaller = fiberCandidate.f_fiberCaller;

        for (int i = 0; i < cFrames; i++)
            {
            Frame frame = aFrame[i];
            if (frame == null)
                {
                continue;
                }

            Fiber fiber = frame.f_fiber;
            if (fiber != fiberCandidate)
                {
                if (fiber.getStatus() == FiberStatus.Waiting)
                    {
                    if (frame.isSafeStack() ||
                        fiberCaller != null && fiberCaller.isContinuationOf(fiber))
                        {
                        continue;
                        }
                    fiberCandidate.setBlocker(frame);
                    return true;
                    }
                }
            }
        fiberCandidate.setBlocker(null);
        return false;
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
