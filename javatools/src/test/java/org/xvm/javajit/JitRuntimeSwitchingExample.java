package org.xvm.javajit;

/**
 * Simple example demonstrating runtime switching between ByteBuddy and ClassFile API implementations.
 * 
 * This shows the recommended pattern for testing both implementations in the same JVM:
 * 
 * Usage in tests:
 * 1. Set system property to switch implementations
 * 2. Use factory methods to create TypeSystem instances  
 * 3. Generate bytecode with both implementations
 * 4. Compare results
 * 5. Restore original property value
 * 
 * Property hierarchy (highest to lowest priority):
 * 1. xdk.properties file (build configuration)
 * 2. System property org.xtclang.jit.implementation (runtime override for tests)
 * 3. Default: "bytebuddy"
 */
public class JitRuntimeSwitchingExample {
    
    private static final String JIT_IMPLEMENTATION_PROPERTY = "org.xtclang.jit.implementation";
    
    /**
     * Example method showing how to test both implementations.
     * This would typically be used in a JUnit test method.
     */
    public static void demonstrateRuntimeSwitching() {
        System.out.println("=== JIT Implementation Runtime Switching Example ===");
        
        // Save original property (in case it was set)
        String originalProperty = System.getProperty(JIT_IMPLEMENTATION_PROPERTY);
        System.out.println("Original property: " + originalProperty);
        
        try {
            // Test ByteBuddy implementation
            System.out.println("\\nStep 1: Testing ByteBuddy implementation");
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
            
            String byteBuddyImpl = JitImplementationFactory.getCurrentImplementationName();
            boolean usingByteBuddy = JitImplementationFactory.isUsingByteBuddy();
            System.out.println("  Current implementation: " + byteBuddyImpl);
            System.out.println("  Using ByteBuddy: " + usingByteBuddy);
            System.out.println("  Using ClassFile: " + JitImplementationFactory.isUsingClassFileApi());
            
            // Generate bytecode with ByteBuddy (would contain actual generation logic)
            System.out.println("  [ByteBuddy bytecode generation would happen here]");
            
            // Test ClassFile API implementation (if available)  
            if (isClassFileApiAvailable()) {
                System.out.println("\\nStep 2: Testing ClassFile API implementation");
                System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
                
                String classFileImpl = JitImplementationFactory.getCurrentImplementationName();
                boolean usingClassFile = JitImplementationFactory.isUsingClassFileApi();
                System.out.println("  Current implementation: " + classFileImpl);
                System.out.println("  Using ClassFile: " + usingClassFile);
                System.out.println("  Using ByteBuddy: " + JitImplementationFactory.isUsingByteBuddy());
                
                // Generate bytecode with ClassFile API (would contain actual generation logic)
                System.out.println("  [ClassFile API bytecode generation would happen here]");
                
                System.out.println("\\nStep 3: Comparing results");
                System.out.println("  [Bytecode comparison would happen here]");
                System.out.println("  [Assert that both implementations produce working bytecode]");
                
            } else {
                System.out.println("\\nStep 2: ClassFile API not available on Java " + 
                    System.getProperty("java.version"));
                System.out.println("  Skipping ClassFile API test");
            }
            
            // Show available implementations
            System.out.println("\\nAvailable implementations:");
            System.out.println(JitImplementationFactory.getAvailableImplementations());
            
        } finally {
            // Always restore original property
            if (originalProperty == null) {
                System.clearProperty(JIT_IMPLEMENTATION_PROPERTY);
                System.out.println("\\nRestored: (no original property)");
            } else {
                System.setProperty(JIT_IMPLEMENTATION_PROPERTY, originalProperty);
                System.out.println("\\nRestored property to: " + originalProperty);
            }
        }
        
        System.out.println("\\n=== Example completed ===");
    }
    
    /**
     * Example bytecode comparison test pattern.
     */
    public static void demonstrateBytecodeComparison() {
        System.out.println("\\n=== Bytecode Comparison Example ===");
        
        String originalProperty = System.getProperty(JIT_IMPLEMENTATION_PROPERTY);
        
        try {
            // Mock parameters (in real test these would be proper test fixtures)
            String className = "TestClass";
            
            // Generate with ByteBuddy
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
            System.out.println("Generating with ByteBuddy...");
            byte[] byteBuddyResult = simulateBytecodeGeneration(className + "_ByteBuddy");
            
            // Generate with ClassFile API (if available)
            byte[] classFileResult = null;
            if (isClassFileApiAvailable()) {
                System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
                System.out.println("Generating with ClassFile API...");
                classFileResult = simulateBytecodeGeneration(className + "_ClassFile");
            }
            
            // Compare results
            System.out.println("\\nResults:");
            System.out.println("  ByteBuddy bytecode: " + byteBuddyResult.length + " bytes");
            if (classFileResult != null) {
                System.out.println("  ClassFile bytecode: " + classFileResult.length + " bytes");
                System.out.println("  Both implementations produced valid bytecode: " + 
                    (byteBuddyResult.length > 0 && classFileResult.length > 0));
            } else {
                System.out.println("  ClassFile API not available - single implementation test");
            }
            
        } finally {
            if (originalProperty == null) {
                System.clearProperty(JIT_IMPLEMENTATION_PROPERTY);
            } else {
                System.setProperty(JIT_IMPLEMENTATION_PROPERTY, originalProperty);
            }
        }
    }
    
    // Helper methods
    
    private static boolean isClassFileApiAvailable() {
        try {
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private static byte[] simulateBytecodeGeneration(String className) {
        // In a real test, this would call:
        // TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(xvm, shared, owned);
        // return typeSystem.genClass(moduleLoader, className);
        
        // For demo purposes, return mock bytecode
        return new byte[]{1, 2, 3, 4, 5}; 
    }
    
    /**
     * Main method for running the example.
     */
    public static void main(String[] args) {
        demonstrateRuntimeSwitching();
        demonstrateBytecodeComparison();
    }
}