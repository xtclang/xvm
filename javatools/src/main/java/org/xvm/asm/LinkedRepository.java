package org.xvm.asm;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A repository that delegates to a chain of repositories. Reads occur from the repositories in the
 * order that they were provided to the constructor. Writes occur to the first repository only.
 */
public class LinkedRepository implements ModuleRepository {

    /**
     * A sequence of repositories to use, in order, to search through. All writes occur to the first
     * repository in the array.
     */
    private final List<ModuleRepository> repos;

    /**
     * A value of true stores a copy of all read modules in the first repository.
     */
    private final boolean readThrough;

    /**
     * Construct a LinkedRepository.
     *
     * @param repos  a sequence of repositories to use, in order, to search through
     */
    @SuppressWarnings("unused")
    public LinkedRepository(@NotNull ModuleRepository... repos) {
        this(false, repos);
    }

    public LinkedRepository(boolean fReadThrough, @NotNull ModuleRepository... repos) {
        this(fReadThrough, Arrays.asList(repos));
    }

    /**
     * Construct a LinkedRepository.
     *
     * @param fReadThrough  pass true to store a copy of all read modules in the first repository
     * @param repos         a sequence of repositories to use, in order, to search through
     */
    public LinkedRepository(boolean fReadThrough, @NotNull List<ModuleRepository> repos) {
        Objects.requireNonNull(repos).forEach(Objects::requireNonNull);
        this.repos       = List.copyOf(repos);
        this.readThrough = fReadThrough;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a list of the repositories that underlie this repository
     */
    public List<ModuleRepository> asList() {
        return repos;
    }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getDomainNames() {
        return repos.stream()
                .map(ModuleRepository::getDomainNames)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public Set<String> getModuleNames() {
        return repos.stream()
                .flatMap(repo -> repo.getModuleNames().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule) {
        var versions = new VersionTree<Boolean>();
        repos.stream()
             .flatMap(repo -> StreamSupport.stream(repo.getAvailableVersions(sModule).spliterator(), false))
             .forEach(version -> versions.put(version, true));
        return versions;
    }

    @Override
    public ModuleStructure loadModule(String moduleName) {
        return loadModule(repo -> repo.loadModule(moduleName));
    }

    @Override
    public ModuleStructure loadModule(String moduleName, Version version, boolean exact) {
        return loadModule(repo -> repo.loadModule(moduleName, version, exact));
    }

    private ModuleStructure loadModule(Function<ModuleRepository, ModuleStructure> loader) {
        boolean isPrimary = true;
        for (ModuleRepository repo : repos) {
            var module = loader.apply(repo);
            if (module == null) {
                isPrimary = false;
                continue;
            }
            if (readThrough && !isPrimary) {
                try {
                    // Create a defensive copy so the primary repository may mutate it
                    var fsCopy = new FileStructure(module, false);
                    repos.getFirst().storeModule(fsCopy.getModule());
                } catch (IOException e) {
                    // TODO: We need to get structured logging in here ASAP
                    System.err.println(e.getMessage());
                    return null;
                }
            }
            return module;
        }
        return null;
    }

    @Override
    public void storeModule(ModuleStructure module) throws IOException {
        // TODO: What about index out of bounds? The contract entry is complete hidden.
        repos.getFirst().storeModule(module);
    }
}
