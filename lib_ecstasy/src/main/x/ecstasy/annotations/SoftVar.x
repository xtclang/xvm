/**
 * A SoftVar is used to make a reference into a soft reference. A soft reference is a reference
 * whose reference can be cleared by the garbage collector if the garbage collector decides that it
 * wants to reclaim the memory represented by the referent. Generally, it is expected that the
 * runtime will attempt to prioritize the retention of soft references that represent some
 * combination of smaller memory usage, more frequent usage, more recent usage, and (in the case of
 * the combination of SoftVar and LazyVar) more expensive-to-calculate values.
 *
 * In order to avoid the possibility of an unassigned reference becoming visible, the SoftVar must
 * be of a {@link Referent} that has a default value (such as {@link Nullable}, with its default
 * value of `Null`), or it must be combined with {@link LazyVar} so that the value is
 * calculable on-demand.
 *
 * A SoftVar can have a {@link notify} notification function provided in its construction that is
 * enqueued into the service's runtime event queue each time that the soft reference is cleared by
 * the garbage collector; see {@link Service.pendingRuntimeEvents} and
 * {@link Service.dispatchRuntimeEvents}.
 *
 * TODO use timer instead of clock
 */
mixin SoftVar<Referent>(function void ()? notify)
        into Var<Referent> {
    /**
     * The runtime's clock that this reference will stamp itself with on every access. The runtime
     * clock is a clock optimized for a high number of accesses, and not for correctness with
     * respect to wall clock time.
     */
    public/private @Inject Clock clock;

    /**
     * The last time that this reference was accessed, maintained for the use of the garbage
     * collector.
     *
     * The garbage collector is under no obligation to use this information in its decisions to
     * clear or retain soft references, but it may choose to use this information to optimize which
     * soft references are *not* cleared. Furthermore, the garbage collector is permitted to alter
     * or reset this value, so this value should not be trusted as authoritative.
     */
    public/private Time? lastAccessTime;

    /**
     * The number of times that this reference has been accessed, maintained for the use of the
     * garbage collector.
     *
     * The garbage collector is under no obligation to use this information in its decisions to
     * clear or retain soft references, but it may choose to use this information to optimize which
     * soft references are *not* cleared. Furthermore, the garbage collector is permitted to alter
     * or reset this value, so this value should not be trusted as authoritative.
     */
    public/private Int accessCount = 0;

    /**
     * Iff this is a lazy reference, this is the cost (in terms of time) of calculating the value of
     * this reference, from the last time that this reference's value was calculated.
     *
     * The garbage collector is under no obligation to use this information in its decisions to
     * clear or retain soft references, but it may choose to use this information to optimize which
     * soft references are *not* cleared. Furthermore, the garbage collector is permitted to alter
     * or reset this value, so this value should not be trusted as authoritative.
     */
    public/private Duration? lastCalcDuration;

    @Override
    Referent get() {
        // soft+lazy references are unassigned after being cleared by the garbage collector
        if (!assigned) {
            // TODO assert (&this).incorporates_(LazyVar);

            Time     start = clock.now;
            Referent value = super();
            Time     stop  = clock.now;

            ++accessCount;
            lastAccessTime   = stop;
            lastCalcDuration = stop - start;

            return value;
        }

        ++accessCount;
        lastAccessTime = clock.now;
        return super();
    }
}
