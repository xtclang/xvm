import logging.BasicLogger;
import logging.BasicMarker;
import logging.Logger;
import logging.Marker;

/**
 * Tests for `Marker` semantics: equality by name, transitive containment, and end-to-
 * end propagation through a `Logger` to a sink.
 */
class MarkerTest {

    @Test
    void shouldContainSelf() {
        Marker m = new BasicMarker("AUDIT");
        assert m.contains(m);
        assert m.containsName("AUDIT");
    }

    @Test
    void shouldContainTransitively() {
        Marker security = new BasicMarker("SECURITY");
        Marker audit    = new BasicMarker("AUDIT");
        Marker breach   = new BasicMarker("BREACH");

        security.add(audit);
        audit.add(breach);

        assert security.contains(audit);
        assert security.contains(breach);   // transitive
        assert security.containsName("BREACH");
    }

    @Test
    void shouldNotContainUnrelated() {
        Marker a = new BasicMarker("A");
        Marker b = new BasicMarker("B");
        assert !a.contains(b);
        assert !a.containsName("B");
    }

    @Test
    void shouldPropagateMarkerOnEvent() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("with.marker", sink);
        Marker      audit  = new BasicMarker("AUDIT");

        logger.info("authenticated", marker=audit);

        assert sink.events.size == 1;
        assert sink.events[0].marker?.name == "AUDIT" : assert;
    }
}
