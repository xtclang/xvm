package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the container-model enforcement: a runtime supports exactly ONE root - a
 * NativeContainer plane with ONE MainContainer on it - and everything else executes in NESTED
 * containers under that root (the sandbox model, where the parent mediates injection and the
 * shared plane's parent-flow retention is safe because the ancestor outlives every observer).
 * Sibling main containers over one shared plane are the unsupported shape: the one time it was
 * tried (the connector-reuse experiment), the reachability sweep immediately caught the shared
 * native heap serving a DEAD sibling's ClassCompositions to the next run through
 * {@code ConstHeap.relocateConst}. Under the ownership-validation property, installing a
 * second root now fails loudly at registration; without the property, the master-compatible
 * behavior is preserved (the Java API reuse demo documents that engine).
 */
public class RuntimeSingleRootTest {
    @Test
    public void secondRootFailsLoudlyUnderValidation() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        String sPrev = System.getProperty(OwnershipDiagnostics.VALIDATE_PROPERTY);
        System.setProperty(OwnershipDiagnostics.VALIDATE_PROPERTY, "true");
        var runtime = new Runtime();
        try {
            var containerNative = NativeContainer.create(runtime, systemRepository());
            var moduleEcstasy   = containerNative.getConstantPool().modEcstasy();

            var containerRoot = MainContainer.create(runtime, containerNative, moduleEcstasy);
            assertNotNull(containerRoot);

            var error = assertThrows(IllegalStateException.class,
                    () -> MainContainer.create(runtime, containerNative, moduleEcstasy),
                    "a second root main container must be refused under validation");
            assertTrue(error.getMessage().contains("NESTED containers"), error.getMessage());
        } finally {
            if (sPrev == null) {
                System.clearProperty(OwnershipDiagnostics.VALIDATE_PROPERTY);
            } else {
                System.setProperty(OwnershipDiagnostics.VALIDATE_PROPERTY, sPrev);
            }
            runtime.shutdownXVM();
        }
    }

    @Test
    public void withoutValidationTheMasterEngineBehaviorIsPreserved() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");
        assumeTrue(System.getProperty(OwnershipDiagnostics.VALIDATE_PROPERTY) == null,
                "this test documents the unvalidated master-compatible path");

        var runtime = new Runtime();
        try {
            var containerNative = NativeContainer.create(runtime, systemRepository());
            var moduleEcstasy   = containerNative.getConstantPool().modEcstasy();

            assertNotNull(MainContainer.create(runtime, containerNative, moduleEcstasy));
            assertNotNull(MainContainer.create(runtime, containerNative, moduleEcstasy),
                    "without the validation property the master-compatible sibling-main"
                            + " behavior remains (see MasterReuseEngineDemoTest)");
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
        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(RuntimeSingleRootTest::repositoryFor)
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
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }
}
