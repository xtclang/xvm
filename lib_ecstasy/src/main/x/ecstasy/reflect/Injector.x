import annotations.InjectedRef;

/**
 * Injector represents an ability to dynamically inject resources at run-time.
 *
 * Consider a common compile-time injections:
 *
 *    @Inject Console console;
 *    @Inject("homeDir") Directory home;
 *
 * Using the Injector, the same effect could be achieved dynamically, as follows:
 *
 *    @Inject injector;
 *    Console   console = injector.inject(Console, "console");
 *    Directory home    = injector.inject(Directory, "homeDir");
 *
 * A typical use for Injector is when a container is used to "wrap" another container (the _guest_),
 * such as when hosting unit tests, profiling, etc. The wrapping container, which is a _host_, must
 * be able to pass through dependency injection requests from its guest to its parent container, for
 * any resources that it chooses not to intercept. The result is that the host can be constructed
 * without advanced knowledge of the set of resources that the guest will request.
 *
 * @see `mgmt.PassThroughResourceProvider`
 */
interface Injector {
    /**
     * Obtain an injection of the specified type and name.
     *
     * @param type  the type of the injection
     * @param name  the injection name
     * @param opts  (optional) the injection parameters
     *
     * @return the injected object
     */
     <InjectionType> InjectionType inject(Type<InjectionType> type, String name,
                                          InjectedRef.Options opts = Null);
}