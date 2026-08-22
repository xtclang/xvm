package org.xvm.asm;


import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for ASM constructor rewrites that remove construction-time callbacks.
 */
public class AsmConstructorEscapeTest {
    /**
     * FileStructure construction now builds root ownership without subclass extension points. This
     * verifies the factory-safe shape still produces the same module/file envelope.
     */
    @Test
    public void fileStructureRemainsRootEnvelope() {
        var file = new FileStructure("test");

        assertTrue(Modifier.isFinal(FileStructure.class.getModifiers()));
        assertSame(file, file.getConstantPool().getFileStructure());
        assertSame(file, file.getModule().getFileStructure());
        assertEquals("test", file.getModuleId().getName());
    }

    /**
     * Moving constructor-time conditional-return initialization out of overridable paths must not
     * change the observable conditional-return metadata.
     */
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

    /**
     * Constructor code must not dispatch to overridable conditional-return hooks while MethodStructure
     * is partial. That was unsafe even before adding parallel construction.
     */
    @Test
    public void methodConstructorDoesNotCallOverridableConditionalReturn() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        var mm = new MultiMethodStructure(clz, Component.Format.MULTIMETHOD.ordinal(),
                pool.ensureMultiMethodConstant(clz.getIdentityConstant(), "conditional"), null);

        Parameter[] returns = {
                new Parameter(pool, pool.typeBoolean(), null, null, true, 0, true)};
        TypeConstant[] returnTypes = {pool.typeBoolean()};
        var sig = pool.ensureSignatureConstant(
                "conditional", ConstantPool.NO_TYPES, returnTypes);
        var method = new HookDetectingMethodStructure(mm, Component.Format.METHOD.ordinal()
                | Constants.Access.PUBLIC.FLAGS, pool.ensureMethodConstant(
                        mm.getIdentityConstant(), sig), returns);

        assertTrue(method.isConditionalReturn());
        assertEquals(0, method.conditionalReturnCalls);

        method.setConditionalReturn(false);

