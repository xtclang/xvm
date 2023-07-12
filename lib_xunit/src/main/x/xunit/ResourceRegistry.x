/**
 * A `ResourceRegistry` is a registry of typed and named resources.
 *
 * When a resource is registered with `ResourceRegistry`, the registry
 * assumes ownership of the resource, up until the point the registry
 * is `closed`.
 */
class ResourceRegistry
        implements Closeable
        implements Stringable {

    /**
     * Create an empty resource registry.
     */
    construct () {
        resources = new HashMap();
    }

    /**
     * Create a new `ResourceRegistry` copying the contents of the specified registry.
     */
    construct (ResourceRegistry registry) {
        Map<RegistryKey, RegistryValue> resources = new HashMap();
        resources.putAll(registry.resources);
        this.resources = resources;
    }

    /**
     * The `Map` of registered resources.
     */
    private Map<RegistryKey, RegistryValue> resources;

    /**
     * Determine if the `ResourceRegistry` is empty.
     */
    Boolean empty.get() {
        return resources.empty;
    }

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
    <Resource> conditional Resource getResource(Type<Resource> type, String? name = Null) {
        if (RegistryValue value := resources.get(new RegistryKey(type, name))) {
            return True, value.resource.as(Resource);
        }
        return False;
    }

    /**
     * Returns all the resources that were registered with the specified `Type`,
     * or a sub-class of the specified type.
     *
     * @param type  the `Type` of the resources to return
     *
     * @Return all the resources of the specified `Type`
     */
    <Resource> Resource[] getResources(Type<Resource> type) {
        Array<Resource> result = new Array();
        for (Map.Entry entry : resources.entries) {
            if (entry.key.type.isA(type)) {
                result.add(entry.value.resource.as(Resource));
            }
        }
        return result;
    }

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
     * @return  the name used to register the resource, which may be different from the name parameter
     *          if the specified `RegistrationBehavior` is `Always`.
     */
    <Resource> conditional String register(Resource resource,
            RegistrationBehavior behavior = RegistrationBehavior.Fail,
            Observer<Resource>? observer = Null) {
        return register(resource, Null, behavior, observer);
    }

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
     * @return  the name used to register the resource, which may be different from the name parameter
     *          if the specified `RegistrationBehavior` is `Always`.
     */
    <Resource> conditional String register(Resource resource, String? name = Null,
            RegistrationBehavior behavior = RegistrationBehavior.Fail, Observer<Resource>? observer = Null) {
        Type<Resource> type  = &resource.actualType;
        return register(type, resource, name, behavior, observer);
    }

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
     * @return  the name used to register the resource, which may be different from the name parameter
     *          if the specified `RegistrationBehavior` is `Always`.
     */    
    <RegisterAs, Resource extends RegisterAs> conditional String register(Type<RegisterAs> type,
            Resource resource, String? name = Null,
            RegistrationBehavior behavior = RegistrationBehavior.Fail,
            Observer<RegisterAs>? observer = Null) {

        String                              nameToRegister = name == Null ? &resource.actualType.toString() : name;
        RegistryKey                         key            = new RegistryKey(type, nameToRegister);
        RegistryValue<RegisterAs, Resource> value          = new RegistryValue(resource.as(Resource), observer);
        return registerInternal(key, value, behavior, nameToRegister);
    }

    /**
     * Registers a resource contained in a `ResourceWrapper` with this `ResourceRegistry`.
     *
     * * Multiple resources for the same `Type` can be registered if each resource is registered
     *   with a unique name.
     *
     * * Resources that implement `Closeable` will be closed when (or if) the registry is closed.
     *
     * @param resource  the `Resource` wrapper containing the resource to register
     *
     * @return  `True` iff the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name parameter
     *          if the specified `RegistrationBehavior` is `Always`.
     */
    conditional String register(Resource resource) {
        RegistryKey   key   = new RegistryKey(resource.type, resource.name);
        RegistryValue value = new RegistryValue(resource.resource, resource.observer);
        return registerInternal(key, value, resource.behavior, resource.name);
    }

    /**
     * Registers a resource with this `ResourceRegistry` using the specified `RegistrationBehavior`
     * to handle duplicate registrations.
     *
     * @param key           the `RegistryKey` to register the resource with
     * @param value         the `RegistryValue` to register
     * @param behavior      the `RegistrationBehavior` to use
     * @param originalName  the original name of the resource to register
     *
     * @return  `True` if the resource was registered, or `False` if registration failed
     * @return  the name used to register the resource, which may be different from the name parameter
     *          if the specified `RegistrationBehavior` is `Always`.
     */
    private conditional String registerInternal(RegistryKey key, RegistryValue value,
            RegistrationBehavior behavior, String originalName) {
        Boolean registered = resources.process(key, entry -> {
            Boolean registered;
            if (entry.exists) {
                switch (behavior) {
                    case Ignore:
                        registered = True;
                        break;
                    case Replace :
                        if (value.resource != entry.value.resource) {
                            entry.value = value;
                        }
                        registered = True;
                        break;
                    case Fail :
                        registered = value.resource == entry.value.resource;
                        break;
                    case Always :
                        registered = False;
                        break;
                }
            }
            else {
                entry.value = value;
                registered = True;
            }
            return registered;
        });

        if (registered || behavior != Always) {
            return registered, key.name;
        }

        @Inject Random random;
        key = new RegistryKey(key.type, $"{originalName}-{random.int64()}");
        return registerInternal(key, value, behavior, originalName);
    }

    /**
     * Unregisters the resource that was previously registered using the specified `Type`
     * and optional name.
     *
     * Note: Unregistering a resource does not cause it to be closed if it is `Closable`,
     * but it will call any `Observer` that was specified when the resource was registered.
     *
     * @param type  the class of the resource
     * @param name  the name of the resource
     *
     * @return `True` if a resource with the specified `Type` and name was unregistered.
     */
    Boolean unregister(Type type, String? name = Null) {
        return resources.process(new RegistryKey(type, name), entry -> {
            if (entry.exists) {
                RegistryValue value    = entry.value;
                Observer?     observer = value.observer;
                if (observer.is(Observer)) {
                    observer.onUnregister(value.resource);
                }
                entry.delete();
                return True;
            }
            return False;
        });
    }


    /**
     * Unregister all resources.
     *
     * Note: `clear()` does not cause resources to be closed if they are `Closable`,
     * but will call any `Observer` that was specified when a resource was registered.
     */
    void clear() {
        Set<RegistryKey> keys = resources.keys;
        resources.processAll(keys, entry -> {
            if (entry.exists) {
                RegistryValue value    = entry.value;
                Observer?     observer = value.observer;
                if (observer.is(Observer)) {
                    observer.onUnregister(value.resource);
                }
                entry.delete();
            }
            return Null;
        });
    }

    /**
     * Merge all the resources in this registry and all the resources in the specified registry
     * into a new `ResourceRegistry`.
     *
     * If both registries contain resources registered with the same type and name,
     * then merging will fail.
     *
     * @param registry  the `ResourceRegistry` to merge with this registry
     *
     * @return `True` if the registries were merged
     * @return a new `ResourceRegistry` containing the merged resources
     */
    conditional ResourceRegistry! merge(ResourceRegistry registry) {
        return merge(registry, RegistrationBehavior.Fail);
    }

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
    conditional ResourceRegistry! merge(ResourceRegistry registry, RegistrationBehavior behavior) {
        ResourceRegistry merged = new ResourceRegistry();
        merged.resources.putAll(this.resources);
        for (Map.Entry entry : registry.resources.entries) {
            RegistryKey   key   = entry.key;
            RegistryValue value = entry.value;
            if (!merged.registerInternal(key, new RegistryValue(value.resource, value.observer), behavior, key.name)) {
                return False;
            }
        }
        return True, merged;
    }

    /**
     * Create a `ResourceRegistry.Resource` wrapping the specified `resource` value.
     *
     * @param resource the actual resource value
     */
    static <ResourceType> Resource<ResourceType, ResourceType> resource(
            ResourceType resource, RegistrationBehavior behavior = RegistrationBehavior.Fail,
            Observer<ResourceType>? observer = Null) {
        Type<ResourceType> type = &resource.actualType;
        String             name = type.toString();
        return new Resource(type, resource, name, behavior, observer);
    }

    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null) {
        for (RegistryValue value : resources.values) {
            value.close(cause);
        }
        resources.clear();
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return &this.actualClass.name.estimateStringLength()
                + 2 + resources.estimateStringLength();
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        Class clz = &this.actualClass;
        clz.name.appendTo(buf);
        "(".appendTo(buf);
        resources.appendTo(buf);
        return ")".appendTo(buf);
    }

    // ----- inner interface Observer --------------------------------------------------------------

    /**
     * A `Observer` receives notifications when a resource has been disposed.
     */
    interface Observer<Resource> {
        /**
         * Called by a `ResourceRegistry` when a resource has been unregistered,
         * without being closed.
         *
         * @param resource  the resource being unregistered
         */
        void onUnregister(Resource resource) {
        }

        /**
         * Called by a `ResourceRegistry` when a resource is being closed.
         *
         * @param resource  the resource being closed
         * @param cause     (optional) an exception that occurred that triggered the close
         */
        void onClosing(Resource resource, Exception? cause = Null) {
        }

        /**
         * Called by a `ResourceRegistry` when a resource is being closed.
         *
         * @param resource  the resource that has been closed
         * @param cause     (optional) an exception that occurred that triggered the close
         */
        void onClosed(Resource resource, Exception? cause = Null) {
        }
    }

    // ----- inner enum RegistrationBehavior -------------------------------------------------------

    /**
     * `RegistrationBehavior` is used to specifying the required behavior
     *  when registering a resource that has already been registered.
     */
    enum RegistrationBehavior {
        /**
         * Registration should be Ignored if a resource with the same identifier is
         * already registered.
         */
        Ignore,

        /**
         * The resource being registered should Replace any existing resource.
         */
        Replace,

        /**
         * Resource registration should Fail (by raising an exception) if a
         * resource with the same identifier is already registered.
         */
        Fail,

        /**
         * Specifies that registration must Always occur. If an resource is already registered
         * with the same identifier, a new identifier is generated (based
         * on the provided identity) and the specified artifact is registered.
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
    static class Resource<RegisterAs, ResourceType extends RegisterAs>(
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
    static const RegistryKey<Resource> {
        /**
         * Create a `RegistryKey`.
         *
         * @param type  the `Type` of the resource
         * @param name  the name of the resource (if `Null` the `Type` name will be used)
         */
        construct(Type<Resource> type, String? name = Null) {
            this.type = type;
            this.name = name.is(String) ? name : type.toString();
        }

        /**
         * The `Type` of the resource.
         */
        Type<Resource> type;

        /**
         * The name of the resource.
         */
        String name;
    }

    // ----- inner class RegistryValue -------------------------------------------------------------

    /**
     * A holder for resource objects and their (optional) `Observer`s.
     *
     * The `Observer` will be invoked when `close()` is invoked on this object.
     * Furthermore, if the provided resource implements `Closeable`, its `close()`
     * method will be invoked.
     */
    static class RegistryValue<RegisterAs, Resource>(Resource resource, Observer<RegisterAs>? observer)
            implements Closeable {

        @Override
        void close(Exception? cause = Null) {
            Observer<RegisterAs>? observer = this.observer;
            Resource              resource = this.resource;

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
