package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.xvm.api.Connector;

import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.runtime.Utils;


/**
 * The "execute" command:
 *
 *  java org.xvm.tool.Runner xtc_path
 */
public class Runner
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

            connector.invoke0("run", Utils.OBJECTS_NONE);

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

