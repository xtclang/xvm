package org.xvm.javajit;


import java.lang.ref.WeakReference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.NATIVE_MODULE;
import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.require;
import static org.xvm.util.Handy.resize;
import static org.xvm.util.Handy.scan;
import static org.xvm.util.Handy.sorted;


/**
 * The Ecstasy-to-Java "just-in-time" (JIT) compiler implementation of the XVM specification.
 */
public class Xvm {
    /**
     * Construct an XVM that JITs to Java bytecode.
     *
     * @param repo  the {@link ModuleRepository} that the XVM system libraries can be loaded from
     */
    public Xvm(ModuleRepository repo) {
        for (int i = 0; i < locks.length; ++i) {
            locks[i] = new Object();
        }
        this.systemRepo       = repo;
        this.nativeTypeSystem = new TypeSystem(this, repo);
        register(this.nativeTypeSystem);
        this.nativeContainer  = createContainer(null, nativeTypeSystem, FailEverythingInjector);

        ModuleLoader ecstasy = null;
        ModuleLoader _native = null;
        for (ModuleLoader loader : nativeTypeSystem.loader.owned) {
            if (loader.module.getName().equals(ECSTASY_MODULE)) {
                assert ecstasy == null;
                ecstasy = loader;
            } else if (loader.module.getName().equals(NATIVE_MODULE)) {
                assert _native == null;
                _native = loader;
            }
        }
        assert ecstasy != null && _native != null;
        this.ecstasyLoader = ecstasy;
        this.nativeLoader  = _native;
    }

    /**
     * The Injector into the native Container is just an asserter that nothing is being injected,
     * since native injections are created from within that Container.
     */
    private static final Injector FailEverythingInjector = new Injector();

    /**
     * The ModuleRepository that the system modules are loaded from.
     */
    final ModuleRepository systemRepo;

    /**
     * The TypeSystem for the invisible "Container -1", which is the only TypeSystem allowed to
     * contain native code.
     */
    final TypeSystem nativeTypeSystem;

    /**
     * The invisible "Container -1", which provides the interface to the "native" world and loads
     * all the Ecstasy system modules that require any native support.
     */
    final Container nativeContainer;

    /**
     * The ModuleLoader for the core Ecstasy library.
     */
    final ModuleLoader ecstasyLoader;

    /**
     * The ModuleLoader for the "native" library (the module that interfaces directly with the JVM).
     */
    final ModuleLoader nativeLoader;

    /**
     * All Containers (held only by a weak reference) keyed by id.
     */
    private final ConcurrentHashMap<Long, WeakReference<Container>> containers = new ConcurrentHashMap<>();

    /**
     * A counter used to generate unique ascending identities for each new Container.
     */
    private final AtomicLong containerCount = new AtomicLong(-1);

    /**
     * A Map holding the TypeSystems (held only by a weak reference) keyed by the temporally-unique
     * name assigned to each TypeSystem.
     */
    private final ConcurrentHashMap<String, WeakReference<TypeSystem>> typeSystems = new ConcurrentHashMap<>();

    /**
     * Internal modification counter used to trigger a cleanup of dropped weak references in
     * {@link #typeSystems}. This variable is not thread safe, but its thread safety is not required
     * for it to do its job.
     */
    private int tsMapModCount;

    /**
     * A Map holding the ModuleLoaders (held only by a weak reference) keyed by the
     * temporally-unique Java package name assigned to each ModuleLoader.
     */
    private final ConcurrentHashMap<String, WeakReference<ModuleLoader>> moduleLoaders = new ConcurrentHashMap<>();

    /**
     * Internal modification counter used to trigger a cleanup of dropped weak references in
     * {@link #moduleLoaders}. This variable is not thread safe, but its thread safety is not
     * required for it to do its job.
     */
    private int mlMapModCount;

