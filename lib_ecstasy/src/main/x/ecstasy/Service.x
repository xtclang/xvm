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
 *   invocation returns, or even potentially when another service is invoked by this service.
 *   Runtime events include only:
 * * * Future completion events, for previous asynchronous invocations initiated by this service.
 * * * Timeout notification for the currently executing service invocation, although the runtime
 *     may temporarily suppress these notifications within a [SynchronizedSection].
 * * * `@Soft` and `@Weak` reference-cleared notifications.
 */
interface Service {

    /**
     * It is only possible to pass an immutable object or a service proxy across a service boundary.
     *
     * `Passable` objects are one of the following:
     *
     * 1. Any object that `.is(immutable)`, including `const`, `enum`, `package`, and `module`
     *    classes, and any non-service object that is immutable as the result of a call to
     *    [Object.makeImmutable()];
     * 2. Any object of a `service` class; for these objects, `.is(service)` will be `True`;
     * 3. Any _virtual child_ of a `service` object; for these objects, `.is(service)` will also be
     *    `True`.
     *
     * Furthermore, when a service method is declared to take or return an interface type, a value
     * of that interface type that is not `Passable` will automatically be proxied (as if it were a
     * service) as that interface type. Unlike service objects, which answer `.is(service)` with
     * `True` from both sides of the service boundary, a proxied interface only answers
     * `.is(service)` with `True` for the proxy itself, and not for the proxied non-service object
     * within the service.
     */
    typedef (immutable | service) as Passable;

    /**
     * In addition to being able to pass an immutable object or a service proxy across a service
     * boundary, Ecstasy allows `@AutoFreezable` objects to be passed, and they are automatically
     * frozen (made immutable by the [Freezable.freeze()] method) before being passed.
     *
     * Note that all [AutoFreezable] objects are also [Freezable].
     */
    typedef (immutable | service | AutoFreezable) as AutoPassable;

    /**
     * `Shareable` is a type that represents an object that is intended to be passed across a
     * service boundary -- i.e. to be shared among services. It is only possible to pass a
     * [Passable] (an immutable object or a service proxy) across a service boundary, but
     * `Shareable` objects are _intended_ to be used in cases when they may need to be passed. When
     * a `Shareable` object is not [Passable], then it should be frozen using its
     * [Freezable.freeze()] method, resulting in an immutable object, which is [Passable].
     *
     * Note that the inclusion of [Freezable] objects also includes all objects that are
     * [AutoFreezable].
     */
    typedef (immutable | service | Freezable) as Shareable;

    /**
     * Transform the provided object if necessary to make it [Passable]. If the object is already
     * [Passable], it is returned unchanged. Otherwise, if the object is [Freezable], it is frozen
     * and the result (which may be a new object) is returned.
     *
     * @param object  the object that needs to be [Passable]
     *
     * @return `True` iff the object was [Passable], or was able to be made [Passable]
     * @return (conditional) a [Passable] object
     */
    static <Any> conditional (Passable + Any) passable(Any object) {
        if (object.is(Passable)) {
            return True, object;
        }

        if (object.is(Freezable)) {
            return True, object.freeze();
        }

        if (object.is(Array)) {
            try {
                return True, object.toArray(Constant).as(immutable + Any);
            } catch (Exception e) {}
        }

        return False;
    }

    /**
     * Transform the provided object if necessary to make it [Passable]. If the object is already
     * [Passable], it is returned unchanged. Otherwise, if the object is [Freezable], it is frozen
     * and the result (which may be a new object) is returned. Otherwise, a service proxy of the
     * specified pure type or interface type is created and returned.
     *
     * @param object     the object that needs to be [Passable]
     * @param interface  the pure type or interface type that is required
     *
     * @return a [Passable] object
     *
     * @throws Exception iff the specified object is not passable and cannot be made so with the
     *         provided interface
     */
    static <Interface> (Passable + Interface) passableAs(Interface object, Type<Interface> interface) {
        if (object.is(Passable)) {
            return object;
        }

        // TODO GG consider a helper such as: if (interface.isInterfaceType()) ...
        try {
            return &object.proxyAs(interface);
        } catch (Exception e) {}

        if (object.is(Freezable)) {
            return object.freeze();
        }

        if (object.is(Array)) {
            try {
                return object.toArray(Constant).as(immutable + Interface);
            } catch (Exception e) {}
        }

        throw new NotShareable("Failed to create a Passable of type {interface} from an object of type {&object.type}");
    }

