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
 *       Void processBatch()
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
mixin AtomicRef<RefType>
        into Ref<RefType>
    {
    Boolean replace(RefType oldValue, RefType newValue)
        {
        return !replaceFailed(oldValue, newValue);
        }

    conditional RefType replaceFailed(RefType oldValue, RefType newValue)
        {
        RefType curValue = get();
        if (curValue == oldValue)
            {
            set(newValue);
            return false;
            }
        else
            {
            return true, curValue;
            }
        }
    }
