/**
 * RO (read-only) is a compile-time mixin for properties:
 * * A property marked with `@RO` on an interface implies that the implementor of the
 *   interface will not have to provide a read/write property, and thus users of the interface
 *   should not be attempting to set a value for the property;
 * * A property marked with `@RO` on a class specifies that the publicly available property
 *   will have a method `set` that will unconditionally throw an exception; this is identical
 *   in behavior to a property declared as "`public/private`".
 */
mixin RO
        into Property
    {
    }
