/**
 * Override is a compile-time mixin for methods and properties:
 * * A method marked with {@code @override} on a type that does not have a corresponding method
 *   declaration on its super-type will cause a compile-time error;
 * * A property marked with {@code @override} on a type that does not have a corresponding property
 *   declaration on its super-type will cause a compile-time error;
 */
mixin Override
        into Method | Property
    {
    }
