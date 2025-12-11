/**
 * A `ResourceRegistry` is a registry of typed and named resources.
 *
 * When a resource is registered with `ResourceRegistry`, the registry assumes ownership of the resource, up until the
 * point the registry is `closed`. At that point, the registry will call the`close()` method on the resource, if it
 * implements `Closeable`.
 *
 * The `ResourceRegistry` can be accessed in XUnit extensions and callbacks via the execution context and used to
 * register resources that are shared across extensions or tests. For example parameter providers that can provide
 * parameters to test methods or injectables.
 */
interface ResourceRegistry
        extends Stringable {

    /**
     * Determine if the `ResourceRegistry` is empty.
     */
    @RO Boolean empty;

    /**
     * Obtain an immutable set of the keys in this `ResourceRegistry`.
     */
    @RO Set<RegistryKey> keys;

    /**
     * Attempts to retrieve the resource that was registered with the
     * specified `Type` and optional name.
     *
     * @param type  the `Type` of the resource
     * @param name  the name of the resource
     *
     * @return `True` iff a resource has been registered with the specified type and name
     * @return the resource
     */
    <ResourceType> conditional ResourceType get(Type<ResourceType> type, String? name = Null);

    /**
     * Returns all the resources that were registered with the specified `Type`,
     * or a sub-class of the specified type.
     *
     * @param type  the `Type` of the resources to return
     *
     * @Return all the resources of the specified `Type`
     */
    <ResourceType> ResourceType[] getAll(Type<ResourceType> type);

    /**
     * Attempts to retrieve the resource that was registered with the
     * specified `RegistryKey`.
     *
     * @param type  the `RegistryKey` of the resource
     *
     * @return `True` iff a resource has been registered with the specified key
     * @return the resource that was registered with the specified key
     */
    conditional RegistryValue get(RegistryKey key);

    /**
     * Registers a resource with this `ResourceRegistry` using the specified `RegistrationBehavior`
     * to handle duplicate registrations.
     *
     * * Multiple resources for the same `Type` can be registered if each resource is registered
     *   with a unique name.
     *
     * * Resources that implement `Closeable` will be closed when (or if) the registry is closed.
     *
     * @param resource  the resource to register
     * @param behavior  the `RegistrationBehavior` to use
     * @param observer  an optional `Observer` that will be called when the resource is being closed
     *
     * @return  `True` iff the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name
     *          parameter if the specified `RegistrationBehavior` is `Always`.
     */
    <ResourceType> conditional String register(
            ResourceType            resource,
            RegistrationBehavior    behavior = RegistrationBehavior.Fail,
            Observer<ResourceType>? observer = Null) = register(resource, Null, behavior, observer);

    /**
     * Registers a resource with this `ResourceRegistry` using the specified `RegistrationBehavior`
     * to handle duplicate registrations.
     *
     * * Multiple resources for the same `Type` can be registered if each resource is registered
     *   with a unique name.
     *
     * * Resources that implement `Closeable` will be closed when (or if) the registry is closed.
     *
     * @param resource  the resource to register
     * @param name      the name of the resource
     * @param behavior  the `RegistrationBehavior` to use
     * @param observer  an optional `Observer` that will be called when the resource is being closed
     *
     * @return  `True` iff the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name
     *          parameter if the specified `RegistrationBehavior` is `Always`.
     */
    <ResourceType> conditional String register(
                    ResourceType            resource,
                    String?                 name     = Null,
                    RegistrationBehavior    behavior = RegistrationBehavior.Fail,
                    Observer<ResourceType>? observer = Null)
            = register(&resource.type, resource, name, behavior, observer);

    /**
     * Registers a resource with this `ResourceRegistry` using the specified `RegistrationBehavior`
     * to handle duplicate registrations.
     *
     * * Multiple resources for the same `Type` can be registered if each resource is registered 
     *   with a unique name.
     *
     * * Resources that implement `Closeable` will be closed when (or if) the registry is closed.
     *
     * @param type      the `Type` of the resource
     * @param resource  the resource to register
     * @param name      the name of the resource
     * @param behavior  the `RegistrationBehavior` to use
     * @param observer  an optional `Observer` that will be called when the resource is being closed
     *
     * @return  `True` iff the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name
     *          parameter if the specified `RegistrationBehavior` is `Always`.
     */    
    <RegisterAs, ResourceType extends RegisterAs> conditional String register(
            Type<RegisterAs>      type,
            ResourceType          resource,
            String?               name     = Null,
            RegistrationBehavior  behavior = RegistrationBehavior.Fail,
            Observer<RegisterAs>? observer = Null);

    /**
     * Registers a resource contained in a `Resource` wrapper with this `ResourceRegistry`.
     *
     * * Multiple resources for the same `Type` can be registered if each resource is registered
     *   with a unique name.
     *
     * * Resources that implement `Closeable` will be closed when (or if) the registry is closed.
     *
     * @param resource  the `Resource` wrapper containing the resource to register
     *
     * @return  `True` iff the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name
     *          parameter if the specified `RegistrationBehavior` is `Always`.
     */
    conditional String registerResource(Resource resource);

    /**
     * Unregisters the resource that was previously registered using the specified `Type` and
     * optional name.
     *
     * Note: Unregistering a resource does not cause it to be closed if it is `Closable`, but it
     * will call any `Observer` that was specified when the resource was registered.
     *
     * @param type  the class of the resource
     * @param name  the name of the resource
     *
     * @return `True` if a resource with the specified `Type` and name was unregistered.
     */
    Boolean unregister(Type type, String? name = Null);

    /**
     * Unregister all resources.
     *
     * Note: `clear()` does not cause resources to be closed if they are `Closable`, but will call
     * any `Observer` that was specified when a resource was registered.
     */
    void clear();

    /**
     * Create a copy of this registry.
     *
     * @return a new `ResourceRegistry` containing the same resources as this registry
     */
    ResourceRegistry! copy();

    /**
     * Merge all the resources in this registry and all the resources in the specified registry into
     * a new `ResourceRegistry`.
     *
     * If both registries contain resources registered with the same type and name, then merging
     * will fail.
     *
     * @param registry  the `ResourceRegistry` to merge with this registry
     *
     * @return `True` if the registries were merged
     * @return a new `ResourceRegistry` containing the merged resources
     */
    conditional ResourceRegistry! merge(ResourceRegistry registry)
            = merge(registry, RegistrationBehavior.Fail);

    /**
     * Merge all the resources in this registry and all the resources in the specified registry
     * into a new `ResourceRegistry`.
     *
     * @param registry  the `ResourceRegistry` to merge with this registry
     * @param behavior  the `RegistrationBehavior` to use
     *
     * @return `True` if the registries were merged
     * @return a new `ResourceRegistry` containing the merged resources
     */
    conditional ResourceRegistry! merge(ResourceRegistry registry, RegistrationBehavior behavior);

    /**
     * Create a `ResourceRegistry.Resource` wrapping the specified `resource` value.
     *
     * @param resource  the resource value
     * @param behavior  the behaviour to use if a resource with the same key is already registered
     * @param observer  an optional `Observer` to register with the resource
     */
    static <ResourceType> Resource<ResourceType, ResourceType> resource(
                    ResourceType            resource,
                    RegistrationBehavior    behavior = RegistrationBehavior.Fail,
                    Observer<ResourceType>? observer = Null)
            = ResourceRegistry.resource(resource, &resource.type.toString(), behavior, observer);

    /**
     * Create a `ResourceRegistry.Resource` wrapping the specified `resource` value.
     *
     * @param resource  the resource value
     * @param name      the resource name
     * @param behavior  the behaviour to use if a resource with the same key is already registered
     * @param observer  an optional `Observer` to register with the resource
     */
    static <ResourceType> Resource<ResourceType, ResourceType> resource(
                    ResourceType            resource,
                    String                  name,
                    RegistrationBehavior    behavior = RegistrationBehavior.Fail,
                    Observer<ResourceType>? observer = Null)
            = new Resource(&resource.type, resource, name, behavior, observer);

    // ----- inner interface Observer --------------------------------------------------------------

    /**
     * A `Observer` receives notifications when a resource has been disposed.
     */
    interface Observer<ResourceType> {
        /**
         * Called by a `ResourceRegistry` when a resource has been unregistered, without being
         * closed.
         *
         * @param resource  the resource being unregistered
         */
        void onUnregister(ResourceType resource) {
        }

        /**
         * Called by a `ResourceRegistry` when a resource is being closed.
         *
         * @param resource  the resource being closed
         * @param cause     (optional) an exception that occurred that triggered the close
         */
        void onClosing(ResourceType resource, Exception? cause = Null) {
        }

        /**
         * Called by a `ResourceRegistry` when a resource is being closed.
         *
         * @param resource  the resource that has been closed
         * @param cause     (optional) an exception that occurred that triggered the close
         */
        void onClosed(ResourceType resource, Exception? cause = Null) {
        }
    }

    // ----- inner enum RegistrationBehavior -------------------------------------------------------

    /**
     * `RegistrationBehavior` is used to specifying the required behavior when registering a
     * resource that has already been registered.
     */
    enum RegistrationBehavior {
        /**
         * Registration should be Ignored if a resource with the same identifier is already
         * registered.
         */
        Ignore,

        /**
         * The resource being registered should Replace any existing resource.
         */
        Replace,

        /**
         * Resource registration should Fail (by raising an exception) if a resource with the same
         * identifier is already registered.
         */
        Fail,

        /**
         * Specifies that registration must Always occur. If an resource is already registered with
         * the same identifier, a new identifier is generated (based on the provided identity) and
         * the specified artifact is registered.
         */
        Always;
    }

    // ----- inner class Resource ------------------------------------------------------------------

    /**
     * A wrapper around the values defining a resource.
     *
     * @param type      the resource `Type`
     * @param resource  the resource
     * @param name      the resource name
     * @param behavior  the behaviour to use if a resource with the same key is already registered
     * @param observer  an optional `Observer` to register with the resource
     */
    static const Resource<RegisterAs, ResourceType extends RegisterAs>(
            Type<RegisterAs>      type,
            ResourceType          resource,
            String                name,
            RegistrationBehavior  behavior = Fail,
            Observer<RegisterAs>? observer = Null) {
    }

    // ----- inner class RegistryKey ---------------------------------------------------------------

    /**
     * The key class for a resource.
     */
    static const RegistryKey<ResourceType> {
        /**
         * Create a `RegistryKey`.
         *
         * @param type  the `Type` of the resource
         * @param name  the name of the resource (if `Null` the `Type` name will be used)
         */
        construct(Type<ResourceType> type, String? name = Null) {
            this.type = type;
            if (name.is(String)) {
                this.name = name;
            } else if (String typeName := type.named()) {
                this.name = typeName;
            } else {
                this.name = "unnamed";
            }
        }

        /**
         * The `Type` of the resource.
         */
        Type<ResourceType> type;

        /**
         * The name of the resource.
         */
        String name;
    }

    // ----- inner class RegistryValue -------------------------------------------------------------

    /**
     * A holder for resource objects and their (optional) `Observer`s.
     *
     * The `Observer` will be invoked when `close()` is invoked on this object. Furthermore, if the
     * provided resource implements `Closeable`, its `close()` method will be invoked.
     *
     * @param resource  the resource object
     * @param observer  an optional observer to invoke when the resource is closed
     */
    static const RegistryValue<RegisterAs, ResourceType>(ResourceType resource,
    Observer<RegisterAs>? observer)
            implements Closeable {
        /**
         * Create a copy of this `RegistryValue`.
         */
        RegistryValue!<RegisterAs, ResourceType> copy() {
            return new RegistryValue(resource, observer);
        }

        @Override
        void close(Exception? cause = Null) {
            Observer<RegisterAs>? observer = this.observer;
            ResourceType              resource = this.resource;

            if (observer.is(Observer)) {
                observer.onClosing(resource.as(RegisterAs), cause);
            }

            if (resource.is(Closeable)) {
                resource.close(cause);
            }

            if (observer.is(Observer)) {
                observer.onClosed(resource.as(RegisterAs), cause);
            }
        }
    }
}
