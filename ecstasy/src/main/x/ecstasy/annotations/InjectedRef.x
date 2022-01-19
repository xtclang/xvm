/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * no later than the point at which the reference comes "into scope".
 */
 mixin InjectedRef<Referent>(String? resourceName = Null, Options opts = Null)
        into Ref<Referent>
    {
    typedef immutable Object? as Options;
    }
