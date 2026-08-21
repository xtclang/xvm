package org.xvm.api;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.Container;
import org.xvm.runtime.OwnershipDiagnostics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Tests for interpreter connector startup.
 */
public class InterpreterConnectorTest {
    @Test
    public void parallelConnectorsLoadIndependentNativeContainers() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var executor = Executors.newFixedThreadPool(CONNECTOR_COUNT);
        try {
            var futures = IntStream.range(0, CONNECTOR_COUNT)
                    .mapToObj(i -> executor.submit(loadEcstasyConnector()))
                    .toList();

            var containers = futures.stream()
                    .map(InterpreterConnectorTest::await)
                    .toList();

            IntStream.range(0, containers.size()).forEach(i -> {
                var container = containers.get(i);
                assertNotNull(container);
                assertNotNull(container.getNativeContainer());
                assertNotNull(container.getNativeContainer().getTemplate("Object"));

                containers.subList(0, i).forEach(that -> {
                    assertNotSame(container, that);
                    assertNotSame(container.getNativeContainer(), that.getNativeContainer());
                });
            });

            OwnershipDiagnostics.assertValid(true, containers.toArray(Container[]::new));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Container> loadEcstasyConnector() {
        return () -> {
            var connector = new InterpreterConnector(systemRepository());
            connector.loadModule(Constants.ECSTASY_MODULE);

            var container = connector.diagnosticContainer();
            assertNotNull(container);
            return container;
        };
    }

    private static Container await(Future<Container> future) {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
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
                .map(InterpreterConnectorTest::repositoryFor)
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
        return Path.of("").toAbsolutePath();
    }

    private static final int CONNECTOR_COUNT = 4;
}
