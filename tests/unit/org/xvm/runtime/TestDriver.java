package org.xvm.runtime;


import org.xvm.api.Connector;

import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.CommandLine;


/**
 * The test driver.
 */
public class TestDriver
    {
    public static void main(String[] asArg) throws Exception
        {
        CommandLine cmd = new CommandLine(new String[0]);

        ModuleRepository repository = cmd.build();

        String sApp = asArg[0];
        if (sApp == null || sApp.trim().length() == 0)
            {
            sApp = "TestApp";
            }

        Connector connector = new Connector(repository);
        connector.loadModule(sApp);
        // connector.getRuntime().setResourceProvider()
        connector.start();
        connector.invoke0("run");
        connector.join();
        }
    }
