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
     * Obtain a Tuple that represents the fields of this Struct.
     */
    Tuple to<Tuple>();

    /**
     * Obtain an array of references representing the fields of this Struct.
     */
    Ref[] to<Ref[]>();

    /**
     * Dereference a name to obtain a field.
     */
    @Op Ref elementFor(String name)
        {
        for (Ref ref : to<Ref[]>())
            {
            // TODO: replace with short circuit when Cam submits: if (ref.refName? == name)
            if (ref != null && ref.refName == name)
                {
                return ref;
                }
            }

        throw new Exception("no such field: " + name);
        }
    }