    /**
     * Represents awareness of whether an object needs to support method calls across a service
     * boundary.
     *
     * Some objects must be service-aware in order to provide specialized behavior when the object
     * may be invoked across a service boundary. Specifically, parameters-to and return-values-from
     * methods across a service boundary must be must be [AutoPassable] -- either service references
     * or immutable objects (or objects that are marked as being automatically convertible to an
     * immutable object. When methods must behave differently as the result of sitting on the far
     * side of a service boundary, the object should implement this interface in a way that
     * correctly identifies when it is potentially being called across a service boundary.
     * Additionally, using that information, affected method implementations should then ensure that
     * their return values are [AutoPassable], unless the method return types are interface types,
     * which are auto-proxied.
     */
    static interface Aware {
        /**
         * Determine if this object is potentially being used as a service.
         *
         * By default, a service-aware object assumes that it is being used as a service if it is a
         * service, which includes virtual child objects of a service.
         *
         * @return `True` iff calls to this object may be originating from another service
         */
        Boolean fromService() = this.is(service);
    }

    // ----- service internals ---------------------------------------------------------------------

    /**
     * A low-level control interface for a Service.
     */
    static interface ServiceControl
            extends ServiceStats {
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
    static interface ServiceStats {
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
        ServiceStats snapshotStats() {
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
            implements ServiceStats {

        @Override
        ServiceStats snapshotStats() {
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
     * (i.e. reentrancy-safe for new fibers), and no SynchronizedSection has been registered.
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
     *      2) the property's parent class/property/method is concurrent-safe (or if the parent is
     *         a class, then the corresponding runtime object instance, if one exists, must also be
     *         concurrent-safe)
     *
     * * A method is considered _concurrent-safe_ iff all of the following hold true:
     *   1) the method is not explicitly unsafe
     *   2) any of:
     *      1) the method is explicitly safe
     *      2) the method is static (it is a function)
     *      3) the method's parent class/property/method is concurrent-safe (or if the parent is a
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

    /**
     * The Timeout that was used when the current fiber on this service was created (i.e. when the
     * service was invoked).
     *
     * The default value for the _incomingTimeout_ is determined based on the remaining timeout of
     * the calling fiber (i.e. the [timeout] property of the calling service).
     *
     * Accessing this property from the outside of the service is not allowed.
     */
    @RO Timeout? incomingTimeout;

    /**
     * The Timeout that both applies to this service' execution, and is used by this service when it
     * invokes other services.
     *
     * If no timeout is currently registered on this service, then the [incomingTimeout] value will
     * be used.
     *
     * Accessing this property from the outside of the service is not allowed.
     */
    @RO Timeout? timeout;

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
     * Obtain the current [SharedContext.Token] (if any exists) using its [SharedContext].
     *
     * @param ctx  the `SharedContext` object
     *
     * @return the current `Token` for the specified `SharedContext`, otherwise `Null`
     */
    <Value> SharedContext<Value>.Token? findContextToken(SharedContext<Value> ctx);

    /**
     * Register a [SharedContext.Token], replacing any previously registered `Token` for the same
     * [SharedContext]. Until the `Token` is closed, the `Token` will be available (via its
     * `SharedContext`) from any point within this service. Furthermore, any calls from this service
     * to another service will have the effect of automatically registering the same `Token` with
     * that service for the duration of that service call, i.e. for the duration of that fiber.
     *
     * @param token  the `Token` to register
     */
    void registerContextToken(SharedContext.Token token);

    /*
     * Unregister the [SharedContext.Token] from its [SharedContext].
     *
     * @param token  the `Token` to unregister
     */
    void unregisterContextToken(SharedContext.Token token);

    /**
     * Register a Timeout for the service, replacing any previously registered Timeout.
     *
     * Calling this method from the outside of the service is not allowed.
     */
    void registerTimeout(Timeout? timeout);

    /**
     * Register a SynchronizedSection for the service, replacing any previously registered
     * SynchronizedSection.
     *
     * Calling this method from the outside of the service is not allowed.
     */
    @Synchronized
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
    String toString() {
        return serviceName;
    }

    @Override
    immutable Service makeImmutable() {
        // services are, by their nature, mutable; it is illegal to attempt to make a service into
        // an immutable object
        throw new Unsupported();
    }
}