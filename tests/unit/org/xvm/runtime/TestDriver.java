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

        String sApp = asArg.length > 0 ? asArg[0] : null;
        if (sApp == null || sApp.trim().length() == 0)
            {
            sApp = "TestApp";
            }

        Container container = new Container(runtime, sApp, repository);

        runtime.start();
        container.start();

        container.invoke0("run");

        // extremely naive; replace
        do {
            Thread.sleep(500);
            }
        while (!runtime.isIdle() || !container.isIdle());
        }
    }
