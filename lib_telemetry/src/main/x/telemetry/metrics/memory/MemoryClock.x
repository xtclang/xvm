/**
 * Shared clock utility for the in-memory SDK.
 *
 * Provides the current wall-clock time as nanoseconds since the Unix epoch, which is
 * the unit required by the OTel data model for `timeUnixNano` and `startTimeUnixNano`
 * fields on every data point.
 */
const MemoryClock {
    static UInt64 nowNanos() {
        @Inject Clock clock;
        return (clock.now.epochPicos / 1000).toUInt64();
    }
}
