/**
 * Represents the compiled information for a typedef.
 *
 * A typedef is a "type definition", which allows a simple name to represent a type. Since the
 * specification of a type in source code can be both verbose and error-prone, being able to specify
 * it once and associate it with a simple, descriptive name enables more readable, understandable,
 * evolvable, and reliable code.
 */
interface TypedefTemplate
        extends ComponentTemplate
    {
    /**
     * The type that this `typedef` refers to.
     */
    @RO TypeTemplate referredToType;
    }
