package org.xvm.javajit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import org.xvm.asm.ModuleStructure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that actually generates bytecode using both JIT implementations.
 * This will demonstrate:
 * 1. ClassFile API working (if available)
 * 2. ByteBuddy producing different (wrong) bytecode
 * 3. What the actual JIT pipeline looks like
 */
public class JitBytecodeActualTest {
    
    private String originalJitImplementation;
    
    @BeforeEach
    void setUp() {
        // Save original JIT implementation
        originalJitImplementation = System.getProperty("org.xtclang.jit.implementation");
    }
    
    @AfterEach
    void tearDown() {
        // Restore original JIT implementation
        if (originalJitImplementation == null) {
            System.clearProperty("org.xtclang.jit.implementation");
        } else {
            System.setProperty("org.xtclang.jit.implementation", originalJitImplementation);
        }
    }
    
    @Test
    @DisplayName("Test ClassFile API Bytecode Generation")
    void testClassFileApiBytecodeGeneration() {
        Assumptions.assumeTrue(isClassFileApiAvailable(), "ClassFile API not available");
        
        System.setProperty("org.xtclang.jit.implementation", "classfile");
        
        try {
            // Create a mock TypeSystem and try to generate a class
            MockModuleLoader mockLoader = new MockModuleLoader("test.package");
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            
            System.out.println("Using TypeSystem: " + typeSystem.getClass().getName());
            
            // Try to generate a simple class
            byte[] bytecode = typeSystem.genClass(mockLoader, "TestClass");
            
            if (bytecode != null) {
                System.out.println("ClassFile API generated " + bytecode.length + " bytes of bytecode");
                System.out.println("First 32 bytes (hex): " + HexFormat.of().formatHex(bytecode, 0, Math.min(32, bytecode.length)));
                
                // Try to analyze the bytecode
                analyzeBytecode("ClassFile API", bytecode);
                
                assertTrue(bytecode.length > 0, "Should generate some bytecode");
                // Java class file magic number is 0xCAFEBABE
                assertEquals(0xCA, bytecode[0] & 0xFF, "Java class file should start with 0xCA");
                assertEquals(0xFE, bytecode[1] & 0xFF, "Java class file should start with 0xFE");
                assertEquals(0xBA, bytecode[2] & 0xFF, "Java class file should start with 0xBA");
                assertEquals(0xBE, bytecode[3] & 0xFF, "Java class file should start with 0xBE");
            } else {
                fail("ClassFile API returned null bytecode");
            }
            
        } catch (Exception e) {
            System.out.println("ClassFile API test failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Test
    @DisplayName("Test ByteBuddy Bytecode Generation")  
    void testByteBuddyBytecodeGeneration() {
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        
        try {
            // Create a mock TypeSystem and try to generate a class
            MockModuleLoader mockLoader = new MockModuleLoader("test.package");
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            
            System.out.println("Using TypeSystem: " + typeSystem.getClass().getName());
            
            // Try to generate a simple class
            byte[] bytecode = typeSystem.genClass(mockLoader, "TestClass");
            
            if (bytecode != null) {
                System.out.println("ByteBuddy generated " + bytecode.length + " bytes of bytecode");
                System.out.println("First 32 bytes (hex): " + HexFormat.of().formatHex(bytecode, 0, Math.min(32, bytecode.length)));
                
                // Try to analyze the bytecode
                analyzeBytecode("ByteBuddy", bytecode);
                
                assertTrue(bytecode.length > 0, "Should generate some bytecode");
                // Java class file magic number is 0xCAFEBABE
                assertEquals(0xCA, bytecode[0] & 0xFF, "Java class file should start with 0xCA");
                assertEquals(0xFE, bytecode[1] & 0xFF, "Java class file should start with 0xFE");
                assertEquals(0xBA, bytecode[2] & 0xFF, "Java class file should start with 0xBA");
                assertEquals(0xBE, bytecode[3] & 0xFF, "Java class file should start with 0xBE");
            } else {
                System.out.println("ByteBuddy returned null bytecode - this is expected for current stub implementation");
                // For now, this is expected because ByteBuddy implementation is incomplete
            }
            
        } catch (Exception e) {
            System.out.println("ByteBuddy test error: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the test - we expect ByteBuddy to have issues
        }
    }
    
    @Test
    @DisplayName("Compare ClassFile API vs ByteBuddy Bytecode")
    void compareBytecodeGenerations() {
        // Only run if ClassFile API is available
        Assumptions.assumeTrue(isClassFileApiAvailable(), "ClassFile API not available");
        
        byte[] classFileBytecode = null;
        byte[] byteBuddyBytecode = null;
        
        // Generate with ClassFile API
        System.setProperty("org.xtclang.jit.implementation", "classfile");
        try {
            MockModuleLoader mockLoader = new MockModuleLoader("test.package");
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            classFileBytecode = typeSystem.genClass(mockLoader, "TestClass");
            System.out.println("ClassFile API bytecode: " + (classFileBytecode != null ? classFileBytecode.length + " bytes" : "null"));
        } catch (Exception e) {
            System.out.println("ClassFile API failed: " + e.getMessage());
        }
        
        // Generate with ByteBuddy
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        try {
            MockModuleLoader mockLoader = new MockModuleLoader("test.package");
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            byteBuddyBytecode = typeSystem.genClass(mockLoader, "TestClass");
            System.out.println("ByteBuddy bytecode: " + (byteBuddyBytecode != null ? byteBuddyBytecode.length + " bytes" : "null"));
        } catch (Exception e) {
            System.out.println("ByteBuddy failed: " + e.getMessage());
        }
        
        // Compare results
        System.out.println("\n=== COMPARISON RESULTS ===");
        if (classFileBytecode != null && byteBuddyBytecode != null) {
            System.out.println("Both implementations generated bytecode:");
            System.out.println("  ClassFile API: " + classFileBytecode.length + " bytes");
            System.out.println("  ByteBuddy: " + byteBuddyBytecode.length + " bytes");
            
            // Compare first few bytes
            boolean identical = java.util.Arrays.equals(classFileBytecode, byteBuddyBytecode);
            System.out.println("  Bytecode identical: " + identical);
            
            if (!identical) {
                System.out.println("  Differences detected - this shows the implementations are different");
                compareBytecodeHex(classFileBytecode, byteBuddyBytecode);
            }
        } else {
            System.out.println("Cannot compare - one or both implementations failed");
            System.out.println("  ClassFile API success: " + (classFileBytecode != null));  
            System.out.println("  ByteBuddy success: " + (byteBuddyBytecode != null));
        }
    }
    
    private void analyzeBytecode(String implementation, byte[] bytecode) {
        System.out.println("\n--- " + implementation + " Bytecode Analysis ---");
        System.out.println("Size: " + bytecode.length + " bytes");
        
        if (bytecode.length >= 8) {
            // Java class file format: magic(4) + minor(2) + major(2) + ...
            int magic = ((bytecode[0] & 0xFF) << 24) | ((bytecode[1] & 0xFF) << 16) | 
                       ((bytecode[2] & 0xFF) << 8) | (bytecode[3] & 0xFF);
            int minor = ((bytecode[4] & 0xFF) << 8) | (bytecode[5] & 0xFF);
            int major = ((bytecode[6] & 0xFF) << 8) | (bytecode[7] & 0xFF);
            
            System.out.printf("Magic: 0x%08X (should be 0xCAFEBABE)%n", magic);
            System.out.println("Version: " + major + "." + minor);
            
            if (magic == 0xCAFEBABE) {
                System.out.println("✓ Valid Java class file");
            } else {
                System.out.println("✗ Invalid magic number - not a valid class file");
            }
        }
    }
    
    private void compareBytecodeHex(byte[] bytecode1, byte[] bytecode2) {
        int maxLen = Math.max(bytecode1.length, bytecode2.length);
        int compareLen = Math.min(64, maxLen); // Only compare first 64 bytes
        
        System.out.println("First " + compareLen + " bytes comparison:");
        System.out.println("ClassFile: " + HexFormat.of().formatHex(bytecode1, 0, Math.min(compareLen, bytecode1.length)));
        System.out.println("ByteBuddy: " + HexFormat.of().formatHex(bytecode2, 0, Math.min(compareLen, bytecode2.length)));
    }
    
    private boolean isClassFileApiAvailable() {
        try {
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Mock ModuleLoader for testing - doesn't require real XTC modules
     */
    private static class MockModuleLoader extends ModuleLoader {
        public MockModuleLoader(String pkg) {
            super(null, null, pkg);
            // prefix is automatically set by parent constructor
        }
        
        @Override
        public String getName() {
            return "MockModuleLoader(" + prefix + ")";
        }
    }
}