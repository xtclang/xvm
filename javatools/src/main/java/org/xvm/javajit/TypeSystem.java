package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.classfile.attribute.SourceFileAttribute;

import java.lang.constant.ClassDesc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.builders.CommonBuilder;
import org.xvm.javajit.builders.ModuleBuilder;

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
        this.name   = xvm.generateTypeSystemName(shared, owned, this instanceof NativeTypeSystem);
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
     * A cache of builders classes keyed by type.
     */
    protected final Map<TypeConstant, Class> buildersByType = new ConcurrentHashMap<>();

    /**
     * @return the ConstantPool associated with this TypeSystem
     */
    public ConstantPool pool() {
        // TODO should there be a separate ConstantPool created for this type system when there are only shared modules? i.e. is there a FileStructure?
        return owned.length == 0 ? xvm.ecstasyPool /* <-- TODO wrong */ : owned[0].module.getConstantPool();
    }

    /**
     * Find the ConstantPool that "owns" the specified signature.
     */
    public ConstantPool findOwnerPool(SignatureConstant sig) {
        ConstantPool thisPool = pool();

        // try to go "up" towards the root Ecstasy type system to see if anyone "above" us fully
        // "knows" about the specified constant
        for (ModuleLoader loader : shared) {
            ConstantPool thatPool = loader.module.getConstantPool();
            if (thatPool != thisPool && sig.isShared(thatPool)) {
                return loader.typeSystem.findOwnerPool(sig);
            }
        }

        // if we can't go up, then we have to fully "know" the constant (i.e. there is no "down")
        assert sig.isShared(thisPool);
        return thisPool;
    }

    /**
     * Find the ModuleLoader that "owns" (i.e. will assemble the class for) the specified type.
     *
     * @param type  a type with {@link TypeConstant#isSingleUnderlyingClass single underlying class}
     */
    public ModuleLoader findOwnerLoader(TypeConstant type) {
        ConstantPool thisPool = pool();

        // try to go "up" towards the root Ecstasy type system to see if anyone "above" us fully
        // "knows" about the specified constant
        for (ModuleLoader loader : shared) {
            ConstantPool thatPool = loader.module.getConstantPool();
            if (thatPool != thisPool && type.isShared(thatPool)) {
                return loader.typeSystem.findOwnerLoader(type);
            }
        }

        // if we can't go up, then we have to fully "know" the constant (i.e. there is no "down")
        assert type.isShared(thisPool);

        // select the module which is responsible for assembling the class for the type
        IdentityConstant id     = type.getSingleUnderlyingClass(true);
        ModuleConstant   module = id.getModuleConstant();

        for (ModuleLoader loader : owned) {
            if (loader.module.getIdentityConstant().equals(module)) {
                return loader;
            }
        }

        throw new IllegalStateException("No owner loader for " + type);
    }

    /**
     * Find the ModuleLoader that "owns" (i.e. will assemble the class for) the specified class name.
     */
    public ModuleLoader findOwnerLoader(String name) {
        for (ModuleLoader loader : owned) {
            if (name.startsWith(loader.prefix)) {
                return loader;
            }
        }

        for (ModuleLoader loader : shared) {
            if (name.startsWith(loader.prefix)) {
                return loader;
            }
        }
        return null;
    }

    /**
     * @return the main ModuleStructure associated with this TypeSystem
     */
    public ModuleStructure mainModule() {
        return owned.length == 0 ? null : owned[0].module;
    }

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
     * @param moduleLoader the ModuleLoader that contains the structure information for the
     *                     containing module and all its classes
     * @param name         the suffix of the Java class name to create (sans the module prefix)
     *
     * @return the bytes of the ClassFile for the specified class name
     */
    public byte[] genClass(ModuleLoader moduleLoader, String name) {
        Artifact art = deduceArtifact(moduleLoader.module, name);
        if (art != null) {
            if (art.id.getComponent() instanceof ClassStructure clz) {
                String       className = moduleLoader.prefix + name;
                TypeConstant type      = clz.getCanonicalType();
                Builder      builder   = ensureBuilder(type);
                Consumer<? super ClassBuilder> handler = cb -> {
                    cb.with(SourceFileAttribute.of(clz.getSourceFileName()));
                    switch (art.shape) {
                        case Impl:
                            builder.assembleImpl(className, cb);
                            break;

                        default:
                            throw new UnsupportedOperationException();
                    }
                };

                return ClassFile.of().
                    build(ClassDesc.of(className), handler);
            }
        }
        return null;
    }

    /**
     * @return a builder for the specified type
     */
    protected Builder ensureBuilder(TypeConstant type) {
        ConstantPool pool = pool();
        if (type.isA(pool.typeModule())) {
            // it's definitely not Module, since this is not the native TypeSystem
            assert !type.equals(pool.typeModule());
            return new ModuleBuilder(this, type);
        }
        return new CommonBuilder(this, type);
    }

    /**
     * Jit class shapes.
     */
    public enum ClassfileShape {
        Pure  ("i$"),
        Proxy ("p$"),
        Duck  ("d$"),
        Mask  ("m$"),
        Future("f$"),
        Impl  (""), // needs to be last
        ;

        ClassfileShape(String prefix) {
            this.prefix = prefix;
        }

        public final String prefix;
    }

    public record Artifact(IdentityConstant id, ClassfileShape shape) {}

    public Artifact deduceArtifact(ModuleStructure module, String name) {
        if (name.equals("$module")) {
            return new Artifact(module.getIdentityConstant(), ClassfileShape.Impl);
        }

        for (ClassfileShape shape : ClassfileShape.values()) {
            if (name.startsWith(shape.prefix)) {
                if (shape != ClassfileShape.Impl) {
                    name = name.substring(2); // all other suffixes are of the length 2
                }
                if (module.getChildByPath(name) instanceof ClassStructure struct) {
                    return new Artifact(struct.getIdentityConstant(), shape);
                }
            }
        }
        return null;
    }

    public Artifact deduceArtifact(ModuleStructure module, String prefix, String name) {
        // TODO
        return null;
    }

    public Set<ClassfileShape> expectedClassfileShape(Component component) {
        // TODO
        return null;
    }

    public ModuleLoader loaderForComponent(IdentityConstant id) {
        // TODO
        return null;
    }

    public String classfileNameForComponent(IdentityConstant id, ClassfileShape shape) {
        // TODO
        return null;
    }

    /**
     * Ensure a unique Java class name for the specified Ecstasy type.
     */
    public String ensureJitClassName(TypeConstant type) {
        if (type.isSingleUnderlyingClass(true)) {
            ClassStructure structure = (ClassStructure)
                type.getSingleUnderlyingClass(true).getComponent();

            List<Contribution> condIncorporates = structure.collectConditionalIncorporates(type);
            TypeConstant       canonicalType;
            if (condIncorporates == null) {
                canonicalType = structure.getCanonicalType();
            } else {
                // TODO: implement conditional class name computation
                // System.err.println("Not implemented: conditional incorporates for " + type);
                canonicalType = structure.getCanonicalType();
            }

            return canonicalType.ensureJitClassName(this);
        }
        throw new IllegalArgumentException("No JIT class for " + type.getValueString());
    }
}



