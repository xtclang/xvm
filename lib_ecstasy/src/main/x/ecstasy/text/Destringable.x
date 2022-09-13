/**
 * An interface that allows a class to instantiate from a String. The general contract is that a
 * String produced by a class' [toString](Object.toString()) method should produce an identical
 * object via the `Destringable` String-based constructor.
 */
interface Destringable
    {
    /**
     * Construct from a `String` value that could have been previously produced from either the
     * [Stringable] interface on the same class, or form the [toString](Object.toString()) method.
     *
     * @param text  a `String` value that the class can use to construct itself from, and which the
     *              class will assume came from an invocation of `toString` on some previous
     *              instance of this same class
     */
    construct(String text);
    }
