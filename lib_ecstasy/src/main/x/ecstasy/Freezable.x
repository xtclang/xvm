/**
 * Any "container" data structure that is able to provide an immutable copy of itself should
 * implement this interface, allowing the runtime to non-destructively obtain a immutable version
 * of the data structure when and as necessary, such as when crossing a service boundary.
 */
interface Freezable
    {
    /**
     * Obtain an immutable reference to this value, either by creating an immutable form of this
     * object, or by making this object immutable.
     *
     * @param inPlace  (optional) specify that the object should make itself immutable if it can,
     *                 instead of creating a new immutable copy of itself
     */
    immutable Freezable freeze(Boolean inPlace = False);

    /**
     * Determine if the passed object needs to be frozen.
     *
     * @param object  the object to evaluate
     *
     * @return True iff the object is Freezable and it must be frozen to become Shareable; False iff
     *         the object is already immutable or a service
     *
     * @throws NotShareable if the object is not already immutable (i.e. it has not yet been frozen),
     *         is not a `service`, and is not `Freezable`
     */
    static <AnyType> conditional AnyType+Freezable requiresFreeze(AnyType object)
        {
        // TODO GG: check for (immutable Clz) fails
//        if (object.is(immutable | service))
//            {
//            return False;
//            }

        if (object.is(immutable) || object.is(service))
            {
            return False;
            }

        if (object.is(Freezable))
            {
            return True, object;
            }

        throw new NotShareable();
        }

    /**
     * Freeze the passed object if it needs to be frozen in order to be passed to another service.
     *
     * @param object   the object to evaluate
     * @param inPlace  (optional) specify that the object should make itself immutable if it can,
     *                 instead of creating a new immutable copy of itself
     *
     * @return True iff the object is Freezable and must be frozen, False iff the object reference
     *         is already frozen (or a service)
     *
     * @throws NotShareable if the object is not already immutable (i.e. it has not yet been frozen),
     *         is not a `service`, and is not `Freezable`
     */
    static <AnyType> AnyType+Shareable frozen(AnyType object, Boolean inPlace=False)
        {
        if (val notYetFrozen := requiresFreeze(object))
            {
            return notYetFrozen.freeze(inPlace);
            }

        return object.as(Shareable);
        }
    }