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
     * Wait for the identified task to finish and remove it from the task registry.
     *
     * @return the task's integer result, if it completed with one
     * @return the task failure text, if it completed exceptionally
     * @return the task start time as milliseconds since the epoch
     * @return the task stop time as milliseconds since the epoch
     */
    Tuple<Int?, String?, Int, Int> awaitTask(Int id) = TaskRegistry.awaitTask(id);

    /**
     * @return the identified task's start time as milliseconds since the epoch
     */
    Int taskStarted(Int id) = TaskRegistry.taskStarted(id);

    /**
     * @return a list of all running tasks and their processing times
     */
    String runningTasks() = TaskRegistry.runningTasks();

    /**
     * @return a human-readable task status
     */
    String taskStatus(Int id) = TaskRegistry.taskStatus(id);

    /**
     * Stop the identified task's container.
     */
    void killTask(Int id) = TaskRegistry.killTask(id);

    /**
     * Task registry singleton service.
     */
    static service TaskRegistry {
        private Map<Int, Task> tasks = new HashMap();

        private Int nextTaskId = 1;

        Int runTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
            Int  id   = nextTaskId++;
            Task task = new Task(id, template, repository, consoleId);
            tasks[id] = task;
            task.start();
            return id;
        }

        Tuple<Int?, String?, Int, Int> awaitTask(Int id) {
            Task task = taskFor(id);
            Tuple<Int?, String?, Int, Int> outcome = task.awaitCompletion();
            tasks.remove(id);
            return outcome;
        }

        Int taskStarted(Int id) = taskFor(id).startedMillis;

        String runningTasks() {
            StringBuffer listing = new StringBuffer();
            for (Task task : tasks.values) {
                if (String description := task.runningDescription()) {
                    if (listing.empty) {
                        listing.addAll("ID\tMODULE\tPROCESSING TIME");
                    }
                    listing.add('\n').addAll(description);
                }
            }
            return listing.empty ? "No running tasks" : listing.toString();
        }

        String taskStatus(Int id) {
            if (Task task := tasks.get(id)) {
                return task.status;
            }
            assert 0 < id < nextTaskId as $"Unknown task {id}";
            return "terminated";
        }

        void killTask(Int id) = taskFor(id).kill();

        Int submitTask(String moduleName, ModuleRepository repository) =
                runTask(repository.getResolvedModule(moduleName), repository, Null);

        private Task taskFor(Int id) {
            assert Task task := tasks.get(id) as $"Unknown task {id}";
            return task;
        }
    }

    /**
     * State and control for one application container.
     */
    service Task(Int id, ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
        private Container? container;

        private Time? started;

        @Future Tuple<Int?, String?, Int, Int> completion;

        Boolean running;

        Int? result;

        String? failure;

        String status.get() = running
                ? "running"
                : failure == Null
                    ? result == Null ? "stopped" : $"stopped: {result}"
                    : $"failed: {failure}";

        Int startedMillis.get() {
            assert Time started ?= this.started;
            return epochMillis(started);
        }

        conditional String runningDescription() {
            if (!running) {
                return False;
            }

            assert Time started ?= this.started;
            @Inject Clock clock;
            return True, $"{id}\t{template.qualifiedName}\t{clock.now - started}";
        }

        Tuple<Int?, String?, Int, Int> awaitCompletion() = completion;

        void start() {
            assert container == Null;

            @Inject Clock clock;
            started = clock.now;

            ResourceProvider injector;
            if (Int consoleId ?= this.consoleId) {
                @Inject(resourceName=$"console_{consoleId}") Console console;
                injector = new TaskResourceProvider(console);
            } else {
                injector = new BasicResourceProvider();
            }

            container = new Container(template, Lightweight, repository, injector);
            running   = True;

            @Future Tuple outcome = container.invoke("run", ());
            &outcome.whenComplete((tuple, exception) -> {
                if (exception == Null) {
                    if (tuple != Null && !tuple.empty && tuple[0].is(Int)) {
                        result = tuple[0].as(Int);
                    } else {
                        result = 0;
                    }
                } else {
                    failure = exception.toString();
                }

                Time stopped = clock.now;
                assert Time started ?= this.started;
                running   = False;
                container = null;
                completion = (result, failure, epochMillis(started), epochMillis(stopped));
            });
        }

        void kill() {
            if (Container container ?= this.container, running) {
                container.kill();
                running = False;
            }
        }

        private static Int epochMillis(Time time) =
                (time.epochPicos / Duration.PicosPerMilli).toInt64();
    }

    /**
     * The HTTP surface over the task registry.
     */
    @WebService("/")
    service Api {
        @Post("run{/moduleName}")
        Int runModule(String moduleName) {
            @Inject("repository") ModuleRepository repository;

            Int id = TaskRegistry.submitTask(moduleName, repository);
            @Future Tuple<Int?, String?, Int, Int> ignored = TaskRegistry.awaitTask(id);
            return id;
        }

        @Get("tasks")
        String tasks() = TaskRegistry.runningTasks();

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
