interface Comparable
    {
    /**
     * Compare two objects of the same compile-time type for equality.
     *
     * @return True iff the objects are equivalent
     */
    static <CompileType extends Comparable> Boolean equals(CompileType value1, CompileType value2);
    }