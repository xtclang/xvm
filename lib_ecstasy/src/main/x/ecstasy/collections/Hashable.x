/**
 * The [Hashable] interface represents the general capabilities of data types that can produce a
 * hash value, and can be compared for purposes of equality. The general contract for a `Hashable`
 * object is that its hash function is repeatable (stable) in the absence of mutation to the object
 * itself, and that for two objects for which [equals] evaluates to true, the [hashCode] of both
 * objects must be the same.
 *
 * A [Hasher] is an object that represents the functionality of this functional interface, but does
 * so separately from the objects being hashed and compared for equality. When data structures rely
 * on a `Hasher` instead of directly invoking the `Hashable` functions on a type, it allows an
 * alternative implementation of the hashing and equality comparison to be provided. One example is
 * how it's possible to manage [String] values in a case-insensitive manner in either a [HasherMap]
 * or [HasherSet] using the [CaseInsensitive] `Hasher`.
 *
 * Generally, `Hashable` types also tend to be `immutable` types, because an object's [hashCode]
 * should be stable for the lifetime of the object. When using mutable objects in a hashed data
 * structure (such as a [HashMap], [HashSet], [HasherMap], or [HasherSet]), mutations to the hashed
 * objects that can change the [hashCode] will almost certainly cause the data structures to fail in
 * horrible and unpredictable manners. To be safe when using potentially-mutable objects in such a
 * scenario, the hashed data structure must no longer be used (either accessed or mutated) in any
 * way after any mutation occurs to any of the objects hashed by that data structure.
 */
interface Hashable
        extends Comparable {
    /**
     * Calculate a hash code for an object of a given type.
     *
     * Note: if [equals] returns `True` for two objects, this function must yield the same hash
     * value for each of the two objects.
     *
     * @param value  the [Hashable] value to compute a hash-code for
     *
     * @return a hash code for the specified object
     */
    static <CompileType extends Hashable> Int hashCode(CompileType value);

    /**
     * Compare two [Hashable] objects of the same compile-time type for equality.
     *
     * @param value1  the first [Hashable] value to compare
     * @param value2  the second [Hashable] value to compare
     *
     * @return `True` iff the objects are equivalent
     */
    @Override
    static <CompileType extends Hashable> Boolean equals(CompileType value1, CompileType value2);
}