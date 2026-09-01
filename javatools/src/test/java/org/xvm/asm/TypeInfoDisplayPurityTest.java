package org.xvm.asm;


import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Gate for the {@code TypeInfo} display split.
 *
 * <p>The no-arg {@code toString()} is the one Java and an IDE debugger call implicitly, so it is the
 * PURE header. The full member dump - which resolves nested identities into the shared
 * {@link ConstantPool}, and with {@code fRuntime} set computes and CACHES optimized method chains -
 * moved to the explicitly-named {@code dump(boolean)}, which nothing calls by accident.</p>
 *
 * <p>Historically the no-arg {@code toString()} delegated straight to the member dump, so hovering a
 * {@code TypeInfo} in the Variables view warmed method chains across the whole type; and because the
 * member walk reaches {@code MethodInfo.isOp()}, which reads the AMBIENT thread-local ConstantPool,
 * it threw {@code NullPointerException} outright on any thread with no pool bound.</p>
 */
public class TypeInfoDisplayPurityTest {
    @Test
    public void toStringIsAPureHeaderAndDumpIsTheFullMemberList() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            ConstantPool pool = DisplayPurityFixture.nativePool(runtime);
            TypeInfo     info = pool.typeInt64().ensureTypeInfo();  // build it first: that is legitimate

            int    cBefore = pool.size();
            String header  = info.toString();

            assertTrue(header.startsWith("TypeInfo:"), header);
            assertFalse(header.contains("\n"),
                    "the pure toString() is a one-line header, not a member dump: " + header);
            assertFalse(header.contains("- Methods"),
                    "the pure toString() must not walk members: " + header);

            for (int i = 0; i < 5; i++) {
                info.toString();
            }
            assertEquals(cBefore, pool.size(),
                    "the pure toString() grew the shared ConstantPool");

            // The full dump is still available, still starts with the same header, and is what
            // Ecstasy's Type.dump() returns. It needs a pool bound to the thread, exactly as the
            // historical toString() did - that is the point: the FORCED rendering may keep its
            // ambient requirements, because nothing reaches it implicitly.
            String dump;
            try (var ignore = ConstantPool.withPool(pool)) {
                dump = info.dump(false);
            }
            // rendered after the dump, so both agree on the lazily-computed "abstract" marker that
            // only the forced path is allowed to compute
            assertTrue(dump.startsWith(info.toString()),
                    "the dump starts with exactly the header toString() produces");
            assertTrue(dump.contains("\n- Methods"),
                    "dump(false) is the full member dump - this is what Type.dump() returns");
            assertTrue(dump.contains("\n- Properties"), "dump(false) lists properties");
        } finally {
            runtime.shutdownXVM();
        }
    }
}
