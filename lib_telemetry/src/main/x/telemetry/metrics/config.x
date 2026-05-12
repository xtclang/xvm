import metrics.model.AggregationTemporality;

package config {
    /**
     * The injection name prefix for all telemetry configuration values.
     */
    static String MetricsConfigPrefix = ConfigPrefix  + ".metrics";

    /**
     * The injection name for the metrics enabled configuration values.
     */
    static String ConfigMetricsEnabled = MetricsConfigPrefix  + ".enabled";

    /**
     * The injection name for the metrics temporality configuration values.
     */
    static String ConfigMetricsTemporality = MetricsConfigPrefix  + ".temporality";

    /**
     * Returns true if metrics are enabled.
     */
    @Lazy Boolean enabled.calc() {
        @Inject(ConfigMetricsEnabled) Boolean? enabled;
        return enabled ?: True;
    }

    @Lazy AggregationTemporality aggregationTemporality.calc() {
        @Inject(ConfigMetricsTemporality) AggregationTemporality? cfgTemporality;
        return cfgTemporality ?: MetricDefaults.DefaultAggregationTemporality;
    }

}