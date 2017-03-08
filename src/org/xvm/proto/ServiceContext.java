package org.xvm.proto;

import java.util.Queue;
import java.util.Stack;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class ServiceContext
    {
    Container m_container;
    Thread m_thread;
    Queue m_queueInvocations;
    ObjectHeap m_heap;
    Stack<Frame> m_frames;
    TypeSet m_types;

    public ObjectHandle ensureConstHandle(int nConstType, int nConstValue)
        {
        return m_heap.ensureConstHandle(nConstType, nConstValue);
        }
    }
