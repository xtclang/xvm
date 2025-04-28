/**
 * A [Struct] is a simple container of values, each of which is represented by a [Property] of the
 * [Struct], and referred to as a _field_.
 *
 * [Struct] is not intended to be implemented by the developer; rather, it is a means by which the
 * runtime exposes the underlying and internal state of an object to the developer. For example, the
 * `new` operator begins by allocating a [Struct] that will be used to collect all of the state for
 * the object that is being created, first by passing that Struct to the class' constructor named
 * `construct()` (optionally with parameters), and then using the resulting state in the [Struct] to
 * initialize the new object. Furthermore, an object has a `this:struct` reference to its own
 * internal state, and that [Struct] can be [obtained via an object reference](Ref.revealStruct()).
 *
 * Note that this interface declares no properties, and awkwardly uses methods where one would
 * expect to see properties; this is not an accident: Since this interface is
 * merged with the properties declared by the class which it represents, any property declared here
 * could collide with a property declared by that class -- a condition which must be avoided.
 */
interface Struct {
    /**
     * A method (not a property) that indicates the mutability of the [Struct]. Once a [Struct] has
     * been [frozen](freeze), the result from this method will be `False`, and any attempt to store
     * a new value in a property (i.e. "field") of the structure will result in a [ReadOnly]
     * exception.
     *
     * @return `True` iff the [Struct] is mutable, which indicates that the object references held
     *         in the [Struct]'s fields can be replaced with other references
     */
    Boolean isMutable();

    /**
     * If this [Struct] is mutable (as indicated by the result of [isMutable()]), then this method
     * will make the [Struct] immutable. Since the [Struct] represents the state of the object,
     * making the [Struct] of an object immutable has the effect of making that object immutable.
     *
     * If this [Struct] is already immutable, then this method has no effect.
     *
     * @return this [Struct], but in an immutable form
     *
     * @throws Unsupported  if the [Struct] is the structure of a `service` (services cannot be made
     *         immutable) or of any mutable object that is not within the current service
     */
    immutable Struct freeze();

    /**
     * Calculate the actual amount of memory used by this structure (of an object), including the
     * object's header (if any) and any padding for memory alignment. The calculated size will
     * include the memory required to hold all of the underlying property references of this
     * structure. Additionally, the size will include the sizes of all of the
     * [self-contained](Ref.selfContained) property references, but explicitly does not include
     * the sizes of the referents for references that are <b>not</b> self-contained.
     *
     * @return the size of this structure, in bytes
     */
    Int calcByteSize();
}
