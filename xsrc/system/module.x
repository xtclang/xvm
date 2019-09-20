/**
 * This is the archetype Ecstasy module, the seed from which all Ecstasy code must derive,
 * and the foundation upon which all Ecstasy code builds. This module contains the Ecstasy
 * type system, defining each of the intrinsically supported data types, and the various
 * structures and capabilities of the Ecstasy type system and runtime environment.
 * Additionally, a number of useful data structures and algorithms are included to promote
 * the productivity of developers writing Ecstasy code.
 * <p>
 * All Ecstasy modules import this module automatically, as if they had the following line
 * of code:
 * <code>import ecstasy.xtclang.org as x;</code>
 * <p>
 * This module is fully and completely self-referential, containing no references to other
 * modules, and no link-time or runtime dependencies.
 *
 * @Copyright 2016-2017 xqiz.it
 */
module Ecstasy.xtclang.org
    {
    /**
     * The Nullable type is the only type that can contain the value Null.
     *
     * Nullable is an Enumeration whose only value is the singleton enum value `Null`.
     */
    enum Nullable { Null }

    /**
     * The Ordered enumeration describes the result of comparing two items for the purpose of
     * ordering.
     */
    enum Ordered(String symbol) { Lesser("<"), Equal("="), Greater(">") }

    /**
     * A Deadlock exception is raised by the runtime in response to a situation in which re-entrancy
     * to a service is necessary, but for one of several reasons cannot be accomplished.
     */
    const Deadlock(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A TimedOut exception is raised by the runtime in response to a thread-of-execution exceeding
     * a timeout (wall clock time) limitation within which it was running.
     */
    const TimedOut(Timeout timeout, String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A ReadOnly exception is raised when an attempt is made to modify a read-only value.
     */
    const ReadOnly(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A TypeMismatch exception is raised when an attempt is made to cast a reference to a type, and
     * the reference is incompatible with the specified type.
     */
    const TypeMismatch(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An OutOfBounds exception is raised when an attempt is made to invoke an operation with a
     * value that is out-of-bounds, or if the operation would result in an out-of-bounds condition.
     */
    const OutOfBounds(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A ConcurrentModification exception is raised when an object detects a modification that it
     * was not expecting, and is unable to predictably continue processing according to the
     * contracts that it provides.
     */
    const ConcurrentModification(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An IllegalArgument exception is raised when an invalid argument is passed to a method or a
     * function.
     */
    const IllegalArgument(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An IllegalState exception is raised when a data structure is not in a consistent state
     * to perform a requested operation.
     */
    const IllegalState(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An Assertion exception is raised when an assert fails.
     */
    const Assertion(String? text = null, Exception? cause = null)
            extends IllegalState(text, cause);

    /**
     * An UnsupportedOperation exception is raised when an attempt is made to invoke functionality
     * that is not present or has not yet been implemented.
     */
    const UnsupportedOperation(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A Closed exception is raised when an attempt is made to use an object that has transitioned
     * to a closed state, including when a future is awaited and it is closed.
     */
    const Closed(String? text = null, Exception? cause = null)
            extends IllegalState(text, cause);

    /**
     * The interface associated with objects that are automatically closed by the `using` and
     * `try`-with-resources blocks.
     */
    interface Closeable
        {
        /**
         * This method is invoked to mark the end of the use of an object. The object may release
         * its resources at this point, and may subsequently be cantankerous and/or unusable as a
         * result.
         */
        void close();
        }

    /**
     * Represents an Ecstasy Module, which is the outer-most level organizational unit for
     * source code, and the aggregate unit for compiled code distribution and deployment.
     * <p>
     * Because of its name, the Module type must be defined inside (textually included in)
     * the Ecstasy "module.x" file, for two reasons: (1) the Module.x file would conflict
     * with the Ecstasy "module.x" file that is in the same directory, and (2) the file
     * name "module.x" is reserved for defining the module itself, while in this case we
     * are defining the "Module" type.
     */
    interface Module
            extends Package
        {
        // TODO version number

        // TODO list of depends-on other modules
        }

    /**
     * Represents an Ecstasy Package.
     * <p>
     * Because of its name, the Package type must be defined inside (textually included
     * in) the Ecstasy "module.x" file, because the file name "package.x" is reserved for
     * defining the package itself, while in this case we are defining the "Package" type.
     */
    interface Package
        {
        // TODO what is the class for this

        // TODO name-to-contents map
        }
    }
