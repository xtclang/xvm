package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.03.27
 */
public class TestDriver
    {
    public static void main(String[] asArg) throws Exception
        {
        Container container = new Container();

        container.start(container.f_constantPoolAdapter.ensureModuleConstant("TestApp"));

        while (container.isRunning())
            {
            Thread.sleep(500);
            }
        }
    }
