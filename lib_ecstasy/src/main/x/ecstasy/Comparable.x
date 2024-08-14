interface Comparable {
    /**
     * Compare two objects of the same compile-time type for equality.
     *
     * @return True iff the objects are equivalent
     */
    static <CompileType extends Comparable> Boolean equals(CompileType value1, CompileType value2);

    /**
     * The `equals` **method** is a "virtual equals", similar in some ways to OO languages including
     * Java and C#. This concept is only rarely needed or used in Ecstasy, because equality in
     * Ecstasy normally is based on the *compile-time type* of the two objects being compared. This
     * method, on the other hand, uses the *run-time type* of the two objects to select the
     * appropriate `equals` **function** to invoke.
     *
     * @param that  a second value to compare this value to
     *
     * @return True iff this value and the provided value are both of the same class, and if that
     *         class' implementation of [equals] evaluates the two objects to be equal
     */
    Boolean equals(Comparable that) {
        // first, check if this is the same object as the second object
        Ref thisRef = &this;
        Ref thatRef = &that;
        if (thisRef == thatRef) {
            return True;
        }

        // if they're both of the same runtime type, then use that type
        Type shared = thisRef.actualType;
        if (shared == thatRef.actualType) {
            return this.as(shared.DataType) == that.as(shared.DataType);
        }

        // otherwise, verify that they are of the same actual class, and use that class' public type
        shared = thisRef.actualClass.PublicType;
        return this.is(shared.DataType)? == that.is(shared.DataType)? : False;
    }
}