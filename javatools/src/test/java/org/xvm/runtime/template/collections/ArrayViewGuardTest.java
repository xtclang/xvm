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

import org.xvm.runtime.template.collections.xArray.Mutability;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards mutable arrays against view cloning (must-audit row 161, mechanism 4). {@code clear()}
 * replaces {@code ArrayHandle.m_hDelegate} wholesale for {@code Mutability.Mutable} arrays - the
 * one in-place delegate-pointer replacement in the codebase - so on the unguarded shape a shallow
 * {@code cloneAs} view forked the storage pointer: after one alias cleared, the other kept the old
 * delegate, and the two views silently diverged while natural code believed they were one array.
 * Fixed/Persistent/Constant arrays never replace the pointer in place, so their views share one
 * delegate and stay consistent; immutable arrays are the proven-safe clone inputs (ConstHeap
 * relocation asserts immutability).
 */
public class ArrayViewGuardTest {
    /**
     * Cloning a Mutable array must fail loudly; cloning a Constant array must keep working
     * (ConstHeap relocation depends on it). Red on the unguarded shape, where the Mutable clone
     * quietly produced the fork.
     */
    @Test
    public void mutableArrayRefusesViewCloning() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzArray  = (ClassComposition) container.resolveClass(pool.typeArray());
            var clzView   = clzArray.ensureAccess(Access.PROTECTED);

            var hMutable = xArray.createEmptyArray(clzArray, 0, Mutability.Mutable);
            var error = assertThrows(IllegalStateException.class,
                    () -> hMutable.cloneAs(clzView),
                    "a Mutable array view would fork the delegate pointer on clear()");
            assertTrue(error.getMessage().contains("mutable array"), error.getMessage());

            var hConstant = xArray.createEmptyArray(clzArray, 0, Mutability.Constant);
            assertNotSame(hConstant, hConstant.cloneAs(clzView),
                    "immutable arrays must still clone; ConstHeap relocation depends on it");
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
