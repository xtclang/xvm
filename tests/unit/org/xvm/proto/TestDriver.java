package org.xvm.proto;

import org.xvm.asm.ModuleRepository;
import org.xvm.compiler.CommandLine;

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

        CommandLine cmd = new CommandLine(asArg);

        ModuleRepository repository = cmd.build();

        runtime.createContainer("TestApp", repository);

        while (runtime.isIdle())
            {
            Thread.sleep(500);
            }
        }
    }
