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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards mutable tuples against view cloning (must-fix row 148, the freeze-split residual found
 * while closing mechanism 5). {@code TupleHandle} extends {@code ObjectHandle} directly, so it
 * has no shared freeze cell: {@code m_fMutable} is a per-view field while the {@code m_ahValue}
 * element storage is shared by every {@code cloneAs} view. On the unguarded shape, freezing a
 * tuple through one view left the sibling view still claiming mutability and therefore still
 * willing to write into the frozen shared storage. Immutable tuples have a terminal lifecycle
 * that per-view copies cannot desync and remain safe to clone (ConstHeap relocation depends on
 * cloning immutable handles).
 */
public class TupleViewGuardTest {
    /**
     * Cloning a mutable tuple must fail loudly; cloning the same tuple after freezing it must
     * keep working. Red on the unguarded shape, where the mutable clone quietly produced the
     * per-view mutability split.
     */
    @Test
    public void mutableTupleRefusesViewCloning() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzTuple  = (ClassComposition) container.resolveClass(pool.typeTuple());
            var clzView   = clzTuple.ensureAccess(Access.PROTECTED);

            var hTuple = xTuple.makeHandle(clzTuple);
            assertTrue(hTuple.isMutable(), "a plain Tuple composition must start out mutable");

            var error = assertThrows(IllegalStateException.class,
                    () -> hTuple.cloneAs(clzView),
                    "a mutable tuple view would split the per-view mutability on freeze");
            assertTrue(error.getMessage().contains("mutable tuple"), error.getMessage());

            assertTrue(hTuple.makeImmutable(), "freezing an empty tuple must succeed");
            assertFalse(hTuple.isMutable());
            assertNotSame(hTuple, hTuple.cloneAs(clzView),
                    "an immutable tuple has a terminal lifecycle and must clone;"
                            + " ConstHeap relocation depends on it");
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
