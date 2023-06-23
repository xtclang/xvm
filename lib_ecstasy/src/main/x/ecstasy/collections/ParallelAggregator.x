/**
 * The `ParallelAggregator` allows for a customizable aggregation state machine to be implemented
 * in a way that allows the aggregation process to be broken down into chunks that can be performed
 * in parallel. Because references to the aggregator need to be able to be passed among services,
 * the implementations  of `ParallelAggregator` should be `const` (or immutable, a service, etc.),
 * including the objects provided by the [elementAggregator], [intermediateAggregator], and
 * [finalAggregator] properties.
 *
 * The "parallel" aggregation process, represented by this API, is composed of three stages:
 *
 * 1. Each service that is reducing in parallel will be responsible for some "slice" of Element
 *    values. It will obtain the [elementAggregator] from the ParallelAggregator, and obtain an
 *    `Accumulator` from that. It will then feed its `Element` values into the `Accumulator` and
 *    reduce that into a `Partial` value.
 *
 * 2. The `Partial` values will be collected from stage 1; stage 2 is optional. It is possible
 *    that the `Partial` values will also be aggregated in parallel; for example, in a
 *    distributed system, each server may have a number of services that have aggregated stage 1
 *    in parallel, and now each server will (in parallel) aggregate its `Partial` values into a
 *    single `Partial` value that will be returned to the originating server in the distributed
 *    system. In such a case, each server will obtain the [intermediateAggregator] from the
 *    ParallelAggregator, and obtain an `Accumulator` from that. It will then feed its `Partial`
 *    values into the `Accumulator` and reduce that into a single `Partial` value.
 *
 * 3. The last stage is to reduce the remaining `Partial` values into a single `Result` value.
 *    The service that has collected those `Partial` values will obtain the [finalAggregator] from
 *    the ParallelAggregator, and obtain an `Accumulator` from that. It will then feed its
 *    `Partial` values into the `Accumulator` and reduce that into the `Result` value.
 */
interface ParallelAggregator<Element, Partial, Result>
        extends Aggregator<Element, Result> {
    /**
     * A `Aggregator` that operates on `Element` values, and can be used across multiple Ecstasy
     * services concurrently, each operating on a "slice" of the data.
     *
     * This `Aggregator` is used to reduce some slice of `Element` data into a `Partial` result.
     */
    @RO Aggregator<Element, Partial> elementAggregator;

    /**
     * A `Aggregator` that operates on `Partial` values, and can theoretically be used across
     * multiple Ecstasy services concurrently, each operating on a "slice" of the `Partial`
     * values.
     *
     * This `Aggregator` is used to reduce multiple `Partial` results into a single `Partial`
     * result.
     */
    @RO Aggregator<Partial, Partial> intermediateAggregator;

    /**
     * A `Aggregator` that operates on `Partial` values, to and produces the final result of the
     * reduce process.
     *
     * This `Aggregator` is used to reduce multiple `Partial` results into a single `Result`.
     */
    @RO Aggregator<Partial, Result> finalAggregator;

    @Override
    Accumulator init(Int capacity = 0) {
        // the default init() and reduce() implementations should be optimized for non-parallel
        // aggregation, if appropriate, in order to reduce the number of objects and steps
        return elementAggregator.init(capacity);
    }

    @Override
    Result reduce(Accumulator accumulator) {
        // the default init() and reduce() implementations should be optimized for non-parallel
        // aggregation, if appropriate, in order to reduce the number of objects and steps
        Partial intermediateResult = elementAggregator.reduce(accumulator);
        return finalAggregator.reduce(finalAggregator.init().add(intermediateResult));
    }
}
