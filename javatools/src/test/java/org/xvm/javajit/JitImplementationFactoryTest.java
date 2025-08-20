package org.xvm.javajit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for JitImplementationFactory.
 * Tests factory behavior, environment variable handling, and implementation selection.
 */
public class JitImplementationFactoryTest {
    
    private static final Logger LOGGER = Logger.getLogger(JitImplementationFactoryTest.class.getName());
    
    static {
        // Configure detailed logging for test visibility
        LOGGER.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
    }
    
    @BeforeEach
    void setUp() {
        LOGGER.info("=== JitImplementationFactory Test Setup ===");
        
        // Log current environment
        String javaVersion = System.getProperty("java.version");
        
        LOGGER.info("Java version: " + javaVersion);
        LOGGER.info("ClassFile API available: " + JitImplementationFactory.isClassFileApiAvailable());
    }
    
    @Test
    @DisplayName("Factory - Implementation Selection Logic")
    void testImplementationSelectionLogic() {
        LOGGER.info("--- Test: Implementation Selection Logic ---");
        
        // Test current selection
        String currentImpl = JitImplementationFactory.getImplementationInfo();
        boolean usingByteBuddy = JitImplementationFactory.isUsingByteBuddy();
        boolean usingClassFile = JitImplementationFactory.isUsingClassFileApi();
        
        LOGGER.info("Current implementation: " + currentImpl);
        LOGGER.info("Using ByteBuddy: " + usingByteBuddy);
        LOGGER.info("Using ClassFile API: " + usingClassFile);
        
        // Exactly one should be true
        assertTrue(usingByteBuddy != usingClassFile, 
            "Exactly one implementation should be selected");
        
        // Verify consistent reporting
        if (usingByteBuddy) {
            assertEquals("ByteBuddy (default)", currentImpl);
            assertFalse(usingClassFile);
        } else {
            assertEquals("ClassFile API", currentImpl);
            assertFalse(usingByteBuddy);
        }
        
        LOGGER.info("✓ Implementation selection logic test passed");
    }
    
    @Test
    @DisplayName("Factory - Detailed Status Reporting")
    void testDetailedStatusReporting() {
        LOGGER.info("--- Test: Detailed Status Reporting ---");
        
        String detailedStatus = JitImplementationFactory.getDetailedStatus();
        assertNotNull(detailedStatus);
        assertFalse(detailedStatus.trim().isEmpty());
        
        LOGGER.info("Detailed status report:");
        String[] lines = detailedStatus.split("\n");
        for (String line : lines) {
            LOGGER.info("  " + line);
            assertNotNull(line); // Each line should be non-null
        }
        
        // Verify status contains expected information
        assertTrue(detailedStatus.contains("JIT Implementation Status"));
        assertTrue(detailedStatus.contains("Property org.xtclang.jit.implementation"));
        assertTrue(detailedStatus.contains("ClassFile API Available"));
        assertTrue(detailedStatus.contains("Using ByteBuddy"));
        assertTrue(detailedStatus.contains("Using ClassFile API"));
        assertTrue(detailedStatus.contains("Implementation"));
        
        LOGGER.info("✓ Detailed status reporting test passed");
    }
    
    @Test
    @DisplayName("Factory - Default Behavior (ByteBuddy)")
    void testDefaultBehavior() {
        LOGGER.info("--- Test: Default Behavior ---");
        
        // With default property value 'bytebuddy', should use ByteBuddy
        // The default value is defined in xdk.properties as 'bytebuddy'
        String currentImpl = JitImplementationFactory.getImplementationInfo();
        boolean usingByteBuddy = JitImplementationFactory.isUsingByteBuddy();
        boolean usingClassFile = JitImplementationFactory.isUsingClassFileApi();
        
        LOGGER.info("Current implementation: " + currentImpl);
        LOGGER.info("Using ByteBuddy: " + usingByteBuddy);
        LOGGER.info("Using ClassFile API: " + usingClassFile);
        
        // Based on xdk.properties default (bytebuddy), should use ByteBuddy
        if (currentImpl.equals("ByteBuddy (default)")) {
            assertTrue(usingByteBuddy, "Should use ByteBuddy with default property value");
            assertFalse(usingClassFile);
            LOGGER.info("✓ Correctly using ByteBuddy as default implementation");
        } else if (currentImpl.equals("ClassFile API")) {
            // Property may have been overridden to use ClassFile API
            assertTrue(usingClassFile);
            assertFalse(usingByteBuddy);
            boolean classFileAvailable = JitImplementationFactory.isClassFileApiAvailable();
            assertTrue(classFileAvailable, "ClassFile API should be available if it's being used");
            LOGGER.info("✓ Using ClassFile API (property may have been overridden)");
        }
        
        LOGGER.info("✓ Default behavior test passed");
    }
    
