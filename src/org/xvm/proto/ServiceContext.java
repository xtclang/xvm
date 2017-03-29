package org.xvm.proto;

import java.util.Queue;
import java.util.Stack;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

/**
 * The service context.
 *
 * @author gg 2017.02.15
 */
public class ServiceContext
    {
    public final Container f_container;
    public final TypeSet f_types;
    public final ObjectHeap f_heap;
    public final ConstantPoolAdapter f_constantPool;

    Thread m_thread;
    Queue m_queueInvocations;
    Stack<Frame> m_frames;

    public ServiceContext(Container container)
        {
        f_container = container;
        f_heap = container.f_heap;
        f_types = container.f_types;
        f_constantPool = container.f_constantPoolAdapter;
        }

    public Frame createFrame(ObjectHandle hTarget, Frame framePrev, InvocationTemplate template, ObjectHandle[] ahVars)
        {
        return new Frame(this, framePrev, hTarget, template, ahVars);
        }

    }
