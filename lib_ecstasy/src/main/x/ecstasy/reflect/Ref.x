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
 * The Ref also has a Referent property, which is its _type constraint_. For example, when a Ref
 * represents a compile time concept such as a _variable_ or a _property_, the Referent is the
 * _compile time type_ of the reference. The reference may contain additional operations at runtime;
 * the actualType is always a super-set (⊇) of the Referent.
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
interface Ref<Referent> {
    /**
     * De-reference the reference to obtain the referent.
     */
    Referent get();

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
     * assigned; otherwise return False.
     *
     * A small number of references cannot be blindly dereferenced without risking a runtime
     * exception:
     * * `@Lazy` references ({@link annotations.LazyVar}) are allowed to be unassigned,
     *   because they will lazily assign themselves on the first dereference attempt.
     * * `@Future` references ({@link annotations.FutureVar}) are allowed to be unassigned,
     *   because they assigned only on completion of the future, and an attempt to dereference
     *   before that point in time will block until that completion occurs.
     * * `@Soft` and `@Weak` references ({@link annotations.SoftVar} and {@link
     *   annotations.WeakRef}) are allowed to be unassigned, because the garbage collector is
     *   allowed under specific conditions to clear the reference.
     */
    conditional Referent peek() {
        if (assigned) {
            return True, get();
        }

        return False;
    }

    /**
     * Obtain the actual runtime type of the reference that this Ref currently holds. The actualType
     * represents the full set of methods that can be invoked against the referent, and is always a
     * super-set of the Referent:
     *
     *   actualType ⊇ Referent
     *
     * (The Referent denotes the constraint of the reference, i.e. the reference must "be of" the
     * Referent, but is not limited to only having the methods of the Referent; the Referent is
     * often the _compile-time type_ of the reference.)
     */
    @RO Type<Referent> actualType;

    /**
     * Obtain the class of the referent. If the reference is masked, and the caller is not permitted
     * to reveal the type of the referent, then the `actualClass` is the class of the interface type
     * used to mask the reference, and not the class of the referent itself.
     */
    @RO Class actualClass;

    /**
     * Obtain an opaque object that represents the identity of the reference held by this Ref. The
     * identity can be used for comparison with other identities to determine whether two identities
     * originate from the same object.
     *
     * As long as the reference to the `Identity` is held, any subsequent requests for an identity
     * from the same underlying object will provide an `Identity` that yields the same result from
     * the `hashCode()` function. If the Identity is permitted to be garbage-collected, subsequent
     * requests for an identity from the same underlying object _may_ provide an `Identity` that
     * yields a different result from the `hashCode()` function; in other words, the hash code is
     * treated as ephemeral data.
     */
    @RO Identity identity;

    /**
     * Obtain a new reference to the referent such that the reference contains only the methods and
     * properties in the specified {@link Type}. The members of the requested type must be satisfied
     * by the members defined by the object's class. The requested type must be a subset of the
     * referent's [actualType].
     *
     * This method will result in a reference that only contains the members in the specified type,
     * stripping the runtime reference of any members that are not present in the specified type.
     *
     * @param maskType  the type to mask the contents of this reference as
     *
     * @return a reference of the desired type
     */
    <Masked> Masked maskAs<Masked>(Type<Masked> maskType);

    /**
     * Obtain a new reference to the referent such that the reference contains the methods and
     * properties in the specified {@link Type}. The members of the requested type must be satisfied
     * by the members defined by the object's class. The requested type may be a superset of the
     * referent's {@link actualType}.
     *
     * For a reference to an object instantiated within the `Current` or `Inner` [zone], this method
     * will return a reference of the requested type, iff the underlying object is actually of that
     * specified type. For a reference of an `Outer` zone, this method will return `False`.
     *
     * @param revealType  the type to reveal the contents of this reference as
     *
     * @return True iff the desired type is implemented by the underlying object, and the [zone] of
     *         this reference is [Zone.Current] or [Zone.Inner]
     * @return (conditional) a reference of the desired type
     */
    <Unmasked> conditional Unmasked revealAs(Type<Unmasked> revealType);