    /**
     * A lookup table for finding Java package names (which are temporally unique) keyed by module
     * names (which are not unique, since the same module name can be loaded many times with
     * different versions, dependencies, or even the common case where the same name e.g. "module
     * test {...}" is used by different module creators). Since many modules can have the same name,
     * and different versions and conditional refinements of each can be in use simultaneously
     * within the XVM, each module name in this Map is associated with a sparse (i.e. can contain
     * nulls) array of package names, which are used as the keys to the {@link #moduleLoaders} Map.
     * From the {@link ModuleLoader}, it's a quick jump to the {@link TypeSystemLoader} and then the
     * {@link TypeSystem} that the module belongs to.
     *
     * Due to concurrent execution, each map-based jump from data structure to data structure must
     * be checked to make sure that the referred-to object isn't null. Additionally, the data in
     * this table can be out-of-date, because when a TypeSystem is dropped by Java's GC, this data
     * structure isn't automatically/immediately updated, and GC'd package names can be re-used by
     * subsequently created type systems. As a result, the key (the module name) of the entry in
     * this map needs to be double-checked against the actual modules in the TypeSystem to verify
     * that the lookup table was correct.
     */
    private final ConcurrentHashMap<String, String[]> packagesByModule = new ConcurrentHashMap<>();

    /**
     * Used to sort ModuleStructures by their ModuleConstant identities.
     */
    static final Comparator<ModuleStructure> StructureByModuleId =
            Comparator.comparing(ModuleStructure::getIdentityConstant);

    /**
     * Used to sort ModuleLoaders by their ModuleStructure's ModuleConstant identities.
     */
    static final Comparator<ModuleLoader> LoaderByModuleId =
            Comparator.comparing(l -> l.module.getIdentityConstant());

    /**
     * Objects used internally for synchronization, while avoiding single-threading everything. The
     * size of the array should be prime because it is used as a modulo.
     */
    private final Object[] locks = new Object[61];

    // ----- public API ----------------------------------------------------------------------------

    /**
     * Create a Linker in order to create a new TypeSystem.
     *
     * @return a new Linker
     */
    public Linker createLinker() {
        return new Linker(this).addSharedModule(ecstasyLoader);
    }

    /**
     * Create a TypeSystem around the specified module.
     *
     * @param repo     the {@link ModuleRepository} to load the module and its dependencies from; if
     *                 null, then the repository that the XVM was created with will be used
     * @param module   the qualified name of the module to load
     * @param ver      the optional {@link Version} of the module to load; may be null
     * @param refiner  the optional {@link Refiner} to use to refine the module; if null, the
     *                 {@link Refiner#DefaultRefiner} will be used
     *
     * @return the new TypeSystem, or null if the TypeSystem could not be created for any reason
     */
    public TypeSystem createTypeSystem(ModuleRepository repo, String module, Version ver, Refiner refiner) {
        require("module", module);

        if (repo == null) {
            repo = systemRepo;
        }

        ModuleStructure moduleStructure = repo.loadModule(module, ver, false);
        return moduleStructure == null ? null : createTypeSystem(repo, moduleStructure, refiner);
    }

    /**
     * Create a TypeSystem around the specified module.
     *
     * @param repo     the {@link ModuleRepository} to load the module and its dependencies from; if
     *                 null, then the repository that the XVM was created with will be used
     * @param module   the {@link ModuleStructure} to create the TypeSystem around
     * @param refiner  the optional {@link Refiner} to use to refine the module; if null, the
     *                 {@link Refiner#DefaultRefiner} will be used
     *
     * @return the new TypeSystem, or null if the TypeSystem could not be created for any reason
     */
    public TypeSystem createTypeSystem(ModuleRepository repo, ModuleStructure module, Refiner refiner) {
        require("module", module);

        if (refiner == null) {
            refiner = Refiner.DefaultRefiner;
        }

        Linker linker = nativeTypeSystem.create();
        if (repo != null) {
            linker = linker.withRepo(repo);
        }
        linker = linker.withRefiner(refiner)
                       .withMain(module);
        return linker.isBad() ? null : linker.link();
    }

    /**
     * The default inject instance used for "main" containers.
     */
    public final Injector DefaultMainInjector = new Injector() {
        // TODO implement "native" injector
    };

    /**
     * Create a new "main" Container around the specified TypeSystem.
     *
     * @param typeSystem  the {@link TypeSystem} to use to form the Container
     * @param injector    the {@link Injector} to use to provide for dependency injection into the
     *                    new Container
     *
     * @return a new Container
     */
    public Container createContainer(TypeSystem typeSystem, Injector injector) {
        return createContainer(nativeContainer, typeSystem, injector);
    }

