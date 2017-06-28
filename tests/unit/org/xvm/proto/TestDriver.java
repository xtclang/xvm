package org.xvm.proto;

import org.xvm.asm.ModuleRepository;

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

        ModuleRepository repository = null;

        runtime.createContainer("TestApp", repository);

        while (runtime.isIdle())
            {
            Thread.sleep(500);
            }
        }
    }
