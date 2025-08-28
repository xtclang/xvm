package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

import java.util.List;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for Int64 type.
 */
public class Int64Builder extends AugmentingBuilder {

    public Int64Builder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }

    @Override
    protected void assembleInitializer(String className, ClassBuilder classBuilder, List<PropertyInfo> props) {
        // no standard default constructor for Int64
    }
}
