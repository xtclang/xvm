/**
 * A WeakVar is used to make a reference into a weak reference. A weak reference is a reference
 * whose reference can be cleared by the garbage collector if the garbage collector determines that
 * no other non-weak references exist to the same referent.
 *
 * In order to avoid the possibility of an unassigned reference becoming visible, the WeakVar must
 * be of a `Referent` that has a default value (such as [Nullable], with its default value of
 * `Null`).
 *
 * A WeakVar can have a `notify` notification function provided in its construction that is
 * enqueued into the service's runtime event queue each time that the weak reference is cleared by
 * the garbage collector; see [Service.pendingRuntimeEvents] and [Service.dispatchRuntimeEvents].
 */
mixin WeakVar<Referent>(function void ()? notify)
        into Var<Referent> {
}
