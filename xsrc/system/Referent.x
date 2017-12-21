/**
 * Information about a referent, in other words, about an object that is referred-to.
 */
interface Referent
    {
    /**
     * The actual runtime type of the reference to the referent.
     * <p>
     * From the referent's point of view, the ActualType is the type of <code>this:target</code>.
     * <p>
     * From the referrer's point of view, the ActualType represents the full set of methods that can
     * be invoked against the referent.
     */
    @RO Type ActualType;

    /**
     * Obtain a new reference to the referent such that the reference contains only the methods and
     * properties in the specified {@link Type}. The members of the requested type must be satisfied
     * by the members defined by the object's class. The requested type must be a subset of the
     * referent's {@link ActualType}.
     * <p>
     * This method will result in a reference that only contains the members in the specified type,
     * stripping the runtime reference of any members that are not present in the specified type.
     */
    <AsType> AsType maskAs<AsType>();

    /**
     * Obtain a new reference to the referent such that the reference contains the methods and
     * properties in the specified {@link Type}. The members of the requested type must be satisfied
     * by the members defined by the object's class. The requested type may be a superset of the
     * referent's {@link ActualType}.
     * <p>
     * For a reference to an object from the same module as the caller, this method will return a
     * reference that contains the members in the specified type. For a reference to an object from
     * a different module, this method cannot produce an original reference, and will result in the
     * conditional false.
     */
    <AsType> conditional AsType revealAs<AsType>();

    /**
     * Determine if the referent is an instance of the specified type.
     */
    Boolean instanceOf(Type type);

    /**
     * Determine if the class of the referent implements the specified interface.
     *
     * Note: unlike the {@link instanceOf}, this method doesn't simply check if the referent's class
     * has all methods that the specified interface has. Instead, it returns true iff any of the
     * following conditions holds true:
     *  - the referent's class explicitly declares that it implements the specified interface, or
     *  - the referent's super class implements the specified interface (recursively), or
     *  - any of the interfaces that the referent's class declares to implement extends the
     *    specified interface (recursively)
     */
    Boolean implements_(Class interface_);

    /**
     * Determine if the class of the referent extends (or is) the specified class.
     */
    Boolean extends_(Class class_);

    /**
     * Determine if the class of the referent incorporates the specified mixin.
     */
    Boolean incorporates_(Class mixin_);

    /**
     * Determine if the referent is a service.
     */
    @RO Boolean service_;

    /**
     * Determine if the referent is an immutable const.
     */
    @RO Boolean const_;

    /**
     * Determine if the referent is immutable.
     */
    @RO Boolean immutable_;
    }
