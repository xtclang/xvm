package org.xvm.asm;


import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import static org.xvm.util.Handy.copyOf;


/**
 * A repository that delegates to a chain of repositories. Reads occur from the repositories in the
 * order that they were provided to the constructor. Writes occur to the first repository only.
 */
public class LinkedRepository
        implements ModuleRepository {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a LinkedRepository.
     *
     * @param repos  a sequence of repositories to use, in order, to search through
     */
    public LinkedRepository(ModuleRepository... repos) {
        this(false, repos);
    }

    /**
     * Construct a LinkedRepository.
     *
     * @param fReadThrough  pass true to store a copy of all read modules in the first repository
     * @param repos         a sequence of repositories to use, in order, to search through
     */
    public LinkedRepository(boolean fReadThrough, ModuleRepository... repos) {
        assert repos != null && repos.length > 0;
        for (ModuleRepository repo : repos) {
            assert repo != null;
        }

        this.repos       = copyOf(repos);
        this.readThrough = fReadThrough;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a list of the repositories that underlie this repository
     */
    public List<ModuleRepository> asList() {
        return List.of(repos);
    }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getDomainNames() {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos) {
            names.addAll(repo.getDomainNames());
        }
        return names;
    }

    @Override
    public Set<String> getModuleNames(String sDomain) {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos) {
            names.addAll(repo.getModuleNames(sDomain));
        }
        return names;
    }

    @Override
    public Set<String> getModuleNames() {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos) {
            names.addAll(repo.getModuleNames());
        }
        return names;
    }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule) {
        VersionTree<Boolean> vers = new VersionTree<>();
        for (ModuleRepository repo : repos) {
            for (Version ver : repo.getAvailableVersions(sModule)) {
                vers.put(ver, true);
            }
        }
        return vers;
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        return search(sModule, repo -> repo.loadModule(sModule));
    }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        return search(sModule, repo -> repo.loadModule(sModule, version, fExact));
    }

    /**
     * Search the chain for a module, serving the first repository that has it and populating the
     * front repository on the way back if this is a read-through chain.
     *
     * <p>The two {@code loadModule} overloads differ only in how they ask each repository, so they
     * share this. They used to be copies of each other, and the copies had drifted in two ways -
     * each of which was a defect in the versioned one:
     *
     * <ul>
     * <li>a failed cache write {@code break}-ed out of the search, so a module that WAS found came
     *     back as null. A read-only front repository - which a build output directory routinely is -
     *     made that happen on every versioned load;</li>
     * <li>the front repository was populated with the module INSTANCE rather than a copy, so the
     *     cached entry and the source repository's entry were the same object, and the compiler
     *     mutating one mutated the other.</li>
     * </ul>
     *
     * <p>Both were already fixed in the unversioned overload and neither had been carried across.
     * Sharing the body is the actual fix: the reason one copy was right and the other wrong is that
     * there were two copies.
     *
     * @param sModule  the module name being searched for, for diagnostics
     * @param load     how to ask one repository for it
     *
     * @return the module, or null if no repository has it
     */
    private ModuleStructure search(String sModule, Function<ModuleRepository, ModuleStructure> load) {
        ModuleLoadException failure = null;
        for (int i = 0, c = repos.length; i < c; ++i) {
            ModuleStructure module;
            try {
                module = load.apply(repos[i]);
            } catch (ModuleLoadException e) {
                // the chain is a search, so a broken candidate must not hide a good copy in a
                // later repository; the failure is retained in case the whole search fails
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
                continue;
            }

            if (module != null) {
                // technically we could automatically merge this module with all the other versions
                // found in all of the other repositories; the choice at this point is to defer
                // that work
                return i > 0 && readThrough ? cacheInFront(sModule, module) : module;
            }
        }

        if (failure != null) {
            // the requested module was not served by any repository, and at least one candidate
            // file for it was broken; that evidence must not collapse into "module not found"
            throw failure;
        }
        return null;
    }

    /**
     * Populate the front repository with a module found further down the chain.
     *
     * @param sModule  the module name, for diagnostics
     * @param module   the module that was found
     *
     * @return the copy now held by the front repository, or the original if it could not be cached
     */
    private ModuleStructure cacheInFront(String sModule, ModuleStructure module) {
        try {
            // a copy, so that the compiler mutating the repos[0] contents cannot reach back into
            // the repository the module came from
            ModuleStructure moduleClone = new FileStructure(module, false).getModule();
            repos[0].storeModule(moduleClone);
            return moduleClone;
        } catch (IOException e) {
            // A cache write, not the lookup: failing to populate the front repository says nothing
            // about whether the module was found, and it was. Serve it; the cache stays cold.
            System.err.println("failed to cache " + sModule + " into "
                    + repos[0] + ": " + e.getMessage());
            return module;
        }
    }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException {
        repos[0].storeModule(module);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A sequence of repositories to use, in order, to search through. All writes occur to the first
     * repository in the array.
     */
    private final ModuleRepository[] repos;

    /**
     * A value of true stores a copy of all read modules in the first repository.
     */
    private final boolean            readThrough;
}
