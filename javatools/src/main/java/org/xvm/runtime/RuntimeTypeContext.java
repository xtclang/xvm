package org.xvm.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.FrameDependentConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

/**
 * Canonical runtime descriptors for one prepared image and its linked dependencies.
 *
 * <p>Create the context after linking and native preparation, then retain it with its container.
 * Its dependency graph is captured by object identity: recompiling a module with the same name
 * does not make its descriptors interchangeable. Import an image type with {@link #intern} before
 * deriving another type from it. Do not use this context for foreign types or captured values.
 *
 * <p>The descriptor pool is a transitional adapter to existing constant factories. It has no XTC
 * indices and is not attached as a child of the image. Derived descriptors can grow without
 * extending the image's constant table. Metadata has not yet been separated from
 * {@link TypeConstant}; consequently this context must not be shared between containers.
 */
public final class RuntimeTypeContext {
    /**
     * Create a context over a prepared image without changing its declarations or constant table.
     *
     * @param definitions  the prepared image's constant pool, with its final dependency graph
     */
    public RuntimeTypeContext(ConstantPool definitions) {
        if (!definitions.hasSerializedIndices()) {
            throw new IllegalArgumentException("Runtime type context requires an indexed definition image");
        }
        descriptors = new DescriptorPool(definitions);
    }

    /**
     * Make this context's exact definition graph read-only while leaving its descriptor store
     * growable. Constant-table membership and indices, module links and guarded declarations can
     * no longer change. New type combinations must be constructed through this context.
     *
     * <p>Call after linking and structural native preparation, with exclusive access to the
     * definitions and before publishing them to execution threads. This is permanent for those
     * image objects; compilation or re-linking requires a mutable copy and a new context. A failed
     * transition can leave some files read-only, so do not publish a graph after failure.
     *
     * <p>This is an enforcement boundary for the ongoing migration, not a claim of universal
     * interpreter support. Unmigrated runtime paths that register in the image will fail hereafter.
     * Metadata caches and mutable executable state have separate lifetimes and are not made
     * shareable by this operation.
     */
    public void freezeDefinitions() {
        descriptors.definitions.forEach(pool -> pool.getFileStructure().ensureReadOnly());
    }

    /**
     * Import a type from this image or one of its exact linked dependencies.
     *
     * @param type  an image type or a descriptor already owned by this context
     * @return the context's canonical descriptor; its position is always {@code -1}
     * @throws IllegalArgumentException if the type refers to another image generation or context
     */
    public TypeConstant intern(TypeConstant type) {
        return descriptors.register(type);
    }

    /**
     * Construct a parameterized descriptor without registering anything in the image.
     *
     * @param type        the unparameterized type
     * @param parameters  the type arguments, from this context or its image graph
     * @return the canonical parameterized descriptor
     */
    public TypeConstant parameterize(TypeConstant type, TypeConstant... parameters) {
        TypeConstant base = intern(type);
        var arguments = Arrays.stream(parameters).map(this::intern).toArray(TypeConstant[]::new);
        return descriptors.ensureParameterizedTypeConstant(base, arguments);
    }

    /**
     * Apply reflective type arguments, including the language's normalization of omitted arguments.
     * Import all operands before invoking the existing type algebra, and retain the result here
     * even when that algebra returns an unchanged operand.
     *
     * @param type        the type being parameterized
     * @param parameters  the reflected type arguments from this image or context
     * @return the normalized descriptor owned by this context
     */
    public TypeConstant adoptParameters(TypeConstant type, TypeConstant... parameters) {
        TypeConstant base = intern(type);
        var arguments = Arrays.stream(parameters).map(this::intern).toArray(TypeConstant[]::new);
        return intern(base.adoptParameters(descriptors, arguments));
    }

    /**
     * Compare types in this definition context. Import before even equality or Object shortcuts,
     * so equal names from another generation cannot bypass the ownership check.
     *
     * @param source       the type of the value being assigned
     * @param destination  the required type
     * @return assignability in this context
     */
    public TypeConstant.Relation calculateRelation(TypeConstant source, TypeConstant destination) {
        return intern(source).calculateRelation(intern(destination));
    }

