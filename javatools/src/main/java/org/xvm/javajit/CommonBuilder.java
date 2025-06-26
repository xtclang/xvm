package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import org.xvm.asm.Constants;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        implements Builder {
    public CommonBuilder(TypeSystem typeSystem, TypeConstant type) {
        this.typeSystem = typeSystem;
        this.typeInfo   = type.ensureAccess(Constants.Access.PRIVATE).ensureTypeInfo();
    }

    protected final TypeSystem typeSystem;
    protected final TypeInfo   typeInfo;

    @Override
    public void assembleImpl(String className, ClassBuilder builder) {
        builder.withFlags(ClassFile.ACC_PUBLIC);

    }

    @Override
    public void assemblePure(String className, ClassBuilder builder) {
        builder.withFlags(ClassFile.ACC_PUBLIC);
    }
}
