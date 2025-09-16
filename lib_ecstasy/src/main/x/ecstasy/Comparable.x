/**
 * The Comparable interface represents the general capabilities of data types that can be compared
 * for purposes of equality. Its primary function is [equals], which yields a [True] iff two values
 * are equal.
 */
interface Comparable {
    /**
     * Compare two objects of the same compile-time type for equality.
     *
     * @param value1  the first value to compare
     * @param value2  the second value to compare
     *
     * @return `True` iff the objects are equivalent
     */
    static <CompileType extends Comparable> Boolean equals(CompileType value1, CompileType value2);

    /**
     * This `equals` **method** behaves as a "virtual equals", _similar_ to how other OO languages
     * implement object equality. This concept is only rarely needed or used in Ecstasy, because equality in
     * Ecstasy normally is based on the *compile-time type* of the two objects being compared. This
     * method, on the other hand, uses the *run-time type* of the two objects to select the
     * appropriate [equals] **function** to invoke.
     *
     * @param that  a second value to compare this value to
     *
     * @return True iff this value and the provided value are both of the same type or class, and
     *         that type's or class' [equals](equals(CompileType, CompileType)) function returns
     *         `True`
     */
    Boolean equals(Comparable that) {
        // first, check if this is the same object as the second object
        if (&this == &that) {
            return True;
        }

        // if they're both of the same runtime type, then use that type
        Type runtimeType = &this.type;
        if (runtimeType == &that.type) {
            return this.as(runtimeType.DataType) == that.as(runtimeType.DataType);
        }

        // otherwise, verify that they are of the same actual class, and use that class' public type
        if (&this.class == &that.class) {
            Type classType = &this.class.PublicType;
            return this.as(classType.DataType) == that.as(classType.DataType);
        }

        return False;
    }
}