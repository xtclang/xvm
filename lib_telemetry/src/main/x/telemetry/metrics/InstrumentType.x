/**
 * The kind of a metric instrument, used as a [InstrumentSelector] criterion and to
 * determine the default aggregation per the OTel spec default-aggregation table.
 */
enum InstrumentType {
    Counter,
    UpDownCounter,
    Gauge,
    Histogram,
    ObservableCounter,
    ObservableUpDownCounter,
    ObservableGauge
}
