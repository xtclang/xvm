/**
 * Configuration applied to a metric stream when a [View] matches an instrument.
 *
 * All fields are optional; unset fields mean "use the instrument's own value". Empty
 * `attributeKeys` and `excludedAttributeKeys` arrays mean "no filter" (all attributes
 * pass through). A non-empty allow-list keeps only the listed keys; a non-empty
 * exclude-list removes the listed keys.
 *
 * Example — rename a stream and keep only the "region" attribute:
 *
 *     new StreamConfig(name         = "http.requests.by_region",
 *                      attributeKeys = ["region"])
 */
const StreamConfig(String?      name                  = Null,
                   String?      description           = Null,
                   String[]     attributeKeys         = [],
                   String[]     excludedAttributeKeys = [],
                   Aggregation  aggregation           = Aggregation.Default) {

    /**
     * Returns `True` if this config applies an attribute allow-list or exclude-list.
     */
    Boolean hasFilter.get() = !attributeKeys.empty || !excludedAttributeKeys.empty;

    /**
     * Apply this config's attribute filter to `attrs`. An empty [attributeKeys] means
     * "allow all keys"; a non-empty list keeps only those keys. An empty
     * [excludedAttributeKeys] means "exclude nothing"; a non-empty list removes those
     * keys. If neither list is configured, `attrs` is returned unchanged.
     */
    static Attributes filterAttributes(StreamConfig cfg, Attributes attrs) {
        if (!cfg.hasFilter) {
            return attrs;
        }
        String[] allow   = cfg.attributeKeys;
        String[] exclude = cfg.excludedAttributeKeys;
        Map<String, AnyValue> result = new ecstasy.maps.HashMap();
        for ((String k, AnyValue v) : attrs) {
            if (isAllowed(k, allow, exclude)) {
                result.put(k, v);
            }
        }
        return result;
    }

    private static Boolean isAllowed(String key, String[] allow, String[] exclude) {
        if (!allow.empty) {
            for (String s : allow) {
                if (s == key) {
                    return True;
                }
            }
            return False;
        }
        for (String s : exclude) {
            if (s == key) {
                return False;
            }
        }
        return True;
    }
}
