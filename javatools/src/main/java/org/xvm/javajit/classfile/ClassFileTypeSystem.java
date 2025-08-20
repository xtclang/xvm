package org.xvm.javajit.classfile;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.Xvm;
import org.xvm.javajit.builders.FunctionBuilder;

/**
 * ClassFile API implementation of TypeSystem.
 * Uses Java ClassFile API for bytecode generation, compatible with Java 22+.
 */
public class ClassFileTypeSystem extends TypeSystem {
    
    public ClassFileTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
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
}