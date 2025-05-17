package org.xvm.javajit;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ModuleConstant;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.util.Handy.require;


/**
 * As part of the Ecstasy JIT implementation targeting the JVM, this class represents an Ecstasy
 * TypeSystem, which is a collection of Ecstasy modules that are linked together. This class is
 * responsible for generating the Java ClassFiles for the Ecstasy code represented by this
 * TypeSystem.
 *
 * In the simple case, creating a TypeSystem is about specifying a main module (e.g. "load my app,
 * please!") which automatically drags in the shared Ecstasy core module ("ecstasy.xtclang.org"),
 * and that's it. But creating a TypeSystem is potentially far more complex than this simple
 * example, and can be an iterative and recursive process, which at each stage must resolve
 * significant questions:
 *
 * * Each module has a set of modules that it depends upon, and those dependencies can be required
 *   or optional, and in the case of optional dependencies, the "optional" indicator means only that
 *   the dependency is explicitly supported, while the "desired" indicator implies that the
 *   dependency _should_ be used if it's available; in both cases, there is an inclusion decision to
 *   be made.
 *
 * * Every Module has a dependency on the `ecstasy.xtclang.org` Module, and may have dependencies on
 *   other core Ecstasy library Modules. These core library Modules must be unique within the
 *   runtime, i.e. loaded only once, in order to ensure that the .
 *
 * * Each module that needs to be loaded may have any number of versions available, so if the
 *   decision is made to load that module (either because it's required -- which implies an
 *   affirmative decision -- or because it's optional and an explicit decision is made to load it),
 *   then a further decision must be made as to which version of that module should be used. The
 *   additional dependencies that the yet-to-be-loaded module introduces can vary across its
 *   versions, so until the version to load has been decided, the additional dependencies (including
 *   optional dependencies to decide upon) are unknown.
 *
 * * Each module already selected for inclusion in the TypeSystem may limit and/or influence the
 *   version decision for each additional module being loaded. Each module can specify its own
 *   version constraints on other modules. So as each module is loaded, new constraints may need to
 *   be incorporated into future decisions.
 *
 * * Additionally, attempting to load a module as part of the TypeSystem may reveal requirements or
 *   constraints that are incompatible with decisions that were already made in the formation of the
 *   TypeSystem up to this point. Encountering such a condition means that the TypeSystem is invalid
 *   and cannot be altered into a valid state; to "fix" this requires a new TypeSystem to be built,
 *   but with a different set of decisions along the way to avoid coming to the same dead end.
 *
 * * Some modules are provided from existing TypeSystems to the new TypeSystem. These are called
 *   "shared modules", because they are being shared into the new TypeSystem. The version and
 *   dependency decisions for shared modules were already made, and thus the shared modules must be
 *   consistent (in terms of dependencies) with the modules in the new TypeSystem. Some decisions
 *   made previously when loading those shared modules may be fundamentally inconsistent with the
 *   TypeSystem being formed, which means that the TypeSystem is already invalid.
 *
 * * Modules can also contain conditional elements predicated on a set of defined names (see the
 *   Ecstasy `@Iff` annotation), such as "test" (see `@Test`) and "debug" (see `@Debug`). These
 *   names must be specified (either positively or negatively) before any modules referring to them
 *   are resolved; all subsequent modules loaded within the TypeSystem must be resolved using the
 *   same defined name decisions to avoid potential conflicts across modules.
 *
 * * Each TypeSystem has a main module. It is specified after providing the set of shared Modules.
 *
 * * After the main Module has been specified, then additional modules can be specified. Additional
 *   modules will be split out into their own TypeSystems to the extent possible, in order to allow
 *   them to be reused by other TypeSystems without dragging in any unnecessary (and potentially
 *   conflicting) baggage. Reusing ModuleLoaders implies reusing Java classes, which can (in theory)
 *   save a huge amount of memory (not to mention the amount of work saved) in a multi-tenanted
 *   runtime.
 *
 * * After all Modules have been specified, then the TypeSystem can be linked.
 */
