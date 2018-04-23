package org.xvm.runtime;


import org.xvm.api.Connector;

import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.CommandLine;

/**
 * The connector test,
 *
 * TestConnector [module name] [module path]
 */
public class TestConnector
    {
    public static void main(String[] asArg) throws Exception
        {
        if (asArg.length < 1)
            {
            System.err.println("Application name is missing");
            return;
            }

        if (asArg.length < 2)
            {
            System.err.println("Module location is missing");
            return;
            }

        String sModule = asArg[0];
        String sFile = asArg[1];

        CommandLine cmd = new CommandLine(
            new String[] {"system", sFile});

        ModuleRepository repository = cmd.build();

        // +++ that is the actual use +++
        Connector connector = new Connector(repository);
        connector.loadModule(sModule);

        // configuration of the container happens here

        connector.start();

        connector.invoke0("run", Utils.OBJECTS_NONE);

        connector.join();
        }
    }

