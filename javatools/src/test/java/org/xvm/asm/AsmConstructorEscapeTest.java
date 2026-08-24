package org.xvm.asm;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        // the protected constructor is same-package accessible, so no subclass probe is
        // needed; the no-overridable-call-during-construction half is enforced at compile
        // time by the fatal -Xlint:this-escape gate, which is what lets MethodStructure be
        // final. What remains observable: the constructor-safe path initializes the
        // conditional-return metadata, and the mutator still works post-construction.
        var method = new MethodStructure(mm, Component.Format.METHOD.ordinal()
                | Constants.Access.PUBLIC.FLAGS, pool.ensureMethodConstant(
                        mm.getIdentityConstant(), sig), null, Annotation.NO_ANNOTATIONS,
                returns, Parameter.NO_PARAMS, true, false);

        assertTrue(method.isConditionalReturn());

        method.setConditionalReturn(false);

        assertFalse(method.isConditionalReturn());
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
        // same-package protected constructor; the no-overridable-call half is enforced by the
        // fatal -Xlint:this-escape gate (which lets PropertyStructure be final), so what this
        // pins is the surviving half: construction initializes the metadata directly, and the
        // public mutators still work after construction
        var property = new PropertyStructure(clz,
                Component.Format.PROPERTY.ordinal() | Constants.Access.PUBLIC.FLAGS,
                pool.ensurePropertyConstant(clz.getIdentityConstant(), "value"), null,
                Constants.Access.PRIVATE, pool.typeString());

        assertSame(pool.typeString(), property.getType());
        assertEquals(Constants.Access.PRIVATE, property.getVarAccess());

        property.setVarAccess(Constants.Access.PUBLIC);
        property.setType(pool.typeBoolean());

        assertEquals(Constants.Access.PUBLIC, property.getVarAccess());
        assertSame(pool.typeBoolean(), property.getType());
    }

    /**
     * Constant constructors must validate parents without overridable construction callbacks.
     * The no-overridable-call half is enforced at compile time now: the fatal
     * {@code -Xlint:this-escape} gate refuses a virtual call during construction, and
     * {@code PropertyConstant} is sealed with {@code FormalTypeChildConstant} final, so the
     * hook-counting subclass probes this test used to construct can no longer exist - which is
     * the point. What remains observable is the surviving half: parent validation still runs,
     * non-virtually, inside the constructor.
     */
    @Test
    public void propertyConstantConstructorsDoNotCallOverridableParentCheck() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);

        var property = new PropertyConstant(pool, clz.getIdentityConstant(), "value");
        assertSame(clz.getIdentityConstant(), property.getParentConstant());

        // the validation moved into a non-virtual path rather than being deleted: an illegal
        // parent is still rejected inside the constructor
        var multi = new MultiMethodConstant(pool, clz.getIdentityConstant(), "mm");
        assertThrows(IllegalArgumentException.class,
                () -> new PropertyConstant(pool, multi, "value"),
                "constructor-time parent validation must survive the de-virtualization");

        var formal = (FormalConstant) clz.addTypeParam(
                "Element", pool.typeObject()).getIdentityConstant();
        var formalChild = new FormalTypeChildConstant(pool, formal, "Value");
        assertSame(formal, formalChild.getParentConstant());
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
     * Method cloning used to corrupt both sides of the copy: `Parameter.cloneBody()` cleared the
     * source parameter's implicit-deref flag and copied the source method's cached deref register
     * into the clone. That was broken even on one thread because replacing a method temporarily
     * changed the original method's parameter semantics. It is also hostile to reentrant owners
     * because the clone can keep a register allocated by the source method. The copy must preserve
     * logical parameter metadata while dropping method-owned transient register state.
     */
    @Test
    public void methodClonePreservesSourceDerefStateAndGetsFreshCloneState() {
        var pool  = new FileStructure("test").getConstantPool();
        var type  = pool.typeString();
        var param = new Parameter(pool, type, "value", null, false, 0, false);
        var sourceDeref = new Register(type, "value", 0);
        setParameterField(param, "m_fImplicitDeref", true);
        setParameterField(param, "m_regDeref", sourceDeref);

        Parameter[] params = {param};
        var method     = createCloneableMethod(pool, Parameter.NO_PARAMS, params);
        var clone      = (MethodStructure) method.cloneBody();
        var cloneParam = clone.getParam(0);

        assertTrue(param.isImplicitDeref());
        assertSame(sourceDeref, getParameterField(param, "m_regDeref"));
        assertTrue(cloneParam.isImplicitDeref());
        assertNull(getParameterField(cloneParam, "m_regDeref"));
    }

    /**
     * `MethodStructure.cloneBody()` copied parameters but assigned their containing structure back
     * to the source method. Any later owner-sensitive parameter helper would therefore resolve
     * through the wrong method hierarchy after a temporary replacement. The cloned parameters and
     * returns must be owned by the cloned method.
     */
    @Test
    public void methodCloneAttachesCopiedParametersToClone() {
        var pool = new FileStructure("test").getConstantPool();
        Parameter[] returns = {
                new Parameter(pool, pool.typeString(), "result", null, true, 0, false)};
        Parameter[] params  = {
                new Parameter(pool, pool.typeString(), "value", null, false, 0, false)};
        var method = createCloneableMethod(pool, returns, params);

        var clone = (MethodStructure) method.cloneBody();

        assertSame(clone, clone.getReturn(0).getContaining());
        assertSame(clone, clone.getParam(0).getContaining());
    }

    /**
     * Synthetic delegated methods used to clone only the parameter arrays and share the mutable
     * `Parameter` elements with the source method. That is a real owner bug: a delegated method
     * could observe or overwrite source-method transient deref state. The delegated-method factory
     * must copy the elements for the new method owner before publishing the synthetic method.
     */
    @Test
    public void delegatedMethodFactoryCopiesParameterElementsForNewOwner() {
        var pool = new FileStructure("test").getConstantPool();
        var type = pool.typeString();
        var sourceDeref = new Register(type, "value", 0);
        var sourceReturn = new Parameter(pool, type, "result", null, true, 0, false);
        var sourceParam  = new Parameter(pool, type, "value", null, false, 0, false);
        setParameterField(sourceParam, "m_fImplicitDeref", true);
        setParameterField(sourceParam, "m_regDeref", sourceDeref);
        var multimethod = createMultiMethod(pool, "delegated");

        var delegated = multimethod.createMethodCopyingParameters(false, Constants.Access.PUBLIC,
                null, new Parameter[] {sourceReturn}, new Parameter[] {sourceParam}, true, false);

        assertNotSame(sourceReturn, delegated.getReturn(0));
        assertNotSame(sourceParam, delegated.getParam(0));
        assertSame(delegated, delegated.getReturn(0).getContaining());
        assertSame(delegated, delegated.getParam(0).getContaining());
        assertTrue(delegated.getParam(0).isImplicitDeref());
        assertNull(getParameterField(delegated.getParam(0), "m_regDeref"));
        assertSame(sourceDeref, getParameterField(sourceParam, "m_regDeref"));
    }

    private static MethodStructure createCloneableMethod(
            ConstantPool pool, Parameter[] returns, Parameter[] params) {
        var mm  = createMultiMethod(pool, "method");
        var sig = pool.ensureSignatureConstant(
                "method", toTypes(params), toTypes(returns));
        // the protected constructor and cloneBody() are same-package accessible: no subclass
        // is needed, which is what lets MethodStructure be final
        return new MethodStructure(mm, Component.Format.METHOD.ordinal()
                | Constants.Access.PUBLIC.FLAGS, pool.ensureMethodConstant(
                        mm.getIdentityConstant(), sig), null, Annotation.NO_ANNOTATIONS,
                returns, params, true, false);
    }

    private static MultiMethodStructure createMultiMethod(ConstantPool pool, String name) {
        var file = pool.getFileStructure();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        return new MultiMethodStructure(clz, Component.Format.MULTIMETHOD.ordinal(),
                pool.ensureMultiMethodConstant(clz.getIdentityConstant(), name), null);
    }

    private static TypeConstant[] toTypes(Parameter[] params) {
        return Arrays.stream(params)
                .map(Parameter::getType)
                .toArray(TypeConstant[]::new);
    }

    private static void setParameterField(Parameter param, String name, Object value) {
        try {
            Field field = Parameter.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(param, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object getParameterField(Parameter param, String name) {
        try {
            Field field = Parameter.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(param);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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


}
