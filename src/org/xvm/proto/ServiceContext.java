package org.xvm.proto;

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
    public final ObjectHeap f_heapGlobal;
    public final ConstantPoolAdapter f_constantPool;

    public ServiceDaemon m_daemon;
    public Frame m_frameCurrent;

    ServiceContext(Container container)
        {
        f_container = container;
        f_heapGlobal = container.f_heapGlobal;
        f_types = container.f_types;
        f_constantPool = container.f_constantPoolAdapter;
        }

    public Frame createFrame(Frame framePrev, InvocationTemplate template,
                             ObjectHandle hTarget, ObjectHandle[] ahVar)
        {
        return new Frame(this, framePrev, template, hTarget, ahVar);
        }

    public void startServiceDaemon(String sName)
        {
        ServiceDaemon daemon = m_daemon = new ServiceDaemon(sName, this);
        daemon.start();
        }

    public static ServiceContext getCurrentContext()
        {
        return ServiceDaemon.getCurrentContext();
        }
    }
