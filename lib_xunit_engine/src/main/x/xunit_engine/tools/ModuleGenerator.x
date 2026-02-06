import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ModuleTemplate;

import ecstasy.text.Log;

/**
 * The xunit-based ModuleGenerator.
 *
 * @param moduleName  the underlying (hosted) fully qualified module name
 * @param version     (optional) the version of the underlying (hosted) module
 */
class ModuleGenerator(String moduleName, Version? version = Null) {
    /**
     * The implementation name.
     */
    protected String implName = "xunit";

    /**
     * Generic templates.
     */
    protected String moduleSourceTemplate = $./templates/_module.txt;

    /**
     * Generate (if necessary) all the necessary classes to use a DB module.
     *
     * @param repository  the repository to load necessary modules from
     * @param buildDir    the directory to place all generated artifacts to
     * @param errors      the error log
     *
     * @return True iff the module template was successfully created
     * @return the generated module (optional)
     */
    conditional ModuleTemplate ensureModule(ModuleRepository repository, Directory buildDir, Log errors) {
        ModuleTemplate testModule = repository.getResolvedModule(moduleName, version);
        String         appName    = moduleName;
        String         qualifier  = "";

        if (Int dot := appName.indexOf('.')) {
            qualifier = appName[dot ..< appName.size];
            appName   = appName[0 ..< dot];
        }

        String hostName = $"{appName}_{implName}{qualifier}";

        if (ModuleTemplate hostModule := repository.getModule(hostName)) {
            // try to see if the host module is newer than the original module;
            // if anything goes wrong - follow a regular path
            try {
                Time? dbStamp    = testModule.parent.created;
                Time? hostStamp  = hostModule.parent.created;
                if (dbStamp != Null && hostStamp != Null && hostStamp > dbStamp) {
                    errors.add($"Info: Host module '{hostName}' for '{moduleName}' is up to date");
                    return True, hostModule;
                }
            } catch (Exception ignore) {}
        }

        String moduleImports = collectModuleImports(testModule);
        File   sourceFile    = buildDir.fileFor($"{appName}_{implName}.x");
        createModule(sourceFile, appName, qualifier, moduleImports, testModule);

        if (compileModule(repository, sourceFile, buildDir, errors)) {
            errors.add($"Info: Created a host module '{hostName}' for '{moduleName}'");
            // clean up the temporary test module source file
            sourceFile.delete();
            return repository.getModule(hostName);
        }
        return False;
    }

    /**
     * Create module source file.
     *
     * @param sourceFile     the file to write the generated module source code to
     * @param appName        the name of the module to be tested
     * @param qualifier      the qualifier for the name of the module to be tested
     * @param moduleImports  a String containing the module imports to be injected into the
     *                       generated test module
     * @param moduleTemplate the module template for the module to be tested
     *
     * @return True iff the source file has been successfully created
     */
    void createModule(File           sourceFile,
                      String         appName,
                      String         qualifier,
                      String         moduleImports,
                      ModuleTemplate moduleTemplate) {

        String versionString = version == Null ? "" : $" v:{version}";
        String moduleSource  = moduleSourceTemplate
                                .replace("%appName%"      , appName)
                                .replace("%qualifier%"    , qualifier)
                                .replace("%version%"      , versionString)
                                ;
        sourceFile.create();
        sourceFile.contents = moduleSource.utf8();
    }

    /**
     * Compile the specified source file.
     *
     * @param repository  the module repository to load necessary modules from
     * @param sourceFile  the source file to compile
     * @param buildDir    the directory to write the compiled module into
     * @param errors      the error log
     *
     * @return True iff the source file has been successfully compiled
     */
    Boolean compileModule(ModuleRepository repository, File sourceFile, Directory buildDir, Log errors) {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setLibraryRepository(repository);
        compiler.setResultLocation(buildDir);

        (Boolean success, String[] compilationErrors) = compiler.compile([sourceFile]);

        if (compilationErrors.size > 0) {
            errors.add($|Error: Failed to compile an auto-generated class "{sourceFile}"
                      );
            errors.addAll(compilationErrors);
        }
        return success;
    }

    /**
     * Collect the module imports using the direct and transitive dependencies of the specified
     * module.
     *
     * @param testModule  the module to collect the imports from
     *
     * @return a String specifying all the module imports that should be injected into the generated
     *         test module
     */
    String collectModuleImports(ModuleTemplate testModule) {
        StringBuffer                buf   = new StringBuffer();
        Map<String, ModuleTemplate> deps  = testModule.modulesByPath;
        Set<String>                 names = new HashSet();

        for (ModuleTemplate mt : deps.values) {
            if (mt.qualifiedName == TypeSystem.MackKernel) {
                continue;
            }
            names.add(mt.qualifiedName);
        }

        Int id = 0;
        for (String qualifiedName : names) {
            buf.append("    package ").append("xunit_").append(id++)
               .append(" import ").append(qualifiedName)
               .append("  inject (ecstasy.reflect.Injector _) using SimpleInjector;\n");
        }
        return buf.toString();
    }
}
