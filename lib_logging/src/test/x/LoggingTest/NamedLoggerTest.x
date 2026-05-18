import logging.BasicLogger;
import logging.Logger;
import logging.LoggerRegistry;

/**
 * Tests for `Logger.named(String)` — the SLF4J `LoggerFactory.getLogger(class)` analogue.
 * The derived logger must:
 *   - carry the supplied `name`;
 *   - share the parent's `sink`, so `LogEvent.loggerName` reflects the derived name on
 *     events the *child* emits while events emitted from the parent keep the parent's
 *     name;
 *   - obey the parent's level threshold (because the threshold lives on the sink).
 */
class NamedLoggerTest {

    @Test
    void shouldDeriveLoggerWithSuppliedName() {
        ListLogSink sink   = new ListLogSink();
        Logger      root   = new BasicLogger("root", sink);
        Logger      child  = root.named("payments");

        assert child.name == "payments";
    }

    @Test
    void shouldRouteChildEventsThroughSharedSink() {
        ListLogSink sink   = new ListLogSink();
        Logger      root   = new BasicLogger("root", sink);
        Logger      child  = root.named("payments");

        root.info("from root");
        child.info("from child");

        assert sink.events.size == 2;
        assert sink.events[0].loggerName == "root";
        assert sink.events[1].loggerName == "payments";
    }

    @Test
    void shouldRespectParentSinkLevelThreshold() {
        ListLogSink sink = new ListLogSink();
        sink.setLevel(logging.Level.Warn);
        Logger root  = new BasicLogger("root", sink);
        Logger child = root.named("noisy");

        // The child's level threshold is its sink's threshold (which is the parent's sink),
        // so a Debug event from the child is dropped just like one from the parent.
        child.debug("muted");
        child.warn ("audible");

        assert sink.events.size == 1;
        assert sink.events[0].message == "audible";
        assert sink.events[0].loggerName == "noisy";
    }

    @Test
    void shouldChainNaming() {
        ListLogSink sink   = new ListLogSink();
        Logger      root   = new BasicLogger("root", sink);
        Logger      a      = root.named("a");
        Logger      ab     = a.named("a.b");

        assert a.name == "a";
        assert ab.name == "a.b";

        ab.info("hi");
        assert sink.events.size == 1;
        assert sink.events[0].loggerName == "a.b";
    }

    @Test
    void shouldInternChildrenWhenRegistryAttached() {
        ListLogSink     sink     = new ListLogSink();
        LoggerRegistry  registry = new LoggerRegistry(sink);

        Logger root      = registry.ensure("root");
        Logger rootAgain = registry.ensure("root");
        // Two registry lookups for the same name return the same instance.
        assert &root == &rootAgain;

        // `named` consults the registry when one is attached, so identity is also
        // stable when reaching the same logger by different paths.
        Logger ab1 = root.named("a.b");
        Logger ab2 = registry.ensure("a.b");
        assert &ab1 == &ab2;
    }

    @Test
    void shouldNotInternWhenRegistryAbsent() {
        ListLogSink sink = new ListLogSink();
        Logger      root = new BasicLogger("root", sink);   // no registry

        // Without a registry, each `named` call allocates fresh.
        Logger a1 = root.named("a");
        Logger a2 = root.named("a");
        assert &a1 != &a2;
    }
}
