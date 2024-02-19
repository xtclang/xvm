/**
 * A Struct is a simple container of values, each of which is referred to as a _field_.
 *
 * Struct is not intended to be implemented by the developer; rather, it is a means by which the
 * runtime exposes the underlying state of an object to the developer. For example, the `new`
 * operator begins by allocating a Struct that will be used to collect all of the state for the
 * object that is being created, first by passing that Struct to the `construct()` function, and
 * then using the resulting state in the Struct to initialize the new object. Furthermore, an
 * object has a `this:struct` reference to its own internal state.
 *
 * Note that this interface has no properties; that is purposeful. Since this interface is merged
 * with the properties declared by the class being constructed, any property declared here could
 * collide with a property declared by that class -- a condition which must be avoided.
 */
interface Struct {
    /**
     * Represents the mutability of the structure. Once the property has been set to False, the
     * Struct is no longer mutable, and as a result, the property cannot be set to True.
     *
     * @return True iff the Struct is mutable, which indicates if the object references held in its
     *         fields can be modified
     */
    Boolean isMutable();

    /**
     * If this Struct is mutable (as indicated by the result of the `isMutable()` method), then this
     * method will make the Struct immutable. Making the Struct of an object immutable has the
     * effect of making that object immutable.
     *
     * If this Struct is already immutable, this has no effect.
     *
     * @return this Struct, but in an immutable form
     *
     * @throws Unsupported  if the Struct is the structure of a Service; a Service cannot be made
     *         immutable
     */
    immutable Struct freeze();

    /**
     * Calculate the actual amount of memory used by this structure (of an object), including the
     * object's header (if any) and any padding for memory alignment. The calculated size will
     * include the memory required to hold all of the underlying property references of this
     * structure. Additionally, the size will include the sizes of all of the
     * {@link Ref.selfContained self-contained} property references, but explicitly does not include
     * the sizes of the referents for references that are <b>not</b> self-contained.
     *
     * @return the size of this structure, in bytes
     */
    Int calcByteLength();
}
