package org.xvm.javajit;


import java.lang.reflect.Modifier;

import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.builders.CommonBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A census of the JIT runtime's known concurrency/reentrancy hazards, in the CloneCensusTest
 * ratchet idiom: each test PINS a currently-unsafe shape so the codebase cannot drift silently.
 * These are not aspirational tests - they assert the defect is still there, with the defect's
 * consequences in the javadoc, so that (a) whoever fixes one gets a loud pointer to update the
 * corresponding master-issue row, and (b) nobody can accidentally reintroduce a fixed one
 * without this file flagging the regression of the census itself.
 *
 * The full inventory with file:line evidence lives in
 * {@code docs/reentrancy/plans/master-issue-submissions.md} rows 21-24; the hazards that live
 * in {@code javatools_jitbridge} (bridge enum singletons initialized from the ambient Ctx,
 * non-final $INSTANCE fields, raw shared $values arrays, nType's captured Ctx) cannot be
 * pinned here because the bridge jar is not on this test classpath - their red harness is part
 * of the issue-row ask. The JIT executes through generated classes and Ctx, not through
 * Frame/ServiceContext/ObjectHandle, so the interpreter-side hardening on this branch does NOT
 * protect it; what the JIT does share is the org.xvm.asm layer, and the pins below on
 * TypeConstant/SignatureConstant/ConstantPool are exactly that shared surface.
 */
public class JitConcurrencyHazardCensusTest {
    /**
     * Master-issue row 22: {@code Ctx} carries the per-logical-thread execution state and the
     * container identity, but its constructor performs no owner-consistency validation at all -
     * it does not even reject null. A Ctx whose {@code xvm} disagrees with its
     * {@code container.xvm} (or a null pair) propagates silently into generated code, where
     * every {@code $ctx()}/{@code $xvm()}/{@code $owner()} resolution then answers for the
     * wrong world. When the fix lands (assert {@code xvm == container.xvm}), this pin fails:
     * flip it into the assertion's regression test and update row 22.
     */
    @Test
    public void ctxConstructionPerformsNoOwnerValidation() {
        var ctx = new Ctx(null, null);

        assertNull(ctx.xvm, "pin: Ctx accepted a null Xvm without validation");
        assertNull(ctx.container, "pin: Ctx accepted a null Container without validation");
    }

    /**
     * Master-issue row 23: {@code ModuleLoader.loadedClasses} is a plain {@code HashMap}
     * written during class definition and swap-iterated by the debug dump, while neither
     * {@code ModuleLoader} nor {@code TypeSystemLoader} registers as parallel-capable and each
     * delegates to the other by calling {@code findClass} DIRECTLY - bypassing the JVM's
     * per-name classloading locks. The first concurrent load through shared loaders corrupts
     * this map or dies in {@code LinkageError: duplicate class definition}. When the map
     * becomes concurrent (or the loaders adopt real locking), this pin fails: update row 23.
     */
    @Test
    public void moduleLoaderClassRegistryIsAPlainHashMap() throws Exception {
        var field = ModuleLoader.class.getDeclaredField("loadedClasses");

        assertEquals(Map.class, field.getType(),
                "pin: loadedClasses is declared as a plain Map (the initializer is an"
                        + " unsynchronized HashMap, ModuleLoader.java) shared across loading"
                        + " threads; a fix would retype it to a concurrent map");
        assertFalse(Modifier.isVolatile(field.getModifiers()),
                "pin: the field is not even volatile, so the debug-dump swap is unsafe too");
    }

    /**
     * Master-issue row 23: {@code CommonBuilder}'s skip sets are process-global mutable
     * {@code HashSet} statics written during code generation. They only dedup log lines, but
     * code generation runs lazily inside JVM class loading, so parallel loads write them
     * concurrently. When they become concurrent collections, this pin fails: update row 23.
     */
    @Test
    public void commonBuilderSkipSetsAreProcessGlobalHashSets() throws Exception {
        for (var name : new String[] {"SKIP_SET", "METHOD_SKIP_SET"}) {
            var field = CommonBuilder.class.getDeclaredField(name);

            assertEquals(HashSet.class, field.getType(),
                    "pin: " + name + " is an unsynchronized process-global HashSet");
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    "pin: " + name + " is static, so the race is process-wide");
        }
    }

    /**
     * Master-issue row 24: the JIT caches generated Java names ON the shared ASM constants -
     * {@code TypeConstant.m_sJitName} and {@code SignatureConstant.m_sJitName} - as lazy
     * non-volatile writes, and the uniquifying suffix comes from a per-Xvm counter. Two Xvms
     * sharing constants see first-writer-wins pollution: the second runtime inherits (or
     * half-reads) the first runtime's name numbering. {@code ConstantPool.m_setJitPrimitives}
     * is the same lazy non-volatile pattern at pool scope. When these become volatile/CAS and
     * Xvm-scoped, this pin fails: update row 24.
     */
    @Test
    public void jitNameCachesOnSharedConstantsAreNonVolatile() throws Exception {
        assertFalse(Modifier.isVolatile(
                        TypeConstant.class.getDeclaredField("m_sJitName").getModifiers()),
                "pin: TypeConstant.m_sJitName is a lazy non-volatile write on a shared constant");
        assertFalse(Modifier.isVolatile(
                        SignatureConstant.class.getDeclaredField("m_sJitName").getModifiers()),
                "pin: SignatureConstant.m_sJitName is a lazy non-volatile write on a shared"
                        + " constant");
        assertFalse(Modifier.isVolatile(
                        ConstantPool.class.getDeclaredField("m_setJitPrimitives").getModifiers()),
                "pin: ConstantPool.m_setJitPrimitives is a lazy non-volatile pool-scoped cache");
    }
}
