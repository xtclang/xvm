/**
 * Any "container" data structure that is able to provide an immutable copy of itself should
 * implement this interface, allowing the runtime to non-destructively obtain a immutable version
 * of the data structure when and as necessary, such as when crossing a service boundary.
 */
interface Freezable {
    /**
     * Obtain an immutable reference to this value, either by creating an immutable form of this
     * object, or by making this object immutable.
     *
     * @param inPlace  (optional) specify that the object should make itself immutable if it can,
     *                 instead of creating a new immutable copy of itself
     */
    immutable Freezable freeze(Boolean inPlace = False);

    /**
     * Determine if the specified [Shareable] object needs to be frozen to be [Passable].
     *
     * @param object  the [Shareable] object to evaluate
     *
     * @return `True` iff the object is [Freezable] and must be frozen; `False` iff the object
     *         reference is already [Passable] (either `immutable` or a `service`)
     * @return (conditional) the [Freezable] object reference to [freeze]
     */
    static <Any extends Shareable> conditional Freezable+Any requiresFreeze(Any object) {
        return object.is(Passable)
                ? False
                : (True, object);
    }

    /**
     * Freeze the provided [Shareable] object if it needs to be frozen to be [Passable].
     *
     * @param object   the [Shareable] object to evaluate
     * @param inPlace  (optional) specify that if the object needs to [freeze], that it should make
     *                 itself immutable if it can, instead of creating a new immutable copy of
     *                 itself
     *
     * @return a [Passable] object (either `immutable` or a `service`)
     */
    static <Any extends Shareable> Passable+Any frozen(Any object, Boolean inPlace=False) {
        return object.is(Passable)
                ? object
                : object.freeze(inPlace);
    }
}