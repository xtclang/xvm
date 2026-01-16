package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;

import java.lang.classfile.attribute.SourceFileAttribute;

import java.lang.constant.ClassDesc;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.builders.CommonBuilder;
import org.xvm.javajit.builders.EnumBuilder;
import org.xvm.javajit.builders.EnumValueBuilder;
import org.xvm.javajit.builders.EnumerationBuilder;
import org.xvm.javajit.builders.ExceptionBuilder;
import org.xvm.javajit.builders.ModuleBuilder;
import org.xvm.javajit.builders.PackageBuilder;

import static org.xvm.javajit.Builder.MODULE;

import static org.xvm.util.Handy.require;

import static java.lang.Math.max;
import static java.lang.Math.min;

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

    // special identifier characters used for encoding class/method/field names in Java ClassFiles
    public static final int  ESC      = 0x10458; // "𐑘"
    public static final char CLASS    = 'c';     // prefix
    public static final char INTRFACE = 'i';     // prefix
    public static final char PROXY    = 'p';     // prefix
    public static final char DUCK     = 'd';     // prefix
    public static final char MASK     = 'm';     // prefix
    public static final char NO_MOD   = 'n';     // prefix - native / no build modification
    public static final char FUTURE   = 'f';     // prefix
    public static final char ENUM     = 'e';     // prefix - for Enumeration<Enum> i.e. Class<Enum>
    public static final char EXCEPT   = 'x';     // prefix - gen'd RuntimeException (holds an Ecstasy Exception ref)
    public static final char RESERVED = '¤';     // prefix (0xA4)
    public static final char ID_NUM   = 'ꖛ';     // 0xA59B
    public static final char DOT      = '۰';     // 0x06F0
    public static final char UNION    = 'ǀ';     // 0x01C0
    public static final char INTRSECT = 'ⵜ';     // 0x2D5C
    public static final char DIFF     = 'ᝍ';     // 0x174D
    public static final char NULLABLE = 'ꛫ';     // 0xA6EB
    public static final char BANG     = 'ǃ';     // 0x01C3
    public static final char ANNO     = 'Ҩ';     // 0x04A8
    public static final char SPACE    = '‿';     // 0x203F
    public static final char COMMA    = 'ܝ';     // 0x071D
    public static final char L_PAREN  = 'ꕿ';     // 0xA57F
    public static final char R_PAREN  = 'ꛑ';     // 0xA6D1
    public static final char L_ANGLE  = 'ᐸ';     // 0x1438
    public static final char R_ANGLE  = 'ᐳ';     // 0x1433
    public static final int  MIN_ESC  = min(ESC, min(ID_NUM, min(DOT, min(UNION, min(INTRSECT,
            min(DIFF, min(NULLABLE, min(BANG, min(ANNO, min(SPACE, min(COMMA,  min(L_PAREN,
            min(R_PAREN, min(L_ANGLE, R_ANGLE))))))))))))));

    /**
     * @return the ConstantPool associated with this TypeSystem
     */
    public ConstantPool pool() {
        // TODO should there be a separate ConstantPool created for this type system when there are
        //      only shared modules? i.e. is there a FileStructure?
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

        // it's possible that the type is a parameterized shared type, e.g. Array<Person>
        for (ModuleLoader loader : shared) {
            if (loader.module.getIdentityConstant().equals(module)) {
                return loader;
            }
        }

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
        String className = moduleLoader.prefix + name;

        ModuleStructure module = moduleLoader.module;
        Artifact        art    = deduceArtifact(module, name);
        if (art != null) {
            Builder builder = ensureBuilder(art);
            Consumer<? super ClassBuilder> handler = classBuilder -> {
                switch (art.shape) {
                case Impl:
                    classBuilder.with(SourceFileAttribute.of(art.clz.getSourceFileName()));
                    builder.assembleImpl(className, classBuilder);
                    break;

                case Enum:
                    builder.assembleImpl(className, classBuilder);
                    break;

                case Exception:
                    ((ExceptionBuilder) builder).assembleJavaException(className, classBuilder);
                    break;

                default:
                    throw new UnsupportedOperationException();
                }
            };

            // There are other options that can be useful:
            //     DeadCodeOption.PATCH_DEAD_CODE
            //     DebugElementsOption.DROP_DEBUG
            //     LineNumbersOption.DROP_LINE_NUMBERS
            // TODO: force some of them or make configurable
            ClassFile classFile = ClassFile.of(
                ClassFile.ClassHierarchyResolverOption.of(
                    ClassHierarchyResolver.ofClassLoading(loader)),
                ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS,
                ClassFile.StackMapsOption.GENERATE_STACK_MAPS
            );

            return classFile.build(ClassDesc.of(className), handler);
        }
        return null;
    }

    /**
     * @return a builder for the specified type
     */
    protected Builder ensureBuilder(Artifact art) {
        ConstantPool pool = pool();
        TypeConstant type = art.type;

        if (type.isA(pool.typeModule())) {
            // it's definitely not Module.x, since this is not the native TypeSystem
            assert !type.equals(pool.typeModule());
            return new ModuleBuilder(this, type);
        }

        if (type.isA(pool.typePackage())) {
            // it's definitely not Package.x, since this is not the native TypeSystem
            assert !type.equals(pool.typeModule());
            return new PackageBuilder(this, type);
        }

        if (art.shape == ClassfileShape.Enum) {
            TypeConstant enumerationType = art.clz.getIdentityConstant().getValueType(pool(), null);
            return new EnumerationBuilder(this, enumerationType);
        }

        if (type.isEnum()) {
            return new EnumBuilder(this, type);
        }

        if (type.isEnumValue()) {
            return new EnumValueBuilder(this, type);
        }

        if (type.isA(pool.typeException())) {
            // the root Exception class is handled by the NativeTypeSystem
            assert !type.equals(pool.typeException());
            return new ExceptionBuilder(this, type);
        }

        if (type.isA(pool.typeClass())) {
            TypeConstant publicType = type.getParamType(0);
            if (publicType.isEnumValue()) {
                return new EnumerationBuilder(this, type);
            } else {
                System.err.println("Missing class builder " + type.getValueString());
            }
        }

        return new CommonBuilder(this, type);
    }

    /**
     * Jit class shapes.
     */
    public enum ClassfileShape {
        Class    ("c"), // used to prefix a class name only when a collision would otherwise occur
        Pure     ("i"), // interface
        Proxy    ("p"),
        Duck     ("d"),
        Enum     ("e"),
        Mask     ("m"),
        NoMod    ("n"), // take ClassFile bytes "as is"
        Future   ("f"),
        Exception("x"), // RuntimeException "entangled" with the corresponding XTC Exception class
        Impl     (""),  // needs to be last
        ;

        ClassfileShape(String prefix) {
            this.prefix = prefix;
        }

        public final String prefix;
    }

    public record Artifact(TypeConstant type, ClassStructure clz, ClassfileShape shape) {}

    public Artifact deduceArtifact(ModuleStructure module, String name) {
        if (name.equals(MODULE)) {
            return new Artifact(module.getCanonicalType(), module, ClassfileShape.Impl);
        }

        ClassfileShape shape  = ClassfileShape.Impl;
        int            dotIx  = name.lastIndexOf('.');
        String         simple = name.substring(dotIx + 1);
        if (simple.length() > 1) {
            for (ClassfileShape sh : ClassfileShape.values()) {
                if (sh.prefix.isEmpty() || simple.startsWith(sh.prefix)) {
                    // all suffixes except Impl are of the length 2
                    String pkg = dotIx < 0 ? "" : name.substring(0, dotIx + 1);
                    name  = pkg + simple.substring(sh.prefix.length());
                    shape = sh;
                    break;
                }
            }
        }

        TypeConstant type     = null;
        int          idOffset = name.indexOf(ID_NUM);
        if (idOffset > 0) {
            // the name represents a parameterized type with primitive actual type(s)
            type = (TypeConstant) pool().getConstant(Integer.valueOf(name.substring(idOffset+1)));
            name = name.substring(0, idOffset);
        }

        if (module.getChildByPath(name.replace('$', '.')) instanceof ClassStructure struct) {
            if (type == null) {
                type = struct.getCanonicalType();
            }
            return new Artifact(type, struct, shape);
        }
        return null;
    }

    /**
     * Determine if the specified JIT name is an `Enumeration` class.
     *
     * @param name  a JIT class name
     *
     * @return true iff the name specifies an `Enumeration` class
     */
    public static boolean isEnumerationClass(String name) {
        assert !name.isEmpty();
        return name.codePointAt(0) == ENUM || name.equals("Enumeration");
    }

    /**
     * Build a class name for the `Enumeration` class of the specific already-escaped `Enum` class
     * name.
     *
     * @param name  the JIT name of the `Enum` class
     *
     * @return the JIT name of the `Enumeration` class
     */
    public static String enumerationClass(String name) {
        int offset = max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1;
        return new StringBuilder(name.length() + ENUM > 0xFFFF ? 2 : 1)
                .append(name, 0, offset)
                .appendCodePoint(ENUM)
                .append(name, offset, name.length())
                .toString();
    }

    /**
     * @param name  a class name to be used as part of a Java ClassFile
     *
     * @return the passed in `name` but with any special characters (reserved by the JIT) escaped
     */
    public static String escapeJitClassName(String name) {
        StringBuilder buf = null;
        int classStart = name.lastIndexOf('.') + 1;
        for (int index = 0, size = name.length(); index < size; index = name.offsetByCodePoints(index, 1)) {
            int ch = name.codePointAt(index);
            if (ch >= MIN_ESC || index == classStart) {
                switch (ch) {
                case ESC:
                case CLASS, INTRFACE, PROXY, DUCK, MASK, NO_MOD, FUTURE, ENUM, EXCEPT, RESERVED:
                case ID_NUM, DOT, UNION, INTRSECT, DIFF, NULLABLE, BANG, ANNO, SPACE, COMMA:
                case L_PAREN, R_PAREN, L_ANGLE, R_ANGLE:
                    if (buf == null) {
                        buf = new StringBuilder(size + 8).append(name, 0, index);
                    }
                    buf.appendCodePoint(ESC);
                }
            }
            if (buf != null) {
                buf.appendCodePoint(ch);
            }
        }
        return buf == null ? name : buf.toString();
    }

    // TODO CP - must we escape field/method names?
    /**
     * @param name  a method or field name to be used as part of a Java ClassFile
     *
     * @return the passed in `name` but with any special characters (reserved by the JIT) escaped
     */
    public static String escapeJitMemberName(String name) {
        StringBuilder buf = null;
        for (int index = 0, size = name.length(); index < size; index = name.offsetByCodePoints(index, 1)) {
            int ch = name.codePointAt(index);
            if (ch >= MIN_ESC) {
                switch (ch) {
                case ESC:
                case ID_NUM, DOT, UNION, INTRSECT, DIFF, NULLABLE, BANG, ANNO, SPACE, COMMA:
                case L_PAREN, R_PAREN, L_ANGLE, R_ANGLE:
                    if (buf == null) {
                        buf = new StringBuilder(size + 8).append(name, 0, index);
                    }
                    buf.appendCodePoint(ESC);
                }
            }
            if (buf != null) {
                buf.appendCodePoint(ch);
            }
        }
        return buf == null ? name : buf.toString();
    }

    /**
     * @param name  a previously escaped JIT name
     *
     * @return the original unescaped name
     */
    public static String unescapeJitName(String name) {
        StringBuilder buf     = null;
        boolean       escaped = false;
        for (int index = 0, size = name.length(); index < size; index = name.offsetByCodePoints(index, 1)) {
            int ch = name.codePointAt(index);
            if (ch == ESC && !escaped) {
                if (buf == null) {
                    buf = new StringBuilder(size).append(name, 0, index);
                }
                escaped = true;
            } else if (buf != null) {
                buf.appendCodePoint(ch);
                escaped = false;
            }
        }
        return buf == null ? name : buf.toString();
    }

    /**
     * Register a constant to be used by the runtime. The returned index is going to be stable for
     * the lifetime of the TypeSystem.
     *
     * @param constant  the constant to register
     *
     * @return the index of the constant
     */
    public int registerConstant(Constant constant) {
        return pool().register(constant).getPosition();
    }

    /**
     * Retrieve a constant at the specified index.
     *
     * Note: this operation doesn't have to be synchronized since even when the underlying array
     * growth occurs, the old list still contains the corresponding entry at the right place.
     */
    public Constant getConstant(int index) {
        return pool().getConstant(index);
    }
}



