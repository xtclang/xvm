/**
 * A service is a class that represents the possibility for asynchronous and/or concurrent
 * execution.
 *
 * A service is a programming abstraction for an independent _von Neumann machine_. While a service
 * is _not_ a thread in the traditional programming sense, a service does represent many of the
 * conceptual elements of a thread of execution. In its simplest form, a service could be imagined
 * as a combination of a work queue and a thread, in which any call from outside of the context of
 * the service (i.e. any call into the service from any other conceptual thread of execution) is
 * converted into a work item placed into the work queue and an associated future result provided to
 * the caller as a return value.
 *
 * As a conceptual thread of execution, a service incorporates a number of important aspects and
 * responsibilities:
 *
 * * A service represents the ownership of some amount of dynamic memory. Allocations that occur
 *   within the context of a service are made from that dynamic memory, and those allocations are
 *   managed by the service. As a consequence of this, a service is responsible for the garbage
 *   collection (GC) of the unreachable objects within that dynamic memory. As a benefit, a service
 *   provides real-time information related to its memory foot-print.
 * * A service has a natural surface boundary. Calls originating within another service must enter
 *   this service boundary, and calls from this service to another service must exit this service
 *   boundary. Only two specific types of objects can permeate the boundary: Immutable objects, and
 *   services. What this means in practice is that all parameters to an invocation of a service
 *   method, and all return values from the same, must either be service references or references to
 *   immutable objects.
 * * Functionality from the service can be passed through the boundary _as a service_. In other
 *   words, when a function is passed out through the service boundary, it is automatically
 *   transformed into a proxy representation of that functionality within this service context.
 *   Similarly, when the proxy is invoked, the invocation is treated as if it were an invocation
 *   against the service itself. Lastly, if the proxy is passed back _as a parameter_ to the service
 *   from whence it originated, the proxy is automatically transformed into a reference to the
 *   original function (i.e. the proxy is _unwrapped_). It is also possible that a function is also
 *   an immutable (for example, if all of its captures are immutable), in which case the function
 *   _may_ be passed as an immutable and not as a proxy back to the function in the calling service.
 * * Immutable objects passed through the surface boundary may be transferred either as immutable
 *   objects, or under certain circumstances they may be passed by proxy. If passed as an immutable
 *   object, and if the object is located within the memory managed by the calling service, then a
 *   copy of the object may be created either in the memory managed by the destination service, or
 *   in a memory area dedicated solely to immutable objects and shared by all of the services within
 *   the current {@see Container}. Immutable objects passed among services within the same container
 *   are expected to be passed as immutable objects, while immutable objects passing through a
 *   container boundary may be converted to proxies in order to preserve the seal of the container.
 * * The decisions as to the degree of asynchronicity and/or concurrency for the service are managed
 *   entirely by the runtime. The rationale is that knowledge of available hardware and software
 *   threads will only be known by the runtime, and the actual runtime usage of an particular
 *   service will indicate how that service can be most efficiently executed, particularly in
 *   environments in which the resources are being explicitly managed (and perhaps constrained).
 * * The service has an independent life-cycle. A service can be explicitly shut down (cleanly) or
 *   killed (without giving the service a chance to cleanly shut down) in order to terminate its
 *   life cycle and release any resources that it holds. As a result of all of the service's
 *   resources being owned by the service itself, a service can be shut down independently of all
 *   other services, and its resources can easily be reclaimed; however, since it is likely that
 *   other services still hold proxy references to the service, subsequent invocation through those
 *   proxies will result in an exception.
 * * The runtime itself may provide events to the service, enqueuing them so that if the service is
 *   running, the event does not interrupt the execution. These events will be automatically
 *   processed by a service when it is not busy processing, such as when the current service
 *   invocation returns, calls {@link yield}, or even potentially when another service is invoked by
 *   this service. Runtime events may be processed even if the {@link reentrancy} setting is
 *   {@link Reentrancy.Forbidden}. Runtime events include only:
 * * * Timeout notification for the currently executing service invocation, although the runtime
 *     may temporarily suppress these notifications within a {@link CriticalSection}.
 * * * `@Soft` and `@Weak` reference-cleared notifications.
 */
