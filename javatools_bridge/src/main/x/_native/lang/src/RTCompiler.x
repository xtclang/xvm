import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.FileTemplate;

/**
 * This is the native runtime implementation of Compiler. It's constructed natively, so all the
 * fields are initially unassigned.
 */
service RTCompiler
        implements Compiler {

    ModuleRepository libRepo;
    Directory        outputDir;

    @Override
    void setLibraryRepository(ModuleRepository libRepo) {
        this.libRepo = libRepo;
    }

    @Override
    void setResultLocation(Directory outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    (Boolean success, String[] errors) compile((Directory|File)[] sources) {
        assert &outputDir.assigned as $"Output directory must be specified";

        ModuleRepository? repo = &libRepo.assigned ? libRepo : Null;

        (FileTemplate[] modules, String[] errors) = compileImpl(repo, sources);

        if (modules.empty) {
            return False, errors;
        }

        for (FileTemplate template : modules) {
            outputDir.fileFor(template.name).contents = template.contents;
        }
        return True, errors;
    }

    @Override
    String toString() {
        return "Compiler";
    }

    // ----- native methods ------------------------------------------------------------------------

    (FileTemplate[] modules, String[] errors)
            compileImpl(ModuleRepository? repo, (Directory|File)[] sources)
        {TODO("native");}
}