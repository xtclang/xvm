/**
 * This is an application intended to be hosted in "Container 0" for the purpose of running
 * additional applications (such as module test-runs) that can be loaded dynamically.
 *
 * To run the server and interactive client from the `manualTests` directory, use two terminal
 * tabs:
 *
 *     xec -L build/xtc/main/lib runner.xtclang.org
 *
 *     xec runner_client.xtclang.org http://localhost:8080
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
    import web.Produces;
    import web.WebService;
    import web.http.HostInfo;

    /**
     * Start the HTTP endpoint. Native callers should invoke [registerTask] and [startTask]
     * directly.
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
     * Register a task for the supplied module.
     *
     * @param template    the module to run
     * @param repository  the repository used to resolve the module's dependencies
     * @param consoleId   the optional ID of the named native console resource
     *
     * @return the task identifier
     */
    Int registerTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId) =
            TaskRegistry.registerTask(template, repository, consoleId);

    /**
     * Start the identified task.
     *
     * @return the task's integer result, or zero if it completed without one
     * @return the task failure text, or an empty string if it completed normally
     */
    Tuple<Int, String> startTask(Int id) = TaskRegistry.startTask(id);

    /**
     * @return a human-readable task status
     */
    String taskStatus(Int id) = TaskRegistry.taskStatus(id);

    /**
     * Stop the identified task's container.
     */
    void killTask(Int id) = TaskRegistry.killTask(id);

    /**
     * Delete the file-system root allocated to the identified task.
     */
    void deleteTaskDirectory(Int id, String moduleName) =
            TaskRegistry.deleteTaskDirectory(id, moduleName);

    /**
     * Task registry singleton service.
     */
    static service TaskRegistry {
        private Map<Int, Task> tasks = new HashMap();

        private Int nextTaskId = 1;

        /**
         * Implementation of the `registerTask` API.
         */
        Int registerTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId,
                         Boolean retainStore = True) {
            Int  id   = nextTaskId++;
            Task task = new Task(id, template, repository, consoleId, retainStore);
            tasks[id] = task;
            return id;
        }

        /**
         * Implementation of the `startTask` API.
         */
        Tuple<Int, String> startTask(Int id) = taskFor(id).start();

        /**
         * Implementation of the `taskStatus` API.
         */
        String taskStatus(Int id) {
            if (Task task := findTask(id)) {
                return task.status;
            }
            return "terminated";
        }

        /**
         * Implementation of the `killTask` API.
         */
        void killTask(Int id) = taskFor(id).kill();

        /**
         * Remove the identified task from the registry.
         */
        void unregisterTask(Int id) = tasks.remove(id);

        /**
         * Register and start a task requested through the web API. Note, that the file store is
         * immediately removed upon the task completion.
         */
        Int submitTask(String moduleName, ModuleRepository repository) {
            Int id = registerTask(
                    repository.getResolvedModule(moduleName), repository, Null, False);
            taskFor(id).start^();
            return id;
        }

        /**
         * Delete the file-system root allocated to the identified task.
         */
        void deleteTaskDirectory(Int id, String moduleName) {
            assert !findTask(id);

            @Inject Directory curDir;
            curDir.dirFor(taskDirectoryName(moduleName, id)).deleteRecursively();
        }

        /**
         * Implementation of the `runningTasks` through web API.
         */
        String runningTasks() {
            StringBuffer listing = new StringBuffer();
            for (Task task : tasks.values) {
                if (String description := task.runningDescription()) {
                    if (!listing.empty) {
                        listing.add('\n');
                    }
                    listing.addAll(description);
                }
            }
            return listing.empty ? "No running tasks" : listing.toString();
        }

        /**
         * @return True iff the identified task remains registered
         * @return (conditional) the registered task
         */
        private conditional Task findTask(Int id) {
            if (Task task := tasks.get(id)) {
                return True, task;
            }
            assert 0 < id < nextTaskId as $"Unknown task {id}";
            return False;
        }

        /**
         * @return a registered task, asserting that it is still available
         */
        private Task taskFor(Int id) {
            assert Task task := findTask(id) as $"Task {id} has terminated";
            return task;
        }
    }

    /**
     * State and control for one application container.
     */
    service Task(Int id, ModuleTemplate template, ModuleRepository repository, Int? consoleId,
                 Boolean retainStore) {
        private Container? container;

        private Time? started;

        Boolean running;

        String status.get() = running ? "running" : "stopped";

        conditional String runningDescription() {
            if (!running) {
                return False;
            }

            assert Time started ?= this.started;
            @Inject Clock clock;
            return True, $"{id} {template.qualifiedName} processing: {clock.now - started} sec";
        }

        Tuple<Int, String> start() {
            assert container == Null;

            @Inject Clock clock;
            started = clock.now;

            BufferedConsole? bufferedConsole = Null;
            ResourceProvider injector;
            if (Int consoleId ?= this.consoleId) {
                @Inject(resourceName=$"console_{consoleId}") Console console;
                injector = new TaskResourceProvider(id, template, console);
            } else {
                @Inject Console console;
                bufferedConsole = new BufferedConsole($"{id}> ", console);
                injector = new TaskResourceProvider(
                        id, template, &bufferedConsole.maskAs(Console));
            }

            container = new Container(template, Lightweight, repository, injector);
            running   = True;

            @Future Tuple<Int, String> completion;
            @Future Tuple              outcome = container.as(Container).invoke("run", ());
            &outcome.whenComplete((tuple, exception) -> {
                bufferedConsole?.flush();

                Int    result  = 0;
                String failure = "";
                if (exception == Null) {
                    if (tuple != Null && !tuple.empty && tuple[0].is(Int)) {
                        result = tuple[0].as(Int);
                    }
                } else {
                    failure = exception.toString();
                }

                running    = False;
                container  = Null;
                completion = (result, failure);
                TaskRegistry.unregisterTask^(id);

                if (!retainStore) {
                    TaskRegistry.deleteTaskDirectory^(id, template.name);
                }
            });
            return completion;
        }

        void kill() {
            if (Container container ?= this.container, running) {
                container.kill();
                running = False;
            }
        }
    }

    /**
     * A Console that buffers incomplete lines and identifies every output line with the specified
     * prefix.
     */
    service BufferedConsole(String prefix, Console console)
            implements Console {
        private StringBuffer line = new StringBuffer();

        @Override
        void print(Object object = "", Boolean suppressNewline = False) {
            line.append(object);
            if (!suppressNewline) {
                console.print(prefix + line.toString());
                line.clear();
            }
        }

        /**
         * Flush an incomplete line.
         */
        void flush() {
            if (!line.empty) {
                console.print(prefix + line.toString());
                line.clear();
            }
        }

        @Override
        String readLine(String prompt = "", Boolean suppressEcho = False) {
            throw new Unsupported();
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

        @Get("tasks")
        @Produces(Text)
        String tasks() = TaskRegistry.runningTasks();

        @Get("task{/id}")
        @Produces(Text)
        String status(Int id) = TaskRegistry.taskStatus(id);

        @Post("task{/id}/kill")
        @Produces(Text)
        String kill(Int id) {
            TaskRegistry.killTask(id);
            return TaskRegistry.taskStatus(id);
        }
    }

    /**
     * Provides the task-specific file system and console, and delegates the remaining basic
     * injections.
     */
    service TaskResourceProvider(Int id, ModuleTemplate template, Console console)
            extends BasicResourceProvider {
        @Lazy FileStore store.calc() {
            @Inject Directory curDir;
            Directory taskDir = curDir.dirFor(taskDirectoryName(template.name, id)).ensure();
            return new ecstasy.fs.DirectoryFileStore(taskDir);
        }

        @Override
        Supplier getResource(Type type, String name) {
            switch (type.isNullable() ?: type, name) {
            case (Console, "console"):
                return console;

            case (FileStore, "storage"):
                return &store.maskAs(FileStore);

            case (Directory, "rootDir"):
            case (Directory, "homeDir"):
            case (Directory, "curDir"):
                Directory root = store.root;
                return &root.maskAs(Directory);

            case (Directory, "tmpDir"):
                Directory temp = store.root.dirFor(".temp").ensure();
                return &temp.maskAs(Directory);
            }
            return super(type, name);
        }
    }

    /**
     * Compute the file-system root name for a task.
     */
    static String taskDirectoryName(String moduleName, Int id) = $"{moduleName}_{id}";
}
