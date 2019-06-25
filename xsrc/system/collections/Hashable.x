interface Hashable
    {
    /**
     * Calculate a hash code for an object of a given type.
     *
     * Note: if the `equals` function returns "True" for two distinct objects, this function must
     *       yield the same hash value for both objects.
     */
    static <CompileType extends Hashable> Int hashCode(CompileType value);

    /**
     * Compare two objects of the same Hashable type for equality.
     *
     * @return true iff the objects are equivalent
     */
    static <CompileType extends Hashable> Boolean equals(CompileType value1, CompileType value2);
    }