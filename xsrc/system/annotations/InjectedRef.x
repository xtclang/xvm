/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * no later than the point at which the reference comes "into scope".
 */
mixin InjectedRef<RefType>(String resourceName, Object? opts = null)
        into Ref<RefType>
    {
    }
