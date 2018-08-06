/**
 * A Ref represents a _reference_ to an Ecstasy object. In Ecstasy, "everything is an object", and
 * the only way that one can interact with an object is through a reference to that object. The
 * _referent_ is the object being referred to; the _reference_ (encapsulated in and represented by a
 * Ref object) is the object that refers to the referent.
 *
 * An Ecstasy reference is conceptually composed of two pieces of information:
 * * A _type_;
 * * An _identity_.
 *
 * The type portion of an Ecstasy reference, represented by the _actualType_ property of the Ref, is
 * simply the set of operations that can be invoked against the referent and the set of properties
 * that it contains. Regardless of the actual operations that the referent object implements, only
 * those present in the type of the reference can be invoked through the reference. This allows
 * references to be purposefully narrowed; an obvious example is when an object only provides a
 * reference to its _public_ members.
 *
 * The Ref also has a RefType property, which is its _type constraint_. For example, when a Ref
 * represents a compile time concept such as a _variable_ or a _property_, the RefType is the
 * _compile time type_ of the reference. The reference may contain additional operations at runtime;
 * the actualType is always a super-set (⊇) of the RefType.
 *
 * The identity portion of an Ecstasy reference is itself unrepresentable in Ecstasy. In fact, it is
 * this very unrepresentability that necessitates the Ref abstraction in the first place. For
 * example, the identity may be implemented as a pointer, which points to an address in memory at
 * which the state of the object is stored. However, that address could be located on the process'
 * program stack, or allocated via a dynamic memory allocation, or could point into a particular
 * element of an array or a structure that itself is located on the program stack or allocated via
 * a dynamic memory allocation. Or the identity could be a handle, adding a layer of indirection to
 * each of the above. Or the identity could itself _be_ the object, as one would expect for the
 * simplest (the most primitive) of types, such as booleans, bytes, characters, and integers.
 *
 * To allow the Ecstasy runtime to provide the same behavioral guarantees regardless of how objects
 * are allocated and managed, how they are addressed, and how house-keeping activities potentially
 * affect all of the above, the Ref provides an opaque abstraction that hides the actual identity
 * (and thus the actual underlying implementation) from the program and from the programmer.
 *
 * Because it is impossible to represent the identity in Ecstasy, the Ref type is itself simply an
 * interface; the actual Ref instances used for parameters, variables, properties, array elements,
 * and so on, are provided by the runtime itself, and exposed to the running code via this
 * interface.
 */
interface Ref<RefType>
        extends Referent
    {
    /**
     * Determine if there is a referent. In most cases, it is impossible for a Ref to not be
     * assigned, but there are specific cases in which a reference may not have a referent,
     * including:
     *
     * * Future return values;
     * * Conditional return values;
     * * Uninitialized properties in an object structure during construction;
     * * Lazy references that have not yet lazily populated;
     * * Soft or weak references that have had their referents collected.
     */
    @RO Boolean assigned;

    /**
     * Conditionally dereference the reference to obtain the referent, iff the reference is
     * assigned; otherwise return false.
     *
     * A small number of references cannot be blindly dereferenced without risking a runtime
     * exception:
     * * {@code @Lazy} references ({@link annotations.LazyVar}) are allowed to be unassigned,
     *   because they will lazily assign themselves on the first dereference attempt.
     * * {@code @Future} references ({@link annotations.FutureVar}) are allowed to be unassigned,
     *   because they assigned only on completion of the future, and an attempt to dereference
     *   before that point in time will block until that completion occurs.
     * * {@code @Soft} and {@code @Weak} references ({@link annotations.SoftVar} and {@link
     *   annotations.WeakRef}) are allowed to be unassigned, because the garbage collector is
     *   allowed under specific conditions to clear the reference.
     */
    conditional RefType peek()
        {
        if (assigned)
            {
            return True, get();
            }

        return False;
        }

    /**
     * De-reference the reference to obtain the referent.
     */
    RefType get();

    /**
     * Obtain the actual runtime type of the reference that this Ref currently holds. The actualType
     * represents the full set of methods that can be invoked against the referent, and is always a
     * super-set of the RefType:
     *
     *   actualType ⊇ RefType
     *
     * (The RefType denotes the constraint of the reference, i.e. the reference must "be of" the
     * RefType, but is not limited to only having the methods of the RefType; the RefType is often
     * the _compile-time type_ of the reference.)
     */
    @Override
    @RO Type actualType;

    /**
     * REVIEW: should we move this function to the Referent interface?
     *
     * Reference equality is used to determine if two references are referring to the same referent
     * _identity_. Specifically, two references are equal iff they reference the same runtime
     * object, or the two objects that they reference are both immutable and structurally identical.
     *
     * Because the reference identity is impossible to represent in Ecstasy, the actual
     * implementation of this function is also impossible to represent in Ecstasy, hence the
     * implementation may appear to be infinitely recursive.
     */
    static <CompileType extends Ref> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1 == value2;
        }

    /**
     * The optional name of the reference. References are used for arguments, local variables,
     * object properties, constant pool values, array elements, fields of structures, elements of
     * tuples, and many other purposes; in some of these uses, it is common for a reference to be
     * named. For example, arguments, local variables, struct fields, and properties are almost
     * always named, but tuple elements are often not named, and array elements are never named.
     */
    @RO String? refName;

    /**
     * The reference uses a number of bytes for its own storage; while the size of the reference is
     * not expected to dynamically change, reference sizes may vary from one reference to another.
     * References may be larger than expected, because references may include additional information
     * -- and potentially even the entire referent -- within the reference itself.
     */
    @RO Int byteLength;

    /**
     * Determine if the reference is completely self-contained, in that the referent is actually
     * embedded within the reference itself.
     */
    @RO Boolean selfContained;
    }
