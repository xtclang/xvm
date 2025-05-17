package org.xvm.javajit;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

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
 * and that's it. But creating a TypeSystem is potentially an iterative and recursive process, which
 * at each stage must resolve significant questions:
 *
 * * Each module has a set of modules that it depends upon, and those dependencies can be required
 *   or optional, and in the case of optional dependencies, the "optional" indicator means only that
 *   the dependency is explicitly supported, while the "desired" indicator implies that the
 *   dependency _should_ be used if it's available; in both cases, there is an inclusion decision to
 *   be made.
 *
 * * Furthermore, each yet-to-be-loaded module may have any number of versions available, so if the
 *   decision is made to load that module (either because it's required -- which implies a decision
 *   -- or if it's optional and an explicit decision is made to load it), then a further decision
 *   must be made as to which version of that module should be used. The additional dependencies
 *   that the yet-to-be-loaded module introduces can vary across its versions, so until the version
 *   to load has been decided, the additional dependencies (including optional dependencies to
 *   decide upon) are unknown.
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
 *   consistent (in terms of dependencies) with the modules in the new TypeSystem.
 *
 * * Modules can also contain conditional elements predicated on a set of defined names (see the
 *   Ecstasy `@Iff` annotation), such as "test" (see `@Test`) and "debug" (see `@Debug`). These
 *   names must be specified before any modules are loaded; all modules loaded will be resolved
 *   using the same set of defined names.
 *
 * * After all shared Modules and condition names have been specified, then the main Module can be
 *   specified.
 *
 * * After the main Module has been specified, then additional Modules can be specified.
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
        this.xvm = xvm;
        // TODO hand build the "container -1" type system using ecstasy.xtclang.org etc.
        // load ECSTASY_MODULE ecstasy.xtclang.org
        // load TURTLE_MODULE mack.xtclang.org
        // load NATIVE_MODULE _native.xtclang.org ?????
        this.fileStructure = null;
        this.loader        = null;
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
        String name = ""; // TODO
        this.xvm           = xvm;
        this.fileStructure = fileStructure;
        this.loader        = new TypeSystemLoader(name, sharedLoaders, this);
        for (ModuleConstant moduleId : fileStructure.moduleIds()) {
            String prefix = ""; // TODO
            loader.includeModule(fileStructure.getModule(moduleId), prefix);
        }
        loader.start();
    }

    /**
     * The XVM within which this TypeSystem exists
     */
    final Xvm xvm;

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
        private ArrayList<ModuleLoader> sharedLoaders;

        /**
         * Defined names for the modules that are being loaded/resolved in the new TypeSystem.
         */
        private Set<String> defines;

        /**
         * The ModuleRepository to load modules from, if necessary.
         */
        private ModuleRepository repo;

        /**
         * TODO
         */
        private ArrayList<ModuleStructure> modules;

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
         * @param module  the identity of the existing module to share into the new TypeSystem
         *
         * @return the Linker
         */
        public Linker addSharedModule(ModuleConstant module) {
            require("module", module);
            ModuleLoader loader = null; // TODO get the ModuleLoader from the parent TypeSystem
            return addSharedModule(loader);
        }

        /**
         * Add a single shared module to the TypeSystem being built.
         *
         * @param loader  the ModuleLoader for the existing module to share into the new TypeSystem
         *
         * @return the Linker
         */
        public Linker addSharedModule(ModuleLoader loader) {
            require("loader", loader);
            // TODO
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
         * TODO
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
         * TypeSystem.
         *
         * @param name  the name to define
         *
         * @return the Linker
         */
        public Linker define(String name) {
            require("name", name);
            // TODO check that we can still define names
            defines.add(name);
            return this;
        }

        /**
         * Specify the "condition names" for the modules that will be loaded and resolved.
         *
         * This must be called BEFORE specifying the main module or any additional modules of the
         * TypeSystem.
         *
         * @param names  the names to define
         *
         * @return the Linker
         */
        public Linker define(Collection<String> names) {
            require("names", names);
            for (String name : names) {
                define(name);
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
        boolean defined(String name) {
            require("name", name);
            return defines != null && defines.contains(name);
        }

        /**
         * Obtain a read-only Set of all defined names within this Linker.
         *
         * @return the names defined within this Linker
         */
        Set<String> defines() {
            return defines == null ? Collections.emptySet() : Collections.unmodifiableSet(defines);
        }

        /**
         * From this point forward, the Linker will attempt to load modules from the provided
         * ModuleRepository if it is necessary to load any modules. This ModuleRepository will
         * replace any previously specified ModuleRepository.
         *
         * @param repo TODO (must not be null)
         *
         * @return the Linker
         */
        Linker withRepo(ModuleRepository repo) {
            require("repo", repo);
            this.repo = repo;
            return this;
        }

        ModuleRepository repo() {
            return repo;
        }

        /**
         * Obtain all information, warnings, and errors logged thus far by the linker.
         *
         * @return a non-null ErrorList, which may be empty
         */
        public ErrorList errorList() {
            // just in case the
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



    public TypeSystemLoader ensureClassLoader() {
        // TODO
        return null; // new TypeSystemLoader(...);
    }

    public Class getMainModule() {
        // TODO something like: ensureClassLoader().loadClass(moduleName)
        return null;
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

