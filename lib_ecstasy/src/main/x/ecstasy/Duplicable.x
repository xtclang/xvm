/**
 * A Duplicable object is any that is capable of producing new, duplicate copies of itself. The
 * Duplicable is the interface for _"virtual new, using the copy constructor"_. The copying behavior
 * is defined as a "shallow copy".
 *
 * An immutable object is considered to be a duplicate of itself; there is generally no benefit from
 * actually duplicating an immutable object.
 */
interface Duplicable {
    /**
     * Construct a new duplicate of this object. This new, duplicated copy is assumed to be a
     * _shallow copy_ of the original; a shallow copy will likely share references (to other
     * objects) with the original object that the copy was duplicated from.
     *
     * This is a virtual "copy constructor".
     *
     * @param that  the `Duplicable` object to duplicate from
     */
    construct(Duplicable that);

    /**
     * Produce a duplicate copy of this object. The new, duplicated copy is assumed to be a _shallow
     * copy_; a shallow copy will likely share references (to other objects) with the original
     * `Duplicable` object that the copy was duplicated from.
     *
     * An immutable object is allowed to return itself as its duplicate; there is generally no
     * benefit from duplicating immutable objects.
     *
     * @return a shallow copy of `this`
     */
    Duplicable duplicate() {
        return this.is(immutable)
                ? this
                : this.new(this);
    }
}