public class TypeSystem {
    /**
     * Construct the core TypeSystem for the hidden "Container -1".
     *
     * @param xvm   the XVM
     * @param repo  the ModuleRepository to load the core Ecstasy modules from
     */
    TypeSystem(Xvm xvm, ModuleRepository repo) {
        require("xvm", xvm);
        require("repo", repo);

        // TODO hand build the "container -1" type system using ecstasy.xtclang.org etc.
        // load ECSTASY_MODULE ecstasy.xtclang.org
        // load TURTLE_MODULE mack.xtclang.org
        // load NATIVE_MODULE _native.xtclang.org ?????
        FileStructure fs = null;

        this.xvm           = xvm;
        this.fileStructure = fs;
        this.name          = xvm.generateTypeSystemName(this);
        this.loader        = new TypeSystemLoader(name, null, this);
    }

    /**
     * Create a new TypeSystem.
     *
     * @param xvm            the XVM
     * @param fileStructure  the resolved FileStructure containing all the resolved ModuleStructures
     *                       for the TypeSystem
     * @param sharedLoaders  the ModuleLoaders for the shared-in modules
     */
    TypeSystem(Xvm xvm, FileStructure fileStructure, ModuleLoader[] sharedLoaders) {
        require("xvm"          , xvm);
        require("fileStructure", fileStructure);

        this.xvm           = xvm;
        this.fileStructure = fileStructure;
        this.name          = xvm.generateTypeSystemName(this);
        this.loader        = new TypeSystemLoader(name, sharedLoaders, this);

        // create the modules that are being loaded by this TypeSystem
        for (ModuleConstant moduleId : fileStructure.moduleIds()) {
            String prefix = xvm.generateModulePackagePrefix(moduleId);
            loader.includeModule(fileStructure.getModule(moduleId), prefix);
        }
        loader.start();
    }

    /**
     * The XVM within which this TypeSystem exists
     */
    final Xvm xvm;

    /**
     * The unique TypeSystem name (used to provide a name for the ClassLoader).
     */
    final String name;

    /**
     * The linked type system, as represented by a FileStructure containing the resolved modules.
     */
    final FileStructure fileStructure;

    /**
     * The ClassLoader responsible for providing the JVM with the classes necessary to implement the
     * TypeSystem.
     */
    final TypeSystemLoader loader;

    /**
     * A Module Refiner is the "decision maker" for selecting among various version, dependency, and
     * conditional-define options for Module(s) being fed to a Linker.
     */
    public interface Refiner {
        /**
         * Select a Version of a Module to use. An implementation may choose to throw an Exception
         * or return `null` to indicate that none of the available Module Versions is acceptable,
         * which will kill the linking process.
         *
         * @param module    the identifier of the Module (never null)
         * @param versions  the available Module Versions that do not conflict with previously
         *                  discovered Version constraints (never null, never empty)
         * @param prefs     a list of preferred versions (never null, may be empty)
         *
         * @return the Version of the Module to use
         */
        default Version whichVersion(ModuleConstant module, VersionTree versions, List<Version> prefs) {
            require("module"  , module);
            require("versions", versions);
            require("prefs"   , prefs);

            Version ver = versions.findHighestVersion();
            if (ver.isGARelease()) {
                return ver;
            }

            // select the highest GA version, or failing that, the highest version closest to GA
            Version[] best = new Version[6];
            for (Iterator<Version> iter = versions.iterator(); iter.hasNext(); ) {
                ver = iter.next();
                best[-ver.getReleaseCategory()] = ver;
            }
            for (int i = 0, c = best.length; i < c; ++i) {
                ver = best[i];
                if (ver != null) {
                    return ver;
                }
            }
            throw new IllegalStateException();
        }

