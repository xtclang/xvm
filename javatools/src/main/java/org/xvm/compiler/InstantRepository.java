package org.xvm.compiler;


import java.util.Collections;
import java.util.Set;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;


/**
 * An in-memory repository for the modules being built by the compiler.
 */
public class InstantRepository
        implements ModuleRepository
    {
    public InstantRepository(ModuleStructure module)
        {
        f_module = module;
        }

    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames()
        {
        return Collections.singleton(f_module.getName());
        }

    @Override
    public ModuleStructure loadModule(String sModule)
        {
        return sModule.equals(f_module.getName()) ? f_module : null;
        }

    @Override
    public void storeModule(ModuleStructure module)
        {
        throw new UnsupportedOperationException();
        }


    // ----- fields --------------------------------------------------------------------------------

    private final ModuleStructure f_module;
    }