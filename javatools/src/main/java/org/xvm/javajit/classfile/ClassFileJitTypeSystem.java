package org.xvm.javajit.classfile;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitTypeSystem;
import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystemLoader;
import org.xvm.javajit.Xvm;
import org.xvm.javajit.builders.CommonBuilder;
import org.xvm.javajit.builders.FunctionBuilder;
import org.xvm.javajit.builders.ModuleBuilder;

/**
 * ClassFile API implementation of JitTypeSystem.
 * Uses Java 22+ ClassFile API for bytecode generation.
 */
public class ClassFileJitTypeSystem extends TypeSystem implements JitTypeSystem {
    
    public ClassFileJitTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        super(xvm, shared, owned);
    }
    
    @Override
    public byte[] genClass(ModuleLoader moduleLoader, String name) {
        String className = moduleLoader.prefix + name;
        if (className.startsWith(Builder.N_xFunction)) {
            TypeConstant fnType = functionTypes.get(className.substring(Builder.N_xFunction.length() + 1));
            assert fnType != null;
            return ClassFile.of().
                build(ClassDesc.of(className), classBuilder ->
                    new FunctionBuilder(this, fnType).assemblePure(className, classBuilder));
        }

        Artifact art = deduceArtifact(moduleLoader.module, name);
        if (art != null) {
            if (art.id().getComponent() instanceof ClassStructure clz) {
                TypeConstant type      = clz.getCanonicalType();
                Builder      builder   = ensureBuilder(type);
                Consumer<? super ClassBuilder> handler = classBuilder -> {
                    classBuilder.with(SourceFileAttribute.of(clz.getSourceFileName()));
                    switch (art.shape()) {
                        case Impl:
                            builder.assembleImpl(className, classBuilder);
                            break;

                        default:
                            throw new UnsupportedOperationException();
                    }
                };

                // There are other options that can be useful:
                //     StackMapsOption.GENERATE_STACK_MAPS
                //     DeadCodeOption.PATCH_DEAD_CODE
                //     DebugElementsOption.DROP_DEBUG
                //     LineNumbersOption.DROP_LINE_NUMBERS
                // TODO: force some of them or make configurable
                ClassFile classFile = ClassFile.of(
                    ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofClassLoading(loader))
                );

                return classFile.build(ClassDesc.of(className), handler);
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
    
    @Override
    public Xvm getXvm() {
        return xvm;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public TypeSystemLoader getLoader() {
        return loader;
    }
    
    @Override
    public ModuleLoader[] getShared() {
        return shared;
    }
    
    @Override
    public ModuleLoader[] getOwned() {
        return owned;
    }
    
    @Override
    public String getImplementationType() {
        return "ClassFile API";
    }
}