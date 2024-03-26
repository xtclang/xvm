/**
 * A Timeout is used to constrain the wall-clock time limit for calls made to other services
 * from this service. Specifically, once a timeout is put in place, all service invocations that
 * originate from this service will carry the timeout, such that those service invocations will
 * need to complete within the remainder of that timeout, or risk a TimedOut exception being raised.
 *
 * The Timeout mechanism is a cooperative mechanism, and is not intended to be used as a strict
 * resource management mechanism. Rather, it is intended for uses in which returning with a bad
 * answer (or an exception) is preferable to not returning at all. Generally, timeouts are useful
 * for user-interactive systems, in which failing to respond within a reasonable period of time is
 * unacceptable, and for systems that need to assume the worst if an external process -- such as a
 * persistent storage system or network communication with a remote system -- takes an unexpectedly
 * long period of time.
 *
 * The timeout is stored on the current service and exposed as [Service.timeout]. When a new
 * timeout is created, it automatically registers itself with the current service using the
 * [Service.registerTimeout] method. Employing either a `using` or `try`-with-resources
 * block will automatically unregister the timeout at the conclusion of the block, causing all
 * of the potentially-asynchronous service invocations that occurred within the block to be infected
 * by the timeout. When the timeout unregisters itself, it re-registers whatever previous timeout it
 * replaced (if any).
 *
 * Timeouts _nest_. When a new timeout is created, it configures itself to use no more time than
 * remains on the current timeout. This allows the developer to create a new timeout without
 * concern that it is violating an existing timeout. In the following example, two different
 * time-outs (1 second and 500 milli-seconds) are used, but if there is less than 1 second remaining
 * on the current timeout for the current service, then the timeouts in the example will be reduced
 * in order to fit within that existing timeout:
 *
 *   // to obtain asynchronous results from other services, use future references for the return
 *   // values from methods on those services
 *   @Future Body body;
 *   @Future Ad   ad1;
 *   @Future Ad   ad2;
 *
 *   // async request for the page body, but don't wait more than 1000ms for it
 *   using (new Timeout(Duration:1S)) {
 *       body = contentSvc.genBody();
 *
 *       // async request for two advertisements, but don't wait more than 500ms for either
 *       using (new Timeout(Duration:.5S)) {
 *           ad1 = adSvc1.selectAd();
 *           ad2 = adSvc2.selectAd();
 *       }
 *   }
 *
 *   // handle time-outs and other exceptions using some default content
 *   ad1  = &ad1.handle(e -> blankAd);
 *   ad2  = &ad2.handle(e -> blankAd);
 *   body = &body.handle(e -> errPage(e));
 *
 *   // an attempt to dereference a future will automatically wait for the future to complete;
 *   // at this point, wait for the three separate results, and use them to render the page, but
 *   // regardless, try not to take more than 1000ms total for all three parts to complete
 *   return renderPage(body, ad1, ad2);
 *
 * If a service needs to begin a long-running task that is independent of the timeout that the
 * service is currently constrained by, construct an _independent_ timeout:
 *
 *   using (new Timeout(Duration:5H, True)) {
 *       new LongRunningReports().begin();
 *   }
 */
const Timeout
        implements Closeable {

    construct(Duration remainingTime, Boolean independent = False) {
        assert remainingTime > Duration:0S;

        // store off the previous timeout; it will be replaced by this timeout, and restored when
        // this timeout is closed
        previousTimeout = this:service.timeout;

        // calculate the duration of this Timeout
        duration = remainingTime;

        Timeout? previousTimeout = this.previousTimeout;
        if (!independent && previousTimeout != Null) {
            // because the timeout is not independent, it must respect the current outgoing timeout
            // that it is replacing
            duration = duration.notGreaterThan(previousTimeout.remainingTime);
        }
        this.independent = independent;
    } finally {
        this:service.registerTimeout(this);
    }

    /**
     * The timer selected by the runtime to manage timeouts.
     */
    @Inject Timer timer;

    /**
     * The `Timeout` that this timeout replaced, if any.
     */
    Timeout? previousTimeout;

    /**
     * True indicates that this timeout is independent of any previous timeout. By using an
     * independent timeout, the new timeout may have a duration greater than the timeout that
     * governs this service.
     */
    Boolean independent;

    /**
     * The duration of this Timeout.
     */
    Duration duration;

    /**
     * Determine the amount of remaining time on this timeout. This value decreases until it
     * reaches zero.
     */
    Duration remainingTime.get() {
        return (duration - timer.elapsed).notLessThan(Duration.None);
    }

    /**
     * Determine whether this timeout has timed out.
     */
    Boolean expired.get() {
        return timer.elapsed > duration;
    }

    /**
     * Determine whether this timeout is the active timeout for the current service.
     */
    Boolean active.get() {
        return this:service.timeout == this;
    }

    /**
     * Determine whether this timeout is registered with the current service, regardless of whether
     * it is the currently-active timeout.
     */
    Boolean registered.get() {
        Timeout? timeout = this:service.timeout;
        while (timeout != Null) {
            if (this == timeout) {
                return True;
            }

            timeout = timeout.previousTimeout;
        }

        return False;
    }

    /**
     * Check to see if the timeout has expired, and if it has, invoke its expiration.
     */
    void checkExpiry() {
        if (expired) {
            onExpiry();
        }
    }

    /**
     * This method is invoked when the timeout determines that it has expired. The default behavior
     * of this method is to throw a TimedOut exception.
     */
    protected void onExpiry() {
        throw new TimedOut(this);
    }

    /**
     * Close the timeout. This method is invoked automatically by the `using` or `try`
     * with-resources keywords.
     */
    @Override
    void close(Exception? cause = Null) {
        if (registered) {
            // the reason that the timeout checks whether it is registered instead of if it is
            // active is that it is possible that a downstream Timeout was not properly closed,
            // e.g. by failing to use a "using" or "try"-with-resources construct
            this:service.registerTimeout(previousTimeout);
        }
    }
}