/**
 * Corresponds to `org.slf4j.helpers.BasicMarker` — the small in-memory `Marker`
 * implementation that ships with `slf4j-api`.
 *
 * Minimal default `Marker` implementation. Stores child references in a list. Implements
 * the [Freezable] contract by freezing its child list on `freeze`; once frozen, `add` and
 * `remove` will throw — matching the standard Ecstasy `Freezable` posture.
 */
class BasicMarker(String name)
        implements Marker {

    private Marker[] children = new Marker[];

    @Override
    void add(Marker reference) {
        if (!children.contains(reference)) {
            children.add(reference);
        }
    }

    @Override
    Boolean remove(Marker reference) {
        return children.removeIfPresent(reference);
    }

    @Override
    @RO Boolean hasReferences.get() {
        return !children.empty;
    }

    @Override
    @RO Iterator<Marker> references.get() {
        return children.iterator();
    }

    @Override
    Boolean contains(Marker other) {
        if (this.name == other.name) {
            return True;
        }
        for (Marker child : children) {
            if (child.contains(other)) {
                return True;
            }
        }
        return False;
    }

    @Override
    Boolean containsName(String name) {
        if (this.name == name) {
            return True;
        }
        for (Marker child : children) {
            if (child.containsName(name)) {
                return True;
            }
        }
        return False;
    }

    // ---- Freezable ------------------------------------------------------------------------

    @Override
    immutable Marker freeze(Boolean inPlace = False) {
        if (this.is(immutable)) {
            return this;
        }
        if (inPlace) {
            // Freeze each child in place and the children array as well, then make this immutable.
            for (Int i : 0 ..< children.size) {
                children[i] = children[i].freeze(inPlace=True);
            }
            children = children.freeze(inPlace=True);
            return makeImmutable();
        }
        // Out-of-place: produce a fresh frozen copy.
        BasicMarker copy = new BasicMarker(name);
        for (Marker child : children) {
            copy.children.add(child.freeze(inPlace=False));
        }
        copy.children = copy.children.freeze(inPlace=True);
        return copy.makeImmutable();
    }
}
