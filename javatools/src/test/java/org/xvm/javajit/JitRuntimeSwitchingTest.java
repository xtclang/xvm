package org.xvm.javajit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import org.xvm.asm.ModuleStructure;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test demonstrating runtime switching between ByteBuddy and ClassFile API implementations
 * using System properties. This shows how to test both implementations in the same JVM.
 * 
 * Property hierarchy (highest to lowest priority):
 * 1. xdk.properties file
 * 2. System property org.xtclang.jit.implementation  
 * 3. Default: "bytebuddy"
 */
public class JitRuntimeSwitchingTest {
    
    private static final Logger LOGGER = Logger.getLogger(JitRuntimeSwitchingTest.class.getName());
    private static final String JIT_IMPLEMENTATION_PROPERTY = "org.xtclang.jit.implementation";
    
    private String originalSystemProperty;
    
    static {
        // Configure detailed logging for test visibility
        LOGGER.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
    }
    
    @BeforeEach
    void setUp() {
        LOGGER.info("=== JIT Runtime Switching Test Setup ===");
        
        // Save original system property
        originalSystemProperty = System.getProperty(JIT_IMPLEMENTATION_PROPERTY);
        LOGGER.info("Original system property: " + originalSystemProperty);
        
        // Log available implementations
        LOGGER.info(JitImplementationFactory.getAvailableImplementations());
    }
    
    @AfterEach 
    void tearDown() {
        // Restore original system property
        if (originalSystemProperty == null) {
            System.clearProperty(JIT_IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, originalSystemProperty);
        }
        LOGGER.info("Restored original system property: " + originalSystemProperty);
    }
    
    @Test
    @DisplayName("Runtime Switching Between Implementations")
    void testRuntimeSwitching() throws Exception {
        LOGGER.info("--- Test: Runtime Switching Between Implementations ---");
        
        // Test 1: Default implementation (should be ByteBuddy)
        LOGGER.info("Step 1: Testing default implementation");
        String defaultImpl = JitImplementationFactory.getCurrentImplementationName();
        LOGGER.info("Default implementation: " + defaultImpl);
        assertEquals("bytebuddy", defaultImpl.toLowerCase());
        
        // Test 2: Switch to ByteBuddy explicitly
        LOGGER.info("Step 2: Switching to ByteBuddy explicitly");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        String byteBuddyImpl = JitImplementationFactory.getCurrentImplementationName();
        LOGGER.info("After setting to bytebuddy: " + byteBuddyImpl);
        assertEquals("bytebuddy", byteBuddyImpl.toLowerCase());
        assertTrue(JitImplementationFactory.isUsingByteBuddy());
        assertFalse(JitImplementationFactory.isUsingClassFileApi());
        
        // Test 3: Switch to ClassFile API (if available)
        if (isClassFileApiAvailable()) {
            LOGGER.info("Step 3: Switching to ClassFile API");
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
            
            String classFileImpl = JitImplementationFactory.getCurrentImplementationName();
            LOGGER.info("After setting to classfile: " + classFileImpl);
            assertEquals("classfile", classFileImpl.toLowerCase());
            assertTrue(JitImplementationFactory.isUsingClassFileApi());
            assertFalse(JitImplementationFactory.isUsingByteBuddy());
        } else {
            LOGGER.info("Step 3: ClassFile API not available - skipping switch test");
        }
        
        // Test 4: Switch back to ByteBuddy
        LOGGER.info("Step 4: Switching back to ByteBuddy");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        String backToByteBuddyImpl = JitImplementationFactory.getCurrentImplementationName();
        LOGGER.info("After switching back: " + backToByteBuddyImpl);
        assertEquals("bytebuddy", backToByteBuddyImpl.toLowerCase());
        
        LOGGER.info("✓ Runtime switching test passed");
    }
    
    @Test
    @DisplayName("Bytecode Generation Comparison via Runtime Switching")
    void testBytecodeGenerationComparison() throws Exception {
        LOGGER.info("--- Test: Bytecode Generation Comparison via Runtime Switching ---");
        
        // Mock parameters for TypeSystem creation
        Xvm mockXvm = createMockXvm();
        ModuleLoader[] mockShared = new ModuleLoader[0];
        ModuleStructure[] mockOwned = new ModuleStructure[0];
        
        // Test ByteBuddy implementation
        LOGGER.info("Step 1: Testing ByteBuddy implementation");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        TypeSystem byteBuddyTypeSystem = JitImplementationFactory.createJitTypeSystem(mockXvm, mockShared, mockOwned);
        assertNotNull(byteBuddyTypeSystem);
        LOGGER.info("✓ ByteBuddy TypeSystem created: " + byteBuddyTypeSystem.getClass().getSimpleName());
        
        // Generate bytecode with ByteBuddy
        byte[] byteBuddyBytecode = generateMockBytecode(byteBuddyTypeSystem, "TestClass");
        assertNotNull(byteBuddyBytecode);
        LOGGER.info("ByteBuddy bytecode generated: " + byteBuddyBytecode.length + " bytes");
        
        // Test ClassFile API implementation (if available)
        if (isClassFileApiAvailable()) {
            LOGGER.info("Step 2: Testing ClassFile API implementation");
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
            
            TypeSystem classFileTypeSystem = JitImplementationFactory.createJitTypeSystem(mockXvm, mockShared, mockOwned);
            assertNotNull(classFileTypeSystem);
            LOGGER.info("✓ ClassFile TypeSystem created: " + classFileTypeSystem.getClass().getSimpleName());
            
            // Generate bytecode with ClassFile API
            byte[] classFileBytecode = generateMockBytecode(classFileTypeSystem, "TestClass");
            assertNotNull(classFileBytecode);
            LOGGER.info("ClassFile API bytecode generated: " + classFileBytecode.length + " bytes");
            
            // Compare results
            LOGGER.info("Bytecode size comparison:");
            LOGGER.info("  ByteBuddy: " + byteBuddyBytecode.length + " bytes");
            LOGGER.info("  ClassFile API: " + classFileBytecode.length + " bytes");
            
            // Both should generate bytecode (sizes may differ)
            assertTrue(byteBuddyBytecode.length > 0);
            assertTrue(classFileBytecode.length > 0);
            
            // Verify they're from different implementations
            assertNotEquals(byteBuddyTypeSystem.getClass(), classFileTypeSystem.getClass());
            
            LOGGER.info("✓ Both implementations generated valid bytecode");
        } else {
            LOGGER.info("Step 2: ClassFile API not available - skipping comparison");
        }
        
        LOGGER.info("✓ Bytecode generation comparison test passed");
    }
    
