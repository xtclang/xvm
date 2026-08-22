package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for runtime class-composition operations that must not mutate a pool after publication.
 */
public class ClassCompositionLateRegistrationTest {
    /**
     * Access views are runtime-hot operations. The old `ClassComposition.ensureAccess(PROTECTED)`
     * path could register a protected `AccessTypeConstant` after the pool had been published to
     * runtime execution. That is not safe for same-JVM or parallel runtime execution: a supposedly
     * running pool keeps growing while other readers can observe it. The constructor now prewarms
     * the canonical protected/private/struct access constants, preserving lazy composition
     * allocation but avoiding that later access-view pool mutation.
     */
    @Test
    public void protectedAccessViewDoesNotRegisterAfterRuntimePublication() throws Exception {
        var runtime = new Runtime();
        try {
            var file      = new FileStructure("LateAccess");
            var container = new TestContainer(runtime, file);
            var pool      = file.getConstantPool();
            var type      = pool.typeObject();
            var clz       = new ClassComposition(container, null, type);

            withLateRegistrationValidation(() -> {
                pool.markRuntimePublishedForDiagnostics("unit-test");

                TypeComposition protectedView = clz.ensureAccess(Access.PROTECTED);

                assertSame(protectedView, clz.ensureAccess(Access.PROTECTED));
                assertTrue(pool.isRuntimePublishedForDiagnostics());
            });
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * Runtime publication diagnostics prewarm access-type constants for class/type constants the
     * pool already knows. That lets a class composition remain lazy without mutating the published
     * pool when the first object construction asks for the composition during execution.
     */
    @Test
    public void firstClassCompositionDoesNotRegisterAfterRuntimePublication() throws Exception {
        var runtime = new Runtime();
        try {
            var file      = new FileStructure("LateComposition");
            var container = new TestContainer(runtime, file);
            var pool      = file.getConstantPool();
            var type      = pool.typeObject();

            withLateRegistrationValidation(() -> {
                pool.markRuntimePublishedForDiagnostics("unit-test");

                var clz = new ClassComposition(container, null, type);

                assertSame(clz, clz.ensureAccess(Access.PUBLIC));
                assertTrue(pool.isRuntimePublishedForDiagnostics());
            });
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static void withLateRegistrationValidation(CheckedRunnable action) throws Exception {
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

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class TestContainer extends Container {
        private TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
        }

        @Override
        public boolean isSpecified(String name) {
            return false;
        }

        @Override
        public boolean isPresent(IdentityConstant id) {
            return false;
        }

        @Override
        public boolean isVersionMatch(ModuleConstant module, VersionConstant version) {
            return false;
        }

        @Override
        public boolean isVersion(VersionConstant version) {
            return false;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type,
                                          ObjectHandle opts) {
            return null;
        }
    }
}
