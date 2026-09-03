package org.xvm.asm;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.xvm.asm.constants.TypeConstant;


/**
 * The type-system work that ONE thread is in the middle of: which types it is flattening, and
 * which ones it still owes.
 *
 * <p>This exists so that "who is executing" is a thing you can name and ask, rather than something
 * inferred. Building a {@code TypeInfo} is recursive and, on a shared pool, concurrent, and every
 * concurrency defect found in that machinery has been a question of ownership - is this
 * place-holder mine or a peer's; is this deferred entry mine to drain. Both were previously
 * answered by reading scattered thread-locals hung off {@link ConstantPool}, which is the wrong
 * home twice over: the state is not pool state, and a pool-scoped thread-local silently means
 * "per pool per thread", which is how deferred entries recorded against a shared library's pool
 * survived a drain that ran against a compile's pool.
 *
 * <h2>Why this shape</h2>
 *
 * <p>Every field is {@code final} and populated at construction, so there is no lazy-initialize,
 * null-check, assign dance at the use sites and no "has this been set up yet" state to get wrong.
 * The object is reachable only through {@link #current()}, so it is confined to one thread <i>by
 * construction</i> - which is why nothing here is synchronized or volatile, and why that is safe
 * rather than merely untested. The collections are mutable because the work is, but they are
 * mutable by exactly one thread.
 *
 * <p>Identity, not equality, for the building set: the place-holder it replaced was a field on one
 * {@code TypeConstant} instance, so an identity set preserves the original semantics exactly and
 * costs no hashing of types.
 */
public final class TypeSystemThread {
    private static final ThreadLocal<TypeSystemThread> CURRENT =
            ThreadLocal.withInitial(TypeSystemThread::new);

    /**
     * @return the calling thread's type-system work; never null, and never another thread's
     */
    public static TypeSystemThread current() {
        return CURRENT.get();
    }

    private TypeSystemThread() {}

    /**
     * The types this thread is flattening right now. Encountering a "being built" place-holder for
     * a type in here is real recursion - to complete X we need Y, and to build Y we need X - and
     * the only correct move is to defer. Encountering one for a type NOT in here means another
     * thread owns that build, which is not recursion, and deferring to it would be a bet that it
     * publishes before we run out of retries.
     */
    private final Set<TypeConstant> building = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The types this thread still owes TypeInfo work for. */
    private final List<TypeConstant> deferred = new ArrayList<>();

    /**
     * @param type  the type to test
     *
     * @return true iff THIS thread is currently building a TypeInfo for the type
     */
    public boolean isBuilding(TypeConstant type) {
        return building.contains(type);
    }

    /**
     * Declare that this thread has begun building the type's TypeInfo.
     *
     * @param type  the type
     */
    public void beginBuilding(TypeConstant type) {
        assert type != null;
        building.add(type);
    }

    /**
     * Declare that this thread has finished or abandoned building the type's TypeInfo. Always call
     * this from a {@code finally}: a leaked entry would convince this thread, on a later and
     * unrelated request, that it is already building a type it is not, and it would defer forever.
     *
     * @param type  the type
     */
    public void endBuilding(TypeConstant type) {
        building.remove(type);
    }

    /**
     * Record that a type needs its TypeInfo built or rebuilt before this thread's outermost
     * request can answer.
     *
     * @param type  the type
     */
    public void defer(TypeConstant type) {
        assert type != null;
        if (!deferred.contains(type)) {
            deferred.add(type);
        }
    }

    /**
     * @return true iff this thread owes any TypeInfo work
     */
    public boolean hasDeferred() {
        return !deferred.isEmpty();
    }

    /**
     * Take the owed work, leaving this thread owing nothing. The caller receives a snapshot it can
     * iterate while the build it drives adds new entries to a now-empty list.
     *
     * @return the types that were owed, possibly empty
     */
    public List<TypeConstant> takeDeferred() {
        if (deferred.isEmpty()) {
            return Collections.emptyList();
        }

        var listTaken = List.copyOf(deferred);
        deferred.clear();
        return listTaken;
    }

    /**
     * @return a description of anything this thread is still in the middle of, or null if it is
     *         idle
     *
     * <p>For asserting at a request boundary. A thread that finishes a compile still owing work, or
     * still marked as building something, has leaked - and the symptom otherwise appears on a later
     * and unrelated request, which is what makes it expensive to diagnose.
     */
    public String describeLeak() {
        return building.isEmpty() && deferred.isEmpty()
                ? null
                : "building=" + building + " deferred=" + deferred;
    }

    @Override
    public String toString() {
        return "TypeSystemThread{" + Thread.currentThread().getName()
                + " building=" + building.size() + " deferred=" + deferred.size() + '}';
    }
}