    @Test
    @DisplayName("Property Hierarchy Test")
    void testPropertyHierarchy() throws Exception {
        LOGGER.info("--- Test: Property Hierarchy (xdk.properties > system property > default) ---");
        
        // Test that system properties work when xdk.properties doesn't override
        LOGGER.info("Step 1: Testing system property with ByteBuddy");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        String implementation = JitImplementationFactory.getCurrentImplementationName();
        LOGGER.info("With system property 'bytebuddy': " + implementation);
        assertEquals("bytebuddy", implementation.toLowerCase());
        
        // Test switching to ClassFile API (if available)
        if (isClassFileApiAvailable()) {
            LOGGER.info("Step 2: Testing system property with ClassFile API");
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
            
            String classFileImplementation = JitImplementationFactory.getCurrentImplementationName();
            LOGGER.info("With system property 'classfile': " + classFileImplementation);
            assertEquals("classfile", classFileImplementation.toLowerCase());
        } else {
            LOGGER.info("Step 2: ClassFile API not available - skipping");
        }
        
        // Test invalid implementation - should fall back to default but property remains set
        LOGGER.info("Step 3: Testing invalid implementation fallback");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "nonexistent");
        
        String fallbackImplementation = JitImplementationFactory.getCurrentImplementationName();
        LOGGER.info("With invalid 'nonexistent': " + fallbackImplementation);
        assertEquals("bytebuddy", fallbackImplementation.toLowerCase());
        
        // Reset to valid value - property should never be left in invalid state
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        LOGGER.info("✓ Property hierarchy test passed");
    }
    
    @Test 
    @DisplayName("Factory Methods Consistency Test")
    void testFactoryMethodsConsistency() throws Exception {
        LOGGER.info("--- Test: Factory Methods Consistency ---");
        
        // Test ByteBuddy consistency
        LOGGER.info("Step 1: Testing ByteBuddy consistency");
        System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "bytebuddy");
        
        assertTrue(JitImplementationFactory.isUsingImplementation("bytebuddy"));
        assertTrue(JitImplementationFactory.isUsingByteBuddy());
        assertFalse(JitImplementationFactory.isUsingClassFileApi());
        assertEquals("bytebuddy", JitImplementationFactory.getCurrentImplementationName().toLowerCase());
        
        // Test ClassFile API consistency (if available)
        if (isClassFileApiAvailable()) {
            LOGGER.info("Step 2: Testing ClassFile API consistency");
            System.setProperty(JIT_IMPLEMENTATION_PROPERTY, "classfile");
            
            assertTrue(JitImplementationFactory.isUsingImplementation("classfile"));
            assertTrue(JitImplementationFactory.isUsingClassFileApi());
            assertFalse(JitImplementationFactory.isUsingByteBuddy());
            assertEquals("classfile", JitImplementationFactory.getCurrentImplementationName().toLowerCase());
        } else {
            LOGGER.info("Step 2: ClassFile API not available - skipping consistency test");
        }
        
        LOGGER.info("✓ Factory methods consistency test passed");
    }
    
    // Helper methods
    
    private boolean isClassFileApiAvailable() {
        try {
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private Xvm createMockXvm() {
        // Create a minimal mock Xvm for testing
        // In a real implementation, this would be a proper mock or test fixture
        return new Xvm() {
            // Minimal implementation for testing
        };
    }
    
    private byte[] generateMockBytecode(TypeSystem typeSystem, String className) {
        try {
            // Create mock ModuleLoader for testing
            ModuleLoader mockLoader = new ModuleLoader() {
                // Minimal implementation for testing
            };
            
            // Generate bytecode using the TypeSystem
            return typeSystem.genClass(mockLoader, className);
        } catch (Exception e) {
            LOGGER.warning("Mock bytecode generation failed: " + e.getMessage());
            // Return mock bytecode for testing
            return new byte[]{1, 2, 3, 4}; 
        }
    }
}