package org.xvm.javajit.bytebuddy;

import org.xvm.asm.ModuleStructure;
import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystemProvider;
import org.xvm.javajit.Xvm;

/**
 * Service Provider for ByteBuddy based TypeSystem implementation.
 * 
 * This provider creates TypeSystems that use ByteBuddy for bytecode
 * generation (compatible with Java 21-24+).
 */
public class ByteBuddyTypeSystemProvider implements TypeSystemProvider {
    
    @Override
    public String getName() {
        return "bytebuddy";
    }
    
    @Override
    public int getPriority() {
        // Higher priority than ClassFile API since ByteBuddy works on more Java versions
        // and doesn't require preview features
        return 20;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if ByteBuddy is available on the classpath
            Class.forName("net.bytebuddy.ByteBuddy");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public TypeSystem createTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("ByteBuddy is not available on the classpath");
        }
        return new ByteBuddyTypeSystem(xvm, shared, owned);
    }
    
    @Override
    public String getDescription() {
        try {
            // Try to get ByteBuddy version
            Package pkg = Class.forName("net.bytebuddy.ByteBuddy").getPackage();
            String version = pkg != null && pkg.getImplementationVersion() != null 
                ? pkg.getImplementationVersion() 
                : "unknown";
            return "ByteBuddy TypeSystem (Java 21+, version " + version + ") - no preview features required";
        } catch (Exception e) {
            return "ByteBuddy TypeSystem (Java 21+) - no preview features required";
        }
    }
}