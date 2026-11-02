package org.xtclang.plugin.launchers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.concurrent.atomic.AtomicReference;

import org.gradle.api.logging.Logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForkedStrategyTest {
    @Test
    void aCanceledWaitStopsTheProgram(@TempDir final Path dir) throws Exception {
        final var source = Files.writeString(dir.resolve("Sleeper.java"),
            "class Sleeper { public static void main(String[] args) throws Exception { Thread.sleep(60_000); } }");
        final var java = ProcessHandle.current().info().command().orElseThrow();
        final var process = new ProcessBuilder(java, source.toString()).start();
        final var strategy = new AttachedStrategy<>(Logging.getLogger(ForkedStrategyTest.class), java);

        // A canceled build interrupts the task's thread while it waits for the program
        final var failure = new AtomicReference<Throwable>();
        final var waiter = new Thread(() -> {
            try {
                strategy.waitForProcess(process);
            } catch (final Throwable e) {
                failure.set(e);
            }
        });
        waiter.start();
        waiter.interrupt();
        waiter.join(30_000);

        assertInstanceOf(InterruptedException.class, failure.get());
        assertFalse(process.isAlive());
    }
}
