/**
 * An implementation of a JSON Merge Patch as specified
 * in [RFC7396](http://tools.ietf.org/html/rfc7396).
 *
 * @param patch  the JSON value to apply as a merge patch
 */
class JsonMergePatch(Doc patch) {

    /**
     * @return True iff this patch is empty, i.e. it will not apply any
     */
    Boolean empty.get() {
        Doc patch = this.patch;
        if (patch.is(JsonObject)) {
            return patch.empty;
        }
        return False;
    }

    /**
     * Apply this patch to the specified target.
     *
     * @param target   the JSON value to apply this patch to
     * @param inPlace  True to modify the target in place (if applicable), or
     *                 False to leave the target unmodified and return a patched
     *                 copy of the target
     *
     * @return the JSON value resulting from applying this patch to the target
     */
    Doc apply(Doc target, Boolean inPlace = False) {
        return merge(target, patch);
    }

    private Doc merge(Doc doc, Doc patch, Boolean inPlace = False) {
        if (patch.is(JsonObject)) {
            JsonObject target;

            if (doc.is(JsonObject)) {
                target = doc;
            } else {
                target = json.newObject();
            }

            JsonObjectBuilder builder = new JsonObjectBuilder(target);
            for (Map.Entry<String, Doc> entry : patch.entries) {
                String key   = entry.key;
                Doc    value = entry.value;
                if (value == Null) {
                    target.remove(key);
                } else {
                    if (Doc targetValue := target.get(key)) {
                        merge(key, merge(targetValue, value, inPlace));
                    } else {
                        merge(key, merge(json.newObject(), value, True));
                    }
                }
            }
            return builder.build();
        }
        return patch;
    }

    /**
     * Generate a JSON Merge Patch from the specified source and target JSON values.
     *
     * @param source the source value
     * @param target the target value
     *
     * @return a `JsonMergePatch` which when applied to the source, will produce the target
     */
    static JsonMergePatch create(Doc source, Doc target) {
        return new JsonMergePatch(diff(source, target));
    }

    /**
     * Generate a JSON Merge Patch from the source and target {@code JsonValue}.
     *
     * @param source the source
     * @param target the target
     *
     * @return a JSON Patch which when applied to the source, yields the target
     */
    private static Doc diff(Doc source, Doc target) {
        if (source.is(JsonObject) && target.is(JsonObject)) {
            JsonObjectBuilder builder = new JsonObjectBuilder();
            // Find the source entries to be removed or replaced
            for (Map.Entry<String, Doc> entry : source.entries) {
                String key = entry.key;
                if (Doc targetValue := target.get(key)) {
                    // key is in both
                    if (entry.value != targetValue) {
                        // target has a different value, so recurse down...
                        builder.add(key, diff(entry.value, targetValue));
                    }
                } else {
                    // key is not in target, so remove from the source
                    builder.add(key, Null);
                }
            }
            // Find entries to be added from the target to the source
            for (Map.Entry<String, Doc> entry : target.entries) {
                String key = entry.key;
                if (!source.contains(entry.key)) {
                    builder.add(key, entry.value);
                }
            }
            return builder.build();
        }
        // either the source or the target is something other than a JSON object
        return target;
    }
}