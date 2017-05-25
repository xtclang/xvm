package org.xvm.proto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO:
 *
 * @author gg 2017.02.15
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

    public Container createContainer(String sName)
        {
        Container container = new Container(this, sName);

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
