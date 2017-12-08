package org.xvm.runtime;


import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.CommandLine;


/**
 * The test driver.
 */
public class TestDriver
    {
    public static void main(String[] asArg) throws Exception
        {
        Runtime runtime = new Runtime();

        CommandLine cmd = new CommandLine(new String[0]);

        ModuleRepository repository = cmd.build();

        String sApp = asArg[0];
        if (sApp == null || sApp.trim().length() == 0)
            {
            sApp = "TestApp";
            }

        runtime.createContainer(sApp, repository);

        while (runtime.isIdle())
            {
            Thread.sleep(500);
            }
        }
    }