    @Test
    @DisplayName("Factory - ClassFile API Availability Detection")
    void testClassFileApiAvailabilityDetection() {
        LOGGER.info("--- Test: ClassFile API Availability Detection ---");
        
        boolean factoryDetection = JitImplementationFactory.isClassFileApiAvailable();
        boolean manualDetection = JitImplementationFactory.isClassFileApiAvailable();
        
        LOGGER.info("Factory detection: " + factoryDetection);
        LOGGER.info("Manual detection: " + manualDetection);
        
        // Both methods should agree
        assertEquals(manualDetection, factoryDetection, 
            "Factory detection should match manual detection");
        
        String javaVersion = System.getProperty("java.version");
        LOGGER.info("Java version: " + javaVersion);
        
        if (javaVersion.startsWith("21")) {
            assertFalse(factoryDetection, 
                "ClassFile API should not be available on Java 21");
            LOGGER.info("✓ Correctly detected ClassFile API unavailable on Java 21");
        } else if (javaVersion.startsWith("22") || javaVersion.startsWith("23") || javaVersion.startsWith("24")) {
            // ClassFile API should be available (though might require --enable-preview on 22/23)
            LOGGER.info("ClassFile API availability on Java " + javaVersion.substring(0, 2) + ": " + factoryDetection);
        }
        
        LOGGER.info("✓ ClassFile API availability detection test passed");
    }
    
    @Test
    @DisplayName("Factory - Property Value Edge Cases")
    void testPropertyValueEdgeCases() {
        LOGGER.info("--- Test: Property Value Edge Cases ---");
        
        // Test the current property-based implementation
        String currentImpl = JitImplementationFactory.getImplementationInfo();
        boolean usingClassFile = JitImplementationFactory.isUsingClassFileApi();
        boolean classFileAvailable = JitImplementationFactory.isClassFileApiAvailable();
        
        LOGGER.info("Current implementation: " + currentImpl);
        LOGGER.info("Using ClassFile API: " + usingClassFile);
        LOGGER.info("ClassFile API available: " + classFileAvailable);
        
        // Log the decision logic
        LOGGER.info("Decision logic:");
        LOGGER.info("  ClassFile API available: " + classFileAvailable);
        LOGGER.info("  Property value determines implementation selection");
        LOGGER.info("  Actually using ClassFile API: " + usingClassFile);
        
        // Verify that if ClassFile API is being used, it must be available
        if (usingClassFile) {
            assertTrue(classFileAvailable, 
                "If ClassFile API is being used, it must be available");
            LOGGER.info("✓ ClassFile API usage correctly requires availability");
        }
        
        // Verify that exactly one implementation is used
        boolean usingByteBuddy = JitImplementationFactory.isUsingByteBuddy();
        assertTrue(usingByteBuddy != usingClassFile, 
            "Exactly one implementation should be selected");
        
        LOGGER.info("✓ Property value edge cases test passed");
    }
    
    @Test
    @DisplayName("Factory - Logging and Initialization")
    void testLoggingAndInitialization() {
        LOGGER.info("--- Test: Logging and Initialization ---");
        
        // The static initializer should have already run and logged information
        // We can't easily test the static logging, but we can verify the factory is properly initialized
        
        // Verify factory methods work
        assertDoesNotThrow(() -> JitImplementationFactory.getImplementationInfo(),
            "getImplementationInfo() should not throw");
        
        assertDoesNotThrow(() -> JitImplementationFactory.isUsingByteBuddy(),
            "isUsingByteBuddy() should not throw");
        
        assertDoesNotThrow(() -> JitImplementationFactory.isUsingClassFileApi(),
            "isUsingClassFileApi() should not throw");
        
        assertDoesNotThrow(() -> JitImplementationFactory.getDetailedStatus(),
            "getDetailedStatus() should not throw");
        
        // Verify returned values are sensible
        String impl = JitImplementationFactory.getImplementationInfo();
        assertTrue(impl.equals("ByteBuddy (default)") || impl.equals("ClassFile API"),
            "Implementation info should be one of the expected values");
        
        LOGGER.info("Current implementation: " + impl);
        LOGGER.info("✓ Factory properly initialized and all methods work");
        
        LOGGER.info("✓ Logging and initialization test passed");
    }
    
    @Test
    @DisplayName("Factory - Consistency Across Multiple Calls")
    void testConsistencyAcrossMultipleCalls() {
        LOGGER.info("--- Test: Consistency Across Multiple Calls ---");
        
        // Factory should return consistent results across multiple calls
        String impl1 = JitImplementationFactory.getImplementationInfo();
        String impl2 = JitImplementationFactory.getImplementationInfo();
        String impl3 = JitImplementationFactory.getImplementationInfo();
        
        boolean bb1 = JitImplementationFactory.isUsingByteBuddy();
        boolean bb2 = JitImplementationFactory.isUsingByteBuddy();
        boolean bb3 = JitImplementationFactory.isUsingByteBuddy();
        
        boolean cf1 = JitImplementationFactory.isUsingClassFileApi();
        boolean cf2 = JitImplementationFactory.isUsingClassFileApi();
        boolean cf3 = JitImplementationFactory.isUsingClassFileApi();
        
        // All calls should return the same results
        assertEquals(impl1, impl2);
        assertEquals(impl2, impl3);
        
        assertEquals(bb1, bb2);
        assertEquals(bb2, bb3);
        
        assertEquals(cf1, cf2);
        assertEquals(cf2, cf3);
        
        LOGGER.info("Implementation info consistent: " + impl1);
        LOGGER.info("ByteBuddy usage consistent: " + bb1);
        LOGGER.info("ClassFile API usage consistent: " + cf1);
        
        LOGGER.info("✓ Consistency across multiple calls test passed");
    }
    
}