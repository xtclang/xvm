package org.xvm.runtime.template.annotations;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.annotations.xFuture.FutureHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for the display of a future's state.
 *
 * <p>These exercise {@link FutureHandle#describe} directly rather than through a handle, because
 * the whole point of the method is that it depends on nothing but the future: no container, no
 * composition, and no running service.
 */
public class xFutureTest {
    @Test
    public void absentFutureIsDescribedRatherThanThrowing() {
        // FutureTupleHandle.getFuture() returns null when it holds no futures, which used to make
        // toString() throw NullPointerException
        assertEquals("<no future>", FutureHandle.describe(null));
    }

    @Test
    public void pendingFutureIsNotCompleted() {
        assertEquals("Not completed", FutureHandle.describe(new CompletableFuture<>()));
    }

    @Test
    public void completedFutureReportsItsValue() {
        assertEquals("Completed: null",
                FutureHandle.describe(CompletableFuture.completedFuture(null)));
    }

    @Test
    public void cancelledFutureIsNotReportedAsCompleted() {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();
        future.cancel(true);

        // a cancelled future is done and completed exceptionally, so both orderings matter here
        assertEquals("<cancelled>", FutureHandle.describe(future));
    }

    @Test
    public void failedFutureIsNotReportedAsCompleted() {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("deliberate"));

        String description = FutureHandle.describe(future);
        assertEquals("<failed>", description);

        // the previous implementation rendered this as "Completed: <translated exception>"
        assertFalse(description.contains("Completed"), "a failed future must not read as completed");
    }

    @Test
    public void describingAFailedFutureDoesNotRethrow() {
        CompletableFuture<ObjectHandle> future = new CompletableFuture<>();
        future.completeExceptionally(new OutOfMemoryError("deliberate"));

        // get() would propagate this; describe() must not, since it runs from toString()
        assertEquals("<failed>", FutureHandle.describe(future));
    }
}
