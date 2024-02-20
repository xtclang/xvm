import ecstasy.fs.DirectoryFileStore.FileNodeWrapper;
import ecstasy.fs.FileNode;

import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.FileTemplate;

import fs.OSFile;
import fs.OSFileNode;
import fs.OSStorage;

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

        conditional OSFileNode unwrap(FileNode node) {
            if (OSFileNode osNode := &node.revealAs(OSFileNode)) {
                return True, osNode;
            }
            if (FileNodeWrapper wrapper := &node.revealAs(FileNodeWrapper)) {
                return unwrap(wrapper.origNode);
            }
            return False;
        }

        // make sure all the sources we pass to the "compileImpl" method are OSFileNode objects,
        // so the native implementation can trivially retrieve the corresponding paths
        OSFileNode[] osSources = new OSFileNode[];
        Boolean[]    temporary = new Boolean[];
        for ((Directory|File) source : sources) {
            if (OSFileNode osNode := unwrap(source)) {
                osSources += osNode;
                temporary += False;
            } else if (source.is(File)) {
                // unknown implementation - copy the 'source' file to 'tmpDir'
                Directory tmpDir = OSStorage.instance().tmpDir;
                String    name   = source.name;
                for (Int attempt = 0; True; attempt++) {
                    File file = tmpDir.fileFor(attempt == 0 ? name : $"{name}_{attempt}");
                    if (file.create()) {
                        assert OSFile osFile := &file.revealAs(OSFile);
                        osFile.contents = source.contents;
                        osSources += osFile;
                        temporary += True;
                        break;
                    }
                }
            } else {
                TODO copy the 'source' directory to 'tmpDir'
            }
        }

        (FileTemplate[] modules, String[] errors) = compileImpl(repo, osSources);

        for (Int i : 0 ..< osSources.size) {
            if (temporary[i]) {
                osSources[i].delete();
            }
        }

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
            compileImpl(ModuleRepository? repo, (OSFileNode)[] sources)
        {TODO("native");}
}