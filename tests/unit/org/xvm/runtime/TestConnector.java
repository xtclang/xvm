package org.xvm.runtime;


import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.CommandLine;

/**
 * The connector test,
 */
public class TestConnector
    {
    public static void main(String[] asArg) throws Exception
        {
        if (asArg.length == 0)
            {
            System.err.println("Application name is missing");
            return;
            }

        String sApp = asArg[0];

        CommandLine cmd = new CommandLine(
            new String[] {ConstantPool.ECSTASY_MODULE, sApp});

        ModuleRepository repository = cmd.build();

        Connector connector = new Connector(repository);
        connector.loadModule(sApp);
        connector.start();

        connector.invoke0("run", Utils.OBJECTS_NONE);

        connector.join();
        }
    }

