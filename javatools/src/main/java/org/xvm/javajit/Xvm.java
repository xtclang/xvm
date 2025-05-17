package org.xvm.javajit;


import java.lang.ref.WeakReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.ModuleConstant;


/**
 * The runtime of the Java JIT for Ecstasy.
 */
public class Xvm {
    public Xvm(ModuleRepository repo) {
        this.systemRepo       = repo;
        this.nativeTypeSystem = new TypeSystem(this, repo);
        this.nativeContainer  = new Container(null, -1, nativeTypeSystem);
    }

    /**
     * The ModuleRepository that the system modules are loaded from.
     */
    private ModuleRepository systemRepo;

    /**
     * The TypeSystem for the invisible "Container -1", which is the only TypeSystem allowed to
     * contain native code.
     */
    private TypeSystem nativeTypeSystem;

    /**
     * The invisible "Container -1", which provides the interface to the "native" world and loads
     * all the Ecstasy system modules that require any native support.
     */
    private Container nativeContainer;

    /**
     *
     */
    private Container mainContainer;

    /**
     * All nested containers (id > 0).
     */
    private Map<Integer, WeakReference<Container>> nestedContainers;

    /**
     * Obtain the specified Container by id.
     *
     * @param id  the Container id
     *
     * @return the specified Container if it exists; otherwise null
     */
    public Container getContainer(int id) {
        if (id <= 0) {
            if (id == -1) {
                return nativeContainer;
            }
            if (id == 0) {
                return mainContainer;
            }
            throw new IllegalArgumentException("illegal container id: " + id);
        }

        if (nestedContainers != null) {
            WeakReference<Container> ref = nestedContainers.get(id);
            if (ref != null) {
                return ref.get();
            }
        }
        return null;
    }

//    static class ModuleId {
//        ModuleConstant moduleConstant;
//        // Version     ver;             // part of moduleConstant
//        Set<String>    defines;
//        Set<ModuleId>  optionalDeps;
//        byte[]         hash;            // strong digest of the module structure
//    }
//
//    private Map<String, ModuleId> modulesByPrefix;
//    private Map<ModuleId, ModuleLoader> moduleLoaders;
//
//    private Map<String, Set<ModuleId>> modulesByName;
//
//
//    static class TypeSystemInfo {
//        // TODO
//    }
//
//    public static class ClassInfo {
//        // TODO
//    }
//
//    void registerClass(String name, ClassInfo info) {
//        // TODO
//    }
}