    /**
     * Each object exists within a container, and a `Zone` describes the relationship between the
     * referent object's container (the container within which it executes any of its operations)
     * and the current container:
     *
     * * Current - the referent exists inside of the current container;
     * * Inner   - the referent exists within a container created within the current container;
     * * Outer   - the referent exists within a container that created the current container, but
     *             **not** within the current container;
     *
     * Each `Zone` implies a variation in _trust_. For the purpose of security, a container must not
     * trust references from `Inner` containers.
     *
     * Object references from an `Outer` container are necessarily opaque to code executing in the
     * `Current` container; it is not possible to use [revealAs] against `Outer` references.
     */
    enum Zone {Current, Inner, Outer}

    /**
     * The [Zone] of the referent in relation to the `Current` container.
     */
    @RO Zone zone;

    /**
     * Specifies whether the referent is local to [this:service].
     *
     * Determine if the referent will execute its operations _locally_ within the realm of the
     * current [Service], [this:service]. If operations against the reference must be executed by
     * another service (i.e. either by proxying the execution request, or by some other means of a
     * service context change), then the value of this property will be `False`.
     */
    @RO Boolean local;

    /**
     * Determine if the referent is an "instance of" the specified type.
     *
     * @param type  the type to test for
     *
     * @return True iff the referent is of the specified type
     */
    Boolean instanceOf(Type type) {
        if (peek()) {
            return actualType.isA(type);
        }

        return False;
    }

    /**
     * Determine if the referent is a `service`.
     */
    @RO Boolean isService.get() {
        if (actualType.is(Type<Service>)) {
            return True;
        }

        Referent referent = get();
        return referent.is(Inner) && referent.&outer.isService;
    }

    /**
     * Determine if the referent is an `immutable const`.
     */
    @RO Boolean isConst.get() {
        return actualType.is(Type<immutable Const>);
    }

    /**
     * Determine if the referent is `immutable`.
     */
    @RO Boolean isImmutable.get() {
        return actualType.is(Type<immutable Object>);
    }

    /**
     * The optional name of the reference. References are used for arguments, local variables,
     * object properties, constant pool values, array elements, fields of structures, elements of
     * tuples, and many other purposes; in some of these uses, it is common for a reference to be
     * named. For example, arguments, local variables, struct fields, and properties are almost
     * always named, but tuple elements are often not named, and array elements are never named.
     *
     * @return True iff the reference has a name
     * @return (conditional) the name associated with the reference
     */
    conditional String hasName();

    /**
    * The [Property] corresponding to this reference, if this is a reference to a property value.
    */
    conditional (Property<Object, Referent, Ref<Referent>>, Object) isProperty();

    /**
    * The reference annotations. These are the annotations that apply to the reference itself (i.e.
    * they mix into `Ref` or `Var`), such as `@Future` and `@Lazy`. The order of the annotations in
    * the array is "left-to-right"; so for example an annotated Var:
    *     @A1 @A2 List list = ...
    * would produce the `annotations` array holding `A1` at index zero.
    */
    @RO Annotation[] annotations;

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

    /**
     * Reference equality is used to determine if two references are referring to the same referent
     * _identity_. Specifically, two references are equal if they reference the same runtime
     * object. Additionally, for optimization purposes, the runtime is *permitted* to indicate that
     * two references to two separate runtime objects are equal, in the case where both references
     * are to immutable objects whose structures are identical.
     */
    static <CompileType extends Ref> Boolean equals(CompileType value1, CompileType value2) {
        return value1.identity == value2.identity;
    }

    /**
     * A reference identity is an object that performs three tasks:
     *
     * * It prevents the reference from being garbage collected;
     * * It provides a hash code for the reference;
     * * It provides comparison of any two references.
     */
    static interface Identity
            extends immutable Hashable {
        @Override
        static <CompileType extends Identity> Int64 hashCode(CompileType value) {
            // the implementation of the Identity hash code is naturally self-referential; this code
            // cannot work in actuality; see also: infinite recursion
            return value.hashCode();
        }

        @Override
        static <CompileType extends Identity> Boolean equals(CompileType value1, CompileType value2) {
            // the implementation of the Identity equality is naturally self-referential; this code
            // cannot work in actuality; see also: infinite recursion
            return value1 == value2;
        }
    }
}