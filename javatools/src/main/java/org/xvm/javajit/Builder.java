package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;

/**
 * Java class builder.
 */
public interface Builder {
    /**
     * Assemble the java class for an "impl" shape.
     */
    void assembleImpl(String className, ClassBuilder builder);

    /**
     * Assemble the java class for a "pure" shape.
     */
    void assemblePure(String className, ClassBuilder builder);
}
