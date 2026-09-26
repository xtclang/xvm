package org.xtclang.plugin.runtime;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.gradle.api.GradleException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectRuntimeCleanupTest {
    @Test
    void failedMethodLookupClosesTheNewLoader() {
        final var loader = new TrackingLoader(false, false);
        assertThrows(NoSuchMethodException.class, () -> DirectRuntimeBuildService.loadRuntimeEntry(loader));
        assertTrue(loader.closed);
    }

    @Test
    void linkageFailureClosesTheNewLoaderAndPreservesCloseFailure() {
        final var loader = new TrackingLoader(true, true);
        final var failure = assertThrows(LinkageError.class, () -> DirectRuntimeBuildService.loadRuntimeEntry(loader));
        assertTrue(loader.closed);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(loader.closeFailure, failure.getSuppressed()[0]);
    }

    @Test
    void oneCloseFailureDoesNotPreventOtherLoadersFromClosing() {
        final var first = new TrackingLoader(false, true);
        final var second = new TrackingLoader(false, true);
        final var third = new TrackingLoader(false, false);
        final var entries = List.of(entry(first), entry(second), entry(third));

        final var failure = assertThrows(GradleException.class, () -> DirectRuntimeBuildService.closeEntries(entries));
        assertTrue(first.closed);
        assertTrue(second.closed);
        assertTrue(third.closed);
        assertSame(first.closeFailure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(second.closeFailure, failure.getSuppressed()[0].getCause());
    }

    private static DirectRuntimeBuildService.RuntimeEntry entry(final TrackingLoader loader) {
        return new DirectRuntimeBuildService.RuntimeEntry(loader, null, null, null);
    }

    private static final class TrackingLoader extends URLClassLoader {
        private final boolean failLoading;
        private final boolean failClosing;
        private final IOException closeFailure = new IOException("close failed");
        private boolean closed;

        private TrackingLoader(final boolean failLoading, final boolean failClosing) {
            super(new URL[0]);
            this.failLoading = failLoading;
            this.failClosing = failClosing;
        }

        @Override
        public Class<?> loadClass(final String name) {
            if (failLoading) {
                throw new LinkageError("executor linkage failed");
            }
            return String.class; // A loaded class with none of the required executor methods.
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
            if (failClosing) {
                throw closeFailure;
            }
        }
    }
}
