/**
 * An implementation of a JSON Merge Patch as specified in
 * [JSON Merge Patch specification](http://tools.ietf.org/html/rfc7396).
 *
 * @param patch  the JSON value to apply as a merge patch
 */
class JsonMergePatch(Doc patch) {

    /**
     * `True` iff this patch is empty, i.e. it will not apply to any.
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
     * @param inPlace  (optional) `True` to modify the target in place (if applicable), or `False`
     *                 to leave the target unmodified and return a patched copy of the target
     *
     * @return the JSON value resulting from applying this patch to the target
     */
    Doc apply(Doc target, Boolean inPlace = False) {
        return merge(target, patch, inPlace);
    }

    /**
     * Perform a merge as described by the pseudo code in RFC 7396.
     *
     *     define MergePatch(Target, Patch):
     *          if Patch is an Object:
     *            if Target is not an Object:
     *              Target = {} # Ignore the contents and set it to an empty Object
     *            for each Name/Value pair in Patch:
     *              if Value is null:
     *                if Name exists in Target:
     *                  remove the Name/Value pair from Target
     *              else:
     *                Target[Name] = MergePatch(Target[Name], Value)
     *            return Target
     *          else:
     *            return Patch
     *
     *  * If the `patch` parameter is not a `JsonObject` the `patch` parameter is returned as the result.
     *
     *  * If the target `Doc` is not a `JsonObject` it is ignored and the merge will be applied to
     *    a new empty `JsonObject`.
     *
     *  * If the target `Doc` is a mutable `JsonObject` and the `inPlace` parameter is `True` the merge will be
     *    applied directly to the target.
     *
     *  * A `Null` value for a key in the `patch` will cause the corresponding entry in the target to be removed.
     *    Any `Null` value in the `patch` will not appear in the merged result.
     *
     * @param doc      that target JSON value to apply the patch to
     * @param patch    the JSON value representing the patch to apply
     * @param inPlace  (optional) `True` to modify the target in place (if applicable), or `False`
     *                 to leave the target unmodified and return a patched copy of the target
     *
     * @return the JSON value resulting from applying this patch to the target
     */
    private Doc merge(Doc doc, Doc patch, Boolean inPlace = False) {
        if (patch.is(JsonObject)) {
            JsonObject target;
            if (doc.is(JsonObject)) {
                if (doc.is(immutable) || !inPlace) {
                    // we can make in place true as we are making a new target so there is
                    // no point continually copying target elements from here on
                    inPlace = True;
                    target = json.newObject();
                    target.putAll(doc);
                } else {
                    target = doc;
                }
            } else {
                // we can make in place true as we are making a new target so there is
                // no point continually copying target elements from here on
                inPlace = True;
                target  = json.newObject();
            }

            for ((String key, Doc value) : patch) {
                if (value == Null) {
                    target.remove(key);
                } else {
                    target[key] = merge(target[key], value, inPlace);
                }
            }
            // TODO JK:
            // If the original target is immutable the target being returned will be a copy
            // that is currently mutable. Should it be made immutable to match the original
            // target doc parameter?
            // Basically, should the mutability of the result match the mutability of the
            // original doc parameter?
            return target;
        }
        // TODO JK:
        // Should a copy of the patch be returned and should it be immutable?
        // If we do make a copy, should the mutability of the result match the mutability of the
        // original doc parameter?
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
     * Generate a JSON Merge Patch from the source and target `JsonValue`.
     *
     * @param source  the source
     * @param target  the target
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