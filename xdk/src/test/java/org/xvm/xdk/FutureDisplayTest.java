package org.xvm.xdk;

import java.nio.file.Path;

import java.util.HashSet;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;

import org.xvm.runtime.template.annotations.xFuture;
import org.xvm.runtime.template.annotations.xFuture.FutureTupleHandle;

import org.xvm.runtime.template.text.xString;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises debugger display through actual future handles, without fixing the message wording.
 */
class FutureDisplayTest {
    private static Runtime runtime;
    private static NativeContainer container;

    @BeforeAll
    static void initializeRuntime() {
        var xdk = Path.of("build/install/xdk");
        var repository = new LinkedRepository(
                new DirRepository(xdk.resolve("lib").toFile(), true),
                new DirRepository(xdk.resolve("javatools").toFile(), true));
        runtime   = new Runtime();
        container = new NativeContainer(runtime, repository);
    }

    @AfterAll
    static void shutdownRuntime() {
        if (runtime != null) {
            runtime.shutdownXVM();
        }
    }

    @Test
    void tuplesWithoutFuturesArePrintable() {
        var pool = container.getConstantPool();
        var empty = new FutureTupleHandle(
                xFuture.INSTANCE.ensureClass(container, pool.ensureFuture(pool.ensureTupleType())),
                new ObjectHandle[0]);
        var value = xString.makeHandle("already resolved");
        var resolved = new FutureTupleHandle(
                xFuture.INSTANCE.ensureClass(container,
                        pool.ensureFuture(pool.ensureTupleType(pool.typeString()))),
                new ObjectHandle[] {value});

        for (var tuple : List.of(empty, resolved)) {
            assertNull(tuple.getFuture());
            assertFalse(assertDoesNotThrow(tuple::toString).isBlank());
        }
    }

    @Test
    void displayPreservesPendingAndCompletedValues() {
        var future = new CompletableFuture<ObjectHandle>();
        var handle = xFuture.makeHandle(future);
        var pending = assertDoesNotThrow(handle::toString);
        assertFalse(future.isDone());

        var value = xString.makeHandle("future result");
        future.complete(value);
        var completed = assertDoesNotThrow(handle::toString);
        assertNotEquals(pending, completed);
        assertTrue(completed.contains(value.toString()), "display must include the completed value");
        assertSame(value, future.getNow(null));
    }

    @Test
    void completionStatesHaveDistinctDescriptions() {
        var pending = new CompletableFuture<ObjectHandle>();
        var completed = CompletableFuture.<ObjectHandle>completedFuture(null);
        var cancelled = new CompletableFuture<ObjectHandle>();
        cancelled.cancel(false);
        var failed = CompletableFuture.<ObjectHandle>failedFuture(new IllegalStateException());

        var descriptions = new HashSet<String>();
        for (var future : List.of(pending, completed, cancelled, failed)) {
            descriptions.add(assertDoesNotThrow(xFuture.makeHandle(future)::toString));
        }
        assertEquals(4, descriptions.size(), "pending, successful, cancelled and failed states differ");
    }

    @Test
    void failedFutureDisplayDoesNotRenderTheStoredFailure() {
        var messageReads = new AtomicInteger();
        var exception = new IllegalStateException() {
            @Override
            public String getMessage() {
                messageReads.incrementAndGet();
                return "exception detail";
            }
        };
        var error = new OutOfMemoryError() {
            @Override
            public String getMessage() {
                messageReads.incrementAndGet();
                return "error detail";
            }
        };

        // The old get()/Utils.translate() path rendered the cause and allocated an XVM exception.
        for (var cause : List.of(exception, error)) {
            var future = CompletableFuture.<ObjectHandle>failedFuture(cause);
            var handle = xFuture.makeHandle(future);
            assertFalse(assertDoesNotThrow(handle::toString).isBlank());
            assertEquals(0, messageReads.get(), "display must inspect state without rendering the cause");
            assertTrue(future.isCompletedExceptionally());
        }
    }
}
