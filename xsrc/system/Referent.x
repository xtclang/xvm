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
    @ro Type ActualType;

    /**
     * Obtain a new reference to the referent such that the reference contains the methods and
     * properties in the specified {@link Type}. The members of the requested type must be satisfied
     * by the members defined by the object's class.
     * <p>
     * A caller from within the same module as the class of the referent can use this method to
     * obtain a new reference to the referent, as long as the referent's class can satisfy the
     * members of the requested type. In other words, the requested type can represent any
     * combination of added and removed members, allowing an arbitrarily narrowed or widened to be
     * requested.
     * <p>
     * A caller from outside of the module of the class of the referent can only use this method to
     * obtain a reference with a subset of the members in the reference that they are holding.
     *
     * @throws TODO if the requested type cannot be satisfied by the object's class, or if the
     *              caller does not have standing to request the specified type
     */
    @ro asType as(Type asType);

    /**
     * Determine if the referent is an instance of the specified type.
     */
    Boolean instanceOf(Type type);

    /**
     * Determine if the class of the referent implements the specified interface.
     */
    Boolean implements(Class interface);

    /**
     * Determine if the class of the referent extends (or is) the specified class.
     */
    Boolean extends(Class class);

    /**
     * Determine if the class of the referent incorporates the specified trait or mixin.
     */
    Boolean incorporates(Class traitOrMixin);

    /**
     * Determine if the referent is a service.
     */
    @ro Boolean isService;

    /**
     * Determine if the referent is an immutable const.
     */
    @ro Boolean isConst;

    /**
     * Determine if the referent is immutable.
     */
    @ro Boolean immutable;
    }
