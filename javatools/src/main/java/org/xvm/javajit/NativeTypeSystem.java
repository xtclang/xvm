package org.xvm.javajit;

import java.io.IOException;
import java.io.InputStream;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Superclass;

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

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.builders.AugmentingBuilder;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.NATIVE_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;

import static org.xvm.javajit.Builder.ENUMERATION;

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
     * A cache of native class names keyed by type.
     */
    public final Map<TypeConstant, String> nativeByType = new ConcurrentHashMap<>();

    /**
     * A cache of native types keyed by native class name.
     * TODO: not currently used, remove
     */
    public final Map<String, TypeConstant> nativeByName = new ConcurrentHashMap<>();

    /**
     * A cache of builders for native classes keyed by type.
     */
    public final Map<TypeConstant, Class> nativeBuilders = new ConcurrentHashMap<>();

    @Override
    public byte[] genClass(ModuleLoader moduleLoader, String name) {
        String className = moduleLoader.prefix + name;
        String classPath = className.replace('.', '/') + ".class";
        try (InputStream in = bridgeLoader.getResourceAsStream(classPath)) {
            if (in != null) {
                byte[] classBytes = in.readAllBytes();
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                if (simpleName.startsWith("x") || simpleName.endsWith(ENUMERATION)) {
                    // by convention the classes that start with an "x" are "rebase" classes
                    // that we take "as is" (they don't need to be augmented)
                    return classBytes;
                }

                ClassModel     model  = ClassFile.of().parse(classBytes);
                String         path   = className.substring(moduleLoader.prefix.length()).replace('$', '.');
                ClassStructure struct = (ClassStructure) moduleLoader.module.getChildByPath(path);
                if (struct == null) {
                    throw new RuntimeException("Structure is missing for " + moduleLoader.prefix + name);
                }
                return augmentNativeClass(model, className, struct.getCanonicalType());
            }
        } catch (IOException ignore) {}

        // there is no native class, but there must be a corresponding Ecstasy component
        return super.genClass(moduleLoader, name);
    }

    /**
     * Augment the existing native class with the Ecstasy methods.
     */
    private byte[] augmentNativeClass(ClassModel model, String className, TypeConstant type) {
        Builder builder = ensureBuilder(type, model);

        return ClassFile.of().transformClass(model, (classBuilder, element) -> {
            classBuilder.with(element); // copy everything "as is"

            if (element instanceof Superclass) {
                // augment the new classfile using the Ecstasy class structure (just once!)
                builder.assembleImpl(className, classBuilder);
            }
        });
    }

    protected Builder ensureBuilder(TypeConstant type, ClassModel model) {
        assert model != null;

        if (nativeBuilders.get(type) instanceof Class builderClass) {
            try {
                return (Builder) builderClass.getDeclaredConstructor(
                    TypeSystem.class, TypeConstant.class, ClassModel.class).
                        newInstance(this, type, model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return new AugmentingBuilder(this, type, model);
    }

    // ----- internal ------------------------------------------------------------------------------

    private void registerNativeClasses() {
        ConstantPool pool = pool();

        // only rebased types need to be registered
        nativeByType.put(pool.typeModule(),  Builder.N_xModule);
        nativeByType.put(pool.typeObject(),  Builder.N_xObj);
        nativeByType.put(pool.typeService(), Builder.N_xService);
        nativeByType.put(pool.typeType(),    Builder.N_xType);

        for (Map.Entry<TypeConstant, String > entry : nativeByType.entrySet()) {
            nativeByName.put(entry.getValue(), entry.getKey());
        }

        nativeBuilders.put(pool.typeInt64(),  org.xvm.javajit.builders.Int64Builder.class);
        nativeBuilders.put(pool.typeString(), org.xvm.javajit.builders.StringBuilder.class);

        // pre-register functions used by the native classes

        // prime the function name counter
        xvm.createUniqueSuffix("");

        // xFunction.$0: function void()
        String f0 = ensureJitClassName(
            pool.buildFunctionType(TypeConstant.NO_TYPES, TypeConstant.NO_TYPES));
        assert f0.equals(Builder.N_xFunction + "$$0");
    }
}



