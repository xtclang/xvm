package org.xvm.javajit;

import org.xvm.asm.ModuleStructure;

/**
 * Service Provider Interface for TypeSystem implementations.
 * 
 * This follows the standard Java SPI pattern used throughout the JDK
 * (e.g., JDBC drivers, logging frameworks, XML parsers).
 * 
 * Implementations should:
 * 1. Implement this interface
 * 2. Provide a no-arg constructor
 * 3. Register in META-INF/services/org.xvm.javajit.TypeSystemProvider
 */
public interface TypeSystemProvider {
    
    /**
     * @return the name of this implementation (e.g., "classfile", "bytebuddy")
     */
    String getName();
    
    /**
     * @return the priority of this provider (higher = preferred when multiple match)
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Check if this implementation is available in the current runtime.
     * 
     * @return true if this implementation can be used
     */
    boolean isAvailable();
    
    /**
     * Create a TypeSystem instance using this implementation.
     * 
     * @param xvm the XVM instance
     * @param shared shared module loaders
     * @param owned owned module structures
     * @return a TypeSystem implementation
     */
    TypeSystem createTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned);
    
    /**
     * @return a description of this implementation
     */
    default String getDescription() {
        return getName() + " TypeSystem implementation";
    }
}