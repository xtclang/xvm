package org.xvm.runtime;

import java.util.ArrayDeque;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class ContainerActivityTest {
    @Test
    void terminationWaitsForNativeResourceCleanup() {
        try (var runtime = new Runtime()) {
            var request = container(runtime, null);
            var released = new CompletableFuture<Void>();
            var called = new AtomicBoolean();
            request.onTermination(() -> {
                assertTrue(called.compareAndSet(false, true), "Cleanup must run only once");
                return released;
            });
            var stopped = request.terminateServices();
            assertTrue(called.get());
            assertFalse(stopped.isDone());
            request.terminateServices();
            released.complete(null);
            assertTrue(stopped.isDone());
        }
    }

    @Test
    void completionIncludesDescendantsButNotParentsOrSiblings() {
        try (var runtime = new Runtime()) {
            var parent = container(runtime, null);
            var child = container(runtime, parent);
            var sibling = container(runtime, parent);
            var nested = container(runtime, child);
            parent.registerNativeCallback();
            sibling.registerNativeCallback();
            nested.registerNativeCallback();

            var complete = child.whenIdle();
            assertFalse(complete.isDone());
            nested.unregisterNativeCallback();
            assertTrue(complete.isDone());
            assertFalse(parent.whenIdle().isDone());

            sibling.unregisterNativeCallback();
            parent.unregisterNativeCallback();
            assertTrue(parent.whenIdle().isDone());
        }
    }

    @Test
    void cancellationWaitsForTheIOWorkerToExitAndLeavesOtherContainersUsable() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var runtime = new Runtime()) {
            var request = container(runtime, null);
            var sibling = container(runtime, null);
            var result = request.scheduleIO(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    release.await();
                }
                return 1;
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var idle = request.whenIdle();
                assertFalse(idle.isDone());
                var stopped = request.terminateServices();
                assertTrue(interrupted.await(5, TimeUnit.SECONDS));
                assertTrue(result.isCancelled());
                assertFalse(stopped.isDone(), "Cancellation must wait for worker cleanup");
                assertEquals(7, sibling.scheduleIO(() -> 7).get(5, TimeUnit.SECONDS));
                release.countDown();
                stopped.get(5, TimeUnit.SECONDS);
                assertTrue(idle.isCompletedExceptionally());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cancellationBeforeSubmissionRunsNoIO() {
        var submitted = new ArrayDeque<Runnable>();
        try (var runtime = new Runtime() {
            @Override
            protected void submitIO(Runnable task) {
                submitted.add(task);
            }
        }) {
            var request = container(runtime, null);
            var ran = new AtomicBoolean();
            var result = request.scheduleIO(() -> {
                ran.set(true);
                return 1;
            });
            assertTrue(request.terminateServices().isDone());
            submitted.remove().run();
            assertTrue(result.isCancelled());
            assertFalse(ran.get());
        }
    }

    private static Container container(Runtime runtime, Container parent) {
        return new Container(runtime, parent, new FileStructure("test").getModuleId()) {
            @Override
            public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
