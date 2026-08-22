package org.xvm.asm;


import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for ASM constructor rewrites that remove construction-time callbacks.
 */
public class AsmConstructorEscapeTest {
    @Test
    public void fileStructureRemainsRootEnvelope() {
        var file = new FileStructure("test");

        assertTrue(Modifier.isFinal(FileStructure.class.getModifiers()));
        assertSame(file, file.getConstantPool().getFileStructure());
        assertSame(file, file.getModule().getFileStructure());
        assertEquals("test", file.getModuleId().getName());
    }

    @Test
    public void methodConstructionPreservesConditionalReturnShape() {
        var file    = new FileStructure("test");
        var pool    = file.getConstantPool();
        var clz     = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        var returns = new Parameter[] {
                new Parameter(pool, pool.typeBoolean(), null, null, true, 0, true)};

        var method = clz.createMethod(false, Constants.Access.PUBLIC, null,
                returns, "conditional", Parameter.NO_PARAMS, true, false);

        assertTrue(method.isConditionalReturn());
        assertEquals(1, method.getReturnCount());
        assertSame(returns[0], method.getReturn(0));
        assertTrue(method.getReturn(0).isConditionalReturn());
    }

    @Test
    public void propertyConstructionPreservesTypeAndVarAccess() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);

        var property = clz.createProperty(false, Constants.Access.PUBLIC,
                Constants.Access.PRIVATE, pool.typeString(), "value");

        assertEquals(pool.typeString(), property.getType());
        assertEquals(Constants.Access.PRIVATE, property.getVarAccess());
    }

    @Test
    public void typeInfoPlaceholderKeepsLegacyStringAndCacheBehavior() {
        var pool = new FileStructure("test").getConstantPool();
        var placeholder = pool.infoPlaceholder();

        assertSame(placeholder, pool.infoPlaceholder());
        assertEquals("Placeholder", placeholder.toString());
        assertEquals("Placeholder", placeholder.toString());
    }

    @Test
    public void versionTreeConstructorDoesNotCallOverridableClear() {
        var tree = new ClearTrackingVersionTree<String>();

        assertTrue(tree.isEmpty());
        assertEquals(0, tree.clearCalls);

        tree.put(new Version("1.0"), "one");
        assertFalse(tree.isEmpty());
        tree.clear();

        assertTrue(tree.isEmpty());
        assertEquals(1, tree.clearCalls);
    }

    private static final class ClearTrackingVersionTree<V> extends VersionTree<V> {
        @Override
        public void clear() {
            if (!constructed) {
                throw new IllegalStateException("clear called before subclass construction");
            }

            ++clearCalls;
            super.clear();
        }

        private boolean constructed = true;
        private int clearCalls;
    }
}
