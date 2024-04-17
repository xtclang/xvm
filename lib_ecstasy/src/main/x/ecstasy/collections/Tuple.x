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
 * explicitly supported by both the compiler and the runtime.
 *
 * All mutating operations on a tuple will result in the creation of a new tuple with the requested
 * changes incorporated; the original Tuple is left unchanged. This style of mutation is called a
 * _persistent data structure_, and is analogous to the [Array] support for the
 * [Persistent](Array.Mutability.Persistent) mutability option. The tuple may or may not be
 * `immutable`; to ensure that a tuple is immutable, use the [freeze] method.
 *
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
     * tuple.
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
     * Creates and returns a new tuple that is the concatenation of that tuple to this tuple.
     *
     * If the compile-time types of both _this_ tuple and _that_ tuple are known, then the
     * compile-time type of the returned tuple is known; otherwise, an explicit cast to a
     * compile-time type is required to regain the compile-time type.
     */
    @Op("+") Tuple!<> addAll(Tuple!<> that);

    /**
     * Modify the value of the specified element in the tuple, returning the resultant new tuple.
     *
     * @throws TypeMismatch  if the value does not match the type of the specified tuple element
     */
    Tuple replace(Int index, Object value);

    /**
     * Obtain a portion of the tuple as a tuple, with the portion specified as a [Range].
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
     * If the compile-time type of this tuple is known, and if the index is a compile-time constant
     * value, then the compile-time type of the returned tuple is known; otherwise, an explicit cast
     * to a compile-time type is required to regain the compile-time type.
     */
    Tuple!<> remove(Int index);

    /**
     * Create and return a new tuple that is a copy of this tuple, except with the specified
     * elements removed.
     *
     * If the compile-time type of this tuple is known, and if the interval is a compile-time
     * constant value, then the compile-time type of the returned tuple is known; otherwise, an
     * explicit cast to a compile-time type is required to regain the compile-time type.
     */
    Tuple!<> removeAll(Interval<Int> interval);

    /**
     * Return an `immutable` tuple of the same element types and values as are present in this tuple.
     *
     * @throws Exception if any of the values in the tuple are not [Shareable]
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