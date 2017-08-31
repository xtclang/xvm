/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * when the reference comes "in scope".
 * TODO provide compile-time behavior in addition to runtime mixin behavior
 * TODO provide an ability to define what can be injected into an app (as a compiler plug-in)
 * TODO provide a way to formalize the optional configuration of an injection
 */
mixin InjectedRef<RefType>(Type type, String name, Object? opts = null)
        into Ref<RefType>
    {
    RefType get()
        {
        assert assigned;
        return super();
        }

    Void set(RefType value)
        {
        assert false;
        }
    }
