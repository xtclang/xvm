/**
 * This interface represents the capabilities that are common to every Ecstasy object. In Ecstasy,
 * "everything is an object", and every class is an implementation of this [Object] interface.
 *
 * Each object is accessed and manipulated via a reference to that object, and each object reference
 * will contain _at least_ the methods of the [Object] interface. In other words, every reference
 * will have at least the methods [toString], [equals](Comparable.equals(Comparable)), and
 * [makeImmutable], as well as the [equals](Comparable.equals(CompileType, CompileType)) function
 * from the [Comparable] functional interface.
 *
 * Additional structural and meta-information about the object is available from the reference
 * object itself, represented by the [Ref] interface, and obtained via the unary `&` operator:
 *
 * * The compile-time [Type] of the reference is provided by [Ref.Referent]
 * * The run-time [Type] of the reference is provided by [Ref.type];
 * * The [Class] of the object is provided by [Ref.class], unless the reference has been _masked_ by
 *   [Ref.maskAs<AsType>()], in which case only the [Class] of the masking [Type] is provided.
 * * The reference [Identity](Ref.Identity) is provided by [Ref.identity].
 * * The internal [Struct] of the object is provided by [Ref.revealStruct()].
 */
interface Object
        extends Comparable {
    /**
     * [Object] equality is _reference equality_: Two [Object]s are equal iff the reference to each
     * [Object] is equal. Generally, this will mean that comparing any two [Object]s will result
     * in equality only if they are the same [Object] instance, but more correctly two [Object]s are
     * equal iff the two [Object]s share the same [reference Identity](Ref.Identity).
     */
    @Override
    static <CompileType extends Object> Boolean equals(CompileType o1, CompileType o2) = &o1 == &o2;

    /**
     * Provide a [String] representation of this [Object].
     *
     * This is intended primarily for debugging, log messages, and other diagnostic features.
     *
     * @return a [String] that contains information about this [Object]
     */
    String toString() = this:class.toString();

    /**
     * Ensure that object is immutable.
     *
     * @return this object, having been made deeply immutable
     *
     * @throws Unsupported  if an attempt is made to invoke this method on a `service`, on an
     *                      [Object] that exists in a different `service`, or on any other `Object`
     *                      that explicitly rejects immutability
     */
    immutable Object makeImmutable() {
        this:struct.freeze();
        return this.as(immutable Object);
    }
}