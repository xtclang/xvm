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

import org.xvm.asm.constants.HandleConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards {@link HandleConstant} against serving one container's live runtime handle raw to a
 * sibling container. Master's {@code getHandle(Frame)} returned the wrapped handle unconditionally:
 * two containers loaded over one module share the module's constant pool, so a sibling resolving
 * the constant received the creator's live object with no ownership check, bypassing the
 * maskAs/proxy isolation machinery entirely. Found by the must-audit row 125 completion sweep.
 */
public class HandleConstantOwnerGuardTest {
    /**
     * The owner may always retrieve its own handle; another container may only receive what could
     * legally cross a service boundary anyway (a non-service, pass-through value). A mutable
     * container-owned object must not be served raw across containers - on master it was.
     */
    @Test
    public void liveHandleIsNotServedRawAcrossContainers() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var containerA = NativeContainer.create(runtime, systemRepository());
            var containerB = NativeContainer.create(runtime, systemRepository());
            var poolB      = containerB.getConstantPool();

            var clzB    = new ClassComposition(containerB, containerB.getTemplate("Object"),
                    poolB.typeObject());
            var hOwnedB = new GenericHandle(clzB);
            assertTrue(hOwnedB.isMutable(), "the probe handle must be mutable owner state");

            var constant = new HandleConstant(hOwnedB);

            assertSame(hOwnedB, constant.getHandleFor(containerB),
                    "the owning container must always be served its own handle");

            var error = assertThrows(IllegalStateException.class,
                    () -> constant.getHandleFor(containerA),
                    "a mutable container-owned handle must not be served raw to a sibling");
            assertTrue(error.getMessage().contains("cannot be served raw"), error.getMessage());

            // pass-through values keep crossing freely: an immutable core-type handle is exactly
            // what could legally cross a service boundary, so the guard must not over-tighten
            var hString = xString.makeHandle(containerB, "shared-constant");
            assertSame(hString, new HandleConstant(hString).getHandleFor(containerA),
                    "immutable pass-through values must still be served across containers");
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
                .map(HandleConstantOwnerGuardTest::repositoryFor)
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
