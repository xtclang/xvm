/**
 * A data structure that is able to provide an immutable copy of itself should implement this
 * interface, allowing the runtime to obtain a immutable version of the data structure when
 * necessary, such as when crossing a service boundary.
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
     * @return True iff the object is Freezable and must be frozen, False iff the object reference
     *         is already frozen (or a service)
     *
     * @throws ConstantRequired if the object is not already frozen, is not a service, and is not
     *         Freezable
     */
    static Boolean requiresFreeze(Object o)
        {
        if (&o.isImmutable || &o.isService)
            {
            return False;
            }

        if (o.is(Freezable))
            {
            return True;
            }

        throw new ConstantRequired();
        }
    }
