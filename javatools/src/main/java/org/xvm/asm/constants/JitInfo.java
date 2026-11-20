package org.xvm.asm.constants;

import org.xvm.asm.ClassStructure;

import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.TypeSystem;

import java.lang.constant.ClassDesc;

import java.lang.ref.WeakReference;

/**
 * Cached information about the JIT's JVM representation of an Ecstasy type.
 *
 * Names and descriptors are cached without retaining a TypeSystem or ModuleLoader.
 * Terms are defined in the "jit_class_names.txt" doc.
 */
public final class JitInfo {
    /**
     * Construct a JitInfo.
     *
     * @param type       the type for which this JitInfo exists
     * @param loader     the ModuleLoader for this type
     * @param jccType    the "Java Call Class" type
     * @param jicType    the "Java Instance Class" type
     * @param jImplType  the "Java Implementation Class" type
     */
    public JitInfo(
            TypeConstant type,
            ModuleLoader loader,
            TypeConstant jccType,
            TypeConstant jicType,
            TypeConstant jImplType) {
        assert type != null && loader != null && jccType != null && jImplType != null;

        this.type      = type;
        this.loader    = loader.weakRef;
        this.jccType   = jccType;
        this.jicType   = jicType;
        this.jImplType = jImplType;
    }

    /**
     * Represent a different type using the same JIT mappings.
     *
     * @param type  the different type to represent
     *
     * @return a clone of this JitInfo with the specified type
     */
    public JitInfo withType(TypeConstant type) {
        assert type != null;
        return this.type == type
                ? this
                : new JitInfo(type, loader(), jccType, jicType, jImplType);
    }

