import ecstasy.reflect.Return;

/**
 * Return value implementation.
 */
const RTReturn<ReturnType>(Int ordinal, String? name)
        implements Return<ReturnType>
    {
    @Override
    conditional String hasName()
        {
        return name == Null
                ? False
                : (True, name.as(String));
        }
    }