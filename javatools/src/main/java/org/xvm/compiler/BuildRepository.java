package org.xvm.compiler;


import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * An in-memory repository for the modules being built by the compiler.
 */
public class BuildRepository
        implements ModuleRepository
    {
    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames()
        {
        return Collections.unmodifiableSet(modulesByName.keySet());
        }

    @Override
    public ModuleStructure loadModule(String sModule)
        {
        return modulesByName.get(sModule);
        }

    @Override
    public void storeModule(ModuleStructure module)
        {
        assert module != null;
        modulesByName.put(module.getIdentityConstant().getName(), module);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Copy all the modules from a specified BuildRepository.
     */
    public void storeAll(BuildRepository repoThat)
        {
        modulesByName.putAll(repoThat.modulesByName);
        }


    // ----- fields --------------------------------------------------------------------------------

    private final Map<String, ModuleStructure> modulesByName = new TreeMap<>();
    }

