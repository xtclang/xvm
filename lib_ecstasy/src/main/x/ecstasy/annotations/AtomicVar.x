/**
 * An atomic reference is one whose referent can be accessed across service boundaries without
 * invoking the service containing the reference. The reason that the reference can be accessed
 * across service boundaries is that the runtime treats an atomic reference _as a service_. (Only
 * service proxies and immutable objects can permeate the service boundary.)
 *
 * There are a few reasons to use atomic references:
 * * As a light-weight service: A service that exposes mutable storage of a referent.
 * * As a synchronous portal into a service: A service can expose real-time information through
 *   a property on the service's interface that is declared as an AtomicRef.
 * * As a means for coordination through atomic mutations: Allowing multiple services to safely
 *   share and aggregate information.
 *
 * Consider the following example that illustrates the first two use cases:
 *
 *   service LongRunningTask(WorkItem[] work)
 *       {
 *       // Even while the processBatch() method is running, other services can query
 *       // the percentDone value, and monitor its progress from 0% to 100%
 *       @Atomic Int percentDone;
 *
 *       void processBatch()
 *           {
 *           Int lastPercent = 0;
 *           for (Int i : 0..work.length)
 *               {
 *               Int percent = i * 100 / work.length;
 *               if (percent != lastPercent)
 *                   {
 *                   percentDone = percent;
 *                   lastPercent = percent;
 *                   }
 *
 *               // actually do the work ...
 *               WorkItem work = work[i];
 *               // ...
 *               }
 *           }
 *       }
 *
 * Consider the following example that illustrates the second and third use cases
 * (also see AtomicIntNumber for the specifics of the Int operation overrides):
 *
 *   static service Statistics
 *       {
 *       @Atomic Int hits;
 *       @Atomic Int misses;
 *       }
 *
 *   // somewhere else in various other services
 *   if (cache.contains(key))
 *       {
 *       // atomically increment the hit count
 *       ++Statistics.hits;
 *       }
 *   else
 *       {
 *       // atomically increment the miss count
 *       ++Statistics.misses;
 *       }
 */
mixin AtomicVar<Referent>
        extends VolatileVar<Referent>
        incorporates conditional AtomicIntNumber<Referent extends IntNumber> {
    /**
     * Atomically replace the referent for this variable reference.
     *
     * @param newValue  the value to set the referent to
     *
     * @return the old referent value
     */
    Referent exchange(Referent newValue) {
        Referent curValue = get();
        set(newValue);
        return curValue;
    }

    /**
     * Atomically replace the referent for this variable reference to the specified value
     * if and only if the current value matches the specified one.
     *
     * @param newValue  the value to set the referent to
     * @param oldValue  the value the referent is expected to be equal to
     *
     * @return True if the value has been successfully changed, False if the current value
     *         didn't match the `oldValue`
     */
    Boolean replace(Referent oldValue, Referent newValue) {
        return !replaceFailed(oldValue, newValue);
    }

    /**
     * Atomically replace the referent for this variable reference to the specified value
     * if and only if the current value matches the specified one.
     *
     * @param newValue  the value to set the referent to
     * @param oldValue  the value the referent is expected to be equal to
     *
     * @return True iff the operation failed due to the fact that the current value didn't match
     *         the specified `oldValue`
     * @return (conditional) the current value
     */
    conditional Referent replaceFailed(Referent oldValue, Referent newValue) {
        Referent curValue = get();
        if (&curValue == &oldValue) {
            set(newValue);
            return False;
        } else {
            return True, curValue;
        }
    }
}