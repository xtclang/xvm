package org.xvm.javajit;

import org.xvm.asm.ModuleStructure;

/**
 * Standalone test to demonstrate what the JIT implementations actually do.
 * Run this to see the reality vs. the documentation.
 */
public class JitTestMain {
    
    public static void main(String[] args) {
        System.out.println("=== XVM JIT Implementation Test ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();
        
        // Test 1: Factory Status
        testFactoryStatus();
        
        // Test 2: ClassFile API (if available) 
        testClassFileApi();
        
        // Test 3: ByteBuddy Implementation
        testByteBuddy();
        
        // Test 4: Compare implementations
        compareImplementations();
    }
    
    private static void testFactoryStatus() {
        System.out.println("--- Factory Status ---");
        try {
            String current = JitImplementationFactory.getCurrentImplementationName();
            String available = JitImplementationFactory.getAvailableImplementations();
            String detailed = JitImplementationFactory.getDetailedStatus();
            
            System.out.println("Current Implementation: " + current);
            System.out.println("Available Implementations:");
            System.out.println(available);
            System.out.println("Detailed Status:");
            System.out.println(detailed);
        } catch (Exception e) {
            System.out.println("Factory status failed: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testClassFileApi() {
        System.out.println("--- Testing ClassFile API ---");
        
        if (!isClassFileApiAvailable()) {
            System.out.println("ClassFile API not available on Java " + System.getProperty("java.version"));
            System.out.println();
            return;
        }
        
        System.setProperty("org.xtclang.jit.implementation", "classfile");
        
        try {
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            System.out.println("Created TypeSystem: " + typeSystem.getClass().getName());
            
            // Try to generate bytecode with a mock loader
            TestModuleLoader mockLoader = new TestModuleLoader("test.classfile");
            byte[] bytecode = typeSystem.genClass(mockLoader, "TestClass");
            
            if (bytecode != null) {
                System.out.println("✓ ClassFile API generated " + bytecode.length + " bytes");
                System.out.println("  Magic bytes: " + String.format("0x%02x%02x%02x%02x", 
                    bytecode[0], bytecode[1], bytecode[2], bytecode[3]));
                System.out.println("  Valid class file: " + isValidClassFile(bytecode));
            } else {
                System.out.println("✗ ClassFile API returned null");
            }
        } catch (Exception e) {
            System.out.println("✗ ClassFile API failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void testByteBuddy() {
        System.out.println("--- Testing ByteBuddy ---");
        
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        
        try {
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            System.out.println("Created TypeSystem: " + typeSystem.getClass().getName());
            
            // Try to generate bytecode with a mock loader
            TestModuleLoader mockLoader = new TestModuleLoader("test.bytebuddy");
            byte[] bytecode = typeSystem.genClass(mockLoader, "TestClass");
            
            if (bytecode != null) {
                System.out.println("✓ ByteBuddy generated " + bytecode.length + " bytes");
                System.out.println("  Magic bytes: " + String.format("0x%02x%02x%02x%02x", 
                    bytecode[0], bytecode[1], bytecode[2], bytecode[3]));
                System.out.println("  Valid class file: " + isValidClassFile(bytecode));
            } else {
                System.out.println("✗ ByteBuddy returned null");
            }
        } catch (Exception e) {
            System.out.println("✗ ByteBuddy failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void compareImplementations() {
        System.out.println("--- Implementation Comparison ---");
        
        byte[] classFileBytecode = null;
        byte[] byteBuddyBytecode = null;
        
        // Generate with ClassFile API
        if (isClassFileApiAvailable()) {
            System.setProperty("org.xtclang.jit.implementation", "classfile");
            try {
                TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
                TestModuleLoader mockLoader = new TestModuleLoader("test.compare");
                classFileBytecode = typeSystem.genClass(mockLoader, "CompareClass");
            } catch (Exception e) {
                System.out.println("ClassFile comparison failed: " + e.getMessage());
            }
        }
        
        // Generate with ByteBuddy
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        try {
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            TestModuleLoader mockLoader = new TestModuleLoader("test.compare");
            byteBuddyBytecode = typeSystem.genClass(mockLoader, "CompareClass");
        } catch (Exception e) {
            System.out.println("ByteBuddy comparison failed: " + e.getMessage());
        }
        
        // Compare results
        System.out.println("Comparison Results:");
        System.out.println("  ClassFile API: " + (classFileBytecode != null ? classFileBytecode.length + " bytes" : "null"));
        System.out.println("  ByteBuddy: " + (byteBuddyBytecode != null ? byteBuddyBytecode.length + " bytes" : "null"));
        
        if (classFileBytecode != null && byteBuddyBytecode != null) {
            boolean identical = java.util.Arrays.equals(classFileBytecode, byteBuddyBytecode);
            System.out.println("  Bytecode identical: " + identical);
            
            if (!identical) {
                System.out.println("  This demonstrates the implementations are different!");
                showHexComparison(classFileBytecode, byteBuddyBytecode, 32);
            }
        } else {
            System.out.println("  Cannot compare - one or both implementations failed");
        }
        System.out.println();
    }
    
    private static void showHexComparison(byte[] bytecode1, byte[] bytecode2, int bytes) {
        System.out.println("First " + bytes + " bytes:");
        
        StringBuilder hex1 = new StringBuilder("  ClassFile: ");
        StringBuilder hex2 = new StringBuilder("  ByteBuddy: ");
        
        for (int i = 0; i < bytes && i < bytecode1.length; i++) {
            hex1.append(String.format("%02x ", bytecode1[i] & 0xFF));
        }
        for (int i = 0; i < bytes && i < bytecode2.length; i++) {
            hex2.append(String.format("%02x ", bytecode2[i] & 0xFF));
        }
        
        System.out.println(hex1.toString());
        System.out.println(hex2.toString());
    }
    
    private static boolean isClassFileApiAvailable() {
        try {
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private static boolean isValidClassFile(byte[] bytecode) {
        if (bytecode.length < 4) return false;
        return bytecode[0] == (byte) 0xCA && bytecode[1] == (byte) 0xFE &&
               bytecode[2] == (byte) 0xBA && bytecode[3] == (byte) 0xBE;
    }
    
    /**
     * Mock ModuleLoader for testing
     */
    private static class TestModuleLoader extends ModuleLoader {
        private final String testPrefix;
        
        public TestModuleLoader(String pkg) {
            super(null, null, pkg);
            this.testPrefix = pkg + ".";
        }
        
        @Override
        public String getName() {
            return "TestModuleLoader(" + testPrefix + ")";
        }
        
        public String getTestPrefix() {
            return testPrefix;
        }
    }
}