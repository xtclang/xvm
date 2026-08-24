package org.xvm.runtime.template._native.net;


import java.io.File;

import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.Runtime;

import org.xvm.runtime.template._native.net.xRTSocket.SocketHandle;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the shared socket state of {@code SocketHandle} views (must-audit graduation from the
 * clone study). The handle is created with a masked composition - {@code getCanonicalType()} is
 * {@code net.Socket}, not the native class - so {@code revealOrigin()} manufactures a fresh view
 * clone on native entries. On the old shape the native {@code Socket} lived in a per-view
 * {@code public volatile} field: the write after connect landed on one view while the registered
 * service handle kept {@code null} forever, and any additional access view silently dropped the
 * close-path write. The socket now lives in a holder shared by every view (the
 * {@code xRTServer.HttpServerHandle} idiom).
 */
public class SocketHandleStateSharingTest {
    /**
     * A socket installed through one revealed view must be visible through every other view,
     * and clearing it through one view must clear it for all. Red on the per-view field shape,
     * where each revealed clone carried its own socket reference.
     */
    @Test
    public void socketStateIsSharedAcrossViews() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var template  = NativeTemplates.get(container).socket();
            var context   = container.createServiceContext("socket-view-test");
            var hMasked   = new SocketHandle(template.getCanonicalClass(), context);

            var hViewA = (SocketHandle) hMasked.revealOrigin();
            var hViewB = (SocketHandle) hMasked.revealOrigin();
            assertNotSame(hMasked, hViewA,
                    "the canonical composition is masked; revealOrigin must produce a view");
            assertNotSame(hViewA, hViewB, "each reveal produces a fresh view");

            var socket = new Socket();
            hViewA.setSocket(socket);

            assertSame(socket, hViewB.getSocket(),
                    "a socket installed through one view must be visible through every view;"
                            + " a per-view field left the sibling views with null");
            assertSame(socket, hMasked.getSocket(),
                    "the registered (masked) service handle must see the socket too;"
                            + " on the old shape it kept null forever");

            hViewB.setSocket(null);
            assertNull(hViewA.getSocket(),
                    "the close-path write must be visible through every view");
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
                .map(SocketHandleStateSharingTest::repositoryFor)
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
