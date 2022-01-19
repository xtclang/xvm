/**
 * A SynchronizedSection is used to constrain concurrency in a service for a scope of processing.
 * By default, a SynchronizedSection has the same effect as the [@Synchronized](Synchronized)
 * annotation, and optionally, it can be completely constrain concurrency.
 *
 * The SynchronizedSection is stored on the current service and exposed as the property {@link
 * Service.synchronizedSection}. When a new SynchronizedSection is created, it automatically
 * registers itself with the current service using the {@link Service.registerSynchronizedSection}
 * method. Employing either a `using` or `try`-with-resources block will automatically
 * unregister the SynchronizedSection at the conclusion of the block, restoring the previous
 * SynchronizedSection that it replaced (if any).
 *
 * SynchronizedSections _nest_. When a new SynchronizedSection is created, it configures itself to
 * allow no additional concurrency than that which is already in place for the service. This allows
 * the developer to create a new SynchronizedSection without concern that it is weakening an
 * existing SynchronizedSection. In the following example, two different SynchronizedSections are
 * used, each potentially escalating the level of synchronicity:
 *
 *   // lock down concurrency such that other fibers in this service will only be permitted to
 *   // execute concurrent-safe code
 *   using (new SynchronizedSection())
 *       {
 *       prepare();
 *
 *       // lock down concurrent reentrancy completely; no other fiber will be permitted to execute
 *       using (new SynchronizedSection(critical=True))
 *           {
 *           commit();
 *           }
 *       }
 *
 * @see Service.Synchronicity
 */
const SynchronizedSection
        implements Closeable
    {
    @Synchronized
    construct(Boolean critical = False)
        {
        // store off the previous SynchronizedSection; it will be replaced by this SynchronizedSection, and
        // restored when this SynchronizedSection is closed
        previousSynchronizedSection = this:service.synchronizedSection;

        // store off the previous "critical" value; it will be replaced by this SynchronizedSection,
        // and restored when this SynchronizedSection is closed
        Boolean previousCritical = previousSynchronizedSection?.critical : False;

        // calculate the reentrancy for the synchronized section
        this.critical = critical | previousCritical;
        }
    finally
        {
        this:service.registerSynchronizedSection(this);
        }

    /**
     * The SynchronizedSection that this SynchronizedSection replaced, if any.
     */
    SynchronizedSection? previousSynchronizedSection;

    /**
     * True iff all other fibers are blocked from executing by this SynchronizedSection.
     */
    Boolean critical;

    /**
     * Determine whether this SynchronizedSection is the active SynchronizedSection for the current service.
     */
    Boolean active.get()
        {
        return this:service.synchronizedSection == this;
        }

    /**
     * Determine whether this SynchronizedSection is registered with the current service, regardless of
     * whether it is the currently-active SynchronizedSection.
     */
    Boolean registered.get()
        {
        SynchronizedSection? cs = this:service.synchronizedSection;
        while (cs != Null)
            {
            if (this == cs)
                {
                return True;
                }

            cs = cs.previousSynchronizedSection;
            }

        return False;
        }

    /**
     * Close the SynchronizedSection. This method is invoked automatically by the `using` or
     * `try` with-resources keywords.
     */
    @Override
    void close(Exception? cause = Null)
        {
        if (registered)
            {
            // the reason that the SynchronizedSection checks whether it is registered instead of
            // if it is active is that it is possible that a downstream SynchronizedSection was not
            // properly closed, e.g. by failing to use the "using" or "try"-with-resources syntax
            this:service.registerSynchronizedSection(previousSynchronizedSection);
            }
        }
    }