    /**
     * Obtain the specified Container by id.
     *
     * @param id  the Container id
     *
     * @return the specified Container if it exists; otherwise null
     */
    public Container getContainer(long id) {
        if (id < 0) {
            if (id == -1) {
                return nativeContainer;
            }
            throw new IllegalArgumentException("illegal container id: " + id);
        }

        WeakReference<Container> ref = containers.get(id);
        if (ref != null) {
            return ref.get();
        }
        return null;
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Create a Container around the specified TypeSystem.
     *
     * This should be the only place in the code that creates Creator instances.
     *
     * @param parent      the parent Container of the new Container; required, except for the native
     *                    Container
     * @param typeSystem  the TypeSystem for the new Container; required
     * @param injector    the Injector for the new Container; optional
     *
     * @return the new Container
     */
    Container createContainer(Container parent, TypeSystem typeSystem, Injector injector) {
        assert typeSystem != null;

        if (injector == null) {
            injector = DefaultMainInjector;
        }

        long id = containerCount.getAndIncrement();
        assert id >= 0 && parent != null || id == -1 && parent == null;

        Container container = new Container(parent, id, typeSystem, injector);
        if (containers.putIfAbsent(id, new WeakReference<>(container)) != null) {
            throw new IllegalStateException();
        }

        // occasionally sweep through the data structure that holds weak references to Containers
        // and discard entries whose weak references have been cleared
        if ((id & 0x3FF) == 0) {    // every 1000x through this method ...
            containers.entrySet().removeIf(e -> e.getValue().get() == null);
        }

        return container;
    }

    /**
     * After successfully linking, this method either finds an already-existing TypeSystem that is
     * exactly identical to the one being requested, or it creates a new TypeSystem. All manner of
     * race conditions exist (and are hopefully handled correctly) here.
     *
     * Other than the initial native TypeSystem, this should be the only place in the code that
     * creates TypeSystem instances.
     *
     * @param shared  the ModuleLoaders shared into the TypeSystem
     * @param owned   the ModuleStructures to load within the TypeSystem
     *
     * @return a TypeSystem
     */
    TypeSystem ensureTypeSystem(ModuleLoader[] shared, ModuleStructure[] owned) {
        assert shared != null && owned != null;

        // use any of the module names in the desired type system as the module to use to find
        // existing TypeSystems that include that module, since one of those TypeSystems might be
        // identical to the TypeSystem being requested; in the case of a shared module, grab the
        // last of the shared module names, just to avoid accidentally grabbing the universally
        // shared "ecstasy.xtclang.org" module
        String moduleName = (owned.length > 0 ? owned[0] : shared[shared.length-1].module).getName();

        // pre-sort the modules so that all type systems will order their modules the same; this
        // makes comparison of type systems much simpler and more efficient
        shared = sorted(shared, LoaderByModuleId);
        owned  = sorted(owned, StructureByModuleId);

        // given the requested shape of the TypeSystem, make sure no other thread is simultaneously
        // racing us to build the same TypeSystem; note: this method will deadlock if it recurses
        synchronized (mutex(typeSystemKey(owned.length > 0 ? owned : modulesOf(shared)))) {
            // look for an existing TypeSystem with the same exact set of owned and shared modules
            String[] pkgNames = packagesByModule.get(moduleName);
            if (pkgNames != null) {
                for (String pkgName : pkgNames) {
                    if (pkgName != null) {
                        WeakReference<ModuleLoader> ref = moduleLoaders.get(pkgName);
                        if (ref != null) {
                            ModuleLoader loader = ref.get();
                            if (loader != null && loader.module.getName().equals(moduleName)) {
                                TypeSystem ts = loader.typeSystem;
                                if (sameTypeSystem(ts, shared, owned)) {
                                    return ts;
                                }
                            }
                        }
                    }
                }
            }

            // no such TypeSystem already exists; create one
            TypeSystem ts = new TypeSystem(this, shared, owned);
            register(ts);
            return ts;
        }
    }

    /**
     * Add the TypeSystem to the known {@link #typeSystems}.
     *
     * @param ts  the new TypeSystem
     */
    void register(TypeSystem ts) {
        // add the TypeSystem
        if (typeSystems.putIfAbsent(ts.name, new WeakReference<>(ts)) != null) {
            throw new IllegalStateException();
        }
    }

    /**
     * Generate a temporally-unique name for a TypeSystem.
     *
     * @param shared  the ModuleLoaders shared into the TypeSystem
     * @param owned   the ModuleStructures to load within the TypeSystem
     *
     * @return a unique TypeSystem name based on the name of the TypeSystem's owned modules
     */
    String generateTypeSystemName(ModuleLoader[] shared, ModuleStructure[] owned) {
        assert shared != null && owned != null;

        // occasionally sweep through the data structure that holds weak references to TypeSystems
        // and discard entries whose weak references have been cleared.
        if ((++tsMapModCount & 0x3FF) == 0) {   // every 1000x through this method ...
            typeSystems.entrySet().removeIf(e -> e.getValue().get() == null);
        }

        if (owned.length == 0) {
            assert shared.length > 0;
            String allNames = typeSystemKey(modulesOf(shared));
            String name     = "shared:" + allNames;
            int    count    = 1;
            while (typeSystems.containsKey(name) && typeSystems.get(name).get() != null) {
                name = "shared_" + (++count) + ":" + allNames;
            }
            return name;
        }

        ModuleStructure module = owned[0];
        String          pkg    = moduleToPackageName(module.getName(), module.getVersion());
        String          name   = pkg;
        int             count  = 1;
        while (typeSystems.containsKey(name) && typeSystems.get(name).get() != null) {
            name = pkg + ".alt" + (++count);
        }
        return name;
    }

    /**
     * Compare two type systems for identicality.
     *
     * @param ts      an existing TypeSystem
     * @param shared  an already-sorted array of shared ModuleLoaders to compare to the shared
     *                ModuleLoaders in the TypeSystem
     * @param owned   an already-sorted array of owned ModuleStructures to compare to the
     *                ModuleStructures of the owned ModuleLoaders in the TypeSystem
     *
     * @return true iff the specified TypeSystem is identical to the TypeSystem that would be
     *         created from the `shared` ModuleLoaders and the `owned` ModuleStructures
     */
    boolean sameTypeSystem(TypeSystem ts, ModuleLoader[] shared, ModuleStructure[] owned) {
        if (shared.length != ts.shared.length || owned.length != ts.owned.length) {
            return false;
        }

        for (int i = 0; i < owned.length; ++i) {
            if (!sameModule(owned[i], ts.owned[i].module)) {
                return false;
            }
        }

        for (int i = 0; i < shared.length; ++i) {
            if (!sameModule(shared[i].module, ts.shared[i].module)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare two modules for identicality.
     *
     * @param module1  a refined module to compare with another refined module
     * @param module2  a refined module to compare with another refined module
     *
     * @return true iff the two refined modules are identical and identically refined
     */
    boolean sameModule(ModuleStructure module1, ModuleStructure module2) {
        return module1.isRefined() && module2.isRefined()
            && module1.getName().equals(module2.getName())
            && Arrays.equals(module1.getDigest(), module2.getDigest());
    }

    /**
     * Given an array of ModuleLoaders, produce a corresponding array of ModuleStructures.
     *
     * @param loaders  an array of ModuleLoaders
     *
     * @return a corresponding array of ModuleStructures
     */
    ModuleStructure[] modulesOf(ModuleLoader[] loaders) {
        ModuleStructure[] modules = new ModuleStructure[loaders.length];
        for (int i = 0; i < loaders.length; ++i) {
            modules[i] = loaders[i].module;
        }
        return modules;
    }

    /**
     * Generate a temporally-unique package name for a module, create a ModuleLoader using that
     * name, and register the ModuleLoader under that name.
     *
     * @param tsl     the {@link TypeSystemLoader} within which the module will exist
     * @param module  the {@link ModuleStructure} to use to generate Java classes from (to load)
     *
     * @return the new {@link ModuleLoader}
     */
    ModuleLoader createModuleLoader(TypeSystemLoader tsl, ModuleStructure module) {
        assert tsl != null && module != null;

        if ((++mlMapModCount & 0xFF) == 0) {    // every 250x through this method
            loadersGc();
        }

        synchronized (moduleLoaders) {
            String moduleName = module.getName();
            String pkg        = moduleToPackageName(moduleName, module.getVersion());
            String unique     = pkg;
            int    count      = 1;
            while (moduleLoaders.containsKey(unique)) {
                unique = pkg + ".alt" + (++count);
            }

            // TODO CP can we defer this?
            ModuleLoader loader   = new ModuleLoader(tsl, module, unique);
            String[]     packages = packagesByModule.get(moduleName);
            String[]     original = packages;
            int          index;
            if (packages == null) {
                // this module wasn't already loaded
                packages = new String[4];
                index    = 0;
            } else {
                if ((index = scan(packages, unique)) < 0 &&
                    (index = scan(packages, null  )) < 0) {
                    // replace a full array with a bigger array
                    index    = packages.length;
                    packages = resize(packages, packages.length * 2);
                }
            }
            packages[index] = unique;
            if (packages != original) {
                packagesByModule.put(moduleName, packages);
            }
            moduleLoaders.put(unique, new WeakReference<>(loader));
            return loader;
        }
    }

    /**
     * Occasionally sweep through the data structure that holds weak references to ModuleLoaders and
     * discard entries whose weak references have been cleared.
     */
    private void loadersGc() {
        synchronized (moduleLoaders) {
            for (Iterator<Map.Entry<String, WeakReference<ModuleLoader>>> iter
                    = moduleLoaders.entrySet().iterator(); iter.hasNext(); ) {
                var    entry = iter.next();
                String pkg   = entry.getKey();
                var    ref   = entry.getValue();
                if (ref != null && ref.get() == null) {
                    // this weak reference has been dropped and needs to be cleaned out
                    moduleLoaders.remove(pkg, ref);
                }
            }

            // clean up the index
            for (Iterator<Map.Entry<String, String[]>> iter = packagesByModule.entrySet().iterator();
                    iter.hasNext(); ) {
                var      entry  = iter.next();
                String   module = entry.getKey();
                String[] pkgs   = entry.getValue();
                boolean  any    = false;
                for (int i = 0; i < pkgs.length; ++i) {
                    String pkg = pkgs[i];
                    if (pkg != null) {
                        WeakReference<ModuleLoader> ref;
                        ModuleLoader loader;
                        if ((ref = moduleLoaders.get(pkg)) != null
                                && (loader = ref.get()) != null
                                && loader.module.getName().equals(module)) {
                            any = true;
                        } else {
                            pkgs[i] = null;
                        }
                    }
                }
                if (!any) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * Convert a dot-delimited Ecstasy module name to a dot-delimited Java package name.
     *
     * @param module  a dot-delimited Ecstasy module name
     * @param ver     the module version, or null
     *
     * @return a dot-delimited Java package name
     */
    static String moduleToPackageName(String module, Version ver) {
        assert module != null && !module.isEmpty() && module.indexOf('/') < 0;

        StringBuilder buf   = new StringBuilder();
        String[]      parts = parseDelimitedString(module, '.');
        if (parts.length <= 1) {
            buf.append("anon");
        }
        for (int i = parts.length - 1; i >= 0; --i) {
            String part = parts[i];
            if (part.isEmpty()) {
                throw new IllegalArgumentException();
            }
            if (!buf.isEmpty()) {
                buf.append('.');
            }
            buf.append(part);
        }

        if (ver != null) {
            String s = ver.normalize().withoutBuildString().toString().replace('.', '_');
            if (isDigit(s.charAt(0))) {
                buf.append("_v");
            }
            buf.append(s);
        }
        return buf.toString();
    }

    /**
     * Obtain a String key for a TypeSystem based on a single module. This key can be used to obtain
     * a mutex to synchronize on for creating a TypeSystem around the specified module.
     *
     * @param module  the single main module owned by the TypeSystem
     *
     * @return a predictable String based on the module identity
     */
    String typeSystemKey(ModuleStructure module) {
        assert module != null;
        return module.getName();
    }

    /**
     * Obtain a String key for a TypeSystem based on its modules. This key can be used to obtain a
     * mutex to synchronize on for creating a TypeSystem around the specified module.
     *
     * @param modules  an array of modules in the TypeSystem
     *
     * @return a predictable String based on the module identities, regardless of their order
     */
    String typeSystemKey(ModuleStructure[] modules) {
        assert modules != null;
        checkElementsNonNull(modules);
        int count = modules.length;
        assert count > 0;

        if (count == 1) {
            return typeSystemKey(modules[0]);
        }

        modules = sorted(modules, StructureByModuleId);
        StringBuilder buf = new StringBuilder();
        buf.append('{')
            .append(modules[0].getName());
        for (int i = 1; i < count; ++i) {
            buf.append(',')
                .append(modules[i].getName());
        }
        return buf.append('}').toString();
    }

    /**
     * Obtain an internal object to synchronize on.
     *
     * @param s  the String to get a mutex for
     *
     * @return the mutex to synchronize on
     */
    Object mutex(String s) {
        return locks[(s.hashCode() & 0x7FFFFFFF) % locks.length];
    }
}
