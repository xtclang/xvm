package org.xvm.javajit;

import org.xvm.asm.ModuleStructure;

/**
 * Common interface for JIT TypeSystem implementations.
 * This allows switching between ClassFile API and ByteBuddy implementations
 * without changing the calling code.
 */
public interface JitTypeSystem {
    
    /**
     * Create a Java class for the specified class name.
     *
     * @param moduleLoader the ModuleLoader that contains the structure information for the
     *                     containing module and all its classes
     * @param name         the suffix of the Java class name to create (sans the module prefix)
     *
     * @return the bytes of the class for the specified class name
     */
    byte[] genClass(ModuleLoader moduleLoader, String name);
    
    /**
     * Get the XVM associated with this TypeSystem.
     */
    Xvm getXvm();
    
    /**
     * Get the unique TypeSystem name.
     */
    String getName();
    
    /**
     * Get the TypeSystemLoader for this TypeSystem.
     */
    TypeSystemLoader getLoader();
    
    /**
     * Get the shared ModuleLoaders.
     */
    ModuleLoader[] getShared();
    
    /**
     * Get the owned ModuleLoaders.
     */
    ModuleLoader[] getOwned();
    
    /**
     * Get a string representation of the implementation type.
     */
    String getImplementationType();
}