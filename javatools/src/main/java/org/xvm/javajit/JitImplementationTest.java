package org.xvm.javajit;

/**
 * Simple test class to verify JitImplementationFactory behavior.
 * This can be run as a standalone program to test the environment variable logic.
 */
public class JitImplementationTest {
    
    public static void main(String[] args) {
        System.out.println("=== JIT Implementation Factory Test ===");
        System.out.println();
        
        // Print detailed status
        System.out.println(JitImplementationFactory.getDetailedStatus());
        System.out.println();
        
        // Test different scenarios
        System.out.println("=== Test Results ===");
        System.out.println("Implementation: " + JitImplementationFactory.getImplementationInfo());
        System.out.println("Using ByteBuddy: " + JitImplementationFactory.isUsingByteBuddy());
        System.out.println("Using ClassFile API: " + JitImplementationFactory.isUsingClassFileApi());
        
        // Test recommendations
        System.out.println();
        System.out.println("=== Recommendations ===");
        if (JitImplementationFactory.isUsingByteBuddy()) {
            System.out.println("✓ Using ByteBuddy (default) - supports Java 21-24+");
            System.out.println("  To switch to ClassFile API: set environment variable USE_CLASSFILE_API=true");
        } else {
            System.out.println("✓ Using ClassFile API - requires Java 22+");
            System.out.println("  To switch to ByteBuddy: unset USE_CLASSFILE_API or set it to false");
        }
        
        // Java version check
        String javaVersion = System.getProperty("java.version");
        System.out.println();
        System.out.println("Current Java Version: " + javaVersion);
        
        if (javaVersion.startsWith("21")) {
            if (JitImplementationFactory.isUsingClassFileApi()) {
                System.out.println("⚠️  WARNING: Using ClassFile API on Java 21 - this may not work!");
                System.out.println("   Consider using ByteBuddy (default) for Java 21 compatibility");
            } else {
                System.out.println("✓ Good: Using ByteBuddy on Java 21 - fully supported");
            }
        } else if (javaVersion.startsWith("22") || javaVersion.startsWith("23") || javaVersion.startsWith("24")) {
            System.out.println("✓ Good: Both implementations should work on Java " + 
                javaVersion.substring(0, 2));
        }
    }
}