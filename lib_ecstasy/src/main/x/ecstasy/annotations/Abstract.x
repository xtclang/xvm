/**
 * Abstract is a compile-time mixin for classes, properties, and methods:
 * * A class marked with `@Abstract`, or that contains any member that is marked with
 *   `@Abstract`, is explicitly abstract. An abstract class cannot be instantiated. It is a
 *   compile time error for a class to be abstract if it is illegal to extend the class, such as
 *   occurs with an `enum` value. It is a compile time warning for an interface or a member
 *   thereof to be marked with `@Abstract`. A mixin marked with `@Abstract` cannot be
 *   incorporated or used as an annotation.
 * * A class property marked with `@Abstract` defers the determination of its requirement for
 *   a field.
 * * A method marked with `@Abstract` requires TODO
 */
mixin Abstract
        into Class | Property | Method {
}
