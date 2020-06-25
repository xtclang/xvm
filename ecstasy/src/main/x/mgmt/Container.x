import reflect.ModuleTemplate;

/**
 * The Container service.
 *
 * Notes:
 * 1. Secure container (no shared singleton services)
 * 2. Lightweight container (all modules from the parent container are shared)
 *    - e.g. load some additional trusted code that you generated on the fly, Excel formula
 * 3. Debugger as a parent container
 */
service Container
        delegates ApplicationControl(appControl)
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(String moduleName, ModuleRepository repository,
              ResourceProvider injector, Module[] sharedModules = [])
        {
        // validate the modules
        // TODO

        // make sure that the shared modules whatever ...
        // TODO

        // load and link the modules
        @Inject Linker linker;
        (TypeSystem innerTypeSystem, ApplicationControl appControl) =
                linker.loadAndLink(moduleName, repository, injector);

        // store off the results
        this.moduleName      = moduleName;
        this.repository      = repository;
        this.sharedModules   = sharedModules;
        this.innerTypeSystem = innerTypeSystem;
        this.appControl      = appControl;
        }


    // ----- Container API -------------------------------------------------------------------------

    /**
     * The name of the underlying module.
     */
    public/private String moduleName;

    /**
     * The repository.
     */
    public/private ModuleRepository repository;

    /**
     * The shared modules.
     */
    public/private Module[] sharedModules;

    /**
     * The TypeSystem for services running inside this Container.
     */
    public/private TypeSystem innerTypeSystem;

    /**
     * The AppControl for services running inside this Container.
     *
     * Note: this delegating property is marked as Atomic, which creates an asynchronous delegating
     *       stub.
     */
    @Atomic private ApplicationControl appControl;

    /**
     * The linker.
     */
    static interface Linker
        {
        /**
         * Validate the content of the provided XTC structure and return the name of the primary
         * module.
         *
         * REVIEW: this method will probably go away
         * @throws an Exception if the bytes don't represent a valid module
         */
        String validate(Byte[] bytes);

        /**
         * Load and verify the specified module.
         *
         * @throws an Exception if the module cannot be loaded for any reason
         */
        (TypeSystem typeSystem, ApplicationControl) loadAndLink(String moduleName,
                ModuleRepository repository, ResourceProvider injector, Module[] sharedModules = [])
            {
            return resolveAndLink(repository.getModule(moduleName), repository, injector, sharedModules);
            }

        /**
         * Load and verify the specified module.
         *
         * @throws an Exception if the module cannot be loaded for any reason
         */
        (TypeSystem typeSystem, ApplicationControl) resolveAndLink(immutable Byte[] bytes,
                ModuleRepository repository, ResourceProvider injector, Module[] sharedModules = []);

        /**
         * Link the provided modules together to form a type system.
         *
         * @throws an Exception if an error occurs attempting to link the provided modules together
         */
        (TypeSystem typeSystem, ApplicationControl) link(
                (ModuleTemplate | Module)[] modules, ResourceProvider injector);
        }

    /**
     * Represents the container control facility.
     */
    static interface ApplicationControl
        {
        /**
         * Add a constraint for the specified name. The names are conventionally well known, for
         * example `memory`, `time interval`, `cpu cycles` `network bandwidth`.
         * TODO: there has to be a full section on the names and valid ranges
         */
        void addConstraint(String name, Interval<Int> interval);

        /**
         * Invoke a module method with a given name and arguments.
         */
        Tuple invoke(String methodName, Tuple args);

        /**
         * Get the main service for the running application.
         *
         * @return True iff the application managed by the ApplicationControl is running
         * @return (conditional) the application's main service
         */
        conditional Service mainService();

        /**
         * Pause the application. This call will try a best effort attempt to stop the application
         * execution when it reaches a safe point (TODO: explain).
         */
        void pause();

        /**
         * Persist the application state to the specified FileStore. This operation is allowed only
         * after the application has been paused.
         */
        void flush(FileStore store);

        /**
         * Reload the application state from the specified FileStore and resume its execution.
         */
        void reactivate(FileStore store);

        /**
         * Kill the application immediately.
         */
        void kill();
        }
    }

