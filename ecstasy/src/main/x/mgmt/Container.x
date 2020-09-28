import reflect.FileTemplate;
import reflect.ModuleTemplate;

/**
 * A Container is the fundamental domain of execution in Ecstasy, and represents the intersection of
 * a type system that defines the components of execution, with the conceptual machine on which that
 * execution occurs.
 *
 * All Ecstasy execution occurs within a container. To paraphrase Descartes: _laboro, ergo sum_ ("I
 * work, therefore I am"). The fact that Ecstasy code is executing is proof of the existence of the
 * container within which it executes, but generally, that is the only proof that exists from the
 * point of reference of the running code, because the existence of (and a reference to) the
 * container within which the code is executing is undiscoverable from within. The analogy to the
 * physical universe is apt, in that the domain is seemingly boundless from within, yet describable
 * and knowable only in terms of its contents and the behavior thereof.
 *
 * From outside of the container, though, the container is as real and fungible as any other object,
 * and its use is conceptually straight-forward:
 *
 * * A container is created by specifying the module (or modules) that will form its type system;
 *
 * * The modules (along with any dependencies) are loaded (if necessary) and linked to form an
 *   immutable type system for the container;
 *
 * * The container exposes a management and monitoring interface that allows the creator of (or any
 *   object with a reference to) the container to (i) specify constraints for the container, (ii)
 *   initiate execution within the container, (iii) enumerate the nested containers and services
 *   within the container, (iv) obtain run-time statistics for the resource utilization by the
 *   container, (v) persist a snap-shot of the runtime state of the container in a manner that the
 *   container could potentially be re-started from that point, and (vi) control the life cycle of
 *   the container, including pausing, resuming, and killing it;
 *
 * * The contents of the container are naturally visible to and manipulable by the creator of the
 *   container, just as the contents of the container are visible to and manipulable by the code
 *   running inside of the container; in other words, there is no security protection from the
 *   creator of the container, just as there is no security against other code that is loaded as
 *   part of the same container;
 *
 * * Since it is possible to create a new `Container` from Ecstasy code (and indeed, that is
 *   _conceptually_ the only way to create a container), it follows naturally that containers form
 *   a strict hierarchy, with each container nested beneath the container that created it, and with
 *   the containers that it directly creates nested under it; all of the rules and concepts related
 *   to the container are -- by design -- recursively consistent.
 *
 * A `Container`, like any object in Ecstasy, is prevented from being garbage-collected by either
 * its _activity_ or _by the presence of references to it_. As with any [service](Service), a
 * container is considered _active_ if it has any fibers; in the case of a container, this rule
 * means that if any service within the container has a fiber, then the container is considered to
 * be _active_. Similarly, as with any service, all references to a container or objects within the
 * container from outside of the container are _proxies_, and the existence of any such reference
 * would prevent the container from being garbage-collected. If code running within the container
 * registers for call-backs from services that are injected into the container, then those pending
 * potential callbacks would be references into the container, and as such, would prevent automatic
 * garbage-collection of the container; examples include `Timer` alarms and `Socket` connections.
 */
