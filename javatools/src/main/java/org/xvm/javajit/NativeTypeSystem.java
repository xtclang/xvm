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
     * Create the NativeTypeSystem.
     */
    private NativeTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        super(xvm, shared, owned);

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
     * A cache of native class names keyed by type.
     */
    public final Map<TypeConstant, String> nativeByType = new ConcurrentHashMap<>();

    /**
     * A cache of native types keyed by native class name.
     */
    public final Map<String, TypeConstant> nativeByName = new ConcurrentHashMap<>();

    /**
     * A cache of builders for native classes keyed by type.
     */
    public final Map<TypeConstant, Class> nativeBuilders = new ConcurrentHashMap<>();

    /**
     * Load the native class for the specifed name.
     *
     * @return the class bytes or null if the name does not represent a native class
     */
    public byte[] loadNativeClass(ModuleLoader loader, String name)
            throws ClassNotFoundException {
        if (nativeByName.get(name) instanceof TypeConstant nativeType) {
            try {
                try (InputStream in = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
                    assert in != null;
                    ClassModel model = ClassFile.of().parse(in.readAllBytes());
                    return augmentNativeClass(loader, model, name, nativeType);
                }
            } catch (IOException e) {
                throw new ClassNotFoundException("Missing native class " + name);
            }
        }
        return null;
    }

    /**
     * Augment the existing native class with the Ecstasy methods.
     */
    private byte[] augmentNativeClass(ModuleLoader moduleLoader, ClassModel model,
                                     String className, TypeConstant type) {

        ClassStructure  clz     = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();
        Builder         builder = ensureBuilder(type);
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
                methodHandler = mb -> {};
            }
            cb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(),
                mm.flags().flagsMask(), methodHandler);

        });
    }

    @Override
    protected Builder ensureBuilder(TypeConstant type) {
        if (nativeBuilders.get(type) instanceof Class builderClass) {
            try {
                return (Builder) builderClass.getDeclaredConstructor(
                    TypeSystem.class, TypeConstant.class).newInstance(this, type);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return super.ensureBuilder(type);
    }

    // ----- internal ------------------------------------------------------------------------------

    private void registerNativeClasses() {
        ConstantPool pool = pool();

        nativeByType.put(pool.typeBoolean(), Builder.xBool);
        nativeByType.put(pool.typeInt64(),   Builder.xInt64);
        nativeByType.put(pool.typeModule(),  Builder.xModule);
        nativeByType.put(pool.typeObject(),  Builder.xObj);
        nativeByType.put(pool.typeString(),  Builder.xStr);

        for (Map.Entry<TypeConstant, String > entry : nativeByType.entrySet()) {
            nativeByName.put(entry.getValue(), entry.getKey());
        }

        nativeBuilders.put(pool.typeBoolean(), org.xvm.javajit.builders.BoolBuilder.class);
        nativeBuilders.put(pool.typeInt64(),   org.xvm.javajit.builders.Int64Builder.class);
        nativeBuilders.put(pool.typeModule(),  org.xvm.javajit.builders.IntrinsicBuilder.class);
        nativeBuilders.put(pool.typeObject(),  org.xvm.javajit.builders.IntrinsicBuilder.class);
        nativeBuilders.put(pool.typeString(),  org.xvm.javajit.builders.StringBuilder.class);
    }
}



