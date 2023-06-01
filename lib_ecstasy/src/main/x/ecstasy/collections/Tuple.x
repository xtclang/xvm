/**
 * A Tuple is a container type for an arbitrary number of elements, each of its own arbitrary type.
 *
 * Generally, the broad use of tuples is discouraged in type-safe, object-oriented languages,
 * because they are considered to be poor analogues of classes. However, there are several natural
 * occurrences of Tuples within Ecstasy, and these uses are pervasive in the languages:
 *
 * * Parameter lists
 *
 * * Return values, including "conditional" returns
 *
 * Because tuple values are defined using parenthesis, and parenthesis are also used for method
 * invocations and to explicitly denote order of precedence, a number of conventions are used:
 *
 * * The empty tuple and a tuple with a single value must use an explicit type literal declaration:
 *   `Tuple:()` or `Tuple:(a)`
 *
 * * The return statement can return multiple comma-delimited values _without_ the use of
 *   parenthesis: `return a, b, c;`
 *
 * * All other cases should use a comma-delimited set of values within a matching pair of
 *   parenthesis: `(Int i, String s) = (0, "hello");`
 *
 * Due to the complexity of the non-uniform element type, Tuple is not intended to be implemented by
 * the developer; rather, it is a data type provided by the runtime, and whose type safety is
 * explicitly supported by both the compiler and the runtime. The implementation provided by the
 * runtime uses either the [Fixed](Mutability.Fixed) or [Constant](Mutability.Constant) mutability
 * by default, depending on the types of the elements; if the elements are all immutable or
 * services, then the tuple mutability will be `Constant`; otherwise, it will be `Fixed`.
 * The run-time also provides an implementation of the `Freezable` interface. Additionally, for a
 * tuple that contains only `immutable`, `service`, and `AutoFreezable` elements, the Tuple will
 * automatically incorporate `AutoFreezable(inPlace=False)`.
 */
