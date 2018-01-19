/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * when the reference comes "in scope".
 */
mixin InjectedRef<RefType>(Type type, String name, Object? opts = null)
        into Ref<RefType>
    {
    }