interface Service
    {
    /**
     * A low-level control interface for a Service.
     */
    static interface ServiceControl
            extends ServiceStats
        {
        /**
         * Request the service to look for objects that are no longer used and reclaim their memory.
         *
         * This method can be invoked from either inside or outside of the service.
         */
        void gc();

        /**
         * Attempt to terminate the Service gracefully by asking it to shut down itself.
         * A client that has a reference to the Service may shut it down, or the service
         * can shut itself down, or the service can be shut down by the runtime when the
         * service is no longer reachable.
         *
         * This method can be invoked from either inside or outside of the service.
         */
        void shutdown();

        /**
         * Forcibly terminate the Service without giving it a chance to shut down gracefully.
         *
         * This method can be invoked from either inside or outside of the service.
         */
        void kill();
        }

    /**
     * A service exposes its status through a status indicator:
     *
     * * Idle indicates that the service is ready to accept any request and all previous requests
     *   have been processed;
     * * IdleWaiting indicates that the service has sent a request to another service and is waiting
     *   (asynchronously) for a response at which point it will resume the execution;
     * * Busy indicates that the service is processing a request;
     * * BusyWaiting indicates that the service has sent a request to another service and the calling
     *   fiber is now waiting synchronously (blocked) for a response;
     * * ShuttingDown indicates that the service has received a shutdown request;
     * * Terminated indicates that the service terminated as a result of either a shutdown or kill
     *   request.
     */
    enum ServiceStatus {Idle, IdleWaiting, Busy, BusyWaiting, ShuttingDown, Terminated}

    /**
     * The various run-time statistics for a Service, with an ability to produce a snap-shot.
     */
    static interface ServiceStats
        {
        /**
         * Determine if the service is still running.
         */
        @RO ServiceStatus statusIndicator;

        /**
         * The wall-clock uptime for the service.
         */
        @RO Duration upTime;

        /**
         * The amount of time that this service has consumed the CPU.
         *
         * In some cases, a service may not track CPU time if the runtime calculates that the cost of
         * tracking the CPU time is too significant to accept in relation to the service's actual CPU
         * time.
         */
        @RO Duration cpuTime;

        /**
         * Determine if there is currently any _visible_ contention for the service. A service is
         * considered to be contended if it is running and if any other requests are pending for the
         * service.
         *
         * The use of the term _visible_ is intended to convey the scenario in which minor contention on
         * the service may exist for such a minuscule period of time that the cost of making the
         * contention visible exceeds the cost of the contention itself, in which case such contention
         * may not be visible.
         */
        @RO Boolean contended;

        /**
         * If the service maintains a backlog to manage pending requests, determine the depth of that
         * backlog (i.e. the length of the queue).
         *
         * As a result of the potential for different approaches to managing contention on a service,
         * it is possible that a service does not maintain a formal backlog, and thus the value of this
         * property may not correctly reflect the presence and/or amount of contention.
         */
        @RO Int backlogDepth;

        /**
         * This is the memory footprint of the service, including memory that might not be being fully
         * utilized at the moment.
         */
        @RO Int bytesReserved;

        /**
         * This is the amount of memory that the service currently has allocated for stuff.
         */
        @RO Int bytesAllocated;

        /**
         * Create an immutable snapshot of the current statistics.
         */
        ServiceStats snapshotStats()
            {
            return new StatsSnapshot(statusIndicator, upTime, cpuTime, contended, backlogDepth,
                    bytesReserved, bytesAllocated);
            }
        }

    /**
     * A simple, immutable implementation of the [ServiceStats] interface.
     */
    static const StatsSnapshot(ServiceStatus statusIndicator,
                               Duration      upTime,
                               Duration      cpuTime,
                               Boolean       contended,
                               Int           backlogDepth,
                               Int           bytesReserved,
                               Int           bytesAllocated)
            implements ServiceStats
        {
        @Override
        ServiceStats snapshotStats()
            {
            return this;
            }
        }

    /**
     * The TypeSystem of the container that this service is running within.
     *
     * This is almost always the same TypeSystem of the service that created this service, except in
     * the case of the container service itself, which uses the TypeSystem that it forms in its
     * constructor from the set of modules that it is instantiated around.
     *
     * When running code needs to determine the TypeSystem of the current container, it can simply
     * obtain this value:
     *
     *     TypeSystem ts = this:service.typeSystem;
     */
    @RO TypeSystem typeSystem;

    /**
     * The name assigned to the service. If this method is not overridden, the name
     * defaults to the name of the service class. This property is intended as a means to help
     * diagnose faults, and to provide runtime manageability information.
     */
    @RO String serviceName;

    /**
     * A low-level control for a Service.
     */
    @RO ServiceControl serviceControl;

    /**
     * Obtain the named ContextToken using its name.
     *
     * Normally, a developer would access a ContextToken using injection, such as this example which
     * requires the injection of the token named "customerId":
     *
     *     @Inject ContextToken<String> customerId;
     *
     * If the token is not guaranteed to exist, then the injection should be made optional, instead:
     *
     *     @Inject ContextToken<String>? customerId;      // note the Nullable indicator on the type
     */
    ContextToken? findContextToken(String name);

    /**
     * The current CriticalSection for the service, if any.
     */
    @RO CriticalSection? criticalSection;

    /**
     * The current AsyncSection for the service, if any.
     */
    @RO AsyncSection? asyncSection;

    /**
     * Optional re-entrancy settings for a service:
     *
     * * Open: general re-entrancy is permitted, and requests are processed in a FIFO fashion.
     * * Prioritized (default): general re-entrancy is permitted, but priority is given to the
     *   conceptual thread(s) of execution that have already entered the service.
     * * Exclusive: re-entrancy is only permitted for requests originating from the conceptual
     *   thread of execution that has already entered the service (service A invoked service B
     *   invokes service A).
     * * Forbidden: absolutely no re-entrancy is allowed; a request must complete and return before
     *   a new request can begin. This setting is dangerous because of its ability to easily create
     *   deadlock situations, which will result in a Deadlock exception. Note that runtime events
     *   can still be processed (e.g. by calling {@link yield}) even when reentrancy is Forbidden.
     */
    enum Reentrancy {Open, Prioritized, Exclusive, Forbidden}

    /**
     * The re-entrancy setting for this service.
     *
     * This method is intended primarily to be used from within the service, so that the running
     * code can control the conditions on which it can be arbitrarily interleaved with other threads
     * of execution in the event that an opportunity arises to process requests from the service's
     * backlog.
     *
     * An attempt to set this from outside of the service when the service is processing will likely
     * result in an exception for the caller.
     */
    Reentrancy reentrancy = Exclusive;

    /**
     * The Timeout that was used when the service was invoked, if any. This is the timeout that this
     * service is subject to.
     *
     * The default value for the _incomingTimeout_ is determined based on the remaining timeout
     * value of the calling execution thread (a.k.a. fiber) and could be changed via the
     * {@link #registerTimeout} method.
     */
    @RO Timeout? incomingTimeout;

    /**
     * The current Timeout that will be used by the service when it invokes other services.
     *
     * By default, this is the same as the incoming Timeout, but can be overridden by registering
     * a new Timeout via the {@link #registerTimeout} method.
     */
    @RO Timeout? timeout;

    /**
     * Allow the runtime to process pending runtime notifications for this service, or other service
     * requests in the service's backlog -- if the setting of {@link reentrancy} allows it.
     *
     * A caller should generally *not* yield in a loop or for an extended period of time in order to
     * wait for some event to occur; instead, the caller should use a {@link FutureVar} to request
     * a continuation on completion of a call to another service, or a {@link Timer} to request a
     * continuation at some specified later time.
     *
     * Limitations:
     * * `yield` has no effect on the backlog if reentrancy is set to Forbidden;
     * * `yield` has no effect if it is invoked from outside of the service.
     */
    void yield();

    /**
     * Add a function-to-call to the service's backlog.
     *
     * This method is intended primarily to be used from within the service, so that the running
     * code can defer some separate unit of work until the existing backlog is processed. With
     * respect to calls from outside of the service, all such calls are treated similarly _as if_
     * they were queued in the backlog via this method.
     *
     * Note, that the `doLater` function has access to the service instance via the `this:service`
     * reserved variable.
     *
     * Exceptions raised by the `doLater` function are ignored and lost by the runtime.
     */
    void callLater(function void doLater());

    /**
     * Register a ContextToken, replacing any previously registered ContextToken with the same name.
     * Until the ContextToken is closed, or until the context for that name is erased, the
     * ContextToken will be available (by its name) from any point within this service. Furthermore,
     * any calls from this service to another service will have the effect of automatically
     * registering the same ContextToken with that service for the duration of the call.
     */
    void registerContextToken(ContextToken? token);

    /**
     * Register a Timeout for the service, replacing any previously registered Timeout.
     *
     * If the call is made from within this service, then it only affects the {@link #timeout}
     * of the current execution thread (a.k.a. fiber). Otherwise, the timeout of the service
     * itself will be changed.
     */
    void registerTimeout(Timeout? timeout);

    /**
     * Register a CriticalSection for the service, replacing any previously registered
     * CriticalSection.
     *
     * Calling this method from the outside of the service is not allowed.
     */
    void registerCriticalSection(CriticalSection? criticalSection);

    /**
     * Register a function to invoke when the service is shutting down. This notification is not
     * invoked if the service is killed.
     *
     * Exceptions raised by the `notify` function are ignored and lost by the runtime.
     */
    void registerShuttingDownNotification(function void notify());

    /**
     * Register an AsyncSection to process unhandled exceptions. An unhandled exception can occur
     * when an unguarded asynchronous call originates from within this service, and the result
     * is not explicitly captured using a `@Future` reference. An exception resulting from
     * such a call will trigger an invocation of the `notify` function of the AsyncSection, passing
     * the exception as the argument.
     *
     * When this method is called within this service, it will block the current execution thread
     * until all unguarded asynchronous calls issued while the previously registered AsyncSection
     * was active have completed or timed out.
     *
     * Calling this method from the outside of the service is not allowed.
     */
    void registerAsyncSection(AsyncSection? newAsyncSection);

    /**
     * Register a function to invoke when an unhandled exception occurs. An unhandled exception can
     * occur when an unguarded asynchronous call originates from within this service, and the result
     * is not explicitly captured using a `@Future` or explicitly registered AsyncSection.
     * An exception resulting from such a call will trigger an invocation of the specified function,
     * on a new execution thread passing the exception as the argument. No ordering guarantees of
     * any kind are provided.
     *
     * This notification is provided primarily for logging or debugging purposes.
     *
     * Exceptions raised by the notification function are ignored and lost by the runtime.
     */
    void registerUnhandledExceptionNotification(function void notify(Exception));


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    String toString()
        {
        return this:service.toString();
        }

    @Override
    immutable Service makeImmutable()
        {
        // services are, by their nature, mutable; it is illegal to attempt to make a service into
        // an immutable object
        throw new UnsupportedOperation();
        }
    }
