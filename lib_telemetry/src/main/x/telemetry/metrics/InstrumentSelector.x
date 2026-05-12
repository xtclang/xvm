/**
 * Predicate that determines which instruments a [View] applies to.
 *
 * All provided criteria are treated as AND conditions: an instrument must satisfy every
 * non-null criterion for the selector to match. An `InstrumentSelector` with all fields
 * null matches every instrument.
 *
 * The `name` criterion supports glob wildcards:
 *  - `*` matches any sequence of characters (including empty).
 *  - `?` matches exactly one character.
 *  - A bare `"*"` is the spec-required match-all pattern.
 *
 * Example — match all counters whose name starts with "http.":
 *
 *     new InstrumentSelector(name = "http.*", type = Counter)
 */
const InstrumentSelector(String?         name           = Null,
                         InstrumentType? type           = Null,
                         String?         unit           = Null,
                         String?         meterName      = Null,
                         String?         meterVersion   = Null,
                         String?         meterSchemaUrl = Null) {

    /**
     * Returns `True` if this selector matches the given instrument identity.
     *
     * @param desc   the instrument's [InstrumentDescriptor]
     * @param iType  the instrument's [InstrumentType]
     * @param scope  the [InstrumentationScope] of the [Meter] that created the instrument
     */
    Boolean matches(InstrumentDescriptor desc, InstrumentType iType, InstrumentationScope scope) {
        if (String namePattern ?= name, !matchesGlob(namePattern, desc.name)) {
            return False;
        }
        if (InstrumentType t ?= type, t != iType) {
            return False;
        }
        if (String u ?= unit) {
            String? descUnit = desc.unit;
            if (descUnit == Null || descUnit != u) {
                return False;
            }
        }
        if (String mn ?= meterName, mn != scope.name) {
            return False;
        }
        if (String mv ?= meterVersion) {
            String? scopeVersion = scope.version;
            if (scopeVersion == Null || scopeVersion != mv) {
                return False;
            }
        }
        if (String ms ?= meterSchemaUrl) {
            String? scopeSchema = scope.schemaUrl;
            if (scopeSchema == Null || scopeSchema != ms) {
                return False;
            }
        }
        return True;
    }

    // ----- glob matching -------------------------------------------------------------------------

    private static Boolean matchesGlob(String pattern, String text) {
        return globAt(pattern, 0, text, 0);
    }

    private static Boolean globAt(String pattern, Int pi, String text, Int ti) {
        while (pi < pattern.size) {
            Char c = pattern[pi];
            if (c == '*') {
                // * matches zero or more characters; try every possible split
                if (globAt(pattern, pi + 1, text, ti)) {
                    return True;
                }
                if (ti >= text.size) {
                    return False;
                }
                return globAt(pattern, pi, text, ti + 1);
            } else if (c == '?') {
                // ? matches exactly one character
                if (ti >= text.size) {
                    return False;
                }
                pi++;
                ti++;
            } else {
                // literal match
                if (ti >= text.size || text[ti] != c) {
                    return False;
                }
                pi++;
                ti++;
            }
        }
        return ti == text.size;
    }
}
