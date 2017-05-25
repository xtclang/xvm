package org.xvm.proto;

/**
 * The test driver.
 *
 * @author gg 2017.03.27
 */
public class TestDriver
    {
    public static void main(String[] asArg) throws Exception
        {
        DaemonPool daemons = new DaemonPool("Worker");
        daemons.start();

        Container container = new Container(daemons);

        container.start(container.f_constantPoolAdapter.ensureModuleConstant("TestApp"));

        while (container.isRunning())
            {
            Thread.sleep(500);
            }
        }
    }
