package org.xvm.asm;


import java.io.IOException;
import java.io.UncheckedIOException;

import java.lang.reflect.Field;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeConstant.Relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for opt-in {@link ConstantPool} runtime ownership diagnostics.
 */
public class ConstantPoolDiagnosticsTest {
    /**
     * Boundary scopes are still transitional API. This verifies the supported case: an explicit
     * owner installs the matching ambient pool and the diagnostic assertions accept it.
     */
    @Test
    public void currentPoolAssertionsAcceptScopedPool() {
        assertTrue(assertionsEnabled(), "ConstantPool scope diagnostics require -ea");

        ConstantPool pool = new FileStructure("test").getConstantPool();

        try (var _ = ConstantPool.withPool(pool)) {
            ConstantPool.assertCurrentPool(pool, "unit-test");
            ConstantPool.assertCurrentPoolIfPresent(pool, "unit-test");
        }
    }

    /**
     * Explicit-owner code must work when no ambient pool exists. The old design made many helpers
     * crash or guess from thread state; this verifies the "no scope is fine" guard.
     */
    @Test
    public void currentPoolIfPresentAllowsExplicitOwnerWithoutAmbientScope() {
        assertTrue(assertionsEnabled(), "ConstantPool scope diagnostics require -ea");

        ConstantPool pool = new FileStructure("test").getConstantPool();

        try (var _ = ConstantPool.withPool(null)) {
            ConstantPool.assertCurrentPoolIfPresent(pool, "unit-test");

            AssertionError error = assertThrows(AssertionError.class,
                    () -> ConstantPool.assertCurrentPool(pool, "unit-test"));
            assertTrue(error.getMessage().contains("unit-test"));
        }
    }

    /**
     * A wrong ambient pool is a real owner bug, even on one Java thread with nested work. This
     * proves the transitional assertions catch the mismatch when assertions are enabled.
     */
    @Test
    public void currentPoolAssertionsRejectWrongScopedPool() {
        assertTrue(assertionsEnabled(), "ConstantPool scope diagnostics require -ea");

        var poolExpected = new FileStructure("expected").getConstantPool();
        var poolActual   = new FileStructure("actual").getConstantPool();

        try (var _ = ConstantPool.withPool(poolActual)) {
            AssertionError error = assertThrows(AssertionError.class,
                    () -> ConstantPool.assertCurrentPool(poolExpected, "unit-test"));
            assertTrue(error.getMessage().contains("unit-test"));

            error = assertThrows(AssertionError.class,
                    () -> ConstantPool.assertCurrentPoolIfPresent(poolExpected, "unit-test"));
            assertTrue(error.getMessage().contains("unit-test"));
        }
    }

    /**
     * Stress and launcher tests often run without Java assertions. The diagnostic property must
     * still make a wrong scoped owner fail as a normal exception, because hidden wrong-pool state is
     * exactly the class of bug the same-JVM reentrancy work is trying to expose.
     */
    @Test
    public void currentPoolValidationPropertyRejectsWrongScopedPool()
            throws Exception {
        var poolExpected = new FileStructure("expected").getConstantPool();
        var poolActual   = new FileStructure("actual").getConstantPool();

        withCurrentPoolValidation(() -> {
            try (var _ = ConstantPool.withPool(poolActual)) {
                var error = assertThrows(IllegalStateException.class,
                        () -> ConstantPool.assertCurrentPool(poolExpected, "unit-test"));
                assertTrue(error.getMessage().contains("unit-test"));
                assertTrue(error.getMessage().contains("expected current ConstantPool"));
            }
        });
    }

    /**
     * Transitional bridge code must already know the explicit owner it is asserting. A null owner
     * used to disappear when assertions were disabled; the validation property turns that into a
     * fail-fast diagnostic for stress runs.
     */
    @Test
    public void currentPoolValidationPropertyRejectsMissingExplicitOwner()
            throws Exception {
        withCurrentPoolValidation(() -> {
            var error = assertThrows(IllegalStateException.class,
                    () -> ConstantPool.assertCurrentPool(null, "unit-test"));
            assertTrue(error.getMessage().contains("must provide an explicit ConstantPool owner"));
        });
    }

