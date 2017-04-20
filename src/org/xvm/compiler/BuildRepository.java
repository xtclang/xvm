package org.xvm.compiler;


import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * An in-memory repository for the modules being built by the compiler.
 *
 * @author cp 2017.04.20
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
        modulesByName.put(module.getModuleConstant().getQualifiedName(), module);
        }


    // ----- fields --------------------------------------------------------------------------------

    private Map<String, ModuleStructure> modulesByName = new TreeMap<>();
    }

