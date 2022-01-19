interface Const
        extends Cloneable
        extends collections.Hashable
        extends Orderable
        extends Stringable
    {
    @Override
    Const clone()
        {
        return this;
        }

    /**
     * The default implementation of comparison for Const implementations is to compare each of
     * the fields.
     */
    static <CompileType extends Const> Ordered compare(CompileType value1, CompileType value2);

    /**
     * Calculate a hash code for a given Const by combining the hash codes for each of the fields.
     *
     * Note: if the `equals` function returns "True" for two distinct objects, this function must
     *       yield the same hash value for both objects.
     */
    static <CompileType extends Const> Int hashCode(CompileType value);

    /**
     * The default implementation of comparison-for-equality for Const implementations is to
     * compare each of the fields for equality.
     */
    static <CompileType extends Const> Boolean equals(CompileType value1, CompileType value2);
    }