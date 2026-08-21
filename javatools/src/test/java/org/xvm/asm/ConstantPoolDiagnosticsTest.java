package org.xvm.asm;


import java.lang.reflect.Field;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for opt-in {@link ConstantPool} runtime ownership diagnostics.
 */
public class ConstantPoolDiagnosticsTest {
    @Test
    public void currentPoolAssertionsAcceptScopedPool() {
        assertTrue(assertionsEnabled(), "ConstantPool scope diagnostics require -ea");

        ConstantPool pool = new FileStructure("test").getConstantPool();

        try (var _ = ConstantPool.withPool(pool)) {
            ConstantPool.assertCurrentPool(pool, "unit-test");
            ConstantPool.assertCurrentPoolIfPresent(pool, "unit-test");
        }
    }

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

    @Test
    public void currentPoolAssertionsRejectWrongScopedPool() {
        assertTrue(assertionsEnabled(), "ConstantPool scope diagnostics require -ea");

        ConstantPool poolExpected = new FileStructure("expected").getConstantPool();
        ConstantPool poolActual   = new FileStructure("actual").getConstantPool();

        try (var _ = ConstantPool.withPool(poolActual)) {
            AssertionError error = assertThrows(AssertionError.class,
                    () -> ConstantPool.assertCurrentPool(poolExpected, "unit-test"));
            assertTrue(error.getMessage().contains("unit-test"));

            error = assertThrows(AssertionError.class,
                    () -> ConstantPool.assertCurrentPoolIfPresent(poolExpected, "unit-test"));
            assertTrue(error.getMessage().contains("unit-test"));
        }
    }

    @Test
    public void publicationMarkerIsDisabledByDefault() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        pool.markRuntimePublishedForDiagnostics("unit-test");

        assertFalse(pool.isRuntimePublishedForDiagnostics());
        pool.ensureStringConstant("late");
    }

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

    private static void withLateRegistrationValidation(CheckedRunnable action)
            throws Exception {
        String property = ConstantPool.VALIDATE_LATE_REGISTRATION_PROPERTY;
        String previous = System.getProperty(property);
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

    private static boolean assertionsEnabled() {
        boolean fEnabled = false;
        assert fEnabled = true;
        return fEnabled;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run()
                throws Exception;
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
}
