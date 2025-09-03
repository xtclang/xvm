/**
 * The `@Lazy` annotation allows a reference (such as a property) to be lazily populated with a
 * value the first time it is accessed.
 *
 * Consider the following example in which the [calc] method is explicitly implemented:
 *
 *     const Point(Dec x, Dec y) {
 *         @Lazy Dec distanceFromOrigin.calc() = (x * x * y * y).sqrt();
 *     }
 *
 * Alternatively, a function or lambda can be provided to the constructor of the `@Lazy` annotation:
 *
 *     const Point(Dec x, Dec y) {
 *         @Lazy(() -> (x * x * y * y).sqrt()) Dec distanceFromOrigin;
 *     }
 *
 * When the property is accessed, the value is obtained via its [get] method. When the first access
 * occurs, the internal value of the `@Lazy` property is still unassigned, and so the property
 * invokes its [calc] method to calculate a value for the property. That value is then stored as the
 * internal value of the property, and every subsequent access will use that previously calculated
 * value.
 */
annotation LazyVar<Referent>(function Referent ()? calculate = Null)
        extends VolatileVar<Referent> {

    private function Referent ()? calculate;

    /**
     * Note: Do not implement this method to provide a value; instead, implement the [calc] method.
     */
    @Override
    @Concurrent
    Referent get() {
        if (!assigned) {
            set(calc());
        }

        return super();
    }

    /**
     * Calculate a value for this property. All `@Lazy` properties must either override this method,
     * or provide a [calculate] function for the property constructor.
     *
     * The lazy calculation should be idempotent.
     *
     * @return the value to store in this `@Lazy` property
     */
    @Synchronized
    protected Referent calc() {
        // if a `calculate` function was provided, then invoke it
        return calculate?();

        // if this method was not overridden by an implementation that calculated the value, and if
        // no `calculate` function was provided, then the developer needs to do one or the other
        TODO("construct LazyVar with a calculate function, or override the calc() method");
    }
}