interface Tuple<ElementTypes extends Tuple<ElementTypes>>
        extends Freezable
        extends Stringable {
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
    @Op("[]") Object getElement(Int index);

    /**
     * Modify the value in the specified element in the tuple.
     *
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the parameter is known and checked by the compiler;
     * otherwise, a type mismatch will raise a runtime type assertion.
     *
     * @throws ReadOnly  if the [mutability] of the tuple is not [Fixed](Mutability.Fixed)
     */
    @Op("[]=") void setElement(Int index, Object newValue);

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
     * Creates and returns a new tuple that is the concatenation of the specified value to this
     * tuple. The mutability of the returned tuple is the same as the mutability of this tuple,
     * except when the original mutability is [Constant](Mutability.Constant) and the element to
     * add is not immutable, in which case the resulting Tuple will have
     * [Persistent](Mutability.Persistent) mutability.
     *
     * If the compile-time types of both _this_ tuple and the value are known, then the
     * compile-time type of the returned tuple is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     *
     * While it would be desirable to annotate this method with `@Op("+")`, it's not currently
     * feasible since this method has not one, but two arguments (first being the type parameter),
     * and it's not possible to pass the necessary type information via the `GP_ADD` op code.
     */
    <Element> Tuple!<> add(Element value);

    /**
     * Creates and returns a new tuple that is the concatenation of that tuple to this tuple. The
     * mutability of the returned tuple is the same as the mutability of this tuple, except when
     * the original mutability is [Constant](Mutability.Constant) and at least one of the elements
     * to add is not immutable, in which case the resulting Tuple will have
     * [Persistent](Mutability.Persistent) mutability.
     *
     * If the compile-time types of both _this_ tuple and _that_ tuple are known, then the
     * compile-time type of the returned tuple is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     */
    @Op("+") Tuple!<> addAll(Tuple!<> that);

    /**
     * Modify the value of the specified element in the tuple, returning the resultant tuple. The
     * mutability of the returned tuple is the same as the mutability of this tuple, except when
     * the original mutability is [Constant](Mutability.Constant) and the new element value is not
     * immutable, in which case the resulting Tuple will have [Persistent](Mutability.Persistent)
     * mutability.
     *
     * If the tuple is fixed-size, then modification is made to this tuple, and a reference to this
     * tuple is returned; otherwise, a new tuple with the change is returned, and this tuple is left
     * unchanged.
     *
     * @throws TypeMismatch  if the value does not match the type of the specified tuple element
     */
    Tuple replace(Int index, Object value);

    /**
     * Obtain a portion of the tuple as a tuple, with the portion specified as a [Range].
     *
     * The returned tuple is fixed size, persistent, or const based on whether this tuple is fixed
     * size, persistent, or const; in all cases, changes to the returned tuple will not affect this
     * tuple.
     *
     * If the compile-time type of this tuple is known, and if the interval is a compile-time
     * constant value, then the compile-time type of the returned tuple is known; otherwise, an
     * explicit cast to a compile-time type is required to regain the compile-time type.
     */
    @Op("[..]") Tuple!<> slice(Range<Int> interval);

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
     * If the compile-time type of this tuple is known, and if the interval is a compile-time
     * constant value, then the compile-time type of the returned tuple is known; otherwise, an
     * explicit cast to a compile-time type is required to regain the compile-time type.
     */
    Tuple!<> removeAll(Interval<Int> interval);


    // ----- variable mutability -------------------------------------------------------------------

    /**
     * The levels of mutability of an Tuple. The forms of mutability for a tuple are _similar_ to
     * those of an [array](Array.Mutability), but there are subtle differences, such as how a
     * `Fixed` tuple supports element addition and removal by creating a new `Tuple` instance.
     */
    enum Mutability {
        /*
         * A _Constant_ `Tuple` is also a persistent data structure, but additionally it must be
         * immutable, and all of its contents must be immutable.
         */
        Constant,
        /*
         * A _Persistent_ `Tuple` handles mutation requests by creating a new `Tuple` with the
         * requested changes incorporated.
         */
        Persistent,
        /*
         * A _Fixed-Size_ `Tuple` allows mutations "in place" via replacement of its elements, but
         * mutations that would add or remove elements and thus alter its type or its size will
         * create a new `Tuple` with the requested changes incorporated.
         */
        Fixed
    }

    /**
     * The Mutability of the Tuple.
     */
    @RO Mutability mutability;

    /**
     * Obtain the same contents of this Tuple, but in a Tuple with the specified mutability. If the
     * mutability of this Tuple is already the desired mutability, or if [Mutability.Persistent]
     * is requested and the mutability is already [Mutability.Constant], then this Tuple is
     * returned. Otherwise, if `inPlace` is `True` and mutability is decreasing from `Fixed` to
     * `Persistent`, then the [mutability] of this Tuple is modified and this Tuple is returned. If
     * the requested mutability is `Constant`, then the Tuple is requested to [freeze]. Otherwise,
     * a new Tuple of the desired mutability is created with the same values as this Tuple.
     *
     * @param mutability  the requested [Mutability] of the resulting Tuple
     *
     * @return a Tuple with the requested mutability
     */
    Tuple ensureMutability(Mutability mutability, Boolean inPlace = False);

    /**
     * Return an `immutable const` tuple of the same element types and values as are present in this
     * tuple. All mutating calls to a `const` tuple will result in the creation of a new tuple with
     * the requested changes incorporated.
     *
     * @throws Exception if any of the values in the tuple are neither immutable nor
     *         {@link Freezable}
     */
    @Override
    immutable Tuple freeze(Boolean inPlace = False);


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return 10*size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        buf.add('(');

        for (Int i : 0 ..< size) {
            if (i > 0) {
                buf.addAll(", ");
            }

            Object v = this[i];

            if (v.is(Stringable)) {
                v.appendTo(buf);
            } else {
                v.toString().appendTo(buf);
            }
        }

        return buf.add(')');
    }


    // ----- Comparable ----------------------------------------------------------------------------

    /**
     * Tuples are equal if their types are comparable and the contents are equal.
     */
    static <CompileType extends Tuple> Boolean equals(CompileType value1, CompileType value2) {
        Int c = value1.size;
        if (c != value2.size) {
            return False;
        }

        for (Int i : 0 ..< c) {
            val ref1 = value1.elementAt(i);
            val ref2 = value2.elementAt(i);
            if (ref1 == ref2) {
                // identical references (including two Null values)
                continue;
            }

            val val1 = ref1.get();
            val val2 = ref2.get();
            if (val1 == Null || val2 == Null                            // eliminate either-is-Null
                || (ref1.Referent-Nullable) != (ref2.Referent-Nullable) // so ignore type Nullable
                || val1.as(ref1.Referent) != val2.as(ref1.Referent)) {
                return False;
            }
        }
        return True;
    }
}