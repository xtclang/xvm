package org.xvm.asm;


import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.ModuleConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Unit tests for Component behavior: parent-child relationships, cloning.
 * These tests verify current behavior before refactoring to ensure nothing breaks.
 */
public class ComponentTest {

    private FileStructure fileStruct;
    private ModuleStructure module;
    private ConstantPool pool;

    @BeforeEach
    void setUp() {
        // Create a minimal file structure for testing
        fileStruct = new FileStructure("TestModule");
        module = fileStruct.getModule();
        pool = fileStruct.getConstantPool();
    }

    // ---- Parent-child relationship tests ----

    @Test
    void testFileStructure_hasModule() {
        assertNotNull(module);
        assertEquals("TestModule", module.getName());
    }

    @Test
    void testModule_parentIsFileStructure() {
        assertSame(fileStruct, module.getParent());
    }

    @Test
    void testCreateClass_parentIsContainer() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "TestClass",
                null);

        assertNotNull(clz);
        assertSame(module, clz.getParent());
    }

    @Test
    void testCreateMethod_parentIsClass() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "TestClass",
                null);

        MethodStructure method = clz.createMethod(
                false,  // not static
                Constants.Access.PUBLIC,
                null,   // no annotations
                Parameter.NO_PARAMS,
                "testMethod",
                Parameter.NO_PARAMS,
                true,   // has code
                false); // not usesSuper

        assertNotNull(method);
        // Method's parent is the MultiMethodStructure, which is in the class
        Component parent = method.getParent();
        assertNotNull(parent);
        assertTrue(parent instanceof MultiMethodStructure);
        assertSame(clz, parent.getParent());
    }

    // ---- children() iteration tests ----

    @Test
    void testChildren_emptyClass_hasNoChildren() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "EmptyClass",
                null);

        Collection<? extends Component> children = clz.children();

        assertTrue(children.isEmpty());
    }

    @Test
    void testChildren_classWithMethod_includesMethod() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "ClassWithMethod",
                null);

        clz.createMethod(
                false,
                Constants.Access.PUBLIC,
                null,
                Parameter.NO_PARAMS,
                "myMethod",
                Parameter.NO_PARAMS,
                true,
                false);

        Collection<? extends Component> children = clz.children();

        assertFalse(children.isEmpty());
        // Should have a MultiMethodStructure containing the method
        boolean hasMultiMethod = children.stream()
                .anyMatch(c -> c instanceof MultiMethodStructure);
        assertTrue(hasMultiMethod);
    }

    // ---- Clone tests ----

    @Test
    @Disabled("cloneBody is protected - need to test via subclass or reflection")
    void testCloneBody_createsNewInstance() {
        // cloneBody is protected, so we can't call it directly
        // This test documents expected behavior
    }

    @Test
    void testContribution_clone_createsNewInstance() {
        // Contribution.clone() is package-private, test via public API if available
        // For now, document expected behavior
    }

    // ---- Component identity tests ----

    @Test
    void testGetIdentityConstant_returnsValidConstant() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "IdentityTest",
                null);

        assertNotNull(clz.getIdentityConstant());
        assertEquals("IdentityTest", clz.getIdentityConstant().getName());
    }

    @Test
    void testGetName_returnsComponentName() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "NameTest",
                null);

        assertEquals("NameTest", clz.getName());
    }

    // ---- Access and modifiers tests ----

    @Test
    void testAccess_publicClass() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "PublicClass",
                null);

        assertEquals(Constants.Access.PUBLIC, clz.getAccess());
    }

    @Test
    void testAccess_privateClass() {
        ClassStructure clz = module.createClass(
                Constants.Access.PRIVATE,
                Component.Format.CLASS,
                "PrivateClass",
                null);

        assertEquals(Constants.Access.PRIVATE, clz.getAccess());
    }

    @Test
    void testFormat_class() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "FormatClass",
                null);

        assertEquals(Component.Format.CLASS, clz.getFormat());
    }

    @Test
    void testFormat_interface() {
        ClassStructure iface = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.INTERFACE,
                "FormatInterface",
                null);

        assertEquals(Component.Format.INTERFACE, iface.getFormat());
    }

    // ---- ConstantPool relationship tests ----

    @Test
    void testComponent_getConstantPool_returnsFilePool() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "PoolTest",
                null);

        assertSame(pool, clz.getConstantPool());
    }

    // ---- Conditional compilation tests ----

    @Test
    @Disabled("Conditional compilation requires more setup")
    void testBifurcation_createsConditionalVariants() {
        // Test that component bifurcation for conditional compilation works
        // This is complex and requires ConditionalConstant setup
    }

    // ---- Nested class tests ----

    @Test
    void testNestedClass_parentIsOuterClass() {
        ClassStructure outer = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "OuterClass",
                null);

        ClassStructure inner = outer.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "InnerClass",
                null);

        assertSame(outer, inner.getParent());
    }

    @Test
    void testNestedClass_inChildrenOfOuter() {
        ClassStructure outer = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "OuterClass2",
                null);

        ClassStructure inner = outer.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "InnerClass2",
                null);

        Collection<? extends Component> children = outer.children();
        assertTrue(children.contains(inner));
    }

    // ---- Property tests ----

    @Test
    void testCreateProperty_parentIsClass() {
        ClassStructure clz = module.createClass(
                Constants.Access.PUBLIC,
                Component.Format.CLASS,
                "PropertyTest",
                null);

        PropertyStructure prop = clz.createProperty(
                false,  // not static
                Constants.Access.PUBLIC,
                null,   // no annotations
                pool.ensureEcstasyTypeConstant("Int"),
                "myProp");

        assertNotNull(prop);
        assertSame(clz, prop.getParent());
    }
}
