package org.xvm.runtime.template.collections;


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
import org.xvm.runtime.Runtime;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the mechanism-4 closure (must-audit rows 161/186): access views of one live array share
 * ALL of its lifecycle state. MOV_THIS_A and ClassComposition.ensureAccess create
 * this:public/private/protected views of the same array through a shallow cloneAs - the same-JVM
 * stress harness proved that path ordinary (TestArray hit it at {@code MOV_THIS_A #1, PRIVATE}) -
 * and the two pieces of post-construction state, the delegate pointer that clear() swaps for
 * Mutable arrays and the mutability enum that freeze moves, now live in one ArrayState cell that
 * the shallow clone shares by reference. On the per-view-field shape (master's), a cleared view
 * forked the storage pointer from its sibling and a frozen view left the sibling still claiming
 * mutability against frozen shared storage; both splits are asserted impossible here. Array
 * DELEGATES still refuse view cloning entirely - no legitimate delegate clone path exists.
 */
public class ArrayViewGuardTest {
    /**
     * The delegate-pointer axis: replacing the storage delegate through one view (what clear()
     * does for a Mutable array with elements) must be visible through every sibling view. Red on
     * the per-view-field shape, where each view kept its own delegate pointer after the swap.
     */
    @Test
    public void viewsShareTheDelegatePointer() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzArray  = (ClassComposition) container.resolveClass(pool.typeArray());
            var clzView   = clzArray.ensureAccess(Access.PROTECTED);

            var hArray = xArray.createEmptyArray(clzArray, 0, Mutability.Mutable);
            var hView  = (ArrayHandle) hArray.cloneAs(clzView);
            assertNotSame(hArray, hView, "a view must be a distinct handle");
            assertSame(hArray.getDelegate(), hView.getDelegate(),
                    "views of one array must start on one delegate");

            var hReplacement = xArray.createEmptyArray(clzArray, 0, Mutability.Mutable)
                    .getDelegate();
            hView.setDelegate(hReplacement);
            assertSame(hReplacement, hArray.getDelegate(),
                    "a delegate swap through one view must be authoritative for all views");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The mutability axis: freezing through one view must be immediately authoritative for every
     * sibling view, for each live starting mutability. Red on the per-view-field shape, where
     * the sibling's enum and m_fMutable stayed live and xArray's handle-enum write checks kept
     * passing against frozen shared storage.
     */
    @Test
    public void freezeThroughOneViewIsAuthoritativeForAllViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzArray  = (ClassComposition) container.resolveClass(pool.typeArray());
            var clzView   = clzArray.ensureAccess(Access.PRIVATE);

            for (var mutability : new Mutability[] {
                    Mutability.Mutable, Mutability.Fixed, Mutability.Persistent}) {
                var hArray = xArray.createEmptyArray(clzArray, 0, mutability);
                var hView  = (ArrayHandle) hArray.cloneAs(clzView);

                assertTrue(hView.makeImmutable(), "freezing an empty " + mutability
                        + " array through a view must succeed");
                assertEquals(Mutability.Constant, hArray.getMutability(),
                        "the sibling view's enum must have moved with the freeze");
                assertFalse(hArray.isMutable(),
                        "the sibling view must not still claim mutability");
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The storage engines behind the arrays must never be view-cloned at all: delegates keep
     * per-instance m_cSize/mutability over shared element storage that typed subclasses
     * replace wholesale on grow, and no legitimate clone path exists (ConstHeap registers the
     * array handle, not its delegate). Red on the unguarded shape, where the inherited
     * ObjectHandle.cloneAs quietly produced the fork.
     */
    @Test
    public void arrayDelegatesRefuseViewCloningEntirely() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzArray  = (ClassComposition) container.resolveClass(pool.typeArray());

            for (var mutability : new Mutability[] {Mutability.Mutable, Mutability.Constant}) {
                var hDelegate = xArray.createEmptyArray(clzArray, 0, mutability).getDelegate();
                var error = assertThrows(IllegalStateException.class,
                        () -> hDelegate.cloneAs(hDelegate.getComposition()),
                        "a delegate view would fork the storage pointer or split the freeze"
                                + " state");
                assertTrue(error.getMessage().contains("delegate"), error.getMessage());
            }
        } finally {
            runtime.shutdownXVM();
        }
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
                .map(ArrayViewGuardTest::repositoryFor)
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