    /**
     * Runtime publication diagnostics must be opt-in. The guard is useful for stress/CI, but it
     * cannot change normal compiler/runtime cache behavior when the property is not enabled.
     */
    @Test
    public void publicationMarkerIsDisabledByDefault() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        pool.markRuntimePublishedForDiagnostics("unit-test");

        assertFalse(pool.isRuntimePublishedForDiagnostics());
        pool.ensureStringConstant("late");
    }

    /**
     * A runtime-visible pool should not keep growing silently. This proves the opt-in guard catches
     * late constants after publication instead of letting parallel readers observe new state.
     */
    @Test
    public void lateRegistrationGuardRejectsNewConstantsAfterPublication()
            throws Exception {
        ConstantPool pool = new FileStructure("test").getConstantPool();
        Constant existing = pool.ensureStringConstant("existing");

        withLateRegistrationValidation(() -> {
            pool.markRuntimePublishedForDiagnostics("unit-test");

            assertTrue(pool.isRuntimePublishedForDiagnostics());
            assertSame(existing, pool.ensureStringConstant("existing"));

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> pool.ensureStringConstant("late"));
            assertTrue(error.getMessage().contains("after runtime publication"));
            assertTrue(error.getMessage().contains("unit-test"));
        });
    }

    /**
     * The late-registration guard must fail without mutating lookup-map shape. Otherwise the
     * diagnostic itself would perturb ConstantPool state while checking for unsafe mutation.
     */
    @Test
    public void lateRegistrationGuardDoesNotCreateMissingLookupMap()
            throws Exception {
        ConstantPool pool = new FileStructure("test").getConstantPool();
        Set<Constant.Format> formatsBefore = Set.copyOf(constantMaps(pool).keySet());

        withLateRegistrationValidation(() -> {
            pool.markRuntimePublishedForDiagnostics("unit-test");

            assertThrows(IllegalStateException.class,
                    () -> pool.register(new DiagnosticConstant(pool)));
            assertEquals(formatsBefore, constantMaps(pool).keySet());
        });
    }

    /**
     * Function compatibility is an instance method on a specific pool. The old implementation
     * still asked the ambient current pool for Tuple, so it crashed with no scope and could use the
     * wrong owner under nested execution.
     */
    @Test
    public void functionCompatibilityUsesReceiverPoolWithoutAmbientPool() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        TypeConstant typeTupleReturn = pool.buildFunctionType(
                ConstantPool.NO_TYPES, pool.typeTuple0());
        TypeConstant typeVoidReturn  = pool.buildFunctionType(ConstantPool.NO_TYPES);

        try (var _ = ConstantPool.withPool(null)) {
            assertEquals(Relation.IS_A,
                    pool.checkFunctionCompatibility(typeTupleReturn, typeVoidReturn));
        }
    }

    /**
     * `getCurrentPool()` must not return as a semantic owner API. This source-shape guard prevents
     * new main-code helpers from reintroducing hidden owner lookup outside `ConstantPool` itself.
     */
    @Test
    public void semanticCurrentPoolLookupIsBridgeOnly() throws Exception {
        Path sourceRoot = sourceRoot();
        List<String> offenders;

        try (var files = Files.walk(sourceRoot.resolve("org/xvm"))) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("ConstantPool.java"))
                    .filter(ConstantPoolDiagnosticsTest::usesCurrentPoolInCode)
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertEquals(List.of(), offenders);
    }

    /**
     * There must be no callable current-pool getter. The scoped bridge can read its private
     * thread-local slot internally, but exposing a getter makes hidden owner lookup easy to
     * reintroduce.
     */
    @Test
    public void currentPoolLookupGetterDoesNotExist() {
        assertThrows(NoSuchMethodException.class,
                () -> ConstantPool.class.getDeclaredMethod("getCurrentPool"));
    }

    /**
     * The implicit import catalog is process-wide metadata parsed at class initialization. It must
     * be immutable after startup; otherwise unrelated compiler/runtime requests in the same JVM can
     * corrupt the global name catalog for every later request.
     */
    @Test
    public void staticImplicitMetadataMapsAreImmutable() throws Exception {
        var implicits = staticMap("s_implicits");
        var byPath    = staticMap("s_implicitsByPath");
        String[] parts = {"ecstasy", "text", "String"};

        assertFalse(implicits.isEmpty());
        assertFalse(byPath.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> implicits.put("__test__", parts));
        assertThrows(UnsupportedOperationException.class,
                () -> byPath.put("__test__", "__test__"));
    }

    /**
     * ConstantPool has to publish a newly inserted constant before recursive child registration so
     * same-thread cycles can resolve. The old design let other threads observe that half-registered
     * constant. Same-thread access is intentionally allowed and covered by
     * registrationOwnerCanResolveInProgressConstant(); this test needs a second thread because it
     * proves the public cross-thread observation path waits until recursive registration finishes.
     */
    @Test
    public void otherThreadsWaitForRecursiveRegistrationCompletion() throws Exception {
        var pool          = new FileStructure("target").getConstantPool();
        var started       = new CountDownLatch(1);
        var release       = new CountDownLatch(1);
        var readerStarted = new CountDownLatch(1);
        var index         = new AtomicInteger(-1);
        var source        = new BlockingRegistrationConstant(
                new FileStructure("source").getConstantPool(), started, release, index);
        var executor      = Executors.newFixedThreadPool(2);

        try {
            var register = executor.submit(() -> pool.register(source));
            assertTrue(started.await(1, TimeUnit.SECONDS));

            var read = executor.submit(() -> {
                readerStarted.countDown();
                return pool.getConstant(index.get());
            });
            assertTrue(readerStarted.await(1, TimeUnit.SECONDS));
            assertFalse(read.isDone(), "reader observed a partially registered constant");

            release.countDown();
            assertSame(register.get(1, TimeUnit.SECONDS), read.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Early publication exists to support same-thread recursive constant graphs. The completion
     * guard must not break that legacy behavior: the registration owner is allowed to resolve its
     * own in-progress constant while registerConstants(...) is still running.
     */
    @Test
    public void registrationOwnerCanResolveInProgressConstant() {
        var pool   = new FileStructure("target").getConstantPool();
        var source = new ReentrantRegistrationConstant(new FileStructure("source").getConstantPool());

        var registered = pool.register(source);

        assertSame(registered, registered.observed);
    }

    /**
     * If recursive registration fails after the constant has been assigned a public pool index, the
     * pool is no longer structurally trustworthy at that index. The guard must keep reporting the
     * registration failure instead of later returning the partial constant graph.
     */
    @Test
    public void failedRecursiveRegistrationStaysFailedForReaders() {
        var pool   = new FileStructure("target").getConstantPool();
        var index  = new AtomicInteger(-1);
        var source = new FailingRegistrationConstant(
                new FileStructure("source").getConstantPool(), index);

        var failure = assertThrows(IllegalStateException.class, () -> pool.register(source));
        assertEquals("registration failure", failure.getMessage());

        var readFailure = assertThrows(IllegalStateException.class,
                () -> pool.getConstant(index.get()));
        assertEquals("constant registration failed", readFailure.getMessage());
        assertSame(failure, readFailure.getCause());
    }

    private static void withLateRegistrationValidation(CheckedRunnable action)
            throws Exception {
        withBooleanProperty(ConstantPool.VALIDATE_LATE_REGISTRATION_PROPERTY, action);
    }

    private static void withCurrentPoolValidation(CheckedRunnable action)
            throws Exception {
        withBooleanProperty(ConstantPool.VALIDATE_CURRENT_POOL_PROPERTY, action);
    }

    private static void withBooleanProperty(String property, CheckedRunnable action)
            throws Exception {
        var previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Constant.Format, Map<Constant, Constant>> constantMaps(ConstantPool pool)
            throws ReflectiveOperationException {
        Field field = ConstantPool.class.getDeclaredField("m_mapConstants");
        field.setAccessible(true);
        return (Map<Constant.Format, Map<Constant, Constant>>) field.get(pool);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> staticMap(String name) throws ReflectiveOperationException {
        var field = ConstantPool.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private static boolean usesCurrentPoolInCode(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .map(String::stripLeading)
                    .filter(line -> !line.startsWith("//") && !line.startsWith("*"))
                    .anyMatch(line -> line.contains("getCurrentPool("));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java");
        return Files.exists(project.resolve("org/xvm/asm/ConstantPool.java"))
                ? project
                : cwd.resolve("javatools/src/main/java");
    }

    private static boolean assertionsEnabled() {
        boolean fEnabled = false;
        assert fEnabled = true;
        return fEnabled;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class DiagnosticConstant
            extends Constant {
        private DiagnosticConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "diagnostic";
        }

        @Override
        public String getDescription() {
            return "diagnostic";
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }
    }

    private static final class BlockingRegistrationConstant
            extends Constant {
        private final CountDownLatch started;
        private final CountDownLatch release;
        private final AtomicInteger  index;

        private BlockingRegistrationConstant(ConstantPool pool, CountDownLatch started,
                                             CountDownLatch release, AtomicInteger index) {
            super(pool);

            this.started = started;
            this.release = release;
            this.index   = index;
        }

        @Override
        public Format getFormat() {
            return Format.String;
        }

        @Override
        public String getValueString() {
            return "blocking-registration";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected void registerConstants(ConstantPool pool) {
            index.set(getPosition());
            started.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("registration test was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected BlockingRegistrationConstant copyForAdoption(AdoptionContext context) {
            // Test fixture: adoption must keep the synchronization probes, not any source-pool
            // ownership. Production constants must make the same explicit logical-state decision.
            return new BlockingRegistrationConstant(context.pool(), started, release, index);
        }

        @Override
        protected int compareDetails(Constant that) {
            return that instanceof BlockingRegistrationConstant ? 0 : 1;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }
    }

    private static final class ReentrantRegistrationConstant
            extends Constant {
        private Constant observed;

        private ReentrantRegistrationConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.String;
        }

        @Override
        public String getValueString() {
            return "reentrant-registration";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected void registerConstants(ConstantPool pool) {
            observed = pool.getConstant(getPosition());
        }

        @Override
        protected ReentrantRegistrationConstant copyForAdoption(AdoptionContext context) {
            // Test fixture: the observed constant belongs to registration execution and starts empty
            // in the adopted copy, matching the no-shared-runtime-state production rule.
            return new ReentrantRegistrationConstant(context.pool());
        }

        @Override
        protected int compareDetails(Constant that) {
            return that instanceof ReentrantRegistrationConstant ? 0 : 1;
        }

        @Override
        protected int computeHashCode() {
            return 3;
        }
    }

    private static final class FailingRegistrationConstant
            extends Constant {
        private final AtomicInteger index;

        private FailingRegistrationConstant(ConstantPool pool, AtomicInteger index) {
            super(pool);

            this.index = index;
        }

        @Override
        public Format getFormat() {
            return Format.String;
        }

        @Override
        public String getValueString() {
            return "failing-registration";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected void registerConstants(ConstantPool pool) {
            index.set(getPosition());
            throw new IllegalStateException("registration failure");
        }

        @Override
        protected FailingRegistrationConstant copyForAdoption(AdoptionContext context) {
            // Test fixture: keep only the index probe used to verify failed-publication behavior.
            return new FailingRegistrationConstant(context.pool(), index);
        }

        @Override
        protected int compareDetails(Constant that) {
            return that instanceof FailingRegistrationConstant ? 0 : 1;
        }

        @Override
        protected int computeHashCode() {
            return 2;
        }
    }
}
