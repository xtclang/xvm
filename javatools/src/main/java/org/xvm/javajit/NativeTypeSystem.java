package org.xvm.javajit;

import java.io.IOException;
import java.io.InputStream;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Superclass;

import java.lang.constant.ConstantDescs;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.builders.ArrayBuilder;
import org.xvm.javajit.builders.AugmentingBuilder;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.NATIVE_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;

import static org.xvm.util.Handy.require;

/**
 * Native (core) type system.
 */
public class NativeTypeSystem
        extends TypeSystem {
    /**
     * Create the NativeTypeSystem, which combines Ecstasy, "bridge" module and modules that the
     * bridge depends on.
     */
    private NativeTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        super(xvm, shared, owned);

        URL  javatoolsURL  = ConstantPool.class.getProtectionDomain().getCodeSource().getLocation();
        Path javatoolsPath = Paths.get(javatoolsURL.getPath());
        Path bridgePath    = Files.isDirectory(javatoolsPath)
            ? Paths.get(javatoolsPath.toString().replace("javatools", "javatools_jitbridge"))
            : javatoolsPath.resolveSibling("javatools-jitbridge.jar");
        try {
            bridgeLoader = new URLClassLoader(new URL[] {bridgePath.toUri().toURL()});
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid path: " + bridgePath);
        }

        registerNativeClasses();
    }

    /**
     * Create the core TypeSystem for the hidden "Container -1". This should ONLY be called from the
     * Linker.
     *
     * @param xvm   the XVM
     * @param repo  the ModuleRepository to load the core Ecstasy modules from
     */
    static NativeTypeSystem create(Xvm xvm, ModuleRepository repo) {
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
        list.sort(Xvm.StructureByModuleId);
        ModuleLoader[]    shared = new ModuleLoader[0];
        ModuleStructure[] owned  = list.toArray(new ModuleStructure[0]);
        return new NativeTypeSystem(xvm, shared, owned);
    }

    /**
     * The bridge ClassLoader. Note: it **must** never be used with "loadClass" API, only with
     * {@link ClassLoader#getResourceAsStream(String)}" API.
     */
    private final ClassLoader bridgeLoader;

    /**
     * A cache of native class names keyed by class id.
     */
    private final Map<IdentityConstant, String> nativeByClass = new ConcurrentHashMap<>();

    /**
     * A cache of native class names keyed by the type.
     */
    private final Map<TypeConstant, String> nativeByType = new ConcurrentHashMap<>();

    /**
     * A registry of function JIT class names keyed by the function types.
     */
    public final Map<TypeConstant, String> nativeFunctions = new ConcurrentHashMap<>();

    /**
     * A cache of builders for native classes keyed by type.
     */
    public final Map<TypeConstant, Class> nativeBuilders = new ConcurrentHashMap<>();

    /**
     * @return a reserved class name for the specified type, or null if it's not reserved
     */
    public String getReservedName(TypeConstant type) {
        String name = nativeByType.get(type);
        if (name == null) {
            name = nativeByClass.get(type.getSingleUnderlyingClass(true));
        }
        return name;
    }

    @Override
    public byte[] genClass(ModuleLoader moduleLoader, String name) {
        String className = moduleLoader.prefix + name;
        String classPath = className.replace('.', '/') + ".class";
        try (InputStream in = bridgeLoader.getResourceAsStream(classPath)) {
            if (in != null) {
                byte[] classBytes = in.readAllBytes();
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                       simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);
                if (simpleName.codePointAt(0) == NO_MOD || isEnumerationClass(simpleName)) {
                    // by convention the classes that start with an "n" are "no-modification"
                    // classes that we take "as is" (they must not be augmented)
                    return classBytes;
                }

                ClassModel     model     = ClassFile.of().parse(classBytes);
                String         path      = className.substring(moduleLoader.prefix.length()).replace('$', '.');
                TypeConstant[] formals   = TypeConstant.NO_TYPES;
                int            typeStart = path.indexOf('ᐸ') ;

                if (typeStart > 0) {
                    // TODO: do we need support for multiple formal types?
                    int typeEnd = path.indexOf('ᐳ', typeStart);
                    if (typeEnd == -1) {
                        throw new RuntimeException("Invalid name " + path);
                    }
                    String       typeString = path.substring(typeStart + 1, typeEnd);
                    TypeConstant type = switch (typeString) {
                        case "Bit"     -> pool().typeBit();
                        case "Char"    -> pool().typeChar();
                        case "Dec32"   -> pool().typeDec32();
                        case "Dec64"   -> pool().typeDec64();
                        case "Dec128"  -> pool().typeDec128();
                        case "Float32" -> pool().typeFloat32();
                        case "Float64" -> pool().typeFloat64();
                        case "Int8"    -> pool().typeInt8();
                        case "Nibble"  -> pool().typeNibble();
                        case "Int16"   -> pool().typeInt16();
                        case "Int32"   -> pool().typeInt32();
                        case "Int64"   -> pool().typeInt64();
                        case "Int128"  -> pool().typeInt128();
                        case "UInt8"   -> pool().typeUInt8();
                        case "UInt16"  -> pool().typeUInt16();
                        case "UInt32"  -> pool().typeUInt32();
                        case "UInt64"  -> pool().typeUInt64();
                        case "UInt128" -> pool().typeUInt128();
                        case "Object"  -> pool().typeObject();
                        default        -> throw new RuntimeException("Unsupported type " + typeString);
                    };
                    formals = new TypeConstant[] {type};
                    path    = path.substring(0, typeStart);
                }

                ClassStructure struct = (ClassStructure) moduleLoader.module.getChildByPath(path);
                if (struct == null) {
                    throw new RuntimeException("Structure is missing for " + moduleLoader.prefix + name);
                }
                TypeConstant type = struct.getCanonicalType();
                if (formals.length > 0) {
                    type = type.adoptParameters(pool(), formals);
                }
                return augmentNativeClass(model, className, type);
            }
        } catch (IOException ignore) {}

        // there is no native class, but there must be a corresponding Ecstasy component
        return super.genClass(moduleLoader, name);
    }

    /**
     * Augment the existing native class with the Ecstasy methods.
     */
    private byte[] augmentNativeClass(ClassModel model, String className, TypeConstant type) {
        ClassFile classFile = ClassFile.of(
                ClassFile.ClassHierarchyResolverOption.of(
                    ClassHierarchyResolver.ofClassLoading(loader)),
                ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS,
                ClassFile.StackMapsOption.GENERATE_STACK_MAPS);
        return classFile.transformClass(model, (classBuilder, element) -> {
            if (element instanceof Interfaces) {
                // don't copy an interface list; it would prevent the builder to add any;
                // NOTE: any "implements" declared by the native classes will be removed
                //       unless the corresponding component declares them as well; if that ever
                //       becomes a problem, we can collect the declared ones here and pass it to
                //       the builder to merge
                return;
            }

            // ignore native enum values; we are not generating any code for them
            if (!type.isEnumValue() &&
                    element instanceof MethodModel methodModel &&
                    methodModel.methodName().stringValue().equals(ConstantDescs.CLASS_INIT_NAME)) {
                // skip the static initializer for now; we will re-incorporate it later;
                // see AugmentingBuilder.augmentStaticInitializer()
                return;
            }

            classBuilder.with(element); // copy everything else "as is"

            if (element instanceof Superclass) {
                // augment the new classfile using the Ecstasy class structure (just once!)
                ensureBuilder(type, model).assembleImpl(className, classBuilder);
            }
        });
    }

    protected Builder ensureBuilder(TypeConstant type, ClassModel model) {
        assert model != null;

        if (nativeBuilders.get(type) instanceof Class builderClass) {
            try {
                return (AugmentingBuilder) builderClass.getDeclaredConstructor(
                    TypeSystem.class, TypeConstant.class, ClassModel.class).
                        newInstance(this, type, model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (type.isArray()) {
            return new ArrayBuilder(this, type, model);
        }

        return new AugmentingBuilder(this, type, model);
    }

    // ----- internal ------------------------------------------------------------------------------

    void registerNativeClasses() {
        ConstantPool pool = pool();

        // only rebased types need to be registered
        nativeByClass.put(pool.clzEnum(),      Builder.N_nEnum);
        nativeByClass.put(pool.clzEnumValue(), Builder.N_nEnum);
        nativeByClass.put(pool.clzModule(),    Builder.N_nModule);
        nativeByClass.put(pool.clzObject(),    Builder.N_nObj);
        nativeByClass.put(pool.clzRef(),       Builder.N_nRef);
        nativeByClass.put(pool.clzService(),   Builder.N_nService);
        nativeByClass.put(pool.clzType(),      Builder.N_nType);
        nativeByClass.put(pool.clzVar(),       Builder.N_nRef);

        // various types used by native classes
        TypeConstant typeChar       = pool.typeChar();
        TypeConstant iterableᐸCharᐳ = pool.ensureParameterizedTypeConstant(pool.typeIterable(), typeChar);
        TypeConstant iteratorᐸCharᐳ = pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeChar);

        nativeByType.put(iterableᐸCharᐳ, Builder.N_IterableChar);
        nativeByType.put(iteratorᐸCharᐳ,  Builder.N_IteratorChar);

        // specialized builders
        nativeBuilders.put(pool.typeInt64(),  org.xvm.javajit.builders.Int64Builder.class);

        // pre-register functions used by the native classes:

        // prime the function name counter
        xvm.createUniqueSuffix("");

        // xFunction.ꖛ0: function void()
        nativeFunctions.put(
            pool.buildFunctionType(TypeConstant.NO_TYPES, TypeConstant.NO_TYPES),
            Builder.N_nFunction + "$ꖛ0");
    }
}



