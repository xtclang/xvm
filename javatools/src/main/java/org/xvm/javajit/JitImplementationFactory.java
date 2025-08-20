package org.xvm.javajit;

import org.xvm.asm.ModuleStructure;
import java.util.ServiceLoader;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Factory for creating TypeSystem implementations using the Service Provider Interface (SPI) pattern.
 * 
 * This factory automatically discovers available TypeSystem implementations using Java's
 * standard ServiceLoader mechanism. Implementations register themselves in:
 * META-INF/services/org.xvm.javajit.TypeSystemProvider
 * 
 * Selection priority:
 * 1. Explicit property: org.xtclang.jit.implementation=<name>
 * 2. Highest priority available implementation
 * 3. First available implementation as fallback
 */
public class JitImplementationFactory {
    
    private static final String JIT_IMPLEMENTATION_PROPERTY = "org.xtclang.jit.implementation";
    
    // Cache of available providers (loaded once)
    private static final List<TypeSystemProvider> AVAILABLE_PROVIDERS = loadAvailableProviders();
    
    // Static initialization to log JDK information
    static {
        logJdkInformation();
    }
    
    /**
     * Load and cache all available TypeSystem providers using SPI.
     */
    private static List<TypeSystemProvider> loadAvailableProviders() {
        List<TypeSystemProvider> providers = new ArrayList<>();
        
        ServiceLoader<TypeSystemProvider> loader = ServiceLoader.load(TypeSystemProvider.class);
        for (TypeSystemProvider provider : loader) {
            if (provider.isAvailable()) {
                providers.add(provider);
            }
        }
        
        // Sort by priority (highest first)
        providers.sort(Comparator.comparingInt(TypeSystemProvider::getPriority).reversed());
        
        return providers;
    }
    
    /**
     * Log JDK version and path information at startup.
     */
    private static void logJdkInformation() {
        try {
            String javaVersion = System.getProperty("java.version");
            String javaHome = System.getProperty("java.home");
            String javaVendor = System.getProperty("java.vendor");
            String javaVmName = System.getProperty("java.vm.name");
            
            System.out.println("=== JIT Implementation Factory Initialization ===");
            System.out.println("Java Version: " + javaVersion);
            System.out.println("Java Vendor: " + javaVendor);
            System.out.println("Java VM: " + javaVmName);
            System.out.println("Java Home (Toolchain): " + javaHome);
            System.out.println("Property " + JIT_IMPLEMENTATION_PROPERTY + ": " + getJitImplementationProperty());
            System.out.println("Current Implementation: " + getCurrentImplementationName());
            System.out.println("Available Providers: " + AVAILABLE_PROVIDERS.size());
            System.out.println("==============================================");
        } catch (Exception e) {
            System.err.println("Warning: Could not log JDK information: " + e.getMessage());
        }
    }
    
    /**
     * Create a TypeSystem instance using the appropriate implementation.
     * Uses SPI to discover and select the best available implementation.
     */
    public static TypeSystem createJitTypeSystem(Xvm xvm, ModuleLoader[] shared, ModuleStructure[] owned) {
        String requestedImpl = getJitImplementationProperty();
        
        // First try to find explicitly requested implementation
        if (requestedImpl != null && !requestedImpl.isEmpty()) {
            for (TypeSystemProvider provider : AVAILABLE_PROVIDERS) {
                if (requestedImpl.equalsIgnoreCase(provider.getName())) {
                    System.out.println("Using explicitly requested TypeSystem: " + provider.getDescription());
                    return provider.createTypeSystem(xvm, shared, owned);
                }
            }
            System.err.println("Warning: Requested TypeSystem '" + requestedImpl + "' not available, using default");
        }
        
        // Fall back to highest priority available implementation
        if (!AVAILABLE_PROVIDERS.isEmpty()) {
            TypeSystemProvider chosen = AVAILABLE_PROVIDERS.get(0);
            System.out.println("Using default TypeSystem: " + chosen.getDescription());
            return chosen.createTypeSystem(xvm, shared, owned);
        }
        
        // No implementations available
        throw new UnsupportedOperationException("No TypeSystem implementations available. " +
            "Ensure ByteBuddy or ClassFile API implementations are on the classpath.");
    }
    
