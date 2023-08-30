package org.xvm.runtime;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xvm.api.Connector;

import org.xvm.asm.DirRepository;
import org.xvm.asm.FileRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;

import org.xvm.tool.Compiler;
import org.xvm.tool.Disassembler;

import org.xvm.util.Handy;

/**
 * The connector test.
 *
 * TestConnector [module-source-path]+
 */
public class TestConnector
    {
    public static void main(String[] asArg) throws Exception
        {
        if (asArg.length < 1)
            {
            System.err.println("Module location is missing");
            return;
            }

        // let the $prj be the root of the "xvm" project (e.g. ~/Development/xvm);
        // the working directory is assumed to be:
        //      $prj/manualTests
        // the build directory would be:
        //      $prj/manualTests/build
        // and the system libraries then could be found at :
        //      $prj/xdk/build/xdk/lib,
        //      $prj/xdk/build/xdk/javatools/javatools_bridge.xtc

        int cModules = asArg.length;

        List<String> listCompileArgs = new ArrayList<>(8 + cModules);
        listCompileArgs.add("-o");
        listCompileArgs.add("./build");
        listCompileArgs.add("-L");
        listCompileArgs.add("../xdk/build/xdk/lib");
        listCompileArgs.add("-L");
        listCompileArgs.add("../xdk/build/xdk/javatools/javatools_turtle.xtc");
        listCompileArgs.add("-L");
        listCompileArgs.add("../xdk/build/xdk/javatools/javatools_bridge.xtc");
        listCompileArgs.add("-L");
        listCompileArgs.add("./build");
        listCompileArgs.addAll(Arrays.asList(asArg));

        Compiler compiler = new Compiler(listCompileArgs.toArray(Handy.NO_ARGS));
        compiler.run();

        List<File> listSrcFile   = compiler.options().getInputLocations();
        String[]   asFileNames   = new String[cModules];
        String[]   asModuleNames = new String[cModules];
        for (int i = 0; i < cModules; i++)
            {
            File   fileOrig   = listSrcFile.get(i);
            File   fileModule = compiler.findModule(fileOrig);
            if (fileModule == null)
                {
                System.err.println("Failed to find module for " + fileOrig);
                return;
                }
            String sName = compiler.getModuleName(fileModule);
            int    ofDot = sName.indexOf('.');

            asFileNames[i]   = ofDot < 0 ? sName : sName.substring(0, ofDot);
            asModuleNames[i] = sName;
            }

        List<File> listSysPaths = compiler.options().getModulePath();
        int        cLibs        = listSysPaths.size();

        ModuleRepository[] aRepo = new ModuleRepository[1 + cLibs + cModules];
        aRepo[0] = new BuildRepository();

        for (int i = 0; i < cLibs; ++i)
            {
            File file = listSysPaths.get(i);
            aRepo[1 + i] = file.isDirectory()
                ? new DirRepository(file, true)
                : new FileRepository(file, true);
            }

        File dirBuild = new File("./build");
        assert dirBuild.exists() && dirBuild.isDirectory();

        for (int i = 0; i < cModules; ++i)
            {
            File file = new File(dirBuild, asFileNames[i] + ".xtc");

            try
                {
                aRepo[1 + cLibs + i] = file.isDirectory()
                    ? new DirRepository(file, true)
                    : new FileRepository(file, true);
                }
            catch (RuntimeException | Error e)
                {
                System.err.println("Failed to load " + file);
                return;
                }
            }

        ModuleRepository repository = new LinkedRepository(true, aRepo);
        Connector        connector  = new Connector(repository);

        for (String sModule : asModuleNames)
            {
            System.out.println("\n++++++ Loading module: " + sModule + " +++++++\n");

            // +++ that is the actual use +++
            connector.loadModule(sModule);

            if (System.getProperties().containsKey("DEBUG"))
                {
                ModuleStructure module = (ModuleStructure)
                        connector.getContainer().getModule().getComponent();
                if (module != null)
                    {
                    module.visitChildren(Disassembler::dump, false, true);
                    }
                }

            // configuration of the container happens here

            connector.start();

            connector.invoke0("run", Utils.OBJECTS_NONE);

            connector.join();
            }
        }
    }