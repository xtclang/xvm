/**
 * A Tuple is a container type for an arbitrary number of elements, each of its own arbitrary type.
 *
 * Generally, the broad use of tuples is discouraged in type-safe, object-oriented languages,
 * because they are considered to be poor analogues of classes. However, there are several natural
 * occurrences of Tuples within Ecstasy, and these uses are pervasive in the languages:
 * * Parameter lists
 * * Return values, including "conditional" returns
 *
 * Because tuple values are defined using parenthesis, and parenthesis are also used for method
 * invocations and to explicitly denote order of precedence, a number of conventions are used:
 * * The empty tuple and a tuple with a single value must use an explicit type literal declaration:
 *   `Tuple:()` or `Tuple:(a)`
 * * The return statement can return multiple comma-delimited values _without_ the use of
 *   parenthesis: `return a, b, c;`
 * * All other cases should use a comma-delimited set of values within a matching pair of
 *   parenthesis: `(Int i, String s) = (0, "hello");`
 *
 * Due to the complexity of the non-uniform element type, Tuple is not intended to be implemented by
 * the developer; rather, it is a data type provided by the runtime, and whose type safety is
 * explicitly supported by both the compiler and the runtime.
 */
interface Tuple<ElementTypes extends Tuple<ElementTypes>>
        extends FixedSizeAble
        extends PersistentAble
        extends ImmutableAble
    {
    /**
     * The number of elements in the tuple. A tuple cannot change its size; a size change requires
     * the creation of a new tuple.
     */
    @RO Int size;

    /**
     * Obtain the value of the specified element in the tuple.
     *
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the returned value is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    @Op("[]")
    Object getElement(Int index);

    /**
     * Modify the value in the specified element in the tuple.
     *
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the parameter is known and checked by the compiler;
     * otherwise, a type mismatch will raise a runtime type assertion.
     *
     * This operation will throw an exception if the tuple is either persistent or `const`.
     */
    @Op("[]=")
    void setElement(Int index, Object newValue);

    /**
     * Obtain the Ref for the specified element.
     *
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the returned Ref is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    Ref<Object> elementAt(Int index);


    // ----- tuple manipulations -------------------------------------------------------------------

    /**
     * Creates and returns a new tuple that is the concatenation of that tuple to this tuple. The
     * returned tuple is fixed size, persistent, or const based on whether this tuple is fixed size,
     * persistent, or const.
     *
     * If the compile-time types of both _this_ tuple and _that_ tuple are known, then the
     * compile-time type of the returned tuple is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     */
    @Op("+")
    Tuple!<> add(Tuple!<> that);

    /**
     * Modify the value of the specified element in the tuple, returning the resultant tuple.
     *
     * The returned tuple is fixed size, persistent, or const based on whether this tuple is fixed
     * size, persistent, or const.
     *
     * If the tuple is fixed-size, then modification is made to this tuple, and a reference to this
     * tuple is returned; otherwise, a new tuple with the change is returned, and this tuple is left
     * unchanged.
     */
    Tuple!<> replace(Int index, Object value);

    /**
     * Obtain a portion of the tuple as a tuple.
     *
     * The returned tuple is fixed size, persistent, or const based on whether this tuple is fixed
     * size, persistent, or const; in all cases, changes to the returned tuple will not affect this
     * tuple.
     *
     * If the compile-time type of this tuple is known, and if the interval is a compile-time constant
     * value, then the compile-time type of the returned tuple is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    @Op("[..]")
    Tuple!<> slice(Interval<Int> interval);

    /**
     * Creates and returns a new tuple that is a copy of this tuple, except with the specified
     * element removed.
     *
     * The returned tuple is fixed size, persistent, or const based on whether this tuple is fixed
     * size, persistent, or const; in all cases, changes to the returned tuple will not affect this
     * tuple.
     *
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the returned tuple is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    Tuple!<> remove(Int index);

    /**
     * Create and return a new tuple that is a copy of this tuple, except with the specified
     * elements removed.
     *
     * The returned tuple is fixed size, persistent, or const based on whether this tuple is fixed
     * size, persistent, or const; in all cases, changes to the returned tuple will not affect this
     * tuple.
     *
     * If the compile-time type of this tuple is known, and if the interval is a compile-time constant
     * value, then the compile-time type of the returned tuple is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    Tuple!<> remove(Interval<Int> interval);

    /**
     * Return a fixed-size tuple (whose values are mutable) of the same type and with the same
     * contents as this tuple. If this tuple is already a fixed-size tuple, then _this_ is returned.
     */
    @Override
    Tuple ensureFixedSize(Boolean inPlace = false);

    /**
     * Return a persistent tuple of the same element types and values as are present in this tuple.
     * If this tuple is already persistent or `const`, then _this_ is returned.
     *
     * A _persistent_ tuple does not support replacing the contents of the elements in this tuple
     * using the {@link replace} method; instead, calls to {@link replace} will return a new tuple.
     */
    @Override
    Tuple ensurePersistent(Boolean inPlace = false);

    /**
     * Return a `const` tuple of the same element types and values as are present in this
     * tuple.
     *
     * All mutating calls to a `const` tuple will result in the creation of a new
     * `const` tuple with the requested changes incorporated.
     *
     * @throws Exception if any of the values in the tuple are not `const` and are not
     *         {@link ImmutableAble}
     */
    @Override
    immutable Tuple ensureImmutable(Boolean inPlace = false);
    }
