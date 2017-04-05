package collections
    {
    /**
     * Some data structures have variable constraints on their mutability. Specifically, a data
     * structure may support up to four well-known degrees of mutability/immutability:
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
    interface VariablyMutable
        {
        enum MutabilityConstraint {Mutable, FixedSize, Persistent, Constant}

        /**
         * The MutabilityConstraint of the data structure.
         */
        @ro MutabilityConstraint mutability;
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
         * simply return {@code this}.
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
         * simply return {@code this}.
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
     * The benefit of a persistent data structure is similar to that of a {@code const} value, in
     * that it is safe to share. However, only {@code const} values can be passed across a service
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
         * simply return {@code this}.
         *
         * @param inPlace  (optional) specify that the object should convert itself to persistent if
         *                 it supports that form of variable mutability
         */
        PersistentAble ensurePersistent(Boolean inPlace = false);
        }

    /**
     * A data structure that is able to provide a {@code const} copy of itself should implement this
     * interface, allowing the runtime to obtain a {@code const} version of the data structure when
     * necessary, such as when crossing a service boundary.
     */
    interface ConstAble
            extends VariablyMutable
        {
        /**
         * To ensure that the object is a {@code const}, use the object returned from this method.
         *
         * The expected implementation of this method on a {@code const} object is that the method
         * will simply return {@code this}.
         *
         * If the class implements the {@link Const} interface, and {@link inPlace} is specified as
         * true, then a conveivable implementation of this method would be:
         *
         *   meta.immutable = true;
         *   return this;
         *
         * @param inPlace  (optional) specify that the object should convert itself to {@code const}
         *                 if it supports that form of variable mutability
         */
        immutable (Constable+Const) ensureConst(Boolean inPlace = false);
        }
    }
