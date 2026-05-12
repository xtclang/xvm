/**
 * A quantile value within a Summary data point.
 *
 * `quantile` must be in [0.0, 1.0]. Common values are 0.5 (median), 0.9, 0.95, 0.99.
 */
const ValueAtQuantile {
    construct(Float64 quantile, Float64 value) {
        assert:arg quantile >= 0.0 && quantile <= 1.0;
        this.quantile = quantile;
        this.value    = value;
    }

    Float64 quantile;
    Float64 value;
}
