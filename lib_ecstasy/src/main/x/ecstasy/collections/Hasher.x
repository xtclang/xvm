/**
 * A Hasher is an object that calculates hash values and evaluates relational equality on behalf
 * of another type. A Hasher's purpose is to allow hashed data structures to delegate the knowledge
 * of hashing and equality to an externalized object (a "hasher") that encapsulates that knowledge
 * for a particular data type.
 *
 * A broad range of Ecstasy types provide their own built-in hashing and equality implementations,
 * and most of the time that is the implementation that a developer will want to use. For example,
 * all `const` types automatically provide both a `hash` property and an `equals`
 * implementation. Given that many types are already hashable and comparable, the purpose a Hasher
 * is two-fold:
 * * A Hasher allows a developer to override the hashing calculation and the equality operator for
 *   a particular type when storing objects of that type in a hashed data structure.
 * * A Hasher allows a developer to implement both the hashing calculation and the equality operator
 *   for a particular type that does not implement those itself, for example when dealing with
 *   objects whose types are (for whatever reason) not modifiable by the developer, but which
 *   objects need to be managed in a hashed data structure.
 *
 * The expected implementation pattern with Hasher is that a hashed data structure will accept a
 * Hasher that supports the type of the hashed value that the data structure is managing, and that
 * a sub-class of the data structure will narrow the supported type of value to an `immutable
 * Hashable` or `const` type, simply by internally utilizing a [NaturalHasher].
 *
 * @see NaturalHasher
 */
interface Hasher<Value> {
    /**
     * Calculate the hash value for the specified value.
     *
     * The general contract is that this method is idempotent and stable, always providing the same
     * hash value for the same specified value. Further, if two objects `areEqual` by the
     * determination of this Hasher, then both object **must** have the same `hashOf` value.
     * (The opposite is not true: Two objects that have the same `hashOf` value are not
     * required to evaluate as `areEqual` of `True`.
     */
    Int64 hashOf(Value value);

    /**
     * Determine the equality (the sameness) of two specified values.
     *
     * The general contract is that this method determines the equality of `value1` and
     * `value2` in a reflexive, commutative, and transitive manner. Further, if two objects
     * `areEqual` by the determination of this Hasher, then both object **must** have the same
     * `hashOf` value.
     */
    Boolean areEqual(Value value1, Value value2);
}