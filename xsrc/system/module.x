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
module ecstasy.xtclang.org
    {
    /**
     * The Nullable type is the only type that can contain the value Null.
     *
     * Nullable is an Enumeration whose only value is the singleton enum value {@code Null}.
     */
    enum Nullable {Null}

    /**
     * The Ordered enumeration describes the result of comparing two items for the prurpose of
     * ordering.
     */
    enum Ordered(String symbol) {Lesser("<"), Equal("="), Greater(">")}

    typedef Nullable.Null           null;
    typedef Boolean.False           false;
    typedef Boolean.True            true;
    typedef UInt8                   Byte;
    typedef Int64                   Int;
    typedef UInt64                  UInt;
    typedef Decimal64               Dec;
    typedef Tuple<>                 Void;
    typedef annotations.AtomicRef   atomic;
    typedef annotations.Automagic   auto;
    typedef annotations.FutureRef   future;
    typedef annotations.LazyRef     lazy;
    typedef annotations.ReadOnly    ro;
    typedef annotations.SoftRef     soft;
    typedef annotations.WatchRef    watch;
    typedef annotations.WeakRef     weak;

    /**
     * A DeadlockException is raised by the runtime in response to a situation in which re-entrancy
     * to a service is necessary, but for one of several reasons cannot be accomplished.
     */
    const DeadlockException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        /**
         * The stack traces of the various other threads-of-execution contributing to the deadlock,
         * if any.
         */
        @inject Iterable<StackFrame>[] stackTraces;
        };

    /**
     * A TimeoutException is raised by the runtime in response to a thread-of-execution exceeding a
     * timeout (wall clock time) limitation within which it was running.
     */
    const TimeoutException(Timeout timeout, String? text, Exception? cause)
            extends Exception(text, cause);
        {
        }

    /**
     * A ReadOnlyException is raised when an attempt is made to modify a read-only value.
     */
    const ReadOnlyException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }

    /**
     * A BoundsException is raised when an attempt is made to invoke an operation with a value that
     * is out-of-bounds, or if the operation would result in an out-of-bounds condition.
     */
    const BoundsException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }

    /**
     * An UnsupportedOperationException is raised when an attempt is made to invoke functionality
     * that is not present or has not yet been implemented.
     */
    const UnsupportedOperationException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }

    /**
     * A data structure that is able to provide a _mutable_ copy of itself should implement this
     * interface.
     *
     * Generally speaking, there are four degrees of mutability/immutability:
     * * _Mutable_ - a data structure permitting valid mutations to occur to itself;
     * * _Fixed-Size_ - a container data structure that does not permit changes to its _size_;
     * * _Persistent_ - a container data structure that does not permit changes to its _size_ or its
     *   _contents_;
     * * {@code const} - an immutable data structure.
     */
    interface MutableAble
        {
        /**
         * To ensure that the object is mutable, use the object returned from this method.
         *
         * The expected implementation of this method on a mutable object is that the method will
         * simply return {@code this}.
         */
        MutableAble ensureMutable();
        }

    /**
     * A container data structure that is able to provide a _fixed-size_ copy of itself should
     * implement this interface.
     *
     * Generally speaking, there are four degrees of mutability/immutability:
     * * _Mutable_ - a data structure permitting valid mutations to occur to itself;
     * * _Fixed-Size_ - a container data structure that does not permit changes to its _size_;
     * * _Persistent_ - a container data structure that does not permit changes to its _size_ or its
     *   _contents_;
     * * {@code const} - an immutable data structure.
     */
    interface FixedSizeAble
        {
        /**
         * To ensure that the object is of a fixed size, use the object returned from this method.
         *
         * The expected implementation of this method on a fixed-size object is that the method will
         * simply return {@code this}.
         */
        FixedSizeAble ensureFixedSize();
        }

    /**
     * A container data structure that is able to provide a _persistent_ copy of itself should
     * implement this interface.
     *
     * A persistent data structure is a container data type that creates new versions of itself in
     * response to modification requests, thus avoiding changing its own state. A persistent data
     * structure behaves like an immutable data structure, except that its contained data types are
     * not required to be immutable; it acts as if it were "shallow immutable".
     *
     * For example, a persistent array data structure cannot be increased in size, nor decreased in
     * size, nor can any of its elements have their values replaced (i.e. each element has a
     * referent, and the array will not allow any element to modify which referent it holds).
     *
     * The benefit of a persistent data structure is similar to that of a {@code const} value, in
     * that it is safe to share. However, only {@code const} values can be passed across a service
     * boundary.
     *
     * Generally speaking, there are four degrees of mutability/immutability:
     * * _Mutable_ - a data structure permitting valid mutations to occur to itself;
     * * _Fixed-Size_ - a container data structure that does not permit changes to its _size_;
     * * _Persistent_ - a container data structure that does not permit changes to its _size_ or its
     *   _contents_;
     * * {@code const} - an immutable data structure.
     *
     * (Note: The term "container data type" is not related to Ecstasy's {@link Container}
     * functionality, and the term "persistent" is not related to the concept of persistent
     * storage.)
     */
    interface PersistentAble
        {
        /**
         * To ensure that the object is acting as a persistent object, use the object returned from
         * this method.
         *
         * The expected implementation of this method on a persistent object is that the method will
         * simply return {@code this}.
         */
        PersistentAble ensurePersistent();
        }

    /**
     * A data structure that is able to provide a {@code const} copy of itself should implement this
     * interface, allowing the runtime to obtain a {@code const} version of the data structure when
     * necessary, such as when crossing a service boundary.
     */
    interface ConstAble
        {
        /**
         * To ensure that the object is a {@code const}, use the object returned from this method.
         *
         * The expected implementation of this method on a {@code const} object is that the method
         * will simply return {@code this}.
         */
        immutable (Constable+Const) ensureConst();

        /**
         * To ensure that the object is a {@code const}, use the object returned from this method.
         * Unlike {@link ensureConst}, this method will attempt to convert {@code this} to an
         * immutable object that implements the Const interface, and will only create a new object
         * if this object cannot be converted to an immutable form.
         *
         * The expected implementation of this method on a {@code const} object is that the method
         * will simply return {@code this}.
         */
        immutable (Constable+Const) makeConst()
            {
            if (this instanceof Const)
                {
                meta.immutable = true;
                return this;
                }

            return ensureConst();
            }
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

        /**
         * Objects within a module is subject to
         */
        @ro Iterable<Class> autoMixins;
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
