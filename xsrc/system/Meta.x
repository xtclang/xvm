/**
 * The meta information that is available for each object.
 *
 * @see Object.meta
 */
interface Meta
        extends Referent
    {
    /**
     * The class represents the type composition of the object.
     */
    @ro Class class_;

    /**
     * The containing module.
     */
    @ro Module module_;

    /**
     * The read-only struct for this object. Each property that has space allocated for
     * storage of the property's value will occur within this structure.
     */
    @ro Struct struct;

    /**
     * This property represents the immutability of an object. This property can be set to true to
     * make a mutable object immutable; once the object is immutable, it cannot be made mutable.
     */
    @Override
    Boolean immutable_;

    /**
     * The actual amount of memory used by this object, including the object's header (if any) and
     * any padding for memory alignment. The size includes the space required to hold all of the
     * underlying property references that are visible through the {@link struct} property.
     * Furthermore, the size includes the sizes of all of the {@link Ref.selfContained
     * self-contained} property references, but explicitly does not include the sizes of the
     * referents for references that are <b>not</b> self-contained.
     */
    @ro Int byteLength;
    }
