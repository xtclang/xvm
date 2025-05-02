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
 * The type portion of an Ecstasy reference, represented by the [type] property of the [Ref], is
 * simply the set of operations that can be invoked against the [Referent] and the set of properties
 * that it contains. Regardless of the actual operations that the [Referent] object implements, only
 * those present in the type of the reference can be invoked through the reference. This allows
 * references to be purposefully narrowed; an obvious example is when an object only provides a
 * reference to its _public_ members.
 *
 * The Ref also has a Referent property, which is its _type constraint_. For example, when a Ref
 * represents a compile time concept such as a _variable_ or a _property_, the Referent is the
 * _compile time type_ of the reference. The reference may contain additional operations at runtime;
 * the [type] is always a super-set (⊇) of the Referent.
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
     * * `@Lazy` references ([annotations.LazyVar]) are allowed to be unassigned,
     *   because they will lazily assign themselves on the first dereference attempt.
     * * `@Future` references ([annotations.FutureVar]) are allowed to be unassigned,
     *   because they assigned only on completion of the future, and an attempt to dereference
     *   before that point in time will block until that completion occurs.
     * * `@Soft` and `@Weak` references ([annotations.SoftVar] and [annotations.WeakRef]) are
     *   allowed to be unassigned, because the garbage collector is allowed under specific
     *   conditions to clear the reference.
     */
    conditional Referent peek() {
        if (assigned) {
            return True, get();
        }

        return False;
    }

    /**
     * Obtain the actual runtime [Type] of the reference that this [Ref] currently holds. The [type]
     * represents the full set of methods that can be invoked against the [Referent], and is always
     * a super-set of the [Referent]:
     *
     *     type ⊇ Referent
     *
     * (The [Referent] denotes the constraint of the reference, i.e. the reference must "be of" the
     * [Referent], but is not limited to only having the methods of the [Referent]; the [Referent]
     * is often the _compile-time [Type]_ of the reference.)
     */
    @RO Type<Referent> type;

    /**
     * Obtain the class of the referent. If the reference is masked, and the caller is not permitted
     * to reveal the type of the referent, then the `class` is the class of the interface type
     * used to mask the reference, and not the class of the referent itself.
     */
    @RO Class class;

    /**
     * Obtain an opaque object that represents the identity of the reference held by this [Ref]. An
     * [Identity] can be compared with another [Identity] to determine whether the two identities
     * originate from the same object.
     *
     * It is _possible_ for two separately created objects to yield the same reference [Identity],
     * iff the objects possess identical immutable [Struct]s. The [Struct]s are considered identical
     * iff the [Ref] in each and every field in the first [Struct] yields the same [Identity] as the
     * [Ref] in the corresponding field in the second [Struct]. This reference [Identity] sharing
     * behavior is never guaranteed, but it should not be surprising in the case of [selfContained]
     * references, and it is also _possible_ in references that are **not** [selfContained]. The
     * rationale is that two deeply immutable structures that are "bitwise identical" can safely
     * share a reference [Identity], and there space and time efficiencies for doing so.
     *
     * As long as a reference to the [Identity] of an [Object] is held, the [Object] will not be
     * garbage-collected, and any subsequent request for an [Identity] from the same underlying
     * [Object] will provide an [Identity] that yields the same [hashCode()](Identity.hashCode). If
     * the last [Identity] for an [Object] is permitted to be garbage-collected, subsequent requests
     * for an [Identity] from that same [Object] _may_ provide a different opaque [Identity]
     * instance, and that [Identity] _may_ produces a different [hashCode()](Identity.hashCode); in
     * other words, the [Identity] for an [Object] and the [hashCode()](Identity.hashCode) of that
     * [Identity] must be assumed to be stable only for the lifetime of the [Identity] instance
     * itself, as if an [Object] retains its [Identity] using a [Weak] reference.
     */
    @RO Identity identity;

    /**
     * Obtain a new reference to the referent such that the reference contains only the methods and
     * properties in the specified [Type]. The members of the requested type must be satisfied
     * by the members defined by the object's class. The requested type must be a subset of the
     * referent's [type].
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
     * properties in the specified [Type]. The members of the requested type must be satisfied by
     * the members defined by the object's class. The requested type may be a superset of the
     * referent's [type].
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
    <Unmasked> conditional Referent+Unmasked revealAs(Type<Unmasked> revealType);

    /**
     * Obtain the [Struct] of the referenced [Object] (aka the [Referent]), if possible. The
     * [Struct] provides access to the internal state of the [Object], sometimes referred to as the
     * "fields" of the [Object].
     *
     * For a [Ref] to a mutable [Object], the [Struct] can only be revealed if the [Object] belongs
     * to (i.e. was instantiated within) the current [Service]. For a [Ref] to an immutable
     * [Object], the [Struct] can only be revealed if the `private` type of the [Object] can be
     * revealed by the [revealAs] method.
     *
     * @return True iff the referred-to [Object]'s structure is of `SpecificType`, and the [Struct]
     *         of the referred-to [Object] is allowed to be revealed
     * @return (conditional) the [Struct] of the referred-to [Object]
     */
    <SpecificStruct extends Struct> conditional SpecificStruct revealStruct();

    /**
     * Each object exists within a container, and a `Zone` describes the relationship between the
     * referent object's container (the container within which it executes any of its operations)
     * and the current container:
     *
     * * Current - the referent exists inside of the current container;
     * * Inner   - the referent exists within a container created within the current container;
     * * Outer   - the referent exists within a container that created the current container, but
     *             **not** within the current container;
     * * Other   - the referent exists within a container that is not related to (not in the lineage
     *             of) the current container
     *
     * Each `Zone` implies a variation in _trust_. For the purpose of security, a container must not
     * trust references from `Inner` containers.
     *
     * Object references from an `Outer` container are necessarily opaque to code executing in the
     * `Current` container; it is not possible to use [revealAs] against `Outer` references.
     */
    enum Zone {Current, Inner, Outer, Other}

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
    * they mix into [Ref] or [Var]), such as [@Future](FutureVar) and [@Lazy](LazyVar). The order of
    * the annotations in the array is "left-to-right"; so for example an annotated Var:
    *
    *     @A1 @A2 List list = ...
    *
    * would produce the `annotations` array holding `A1` at index zero.
    */
    @RO Annotation[] annotations;

    /**
     * The reference uses a number of bytes for its own storage; while the size of the reference is
     * not expected to dynamically change, reference sizes may vary from one reference to another.
     * References may be larger than expected, because references may include additional information
     * -- and potentially even the entire `Referent` -- within the reference itself.
     */
    @RO Int size;

    /**
     * `True` iff the reference is completely self-contained, in that the `Referent` (the [Object],
     * including it state as represented by its [Struct]) is actually embedded within the reference
     * itself.
     */
    @RO Boolean selfContained;

    /**
     * Reference equality is used to determine if two references are referring to the same referent
     * _identity_. Specifically, two references are equal if they reference the same runtime
     * object. Additionally, for optimization purposes, the runtime is *permitted* to indicate that
     * two references to two separate runtime objects are equal, in the case where both references
     * are to immutable objects whose structures are identical.
     */
    @Override
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