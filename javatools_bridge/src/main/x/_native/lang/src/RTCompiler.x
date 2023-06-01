import ecstasy.lang.ErrorList;

import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.ModuleRepository;

/**
 * This is the native runtime implementation of Compiler.
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
    (Boolean success, String[] errors) compile((Directory|File)[] sources) {TODO("native");}

    @Override
    String toString() {
        return "Compiler";
    }
}