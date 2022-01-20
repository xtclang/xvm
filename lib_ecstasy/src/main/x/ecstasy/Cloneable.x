/**
 * A class that is able to provide a deep clone (a complete copy) of itself should implement this
 * interface.
 *
 * An immutable object is allowed to return itself as its own clone; there is generally no benefit
 * from actually cloning immutable objects.
 */
interface Cloneable
    {
    /**
     * Obtain a clone of this Cloneable object. Note that an immutable object is considered a clone
     * of itself.
     *
     * @return a clone of this object, or this object iff this object is immutable
     */
    Cloneable clone();
    }
