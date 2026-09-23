package org.xvm.runtime;

import java.io.IOException;

import java.lang.ref.Reference;

import java.time.Duration;

import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class OwnedResourceTest {
    @Test
    void idleNestedOwnerIsRetainedUntilItsLastResourceCloses() {
        try (var runtime = new Runtime()) {
            var parent = container(runtime, null);
            var child = container(runtime, parent);
            var cleaned = new CompletableFuture<Void>();
            assertTrue(runtime.retainedContainers().isEmpty());
            var first = child.acquireResource(() -> {
                assertEquals(Set.of(child), runtime.retainedContainers());
                return new Object();
            }, _ -> CompletableFuture.completedFuture(null));
            var last = child.acquireResource(Object::new, _ -> cleaned);
            assertTrue(child.whenIdle().isDone(), "Idle resources must still retain their owner");
            assertEquals(Set.of(child), runtime.retainedContainers());
            first.closeAsync().join();
            assertEquals(Set.of(child), runtime.retainedContainers());
            var closing = last.closeAsync();
            assertFalse(closing.isDone());
            assertEquals(Set.of(child), runtime.retainedContainers());
            cleaned.complete(null);
            closing.join();
            assertTrue(runtime.retainedContainers().isEmpty());
            assertTrue(runtime.containers().contains(child), "Release must preserve ordinary discovery");
        }
    }

    @Test
    void parentTerminationRetainsDescendantsUntilTheirCleanupFinishes() {
        var cleaned = new CompletableFuture<Void>();
        try (var runtime = new Runtime()) {
            var root = container(runtime, null);
            var parent = container(runtime, root);
            var child = container(runtime, parent);
            var nested = container(runtime, child);
            var sibling = container(runtime, root);
            var siblingClosed = new AtomicBoolean();
            nested.onTermination(() -> cleaned);
            sibling.acquireResource(() -> (AutoCloseable) () -> siblingClosed.set(true));
            assertEquals(Set.of(nested, sibling), runtime.retainedContainers());

            var stopped = parent.terminateServices();
            assertFalse(stopped.isDone());
            assertEquals(Set.of(parent, child, nested, sibling), runtime.retainedContainers());
            cleaned.complete(null);
            stopped.join();
            assertEquals(Set.of(sibling), runtime.retainedContainers());
            assertFalse(siblingClosed.get());
            root.terminateServices().join();
            assertTrue(siblingClosed.get());
            assertTrue(runtime.retainedContainers().isEmpty());
        } finally {
            cleaned.complete(null);
        }
    }

    @Test
    void runtimeShutdownRejectsAcquisitionBeforeContainerTerminationBegins() throws Exception {
        var stopping = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var runtime = new Runtime(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = new Container(runtime, null, new FileStructure("test").getModuleId()) {
                @Override
                public CompletableFuture<Void> terminateServices() {
                    stopping.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CompletionException(e);
                    }
                    return super.terminateServices();
                }

                @Override
                public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                    throw new UnsupportedOperationException();
                }
            };
            var closing = workers.submit(() -> { runtime.close(); });
            try {
                assertTrue(stopping.await(5, TimeUnit.SECONDS));
                var opened = new AtomicBoolean();
                assertThrows(IllegalStateException.class, () -> owner.acquireResource(() -> {
                    opened.set(true);
                    return new Object();
                }, _ -> CompletableFuture.completedFuture(null)));
                assertThrows(IllegalStateException.class,
                        () -> owner.onTermination(() -> CompletableFuture.completedFuture(null)));
                assertFalse(opened.get());
                assertEquals(0, owner.ownedResourceCount());
                assertTrue(runtime.retainedContainers().isEmpty());
            } finally {
                release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);
            assertTrue(runtime.isTerminated());
        } finally {
            release.countDown();
        }
    }

    @Test
    void explicitCloseRemovesRegistrationsAndRunsOnce() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var closes = new AtomicInteger();
            for (int i = 0; i < 100; i++) {
                AutoCloseable value = closes::incrementAndGet;
                var resource = owner.acquireResource(() -> {
                    assertFalse(Thread.holdsLock(owner));
                    return value;
                });
                assertSame(value, resource.get());
                assertEquals(1, owner.ownedResourceCount());
                resource.closeAsync().join();
                resource.closeAsync().join();
                assertEquals(0, owner.ownedResourceCount());
                assertThrows(IllegalStateException.class, resource::get);
            }
            owner.terminateServices().join();
            assertEquals(100, closes.get());
        }
    }

    @Test
    void shutdownWaitsForExplicitCleanupAndCannotBeBypassedThroughItsFuture() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var cleaned = new CompletableFuture<Void>();
            var resource = owner.acquireResource(Object::new, _ -> cleaned);
            var cancelledView = resource.closeAsync();
            assertTrue(cancelledView.cancel(false));
            var completedView = resource.closeAsync();
            assertTrue(completedView.complete(null));
            var actualCleanup = resource.closeAsync();
            var stopped = owner.terminateServices();
            assertFalse(actualCleanup.isDone());
            assertFalse(stopped.isDone());
            assertEquals(1, owner.ownedResourceCount());
            cleaned.complete(null);
            actualCleanup.join();
            stopped.join();
            assertEquals(0, owner.ownedResourceCount());
        }
    }

    @Test
    void terminationRejectsAcquisitionBeforeCallingTheFactory() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            owner.terminateServices().join();
            var opened = new AtomicBoolean();
            assertThrows(IllegalStateException.class, () -> owner.acquireResource(() -> {
                opened.set(true);
                return new Object();
            }, _ -> CompletableFuture.completedFuture(null)));
            assertFalse(opened.get());
            assertEquals(0, owner.ownedResourceCount());
        }
    }

    @Test
    void runtimeShutdownDuringAcquisitionClosesTheUndeliveredResource() throws Exception {
        var entered = new CountDownLatch(1);
        var acquired = new CountDownLatch(1);
        var cleaned = new CompletableFuture<Void>();
        var cleanupCalled = new AtomicBoolean();
        try (var runtime = new Runtime(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = container(runtime, null);
            var opening = workers.submit(() -> owner.acquireResource(() -> {
                assertFalse(Thread.holdsLock(owner));
                assertEquals(Set.of(owner), runtime.retainedContainers());
                entered.countDown();
                acquired.await();
                return new Object();
            }, _ -> {
                cleanupCalled.set(true);
                return cleaned;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> runtime.close(Duration.ZERO));
                var stopped = owner.terminateServices();
                assertFalse(stopped.isDone());
                assertFalse(cleanupCalled.get());
                acquired.countDown();
                var failure = assertThrows(ExecutionException.class, () -> opening.get(5, TimeUnit.SECONDS));
                assertInstanceOf(IllegalStateException.class, failure.getCause());
                assertTrue(cleanupCalled.get());
                assertFalse(stopped.isDone(), "Shutdown must also await disposal after acquisition");
                assertEquals(Set.of(owner), runtime.retainedContainers());
                cleaned.complete(null);
                stopped.get(5, TimeUnit.SECONDS);
                assertEquals(0, owner.ownedResourceCount());
                assertTrue(runtime.retainedContainers().isEmpty());
            } finally {
                acquired.countDown();
                cleaned.complete(null);
            }
        }
    }

    @Test
    void failedAcquisitionReleasesItsReservationDuringShutdown() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var stopped = new AtomicReference<CompletableFuture<Void>>();
            var cleanupCalled = new AtomicBoolean();
            var expected = new IOException("acquisition failed");
            var failure = assertThrows(IOException.class, () -> owner.acquireResource(() -> {
                stopped.set(owner.terminateServices());
                assertFalse(stopped.get().isDone());
                throw expected;
            }, _ -> {
                cleanupCalled.set(true);
                return CompletableFuture.completedFuture(null);
            }));
            assertSame(expected, failure);
            stopped.get().join();
            assertFalse(cleanupCalled.get(), "The factory returned no resource to close");
            assertEquals(0, owner.ownedResourceCount());
            assertTrue(runtime.retainedContainers().isEmpty());
        }
    }

    @Test
    void nullAcquisitionDoesNotLeaveARegistration() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var cleanupCalled = new AtomicBoolean();
            assertThrows(NullPointerException.class, () -> owner.acquireResource(() -> null, _ -> {
                cleanupCalled.set(true);
                return CompletableFuture.completedFuture(null);
            }));
            assertFalse(cleanupCalled.get());
            assertEquals(0, owner.ownedResourceCount());
            assertTrue(runtime.retainedContainers().isEmpty());
            owner.terminateServices().join();
        }
    }

    @Test
    void explicitCloseRacingWithTerminationDoesNotHoldOwnerOrResourceMonitors() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cleaned = new CompletableFuture<Void>();
        try (var runtime = new Runtime(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = container(runtime, null);
            var closes = new AtomicInteger();
            var registered = new AtomicReference<OwnedResource<Object>>();
            var resource = owner.acquireResource(Object::new, _ -> {
                assertFalse(Thread.holdsLock(owner));
                assertFalse(Thread.holdsLock(registered.get()));
                closes.incrementAndGet();
                entered.countDown();
                // Hold cleanup initiation here to force shutdown into the concurrent-close path.
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
                return cleaned;
            });
            registered.set(resource);
            var closing = workers.submit(resource::closeAsync);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var stopped = owner.terminateServices();
                assertFalse(stopped.isDone());
                assertEquals(1, closes.get());
                release.countDown();
                var closed = closing.get(5, TimeUnit.SECONDS);
                cleaned.complete(null);
                closed.get(5, TimeUnit.SECONDS);
                stopped.get(5, TimeUnit.SECONDS);
                assertEquals(1, closes.get());
            } finally {
                release.countDown();
                cleaned.complete(null);
            }
        }
    }

    @Test
    void shutdownStillRunsAndAwaitsOtherCleanupAfterOneActionThrows() {
        var runtime = new Runtime();
        try {
            var owner = container(runtime, null);
            var expected = new IllegalStateException("cleanup failed");
            var cleaned = new CompletableFuture<Void>();
            var cleanupCalled = new AtomicBoolean();
            owner.acquireResource(Object::new, _ -> { throw expected; });
            owner.acquireResource(Object::new, _ -> {
                cleanupCalled.set(true);
                return cleaned;
            });
            var stopped = owner.terminateServices();
            assertTrue(cleanupCalled.get());
            assertFalse(stopped.isDone());
            cleaned.complete(null);
            assertSame(expected, assertThrows(CompletionException.class, stopped::join).getCause());
            assertEquals(0, owner.ownedResourceCount());
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isTerminated());
        } finally {
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isTerminated());
        }
    }

    @Test
    void asynchronousExplicitCloseFailureAlsoReachesLaterShutdown() {
        var runtime = new Runtime();
        try {
            var parent = container(runtime, null);
            var owner = container(runtime, parent);
            var expected = new IOException("async cleanup failed");
            var cleaned = new CompletableFuture<Void>();
            var resource = owner.acquireResource(Object::new, _ -> cleaned);
            var closed = resource.closeAsync();
            cleaned.completeExceptionally(expected);
            assertSame(expected, assertThrows(CompletionException.class, closed::join).getCause());
            assertEquals(0, owner.ownedResourceCount());
            assertEquals(Set.of(owner), runtime.retainedContainers());
            assertSame(expected, assertThrows(CompletionException.class,
                    () -> parent.terminateServices().join()).getCause());
            assertEquals(Set.of(parent, owner), runtime.retainedContainers());
            assertThrows(IllegalStateException.class, runtime::close);
        } finally {
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isTerminated());
        }
    }

    @Test
    void checkedCloseFailureIsPreserved() {
        var runtime = new Runtime();
        try {
            var owner = container(runtime, null);
            var expected = new IOException("close failed");
            AutoCloseable value = () -> { throw expected; };
            var resource = owner.acquireResource(() -> value);
            assertSame(expected, assertThrows(CompletionException.class,
                    () -> resource.closeAsync().join()).getCause());
            assertThrows(IllegalStateException.class, runtime::close);
        } finally {
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isTerminated());
        }
    }

    @Test
    void missingCleanupCompletionFailsShutdown() {
        var runtime = new Runtime();
        try {
            var owner = container(runtime, null);
            owner.acquireResource(Object::new, _ -> null);
            assertInstanceOf(NullPointerException.class, assertThrows(CompletionException.class,
                    () -> owner.terminateServices().join()).getCause());
            assertEquals(0, owner.ownedResourceCount());
            assertThrows(IllegalStateException.class, runtime::close);
        } finally {
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isTerminated());
        }
    }

    @Test
    void sharedServiceResourcesBelongToTheCallingApplication() {
        try (var runtime = new Runtime()) {
            var parent = container(runtime, null);
            var child = container(runtime, parent);
            var sibling = container(runtime, parent);
            var closed = new AtomicBoolean();
            var siblingClosed = new AtomicBoolean();
            AutoCloseable value = () -> closed.set(true);
            var siblingValue = new Object();
            var childFrame = frame(child, null);
            var siblingFrame = frame(sibling, null);
            var sharedFrame = frame(parent, childFrame);
            var resource = sharedFrame.acquireResource(() -> value);
            var siblingResource = frame(parent, siblingFrame).acquireResource(() -> siblingValue,
                    _ -> {
                        siblingClosed.set(true);
                        return CompletableFuture.completedFuture(null);
                    });
            assertEquals(0, parent.ownedResourceCount());
            assertEquals(1, child.ownedResourceCount());
            assertEquals(1, sibling.ownedResourceCount());
            child.terminateServices().join();
            assertTrue(closed.get());
            assertThrows(IllegalStateException.class, resource::get);
            assertFalse(siblingClosed.get());
            assertSame(siblingValue, siblingResource.get());
            parent.terminateServices().join();
            assertTrue(siblingClosed.get());
            Reference.reachabilityFence(childFrame);
            Reference.reachabilityFence(siblingFrame);
        }
    }

    @Test
    void cancelledCallbacksReleaseFramesAndRegistrations() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var frame = frame(owner, null);
            for (int i = 0; i < 100; i++) {
                var callback = new WeakCallback(frame, null);
                assertEquals(1, frame.f_context.getCallbackMap().size());
                callback.discard();
                callback.discard();
                assertTrue(frame.f_context.getCallbackMap().isEmpty());
                assertEquals(0, owner.ownedResourceCount());
                assertEquals(null, callback.extractCallback());
            }
            var abandoned = new WeakCallback(frame, null);
            owner.terminateServices().join();
            assertTrue(frame.f_context.getCallbackMap().isEmpty());
            assertEquals(null, abandoned.extractCallback());
        }
    }

    @Test
    void callbackExecutionRacesCancellationWithoutRetainingEitherRegistration() throws Exception {
        try (var runtime = new Runtime(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = container(runtime, null);
            var frame = frame(owner, null);
            for (int i = 0; i < 100; i++) {
                var callback = new WeakCallback(frame, null);
                var start = new CountDownLatch(1);
                var execution = executor.submit(() -> {
                    start.await();
                    return callback.extractCallback();
                });
                var cancellation = executor.submit(() -> {
                    start.await();
                    callback.discard();
                    return null;
                });
                start.countDown();
                execution.get(5, TimeUnit.SECONDS);
                cancellation.get(5, TimeUnit.SECONDS);
                assertTrue(frame.f_context.getCallbackMap().isEmpty());
                assertEquals(0, owner.ownedResourceCount());
            }
        }
    }

    @Test
    void callbackTerminationCancelsAnAlarmAttachedAfterShutdown() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var frame = frame(owner, null);
            var callback = new WeakCallback(frame, null);
            var cancellations = new AtomicInteger();
            owner.terminateServices().join();
            callback.onDiscard(cancellations::incrementAndGet);
            callback.discard();
            assertEquals(1, cancellations.get());
            assertTrue(frame.f_context.getCallbackMap().isEmpty());
        }
    }

    @Test
    void extractingCallbackDoesNotCancelTheAlarmBeforeItsCallbackIsQueued() {
        try (var runtime = new Runtime()) {
            var owner = container(runtime, null);
            var frame = frame(owner, null);
            var callback = new WeakCallback(frame, null);
            var cancelled = new AtomicBoolean();
            callback.onDiscard(() -> cancelled.set(true));
            assertSame(frame, callback.extractCallback().frame());
            assertFalse(cancelled.get(), "Execution must release keep-alive after queuing its callback");
            assertTrue(frame.f_context.getCallbackMap().isEmpty());
            assertEquals(0, owner.ownedResourceCount());
            owner.terminateServices().join();
            assertFalse(cancelled.get());
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

    private static Frame frame(Container container, Frame caller) {
        var context = new ServiceContext(container, "test", 0);
        var fiber = new Fiber(context, new CallLaterRequest(caller, null, new ObjectHandle[0], 0));
        return new Frame(fiber, 0, new Op[0], new ObjectHandle[0], Op.A_IGNORE, null);
    }
}
