import xunit.extensions.ResourceRegistry;

/**
 * A simple implementation of a `ResourceRegistry`.
 */
service SimpleResourceRegistry
        implements ResourceRegistry
        implements Closeable {

    /**
     * Create an empty resource registry.
     */
    construct () {
        resources = new HashMap();
    }

    /**
     * Create a new `ResourceRegistry` copying the contents of the specified registry.
     */
    construct (SimpleResourceRegistry registry) {
        Map<RegistryKey, RegistryValue> resources = new HashMap();
        resources.putAll(registry.resources);
        this.resources = resources;
    }

    /**
     * The `Map` of registered resources.
     */
    private Map<RegistryKey, RegistryValue> resources;

    @Override Boolean empty.get() = resources.empty;

    @Override Set<RegistryKey> keys.get() = new HashSet(resources.keys).freeze(True);

    @Override
    <ResourceType> conditional ResourceType get(Type<ResourceType> type, String? name = Null) {
        RegistryKey key = new RegistryKey(type, name);
        if (RegistryValue value := resources.get(key)) {
            return True, value.resource.as(ResourceType);
        }
        // try String type
        if (type.is(Type<Destringable>)) {
            if (RegistryValue value := resources.get(new RegistryKey(String, key.name))) {
                String s = value.resource.as(String);
                return True, new type.DataType(s).as(ResourceType);
            }
        }
        return False;
    }

    @Override
    <ResourceType> ResourceType[] getAll(Type<ResourceType> type) {
        Array<ResourceType> result = new Array();
        for (Map.Entry entry : resources.entries) {
            if (entry.key.type.is(Type<ResourceType>)) {
                result.add(entry.value.resource.as(ResourceType));
            }
        }
        return result.freeze(True);
    }

    @Override
    conditional RegistryValue get(RegistryKey key) {
        return resources.get(key);
    }

    @Override
    <RegisterAs, ResourceType extends RegisterAs> conditional String register(
            Type<RegisterAs>           type,
            ResourceType               resource,
            String?                    name     = Null,
            RegistrationBehavior       behavior = RegistrationBehavior.Fail,
            Observer<RegisterAs>?      observer = Null) {

        RegistryValue<RegisterAs> value          = new RegistryValue(resource, observer);
        String                    nameToRegister = name ?: &resource.type.toString();

        return registerInternal(new RegistryKey(type, nameToRegister),
                                value,
                                behavior,
                                nameToRegister);
    }

    @Override
    conditional String registerResource(Resource resource) {
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
     * @return  the name used to register the resource, which may be different from the name
     *          parameter if the specified `RegistrationBehavior` is `Always`.
     */
    private conditional String registerInternal(RegistryKey key, RegistryValue value,
            RegistrationBehavior behavior, String originalName) {

        Boolean registered = False;
        if (RegistryValue current := resources.get(key)) {
            switch (behavior) {
                case Ignore:
                    registered = True;
                    break;
                case Replace :
                    resources.put(key, value);
                    registered = True;
                    break;
                case Fail :
                    registered = value.resource == current.resource;
                    break;
                case Always :
                    @Inject Random random;
                    key = new RegistryKey(key.type, $"{originalName}-{random.int64()}");
                    resources.put(key, value);
                    registered = True;
                    break;
            }
        } else {
            resources.put(key, value);
            registered = True;
        }

        return registered, key.name;
    }

    @Override
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

    @Override
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

    @Override
    ResourceRegistry! copy() {
        return new SimpleResourceRegistry(this);
    }

    @Override
    conditional ResourceRegistry! merge(ResourceRegistry registry, RegistrationBehavior behavior) {
        SimpleResourceRegistry merged = new SimpleResourceRegistry(this);
        if (registry.is(SimpleResourceRegistry)) {
            for (Map.Entry entry : registry.resources.entries) {
                RegistryKey   key   = entry.key;
                RegistryValue value = entry.value;
                if (!merged.registerInternal(key, value.copy(), behavior, key.name)) {
                    return False;
                }
            }
        } else {
            for (RegistryKey key : registry.keys) {
                assert RegistryValue value := registry.get(key);
                if (!merged.registerInternal(key, value.copy(), behavior, key.name)) {
                    return False;
                }
            }
        }
        return True, merged;
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
    Int estimateStringLength()
        = &this.class.name.estimateStringLength() + 2 + resources.estimateStringLength();

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        Class clz = &this.class;
        clz.name.appendTo(buf);
        "(".appendTo(buf);
        resources.appendTo(buf);
        return ")".appendTo(buf);
    }
}
