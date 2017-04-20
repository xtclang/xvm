package org.xvm.asm;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


/**
 * A repository that delegates to a chain of repositories. Reads occur from the repositories in the
 * order that they were provided to the constructor. Writes occur to the first repository only.
 *
 * @author cp 2017.04.20
 */
public class LinkedRepository
        implements ModuleRepository
    {
    // ----- constructors  -------------------------------------------------------------------------

    public LinkedRepository(ModuleRepository... repos)
        {
        assert repos != null && repos.length > 0;
        for (ModuleRepository repo : repos)
            {
            assert repo != null;
            }

        this.repos = repos.clone();
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a list of the repositories that underly this repository
     */
    public List<ModuleRepository> asList()
        {
        return Collections.unmodifiableList(Arrays.asList(repos));
        }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getDomainNames()
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getDomainNames());
            }
        return names;
        }

    @Override
    public Set<String> getModuleNames(String sDomain)
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getModuleNames(sDomain));
            }
        return names;
        }

    @Override
    public Set<String> getModuleNames()
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getModuleNames());
            }
        return names;
        }

    @Override
    public Set<Version> getAvailableVersions(String sModule)
        {
        TreeSet<Version> vers = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            vers.addAll(repo.getAvailableVersions(sModule));
            }
        return vers;
        }

    @Override
    public ModuleStructure loadModule(String sModule)
        {
        for (ModuleRepository repo : repos)
            {
            ModuleStructure module = repo.loadModule(sModule);
            if (module != null)
                {
                // technically we could automatically merge this module with all the other versions
                // found in all of the other repositories; the choice at this point is to defer
                // that work
                return module;
                }
            }
        return null;
        }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact)
        {
        for (ModuleRepository repo : repos)
            {
            ModuleStructure module = repo.loadModule(sModule, version, fExact);
            if (module != null)
                {
                return module;
                }
            }
        return null;
        }

    @Override
    public void storeModule(ModuleStructure module)
        {
        repos[0].storeModule(module);
        }


    // ----- fields --------------------------------------------------------------------------------

    private final ModuleRepository[] repos;
    }
