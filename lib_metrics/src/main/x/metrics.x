/**
 * The `metrics` module provides primitives for collecting and querying runtime measurements:
 * counters, gauges, time-bucketed rolling windows, and (eventually) value-bucketed histograms
 * and summaries.
 *
 * The initial scope is a time-bucketed rolling window suitable for driving UI charts from
 * periodic samples (e.g. "requests per minute over the last hour").
 */
module metrics.xtclang.org {
    package agg import aggregate.xtclang.org;
}
