package org.xvm.tool;


import java.util.Arrays;

import org.xvm.compiler.CommandLine;


/**
 * The "compile" commands:
 *
 *  java org.xvm.tool.Compiler system
 *     or
 *  java org.xvm.tool.Compiler module_path, module_path, ...
 */
public class Compiler
    {
    public static void main(String[] asArg)
        {
        if (asArg.length < 1)
            {
            System.err.println("Module name is missing");
            return;
            }

        if (asArg[0].equals("system"))
            {
            System.out.println("Compiling core module...");
            new CommandLine(null).build();
            }
        else
            {
            System.out.println("Compiling modules: " + Arrays.toString(asArg));
            new CommandLine(asArg).build();
            }
        }
    }

