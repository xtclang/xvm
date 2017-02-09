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
     * be invoked against the referent by the holder of the reference.
     */
    @ro Type ActualType;

    /**
     * Obtain a new reference to the referent such that the reference contains the methods and
     * properties in the specified {@link Type}.
     * <p>
     * A caller from within the same module as the class of this object can use this method to
     * obtain a new reference to the referent which contains .. TODO add
     * and/or remove methods and properties from a reference. widen
     * a reference. Callers from outside of the same module can only narrow the reference, so that
     * it contains only the methods in the specified type, stripping the runtime reference of any
     * methods that are not present in the specified type.
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
     * Determine if the referent is an asynchronous service.
     */
    @ro Boolean isAsync;

    /**
     * Determine if the referent is an immutable const.
     */
    @ro Boolean isConst;

    /**
     * Determine if the referent is immutable.
     */
    @ro Boolean immutable;
    }