        /**
         * Make a decision whether to define or exclude the specified name.
         *
         * @param name  a condition name that can be defined or not (never null)
         *
         * @return `true` to define the name; `false` to make the name _not defined_
         */
        default boolean shouldDefine(String name) {
            require("name", name);
            return false;
        }

        /**
         * Determine if the specified Module dependency should be used or excluded.
         *
         * @param module   the depended-upon Module (never null)
         * @param desired  `true` iff the dependency is marked as "desired"
         *
         * @return `true` iff the dependency should be used; `false` to _disallow_ the dependency
         */
        default boolean shouldUse(ModuleConstant module, boolean desired) {
            require("module", module);
            return desired;
        }
    }

    /**
     * The default Refiner instance.
     */
    public static final Refiner DefaultRefiner = new Refiner() {};

    /**
     * Create a Linker in order to create a new TypeSystem.
     *
     * @return a new Linker
     */
    public Linker create() {
        ModuleLoader ecstasyLoader = null;
        for (ModuleLoader loader : loader.loaders) {
            if (loader.module.getName().equals(ECSTASY_MODULE)) {
                ecstasyLoader = loader;
                break;
            }
        }
        if (ecstasyLoader == null) {
            throw new IllegalArgumentException("missing required core Ecstasy module");
        }
        return new Linker().addSharedModule(ecstasyLoader);
    }

    /**
     * A TypeSystem Linker is the "builder" for new TypeSystems.
     */
    public class Linker {
        /**
         * Hidden constructor.
         */
        Linker() {}

        /**
         * The ModuleLoaders for the modules that are being shared into the new TypeSystem.
         */
        private ArrayList<ModuleLoader> sharedLoaders = new ArrayList<>();

        /**
         * Defined names for the modules that are being loaded/resolved in the new TypeSystem.
         */
        private Map<String, Boolean> defines;

        /**
         * The ModuleRepository to load modules from, if necessary.
         */
        private ModuleRepository repo;

        /**
         * The Refiner for this Linker to use when Module-related decisions are required.
         */
        private Refiner refiner = DefaultRefiner;

        /**
         * These are the modules that have been added to the Linker. They are potentially unrefined,
         * which means that the linking step would need to ensure that each of these is refined down
         * to a single version with all dependencies resolved (particularly making decisions on
         * optional dependencies) and with all conditionals resolved (version-related,
         * dependency-related, and named conditions).
         */
        private ArrayList<ModuleStructure> modules = new ArrayList<>();

        /**
         * A structure for collecting information, warnings, and errors from the linking process.
         */
        private ErrorList errors = new ErrorList(24);

        /**
         * Internal cache: Set to `true` once a serious Error has been spotted.
         */
        private boolean bad = false;

        /**
         * Add a single shared module to the TypeSystem being built.
         *
         * @param loader  the ModuleLoader for the existing module to share into the new TypeSystem
         *
         * @return the Linker
         */
        public Linker addSharedModule(ModuleLoader loader) {
            require("loader", loader);
            if (!isBad()) {
                // TODO GG review: how does the shared module get associated with an Ecstasy module
                //                 import? (especially in the db v1 vs db v2 example?)
                if (!sharedLoaders.contains(loader)) {
                    sharedLoaders.add(loader);
                }
            }
            return this;
        }

        /**
         * Share all modules from the specified TypeSystem into the TypeSystem being created.
         *
         * @param typeSystem  a TypeSystem to share all modules from
         *
         * @return the Linker
         */
        public Linker addSharedModules(TypeSystem typeSystem) {
            require("typeSystem", typeSystem);
            return addSharedModules(typeSystem.loader);
        }

        /**
         * Share all modules from the provided TypeSystemLoader into the TypeSystem being created.
         *
         * @param loader  a TypeSystemLoader
         *
         * @return the Linker
         */
        public Linker addSharedModules(TypeSystemLoader loader) {
            require("loader", loader);
            for (ModuleLoader module : loader.loaders) {
                addSharedModule(module);
            }
            return this;
        }

