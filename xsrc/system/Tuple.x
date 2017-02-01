/**
 * A Tuple is a container for an arbitrary number of elements, each of an arbitrary
 * type.
 * <p>
 * The Tuple interface is what the "NonUniformIndexed" interface would look like.
 */
class Tuple<ElementType...>
        implements NonUniformIndexed<ElementType...>
    {
    /**
     * Obtain the Fields of this Tuple as an Array. Note that the data types of the
     * fields can vary, so the FieldType of each Field may differ.
     */
    Field[] to<Field[]>();

    // REVIEW - basically this says we can convert any tuple to a "struct" at runtime
    /**
     * Obtain a Struct that represents the contents of this Tuple.
     * <p>
     * <li>The Struct is immutable iff this Tuple is immutable.</li>
     * <li>The Struct is read-only iff this Tuple is read-only.</li>
     */
    Struct to<Struct>();

    // REVIEW does this make sense? can you use this as a "return type" of "Void"?
    singleton value Void
            implements Tuple<>
        {
        Int FieldCount
            {
            Int get()
                {
                return 0;
                }
            }

        // ...
        }
    }
