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
        Runtime runtime = new Runtime();

        runtime.createContainer("TestApp");

        while (runtime.isIdle())
            {
            Thread.sleep(500);
            }
        }
    }
