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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the structural closure of the tuple freeze-split (must-fix row 148 family): tuple views
 * now share ALL lifecycle state. The element storage ({@code m_ahValue}, never swapped after
 * construction) was always shared by every {@code cloneAs} view; the one per-view field - the
 * mutability flag - is now shared through the freeze cell the base {@code ObjectHandle}
 * installs before the first view copy, so a freeze through any view is authoritative for every
 * sibling. On the per-view shape (master's, and this branch's interim fail-loud guard),
 * freezing through one view left a sibling still claiming mutability over the frozen shared
 * storage.
 */
public class TupleViewGuardTest {
    /**
     * The mutability axis: freezing through one view must be immediately authoritative for
     * every sibling view. Red on the per-view-flag shape, where the sibling's flag stayed
     * mutable over frozen shared storage.
     */
    @Test
    public void freezeThroughOneTupleViewIsAuthoritativeForAllViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzTuple  = (ClassComposition) container.resolveClass(pool.typeTuple());
            var clzView   = clzTuple.ensureAccess(Access.PROTECTED);

            var hTuple = xTuple.makeHandle(clzTuple);
            assertTrue(hTuple.isMutable(), "a plain Tuple composition must start out mutable");

            var hView = hTuple.cloneAs(clzView);
            assertNotSame(hTuple, hView, "a view must be a distinct handle");
            assertTrue(hView.isMutable(), "the view shares the live lifecycle");

            assertTrue(hView.makeImmutable(),
                    "freezing an empty tuple through a view must succeed");
            assertFalse(hTuple.isMutable(),
                    "the sibling view must not still claim mutability after the freeze");

            assertNotSame(hTuple, hTuple.cloneAs(clzView),
                    "an immutable tuple keeps cloning; ConstHeap relocation depends on it");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers (same discovery as ArrayViewGuardTest) ---------------------------------------

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
                .map(TupleViewGuardTest::repositoryFor)
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
