package org.xvm.javajit.classfile;

import org.xvm.asm.ModuleStructure;
import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystemProvider;
import org.xvm.javajit.Xvm;

/**
 * Service Provider for ClassFile API based TypeSystem implementation.
 * 
 * This provider creates TypeSystems that use Java's ClassFile API
 * for bytecode generation (available on Java 22+).
 */
public class ClassFileTypeSystemProvider implements TypeSystemProvider {
    
    @Override
    public String getName() {
        return "classfile";
    }
    
    @Override
    public int getPriority() {
        // Lower priority than ByteBuddy since ClassFile API may require preview features
        return 10;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if ClassFile API is available
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public TypeSystem createTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("ClassFile API is not available in this Java version");
        }
        return new ClassFileJitTypeSystem(xvm, shared, owned);
    }
    
    @Override
    public String getDescription() {
        String javaVersion = System.getProperty("java.version");
        boolean requiresPreview = javaVersion.startsWith("22") || javaVersion.startsWith("23");
        
        return "ClassFile API TypeSystem (Java 22+)" + 
               (requiresPreview ? " - requires --enable-preview" : "");
    }
}