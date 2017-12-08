package org.xvm.runtime;


import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ModuleRepository;


/**
 * TODO:
 */
public class Runtime
    {
    final public DaemonPool f_daemons;

    final protected Map<String, Container> f_mapContainers = new ConcurrentHashMap<>();

    public Runtime()
        {
        f_daemons = new DaemonPool("Worker");
        f_daemons.start();
        }

    public Container createContainer(String sName, ModuleRepository repository)
        {
        Container container = new Container(this, sName, repository);

        container.start();

        f_mapContainers.put(sName, container);

        return container;
        }

    public boolean isIdle()
        {
        // TODO: very naive; replace
        if (f_daemons.m_fWaiting)
            {
            for (Container container : f_mapContainers.values())
                {
                if (!container.isIdle())
                    {
                    return false;
                    }
                }
            }

        return true;
        }
    }
