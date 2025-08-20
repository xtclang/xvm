package org.xvm.javajit;

/**
 * Common interface for JIT Builder implementations.
 * This allows switching between ClassFile API and ByteBuddy implementations
 * without changing the calling code.
 */
public interface JitBuilder {
    
    /**
     * Assemble the java class for an "impl" shape.
     */
    void assembleImpl(String className, Object classBuilder);
    
    /**
     * Assemble the java class for a "pure" shape.
     */
    void assemblePure(String className, Object classBuilder);
    
    /**
     * Get a string representation of the implementation type.
     */
    String getImplementationType();
}