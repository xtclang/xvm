/**
 * An AsyncSection is used to control the behavior of asynchronous "fire and forget" calls issues
 * from within current execution thread (a.k.a. fiber).
 *
 * The AsyncSection is stored on the current service and exposed as {@link Service.asyncSection}.
 * When a new AsyncSection is created, it automatically registers itself with the current service
 * using the {@link Service.registerAsyncSection} method, storing off the previously registered
 * AsyncSection.
 *
 * Employing either a `using` or `try`-with-resources block will automatically
 * re-register whatever previous AsyncSection it replaced (if any) at the conclusion of the block,
 * potentially blocking until all the unguarded asynchronous service invocations that occurred
 * within the block have been completed or timed-out.
 *
 * The following example illustrates usage of try-with-resources block to manage asynchronous
 * exceptions that occur during execution:
 *
 *   List<Exception> listUnguarded = new List();
 *
 *   using (new AsyncSection(listUnguarded.add))
 *       {
 *       svc1.asyncCall1^();
 *       svc2.asyncCall2^();
 *       svc3.asyncCall3^();
 *       }
 *
 *   // by now all the unguarded async calls must have completed
 *   if (!listUnguarded.empty)
 *       {
 *       console.out("some operations failed");
 *       }
 */
const AsyncSection
        implements Closeable
    {
    construct(function void (Exception) notify)
        {
        // store off the previous AsyncSection; it will be restored when
        // this AsyncSection is closed
        previousAsyncSection = this:service.asyncSection;

        this.notify = notify;
        }
    finally
        {
        this:service.registerAsyncSection(this);
        }

    /**
     * The notification function. It's provided primarily for logging or debugging purposes.
     *
     * Exceptions raised by the notification function are ignored and lost by the runtime.
     */
    function void (Exception) notify;

    /**
     * The `AsyncSection` that this AsyncSection replaced, if any.
     */
    AsyncSection? previousAsyncSection;

    /**
     * Determine whether this AsyncSection is the active AsyncSection for the current service.
     */
    Boolean active.get()
        {
        return this:service.asyncSection == this;
        }

    /**
     * Determine whether this AsyncSection is registered with the current service, regardless of
     * whether it is the currently active AsyncSection.
     */
    Boolean registered.get()
        {
        AsyncSection? registered = this:service.asyncSection;
        while (registered != Null)
            {
            if (this == registered)
                {
                return True;
                }

            registered = registered.previousAsyncSection;
            }

        return False;
        }

    /**
     * Close the AsyncSection. This method is invoked automatically by the `using` or
     * `try` with-resources keywords.
     */
    @Override
    void close(Exception? cause = Null)
        {
        if (registered)
            {
            // the reason that the AsyncSection checks whether it is registered instead of if it is
            // active is that it is possible that a downstream AsyncSection was not properly closed,
            // e.g. by failing to use a "using" or "try"-with-resources construct
            this:service.registerAsyncSection(previousAsyncSection);
            }
        }
    }