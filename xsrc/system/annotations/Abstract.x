/**
 * Abstract is a compile-time mixin for classes, properties, and methods:
 * * A class marked with {@code @Abstract}, or that contains any member that is marked with
 *   {@code @Abstract}, is explicitly abstract. An abstract class cannot be instantiated. It is a
 *   compile time error for a class to be abstract if it is illegal to extend the class, such as
 *   occurs with an {@code enum} value. It is a compile time warning for an interface or a member
 *   thereof to be marked with {@code @Abstract}. A mixin marked with {@code @Abstract} cannot be
 *   incorporated or used as an annotation.
 * * A class property marked with {@code @Abstract} defers the determination of its requirement for
 *   a field.
 * * A method marked with {@code @Abstract} requires
 */
mixin Abstract
        into Class | Property | Method
    {
    }
