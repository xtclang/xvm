/**
 * Describes when metric values are reported relative to time.
 *
 * Delta: each report covers only the interval since the last report; (T_n-1, T_n].
 * Cumulative: each report covers all values since the stream started; (T_0, T_n].
 */
enum AggregationTemporality {
    Delta,
    Cumulative
}
