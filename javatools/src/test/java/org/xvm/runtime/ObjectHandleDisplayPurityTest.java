package org.xvm.runtime;


import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Focused gate for {@link ObjectHandle#toString()}, the base rendering that nearly every handle in
 * the runtime inherits - so it is the single most-executed display method in a debugger session.
 *
 * <p>It used to compute {@code getComposition().getType().isImmutable()} purely to decide whether to
 * suppress an {@code "immutable "} prefix. On a terminal type {@code isImmutable()} runs
 * {@code resolveTypedefs()} -&gt; {@code ensureResolvedConstant()}, whose body is
 * {@code m_constId = constId = resolved} - a WRITE - and on the formal branches it also interns
 * {@code typeService()} and writes a relation cache. Every Variables-view row of every handle
 * therefore advanced type-resolution state.</p>
 *
 * <p>Pool-size invariance cannot see this on a warmed container (the constants involved are already
 * interned), so this test asserts the mechanism directly: rendering a handle must never ask its
 * composition for a {@link TypeConstant} at all. The composition below counts the request.</p>
 */
public class ObjectHandleDisplayPurityTest {
    @Test
    public void renderingAHandleNeverAsksItsCompositionForATypeConstant() {
        var clz     = new CountingComposition(false);
        var handle  = new GenericHandle(clz);
        String sOut = handle.toString();

        assertTrue(sOut.contains("counting-composition"),
                "the handle should render the composition's own label: " + sOut);
        assertEquals(0, clz.typeRequests(),
                "toString() called getType() on the composition - that is the entry point to "
                + "isImmutable()/resolveTypedefs()/ensureResolvedConstant(), which writes the "
                + "resolved constant back and can intern; a debugger must not do that");
    }

    /**
     * The {@code "immutable "} prefix must still be decided correctly, from the handle's own
     * mutability flag and the composition's format bits rather than from the type.
     */
    @Test
    public void theImmutablePrefixIsStillCorrect() {
        var hFrozen = new GenericHandle(new CountingComposition(false));
        hFrozen.m_fMutable = false;
        assertTrue(hFrozen.toString().startsWith("(immutable "),
                "a frozen handle of a non-const class is still marked immutable: " + hFrozen);

        var hMutable = new GenericHandle(new CountingComposition(false));
        hMutable.m_fMutable = true;
        assertTrue(hMutable.toString().startsWith("(counting-composition"),
                "a mutable handle must not be marked immutable: " + hMutable);

        var hConst = new GenericHandle(new CountingComposition(true));
        hConst.m_fMutable = false;
        assertTrue(hConst.toString().startsWith("(counting-composition"),
                "a const class must not be redundantly prefixed: " + hConst);
    }

    /**
     * A {@link TypeComposition} that records every request for its {@link TypeConstant}. Everything
     * else throws, so any other reach into the type system from a display path shows up as a
     * failure rather than as a silent pass.
     */
    private static final class CountingComposition
            implements TypeComposition {
        CountingComposition(boolean fConst) {
            f_fConst = fConst;
        }

        int typeRequests() {
            return m_cTypeRequests;
        }

        @Override
        public String toString() {
            return "counting-composition";
        }

        @Override
        public boolean isConst() {
            return f_fConst;
        }

        @Override
        public TypeConstant getType() {
            ++m_cTypeRequests;
            throw new AssertionError("a display path must not need the composition's TypeConstant");
        }

        // ----- everything below is out of scope for a display path -------------------------------

        @Override
        public Container getContainer() {
            throw unsupported();
        }

        @Override
        public OpSupport getSupport() {
            throw unsupported();
        }

        @Override
        public ClassTemplate getTemplate() {
            throw unsupported();
        }

        @Override
        public TypeConstant getInceptionType() {
            throw unsupported();
        }

        @Override
        public TypeConstant getBaseType() {
            throw unsupported();
        }

        @Override
        public TypeComposition maskAs(TypeConstant type) {
            throw unsupported();
        }

        @Override
        public TypeComposition revealAs(TypeConstant type) {
            throw unsupported();
        }

        @Override
        public ObjectHandle ensureOrigin(ObjectHandle handle) {
            throw unsupported();
        }

        @Override
        public ObjectHandle ensureAccess(ObjectHandle handle, Access access) {
            throw unsupported();
        }

        @Override
        public TypeComposition ensureAccess(Access access) {
            throw unsupported();
        }

        @Override
        public boolean isStruct() {
            return false;
        }

        @Override
        public MethodStructure ensureAutoInitializer() {
            throw unsupported();
        }

        @Override
        public ObjectHandle[] initializeStructure() {
            return new ObjectHandle[0];
        }

        @Override
        public ClassComposition.FieldInfo getFieldInfo(Object id) {
            throw unsupported();
        }

        @Override
        public boolean makeStructureImmutable(ObjectHandle[] ahField) {
            throw unsupported();
        }

        @Override
        public boolean hasOuter() {
            return false;
        }

        @Override
        public boolean isInjected(PropertyConstant idProp) {
            throw unsupported();
        }

        @Override
        public boolean isAtomic(PropertyConstant idProp) {
            throw unsupported();
        }

        @Override
        public CallChain getMethodCallChain(Object nidMethod) {
            throw unsupported();
        }

        @Override
        public CallChain getPropertyGetterChain(PropertyConstant idProp) {
            throw unsupported();
        }

        @Override
        public CallChain getPropertySetterChain(PropertyConstant idProp) {
            throw unsupported();
        }

        @Override
        public Map<Object, ClassComposition.FieldInfo> getFieldLayout() {
            throw unsupported();
        }

        @Override
        public StringHandle[] getFieldNameArray() {
            throw unsupported();
        }

        @Override
        public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle hValue) {
            throw unsupported();
        }

        @Override
        public ConstantPool getConstantPool() {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "a display path must not reach this far into the type system");
        }

        private final boolean f_fConst;
        private int           m_cTypeRequests;
    }
}
