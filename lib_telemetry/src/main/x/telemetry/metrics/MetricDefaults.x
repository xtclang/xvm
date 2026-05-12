import metrics.model.AggregationTemporality;

interface MetricDefaults {

    /**
     * The default aggregation temporality.
     */
    static AggregationTemporality DefaultAggregationTemporality = AggregationTemporality.Delta;

}
