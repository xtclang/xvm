package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * The "execute" command:
 *
 *  java org.xvm.tool.Runner xtc_path [method_name [args]]
 *
 * where the default method is "run" with no arguments.
 */
public class Runner
    {
    public static void main(String[] asArg)
        {
        int cArgs = asArg.length;
        if (cArgs == 0)
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

        File dirCur     = new File(System.getProperty("user.dir"));
        File fileCore   = new File(dirCur, "Ecstasy.xtc");
        File fileNative = new File(dirCur, "_native.xtc");
        if (!fileCore.exists() || !fileNative.exists())
            {
            System.err.println("Core modules are missing");
            return;
            }

        BuildRepository repository = new BuildRepository();
        try
            {
            repository.storeModule(new FileStructure(fileCore)  .getModule());
            repository.storeModule(new FileStructure(fileNative).getModule());

            FileStructure   structApp = new FileStructure(new FileInputStream(fileXtc));
            ModuleStructure moduleApp = structApp.getModule();
            repository.storeModule(moduleApp);

            Connector connector = new Connector(repository);
            connector.loadModule(moduleApp.getName());

            connector.start();

            String sMethod = "run";
            if (cArgs > 1)
                {
                sMethod = asArg[1];
                }

            ObjectHandle[] ahArg = Utils.OBJECTS_NONE;
            if (cArgs > 2)
                {
                try (var x = ConstantPool.withPool(connector.getConstantPool()))
                    {
                    StringHandle[] ahName = new StringHandle[cArgs - 2];
                    for (int i = 2; i < cArgs; i++)
                        {
                        ahName[i-2] = xString.makeHandle(asArg[i]);
                        }
                    ahArg = new ObjectHandle[]{xArray.makeStringArrayHandle(ahName)};
                    }
                }
            connector.invoke0(sMethod, ahArg);

            connector.join();
            }
        catch (IOException e)
            {
            System.err.println("Invalid .xtc format: " + e.getMessage());
            }
        catch (InterruptedException e)
            {
            }
        }
    }

