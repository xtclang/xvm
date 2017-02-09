/**
 * A Struct is a simple container of Field objects. Each Field is represented by a
 * property of the Struct.
 * TODO: Explain what that means
 */
interface Struct
        // TODO UniformIndexed[Name] ?
    {
    /**
     * Obtain a Tuple that represents the contents of this Struct.
     * <p>
     * <li>The Tuple is immutable iff this Struct is immutable.</li>
     * <li>The Tuple is read-only iff this Struct is read-only.</li>
     */
    Tuple to<Tuple>();

    /**
     * Obtain an Array that represents the contents of this Struct.
     * TODO define mutability guarantees
     */
    Ref[] to<Ref[]>();
    }
