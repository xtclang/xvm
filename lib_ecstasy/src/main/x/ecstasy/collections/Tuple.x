/**
 * A [Tuple] is a container type for an arbitrary number of elements, each with its own type.
 *
 * Generally, the broad use of tuples is discouraged in type-safe, object-oriented languages,
 * because they are considered to be poor analogues of classes. However, there are several natural
 * occurrences of Tuples within Ecstasy, and these uses are pervasive in the languages:
 *
 * * The arguments passed to a method or function;
 * * The values returned from a method or function, including "conditional" returns.
 *
 * Because [Tuple] values are defined using parenthesis, and parenthesis are also used for enclosing
 * an expression for precedence, the [Tuple] literal `(v)` that contains a single value `v` of type
 * `V` and has no trailing comma will have the _implicit_ type `V` instead of `Tuple<V>`. Because it
 * is in the form of both a [Tuple] literal and a parenthesized expression, it can be used anywhere
 * that a `Tuple<V>` is required, though; consider the following examples:
 *
 *     V v = ...            // assume that there exists some value `v`
 *     val   a = (v);       // the type of "a" is V, because that is its implicit type
 *     Tuple b = (v);       // the type of "b" is Tuple<V>, because a Tuple is required
 *     val   c = (v,);      // the type of "c" is Tuple<V>, because of the trailing comma
 *     val   d = Tuple:(v); // the type of "d" is Tuple<V>, because of the explicit type literal
 *
 * The `return` statement can return multiple values either as a [Tuple] or as comma-delimited list
 * of values; for a function declared as returning three values, both `return (a, b, c);` and
 * `return a, b, c;` are legal, and there is no difference from the caller's perspective. Similarly,
 * a caller can obtain the return value(s) from a method as either a comma-delimited list or as a
 * [Tuple], regardless of how the `return` statement in the function was constructed; consider a
 * caller of the function declared as `(Int, String) baz()`:
 *
 *     (Int i, String s)    = baz();    // assign the two results of the function to `i` and `s`
 *     Tuple<Int, String> t = baz();    // assign the tuple result of the function to `t`
 *
 * As illustrated above, for each returned value from a function, the resulting [Tuple] will have
 * one corresponding value. A `void` function returns zero values, which means that when a caller
 * of a `void` function receives back a [Tuple] as a result of the call, that [Tuple] will be of
 * size zero; for example, consider a caller of the function declared as `void bar()`:
 *
 *     Object o = bar();    // compile error: bar() does not return a value
 *     void   v = bar();    // compile error: void is not a type
 *     Tuple  t = bar();    // OK: "t" will be the empty Tuple
 *
 * Both L-values and R-values can use [Tuple] syntax, e.g. `(Int i, String s) = (0, "hello");`
 *
 * Due to the complexity of the non-uniform element types represented by a [Tuple]'s `ElementTypes`,
 * the [Tuple] interface is not intended to be implementable by an Ecstasy developer; rather, it is
 * a data type supported by the Ecstasy compiler and runtime, which provide [Tuple] type safety and
 * any necessary implementations of the interface.
 *
 * All mutating operations on a tuple will result in the creation of a new tuple with the requested
 * changes incorporated; the original Tuple is left unchanged. This style of mutation is called a
 * _persistent data structure_, and is analogous to the [Array] support for the
 * [Persistent](Array.Mutability.Persistent) mutability option. The tuple may or may not be
 * `immutable`; to ensure that a tuple is immutable, use the [freeze] method.
 *
 * The run-time also provides an implementation of the [Freezable] interface. Additionally, for a
 * Tuple with `ElementTypes` that specify only `immutable`, `service`, and `AutoFreezable` types,
 * the [Tuple] will automatically be annotated by [AutoFreezable(inPlace=False)](AutoFreezable).
 */
interface Tuple<ElementTypes extends Tuple<ElementTypes>>
        extends Iterable<Object>
        extends Freezable
        extends Stringable {
    /**
     * The number of elements in the tuple. A tuple cannot change its size; a size change requires
     * the creation of a new tuple.
     */
    @Override
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

    @Override
    Iterator<Object> iterator() {
        class TupleIterator(Int i = 0)
                implements Iterator<Object> {
            @Override
            conditional Element next() {
                return i < this.Tuple.size
                        ? (True, this.Tuple[i++])
                        : False;
            }

            @Override
            conditional Int knownSize() = (True, this.Tuple.size - i);

            @Override
            (TupleIterator, TupleIterator) bifurcate() = (this, new TupleIterator(i));
        }
        return new TupleIterator();
    }

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
     * Return an `immutable` [Tuple] of the same element types and values as are present in this
     * [Tuple].
     *
     * @throws Exception  if any of the values in the [Tuple] are not [Shareable]
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
    @Override
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