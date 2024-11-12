import json.Doc;
import json.JsonArray;
import json.JsonMergePatch;
import json.JsonObject;
import json.Primitive;

class JsonMergePatchTest {

    @Test
    void shouldCreateAnEmptyPatch() {
        JsonObject     o     = json.newObject();
        JsonMergePatch patch = new JsonMergePatch(o);
        assert patch.empty;
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"b"}       {"a":"c"}       {"a":"c"}
     */
    @Test
    void shouldMergeReplacingSingleKeyValue() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="c"]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"="c"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"b"}       {"b":"c"}       {"a":"b",
     *                                    "b":"c"}
     */
    @Test
    void shouldMergeAddingNewEntry() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch(Map:["b"="c"]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"="b", "b"="c"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"b"}       {"a":null}      {}
     */
    @Test
    void shouldMergeRemovingOnlyEntry() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=Null]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result.empty;
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"b",       {"a":null}      {"b":"c"}
     *    "b":"c"}
     */
    @Test
    void shouldMergeRemovingEntry() {
        JsonObject     original = Map:["a"="b", "b"="c"];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=Null]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["b"="c"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":["b"]}     {"a":"c"}       {"a":"c"}
     */
    @Test
    void shouldMergeReplacingArrayWithPrimitive() {
        JsonArray      array    = Array<Doc>:["b"];
        JsonObject     original = Map:["a"=array];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="c"]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"="c"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"c"}       {"a":["b"]}     {"a":["b"]}
     */
    @Test
    void shouldMergeReplacingPrimitiveWithArray() {
        JsonObject     original = Map:["a"="c"];
        JsonArray      array    = Array<Doc>:["b"];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=array]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"=array];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a": {         {"a": {         {"a": {
     *     "b": "c"}       "b": "d",       "b": "d"
     *   }                 "c": null}      }
     *                   }               }
     */
    @Test
    void shouldMergeReplacingChildEntryIgnoringNullValue() {
        JsonObject     original = Map:["a"=Map<String,Doc>:["b"="c"]];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=Map<String,Doc>:["b"="d", "c"=Null]]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"=Map<String,Doc>:["b"="d"]];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a": [         {"a": [1]}      {"a": [1]}
     *     {"b":"c"}
     *    ]
     *   }
     */
    @Test
    void shouldMergeReplacingChildObjectWithArray() {
        JsonObject     original = Map:["a"=Map<String,Doc>:["b"="c"]];
        JsonArray      array    = Array<Doc>:[1];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=array]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"=array];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   ["a","b"]       ["c","d"]       ["c","d"]
     */
    @Test
    void shouldMergeWhereTargetAndPatchAreArrays() {
        JsonArray      original = Array<Doc>:["a", "b"];
        JsonMergePatch patch    = new JsonMergePatch(Array<Doc>:["c", "d"]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonArray);
        assert result == Array<Doc>:["c", "d"];
    }

    @Test
    void shouldMergeWhereTargetAndPatchArePrimitives() {
        Doc            original = "a";
        JsonMergePatch patch    = new JsonMergePatch("b");
        Doc            result   = patch.apply(original);
        assert result.is(Primitive);
        assert result == "b";
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"b"}       ["c"]           ["c"]
     */
    @Test
    void shouldMergeWherePatchIsArray() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch(Array<Doc>:["c"]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonArray);
        assert result == Array<Doc>:["c"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"foo"}     null            null
     */
    @Test
    void shouldMergeWherePatchIsNull() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch(Null);
        Doc            result   = patch.apply(original);
        assert result.is(Primitive);
        assert result == Null;
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"a":"foo"}     "bar"           "bar"
     */
    @Test
    void shouldMergeWherePatchIsPrimitive() {
        JsonObject     original = Map:["a"="b"];
        JsonMergePatch patch    = new JsonMergePatch("bar");
        Doc            result   = patch.apply(original);
        assert result.is(Primitive);
        assert result == "bar";
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {"e":null}      {"a":1}         {"e":null,
     *                                    "a":1}
     */
    @Test
    void shouldMergeAddingNewEntryWhereTargetContainsNull() {
        JsonObject     original = Map:["e"=Null];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=1]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map<String,Doc>:["e"=Null, "a"=1];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   [1,2]           {"a":"b",       {"a":"b"}
     *                    "c":null}
     */
    @Test
    void shouldMergeWhereTargetIsArrayIgnoringNullValuesInPatch() {
        JsonArray      original = Array<Doc>:[1, 2];
        JsonMergePatch patch    = new JsonMergePatch(Map<String,Doc>:["a"="b", "c"=Null]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result == Map:["a"="b"];
    }

    /**
     * Test from the examples in Appendix A of RFC 7396
     *
     *   ORIGINAL        PATCH            RESULT
     *   ------------------------------------------
     *   {}              {"a":            {"a":
     *                    {"bb":           {"bb":
     *                     {"ccc":          {}}}
     *                      null}}}
     */
    @Test
    void shouldMergeWhereTargetIsEmptyIgnoringNullValuesInPatch() {
        JsonObject     original = Map<String, Doc>:[];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"=Map<String,Doc>:["bb"=Map<String,Doc>:["ccc"=Null]]]);
        Doc            result   = patch.apply(original);
        assert result.is(JsonObject);
        assert result.size == 1;
        Doc a = result["a"];
        assert a.is(JsonObject);
        assert a.size == 1;
        Doc bb = a["bb"];
        assert bb.is(JsonObject);
        assert bb.empty;
    }

    @Test
    void shouldMergeInPlaceWhenTargetIsMutableAndInPlaceIsTrue() {
        JsonObject     original = json.newObject();
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="b"]);
        Doc            result   = patch.apply(original, True);
        assert &result == &original as "source and copy should be the same object reference";
        assert result.is(JsonObject);
        assert result == Map:["a"="b"];
    }

    @Test
    void shouldMergeMakingCopyWhenTargetIsImmutableAndInPlaceIsTrue() {
        JsonObject     original = Map<String, Doc>:[];
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="b"]);
        Doc            result   = patch.apply(original, True);
        assert &result != &original as "source and copy should not be the same object reference";
        assert result.is(JsonObject);
        assert result == Map:["a"="b"];
    }

    @Test
    void shouldMergeMakingCopyWhenTargetIsMutableAndInPlaceIsFalse() {
        JsonObject     original = new HashMap();
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="b"]);
        Doc            result   = patch.apply(original, False);
        assert &result != &original as "source and copy should not be the same object reference";
        assert result.is(JsonObject);
        assert result == Map:["a"="b"];
    }

    @Test
    void shouldMergeMakingCopyWhenTargetIsMutableAndInPlaceNotSpecified() {
        JsonObject     original = new HashMap();
        JsonMergePatch patch    = new JsonMergePatch(Map:["a"="b"]);
        Doc            result   = patch.apply(original);
        assert &result != &original as "source and copy should not be the same object reference";
        assert result.is(JsonObject);
        assert result == Map:["a"="b"];
    }
}