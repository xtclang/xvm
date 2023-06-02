/**
 * This is the archetype Ecstasy module, the seed from which all Ecstasy code must derive,
 * and the foundation upon which all Ecstasy code builds. This module contains the Ecstasy
 * type system, defining each of the intrinsically supported data types, and the various
 * structures and capabilities of the Ecstasy type system and runtime environment.
 * Additionally, a number of useful data structures and algorithms are included to promote
 * the productivity of developers writing Ecstasy code.
 *
 * All Ecstasy modules import this module automatically, as if they had the following line
 * of code:
 *
 *     package ecstasy import ecstasy.xtclang.org;
 *
 * This module is fully and completely self-referential, containing no references to other
 * modules, and no link-time or runtime dependencies.
 *
 * @Copyright 2016-2022 xqiz.it
 */
module ecstasy.xtclang.org {
    /**
     * The `Nullable` type is the only type that can contain the value [Null].
     *
     * `Nullable` is an [Enumeration] whose only value is the singleton [Enum] value `Null`.
     */
    enum Nullable {
        Null;

        /**
         * Given some `NotNullable` type, and a value that is either of that type or is `Null`,
         * convert that value into a conditional return of type `NotNullable`.
         *
         * @param value  either a `NotNullable` value, or the `Null` value
         *
         * @return `True` iff the value is not the `Null` value
         * @return (conditional) the `NotNullable` value
         */
        static <NotNullable> conditional NotNullable notNull(NotNullable? value) {
            return value == Null
                    ? False
                    : (True, value);
        }
    }

    /**
     * The `Ordered` enumeration describes the result of comparing two items for the purpose of
     * ordering.
     */
    enum Ordered(String symbol, Ordered reversed) {
        Lesser("<", Greater), Equal("=", Equal), Greater(">", Lesser);
    }

    /**
     * A `Deadlock` exception is raised by the runtime in response to a situation in which
     * reentrancy to a service is necessary, but for one of several reasons cannot be accomplished.
     */
    const Deadlock(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `TimedOut` exception is raised by the runtime in response to a thread-of-execution
     * exceeding a timeout (wall clock time) limitation within which it was running.
     */
    const TimedOut(Timeout timeout, String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An `OutOfMemory` exception indicates that a memory allocation failed.
     */
    const OutOfMemory(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `ReadOnly` exception is raised when an attempt is made to modify a read-only value.
     */
    const ReadOnly(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `TypeMismatch` exception is raised when an attempt is made to cast a reference to a type,
     * and the reference is incompatible with the specified type.
     */
    const TypeMismatch(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An `OutOfBounds` exception is raised when an attempt is made to invoke an operation with a
     * value that is out-of-bounds, or if the operation would result in an out-of-bounds condition.
     */
    const OutOfBounds(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `ConcurrentModification` exception is raised when an object detects a modification that it
     * was not expecting, and is unable to predictably continue processing according to the
     * contracts that it provides.
     */
    const ConcurrentModification(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An `IllegalArgument` exception is raised when an invalid argument is passed to a method or a
     * function.
     */
    const IllegalArgument(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An `IllegalState` exception is raised when a data structure is not in a consistent state
     * to perform a requested operation.
     */
    const IllegalState(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An `Assertion` exception is raised when an `assert:once`, `assert:rnd`, or `assert:test`
     * statement fails.
     */
    const Assertion(String? text = Null, Exception? cause = Null)
            extends IllegalState(text, cause);

    /**
     * An `UnsupportedOperation` exception is raised when an attempt is made to invoke functionality
     * that is not present or has not yet been implemented.
     */
    const UnsupportedOperation(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A `Closed` exception is raised when an attempt is made to use an object that has transitioned
     * to a closed state, including when a future is awaited and it is closed.
     */
    const Closed(String? text = Null, Exception? cause = Null)
            extends IllegalState(text, cause);

    /**
     * A `NotShareable` exception is raised when an attempt is made to force an object to produce
     * a reference that can be passed to another service, and that is not possible. Only references
     * to immutable objects and services can be passed from one service to another.
     */
    const NotShareable(String? text = Null, Exception? cause = Null)
            extends IllegalArgument(text, cause);

    /**
     * The interface associated with objects that are automatically closed by the `using` and
     * `try`-with-resources blocks.
     */
    interface Closeable {
        /**
         * This method is invoked to mark the end of the use of an object. The object may release
         * its resources at this point, and may subsequently be cantankerous and/or unusable as a
         * result.
         *
         * @param e  (optional) an exception that occurred that triggered the call to `close()`
         */
        void close(Exception? cause = Null);
    }

    /**
     * `Shareable` is a type that represents an object that can be passed across a service boundary,
     * from the realm of one service, passed into a different service. Specifically, it is only
     * possible to pass an immutable object or a service proxy across a service boundary; the exact
     * requirements are as follows:
     *
     * 1. An object that is immutable;
     * 2. An object that is [Freezable], from which an immutable copy will be obtained to pass
     *    across the service boundary;
     * 3. A `service` object;
     * 4. A _virtual child_ object of a `service` object.
     */
    typedef (service | immutable | Freezable) as Shareable;
}