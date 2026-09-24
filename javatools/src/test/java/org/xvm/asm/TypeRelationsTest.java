package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.util.Map;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.constants.PendingTypeConstant;
import org.xvm.asm.constants.TypeConstant.Relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TypeRelationsTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rebuildingTheConstantTableReleasesCompletedRelationKeys(boolean reload) throws Exception {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        relations.calculate(right, left, true, () -> Relation.INCOMPATIBLE);

        // Inspect retained keys directly: equal types loaded again have new identities, so a cache
        // miss would not prove that the old keys were released. This avoids GC timing assertions.
        var completed = TypeRelations.class.getDeclaredField("completed");
        completed.setAccessible(true);
        assertEquals(1, ((Map<?, ?>) completed.get(relations)).size());

        if (reload) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                pool.assemble(out);
            }
            try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                pool.disassemble(in);
            }
        } else {
            pool.preRegisterAll();
            pool.register(right);
            pool.postRegisterAll(true);
            assertTrue(right.getPosition() >= 0);
            assertEquals(-1, left.getPosition());
        }
        assertSame(relations, pool.getTypeRelations());
        assertTrue(((Map<?, ?>) completed.get(relations)).isEmpty(),
                "The pool's semantic table must not retain discarded constant keys");
    }

    @Test
    void completedResultsCanBeClearedWithoutResettingConstantIdentity() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        var calls = new AtomicInteger();
        Supplier<Relation> calculation = () -> {
            calls.incrementAndGet();
            return Relation.INCOMPATIBLE;
        };
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, calculation));
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, calculation));
        assertEquals(1, calls.get());
        relations.clear();
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, calculation));
        assertEquals(2, calls.get());
        assertSame(right, pool.typeString());
        assertSame(left, pool.typeChar());
    }

    @Test
    void anotherOwnerCannotUseAnEqualCachedKey() {
        var first = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var second = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = first.getTypeRelations();
        relations.calculate(first.typeString(), first.typeChar(), true, () -> Relation.INCOMPATIBLE);
        assertNotSame(relations, second.getTypeRelations());
        assertThrows(IllegalArgumentException.class, () -> relations.calculate(
                second.typeString(), first.typeChar(), true, () -> Relation.IS_A));
        assertThrows(IllegalArgumentException.class, () -> relations.calculate(
                first.typeString(), second.typeChar(), true, () -> Relation.IS_A));
    }

    @Test
    void unresolvedCompilerOperandsAreNeverRetainedAsCompletedResults() {
        var owner = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var compilation = new FileStructure("Application").getConstantPool();
        var pending = new PendingTypeConstant(compilation, null);
        var relations = owner.getTypeRelations();
        var calls = new AtomicInteger();
        Supplier<Relation> calculation = () -> {
            calls.incrementAndGet();
            return Relation.INCOMPATIBLE;
        };
        assertEquals(Relation.INCOMPATIBLE,
                relations.calculateUnresolved(owner.typeString(), pending, calculation));
        assertEquals(Relation.INCOMPATIBLE,
                relations.calculateUnresolved(owner.typeString(), pending, calculation));
        assertEquals(2, calls.get());
        assertThrows(IllegalArgumentException.class,
                () -> relations.calculate(owner.typeString(), pending, true, calculation));
    }

    @Test
    void failureReleasesTheCalculationAndCanBeRetried() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var failure = new IllegalStateException("injected calculation failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> relations.calculate(
                pool.typeString(), pool.typeChar(), true, () -> { throw failure; })));
        assertEquals(Relation.IS_A, relations.calculate(
                pool.typeString(), pool.typeChar(), true, () -> Relation.IS_A));
    }

    @Test
    void contextSensitiveDependenciesDoNotPoisonAnOuterCacheEntry() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        Supplier<Relation> contextual = () -> relations.calculate(
                pool.typeChar(), pool.typeObject(), false, () -> Relation.INCOMPATIBLE);
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(
                pool.typeString(), pool.typeChar(), true, contextual));
        assertEquals(Relation.IS_A, relations.calculate(
                pool.typeString(), pool.typeChar(), true, () -> Relation.IS_A));
    }

    @Test
    @Timeout(10)
    void aConcurrentReaderCannotObserveAProvisionalRecursionResult() throws Exception {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        var recursed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var independent = new AtomicInteger();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> relations.calculate(right, left, true, () -> {
                assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true,
                        () -> fail("recursive algebra must not be entered")));
                recursed.countDown();
                await(release);
                return Relation.IS_A;
            }));
            try {
                recursed.await();
                assertEquals(Relation.IS_A, relations.calculate(right, left, true, () -> {
                    independent.incrementAndGet();
                    return Relation.IS_A;
                }));
                assertEquals(1, independent.get());
            } finally {
                release.countDown();
            }
            assertEquals(Relation.IS_A, pending.get());
        }
    }

    @Test
    void aCompletedRecursiveRootCanBeCachedAndRecomputedAfterClearing() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        var calls = new AtomicInteger();
        Supplier<Relation> recursive = () -> {
            calls.incrementAndGet();
            return relations.calculate(right, left, true, () -> fail("recursive calculation"));
        };
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, recursive));
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, recursive));
        assertEquals(1, calls.get());
        relations.clear();
        assertEquals(Relation.INCOMPATIBLE, relations.calculate(right, left, true, recursive));
        assertEquals(2, calls.get());
    }

    @Test
    void aNestedResultDependingOnItsAncestorIsNotPublished() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        assertEquals(Relation.IS_A, relations.calculate(right, left, true, () -> {
            assertEquals(Relation.INCOMPATIBLE, relations.calculate(left, right, true,
                    () -> relations.calculate(right, left, true, () -> fail("recursion"))));
            return Relation.IS_A;
        }));
        assertEquals(Relation.IS_A, relations.calculate(left, right, true, () -> Relation.IS_A));
    }

    @Test
    @Timeout(10)
    void invalidationCannotBeUndoneByAnOlderCalculation() throws Exception {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var relations = pool.getTypeRelations();
        var right = pool.typeString();
        var left = pool.typeChar();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> relations.calculate(right, left, true, () -> {
                started.countDown();
                await(release);
                return Relation.INCOMPATIBLE;
            }));
            try {
                started.await();
                relations.clear(right);
            } finally {
                release.countDown();
            }
            assertEquals(Relation.INCOMPATIBLE, pending.get());
            assertEquals(Relation.IS_A, relations.calculate(right, left, true, () -> Relation.IS_A));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
