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
 *   that service boundary, and calls from a service to another service must exit that service
 *   boundary. Only two specific types of objects can permeate the boundary: Immutable objects and
 *   services. What this means in practice is that all parameters to an invocation of a service
 *   method, and all return values from the same, must either be service references or references to
 *   immutable objects.
 * * Functionality from the service can be passed through the boundary _as a service_. In other
 *   words, when a function is passed out through the service boundary, it is automatically
 *   transformed into a proxy representation of that functionality within this service context.
 *   Similarly, when the proxy is invoked, the invocation is treated as if it were an invocation
 *   against the service itself. Lastly, if the proxy is passed back _as a parameter_ to the service
 *   from whence it originated, the proxy is automatically transformed into a reference to the
 *   original function (i.e. the proxy is _unwrapped_).
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
 */
interface Service
    {
    /**
     * Request a copy of the service proxy that has a timeout.
     *
     * Note that the actual timeout used by invocations via the proxy will be less than the
     * specified timeout if there is currently a timeout for this service and this service's
     * remaining timeout is less than the requested timeout.
     *
     * This method must be invoked via a proxy, and *not* from _this_ service.
     */
    Service withTimeout(Duration timeout)
        {
        assert this != this:service;
        return super(timeout);
        }

    /**
     * Request a copy of the service proxy that has a timeout that is completely independent of any
     * pre-existing timeout.
     *
     * This allows a call to be made to the other service that will be bound neither by the timeout
     * previously configured for that service, nor bound by the remaining timeout that exists for
     * this service. Specifically, it allows a long-running asynchronous process to be begun that
     * may necessarily exceed the time-out that is currently being enforced.
     *
     * This method must be invoked via a proxy, and *not* from _this_ service.
     */
    Service withIndependentTimeout(Duration timeout)
        {
        assert this != this:service;
        return super(timeout);
        }

    /**
     * The _remaining_ time that the service has for its current execution based on the timeout (if
     * any) used to invoke the service.
     */
    @ro @atomic Duration remainingTime;

    /**
     * The wall-clock uptime for the service.
     */
    @ro @atomic Duration upTime;

    /**
     * The amount of time that this service has consumed the CPU.
     *
     * In some cases, a service may not track CPU time if the runtime calculates that the cost of
     * tracking the CPU time is too significant to accept in relation to the service's actual CPU
     * time.
     */
    @ro @atomic Duration cpuTime;

    /**
     * A service exposes its status through a status indicator:
     *
     * * Idle indicates that the service is ready to accept a request;
     * * Busy indicates that the service is processing a request;
     * * ShuttingDown indicates that the service has received a shutdown request;
     * * Terminated indicates that the service terminated as a result of either a shutdown or kill
     *   request.
     */
    enum StatusIndicator {Idle, Busy, ShuttingDown, Terminated};

    /**
     * Determine if the service is still running.
     */
    @ro @atomic StatusIndicator statusIndicator;

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
    @ro @atomic Boolean contended;

    /**
     * If the service maintains a backlog to manage pending requests, determine the depth of that
     * backlog (i.e. the length of the queue).
     *
     * As a result of the potential for different approaches to managing contention on a service,
     * it is possible that a service does not maintain a formal backlog, and thus the value of this
     * property may not correctly reflect the presence and/or amount of contention.
     */
    @ro @atomic Int backlogDepth;

    /**
     * A self-notification that occurs when the service is started.
     *
     * This method must not be invoked from another service (i.e. via a proxy).
     */
    protected Void onStarted()
        {
        assert this == this:service;
        }

    /**
     * A self-notification that occurs when the service is being shut down cleanly.
     *
     * This method must not be invoked from another service (i.e. via a proxy).
     */
    protected Void onShuttingDown()
        {
        assert this == this:service;
        }

    /**
     * A self-notification that occurs when the service encounters an exception that was not caught
     * by user code. This notification is provided primarily for logging or debugging purposes; the
     * communication of the exception back through the proxy is managed independently.
     *
     * This method must not be invoked from another service (i.e. via a proxy).
     */
    protected Void onUnhandledException(Exception e)
        {
        assert this == this:service;
        }

    /**
     * Optional re-entrancy settings for a service:
     *
     * * Prioritized (default): general re-entrancy is permitted, but priority is given to the
     *   conceptual thread(s) of execution that have already entered the service.
     * * Open: general re-entrancy is permitted, and requests are processed in a FIFO fashion.
     * * Exclusive: re-entrancy is only permitted for requests originating from the conceptual
     *   thread of execution that has already entered the service (service A invoked service B
     *   invokes service A).
     * * Forbidden: absolutely no re-entrancy is allowed; a request must complete and return before
     *   a new request can begin. This setting is dangerous because of its ability to easily create
     *   deadlock situations, which will result in a DeadlockException.
     */
    enum Reentrancy {Prioritized, Open, Exclusive, Forbidden};

    /**
     * The re-entrancy setting for this service.
     *
     * This property can only be modified from within this service (i.e. _not_ via a proxy).
     */
    @atomic Reentrancy reentrant
        {
        Void set(Reentrancy reentrant)
            {
            assert this == this:service;
            super(reentrant);
            }
        }

    /**
     * Allow execution of a pending item in the service's backlog, if any.
     *
     * A caller should generally not yield in a loop or for an extended period of time in order to
     * wait for some event to occur; instead, the caller should use a {@link FutureRef} to request
     * a continuation on completion of a call to another service, or a {@link Timer} to request a
     * continuation at a later time.
     *
     * If reentrant is set to Forbidden, this has no effect.
     *
     * This method must not be called from another service (i.e. via a proxy).
     */
    Void yield()
        {
        assert this == this:service;
        super();
        }

    /**
     * Add a function-to-invoke to the service's backlog.
     *
     * This method must not be called from another service (i.e. via a proxy).
     */
    Void invokeLater(function Void () doLater)
        {
        assert this == this:service;
        super(doLater);
        }

    /**
     * This is the memory footprint of the service, including memory that might not be being fully
     * utilized at the moment.
     */
    @ro @atomic Int bytesReserved;

    /**
     * This is the amount of memory that the service currently has allocated for stuff.
     */
    @ro @atomic Int bytesAllocated;

    /**
     * Request the service to look for objects that are no longer used and reclaim their memory.
     */
    Void gc();

    /**
     * When the runtime needs to report information to the service, it enqueues an event that
     * represents the information to communicate. The reason that the information must be enqueued
     * as an event is that the service may be busy processing when the event actually occurs, and
     * there is no mechanism for interrupting that processing in order to deliver the event. These
     * events will be automatically processed by a service when it is not busy processing, or the
     * events can be processed on demand by invoking the {@link dispatchRuntimeEvents} method from
     * either from within or from outside of the service.
     *
     * Runtime events include:
     * * {@code @soft} and {@code @weak} reference-cleared notifications
     */
    @ro Boolean pendingRuntimeEvents;

    /**
     * Process any pending notifications from the runtime.
     *
     * Runtime events include:
     * * {@code @soft} and {@code @weak} reference-cleared notifications
     */
    Void dispatchRuntimeEvents();

    /**
     * Attempt to terminate the Service gracefully by asking it to shut down itself.
     * A client that has a reference to the Service may shut it down, or the service
     * can shut itself down, or the service can be shut down by the runtime when the
     * service is no longer reachable.
     *
     * This method can be invoked from within the service, or via a proxy to the service.
     */
    Void shutdown();

    /**
     * Forcibly terminate the Service without giving it a chance to shut down gracefully.
     *
     * This method can be invoked from within the service, or via a proxy to the service.
     */
    Void kill();
    }