    /**
     * Preserve the callable and implementation mappings while removing the instantiable class.
     */
    public JitInfo withoutJic(TypeConstant type) {
        assert type != null;
        return jicType == null && this.type == type
                ? this
                : new JitInfo(type, loader(), jccType, null, jImplType);
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The type for which this JitInfo exists.
     */
    private final TypeConstant type;

    /**
     * A weak reference to the ModuleLoader, used to verify that this JitInfo is from the correct
     * TypeSystem.
     */
    private final WeakReference<ModuleLoader> loader;

    /**
     * The "Java Call Class" type
     */
    private final TypeConstant jccType;

    /**
     * The "Java Instance Class" type
     */
    private final TypeConstant jicType;

    /**
     * The "Java Implementation Class" type
     */
    private final TypeConstant jImplType;

    /**
     * The categorization of the JIC, describing how an instance is to be created.
     */
    private volatile JICForm jicForm;

    /**
     * Cached JCC name.
     */
    private volatile String jccName;

    /**
     * Cached JIC name.
     */
    private volatile String jicName;

    /**
     * Cached Java implementation class name.
     */
    private volatile String jImplName;

    // ----- public API ----------------------------------------------------------------------------

    /**
     * @return true if this JitInfo is still valid because its ModuleLoader has not been GC'd
     */
    public boolean isValid() {
        return peekLoader() != null;
    }

    /**
     * @param ts  a TypeSystem
     *
     * @return true if this JitInfo is valid for that TypeSystem
     */
    public boolean isValidIn(TypeSystem ts) {
        ModuleLoader loader = peekLoader();
        return loader != null && ts.contains(loader);
    }

    /**
     * @return the TypeSystem that this JitInfo is tied to
     *
     * @throws IllegalStateException iff the TypeSystem has been garbage collected
     */
    public TypeSystem typeSystem() {
        ModuleLoader loader = peekLoader();
        if (loader == null) {
            throw new IllegalStateException("TypeSystem for " + this + " has been garbage collected");
        }
        return loader.typeSystem;
    }

    /**
     * @return the TypeSystem that this JitInfo is tied to, or null if the TypeSystem was GC'd
     */
    public TypeSystem peekTypeSystem() {
        ModuleLoader loader = peekLoader();
        return loader == null ? null : loader.typeSystem;
    }

    /**
     * @return the ModuleLoader that this JitInfo is tied to
     *
     * @throws IllegalStateException iff the ModuleLoader has been garbage collected
     */
    public ModuleLoader loader() {
        ModuleLoader loader = peekLoader();
        if (loader == null) {
            throw new IllegalStateException("ModuleLoader for " + this + " has been garbage collected");
        }
        return loader;
    }

    /**
     * @return the ModuleLoader that this JitInfo is tied to, or null if that ModuleLoader was GC'd
     */
    public ModuleLoader peekLoader() {
        return loader.get();
    }

    /**
     * @return the type for which this JitInfo was created
     */
    public TypeConstant type() {
        return type;
    }

    /**
     * @return the "Java Call Class" type
     */
    public TypeConstant jccType() {
        return jccType;
    }

    /**
     * @return the "Java Instance Class" type
     */
    public TypeConstant jicType() {
        return jicType;
    }

    /**
     * @return the "Java Implementation Class" type
     */
    public TypeConstant jImplType() {
        return jImplType;
    }

    /**
     * @return the JICForm that defines how an instance of the type gets created
     */
    public JICForm jicForm() {
        JICForm form = jicForm;
        if (form == null) {
            jicForm = form = computeJicForm();
        }
        return form;
    }

    /**
     * @return true iff the JCC is an interface, indicating the use of the "invokeinterface" op
     */
    public boolean jccInterface() {
        return jccType instanceof PropertyClassTypeConstant property
                ? property.getRefType().isJitCCInterface(typeSystem())
                : jccType.isDerivedVirtualChildClass() || jccType.isJitImplInterfaceType();
    }

    /**
     * @return the "Java Call Class" name
     */
    public String jccName() {
        String name = jccName;
        if (name == null) {
            jccName = name = computeJccName();
        }
        return name;
    }

    /**
     * @return the "Java Instance Class" name
     */
    public String jicName() {
        if (jicType == null) {
            return null;
        }

        String name = jicName;
        if (name == null) {
            jicName = name = computeJicName();
        }
        return name;
    }

    /**
     * @return the "Java Implementation Class" name
     */
    public String jImplName() {
        String name = jImplName;
        if (name == null) {
            jImplName = name = computeJImplName();
        }
        return name;
    }

    /**
     * @return the Java descriptor for the callable class
     */
    public ClassDesc jccDesc() {
        return ClassDesc.of(jccName());
    }

    /**
     * @return the Java descriptor for the instantiable class
     */
    public ClassDesc jicDesc() {
        return ClassDesc.of(jicName());
    }

    /**
     * @return the Java descriptor for the implementation class
     */
    public ClassDesc jImplDesc() {
        return ClassDesc.of(jImplName());
    }

    // ----- internal ------------------------------------------------------------------------------

    private JICForm computeJicForm() {
        if (jicType == null) {
            return JICForm.Abstract;
        }

        if (jicType instanceof PropertyClassTypeConstant) {
            return JICForm.Simple;
        }

        boolean hasOuter = jicType.getSingleUnderlyingClass(true).getComponent()
                instanceof ClassStructure clz && clz.isInstanceChild();
        return jicType.hasJitRuntimeType()
                ? hasOuter ? JICForm.GenericChild : JICForm.Generic
                : hasOuter ? JICForm.Child        : JICForm.Simple;
    }

    private String computeJccName() {
        if (jccType instanceof PropertyClassTypeConstant property) {
            return property.isNativeRebase()
                    ? jicType == null ? buildPropertyClassName(property) : jicName()
                    : property.getRefType().getJitCCName(typeSystem());
        }
        String name = buildJitName(jccType.removeAnnotations());
        return jccType.isDerivedVirtualChildClass() ? TypeSystem.pureInterface(name) : name;
    }

    private String computeJicName() {
        return jicType instanceof PropertyClassTypeConstant property
                ? buildPropertyClassName(property)
                : buildJitName(jicType);
    }

    private String computeJImplName() {
        if (jImplType instanceof PropertyClassTypeConstant property) {
            return jicType == jImplType ? jicName() : buildPropertyClassName(property);
        }
        return buildJitName(jImplType);
    }

    private String buildPropertyClassName(PropertyClassTypeConstant property) {
        ModuleLoader  loader = loader();
        TypeConstant  type   = loader.module.getConstantPool().register(property.asStandardRef());
        StringBuilder name   = new StringBuilder(property.getProperty().getClassJitName(typeSystem()));
        TypeSystem.appendJitSuffix(name, type.getPosition());
        return loader.prefix + TypeSystem.escapeJitName(name.toString(), true);
    }

    private String buildJitName(TypeConstant type) {
        ModuleLoader owner     = typeSystem().findOwnerLoader(type);
        TypeConstant canonical = owner.module.getConstantPool().register(type);
        return canonical.buildJitClassName(owner);
    }

    // ----- JICForm enumeration -------------------------------------------------------------------

    /**
     * Defines the meaning of a JIT Instance Class (JIC).
     *
     * For a given type, there is a "JIT Instance Class" (JIC), which may be null if the type cannot
     * be instantiated ("Abstract"), may be directly newable ("Simple"), and so on.
     *
     * @see org.xvm.javajit.builders.CommonBuilder#assembleNew
     */
    public enum JICForm {
        /**
         * There is no JIC because the type is not instantiable.
         */
        Abstract,
        /**
         * The JIC has the simple (Ctx-only) instantiation helper.
         */
        Simple,
        /**
         * The JIC has the simple-with-type (Ctx, TypeConstant) instantiation helper.
         */
        Generic,
        /**
         * The JIC has a virtual child (Ctx, Outer) instantiation helper.
         */
        Child,
        /**
         * The JIC has a virtual child with type (Ctx, TypeConstant, Outer) instantiation helper.
         */
        GenericChild,
        /**
         * The JIC requires runtime evaluation, because its name depends on type information that is
         * only available at runtime. For example, the code "new Element[]" (where "Element" is a
         * Type Parameter Type) needs to have the actual value of "Element" in order to know what
         * L1/L2 specialized class of "Array" to instantiate.
         */
        Dynamic,
    }
}
