import logging.Marker;
import logging.MarkerFactory;

/**
 * Tests for `MarkerFactory` interning semantics. SLF4J users expect
 * `MarkerFactory.getMarker("AUDIT")` to return a canonical marker for that name, while
 * `getDetachedMarker(...)` returns an unregistered, one-off marker.
 */
class MarkerFactoryTest {

    @Test
    void shouldInternMarkersByName() {
        MarkerFactory factory = new MarkerFactory();

        Marker first  = factory.getMarker("AUDIT");
        Marker second = factory.getMarker("AUDIT");

        assert &first == &second;
        assert first.name == "AUDIT";
    }

    @Test
    void shouldTrackInternedMarkerExistence() {
        MarkerFactory factory = new MarkerFactory();

        assert !factory.exists("SECURITY");

        factory.getMarker("SECURITY");

        assert factory.exists("SECURITY");
    }

    @Test
    void shouldCreateDetachedMarkersOutsideTheInternCache() {
        MarkerFactory factory = new MarkerFactory();

        Marker detached = factory.getDetachedMarker("AUDIT");

        assert !factory.exists("AUDIT");
        Marker interned = factory.getMarker("AUDIT");
        assert &detached != &interned;
    }
}
