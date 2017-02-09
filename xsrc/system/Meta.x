/**
 * TODO
 */
interface Meta
    {
// TODO move to Ref/Meta common base - as ActualType
    /**
     * The Type of the referent as it is available to the referrer.
     */
    @ro Type Type;

    /**
     * The Class represents the actual type composition of the object.
     */
    @ro Class class;

    /**
     * The containing module.
     */
    @ro Module module;

    /**
     * The read-only struct for this object. Each property that has space allocated for
     * storage of the property's value will occur within this structure.
     */
    @ro Struct struct;

// TODO move to Ref/Meta common base
    /**
     * This property represents the immutability of an object. Once the object
     * is immutable, it cannot be made mutable.
     */
    Boolean immutable;

// TODO move to Ref/Meta common base
    /**
     * The actual amount of memory used by this object, including the object's header (if
     * any) and any padding for memory alignment. The size includes the space required to
     * hold all of the underlying property references that are visible through the
     * {@link struct} property. Furthermore, the size includes the sizes of all of the
     * {@link Ref.selfContained self-contained} property references, but explicitly does
     * not include the sizes of the referents for references that are <b>not</b>
     * self-contained.
     */
    @ro Int byteLength;
    }
