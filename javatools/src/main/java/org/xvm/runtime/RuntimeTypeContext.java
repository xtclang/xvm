package org.xvm.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import java.util.function.Predicate;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.RuntimeMethods;

import org.xvm.asm.constants.FrameDependentConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

/**
 * Canonical runtime descriptors for one prepared image and its linked dependencies.
 *
 * <p>Create the context after linking and native preparation, then retain it with its container.
 * Its dependency graph is captured by object identity: recompiling a module with the same name
 * does not make its descriptors interchangeable. Import an image type with {@link #intern} before
 * deriving another type from it. Foreign types require explicit transport. Captured annotation
 * values remain in the container's execution heap; only its opaque tokens may enter descriptors.
 *
 * <p>The descriptor pool is a transitional adapter to existing constant factories. It has no XTC
 * indices and is not attached as a child of the image. Derived descriptors can grow without
 * extending the image's constant table. Semantic metadata belongs to this context's separate
 * table; executable preparation still has container-specific state, so this context must not be
 * shared between containers.
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
     * Test exact ownership of an operand pool, without a structural lookup or creating descriptors.
     * Container uses this when a shared ancestor's compiled method executes in a child service.
     *
     * @param pool  the operand's pool
     * @return true for this descriptor pool or an exact captured definition pool
     */
    boolean owns(ConstantPool pool) {
        return pool == descriptors || descriptors.definitions.contains(pool);
    }

    /**
     * Translate a descriptor or declaration reference across an established sharing boundary. The source
     * context validates the complete graph first; every referenced module must then be approved
     * by the caller's sharing policy. Equal module names alone never authorize this operation.
     * The result refers to this context's prepared declarations and carries no source caches.
     *
     * <p>This package-private boundary is used by Container when transporting shared references.
     * Ordinary interning continues to reject other contexts, including the source after this
     * operation returns. No image table is modified.
     *
     * @param constant  the descriptor or declaration reference being transported
     * @param source    its known source container's context
     * @param shared    the container policy approving each referenced module
     * @param <T>       the constant type
     *
     * @return this context's canonical descriptor
     */
    <T extends Constant> T importShared(T constant, RuntimeTypeContext source,
                                      Predicate<ModuleConstant> shared) {
        T canonical = source.descriptors.register(constant);
        // Validate and collect before taking the destination lock: reciprocal transports must
        // not acquire two descriptor-store locks in opposite order.
        Set<Constant> operands = Collections.newSetFromMap(new IdentityHashMap<>());
        collectOperands(canonical, operands);
        return descriptors.importShared(canonical, operands, shared);
    }

    private static void collectOperands(Constant constant, Set<Constant> operands) {
        if (operands.add(constant)) {
            constant.forEachUnderlying(child -> collectOperands(child, operands));
        }
    }

    /**
     * Describe a declaration in this context. Import its identity before requesting its type:
     * {@link IdentityConstant#getType()} can create a terminal type even for an existing
     * declaration, and must not do so in the definition image after publication.
     *
     * @param identity  a declaration from this context or its exact definition graph
     * @return the declaration's type, owned by this descriptor context
     */
    public TypeConstant typeOf(IdentityConstant identity) {
        return intern(descriptors.register(identity).getType());
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
     * Discard derived semantic answers while retaining canonical descriptors and the definition
     * graph. Subsequent queries rebuild TypeInfo, member lookups, normalization and relations.
     * Existing callers may finish using their completed metadata; this is not an execution-state
     * reset, an image-generation change, or permission to mutate frozen declarations.
     */
    public void clearMetadata() {
        descriptors.getTypeMetadata().clear();
        clearRelations();
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
        IncompatibleTypeOwnerException(String message) {
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
        public RuntimeMethods getRuntimeMethods() {
            return methods;
        }

        private synchronized <T extends Constant> T importShared(T constant, Set<Constant> operands,
                                                                Predicate<ModuleConstant> shared) {
            for (Constant operand : operands) {
                if (operand instanceof HandleConstant capture && !capture.isShared(this)) {
                    throw new IncompatibleTypeOwnerException("Captured values cannot cross descriptor contexts");
                }
                if (operand instanceof ModuleConstant module
                        && (!shared.test(module) || getFileStructure().getModule(module) == null)) {
                    throw new IncompatibleTypeOwnerException("Module is not shared between containers: " + module);
                }
            }

            // Permit only these exact, prevalidated source objects during recursive adoption.
            // The monitor excludes ordinary register/getConstant calls until the set is restored;
            // no ambient pool or thread-local import permission is introduced.
            Set<Constant> previous = sharedOperands;
            sharedOperands = operands;
            try {
                return register(constant);
            } finally {
                sharedOperands = previous;
            }
        }

        @Override
        protected TypeConstant getNakedRefMetadataType() {
            // The configured bootstrap prototype supplies the shape of get(). Its module has
            // already been copied into the prepared image; bind the synthetic metadata to that
            // declaration. This explicit bootstrap adaptation does not admit arbitrary foreign
            // constants into the descriptor interner.
            var identity = getNakedRefType().getSingleUnderlyingClass(true);
            var module = getFileStructure().getModule(identity.getModuleConstant());
            if (module == null || module.isFingerprint()) {
                throw new IllegalStateException("Prepared image is missing the NakedRef prototype");
            }
            var declaration = (ClassStructure) module.getChild(identity.getName());
            return declaration.getFormalType(this);
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
            if (registered.contains(constant) || sharedOperands.contains(constant) || !visited.add(constant)) {
                return;
            }
            if (constant instanceof FrameDependentConstant && !(constant instanceof HandleConstant)
                    || constant.containsUnresolved()) {
                throw new IllegalArgumentException("Runtime descriptor requires a resolved definition");
            }
            ConstantPool owner = constant.getConstantPool();
            if (constant instanceof HandleConstant && owner != this) {
                throw new IncompatibleTypeOwnerException("Capture belongs to another descriptor context");
            }
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

        // Guarded by this pool's monitor. Non-empty only during an explicit shared-reference import;
        // identity membership prevents equal but unapproved foreign operands from being admitted.
        private Set<Constant> sharedOperands = Set.of();

        private final RuntimeMethods methods = new RuntimeMethods(this);
    }

    private final DescriptorPool descriptors;
}
