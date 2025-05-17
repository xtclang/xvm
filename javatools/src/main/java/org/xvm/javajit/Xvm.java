package org.xvm.javajit;


import java.lang.ref.WeakReference;

import java.util.HashMap;
import java.util.Map;

import java.util.Set;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.Version;
import org.xvm.asm.constants.ModuleConstant;

import static org.xvm.util.Handy.parseDelimitedString;


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
     * This is a convenient constructor for loading a main Module into a new Xvm on construction.
     *
     * @param repo        the ModuleRepository capable of loading both system Modules and the main
     *                    Module
     * @param moduleName  the qualified name of main Module to load
     */
    public Xvm(ModuleRepository repo, String moduleName) {
        this(repo);
        TypeSystem main = nativeTypeSystem.create()
                                          .withRepo(repo)
                                          .withMain(moduleName)
                                          .link();
        mainContainer = new Container(nativeContainer, 0, main);
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
     * The initial "Container 0", which represents the "main" (in C or Java terms) for the XVM.
     */
    private Container mainContainer;

    /**
     * All nested containers (id > 0).
     */
    private Map<Integer, WeakReference<Container>> nestedContainers;

    /**
     * TODO
     */
    private Map<String, WeakReference<TypeSystem>> tsNames = new HashMap<>();

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

    /**
     * Convert a dot-delimited Ecstasy module name to a dot-delimited Java package name.
     *
     * @param module  a dot-delimited Ecstasy module name
     *
     * @return a dot-delimited Java package name
     */
    public static String moduleNameToPackage(String module) {
        if (module.indexOf('.') < 0) {
            return "xvm." + module;
        }

        String[] parts = parseDelimitedString(module, '.');
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
            assert !part.isEmpty();
            if (!buf.isEmpty()) {
                buf.append('.');
            }
            buf.append(part);
        }
        return buf.toString();
    }

    /**
     * Generate a temporally-unique name for a TypeSystem.
     *
     * @param ts  the TypeSystem object
     *
     * @return a unique TypeSystem name based on the name of the TypeSystem's main module
     */
    public String generateTypeSystemName(TypeSystem ts) {
        synchronized (tsNames) {
            String pkg   = moduleNameToPackage(ts.fileStructure.getModule().getName());
            String name  = pkg;
            int    count = 1;
            while (tsNames.containsKey(name) && tsNames.get(name).get() != null) {
                name = pkg + (++count);
            }
            tsNames.put(name, new WeakReference<>(ts));
            return name;
        }
    }

    /**
     * Generate a temporally-unique package name for a module.
     *
     * @param module  the {@link ModuleConstant} that specifies the module
     * 
     * @return a '/'-delimited name that ends with '/' that is a temporarily unique package name
     */
    public String generateModulePackagePrefix(ModuleConstant module) {
        // TODO
        return null;
    }

    // TODO need a way to find an existing module and/or typesystem

    static final class ModuleId {
        String        name;
        Version       ver;             // part of moduleConstant
        Set<String>   defines;
        Set<ModuleId> optionalDeps;
        byte[]        hash;            // digest of the resolved module structure
        private int hashcode;
        @Override public int hashCode() {
            return hashcode;
        }
    }

    private Map<String, ModuleId> modulesByPrefix;

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
