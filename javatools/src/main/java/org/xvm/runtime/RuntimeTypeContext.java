package org.xvm.runtime;

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
 * extending the image's constant table. Metadata and handles have not yet been separated from
 * {@link TypeConstant}; consequently this context must not be shared between containers.
 */
public final class RuntimeTypeContext {
    /**
     * Create a context over a prepared image without changing its declarations or constant table.
     *
     * @param definitions  the prepared image's constant pool, with its final dependency graph
     */
    public RuntimeTypeContext(ConstantPool definitions) {
        descriptors = new DescriptorPool(definitions);
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
        TypeConstant[] arguments = new TypeConstant[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = intern(parameters[i]);
        }
        return descriptors.ensureParameterizedTypeConstant(base, arguments);
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

        private void validate(Constant constant, Set<Constant> visited) {
            if (registered.contains(constant) || !visited.add(constant)) {
                return;
            }
            if (constant instanceof FrameDependentConstant || constant.containsUnresolved()) {
                throw new IllegalArgumentException("Runtime descriptor requires a resolved definition");
            }
            ConstantPool owner = constant.getConstantPool();
            if (owner != this && !definitions.contains(owner)) {
                throw new IllegalArgumentException("Constant belongs to another image or context: "
                        + constant);
            }
            if (constant instanceof ModuleConstant id) {
                var selected = getFileStructure().getModule(id);
                if (selected == null) {
                    throw new IllegalArgumentException("Module is outside this image graph: " + id);
                }
                if (selected.isFingerprint()) {
                    selected = selected.getFingerprintOrigin();
                }
                if (selected == null || selected != id.getComponent()) {
                    throw new IllegalArgumentException("Module resolves to another image generation: " + id);
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

        private final Set<ConstantPool> definitions =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Constant> registered =
                Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private final DescriptorPool descriptors;
}