        /**
         * Specify a "condition name" for the modules that will be loaded and resolved.
         *
         * This must be called BEFORE specifying the main module or any additional modules of the
         * TypeSystem, otherwise the Refiner will be used to determine the definition of any
         * unspecified names.
         *
         * @param name     the name to define (or mark as undefined)
         * @param defined  `true` to define the name; `false` to mark the names as undefined
         *
         * @return the Linker
         */
        public Linker define(String name, boolean defined) {
            require("name", name);
            if (defines.containsKey(name) && defined != defines.get(name)) {
                if (modules.isEmpty()) {
                    // TODO log warning
                } else {
                    // TODO log error
                }
            }
            defines.put(name, defined);
            return this;
        }

        /**
         * Specify the "condition names" for the modules that will be loaded and resolved.
         *
         * This must be called BEFORE specifying the main module or any additional modules of the
         * TypeSystem that depend on these names, otherwise the Refiner will be used to determine
         * the definition of any unspecified names.
         *
         * @param names    the names to define (or mark as undefined)
         * @param defined  `true` to define the name; `false` to mark the names as undefined
         *
         * @return the Linker
         */
        public Linker define(Collection<String> names, boolean defined) {
            require("names", names);
            for (String name : names) {
                define(name, defined);
            }
            return this;
        }

        /**
         * Determine if a name is defined within this Linker.
         *
         * @param name  the name to test for
         *
         * @return true iff the specified name is defined within this Linker
         */
        public boolean specified(String name) {
            require("name", name);
            return defines != null && defines.containsKey(name);
        }

        /**
         * Determine if a name is defined within this Linker.
         *
         * @param name  the name to test for
         *
         * @return true iff the specified name is defined within this Linker
         */
        public boolean defined(String name) {
            require("name", name);
            return defines != null && defines.get(name) == Boolean.TRUE;
        }

        /**
         * Determine if a name is defined within this Linker.
         *
         * @param name  the name to test for
         *
         * @return true iff the specified name is defined within this Linker
         */
        public boolean undefined(String name) {
            require("name", name);
            return defines != null && defines.get(name) == Boolean.FALSE;
        }

        /**
         * Obtain a read-only Map of all defined (or marked-as-undefined) names within this Linker.
         *
         * @return a Map from name to its defined/undefined setting, for each name defined (or
         *         marked as undefined) within this Linker
         */
        public Map<String, Boolean> defines() {
            return defines == null ? Collections.emptyMap() : Collections.unmodifiableMap(defines);
        }

        /**
         * From this point forward, the Linker will attempt to load modules from the provided
         * ModuleRepository if it is necessary to load any modules. This ModuleRepository will
         * replace any previously specified ModuleRepository.
         *
         * @param repo  the ModuleRepository to use to load necessary modules (not null)
         *
         * @return the Linker
         */
        public Linker withRepo(ModuleRepository repo) {
            require("repo", repo);
            this.repo = repo;
            return this;
        }

        /**
         * @return the ModuleRepository that the Linker will use to load a Module if necessary, or
         *         null
         */
        public ModuleRepository repo() {
            return repo;
        }

        /**
         * From this point forward, the Linker will use the specified Refiner to make necessary
         * decisions in the process of refining module choices regarding versions, dependencies,
         * and conditional definitions.
         *
         * @param refiner  the Refiner to use (not null)
         *
         * @return the Linker
         */
        public Linker withRefiner(Refiner refiner) {
            require("Refiner", refiner);
            this.refiner = refiner;
            return this;
        }

        /**
         * @return the Refiner that the Linker will use to refine a Module if necessary, or
         *         null
         */
        public Refiner refiner() {
            return refiner;
        }

        /**
         * TODO
         *
         * @param moduleName
         *
         * @return
         */
        ModuleStructure loadOrFindModule(String moduleName) {
            // TODO
            return null;
        }