        assertFalse(method.isConditionalReturn());
        assertEquals(1, method.conditionalReturnCalls);
    }

    /**
     * Property constructor cleanup must preserve the original type and var-access metadata while
     * avoiding mutation through overridable methods during construction.
     */
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

    /**
     * Property construction must not call overridable mutators before the object is complete. A
     * single reentrant hook could otherwise observe partially assigned property state.
     */
    @Test
    public void propertyConstructorDoesNotCallOverridableMutators() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        var property = new HookDetectingPropertyStructure(clz,
                Component.Format.PROPERTY.ordinal() | Constants.Access.PUBLIC.FLAGS,
                pool.ensurePropertyConstant(clz.getIdentityConstant(), "value"),
                Constants.Access.PRIVATE, pool.typeString());

        assertSame(pool.typeString(), property.getType());
        assertEquals(Constants.Access.PRIVATE, property.getVarAccess());
        assertEquals(0, property.setVarAccessCalls);
        assertEquals(0, property.setTypeCalls);

        property.setVarAccess(Constants.Access.PUBLIC);
        property.setType(pool.typeBoolean());

        assertEquals(Constants.Access.PUBLIC, property.getVarAccess());
        assertSame(pool.typeBoolean(), property.getType());
        assertEquals(1, property.setVarAccessCalls);
        assertEquals(1, property.setTypeCalls);
    }

    /**
     * Constant constructors must validate parents without overridable construction callbacks. This
     * preserves legal property/formal-child behavior while removing `this` escape hazards.
     */
    @Test
    public void propertyConstantConstructorsDoNotCallOverridableParentCheck() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);

        var property = new HookDetectingPropertyConstant(
                pool, clz.getIdentityConstant(), "value");

        assertSame(clz.getIdentityConstant(), property.getParentConstant());
        assertEquals(0, property.checkParentCalls);

        property.checkAgain(clz.getIdentityConstant());

        assertEquals(1, property.checkParentCalls);

        var formal = (FormalConstant) clz.addTypeParam(
                "Element", pool.typeObject()).getIdentityConstant();
        var formalChild = new HookDetectingFormalTypeChildConstant(pool, formal, "Value");

        assertSame(formal, formalChild.getParentConstant());
        assertEquals(0, formalChild.checkParentCalls);

        formalChild.checkAgain(formal);

        assertEquals(1, formalChild.checkParentCalls);
    }

    /**
     * TypeInfo placeholder construction was rewritten to avoid constructor callbacks. This verifies
     * that the legacy string form and cached placeholder behavior remain unchanged.
     */
    @Test
    public void typeInfoPlaceholderKeepsLegacyStringAndCacheBehavior() {
        var pool = new FileStructure("test").getConstantPool();
        var placeholder = pool.infoPlaceholder();

        assertSame(placeholder, pool.infoPlaceholder());
        assertEquals("Placeholder", placeholder.toString());
        assertEquals("Placeholder", placeholder.toString());
    }

    /**
     * VersionTree construction must not call overridable `clear()` while base fields are partial.
     * The test proves the constructor-safe path still initializes the same empty tree.
     */
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

    private static final class HookDetectingMethodStructure extends MethodStructure {
        HookDetectingMethodStructure(
                XvmStructure    parent,
                int             flags,
                MethodConstant  id,
                Parameter[]     returns) {
            super(parent, flags, id, null, Annotation.NO_ANNOTATIONS, returns,
                    Parameter.NO_PARAMS, true, false);
        }

        @Override
        public void setConditionalReturn(boolean conditional) {
            if (!constructed) {
                throw new IllegalStateException(
                        "setConditionalReturn called before subclass construction");
            }

            ++conditionalReturnCalls;
            super.setConditionalReturn(conditional);
        }

        private boolean constructed = true;
        private int conditionalReturnCalls;
    }

    private static final class HookDetectingPropertyStructure extends PropertyStructure {
        HookDetectingPropertyStructure(
                XvmStructure       parent,
                int                flags,
                PropertyConstant   id,
                Constants.Access   varAccess,
                TypeConstant       type) {
            super(parent, flags, id, null, varAccess, type);
        }

        @Override
        public void setVarAccess(Constants.Access access) {
            if (!constructed) {
                throw new IllegalStateException(
                        "setVarAccess called before subclass construction");
            }

            ++setVarAccessCalls;
            super.setVarAccess(access);
        }

        @Override
        public void setType(TypeConstant type) {
            if (!constructed) {
                throw new IllegalStateException(
                        "setType called before subclass construction");
            }

            ++setTypeCalls;
            super.setType(type);
        }

        private boolean constructed = true;
        private int setVarAccessCalls;
        private int setTypeCalls;
    }

    private static final class HookDetectingPropertyConstant extends PropertyConstant {
        HookDetectingPropertyConstant(
                ConstantPool      pool,
                IdentityConstant  parent,
                String            name) {
            super(pool, parent, name);
        }

        void checkAgain(IdentityConstant parent) {
            checkParent(parent);
        }

        @Override
        protected void checkParent(IdentityConstant parent) {
            if (!constructed) {
                throw new IllegalStateException(
                        "checkParent called before subclass construction");
            }

            ++checkParentCalls;
            super.checkParent(parent);
        }

        private boolean constructed = true;
        private int checkParentCalls;
    }

    private static final class HookDetectingFormalTypeChildConstant
            extends FormalTypeChildConstant {
        HookDetectingFormalTypeChildConstant(
                ConstantPool    pool,
                FormalConstant  parent,
                String          name) {
            super(pool, parent, name);
        }

        void checkAgain(IdentityConstant parent) {
            checkParent(parent);
        }

        @Override
        protected void checkParent(IdentityConstant parent) {
            if (!constructed) {
                throw new IllegalStateException(
                        "checkParent called before subclass construction");
            }

            ++checkParentCalls;
            super.checkParent(parent);
        }

        private boolean constructed = true;
        private int checkParentCalls;
    }
}
