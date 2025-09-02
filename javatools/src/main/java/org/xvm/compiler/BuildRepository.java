package org.xvm.compiler;


import java.util.stream.Collectors;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ModuleConstant;


/**
 * An in-memory repository for the modules being built by the compiler.
 */
public class BuildRepository
        implements ModuleRepository {
    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        return modulesById.keySet().stream().map(ModuleConstant::getName).collect(Collectors.toSet());
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        for (Map.Entry<ModuleConstant, ModuleStructure> entry : modulesById.entrySet()) {
            if (entry.getKey().getName().equals(sModule)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        if (version == null) {
            return loadModule(sModule);
        }

        for (Map.Entry<ModuleConstant, ModuleStructure> entry : modulesById.entrySet()) {
            if (entry.getKey().getName().equals(sModule)) {
                ModuleStructure module = entry.getValue();
                if (fExact
                        ? module.getVersion().equals(version)
                        : module.getVersion().isSubstitutableFor(version)) {
                    return module;
                }
            }
        }
        return null;
    }

    @Override
    public void storeModule(ModuleStructure module) {
        assert module != null;

        ModuleConstant idModule = module.getIdentityConstant();
        if (idModule.getVersion() == null) {
            Version version = module.getVersion();
            if (version != null) {
                idModule = idModule.getConstantPool().
                                ensureModuleConstant(idModule.getName(), version);
            }
        }
        modulesById.put(idModule, module);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Copy all the modules from a specified BuildRepository.
     */
    public void storeAll(BuildRepository repoThat) {
        modulesById.putAll(repoThat.modulesById);
    }


    // ----- fields --------------------------------------------------------------------------------

    private final Map<ModuleConstant, ModuleStructure> modulesById = new TreeMap<>();
}

