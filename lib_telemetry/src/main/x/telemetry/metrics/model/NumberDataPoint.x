/**
 * A single data point in a Gauge or Sum metric stream.
 *
 * The value is either a signed 64-bit integer or a 64-bit IEEE 754 double, matching the
 * OTel `as_int` / `as_double` oneof. `timeUnixNano` is the inclusive end of the
 * observation interval. `startTimeUnixNano`, when present, marks the inclusive start and
 * enables gap and reset detection by consumers.
 */
const NumberDataPoint(UInt64         timeUnixNano,
                      NumberValue    value,
                      Attributes     attributes        = [],
                      UInt64?        startTimeUnixNano = Null,
                      Exemplar[]     exemplars         = [],
                      DataPointFlags flags             = DataPointFlags.Default) {}
