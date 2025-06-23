package org.xvm.javajit;


import java.util.ArrayList;

import java.util.Set;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.NATIVE_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;
import static org.xvm.javajit.Xvm.StructureByModuleId;
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

        ModuleStructure ecstasy = repo.loadModule(ECSTASY_MODULE);
        ModuleStructure turtle  = repo.loadModule(TURTLE_MODULE);
        ModuleStructure _native = repo.loadModule(NATIVE_MODULE);

        if (ecstasy == null || turtle == null || _native == null) {
            throw new IllegalStateException("missing core module");
        }

        if (!ecstasy.isRefined() || !turtle.isRefined() || !_native.isRefined()) {
            throw new IllegalStateException("unrefined core module");
        }

        FileStructure fs = new FileStructure(ecstasy, true);
        fs.merge(turtle, true, false);
        fs.merge(_native, true, false);
        ModuleConstant missing = fs.linkModules(repo, true);
        if (missing != null) {
            throw new IllegalStateException("missing core module: " + missing.getName());
        }

        ConstantPool pool = fs.getConstantPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            if (pool.getNakedRefType() == null) {
                turtle = fs.getChild(TURTLE_MODULE);
                ClassStructure clzNakedRef = (ClassStructure) turtle.getChild("NakedRef");
                pool.setNakedRefType(clzNakedRef.getFormalType());
            }
        }

        // build a list of modules that will compose the core aka native TypeSystem
        ArrayList<ModuleStructure> list = new ArrayList<>(fs.children());
        list.sort(StructureByModuleId);
        ModuleLoader[]    shared = new ModuleLoader[0];
        ModuleStructure[] owned  = list.toArray(new ModuleStructure[0]);

        this.xvm    = xvm;
        this.name   = xvm.generateTypeSystemName(shared, owned);
        this.loader = new TypeSystemLoader(this, name, shared, owned);
        this.shared = loader.shared;
        this.owned  = loader.owned;
    }

    /**
     * Create a new TypeSystem. This should ONLY be called from the Linker.
     *
     * @param xvm     the XVM
     * @param shared  the loaders that are "shared into" this TypeSystem from other TypeSystems
     *                and that can be delegated to in order to generate and load code
     * @param owned   the modules that this TypeSystem is responsible for generating code for
     */
    TypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        require("xvm", xvm);
        require("shared", shared);
        require("owned", owned);

        this.xvm    = xvm;
        this.name   = xvm.generateTypeSystemName(shared, owned);
        this.loader = new TypeSystemLoader(this, name, shared, owned);
        this.shared = loader.shared;
        this.owned  = loader.owned;
    }

    /**
     * The XVM within which this TypeSystem exists
     */
    public final Xvm xvm;

    /**
     * The unique TypeSystem name (used to provide a name for the ClassLoader).
     */
    public final String name;

    /**
     * The ClassLoader responsible for providing the JVM with the classes necessary to implement the
     * TypeSystem.
     */
    public final TypeSystemLoader loader;

    /**
     * The ModuleLoaders that are "shared into" this TypeSystem from other TypeSystems and that can
     * be delegated to in order to generate and load code.
     */
    public final ModuleLoader[] shared;

    /**
     * The ModuleLoaders that this TypeSystem is responsible for generating and loading code for.
     */
    public final ModuleLoader[] owned;

    /**
     * Create a Linker in order to create a new TypeSystem that is an extension of this type system.
     *
     * @return a new Linker, primed with the contents of this TypeSystem
     */
    public Linker create() {
        return xvm.createLinker().addSharedModules(this);
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

    enum ClassfileShape {
        Impl,
        Pure,       // i$
        Proxy,      // p$
        Duck,       // d$
        Mask,       // m$
        Future,     // f$
    }

    record Artifact(IdentityConstant id, ClassfileShape shape) {};

    Artifact deduceArtifact(String name) {
        // TODO
        return null;
    }

    Artifact deduceArtifact(ModuleStructure module, String prefix, String name) {
        // TODO
        return null;
    }

    Set<ClassfileShape> expectedClassfileShape(Component component) {
        // TODO
        return null;
    }

    ModuleLoader loaderForComponent(IdentityConstant id) {
        // TODO
        return null;
    }

    String classfileNameForComponent(IdentityConstant id, ClassfileShape shape) {
        // TODO
        return null;
    }
}