service Container
        delegates Control(control)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy `Container`. As part of the construction of the container, a new
     * [TypeSystem] will be formed for the container, by loading and linking (as necessary) the
     * specified modules, using the provided rules and constraints.
     *
     * @param primaryModule     specifies the module that will act as the primary module for the
     *                          container; any down-stream dependencies of the primary module will
     *                          be calculated and loaded to create the container
     * @param model             specifies the container [model](Model) that defines defaults and
     *                          constraints for the construction of the container; optional,
     *                          defaulting to [Secure]
     * @param repository        the repository to use to load required modules; it is not necessary
     *                          to provide a repository to access the standard Ecstasy library
     *                          modules, as those modules can be obtained via the default injected
     *                          repository; optional, defaulting to the injected repository
     * @param injector          the source of all injections that will occur within the container;
     *                          it is not necessary to provide an injector if using the current
     *                          container's injector is desired, since it can be obtained via the
     *                          default injected injector; if the model is `Secure`, then a heavily
     *                          constrained [ResourceProvider] is created by default
     * @param sharedModules     the already-linked modules that are part of the current container's
     *                          `TypeSystem` that should also be used, as-is, within the new
     *                          container's `TypeSystem`; these modules will transitively close over
     *                          their dependencies and bring those additional modules into the new
     *                          container as well; by default, the `ecstasy.xtclang.org` module is
     *                          automatically shared, and if the model is `Lightweight`, then all of
     *                          the modules from the current `TypeSystem` are automatically shared
     * @param additionalModules if any additional modules are required in the new container, but
     *                          are not already implied via the set of module dependencies that
     *                          emanate from the `primaryModule` or the `sharedModules`, then the
     *                          additional modules specified here (and any of their dependencies)
     *                          will be linked into the new container's type system; the default is
     *                          that no additional modules will be loaded
     * @param namedConditions   the link-time condition names to use, such as "test" or "debug"
     */
    construct(ModuleSpec        primaryModule,
              Model             model             = Secure,
              ModuleRepository? repository        = Null,
              ResourceProvider? injector          = Null,
              Module[]          sharedModules     = [],
              ModuleSpec[]      additionalModules = [],
              String[]          namedConditions   = [])
        {
        // load and link the modules to form a type system
        @Inject Linker linker;
        (TypeSystem innerTypeSystem, Control control) = linker.loadAndLink(
                primaryModule, model, repository, injector, sharedModules, additionalModules, namedConditions);

        // store off the results
        this.model           = model;
        this.innerTypeSystem = innerTypeSystem;
        this.control         = control;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The model that was specified to create this Container.
     */
    public/private Model model;

    /**
     * The `TypeSystem` for services running inside this `Container`.
     *
     * A `Container` is a service, and thus also has a [typeSystem](Service.typeSystem) property,
     * which is the TypeSystem of the `Container` that contains this `Container`, and **not** the
     * TypeSystem which this `Container` defines for services running inside this `Container`.
     */
    public/private TypeSystem innerTypeSystem;

    /**
     * The `Control` for services running inside this `Container`.
     *
     * Note: this delegating property is marked as Atomic, which creates an asynchronous delegating
     *       stub.
     */
    @Atomic private Control control;


    // ----- inner types ---------------------------------------------------------------------------

    /**
     * A means of specifying a module to include in a container, either by its name or by providing
     * the actual module template.
     */
    typedef (ModuleTemplate | String) ModuleSpec;

    /**
     * A Container Model specifies a use case that may imply additional constraints for the
     * container when it is created.
     */
    enum Model
        {
        /**
         * The `Lightweight` container model is used to automatically share all modules from the
         * parent container with the newly created container; it is designed to be used only when
         * the parent container fully trusts the code running inside of the container, and wants the
         * new container to be as tightly integrated with the parent container as possible.
         */
        Lightweight,

        /**
         * The `Secure` container model is used to prevent unintentional visibility from inside a
         * container to anything outside of that container; this is the default container model.
         *
         * The `Secure` model verifies that no shared module into the container contains a `service`
         * object (a mutable domain of information) that is either a singleton or reachable via a
         * singleton.
         */
        Secure,

        /**
         * The `Custom` container model is used to tailor a container's construction to a set of
         * rules and defaults that do not fit well into either of the predefined `Lightweight` or
         * `Secure` models. The use of this model implies that both the secure and the lightweight
         * nature of the container may be forfeited, so it should be used with caution.
         */
        Custom,

        /**
         * The `Debugger` container model is used to enable interactive debugging of an application,
         * exposing the various capabilities commonly associated with debugging such as:
         * stepping through code, setting breakpoints, enabling watches, and so on.
         *
         * The `Debugger` container model loads all non-shared modules with the name condition
         * "`debug`" specified.
         */
        Debugger
        }

    /**
     * The module linker.
     */
    static interface Linker
        {
        /**
         * Load and link the specified modules together to form a type system.
         * Construct an Ecstasy `Container`. As part of the construction of the container, a new
         * [TypeSystem] will be formed for the container, by loading and linking (as necessary) the
         * specified modules, using the provided rules and constraints.
         *
         * @param primaryModule     specifies the module that will act as the primary module for the
         *                          type system; any down-stream dependencies of the primary module
         *                          will be calculated and loaded as necessary
         * @param model             specifies a container [model](Model) that the type system will
         *                          be loaded within
         * @param repository        the repository to use to load any necessary modules
         * @param injector          the [ResourceProvider] to use to inject resources into the type
         *                          system
         * @param sharedModules     the already-linked modules that are part of the current
         *                          container's type system that should also be used, as-is, within
         *                          the new type system
         * @param additionalModules a set of additional modules required in the new type system, but
         *                          not necessarily implied via the module dependencies that
         *                          emanate from the `primaryModule` or the `sharedModules`
         * @param namedConditions   the link-time condition names to use, such as "test" or "debug"
         *
         * @throws an Exception if an error occurs attempting to link the provided modules together
         */
        (TypeSystem typeSystem, Control control) loadAndLink(
                ModuleSpec        primaryModule,
                Model             model             = Secure,
                ModuleRepository? repository        = Null,
                ResourceProvider? injector          = Null,
                Module[]          sharedModules     = [],
                ModuleSpec[]      additionalModules = [],
                String[]          namedConditions   = []);

        /**
         * TODO: remove this temporary method
         *
         * Validate the content of the provided XTC structure and return the name of the primary
         * module.
         *
         * @throws an Exception if the bytes don't represent a valid module
         */
        String validate(Byte[] bytes);

        /**
         * TODO: remove this temporary method (e.g. as a FileTemplate constructor)
         *
         * Create the FileTemplate from its serialized content.
         *
         * @throws an Exception if the bytes don't represent a valid module
         */
        FileTemplate loadFileTemplate(Byte[] bytes);
        }

    /**
     * Various states of the controlled container.
     */
    enum Status
        {
        /**
         * The `Container` has been created, but has not yet experienced any activity, either
         * from invocation or reactivation.
         */
        Initial,

        /**
         * The `Container` has had at least one invocation from outside, or has been reactivated
         * from storage. Also, after a container has been paused, resuming the container will
         * transition it back to this running state.
         */
        Running,

        /**
         * The `Container` was running and has been paused.
         */
        Paused,

        /**
         * The `Container` has been killed.
         */
        Dead
        }

    /**
     * Represents the container control facility.
     */
    static interface Control
            extends Service.ServiceControl
        {
        /**
         * The status of the container.
         */
        @RO Status status;

        /**
         * The `Goal` enumeration defines general classes of optimization that the container may
         * be directed to achieve, in terms of time/space trade-offs.
         */
        enum Goal
            {
            /**
             * The goal is to maximize execution throughput.
             */
            Throughput,

            /**
             * The goal is to minimize wall-clock latency of execution.
             */
            Latency,

            /**
             * The goal is to minimize memory utilization.
             */
            Space,

            /**
             * The "reasonable default" optimization goal is to achieve reasonable trade-offs
             * between time and space utilization with overall efficiency being prioritized over
             * any one measurement.
             */
            Balanced
            }

        /**
         * The optimization goal of the container, which may be utilized by the runtime as a hint to
         * guide its behavior.
         */
        Goal targetOptimization;

        /**
         * The execution priority of the container, which may be utilized by the runtime as a hint
         * to guide its behavior. The execution priority could be used to determine the order of
         * execution among containers when an execution backlog occurs. The value, in the range
         * `[0..1]`, is the _probability_ that this backlogged container would execute instead of
         * another backlogged container, assuming that the other container has a default priority.
         *
         * The value of `0` implies that this container should never run before a container with a
         * higher priority when there is a backlog, and the value of `1` implies that this container
         * should **always** run before a container with a lower priority when there is a backlog.
         * However, this behavior is not guaranteed, because this setting is only intended as a
         * hint.
         *
         * Furthermore, containers are arranged hierarchically, and therefore the actual priority of
         * a given container (which is likely to differ from this setting) implicitly reflects the
         * priority of its parent container, and so on.
         *
         * In a backlogged system, it is often important that some progress be made, even within
         * low-priority containers. To that end, it is suggested that container priority be adjusted
         * only as necessary, and in small increments, until the desired behavior is apparent. The
         * priority setting is an advanced feature, and if it is respected by the runtime, it will
         * have a significant effect on the behavior of a loaded system; when in doubt, do not touch
         * it.
         */
        Dec schedulingPriority;

        /**
         * Specify a suggested maximum number of hardware threads to be consumable by the container;
         * this information may be utilized by the runtime as a hint to guide its behavior. Assuming
         * that this capability is supported by the runtime, this method limits the maximum hardware
         * thread-count that the container will consume. A typical processor has multiple cores,
         * each of which can execute one or more hardware threads via simultaneous multithreading
         * (SMT). With a sufficiently concurrent workload, it is expected that the Ecstasy runtime
         * will be able to fully utilize of all of the available hardware threads. This method can
         * be used to constrain a particular container from concurrently consuming more than the
         * specified number of hardware threads.
         *
         * Hardware varies dramatically, and even within a single CPU, the hardware threads can
         * vary dramatically; for example, with the various "Big/Little" processor architectures,
         * the cores of a processor may dramatically differ from each other, and with SMT-capable
         * cores, even threads running within the same core will vary dramatically in their
         * performance. Furthermore, the Ecstasy runtime may be able to utilize specialized compute
         * facilities, such as a GPU, to optimize specific forms of computation. This method accepts
         * a single `max` argument for a number of threads, yet it is recognized that the potential
         * hardware reality is far more complex; the design choice was to err on the side of
         * simplicity. The maximum hardware threads setting is an advanced feature, and if it is
         * respected by the runtime, it will have a significant effect on the behavior of a loaded
         * system; when in doubt, do not touch it.
         *
         * Ecstasy code never _requires_ more than a single hardware thread. Therefore, the runtime
         * will never encounter a condition that would cause it to exceed the specified maximum.
         *
         * @param max  the maximum number of hardware threads to utilize for this container, with
         *             `max > 0`
         */
        void limitThreads(Int max);

        /**
         * Specify the maximum amount of CPU time usable by the container before the container is
         * automatically paused and the provided event is raised. CPU time is defined as the sum
         * of the durations that each hardware thread spends executing code within this container.
         * Assuming that this capability is supported by the runtime, the CPU time metric will be
         * tracked, and when it exceeds the specified maximum, the container will be paused and the
         * specified event will be raised.
         *
         * The ability to measure CPU time varies dramatically across different processors. It is
         * expected than an implementation will use a low-cost, conservative approach to measuring
         * CPU time, which means that this measure is not guaranteed to be exact, nor is the
         * corresponding pausing of the container and subsequent raising of the event required to
         * be immediate when the limit has been exceeded.
         *
         * @param max             the maximum amount of CPU usage to allow before pausing the
         *                        container and raising the provided event
         * @param maxCpuExceeded  the function to invoke when the max duration is exceeded
         */
        void limitCompute(Duration max, function void() maxCpuExceeded);

        /**
         * Specify the maximum amount of RAM usable by the container before the container is
         * automatically paused and the provided event is raised. Assuming that this capability is
         * supported by the runtime, the RAM usage will be tracked, and when it exceeds the
         * specified maximum, the container will be paused and the specified event will be raised.
         *
         * RAM utilization will vary dramatically in an application, particularly with respect to
         * the lazy and deferred nature of garbage collection.  It is expected than an
         * implementation will use a low-cost, conservative approach to measuring RAM utilization,
         * which means that this measure is not guaranteed to be exact, nor is the corresponding
         * pausing of the container and subsequent raising of the event required to be immediate
         * when the limit has been exceeded.
         *
         * @param max             the maximum amount of RAM usage to allow before pausing the
         *                        container and raising the provided event
         * @param maxRamExceeded  the function to invoke when the max RAM allotment is exceeded
         */
        void limitMemory(Int max, function void() maxRamExceeded);

        /**
         * Invoke a module method with a given name and arguments. The invocation will occur on the
         * [main service](mainService) within the container; as long as the invocation is actively
         * executing on the main service, no other invocation will occur on the the main service.
         * If it is necessary to avoid monopolizing the main service, any method intended for
         * invocation on the main service from outside of the container should immediately delegate
         * to another service within the container.
         *
         * Any exception produced by the call will be transmitted in turn to the caller, so it is
         * wise to handle all potential exceptions in the caller, if those potential exceptions are
         * not desirable to allow to continue to propagate.
         *
         * @param methodName  the name of the method to execute
         * @param args        (optional) a tuple of method arguments (which may be the empty tuple);
         *                    defaults to the empty tuple
         * @param runWithin   (optional) the service within which to execute the specified method;
         *                    a `Null` value indicates to use the main service
         *
         * @return a tuple of results (which may be the empty tuple)
         *
         * @throws IllegalState  if the container is in the `Dead` state, or if `runWithin` is
         *                       specified but not a service within this container
         */
        Tuple invoke(String methodName, Tuple args = Tuple:(), Service? runWithin = Null);

        /**
         * Get the main service of the container. Every container has a main service that is used
         * (among other things) to guarantee the serialization of certain operations, particularly
         * the lazy initialization of constants.
         *
         * @return the application's main service, or `Null` if none exists
         */
        @RO Service? mainService;

        /**
         * Obtain an immutable array of the containers nested directly within this container.
         *
         * The property is an array, but represents a potentially dynamic set of nested containers;
         * as a result, the containers exposed by the array may already have been killed by the time
         * that they are obtained from the array, and new containers that have since been created
         * may or may not be present in the array. This property explicitly withholds any and all
         * guarantees regarding transactional isolation, order of iteration, liveliness of the
         * resulting data, data synchronization, and so on.
         */
        @RO Container[] nestedContainers;

        /**
         * Obtain an immutable array of all of the non-`Container` services nested directly within
         * this container.
         *
         * The property is an array, but represents a potentially dynamic set of nested services;
         * as a result, the services exposed by the array may already have been killed by the time
         * that they are obtained from the array, and new services that have since been created may
         * or may not be present in the array. This property explicitly withholds any and all
         * guarantees regarding transactional isolation, order of iteration, liveliness of the
         * resulting data, data synchronization, and so on.
         */
        @RO Service[] nestedServices;

        /**
         * Pause all execution of code within the container. This attempts to pause all service
         * execution within the container as that execution reaches safe points (the definition of
         * which is beyond the scope of this documentation). Any nested containers are automatically
         * paused as well.
         *
         * This call should be assumed to be asynchronous; if it is important to wait for the pause
         * request to complete before proceeding, then obtain the future result of this invocation,
         * and use it to determine when the operation has completed.
         *
         * @throws IllegalState  if the container is not in the `Running` state
         */
        void pause();

        /**
         * Transition from the `Paused` to the `Running` state. Any nested containers that were
         * automatically paused by the corresponding call to [pause] will be automatically resumed
         * as well.
         *
         * This call should be assumed to be asynchronous; if it is important to wait for the resume
         * request to complete before proceeding, then obtain the future result of this invocation,
         * and use it to determine when the operation has completed.
         *
         * @throws IllegalState  if the container is not in the `Paused` state
         */
        void resume();

        /**
         * Persist the application state to the specified FileStore. Any nested containers are
         * automatically paused by the corresponding call to [pause] will be automatically resumed
         * as well.
         *
         * This call should be assumed to be asynchronous; if it is important to wait for the store
         * request to complete before proceeding, then obtain the future result of this invocation,
         * and use it to determine when the operation has completed.
         *
         * @param filestore  the FileStore to store the current state of the container into, which
         *                   may already contain an older, previously-stored state of the container
         *
         * @throws IllegalState  if the container is not in the `Paused` state
         * @throws IOException   if an I/O issue arises while storing the data to the FileStore
         */
        void store(FileStore filestore);

        /**
         * Reload the application state from the specified FileStore. The container must be in the
         * `Initial` state; this will transition from the `Initial` to the `Paused` state.
         *
         * This call should be assumed to be asynchronous; if it is important to wait for the load
         * request to complete before proceeding, then obtain the future result of this invocation,
         * and use it to determine when the operation has completed.
         *
         * @param filestore  the FileStore containing a previously-stored state of a container that
         *                   had an identical (or otherwise compatible) `TypeSystem`
         *
         * @throws IllegalState  if the container is not in the `Initial` state, or if there occurs
         *                       any of a number of potential incompatibilities between the stored
         *                       state and the information expected by the type system
         * @throws IOException   if an I/O issue arises while loading the data from the FileStore
         */
        void load(FileStore filestore);

        /**
         * Kill this container as immediately as is possible without compromising the stability of
         * the remainder of the running system, killing all containers nested within it as well, and
         * regardless of the current state of this container. This method will always transition the
         * container to the `Dead` state.
         */
        @Override
        void kill();


        // ----- resource utilization --------------------------------------------------------------

        /**
         * Determine the "running" status of the container, including all of its contained services
         * and any nested containers.
         */
        @Override
        @RO Service.ServiceStatus statusIndicator; // TODO "Service." should not be necessary

        /**
         * The amount of time spent processing by the entire container, including all of its
         * contained services and any nested containers.
         */
        @Override
        @RO Duration cpuTime;

        /**
         * The memory footprint of the entire container, including all of its contained services and
         * any nested containers.
         */
        @Override
        @RO Int bytesReserved;

        /**
         * The amount of memory currently allocated within the entire container, including within
         * all of its contained services and any nested containers.
         */
        @Override
        @RO Int bytesAllocated;

        /**
         * Request that the container reclaim memory that is not allocated, including from within
         * all of its contained services and any nested containers.
         */
        @Override
        void gc();
        }
    }

