package org.xvm.asm;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ratchet for the side-effect-free-toString enhancement: the no-arg {@code TypeInfo.toString()} (the
 * one Java and an IDE debugger call implicitly) must be PURE - header only, so rendering a {@code
 * TypeInfo} never walks members or grows the pool - while the full member dump is preserved on the
 * explicit {@code toString(boolean)} overload that {@code Type.dump()} calls. See
 * docs/reentrancy/plans/side-effect-free-tostring.md.
 */
public class TypeInfoDisplayPurityTest {
    private ConstantPool pool;

    private TypeInfo int64Info() {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");
        var runtime = new Runtime();
        runtime.start();
        pool = NativeContainer.create(runtime, EmbeddingTestSupport.systemRepository(), ErrorListener.RUNTIME).getConstantPool();
        return pool.typeInt64().ensureTypeInfo();   // fully build it first (legitimate population)
    }

    @Test
    public void toStringIsAPureHeaderThatDoesNotGrowThePool() {
        TypeInfo info = int64Info();
        int      size = pool.size();
        String   s    = info.toString();

        assertTrue(s.startsWith("TypeInfo:"), "header shape");
        assertFalse(s.contains("\n- Methods"),
                "pure toString() must NOT dump members (that is the toString(boolean) overload)");
        // repeated rendering must not force anything into the shared pool
        for (int i = 0; i < 5; i++) {
            info.toString();
        }
        assertEquals(size, pool.size(),
                "pure toString() must not grow the shared ConstantPool");
    }

    @Test
    public void explicitToStringOverloadStillGivesTheFullMemberDump() {
        TypeInfo info = int64Info();
        String   dump = info.toString(false);

        assertTrue(dump.startsWith("TypeInfo:"), "starts with the same header");
        assertTrue(dump.contains("\n- Methods"),
                "toString(false) must give the full member dump - this is what Type.dump() returns");
    }
}
