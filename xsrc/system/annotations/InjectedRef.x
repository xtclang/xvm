/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * when the reference comes "in scope".
 */
mixin InjectedRef<ResourceType>(String resourceName, Object? opts = null)
        into Property<Object, ResourceType> | Ref<ResourceType>
    {
    }
