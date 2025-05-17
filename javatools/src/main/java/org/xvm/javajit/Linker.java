package org.xvm.javajit;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import static org.xvm.javajit.Refiner.DefaultRefiner;
import static org.xvm.util.Handy.require;


/**
 * A TypeSystem Linker is the "builder" for new TypeSystems.
 */
public class Linker {
    /**
     * @param xvm  the Xvm that this Linker will link and create a TypeSystem for
     */
    Linker(Xvm xvm) {
        this.xvm = xvm;
    }

    /**
     * The Xvm that this Linker is building a TypeSystem for.
     */
    private final Xvm xvm;

    /**
     * The ModuleLoaders for the modules that are being shared into the new TypeSystem.
     */
    private final ArrayList<ModuleLoader> sharedLoaders = new ArrayList<>();

    /**
     * Defined names for the modules that are being loaded/resolved in the new TypeSystem.
     */
    private final Map<String, Boolean> defines = new HashMap<>();

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
    private final ArrayList<ModuleStructure> modules = new ArrayList<>();

    /**
     * A structure for collecting information, warnings, and errors from the linking process.
     */
    private final ErrorList errors = new ErrorList(24);

    /**
     * Internal cache: Set to `true` once a serious Error has been spotted.
     */
    private boolean bad = false;

    /**
     * Add a single shared module to the TypeSystem being built.
     *
     * @param loader the ModuleLoader for the existing module to share into the new TypeSystem
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
     * @param typeSystem a TypeSystem to share all modules from
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
     * @param loader a TypeSystemLoader
     *
     * @return the Linker
     */
    public Linker addSharedModules(TypeSystemLoader loader) {
        require("loader", loader);
        for (ModuleLoader module : loader.owned) {
            addSharedModule(module);
        }
        return this;
    }

    /**
     * Specify a "condition name" for the modules that will be loaded and resolved.
     * <p>
     * This must be called BEFORE specifying the main module or any additional modules of the
     * TypeSystem, otherwise the Refiner will be used to determine the definition of any
     * unspecified names.
     *
     * @param name    the name to define (or mark as undefined)
     * @param defined `true` to define the name; `false` to mark the names as undefined
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
     * <p>
     * This must be called BEFORE specifying the main module or any additional modules of the
     * TypeSystem that depend on these names, otherwise the Refiner will be used to determine
     * the definition of any unspecified names.
     *
     * @param names   the names to define (or mark as undefined)
     * @param defined `true` to define the name; `false` to mark the names as undefined
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
     * @param name the name to test for
     *
     * @return true iff the specified name is defined within this Linker
     */
    public boolean specified(String name) {
        require("name", name);
        return defines.containsKey(name);
    }

    /**
     * Determine if a name is defined within this Linker.
     *
     * @param name the name to test for
     *
     * @return true iff the specified name is defined within this Linker
     */
    public boolean defined(String name) {
        require("name", name);
        return defines.get(name) == Boolean.TRUE;
    }

    /**
     * Determine if a name is defined within this Linker.
     *
     * @param name the name to test for
     *
     * @return true iff the specified name is defined within this Linker
     */
    public boolean undefined(String name) {
        require("name", name);
        return defines.get(name) == Boolean.FALSE;
    }

    /**
     * Obtain a read-only Map of all defined (or marked-as-undefined) names within this Linker.
     *
     * @return a Map from name to its defined/undefined setting, for each name defined (or
     *     marked as undefined) within this Linker
     */
    public Map<String, Boolean> defines() {
        return Collections.unmodifiableMap(defines);
    }

    /**
     * From this point forward, the Linker will attempt to load modules from the provided
     * ModuleRepository if it is necessary to load any modules. This ModuleRepository will
     * replace any previously specified ModuleRepository.
     *
     * @param repo the ModuleRepository to use to load necessary modules (not null)
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
     *     null
     */
    public ModuleRepository repo() {
        return repo;
    }

    /**
     * From this point forward, the Linker will use the specified Refiner to make necessary
     * decisions in the process of refining module choices regarding versions, dependencies,
     * and conditional definitions.
     *
     * @param refiner the Refiner to use (not null)
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
     *     null
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
     * @param moduleName the fully qualified name of the main module
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
     * @param module the main module
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
     * @param moduleName the fully qualified name of the module
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
     * @param module the module to add
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
     *     of `ERROR` or worse, which means that a new TypeSystem cannot be produced
     */
    public boolean isBad() {
        return bad || errorList().hasErrors();
    }

    /**
     * Link the provided modules into a new TypeSystem.
     *
     * @return the new TypeSystem, or null if the link failed (which indicates that information
     *     should be available in the Linker's {@link #errorList()})
     */
    public TypeSystem link() {
        if (isBad()) {
            return null;
        }

        ModuleStructure[] owned = null;
        // TODO check for an existing type system with the same exact contents
        // TODO perform linking
        // TODO register new type system

        if (isBad()) {
            return null;
        }

        return xvm.ensureTypeSystem(sharedLoaders.toArray(new ModuleLoader[0]), owned);
    }
}
