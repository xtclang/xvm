/**
 * A `View` pairs an [InstrumentSelector] (which instruments it applies to) with a
 * [StreamConfig] (how those instruments' output streams are customised).
 *
 * Register views with [memory.InMemoryMeterProvider] at construction time:
 *
 *     new InMemoryMeterProvider(
 *         resource = ...,
 *         views    = [
 *             // Drop all "internal.*" metrics
 *             new View(new InstrumentSelector(name = "internal.*"),
 *                      new StreamConfig(aggregation = Aggregation.Drop)),
 *             // Keep only the "region" attribute on HTTP counters
 *             new View(new InstrumentSelector(name = "http.*", type = Counter),
 *                      new StreamConfig(attributeKeys = ["region"])),
 *         ])
 *
 * When multiple views match the same instrument, each matching view produces an
 * independent output stream. When only Drop views match, the instrument is suppressed.
 * When no views match, the instrument uses its default aggregation and configuration.
 */
const View(InstrumentSelector selector, StreamConfig config) {}
