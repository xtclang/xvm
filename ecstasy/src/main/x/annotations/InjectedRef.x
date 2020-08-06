/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * no later than the point at which the reference comes "into scope".
 */
mixin InjectedRef<Referent>(String resourceName, Object? opts = Null)
        into Ref<Referent>
    {
    }
