package org.xvm.javajit.classfile;

import java.lang.classfile.ClassBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Builder;
import org.xvm.javajit.Ctx;
import org.xvm.javajit.JitBuilder;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

/**
 * ClassFile API implementation of JitBuilder.
 * Uses Java 22+ ClassFile API for bytecode generation.
 */
public class ClassFileJitBuilder extends Builder implements JitBuilder {
    
    private final TypeSystem typeSystem;
    private final TypeConstant type;
    
    public ClassFileJitBuilder(TypeSystem typeSystem, TypeConstant type) {
        this.typeSystem = typeSystem;
        this.type = type;
    }
    
    @Override
    public void assembleImpl(String className, Object classBuilderObj) {
        if (!(classBuilderObj instanceof ClassBuilder)) {
            throw new IllegalArgumentException("Expected ClassBuilder, got: " + 
                (classBuilderObj != null ? classBuilderObj.getClass().getName() : "null"));
        }
        
        ClassBuilder classBuilder = (ClassBuilder) classBuilderObj;
        super.assembleImpl(className, classBuilder);
    }
    
    @Override
    public void assemblePure(String className, Object classBuilderObj) {
        if (!(classBuilderObj instanceof ClassBuilder)) {
            throw new IllegalArgumentException("Expected ClassBuilder, got: " + 
                (classBuilderObj != null ? classBuilderObj.getClass().getName() : "null"));
        }
        
        ClassBuilder classBuilder = (ClassBuilder) classBuilderObj;
        super.assemblePure(className, classBuilder);
    }
    
    @Override
    public String getImplementationType() {
        return "ClassFile API Builder";
    }
}