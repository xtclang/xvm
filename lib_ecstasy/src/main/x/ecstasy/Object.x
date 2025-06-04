/**
 * This interface represents the capabilities that are common to every Ecstasy object. In Ecstasy,
 * "everything is an object", and every class is an implementation of this [Object] interface.
 *
 * An object can only be accessed and manipulated via a reference to that object, and every object reference will
 * contain _at least_ the methods of the [Object] interface. In other words, every reference will
 * have at least the methods [toString], [equals](Comparable.equals(Comparable)), and
 * [makeImmutable], as well as the [equals](Comparable.equals(CompileType, CompileType)) function
 * from the [Comparable] functional interface.
 *
 * Additional meta-information about the object is available through:
 * * The reference to the object, represented by the [Ref] interface;
 * * The [Type] of the object reference, which is provided by [Ref.Referent] and [Ref.type];
 *   and
 * * The [Class] of the object, which is accessible via the Type, unless the type has been
 *   masked by [Ref.maskAs<AsType>()].
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
    static <CompileType extends Object> Boolean equals(CompileType o1, CompileType o2) {
        return &o1 == &o2;
    }

    /**
     * Provide a String representation of the object.
     *
     * This is intended primarily for debugging, log messages, and other diagnostic features.
     */
    String toString() {
        if (this.is(Stringable)) {
            val buf = new StringBuffer(estimateStringLength());
            appendTo(buf);
            return buf.toString();
        }

        // the Object's rudimentary toString() shows class information only
        return this:class.toString();
    }

    /**
     * Make this object immutable.
     *
     * @throws Unsupported  if an attempt is made to invoke this method on an object that exists in
     *                      a different service
     */
    immutable Object makeImmutable() {
        this:struct.freeze();
        return this.as(immutable Object);
    }
}