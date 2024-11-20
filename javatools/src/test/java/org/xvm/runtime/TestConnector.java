package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xvm.api.Connector;

import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.tool.Compiler;
import org.xvm.tool.LauncherException;
import org.xvm.tool.ModuleInfo;


/**
 * The connector test.
 *
 * TestConnector [module-source-path]+
 */
public final class TestConnector
    {
        private TestConnector() {
        }

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

        Compiler compiler = new Compiler();
        try
            {
            compiler.run(listCompileArgs);
            }
        catch (LauncherException e)
            {
            System.exit(e.error ? -1 : 0);
            }

        ModuleInfo[]     targets   = compiler.getModuleInfos();
        ModuleRepository repoLibs  = compiler.getLibraryRepo();
        ModuleRepository repoBuild = compiler.getOutputRepo();
        assert targets.length == cModules;

        ModuleRepository repository = new LinkedRepository(true, repoBuild, repoLibs);
        Connector        connector  = new Connector(repository);

        for (ModuleInfo info : targets)
            {
            String name = info.getQualifiedModuleName();
            System.out.println("\n++++++ Loading module: " + name + " +++++++\n");

            // +++ that is the actual use +++
            connector.loadModule(name);

            // configuration of the container happens here

            connector.start(null);

            connector.invoke0("run", Utils.OBJECTS_NONE);

            connector.join();
            }
        }
    }