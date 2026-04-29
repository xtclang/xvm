/**
 * Unit tests for the `logging.xtclang.org` library.
 *
 * The tests build small, in-memory `LogSink` implementations (a list-backed sink, a
 * counting sink) and assert that:
 *   - the level check fast-path elides emission of disabled events;
 *   - the per-level methods (`trace`, `debug`, `info`, `warn`, `error`) route correctly;
 *   - markers and exceptions arrive at the sink intact;
 *   - the fluent event builder accumulates state and short-circuits on disabled levels;
 *   - `MessageFormatter` substitutes `{}` placeholders.
 *
 * Each test class targets one cohesive area; submodules are picked up automatically by
 * the xunit engine.
 */
module LoggingTest {
    package logging   import logging.xtclang.org;
    package xunit import xunit.xtclang.org;
}
