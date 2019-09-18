/**
 * A Struct is a simple container of values, each of which is referred to as a _field_.
 *
 * Struct is not intended to be implemented by the developer; rather, it is a means by which the
 * runtime exposes information to the developer. For example, the {@code new} operator begins by
 * allocating a Struct that will be used to collect all of the state for the object that is being
 * created; it is that Struct that is passed to the {@code construct()} function (represented by the
 * _"this"_ reference) to be initialized.
 */
interface Struct
    {
    /**
     * Represents the mutability of the structure. Once the property has been set to False, the
     * Struct is no longer mutable, and as a result, the property cannot be set to True.
     */
    Boolean mutable;

    /**
     * Obtain a Tuple that represents the fields of this Struct.
     */
    Tuple to<Tuple>();

    /**
     * Obtain an array of references representing the fields of this Struct.
     */
    Ref[] to<Ref[]>();

//    /**
//     * Dereference a name to obtain a field.
//     */
//    Ref elementFor(String name) TODO not String, but Property (or property id)
//        {
//        for (Ref ref : to<Ref[]>())
//            {
//            if (ref.refName == name)
//                {
//                return ref;
//                }
//            }
//
//        throw new Exception("no such field: " + name);
//        }

    /**
     * The actual amount of memory used by this object, including the object's header (if any) and
     * any padding for memory alignment. The size includes the space required to hold all of the
     * underlying property references that are visible through the {@link struct} property.
     * Furthermore, the size includes the sizes of all of the {@link Ref.selfContained
     * self-contained} property references, but explicitly does not include the sizes of the
     * referents for references that are <b>not</b> self-contained.
     */
    @RO Int byteLength;
    }