    /**
     * Create a JitBuilder instance using the appropriate implementation.
     */
    public static JitBuilder createJitBuilder(TypeSystem typeSystem, org.xvm.asm.constants.TypeConstant type) {
        if (isUsingClassFileApi()) {
            return new org.xvm.javajit.classfile.ClassFileJitBuilder(typeSystem, type);
        } else {
            // Use ByteBuddy implementation (default)
            return new org.xvm.javajit.bytebuddy.ByteBuddyJitBuilder(typeSystem, type);
        }
    }
    
    /**
     * Read the JIT implementation property from xdk.properties.
     * Falls back to system property if xdk.properties is not available.
     */
    private static String getJitImplementationProperty() {
        try {
            // First try to read from xdk.properties
            java.util.Properties props = new java.util.Properties();
            java.io.InputStream is = JitImplementationFactory.class.getClassLoader()
                .getResourceAsStream("../../../xdk.properties");
            
            if (is == null) {
                // Try alternative path
                is = JitImplementationFactory.class.getClassLoader()
                    .getResourceAsStream("xdk.properties");
            }
            
            if (is != null) {
                props.load(is);
                is.close();
                String value = props.getProperty(JIT_IMPLEMENTATION_PROPERTY);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            // Fall through to system property
        }
        
        // Fall back to system property
        return System.getProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
    }
    
    /**
     * Get information about available implementations.
     */
    public static String getAvailableImplementations() {
        if (AVAILABLE_PROVIDERS.isEmpty()) {
            return "No TypeSystem implementations available";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Available TypeSystem implementations:\n");
        for (int i = 0; i < AVAILABLE_PROVIDERS.size(); i++) {
            TypeSystemProvider provider = AVAILABLE_PROVIDERS.get(i);
            sb.append("  ").append(i + 1).append(". ").append(provider.getDescription());
            sb.append(" (priority: ").append(provider.getPriority()).append(")");
            if (i == 0) sb.append(" [DEFAULT]");
            sb.append("\n");
        }
        return sb.toString().trim();
    }
    
    /**
     * Get the currently selected implementation name.
     */
    public static String getCurrentImplementationName() {
        String requested = getJitImplementationProperty();
        if (requested != null && !requested.isEmpty()) {
            // Check if requested implementation is available
            for (TypeSystemProvider provider : AVAILABLE_PROVIDERS) {
                if (requested.equalsIgnoreCase(provider.getName())) {
                    return provider.getName();
                }
            }
        }
        
        // Return default (highest priority) implementation
        return AVAILABLE_PROVIDERS.isEmpty() ? "none" : AVAILABLE_PROVIDERS.get(0).getName();
    }
    
    /**
     * Check if a specific implementation is being used.
     */
    public static boolean isUsingImplementation(String name) {
        return name.equalsIgnoreCase(getCurrentImplementationName());
    }
    
    /**
     * Get detailed implementation status for debugging.
     */
    public static String getDetailedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("TypeSystem Implementation Status (SPI-based):\n");
        sb.append("  Property ").append(JIT_IMPLEMENTATION_PROPERTY).append(": ").append(getJitImplementationProperty()).append("\n");
        sb.append("  Current Implementation: ").append(getCurrentImplementationName()).append("\n");
        sb.append("  Available Providers: ").append(AVAILABLE_PROVIDERS.size()).append("\n");
        
        for (TypeSystemProvider provider : AVAILABLE_PROVIDERS) {
            sb.append("    - ").append(provider.getName()).append(": ").append(provider.getDescription()).append("\n");
        }
        
        return sb.toString().trim();
    }
    
    // Backward compatibility methods
    public static boolean isUsingByteBuddy() {
        return isUsingImplementation("bytebuddy");
    }
    
    public static boolean isUsingClassFileApi() {
        return isUsingImplementation("classfile");
    }
    
    public static String getImplementationInfo() {
        return getCurrentImplementationName();
    }
}