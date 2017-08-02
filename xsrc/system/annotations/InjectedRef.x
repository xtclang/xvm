/**
 * An InjectedRef is used to make an immutable reference that is injected with a value
 * when the reference comes "in scope".
 */
mixin InjectedRef<RefType>
        into Ref<RefType>
    {
    RefType get()
        {
        assert (assigned);

        return super();
        }

    Void set(RefType value)
        {
        assert(false);
        }
    }
