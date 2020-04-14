package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.xvm.asm.Component;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;


/**
 * The "disassemble" command:
 *
 *  java org.xvm.tool.Disassembler xtc_path
 */
public class Disassembler
    {
    public static void main(String[] asArg)
        {
        if (asArg.length < 1)
            {
            System.err.println("File name is missing");
            return;
            }

        File fileXtc = new File(asArg[0]);
        if (!fileXtc.exists() || !fileXtc.isFile())
            {
            System.err.println("The .xtc file is missing: " + asArg[0]);
            return;
            }

        try
            {
            FileStructure struct = new FileStructure(new FileInputStream(fileXtc));

            struct.visitChildren(Disassembler::dump, false, true);
            }
        catch (IOException e)
            {
            System.err.println("Invalid .xtc format: " + e.getMessage());
            }
        }

    public static void dump(Component component)
        {
        if (component instanceof MethodStructure)
            {
            MethodStructure method = (MethodStructure) component;
            MethodConstant id     = method.getIdentityConstant();

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
        }

    public static void out(String s)
        {
        System.out.println(s);
        }
    }

