package org.xvm.asm;


import org.junit.jupiter.api.Test;

import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A type-COMPARISON corpus: a gate for any change to the {@code ConstantPool} / type system
 * (freeze-on-publish + annex, ListMap changes, adoption, interning). It pins the CERTAIN,
 * change-sensitive properties that such a change could silently break - constant INTERNING identity,
 * order-sensitive parameterized-type EQUALITY (the ListMap-order dependence), basic {@code isA}
 * relations, nullable widening, and {@code TypeInfo} stability - rather than the murky variance
 * edges. Every assertion here was verified to pass against the real type system (a booted native
 * plane over the built XDK); if a pool change flips any of these, that is a regression.
 *
 * <p>NOTE (see type-resolution-testability.md): type resolution cannot be tested in isolation - this
 * necessarily boots a {@code NativeContainer} over the compiled XDK. That is the endemic cost; this
 * corpus is the integration-level gate we do have.</p>
 */
public class TypeComparisonCorpusTest {
    private Runtime         m_runtime;
    private ConstantPool    m_pool;

    private ConstantPool pool() {
        if (m_pool == null) {
            assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                    "compiled XDK system modules are required");
            m_runtime = new Runtime();
            m_runtime.start();
            m_pool = NativeContainer.create(m_runtime, EmbeddingTestSupport.systemRepository())
                    .getConstantPool();
        }
        return m_pool;
    }

    // ----- interning identity: the pool must dedup structurally-identical types ------------------

    @Test
    public void internedTypesAreIdentical() {
        ConstantPool pool = pool();
        // building the same parameterized type twice must return the SAME interned constant
        assertSame(pool.ensureArrayType(pool.typeInt64()),
                   pool.ensureArrayType(pool.typeInt64()),
                   "Array<Int64> must intern to one constant");
        // two spellings of the same type reach the same interned constant
        assertSame(pool.ensureArrayType(pool.typeString()),
                   pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeString()),
                   "ensureArrayType and ensureParameterizedTypeConstant(Array, ...) must agree");
    }

    @Test
    public void structurallyEqualTypesAreEqual() {
        ConstantPool pool = pool();
        TypeConstant a = pool.ensureArrayType(pool.typeInt64());
        TypeConstant b = pool.ensureArrayType(pool.typeInt64());
        assertEquals(a, b, "structurally identical Array<Int64> must be equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal types must have equal hashCode");
    }

    // ----- ORDER sensitivity: the ListMap-order dependence type resolution relies on --------------

    @Test
    public void parameterOrderIsSignificant() {
        ConstantPool pool = pool();
        TypeConstant intFirst = pool.ensureParameterizedTypeConstant(
                pool.typeTuple(), pool.typeInt64(), pool.typeString());
        TypeConstant strFirst = pool.ensureParameterizedTypeConstant(
                pool.typeTuple(), pool.typeString(), pool.typeInt64());
        assertNotEquals(intFirst, strFirst,
                "Tuple<Int64,String> must NOT equal Tuple<String,Int64> (order is significant)");
        assertNotSameRef(intFirst, strFirst);
        // same order interns identically
        assertSame(intFirst, pool.ensureParameterizedTypeConstant(
                pool.typeTuple(), pool.typeInt64(), pool.typeString()),
                "same-order tuple must intern to one constant");
    }

    // ----- basic isA relations (x.isA(y) == x is assignable to y) ---------------------------------

    @Test
    public void isAReflexiveAndToObject() {
        ConstantPool pool = pool();
        assertTrue(pool.typeInt64().isA(pool.typeInt64()), "Int64 isA Int64 (reflexive)");
        assertTrue(pool.typeInt64().isA(pool.typeObject()), "Int64 isA Object");
        assertTrue(pool.typeString().isA(pool.typeObject()), "String isA Object");
        assertFalse(pool.typeInt64().isA(pool.typeString()), "Int64 is NOT a String");
    }

    // ----- nullable widening ----------------------------------------------------------------------

    @Test
    public void nullableRelations() {
        ConstantPool pool = pool();
        TypeConstant tInt  = pool.typeInt64();
        TypeConstant tIntN = tInt.ensureNullable();
        assertTrue(tIntN.isNullable(), "Int64? is nullable");
        assertFalse(tInt.isNullable(), "Int64 is not nullable");
        assertTrue(tInt.isA(tIntN), "Int64 isA Int64? (widening)");
        assertFalse(tIntN.isA(tInt), "Int64? is NOT assignable to Int64 (narrowing)");
    }

    // ----- immutable interning --------------------------------------------------------------------

    @Test
    public void immutableTypeInterns() {
        ConstantPool pool = pool();
        TypeConstant arr = pool.ensureArrayType(pool.typeInt64());
        assertSame(pool.ensureImmutableTypeConstant(arr),
                   pool.ensureImmutableTypeConstant(arr),
                   "immutable Array<Int64> must intern to one constant");
    }

    // ----- TypeInfo stability (characterization) --------------------------------------------------

    @Test
    public void typeInfoIsStable() {
        ConstantPool pool = pool();
        var info1 = pool.typeInt64().ensureTypeInfo();
        assertNotNull(info1, "Int64 TypeInfo must build");
        int members1 = info1.getMethods().size() + info1.getProperties().size();
        assertTrue(members1 > 0, "Int64 TypeInfo must have members");
        // a second request returns a stable TypeInfo with the same member count
        var info2 = pool.typeInt64().ensureTypeInfo();
        int members2 = info2.getMethods().size() + info2.getProperties().size();
        assertEquals(members1, members2, "TypeInfo member count must be stable across requests");
    }

    // ----- helper ---------------------------------------------------------------------------------

    private static void assertNotSameRef(Object a, Object b) {
        assertFalse(a == b, "expected distinct interned constants");
    }
}
