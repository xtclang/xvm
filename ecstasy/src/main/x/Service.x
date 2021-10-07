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
 *   invocation returns, calls [yield], or even potentially when another service is invoked by
 *   this service. Runtime events may be processed even if [reentrant] evaluates to `False`.
 *   Runtime events include only:
 * * * Future completion events, for previous asynchronous invocations initiated by this service.
 * * * Timeout notification for the currently executing service invocation, although the runtime
 *     may temporarily suppress these notifications within a [CriticalSection].
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

// TODO GG remove
    /**
     * The current CriticalSection for the service, if any.
     */
    @RO CriticalSection? criticalSection;

    /**
     * The current SynchronizedSection for the service, if any.
     */
    @RO SynchronizedSection? synchronizedSection;

    /**
     * The current AsyncSection for the service, if any.
     */
    @RO AsyncSection? asyncSection;

    /**
     * Indicates the potential concurrency of execution for this service, at this moment.
     * Specifically, this value indicates whether the service is able to execute another fiber
     * concurrently, were the service to potentially yield execution as the result of invoking
     * any other service at this point.
     *
     * This property evaluates to True iff each execution frame for the fiber is concurrent-safe
     * (i.e. reentrancy-safe for new fibers), and no CriticalSection has been registered.
     *
     * Rules for determining the concurrent-safeness of an execution frame:
     *
     * * A class/property/method is said to be _explicitly unsafe_ iff the class/property/method is
     *   `@Synchronized`.
     *
     * * A class/property/method is said to be _explicitly safe_ iff the class/property/method is
     *   `@Concurrent`.
     *
     * * A class is considered _concurrent-safe_ iff (1) the class is not explicitly
     *   unsafe, **and** (2) any of the following hold true:
     *   1) the class is explicitly safe;
     *   2) the class is of an immutable form (Module, Package, Const, Enum/Enum-Value);
     *
     * * An object is considered _concurrent-safe_ iff any of the following hold true:
     *   1) the object's class is concurrent-safe
     *   2) the object's class is not explicitly unsafe, **and** the object is immutable
     *
     * * A property is considered _concurrent-safe_ iff all of the following hold true:
     *   1) the property is not explicitly unsafe
     *   2) any of:
     *      1) the property is explicitly safe
     *      2) the property's parent class/property/method is concurrent-safe (and if the parent is
     *         a class, then the corresponding runtime object instance, if one exists, must also be
     *         concurrent-safe)
     *
     * * A method is considered _concurrent-safe_ iff all of the following hold true:
     *   1) the method is not explicitly unsafe
     *   2) any of:
     *      1) the method is explicitly safe
     *      2) the method is static (it is a function)
     *      3) the method's parent class/property/method is concurrent-safe (and if the parent is a
     *         class, then the corresponding runtime object instance, if one exists, must also be
     *         concurrent-safe)
     *
     * * A frame **is** _concurrent-safe_, iff all of the following hold true:
     *   1) The frame is for a method that **is** concurrent-safe
     *   2) The calling frame on this fiber, if there is one, **is** concurrent-safe
     *
     * Note: The value of this property has no meaning outside of the service.
     */
    @RO @Concurrent Synchronicity synchronicity;

    /**
     * A measure of service synchronicity:
     *
     * * Concurrent - **no** fiber within this service has either (a) entered and not exited a
     *   [SynchronizedSection], or (b) invoked a method/function that is not _concurrent-safe_;
     *   the service may schedule other fibers when the current fiber (if any) yields execution by
     *   invoking any other service.
     * * Synchronized - a fiber within this service **has** either (a) entered and not exited a
     *   [SynchronizedSection], or (b) invoked a method/function that is not _concurrent-safe_;
     *   the service may schedule other fibers when the current fiber (if any) yields execution by
     *   invoking any other service, but those scheduled fibers will be permitted to invoke only
     *   _concurrent-safe_ methods, and will be prevented from creating a [SynchronizedSection].
     * * Critical - the current fiber has entered (and not exited) a [SynchronizedSection] with the
     *   `critical` flag specified as True; the service will **not** schedule any other fiber.
     */
    enum Synchronicity {Concurrent, Synchronized, Critical}

    enum Reentrancy {Open, Prioritized, Exclusive, Forbidden}       // TODO GG remove
    Reentrancy reentrancy = Exclusive;                              // TODO GG remove

    /**
     * The Timeout that was used when the service was invoked, if any. This is the timeout that this
     * service is subject to.
     *
     * The default value for the _incomingTimeout_ is determined based on the remaining timeout
     * value of the calling execution thread (a.k.a. fiber) and could be changed via the
     * [registerTimeout] method.
     */
    @RO Timeout? incomingTimeout;

    /**
     * The current Timeout that will be used by the service when it invokes other services.
     *
     * By default, this is the same as the incoming Timeout, but can be overridden by registering
     * a new Timeout via the [registerTimeout] method.
     */
    @RO Timeout? timeout;

// TODO GG remove
    void yield();

    /**
     * Allow the runtime to process pending runtime notifications for this service, including any
     * notifications that pending futures have completed from asynchronous execution.
     *
     * This method will **not** yield execution to any other fiber.
     *
     * Calling this method from the outside of the service is not allowed.
     *
     * @return True iff at least one future has received a completion notification
     */
    Boolean hasFutureArrived();

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
     * If the call is made from within this service, then it only affects the [timeout]
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
     * Register a SynchronizedSection for the service, replacing any previously registered
     * SynchronizedSection.
     *
     * Calling this method from the outside of the service is not allowed.
     */
    void registerSynchronizedSection(SynchronizedSection? synchronizedSection);

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
