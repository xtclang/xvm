/**
 * ReadOnly ({@code @RO}) is a compile-time mixin for properties:
 * * A property marked with {@code @RO} on an interface implies that the implementor of the
 *   interface will not have to provide a read/write property, and thus users of the interface
 *   should not be attempting to set a value for the property;
 * * A property marked with {@code @RO} on a class specifies that the publicly available property
 *   will have a method {@code set} that will unconditionally throw an exception; this is identical
 *   in behavior to a property declared as "{@code public/private}".
 */
mixin ReadOnly
        into Property
    {
    }
                                       Ï