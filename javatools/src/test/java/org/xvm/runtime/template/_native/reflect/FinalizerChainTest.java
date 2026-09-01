package org.xvm.runtime.template._native.reflect;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the constructor finalizer chain (must-audit graduation from the clone-study trace,
 * fixed in commit c621b1dca). {@code FullyBoundHandle.chain()} asserted {@code m_next == null}
 * and then overwrote it, but that was never a sound invariant: {@code Frame.chainFinalizer} can
 * already have linked a next handle onto a frame's head finalizer before
 * {@code ClassTemplate}'s construction epilogue folds the per-frame finalizers together -
 * reachable single-threaded on master when an annotation-mixin constructor with a finalizer
 * delegates to a super constructor that also has one. Red on master: with {@code -ea} the
 * second chain call dies on the assert; with {@code -da} it silently overwrites the link and
 * DROPS the already-chained finalizers. The fix appends at the tail, preserving every link.
 */
public class FinalizerChainTest {
    @Test
    public void chainAppendsAtTailInsteadOfDroppingLinkedFinalizers() {
        assumeSystemModules();

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository(), ErrorListener.RUNTIME);
            var hHead   = new FullyBoundHandle(container, null, Utils.OBJECTS_NONE);
            var hSecond = new FullyBoundHandle(container, null, Utils.OBJECTS_NONE);
            var hThird  = new FullyBoundHandle(container, null, Utils.OBJECTS_NONE);

            assertSame(hHead, hHead.chain(hSecond));
            // the second chain call is the case master got wrong: the head's m_next is
            // already occupied, so master either asserted (-ea) or overwrote and silently
            // dropped hSecond (-da)
            assertSame(hHead, hHead.chain(hThird));

            assertSame(hSecond, hHead.m_next,
                    "the first linked finalizer must survive a later chain call");
            assertSame(hThird, hSecond.m_next,
                    "the second linked finalizer must be appended at the tail");
            assertNull(hThird.m_next);
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers (same discovery as ArrayViewGuardTest) ---------------------------------------

    private static void assumeSystemModules() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");
    }

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
                .map(FinalizerChainTest::repositoryFor)
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
