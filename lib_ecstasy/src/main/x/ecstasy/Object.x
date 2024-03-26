/**
 * This class represents the capabilities that are common to every Ecstasy object. This class is the
 * single inheritance root for the Ecstasy type system; in other words, "everything is an object",
 * and every object is "of a class" that extends this Object class.
 *
 * An object is reachable through a reference, and an object reference **always** includes the
 * public portion of Object.
 *
 * Additional meta-information about the object is available through:
 * * The reference to the object, represented by the [Ref] interface;
 * * The [Type] of the object reference, which is provided by [Ref.Referent] and [Ref.actualType];
 *   and
 * * The [Class] of the object, which is accessible via the Type, unless the type has been
 *   masked by [Ref.maskAs<AsType>()].
 */
interface Object
        extends Comparable {
    /**
     * By default, comparing any two objects will only result in equality if they are the
     * same object, or if they are two constant objects with identical values.
     */
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
     */
    immutable Object makeImmutable() {
        this:struct.freeze();
        return this.as(immutable Object);
    }
}