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
    }
