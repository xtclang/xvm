package org.xvm.runtime;


import org.xvm.api.Connector;

import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.MethodConstant;

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

        int cModules = asArg.length / 2;
        String[] asModule = new String[cModules];
        String[] asFile   = new String[1 + cModules];

        asFile[0] = "system";

        for (int i = 0; i < cModules; i++)
            {
            asModule[i] = asArg[2*i];
            asFile[i+1] = asArg[2*i + 1];
            }

        CommandLine cmd = new CommandLine(asFile);

        ModuleRepository repository = cmd.build();

        if (System.getProperty("CP") != null)
            {
            out("Code dump:");
            dump(repository.loadModule(asModule[0]));
            }

        for (int i = 0; i < cModules; i++)
            {
            System.out.println("\n++++++ Loading module: " + asModule[i] + " +++++++\n");

            // +++ that is the actual use +++
            Connector connector = new Connector(repository);
            connector.loadModule(asModule[i]);

            // configuration of the container happens here

            connector.start();

            connector.invoke0("run", Utils.OBJECTS_NONE);

            connector.join();
            }
        }

    public static void dump(Component component)
        {
        if (component instanceof MethodStructure)
            {
            MethodStructure method = (MethodStructure) component;
            MethodConstant  id     = method.getIdentityConstant();
            if (method.hasCode() && method.ensureCode() != null && !method.isNative())
                {
                out("** code for " + id);
                out(method.ensureCode().toString());
                out("");
                }
            else
                {
                out("** no code for " + id);
                out("");
                }
            }

        if (component != null)
            {
            for (Component child : component.children())
                {
                if (child != null)
                    {
                    dump(child);
                    }
                }
            }
        }

    public static void out(String s)
        {
        System.out.println(s);
        }
    }

