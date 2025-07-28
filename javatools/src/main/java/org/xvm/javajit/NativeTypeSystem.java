package org.xvm.javajit;

import java.io.IOException;
import java.io.InputStream;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.attribute.SourceFileAttribute;

import java.lang.constant.ClassDesc;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

        ModuleLoader bridgeLoader = null;
        for (ModuleLoader loader : this.loader.owned) {
            if (loader.module.getName().equals(NATIVE_MODULE)) {
                bridgeLoader = loader;
                break;
            }
        }
        assert bridgeLoader != null;
        this.bridgeLoader = bridgeLoader;

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
     * The ModuleLoader for the "bridge" (_native.xtclang.org) module.
     */
    public final ModuleLoader bridgeLoader;

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

    public static String XTC_BRIDGE_PREFIX  = "_native.";

    /**
     * Load the native class for the specified name.
     *
     * @return the class bytes or null if the name does not represent a native class
     */
    public byte[] loadNativeClass(ModuleLoader loader, String name)
            throws ClassNotFoundException {
        try {
            try (InputStream in = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in != null) {
                    byte[] classBytes = in.readAllBytes();
                    String simpleName = name.substring(name.lastIndexOf('.') + 1);
                    if (simpleName.startsWith("x")) {
                        // by convention the classes that start with an "x" are "rebase" classes
                        // that we take "as is" (they don't need to be augmented)
                        return classBytes;
                    }

                    ClassModel     model  = ClassFile.of().parse(classBytes);
                    String         path   = name.substring(loader.prefix.length());
                    ClassStructure struct = (ClassStructure) loader.module.getChildByPath(path);
                    if (struct == null) {
                        throw new ClassNotFoundException("Cannot find XTC class for " + path);
                    }
                    return augmentNativeClass(model, name, struct.getCanonicalType());
                }
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to read native class " + name);
        }
        return null;
    }

    /**
     * Augment the existing native class with the Ecstasy methods.
     */
    private byte[] augmentNativeClass(ClassModel model, String className, TypeConstant type) {

        ClassStructure  clz     = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();
        Builder         builder = ensureBuilder(type, model);
        Consumer<? super ClassBuilder> handler = cb -> {
            cloneClassModel(model, cb);

            // augment the new classfile using the Ecstasy class structure
            cb.with(SourceFileAttribute.of(clz.getSourceFileName()));
            builder.assembleImpl(className, cb);
        };

        return ClassFile.of().build(ClassDesc.of(className), handler);
    }

    /**
     * Clone the existing class model into a new Classfile using the specified builder.
     */
    private void cloneClassModel(ClassModel model, ClassBuilder cb) {
        if (model.superclass().isPresent()) {
            cb.withSuperclass(model.superclass().get().asSymbol());
        }

        cb.withFlags(model.flags().flagsMask())
          .withInterfaces(model.interfaces());

        model.fields().forEach(fm ->
            cb.withField(fm.fieldName().stringValue(), fm.fieldTypeSymbol(), fm.flags().flagsMask()));

        model.methods().forEach(mm -> {
            Consumer<? super MethodBuilder> methodHandler;
            if (mm.code().isPresent()) {
                CodeModel cm = mm.code().get();
                methodHandler = mb -> mb.transformCode(cm, CodeTransform.ACCEPT_ALL);
            } else {
                methodHandler = _ -> {};
            }
            cb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(),
                mm.flags().flagsMask(), methodHandler);

        });
    }

    /**
     * @return a ClassStructure for a "bridge" component
     */
    public ClassStructure getBridgeClassStructure(String name) {
        assert name.startsWith(XTC_BRIDGE_PREFIX);

        return (ClassStructure) bridgeLoader.module.getChildByPath(
                name.substring(XTC_BRIDGE_PREFIX.length()));
    }

    /**
     * @return a Java class name for a "bridge" component
     */
    public String getBridgeClassName(String name) {
        return bridgeLoader.prefix +
            getBridgeClassStructure(name).getIdentityConstant().getPathString();
    }

    protected Builder ensureBuilder(TypeConstant type, ClassModel model) {
        if (nativeBuilders.get(type) instanceof Class builderClass) {
            try {
                return (Builder) builderClass.getDeclaredConstructor(
                    TypeSystem.class, TypeConstant.class, ClassModel.class).
                        newInstance(this, type, model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return model == null
            ? super.ensureBuilder(type)
            : new AugmentingBuilder(this, type, model);
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

        nativeBuilders.put(pool.typeBoolean(), org.xvm.javajit.builders.BoolBuilder.class);
        nativeBuilders.put(pool.typeInt64(),   org.xvm.javajit.builders.Int64Builder.class);
        nativeBuilders.put(pool.typeModule(),  org.xvm.javajit.builders.AugmentingBuilder.class);
        nativeBuilders.put(pool.typeObject(),  org.xvm.javajit.builders.AugmentingBuilder.class);
        nativeBuilders.put(pool.typeService(), org.xvm.javajit.builders.AugmentingBuilder.class);
        nativeBuilders.put(pool.typeString(),  org.xvm.javajit.builders.StringBuilder.class);

        // pre-register functions used by the native classes

        // prime the function name counter
        ensureJitClassName(pool.typeFunction());

        // xFunction.$0: function void()
        String f0 = ensureJitClassName(
            pool.buildFunctionType(TypeConstant.NO_TYPES, TypeConstant.NO_TYPES));
        assert f0.equals(Builder.N_xFunction + "$$0");
    }
}



