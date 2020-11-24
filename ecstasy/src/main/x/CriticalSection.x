/**
 * A CriticalSection is used to constrain reentrancy in a service for a scope of processing that
 * must not be interrupted. By default, a CriticalSection sets the reentrancy to {@link
 * Service.Reentrancy.Forbidden}, but it can be specified as {@link Service.Reentrancy.Exclusive}
 * instead.
 *
 * The CriticalSection mechanism is a cooperative mechanism, and is not intended to be used as a
 * strict arbiter of reentrancy. Rather, it is intended for uses in which reducing concurrency and
 * interruption is preferable to the potential side effects of unexpected state manipulation; by
 * controlling reentrancy policy, the CriticalSection mechanism prevents opportunities for
 * unexpected state change.
 *
 * The CriticalSection is stored on the current service and exposed as {@link
 * Service.criticalSection}. When a new CriticalSection is created, it automatically registers
 * itself with the current service using the {@link Service.registerCriticalSection} method.
 * Employing either a `using` or `try`-with-resources block will automatically
 * unregister the CriticalSection at the conclusion of the block, restoring the previous reentrancy
 * setting for the service. When the CriticalSection unregisters itself, it re-registers whatever
 * previous CriticalSection it replaced (if any).
 *
 * CriticalSections _nest_. When a new CriticalSection is created, it configures itself to use no
 * more liberal reentrancy than is already configured for the service. This allows the developer to
 * create a new CriticalSection without concern that it is weakening an existing CriticalSection. In
 * the following example, two different CriticalSections are used, each potentially escalating the
 * level of reentrancy-prevention:
 *
 *   // lock down reentrancy just to the point that only the current conceptual thread of execution
 *   // is allowed to re-enter this service
 *   using (new CriticalSection(Exclusive))
 *       {
 *       prepare();
 *
 *       // lock down reentrancy completely
 *       using (new CriticalSection())
 *           {
 *           commit();
 *           }
 *       }
 *
 * The CriticalSection also keeps track of its {@link duration}, as a potential aid to both the
 * developer and the runtime.
 */
const CriticalSection
        implements Closeable
    {
    construct(Service.Reentrancy reentrancy = Forbidden)
        {
        assert reentrancy == Exclusive || reentrancy == Forbidden;

        // store off the previous reentrancy setting; it will be replaced by this CriticalSection,
        // and restored when this CriticalSection is closed
        previousReentrancy = this:service.reentrancy;

        // store off the previous CriticalSection; it will be replaced by this CriticalSection, and
        // restored when this CriticalSection is closed
        previousCriticalSection = this:service.criticalSection;

        // calculate the re-entrancy for the critical section
        this.reentrancy = reentrancy.maxOf(previousReentrancy);
        }
    finally
        {
        this:service.reentrancy = this.reentrancy;
        this:service.registerCriticalSection(this);
        }

    /**
     * The timer selected by the runtime to measure CriticalSection duration.
     */
    @Inject Timer timer;

    /**
     * The service `Reentrancy` setting that this CriticalSection replaced, if any.
     */
    Service.Reentrancy reentrancy;

    /**
     * The service `Reentrancy` setting that this CriticalSection replaced, if any.
     */
    Service.Reentrancy previousReentrancy;

    /**
     * The CriticalSection that this CriticalSection replaced, if any.
     */
    CriticalSection? previousCriticalSection;

    /**
     * The time at which this CriticalSection began.
     */
    DateTime startTime;

    /**
     * The duration of this CriticalSection.
     */
    Duration duration.get()
        {
        return timer.elapsed;
        }

    /**
     * Determine whether this CriticalSection is the active CriticalSection for the current service.
     */
    Boolean active.get()
        {
        return this:service.criticalSection == this;
        }

    /**
     * Determine whether this CriticalSection is registered with the current service, regardless of
     * whether it is the currently-active CriticalSection.
     */
    Boolean registered.get()
        {
        CriticalSection? cs = this:service.criticalSection;
        while (cs != Null)
            {
            if (this == cs)
                {
                return True;
                }

            cs = cs.previousCriticalSection;
            }

        return False;
        }

    /**
     * Close the CriticalSection. This method is invoked automatically by the `using` or
     * `try` with-resources keywords.
     */
    @Override
    void close(Exception? cause = Null)
        {
        if (registered)
            {
            // the reason that the CriticalSection checks whether it is registered instead of if it
            // is active is that it is possible that a downstream CriticalSection was not properly
            // closed, e.g. by failing to use a "using" or "try"-with-resources construction
            this:service.reentrancy = previousReentrancy;
            this:service.registerCriticalSection(previousCriticalSection);
            }
        }
    }
