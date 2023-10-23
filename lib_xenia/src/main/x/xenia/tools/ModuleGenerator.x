import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

import ecstasy.text.Log;


/**
 * The ModuleGenerator for a hosted web module.
 */
class ModuleGenerator(ModuleTemplate webModule) {
    /**
     * The underlying WebModule template.
     */
    protected ModuleTemplate webModule;

    /**
     * Generic templates.
     */
    protected String moduleSourceTemplate = $./templates/_module.txt;

    /**
     * Generate (if necessary) all the necessary classes to use a web module.
     *
     * @param repository  the repository to load necessary modules from
     * @param buildDir    the directory to place all generated artifacts to
     * @param errors      the error log
     *
     * @return True iff the module template was successfully created
     * @return the resolved generated module (optional)
     */
    conditional ModuleTemplate ensureWebModule(
            ModuleRepository repository, Directory buildDir, Log errors) {

        String appName   = webModule.qualifiedName;
        String qualifier = "";
        if (Int dot := appName.indexOf('.')) {
            qualifier = appName[dot ..< appName.size];
            appName   = appName[0 ..< dot];
        }

        String hostName = $"{appName}_web{qualifier}";

        if (ModuleTemplate hostModule := repository.getModule(hostName)) {
            // try to see if the host module is newer than the original module;
            // if anything goes wrong - follow a regular path
            try {
                Time? dbStamp    = webModule.parent.created;
                Time? hostStamp  = hostModule.parent.created;
                if (dbStamp != Null && hostStamp != Null && hostStamp > dbStamp) {
                    errors.add($|Info: Host module "{hostName}" for "{webModule.qualifiedName}" \
                                |is up to date
                              );
                    return True, repository.getResolvedModule(hostName);
                }
            } catch (Exception ignore) {}
        }

        File sourceFile = buildDir.fileFor($"{appName}_web.x");

        if (createModule(sourceFile, appName, qualifier, errors) &&
            compileModule(repository, sourceFile, buildDir, errors)) {
            errors.add($|Info: Created a host module "{hostName}" for "{webModule.qualifiedName}"
                      );
            return True, repository.getResolvedModule(hostName);
        }
        return False;
    }

    /**
     * Create module source file.
     */
    Boolean createModule(File sourceFile, String appName, String qualifier, Log errors) {
        String moduleSource = moduleSourceTemplate.replace("%appName%"  , appName)
                                                  .replace("%qualifier%", qualifier);
        sourceFile.create();
        sourceFile.contents = moduleSource.utf8();
        return True;
    }

    /**
     * Compile the specified source file.
     */
    Boolean compileModule(ModuleRepository repository, File sourceFile, Directory buildDir, Log errors) {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setLibraryRepository(repository);
        compiler.setResultLocation(buildDir);

        (Boolean success, String[] compilationErrors) = compiler.compile([sourceFile]);

        if (compilationErrors.size > 0) {
            errors.addAll(compilationErrors);
        }
        return success;
    }
}