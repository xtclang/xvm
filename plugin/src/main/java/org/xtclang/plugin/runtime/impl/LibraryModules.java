package org.xtclang.plugin.runtime.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.DirRepository;
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;
import org.xvm.compiler.BuildRepository;

/**
 * The library modules that the in-process compiles of one build share. A compile still links private copies of the
 * modules that it uses, which the read-through of its library repository makes, as on the command line. But a module
 * file that an earlier compile of the build loaded comes from memory instead of being read and deserialized again, as
 * long as its content is the same. So a library that every other one imports, such as ecstasy, is loaded once per
 * build instead of once per compile.
 *
 * <p>The compiles that share these modules must run one at a time, as {@link IsolatedDirectExecutor} runs them.
 */
final class LibraryModules {
    private final ModuleFiles<FileStructure> files =
        new ModuleFiles<>(FileStructure::new, structure -> structure.getModule().isModified());

    /**
     * Returns the library repository of one compile, as {@code Launcher.configureLibraryRepo} makes it: the compile's
     * own build repository, which gets the copies that the compile may change, then a repository for each entry of the
     * module path.
     *
     * @param path the module path
     * @return the library repository
     */
    ModuleRepository libraryRepository(final List<File> path) {
        if (path == null || path.isEmpty()) {
            return new BuildRepository();
        }
        final List<ModuleRepository> repositories = new ArrayList<>(path.size() + 1);
        repositories.add(new BuildRepository());
        for (final var entry : path) {
            repositories.add(repositoryOf(entry));
        }
        return new LinkedRepository(true, repositories.toArray(ModuleRepository[]::new));
    }

    /**
     * @return how many times a module file was read
     */
    int fileReads() {
        return files.fileReads();
    }

    /**
     * @return how many times a module file that an earlier compile read was used again
     */
    int reuses() {
        return files.reuses();
    }

    /**
     * Returns the repository of one module path entry: a directory, whose module files are those that
     * {@link DirRepository} takes, or a module file. Each file must hold one module. Anything else, or a file that
     * cannot be read, gets the repository that the command line would use, which reports it as it always did.
     */
    private ModuleRepository repositoryOf(final File entry) {
        final var moduleFiles = entry.isDirectory()
            ? entry.listFiles(DirRepository.ModulesOnly)
            : entry.isFile() ? new File[] {entry} : null;
        if (moduleFiles == null) {
            return repository(entry);
        }
        // As DirRepository does: a later file with the same module name wins
        final Map<String, ModuleStructure> modules = new HashMap<>();
        for (final var file : moduleFiles) {
            final FileStructure structure;
            try {
                structure = files.get(file);
            } catch (final IOException | RuntimeException e) {
                return repository(entry);
            }
            if (structure.children().stream().filter(module -> !module.isFingerprint()).count() != 1) {
                return repository(entry);
            }
            final var module = structure.getModule();
            modules.put(module.getIdentityConstant().getName(), module);
        }
        return new SharedModules(entry, modules);
    }

    /**
     * The modules of one module path entry that the compiles of a build share. The library repository of a compile
     * copies a module before the compile links it, so none of them changes. A load by version, which no XDK library
     * uses, goes to the repository that the command line would use for the entry, as it registers the module's
     * constants into the compiled module's pool.
     */
    private static final class SharedModules implements ModuleRepository {
        private final File entry;
        private final Map<String, ModuleStructure> modules;
        private ModuleRepository standard;

        private SharedModules(final File entry, final Map<String, ModuleStructure> modules) {
            this.entry = entry;
            this.modules = modules;
        }

        @Override
        public Set<String> getModuleNames() {
            return modules.keySet();
        }

        @Override
        public ModuleStructure loadModule(final String name) {
            return modules.get(name);
        }

        @Override
        public ModuleStructure loadModule(final String name, final Version version, final boolean exact) {
            if (standard == null) {
                standard = repository(entry);
            }
            return standard.loadModule(name, version, exact);
        }

        @Override
        public void storeModule(final ModuleStructure module) throws IOException {
            throw new IOException("repository is read-only: " + this);
        }

        @Override
        public String toString() {
            return "SharedModules(" + entry + ")";
        }
    }

    /**
     * @return a read-only repository of a module directory or module file, as the command line makes one
     */
    private static ModuleRepository repository(final File entry) {
        return entry.isDirectory() ? new DirRepository(entry, true) : new FileRepository(entry, true);
    }
}
