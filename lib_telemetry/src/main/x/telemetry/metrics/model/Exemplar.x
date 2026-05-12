/**
 * A sampled metric observation correlated with a trace span.
 *
 * Exemplars link individual measurements to the distributed trace context that produced
 * them, enabling drill-down from an anomalous metric to the associated trace.
 *
 * `filteredAttributes` carries the subset of the original measurement attributes that
 * are not already present on the enclosing data point.
 */
const Exemplar(UInt64      timeUnixNano,
               NumberValue value,
               Attributes  filteredAttributes = [],
               Byte[]?     traceId            = Null,
               Byte[]?     spanId             = Null) {}
