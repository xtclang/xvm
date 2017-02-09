/**
 * Information about a referent, in other words, about an object that is referred-to.
 */
interface Referent
    {
    /**
     * The actual runtime type of the reference to the referent.
     * <p>
     * From the referent's point of view, the ActualType is the type of <code>this:target</code>.
     * From the referrer's point of view, the ActualType represents the full set of methods that can be invoked against the referent
     * by the holder of the reference.
     */
    @ro Type ActualType;

    /**
     * Obtain a new reference such that it contains the methods in the specified type.
     * <p>
     * For any reference, this method will narrow the reference so that it contains
     * only the methods in the specified type. This strips the runtime reference of
     * any methods that are not present in the specified type.
     * <p>
     * For a reference to an object from the same module as the caller, this method
     * allows the reference to be widened as well.
     */
    @ro asType as(Type asType);

    /**
     * Determine if the referent is an instance of the specified type.
     */
    Boolean instanceOf(Type type);

    /**
     * Determine if the class of the referent implements the specified interface.
     */
    Boolean implements(Class clz);

    /**
     * Determine if the class of the referent extends (or is) the specified class.
     */
    Boolean extends(Class clz);

    /**
     * Determine if the class of the referent incorporates the specified trait or mixin.
     */
    Boolean incorporates(Class clz);

    /**
     * Determine if the referent is a service.
     */
    @ro Boolean isaService;

    /**
     * Determine if the referent is an immutable const.
     */
    @ro Boolean isaConst;

    /**
     * Determine if the referent is immutable.
     */
    @ro Boolean immutable;
    }
