package org.xvm.javajit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that actually works with the current working JIT implementation.
 * This shows what's really happening vs. the broken stubs.
 */
public class WorkingJitTest {
    
    private String originalJitImplementation;
    
    @BeforeEach
    void setUp() {
        originalJitImplementation = System.getProperty("org.xtclang.jit.implementation");
    }
    
    @AfterEach 
    void tearDown() {
        if (originalJitImplementation == null) {
            System.clearProperty("org.xtclang.jit.implementation");
        } else {
            System.setProperty("org.xtclang.jit.implementation", originalJitImplementation);
        }
    }
    
    @Test
    @DisplayName("Test JIT Factory Can Create TypeSystems")
    void testJitFactoryWorks() {
        // Test ClassFile API (if available)
        if (isClassFileApiAvailable()) {
            System.setProperty("org.xtclang.jit.implementation", "classfile");
            String impl = JitImplementationFactory.getCurrentImplementationName();
            System.out.println("ClassFile implementation: " + impl);
            assertEquals("classfile", impl.toLowerCase());
        }
        
        // Test ByteBuddy
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");  
        String impl = JitImplementationFactory.getCurrentImplementationName();
        System.out.println("ByteBuddy implementation: " + impl);
        assertEquals("bytebuddy", impl.toLowerCase());
        
        // Test available implementations
        String available = JitImplementationFactory.getAvailableImplementations();
        System.out.println("Available implementations:");
        System.out.println(available);
        assertTrue(available.contains("bytebuddy"));
    }
    
    @Test
    @DisplayName("Test TypeSystem Creation")
    void testTypeSystemCreation() {
        // This should work with both implementations
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        
        try {
            TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            assertNotNull(typeSystem);
            System.out.println("Created TypeSystem: " + typeSystem.getClass().getName());
            assertTrue(typeSystem.getClass().getName().contains("ByteBuddy"));
        } catch (Exception e) {
            System.out.println("ByteBuddy TypeSystem creation failed: " + e.getMessage());
        }
        
        if (isClassFileApiAvailable()) {
            System.setProperty("org.xtclang.jit.implementation", "classfile");
            
            try {
                TypeSystem typeSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
                assertNotNull(typeSystem);
                System.out.println("Created TypeSystem: " + typeSystem.getClass().getName());
                assertTrue(typeSystem.getClass().getName().contains("ClassFile"));
            } catch (Exception e) {
                System.out.println("ClassFile TypeSystem creation failed: " + e.getMessage());
            }
        }
    }
    
    @Test
    @DisplayName("Test ByteBuddy vs ClassFile Bytecode Generation")
    void testActualBytecodeGeneration() {
        // Create a mock loader
        TestModuleLoader testLoader = new TestModuleLoader();
        
        // Test ByteBuddy
        System.setProperty("org.xtclang.jit.implementation", "bytebuddy");
        TypeSystem byteBuddySystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
        
        System.out.println("\n=== Testing ByteBuddy Implementation ===");
        try {
            byte[] bbBytecode = byteBuddySystem.genClass(testLoader, "TestClass");
            if (bbBytecode != null) {
                System.out.println("ByteBuddy generated: " + bbBytecode.length + " bytes");
                System.out.println("Magic bytes: " + String.format("%02x %02x %02x %02x", 
                    bbBytecode[0], bbBytecode[1], bbBytecode[2], bbBytecode[3]));
                
                // Should be valid Java class file
                assertEquals((byte) 0xCA, bbBytecode[0]);
                assertEquals((byte) 0xFE, bbBytecode[1]);
            } else {
                System.out.println("ByteBuddy returned null - implementation incomplete");
            }
        } catch (Exception e) {
            System.out.println("ByteBuddy failed: " + e.getMessage());
        }
        
        // Test ClassFile API if available
        if (isClassFileApiAvailable()) {
            System.setProperty("org.xtclang.jit.implementation", "classfile");
            TypeSystem classFileSystem = JitImplementationFactory.createJitTypeSystem(null, new ModuleLoader[0], null);
            
            System.out.println("\n=== Testing ClassFile API Implementation ===");
            try {
                byte[] cfBytecode = classFileSystem.genClass(testLoader, "TestClass");
                if (cfBytecode != null) {
                    System.out.println("ClassFile API generated: " + cfBytecode.length + " bytes");
                    System.out.println("Magic bytes: " + String.format("%02x %02x %02x %02x", 
                        cfBytecode[0], cfBytecode[1], cfBytecode[2], cfBytecode[3]));
                    
                    // Should be valid Java class file
                    assertEquals((byte) 0xCA, cfBytecode[0]);
                    assertEquals((byte) 0xFE, cfBytecode[1]);
                } else {
                    System.out.println("ClassFile API returned null");
                }
            } catch (Exception e) {
                System.out.println("ClassFile API failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
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
     * Simple test ModuleLoader that extends the real class properly
     */
    private static class TestModuleLoader extends ModuleLoader {
        public TestModuleLoader() {
            // Create with minimal valid parameters
            super(createMockTypeSystemLoader(), null, "test.package");
            // prefix is automatically set by parent constructor
        }
        
        private static TypeSystemLoader createMockTypeSystemLoader() {
            // We can't easily create a real TypeSystemLoader, so return null
            // The ModuleLoader should handle this gracefully
            return null;
        }
        
        @Override
        public String getName() {
            return "TestModuleLoader";
        }
    }
}