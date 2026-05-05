import logging.BasicLogger;
import logging.BasicMarker;
import logging.Logger;
import logging.Marker;

import xunit.assertions.assertThrows;

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
    void shouldRejectSelfReference() {
        Marker a = new BasicMarker("A");

        IllegalArgument e = assertThrows(() -> a.add(a));
        assert e.message.indexOf("cycle");
    }

    @Test
    void shouldRejectReciprocalReference() {
        Marker a = new BasicMarker("A");
        Marker b = new BasicMarker("B");

        a.add(b);

        IllegalArgument e = assertThrows(() -> b.add(a));
        assert e.message.indexOf("cycle");
    }

    @Test
    void shouldPropagateMarkerOnEvent() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("with.marker", sink);
        Marker      audit  = new BasicMarker("AUDIT");

        logger.info("authenticated", marker=audit);

        assert sink.events.size == 1;
        assert sink.events[0].markers.size == 1;
        assert sink.events[0].markers[0].name == "AUDIT";
        // Backwards-compat single-marker accessor.
        assert sink.events[0].marker?.name == "AUDIT" : assert;
    }

    @Test
    void shouldPropagateMultipleMarkersThroughFluentBuilder() {
        ListLogSink sink     = new ListLogSink();
        Logger      logger   = new BasicLogger("multi.marker", sink);
        Marker      audit    = new BasicMarker("AUDIT");
        Marker      security = new BasicMarker("SECURITY");

        logger.atInfo()
              .addMarker(audit)
              .addMarker(security)
              .log("login attempt");

        assert sink.events.size == 1;
        assert sink.events[0].markers.size == 2;
        assert sink.events[0].markers[0].name == "AUDIT";
        assert sink.events[0].markers[1].name == "SECURITY";
        // The single-marker compat accessor returns the first one.
        assert sink.events[0].marker?.name == "AUDIT" : assert;
    }

    @Test
    void shouldEmitNoMarkersWhenNoneAttached() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("no.marker", sink);

        logger.info("plain message");

        assert sink.events.size == 1;
        assert sink.events[0].markers.empty;
        assert sink.events[0].marker == Null;
    }
}
