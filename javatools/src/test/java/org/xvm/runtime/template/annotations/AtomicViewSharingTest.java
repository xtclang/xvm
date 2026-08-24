package org.xvm.runtime.template.annotations;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.annotations.xAtomicInt128.AtomicLongLongHandle;
import org.xvm.runtime.template.annotations.xAtomicIntNumber.AtomicJavaLongHandle;
import org.xvm.runtime.template.annotations.xInject.InjectedHandle;
import org.xvm.runtime.template.annotations.xLazy.LazyHandle;

import org.xvm.runtime.template.numbers.LongLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the shared-cell contract of handle state that is logically one cell per reference but
 * used to be installed lazily in a per-view field. {@code cloneAs(...)} views are shallow copies,
 * so a view cloned before the first assignment carried its own not-yet-installed field: the first
 * assignment through each view then installed an independent cell, silently splitting one Atomic
 * reference into two (and resolving one injection twice). Found by the must-audit row 161
 * completion sweep; the lazily installed fields exist verbatim on master.
 */
public class AtomicViewSharingTest {
    /**
     * A view cloned before the first assignment must observe an assignment made through the
     * original: the atomic cell and the assigned flag are constructor-final and shared by the
     * shallow view copy, never installed per view.
     */
    @Test
    public void atomicLongViewsShareOneCell() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hRef = new AtomicJavaLongHandle(clz, "probe") {};
            var hView = (AtomicJavaLongHandle) hRef.cloneAs(view(clz));
            assertNotSame(hRef, hView, "the probe requires a genuine view clone");
            assertFalse(hView.isAssigned());

            hRef.m_atomicValue.set(42);
            hRef.m_fAssigned.set(true);

            assertTrue(hView.isAssigned(),
                    "an assignment through one view must be visible through every view");
            assertSame(hRef.m_atomicValue, hView.m_atomicValue,
                    "all views of one Atomic reference must share one cell");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * Same contract for the 128-bit flavor, where unassigned state is the null referent inside
     * the shared final cell.
     */
    @Test
    public void atomicLongLongViewsShareOneCell() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hRef  = new AtomicLongLongHandle(clz, "probe") {};
            var hView = (AtomicLongLongHandle) hRef.cloneAs(view(clz));
            assertFalse(hView.isAssigned());

            hRef.m_atomicValue.set(new LongLong(7, 9));

            assertTrue(hView.isAssigned(),
                    "an assignment through one view must be visible through every view");
            assertSame(hRef.m_atomicValue, hView.m_atomicValue,
                    "all views of one Atomic reference must share one cell");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * An injection must resolve exactly once per reference, no matter how many views exist and no
     * matter which one triggered resolution: the referent lives in a final shared first-wins cell,
     * not in the inherited per-view referent field.
     */
    @Test
    public void injectionResolvesOnceAcrossViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hRef  = new InjectedHandle(clz, "probe", "resource") {};
            var hView = (InjectedHandle) hRef.cloneAs(view(clz));
            assertFalse(hView.isAssigned());

            var hFirst  = new ObjectHandle.GenericHandle(clz);
            var hSecond = new ObjectHandle.GenericHandle(clz);

            hRef.setReferent(hFirst);
            assertSame(hFirst, hView.getReferent(),
                    "a view cloned before resolution must see the resolved injection");

            hView.setReferent(hSecond);
            assertSame(hFirst, hRef.getReferent(),
                    "the first resolution must win; later attempts are quiet no-ops");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The lazy initialization guard must be shared by all views. The old shape synchronized on
     * the handle instance and kept the allowed-to-assign fiber set in a per-view field, so a
     * fiber registered through one view was invisible through another: the legal lazy recompute
     * race then raised a spurious immutable-property exception, and two views did not exclude
     * each other while computing the "at most once" value. This test is red on the old per-view
     * shape: unregistering through a different view returned false.
     */
    @Test
    public void lazyInitGuardIsSharedAcrossViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var hRef  = new LazyHandle(clz, "probe") {};
            var hView = (LazyHandle) hRef.cloneAs(view(clz));
            assertNotSame(hRef, hView);

            // the fiber token is opaque to the guard; null suffices to prove set identity
            hRef.registerAssign(null);
            assertTrue(hView.unregisterAssign(null),
                    "a fiber registered through one view must be visible through every view");
            assertFalse(hRef.unregisterAssign(null),
                    "unregistration must consume the single shared registration");
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static TypeComposition view(ClassComposition clz) {
        return clz.ensureAccess(Access.PROTECTED);
    }

    // ----- helpers (same discovery as ClassCompositionSafePublicationTest) ----------------------

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var manualRepository = repositoryFor("manualTests/build/xtc/xdk/lib");
        if (manualRepository != null) {
            return manualRepository;
        }

        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(AtomicViewSharingTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.get(0);
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory()
                ? new DirRepository(directory, true)
                : null;
    }

    private static File checkoutFile(String path) {
        return checkoutRoot().resolve(path).toFile();
    }

    private static Path checkoutRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("javatools")) &&
                    Files.isDirectory(path.resolve("manualTests"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate checkout root");
    }
}
