package collections
    {
    /**
     * An Orderer is a function that compares two objects for order.
     */
    typedef function Ordered (Object, Object) Orderer;

    /**
     * Some data structures are capable of mutating their data in-place, while others (called
     * "persistent" data structures) create a new copy (or representation) of the data structure
     * when a mutating operation is executed. (The use of the term "persistent" in this context is
     * not related to persistent _storage_, such as in a database or on disk; it refers instead to
     * the "persistence" of the state of the _old_ data structure, even after a change is made that
     * resulted in the creation of a _new_ data structure.) In Ecstasy, when a data structure can
     * support more than one of the following forms of mutability and data structure persistence,
     * then that data structure should implement the `VariablyMutable` interface to represent that
     * capability:
     *
     * * _Mutable_    - a data structure permitting valid mutations to occur to itself, including
     *                  changes in its _size_;
     *
     * * _Fixed-Size_ - a mutable data structure does not permit changes to its _size_;
     *
     * * _Persistent_ - a container data structure that does not permit changes to its _size_ or its
     *                  _contents_, but instead creates a new data structure to represent the result
     *                  of a requested mutating operation;
     *
     * * `const`      - a persistent data structure that holds immutable data, and is itself
     *                  immutable.
     *
     * (Note: The term "container data type" is not related to Ecstasy's [Container] functionality,
     * and the term "persistent" is not related to the concept of persistent storage.)
     */
    interface VariablyMutable
        {
        enum Mutability(Boolean persistent)   // TODO CP - flip this to "inPlace"
            {
            /*
             * A _Mutable_  data structure allows mutations, including changes in its `size`.
             */
            Mutable   (False),
            /*
             * A _Fixed-Size_ data structure allows mutations, but not those that would alter its
             * `size`
             */
            Fixed     (False),
            /*
             * A _Persistent_ data structure handles mutation requests by creating a new data
             * structure with the requested changes. While a Persistent data structure does not
             * expose mutations to its internal data, it is common for multiple persistent data
             * structures to share mutable internal data for efficiency purposes.
             */
            Persistent(True ),
            /*
             * A _Constant_ data structure is also a persistent data structure, but additionally
             * it must be immutable, and all of its contents must be immutable.
             */
            Constant  (True )
            }

        /**
         * The Mutability of the data structure.
         */
        @RO Mutability mutability;
        }

    /**
     * A container data structure that is able to provide a _mutable_ copy of itself should
     * implement this interface.
     */
    interface MutableAble
            extends VariablyMutable
        {
        /**
         * To ensure that the object is mutable, use the object returned from this method.
         *
         * The expected implementation of this method on a mutable object is that the method will
         * simply return `this`.
         */
        MutableAble ensureMutable();
        }

    /**
     * A container data structure that is able to provide a _fixed-size_ copy of itself should
     * implement this interface.
     */
    interface FixedSizeAble
            extends VariablyMutable
        {
        /**
         * To ensure that the object is of a fixed size, use the object returned from this method.
         *
         * The expected implementation of this method on a fixed-size object is that the method will
         * simply return `this`.
         *
         * @param inPlace  (optional) specify that the object should convert itself to fixed-size if
         *                 it supports that form of variable mutability
         */
        FixedSizeAble ensureFixedSize(Boolean inPlace = false);
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
     * The benefit of a persistent data structure is similar to that of a `const` value, in
     * that it is safe to share. However, only `const` values can be passed across a service
     * boundary.
     *
     * (Note: The term "persistent" is not related to the concept of persistent storage.)
     */
    interface PersistentAble
            extends VariablyMutable
        {
        /**
         * To ensure that the object is acting as a persistent object, use the object returned from
         * this method.
         *
         * The expected implementation of this method on a persistent object is that the method will
         * simply return `this`.
         *
         * @param inPlace  (optional) specify that the object should convert itself to persistent if
         *                 it supports that form of variable mutability
         */
        PersistentAble ensurePersistent(Boolean inPlace = false);
        }

    /**
     * A data structure that is able to provide a `const` copy of itself should implement this
     * interface, allowing the runtime to obtain a `const` version of the data structure when
     * necessary, such as when crossing a service boundary.
     */
    interface ImmutableAble
            extends VariablyMutable
        {
        /**
         * To ensure that the object is a `const`, use the object returned from this method.
         *
         * The expected implementation of this method on a `const` object is that the method
         * will simply return `this`.
         *
         * If the class implements the [Const] interface, and [inPlace] is specified as
         * true, then a conceivable implementation of this method would be:
         *
         *   meta.immutable = true;
         *   return this;
         *
         * @param inPlace  (optional) specify that the object should convert itself to `const`
         *                 if it supports that form of variable mutability
         */
        immutable ImmutableAble ensureImmutable(Boolean inPlace = false);
        }

    typedef (ImmutableAble | immutable Object) Freezable;

    /**
     * An ConstantRequired exception is raised when an attempt is made to change mutability to
     * Constant, and some reference that must be `immutable Const` cannot be made so or converted
     * to be so.
     */
    const ConstantRequired(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An SizeLimited exception is raised when an attempt is made to alter a data structure in a
     * manner that would exceed its maximum size.
     */
    const SizeLimited(String? text = null, Exception? cause = null)
            extends Exception(text, cause);
    }
