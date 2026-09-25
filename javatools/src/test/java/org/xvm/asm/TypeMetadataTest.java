package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.util.List;
import java.util.Map;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.TypeMetadata.MemberType;

import org.xvm.asm.constants.PendingTypeConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeConstant.Usage;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.Progress;
import org.xvm.asm.constants.TypeInfoReal;

import org.xvm.runtime.RuntimeTypeContext;

import org.xvm.util.ListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TypeMetadataTest {
    @Test
    void varianceIncludesAccessNameAndDirection() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var type = pool.typeString();
        assertFalse(metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.NO));
        assertTrue(metadata.variance(type, "T", Access.PRIVATE, true, () -> Usage.YES));
        assertTrue(metadata.variance(type, "T", Access.PUBLIC, false, () -> Usage.YES));
        assertTrue(metadata.variance(type, "U", Access.PUBLIC, true, () -> Usage.YES));
        assertFalse(metadata.variance(type, "T", Access.PUBLIC, true, () -> fail("cache miss")));
    }

    @Test
    void foreignEqualKeysAreRejectedBeforeLookup() {
        var first = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var second = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = first.getTypeMetadata();
        metadata.normalize(first.typeString(), first::typeString);
        assertNotSame(metadata, second.getTypeMetadata());
        assertThrows(IllegalArgumentException.class,
                () -> metadata.normalize(second.typeString(), second::typeString));
        assertThrows(IllegalArgumentException.class,
                () -> metadata.resolve(first.typeString(), second.typeString(), first::typeString));
    }

    @Test
    void normalizationAndResolutionHaveIndependentCompleteKeys() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var type = pool.typeString();
        assertSame(type, metadata.normalize(type, () -> type));
        assertSame(pool.typeChar(), metadata.resolve(type, pool.typeObject(), pool::typeChar));
        assertSame(pool.typeChar(), metadata.resolve(type, pool.typeObject(), () -> fail("cache miss")));
        assertSame(pool.typeInt64(), metadata.resolve(type, pool.typeChar(), pool::typeInt64));
        metadata.clear();
        assertSame(type, metadata.normalize(type, () -> type));
        assertSame(pool.typeObject(), metadata.resolve(type, pool.typeObject(), pool::typeObject));
    }

    @Test
    void runtimeResultsMustBelongToTheSelectedDescriptorOwner() {
        var file = new FileStructure("MetadataOwner");
        // Descriptor validation follows constant names to String's type, so include a system
        // module stub just as a linked image would; no compiled distribution is needed.
        file.merge(new FileStructure(Constants.ECSTASY_MODULE).getModule(), false, false);
        var structure = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Value", null);
        var imageType = structure.getIdentityConstant().getType();
        var context = new RuntimeTypeContext(file.getConstantPool());
        var type = context.intern(imageType);
        var metadata = context.getDescriptorPool().getTypeMetadata();
        assertThrows(IllegalArgumentException.class, () -> metadata.normalize(type, () -> imageType));
        assertThrows(IllegalArgumentException.class, () -> metadata.resolve(type, type, () -> imageType));
        assertSame(type, metadata.normalize(type, () -> type));
        assertSame(type, metadata.resolve(type, type, () -> type));
    }

    @Test
    void upstreamCompilerResultsAreReturnedWithoutRetention() {
        var first = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var upstream = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = first.getTypeMetadata();
        var type = first.typeString();
        assertSame(upstream.typeString(), metadata.normalize(type, upstream::typeString));
        assertSame(type, metadata.normalize(type, () -> type));
        assertSame(upstream.typeString(), metadata.resolve(type, type, upstream::typeString));
        assertSame(type, metadata.resolve(type, type, () -> type));
    }

    @Test
    void failuresAndRecursiveAssumptionsAreNotPublished() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var type = pool.typeString();
        var other = pool.typeChar();
        assertThrows(IllegalStateException.class, () -> metadata.variance(type, "T", Access.PUBLIC,
                true, () -> { throw new IllegalStateException("injected"); }));
        assertTrue(metadata.variance(type, "T", Access.PUBLIC, true, () -> {
            assertFalse(metadata.variance(other, "T", Access.PUBLIC, true, () -> Usage.valueOf(
                    metadata.variance(type, "T", Access.PUBLIC, true, () -> fail("recursion")))));
            return Usage.YES;
        }));
        assertTrue(metadata.variance(other, "T", Access.PUBLIC, true, () -> Usage.YES));
        assertTrue(metadata.variance(type, "T", Access.PUBLIC, true, () -> fail("root not cached")));
    }

    @Test
    void unresolvedVarianceIsReusedOnlyWithinItsCurrentCalculation() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var pending = new PendingTypeConstant(pool, null);
        var calls = new AtomicInteger();
        for (int pass = 0; pass < 2; pass++) {
            assertFalse(metadata.variance(pool.typeString(), "T", Access.PUBLIC, true, () -> {
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertFalse(metadata.variance(pending, "T", Access.PUBLIC, true, () -> {
                        calls.incrementAndGet();
                        return Usage.NO;
                    }));
                }
                return Usage.NO;
            }));
            assertEquals(pass + 1, calls.get());
        }
    }

    @Test
    void memberKindsAndOwnersStayIndependentAndInvalidateTogether() {
        var first = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var second = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = first.getTypeMetadata();
        var member = first.ensurePropertyConstant(first.clzString(), "Element");
        metadata.cacheMemberType(member, MemberType.Value, first.typeType());
        metadata.cacheMemberType(member, MemberType.Constraint, first.typeObject());
        assertSame(first.typeType(), metadata.getMemberType(member, MemberType.Value));
        assertSame(first.typeObject(), metadata.getMemberType(member, MemberType.Constraint));
        var foreign = second.ensurePropertyConstant(second.clzString(), "Element");
        assertThrows(IllegalArgumentException.class, () -> metadata.getMemberType(foreign, MemberType.Value));
        first.invalidateTypeInfos(first.clzString());
        assertNull(metadata.getMemberType(member, MemberType.Value));
        assertNull(metadata.getMemberType(member, MemberType.Constraint));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rebuildingTheConstantTableReleasesSemanticKeys(boolean reload) throws Exception {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var type = pool.typeString();
        metadata.normalize(type, () -> type);
        metadata.resolve(type, pool.typeObject(), () -> type);
        metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.NO);
        metadata.cacheMemberType(type, MemberType.Annotation, type);
        metadata.markValidated(type);
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
            pool.register(type);
            pool.postRegisterAll(true);
        }
        // A miss under a new canonical identity does not prove the old key was released.
        // Inspect retention directly instead of relying on nondeterministic GC timing.
        for (var name : List.of("infos", "normalized", "resolutions", "variances", "memberTypes", "validated")) {
            var field = TypeMetadata.class.getDeclaredField(name);
            field.setAccessible(true);
            assertTrue(((Map<?, ?>) field.get(metadata)).isEmpty(), name);
        }
        assertSame(metadata, pool.getTypeMetadata());
    }

    @Test
    void compilerInvalidationDiscardsDerivedResults() {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var calls = new AtomicInteger();
        var type = pool.typeString();
        metadata.normalize(type, () -> { calls.incrementAndGet(); return type; });
        metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.NO);
        pool.invalidateTypeInfos(pool.clzString());
        metadata.normalize(type, () -> { calls.incrementAndGet(); return type; });
        assertEquals(2, calls.get());
        assertTrue(metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.YES));
        assertSame(type, pool.typeString());
    }

    @Test
    @Timeout(10)
    void concurrentQueryCannotSeeProvisionalVarianceAndClearCannotBeUndone() throws Exception {
        var pool = new FileStructure(Constants.ECSTASY_MODULE).getConstantPool();
        var metadata = pool.getTypeMetadata();
        var type = pool.typeString();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> metadata.variance(type, "T", Access.PUBLIC, true, () -> {
                assertFalse(metadata.variance(type, "T", Access.PUBLIC, true, () -> fail("recursion")));
                entered.countDown();
                await(release);
                return Usage.NO;
            }));
            try {
                entered.await();
                assertTrue(metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.YES));
                metadata.clear();
            } finally {
                release.countDown();
            }
            assertFalse(pending.get());
            assertFalse(metadata.variance(type, "T", Access.PUBLIC, true, () -> Usage.NO));
        }
    }

    @Test
    void failedInfoCalculationReleasesAllPlaceholdersAndCompletedDependencies() {
        var fixture = fixture();
        var metadata = fixture.pool.getTypeMetadata();
        var type = fixture.info.getType();
        assertThrows(IllegalStateException.class, () -> metadata.calculateTypeInfo(() -> {
            metadata.setTypeInfo(type, fixture.pool.infoPlaceholder());
            metadata.setTypeInfo(type, fixture.info);
            throw new IllegalStateException("injected after dependency completed");
        }));
        assertNull(metadata.getTypeInfo(type));
        assertFalse(metadata.hasDeferred());
        metadata.calculateTypeInfo(() -> {
            metadata.setTypeInfo(type, fixture.info);
            return fixture.info;
        });
        assertSame(fixture.info, metadata.getTypeInfo(type));
        metadata.clear();
        assertNull(metadata.getTypeInfo(type));
    }

    @Test
    void caughtNestedFailureStillPreventsPublication() {
        var fixture = fixture();
        var metadata = fixture.pool.getTypeMetadata();
        var type = fixture.info.getType();
        metadata.calculateTypeInfo(() -> {
            assertThrows(IllegalStateException.class, () -> metadata.calculateTypeInfo(() -> {
                throw new IllegalStateException("injected nested failure");
            }));
            metadata.setTypeInfo(type, fixture.info);
            return fixture.info;
        });
        assertNull(metadata.getTypeInfo(type));
    }

    @Test
    @Timeout(10)
    void otherThreadsCannotReadPartialInfoAndClearingDetachesPendingPublication() throws Exception {
        var fixture = fixture();
        var metadata = fixture.pool.getTypeMetadata();
        var type = fixture.info.getType();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> metadata.calculateTypeInfo(() -> {
                metadata.setTypeInfo(type, fixture.pool.infoPlaceholder());
                metadata.defer(type);
                assertTrue(metadata.hasDeferred());
                entered.countDown();
                await(release);
                metadata.setTypeInfo(type, fixture.info);
                return fixture.info;
            }));
            try {
                entered.await();
                assertNull(metadata.getTypeInfo(type));
                assertFalse(metadata.hasDeferred());
                metadata.clear();
            } finally {
                release.countDown();
            }
            assertSame(fixture.info, pending.get());
            assertNull(metadata.getTypeInfo(type));
        }
    }

    @Test
    @Timeout(10)
    void discardingAPlaceholderCannotEvictAnotherThreadsCompletedResult() throws Exception {
        var fixture = fixture();
        var metadata = fixture.pool.getTypeMetadata();
        var type = fixture.info.getType();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> metadata.calculateTypeInfo(() -> {
                metadata.setTypeInfo(type, fixture.pool.infoPlaceholder());
                entered.countDown();
                await(release);
                metadata.clearPlaceholder(type);
                assertSame(fixture.info, metadata.getTypeInfo(type));
                return fixture.info;
            }));
            try {
                entered.await();
                metadata.calculateTypeInfo(() -> {
                    metadata.setTypeInfo(type, fixture.info);
                    return fixture.info;
                });
            } finally {
                release.countDown();
            }
            assertSame(fixture.info, pending.get());
            assertSame(fixture.info, metadata.getTypeInfo(type));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void declarationMetadataDoesNotInheritARelationProbeContext() throws Exception {
        var fixture = fixture();
        var contextField = TypeConstant.class.getDeclaredField("s_context");
        contextField.setAccessible(true);
        var binding = (ScopedValue<TypeConstant>) contextField.get(null);
        var context = fixture.info.getType();
        var probe = new TerminalTypeConstant(fixture.pool, context.getDefiningConstant()) {
            @Override
            protected TypeInfo getTypeInfo() {
                assertNull(TypeConstant.getContext(), "A relation probe is not a declaration input");
                return fixture.info;
            }
        };
        ScopedValue.where(binding, context).run(() -> {
            assertSame(context, TypeConstant.getContext());
            assertSame(fixture.info, probe.ensureTypeInfo());
            assertSame(context, TypeConstant.getContext());
        });
        assertNull(TypeConstant.getContext());
    }

    @Test
    void objectBootstrapRetainsEveryAccessViewOfTheRoot() {
        var file = new FileStructure(Constants.ECSTASY_MODULE);
        var pool = file.getConstantPool();
        var object = file.getModule().createClass(Access.PUBLIC, Format.INTERFACE, "Object", null);
        var type = object.getIdentityConstant().getType();
        var privateType = pool.ensureAccessTypeConstant(type, Access.PRIVATE);
        var metadata = pool.getTypeMetadata();
        metadata.calculateTypeInfo(() -> {
            var placeholder = pool.infoPlaceholder();
            metadata.setTypeInfo(type, placeholder);
            metadata.setTypeInfo(privateType, placeholder);
            metadata.finishObjectBootstrap();
            assertSame(placeholder, metadata.getTypeInfo(type));
            assertSame(placeholder, metadata.getTypeInfo(privateType));
            return null;
        });
        assertNull(metadata.getTypeInfo(type), "Placeholders must never be published");
        assertNull(metadata.getTypeInfo(privateType));
    }

    private static Fixture fixture() {
        var file = new FileStructure("MetadataOwner");
        var structure = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Value", null);
        var info = new TypeInfoReal(structure.getIdentityConstant().getType(), 0, structure, 0, false,
                Map.of(), Annotation.NO_ANNOTATIONS, Annotation.NO_ANNOTATIONS, null, null, null,
                List.of(), new ListMap<>(), new ListMap<>(), Map.of(), Map.of(), Map.of(),
                Map.of(), new ListMap<>(), null, Progress.Complete);
        return new Fixture(file.getConstantPool(), info);
    }

    private record Fixture(ConstantPool pool, TypeInfo info) {}

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
