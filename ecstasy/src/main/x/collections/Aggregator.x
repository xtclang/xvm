/**
 * The `Aggregator` allows for a customizable reduction and aggregation state machine to be
 * implemented. The design of the API is intended to encourage `Aggregator` implementations to be
 * stateless and/or immutable; implementations should be `const`, if possible.
 *
 * The "simple" aggregation process, represented by this API, is composed of two stages:
 *
 * 1. Accumulation: The `Aggregator` provides a stateful and mutable [Accumulator] via the [init]
 *    method, into which all of the `Element` values are accumulated.
 *
 * 2. Reduction: The `Aggregator` is then given that same `Accumulator`, as part of the request to
 *    [reduce] the accumulated `Element` value to a `Result` value.
 *
 * An `Aggregator` that can perform this process in parallel should implement the
 * [ParallelAggregator] interface.
 *
 * A collection of commonly used Aggregator implementations are located in the
 * `aggregate.xtclang.org` module.
 */
interface Aggregator<Element, Result>
    {
    /**
     * An Accumulator is the mutable, stateful representation into which `Element` values are
     * accumulated.
     */
    typedef Appender<Element> as Accumulator;

    /**
     * Create an intermediate, mutable data structure that will accumulate Elements as part of
     * the aggregator implementation. The `Accumulator` is write-only from the view of the caller.
     *
     * @return an `Accumulator` that will be used to accumulate all of the `Element` values that
     *         are being aggregated
     */
    Accumulator init();

    /**
     * Extract the aggregated result from the passed `Accumulator`, which must be an `Accumulator`
     * that was previously created by this Aggregator.
     *
     * @param accumulator  the `Accumulator`, previous provided by this Aggregator, that the resul
     *                     will be extracted from
     *
     * @return the result of the aggregation process
     */
    Result reduce(Accumulator accumulator);
    }