    /**
     * Discard completed relation results without changing descriptor identities, metadata or
     * execution state. Subsequent queries recompute in the same fixed definition context.
     */
    public void clearRelations() {
        descriptors.getTypeRelations().clear();
    }

    /**
     * Obtain the factory adapter for runtime code generation. Import operands through
     * {@link ConstantPool#register} before calling factories, which may return an operand directly.
     * Use a method's local constant registry for code references; image-index lookup and assembly
     * are deliberately unsupported. This is not the container's compiled constant pool.
     *
     * @return this context's descriptor store
     */
    public ConstantPool getDescriptorPool() {
        return descriptors;
    }

    /**
     * An operand belongs to a definition generation or runtime context that this context cannot
     * use. Reflection can translate this expected ownership rejection into an Ecstasy type error;
     * failures in descriptor construction or handle initialization must not be translated with it.
     */
    public static final class IncompatibleTypeOwnerException extends IllegalArgumentException {
        private IncompatibleTypeOwnerException(String message) {
            super(message);
        }
    }

    /**
     * Reuse constant construction without assigning or interpreting serialized indices.
     */
    private static final class DescriptorPool extends ConstantPool {
        DescriptorPool(ConstantPool definitions) {
            super(definitions.getFileStructure());
            collectDefinitions(definitions);
            setNakedRefType(definitions.getNakedRefType());
        }

        @Override
        public boolean hasSerializedIndices() {
            return false;
        }

        @Override
        public synchronized Constant getConstant(Constant constant) {
            if (constant != null) {
                validate(constant, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            return super.getConstant(constant);
        }

        @Override
        public synchronized <T extends Constant> T register(T constant) {
            if (constant == null) {
                return null;
            }

            // Validate before the equality-based lookup in register(). Equal names from another
            // image generation must not hit an existing descriptor in this context.
            validate(constant, Collections.newSetFromMap(new IdentityHashMap<>()));
            T result = super.register(constant);
            if (result.getConstantPool() != this || result.containsUnresolved()) {
                throw new IllegalArgumentException("Not a resolved runtime descriptor: " + constant);
            }
            // Remember completion only after recursive adoption of dependencies has finished.
            registered.add(result);
            return result;
        }

        /**
         * The visited set uses identity: two structurally equal operands can belong to different
         * generations, so visiting one must never suppress the ownership check on the other.
         */
        private void validate(Constant constant, Set<Constant> visited) {
            if (registered.contains(constant) || !visited.add(constant)) {
                return;
            }
            if (constant instanceof FrameDependentConstant || constant.containsUnresolved()) {
                throw new IllegalArgumentException("Runtime descriptor requires a resolved definition");
            }
            ConstantPool owner = constant.getConstantPool();
            if (owner != this && !definitions.contains(owner)) {
                throw new IncompatibleTypeOwnerException("Constant belongs to another image or context: "
                        + constant);
            }
            if (constant instanceof ModuleConstant id) {
                var selected = getFileStructure().getModule(id);
                if (selected == null) {
                    throw new IncompatibleTypeOwnerException("Module is outside this image graph: " + id);
                }
                if (selected.isFingerprint()) {
                    selected = selected.getFingerprintOrigin();
                }
                if (selected == null || selected != id.getComponent()) {
                    throw new IncompatibleTypeOwnerException("Module resolves to another image generation: " + id);
                }
            }
            constant.forEachUnderlying(child -> validate(child, visited));
        }

        private void collectDefinitions(ConstantPool pool) {
            if (definitions.add(pool)) {
                var file = pool.getFileStructure();
                for (var id : file.moduleIds()) {
                    var module = file.getModule(id);
                    if (module.isFingerprint() && module.getFingerprintOrigin() != null) {
                        collectDefinitions(module.getFingerprintOrigin().getConstantPool());
                    }
                }
            }
        }

        // NOTE: ConstantPool.equals compares contents, not definition-generation identity.
        private final Set<ConstantPool> definitions =
                Collections.newSetFromMap(new IdentityHashMap<>());

        // Only this exact adopted object has completed validation; an equal foreign object has not.
        private final Set<Constant> registered =
                Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private final DescriptorPool descriptors;
}
