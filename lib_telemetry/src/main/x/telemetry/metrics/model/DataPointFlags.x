/**
 * Flags carried by every data point.
 *
 * When `noRecordedValue` is `True` the point represents a gap or stale marker in the
 * metric stream rather than a real observation. All fields except attributes and
 * timestamps are meaningless in that case.
 */
const DataPointFlags(Boolean noRecordedValue = False) {
    static DataPointFlags Default = new DataPointFlags();
}
