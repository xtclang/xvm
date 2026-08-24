package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the default-deny view-clone rule at the {@code ObjectHandle} base (clone-eradication
 * study follow-up). Every freeze-split fix so far was a per-class patch - cells for
 * {@code GenericHandle}, refusal guards for arrays, tuples, functions, delegates - and the study
 * sweep still found unguarded classes. The base rule inverts the default: a handle whose
 * lifecycle is still live refuses {@code cloneAs} unless its class explicitly opts in via
 * {@code supportsMutableViews()}, so the next view-capable handle class someone adds fails
 * loudly instead of desyncing silently. {@code GenericHandle}'s opt-in is exercised by
 * {@code FreezeViewSharingTest}, which would fail if the opt-in broke.
 */
public class ObjectHandleViewGuardTest {
    /**
     * A mutable handle with no opt-in must refuse view cloning; the same handle becomes
     * cloneable once its lifecycle reaches the terminal immutable state.
     */
    @Test
    public void mutableHandleWithoutOptInRefusesViewCloning() {
        var handle = new ObjectHandle(null) {};
        handle.m_fMutable = true;

        var error = assertThrows(IllegalStateException.class,
                () -> handle.cloneAs(null),
                "an unreviewed mutable handle class must fail loudly, not desync");
        assertTrue(error.getMessage().contains("mutable handle"), error.getMessage());

        assertTrue(handle.makeImmutable());
        assertNotSame(handle, handle.cloneAs(null),
                "an immutable handle has a terminal lifecycle and must clone");
    }

    /**
     * Immutable handles clone freely from the start - ConstHeap relocation depends on it.
     */
    @Test
    public void immutableHandleClonesFreely() {
        var handle = new ObjectHandle(null) {};

        assertNotSame(handle, handle.cloneAs(null));
    }
}
