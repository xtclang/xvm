package org.xvm.javajit.intrinsic;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.TypeSystem;

/**
 * Java class builder for "Impl" shape.
 */
public class ImplBuilder {
    public ImplBuilder(TypeSystem typeSystem, TypeInfo typeInfo) {
        this.typeSystem = typeSystem;
        this.typeInfo   = typeInfo;
    }

    private final TypeSystem typeSystem;
    private final TypeInfo   typeInfo;

    /**
     * TODO
     */
    public void assemble(ClassBuilder builder) {
        builder.withFlags(ClassFile.ACC_PUBLIC);

    }
}