        /**
         * Specify a main module for the TypeSystem.
         *
         * @param moduleName  the fully qualified name of the main module
         *
         * @return the Linker
         */
        public Linker withMain(String moduleName) {
            require("moduleName", moduleName);
            if (!isBad()) {
                if (modules.isEmpty()) {
                    ModuleStructure module = loadOrFindModule(moduleName);
                    if (module == null) {
                        // TODO log error - could not load or find
                    } else {
                        return withMain(module);
                    }
                } else {
                    // TODO log error - main module already provided
                }
            }
            return this;
        }

        /**
         * Specify a main module for the TypeSystem.
         *
         * @param module  the main module
         *
         * @return the Linker
         */
        public Linker withMain(ModuleStructure module) {
            require("module", module);
            if (!isBad()) {
                if (modules.isEmpty()) {
                    // TODO various checks?
                    modules.add(module);
                } else {
                    // TODO log error
                }
            }
            return this;
        }

        /**
         * Specify an additional module for the TypeSystem.
         *
         * @param moduleName  the fully qualified name of the module
         *
         * @return the Linker
         */
        public Linker addModule(String moduleName) {
            require("moduleName", moduleName);
            if (!isBad()) {
                if (modules.isEmpty()) {
                    return withMain(moduleName);
                }

                ModuleStructure module = loadOrFindModule(moduleName);
                if (module == null) {
                    // TODO log error - could not load or find
                } else {
                    return addModule(module);
                }
            }
            return this;
        }

        /**
         * Specify an additional module for the TypeSystem.
         *
         * @param module  the module to add
         *
         * @return the Linker
         */
        public Linker addModule(ModuleStructure module) {
            require("module", module);
            if (!isBad()) {
                if (modules.isEmpty()) {
                    return withMain(module);
                }

                // TODO various checks? e.g. duplicate?
                modules.add(module);
            }
            return this;
        }

        /**
         * Obtain all information, warnings, and errors logged thus far by the linker.
         *
         * @return a non-null ErrorList, which may be empty
         */
        public ErrorList errorList() {
            if (errors.hasErrors()) {
                bad = true;
            }
            return errors;
        }

        /**
         * @return `true` iff the Linker has encountered an issue of {@link org.xvm.util.Severity}
         *         of `ERROR` or worse, which means that a new TypeSystem cannot be produced
         */
        public boolean isBad() {
            return bad || errorList().hasErrors();
        }

        /**
         * Link the provided modules into a new TypeSystem.
         *
         * @return the new TypeSystem
         *
         * @throws IllegalStateException  if the Linker has encountered any errors up to this point,
         *         or encounters an error while performing the module linking
         */
        public TypeSystem link() {
            if (isBad()) {
                throw new IllegalStateException(errorList().getSeriousErrorCount()
                        + " serious errors were encountered before attempting to link");
            }

            FileStructure  newFS  = null;
            // TODO check for an existing type system with the same exact contents
            // TODO perform linking

            if (isBad()) {
                throw new IllegalStateException(errorList().getSeriousErrorCount()
                        + " serious errors were encountered during module linking");
            }

            return new TypeSystem(xvm, newFS, sharedLoaders.toArray(new ModuleLoader[0]));
        }
    }

    /**
     * @return the Java ClassLoader for the TypeSystem
     */
    public TypeSystemLoader loader() {
        return loader;
    }

    /**
     * @return the Java class for the Ecstasy module class from the main module
     */
    public Class getMainModule() {
        return null; // TODO loader.loadClass(moduleName);
    }

    /**
     * Create a Java ClassFile for the specified class name. The name follows a very exact
     * convention for how it is constructed, and that convention is well understood by the
     * TypeSystem (because that name was created by the TypeSystem).
     *
     * @param module  the ModuleStructure that contains the structure information for the class
     * @param prefix  the Java package prefix used for the module
     * @param name    the full Java class name to create
     *
     * @return the bytes of the ClassFile for the specified class name
     */
    public byte[] genClass(ModuleStructure module, String prefix, String name) {
        // TODO
        return null;
    }

}

