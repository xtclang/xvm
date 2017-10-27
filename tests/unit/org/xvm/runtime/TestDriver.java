package org.xvm.runtime;


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

        CommandLine cmd = new CommandLine(new String[0]);

        ModuleRepository repository = cmd.build();

        runtime.createContainer(asArg[0], repository);

        while (runtime.isIdle())
            {
            Thread.sleep(500);
            }
        }
    }
