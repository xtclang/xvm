/**
 * The LazyVar mixin allows a reference (such as a property) to be lazily populated with a value on
 * demand. Specifically, the first time that an attempt is made to de-reference the reference by
 * invoking `get()`, the LazyVar will invoke the deferred calculation of the value and store
 * that value in the reference for subsequent use, before returning that same value.
 *
 * A LazyVar cannot be set, except by its own lazy calculation. A LazyVar is immutable for all
 * practical purposes, with the sole exception being that it will assign itself a value if and
 * when necessary. The lazy calculation should be idempotent.
 *
 * Consider the following example of a "hash" property being calculated lazily:
 *
 *   const Point(Int x, Int y)
 *       {
 *       @Lazy(() -> x ^ y) Int hash;
 *       }
 *
 * Alternatively, the `calc()` method can be overridden:
 *
 *   const Point(Int x, Int y)
 *       {
 *       @Lazy Int hash.calc()
 *           {
 *           return x ^ y;
 *           }
 *       }
 *
 * (Note that the above examples are for illustrative purposes only; it is not necessary to
 * implement the hash code calculation for a `const` class, as a default implementation is
 * provided.)
 */
mixin LazyVar<Referent>(function Referent ()? calculate = Null)
        into Var<Referent>
    {
    private function Referent ()? calculate;

    @Override
    Referent get()
        {
        if (!assigned)
            {
            set(calc());
            }

        return super();
        }

    protected Referent calc()
        {
        return calculate?();
        TODO("construct LazyVar with a calculate function, or override the calc() method");
        }
    }
