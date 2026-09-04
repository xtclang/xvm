/**
 * This is an application intended to be hosted in "Container 0" for the purpose of running
 * additional applications (such as module test-runs) that can be loaded dynamically.
 */
@web.WebApp
module runner.xtclang.org {
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import ecstasy.mgmt.BasicResourceProvider;
    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.ResourceProvider;
    import ecstasy.maps.HashMap;

    import ecstasy.reflect.ModuleTemplate;

    import web.Get;
    import web.Post;
    import web.WebService;
    import web.http.HostInfo;

    /**
     * Start the HTTP endpoint. Native callers should invoke [runTask] directly.
     */
    void run(String[] args=["localhost:8080/8090", "localhost:8080/8090"]) {
        String routeString = args.size > 0 ? args[0] : "localhost:8080/8090";
        String bindString  = args.size > 1 ? args[1] : "localhost:8080/8090";

        HostInfo route   = hostOf(routeString);
        HostInfo binding = hostOf(bindString);
        xenia.createServer(this, route=route, binding=binding);

        @Inject Console console;
        String portSuffix = route.httpPort == 80 ? "" : $":{route.httpPort}";
        console.print($"Runner is listening at http://{route.host}{portSuffix}");

        private HostInfo hostOf(String addressString) {
            if (Int portOffset := addressString.indexOf(":")) {
                String portsString = addressString.substring(portOffset + 1);
                addressString      = addressString[0 ..< portOffset];

                assert Int slashOffset := portsString.indexOf("/") as "Ports are missing";

                UInt16 httpPort  = new UInt16(portsString[0 ..< slashOffset]);
                UInt16 httpsPort = new UInt16(portsString.substring(slashOffset + 1));
                return new HostInfo(addressString, httpPort, httpsPort);
            }
            return new HostInfo(addressString);
        }
    }

    /**
     * Create a container for the supplied module and start its `run()` method.
     *
     * @param template    the module to run
     * @param repository  the repository used to resolve the module's dependencies
     * @param consoleId   the optional ID of the named native console resource
     *
     * @return the task identifier
     */
    Int runTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId) =
            TaskRegistry.runTask(template, repository, consoleId);

    /**
     * @return True iff the identified task has not completed
     */
    Boolean taskRunning(Int id) = TaskRegistry.taskRunning(id);

    /**
     * @return the task's integer result, if it completed with one
     */
    Int? taskResult(Int id) = TaskRegistry.taskResult(id);

    /**
     * @return the task failure text, if it completed exceptionally
     */
    String? taskFailure(Int id) = TaskRegistry.taskFailure(id);

    /**
     * Stop the identified task's container.
     */
    void killTask(Int id) = TaskRegistry.killTask(id);

    /**
     * Forget the identified task, releasing its container.
     *
     * Eviction is explicit rather than automatic on completion, because a caller reads [taskResult]
     * and [taskFailure] after [taskRunning] has gone `False`. Until this is called the registry
     * holds the task, and the task holds its `Container`, and through it that container's
     * composition and template caches.
     */
    void forgetTask(Int id) = TaskRegistry.forgetTask(id);

    /**
     * @return a human-readable task status
     */
    String taskStatus(Int id) = TaskRegistry.taskStatus(id);

    /**
     * Mutable task registry.
     */
    static service TaskRegistry {
        private Map<Int, Task> tasks = new HashMap();

        private Int nextTaskId;

        Int runTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
            Int  id   = allocateTaskId();
            Task task = new Task(id, template, repository, consoleId);
            tasks[id] = task;
            task.start();
            return id;
        }

        Boolean taskRunning(Int id) = taskFor(id).running;

        Int? taskResult(Int id) = taskFor(id).result;

        String? taskFailure(Int id) = taskFor(id).failure;

        void killTask(Int id) {
            taskFor(id).kill();
        }

        void forgetTask(Int id) {
            if (Task task := tasks.get(id)) {
                task.release();
                tasks.remove(id);
            }
        }

        Int submitTask(String moduleName, ModuleRepository repository) {
            return runTask(repository.getResolvedModule(moduleName), repository, Null);
        }

        String taskStatus(Int id) = taskFor(id).status;

        private Int allocateTaskId() {
            do {
                ++nextTaskId;
            } while (tasks.contains(nextTaskId));
            return nextTaskId;
        }

        private Task taskFor(Int id) {
            assert Task task := tasks.get(id) as $"Unknown task {id}";
            return task;
        }
    }

    /**
     * State and control for one application container.
     */
    service Task(Int id, ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
        Boolean running;

        Int? result;

        String? failure;

        String status.get() = running
                ? "running"
                : failure == Null
                    ? result == Null ? "stopped" : $"stopped: {result}"
                    : $"failed: {failure}";

        private Container? container;

        void start() {
            assert container == Null;

            ResourceProvider injector;
            if (Int consoleId ?= this.consoleId) {
                @Inject(resourceName=$"console_{consoleId}") Console console;
                injector = new TaskResourceProvider(console);
            } else {
                injector = new BasicResourceProvider();
            }

            Container        container = new Container(
                    template, Container.Model.Lightweight, repository, injector);
            this.container = container;
            running        = True;

            @Future Tuple outcome = container.invoke("run", ());
            &outcome.whenComplete((tuple, exception) -> {
                if (exception == Null) {
                    if (tuple != Null && !tuple.empty && tuple[0].is(Int)) {
                        result = tuple[0].as(Int);
                    }
                } else {
                    failure = exception.toString();
                }
                running        = False;
                // "this." is required here: the local from start() shadows the property
                this.container = Null;   // the run is over; stop pinning the container
            });
        }

        void kill() {
            if (Container container ?= this.container, running) {
                container.kill();
                running        = False;
                this.container = Null;
            }
        }

        /**
         * Drop any remaining reference to the container, killing it if it is still running. Safe to
         * call more than once.
         */
        void release() {
            if (Container container ?= this.container) {
                if (running) {
                    container.kill();
                    running = False;
                }
                this.container = Null;
            }
        }
    }

    /**
     * The HTTP surface over the task registry.
     */
    @WebService("/")
    service Api {
        @Post("run{/moduleName}")
        Int runModule(String moduleName) {
            @Inject("repository") ModuleRepository repository;

            return TaskRegistry.submitTask(moduleName, repository);
        }

        @Get("task{/id}")
        String status(Int id) = TaskRegistry.taskStatus(id);

        @Post("task{/id}/kill")
        String kill(Int id) {
            TaskRegistry.killTask(id);
            return TaskRegistry.taskStatus(id);
        }
    }

    /**
     * Provides an external console and delegates the remaining basic injections.
     */
    service TaskResourceProvider(Console console)
            extends BasicResourceProvider {
        @Override
        Supplier getResource(Type type, String name) {
            if (type == Console && name == "console") {
                return console;
            }
            return super(type, name);
        }
    }
}
